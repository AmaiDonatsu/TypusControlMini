package com.example.typuscontrolmini

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
//import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
//import java.io.File
//import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.app.Activity
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    private var frameCount = 0
    private val targetFps = 15
    private val frameInterval = 1000L / targetFps // 66ms entre frames
    private var lastFrameTime = 0L

    // Websocket client
    private var webSocketClient: WebSocketClient? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private val commandHandler = CommandHandler()


    companion object {
        // Unificamos TAG para debug fácil
        private const val TAG = "DEBUG_WS" 
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_DEVICE = "device"
        const val EXTRA_SECRET_KEY = "secret_key"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[SERVICE] 🟢 onStartCommand llamado")
        startForeground(NOTIFICATION_ID, createNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: "ws://izabella-unpearled-clearly.ngrok-free.dev/ws/stream"
        val auth: FirebaseAuth? = FirebaseAuth.getInstance()
        val device = intent?.getStringExtra(EXTRA_DEVICE)
        val secretKey = intent?.getStringExtra(EXTRA_SECRET_KEY)

        Log.d(TAG, "[SERVICE] Params: resultCode=$resultCode, device=$device, url=$serverUrl")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            Log.d(TAG, "[SERVICE] ✅ Datos de MediaProjection válidos. Inicializando cliente WS...")

            webSocketClient = WebSocketClient(serverUrl)

            webSocketClient?.setOnCommandReceived { commandJson ->
                Log.d(TAG, "[SERVICE] 📨 Pasando comando a Handler: $commandJson")
                commandHandler.handleCommand(commandJson) { response ->
                    webSocketClient?.sendResponse(response)
                }
            }

            webSocketClient?.connect(
                onConnected = {
                    Log.d(TAG, "[SERVICE] ✅ WS Conectado. Iniciando captura de pantalla...")
                    // CORRECCIÓN: Ejecutar startCapture en el hilo principal
                    // Esto es necesario porque registerCallback(..., null) usa el Looper actual,
                    // y el hilo de OkHttp no tiene Looper.
                    Handler(Looper.getMainLooper()).post {
                        startCapture(resultCode, resultData)
                    }
                },
                onDisconnected = {
                    Log.w(TAG, "[SERVICE] 🔴 WS Desconectado. Deteniendo servicio...")
                    stopSelf()
                },
                auth = auth!!,
                device = "$device",
                secretKey = "$secretKey",
            )

        } else {
            Log.e(TAG, "[SERVICE] ❌ Error: ResultCode no es OK o data es nula")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        Log.d(TAG, "[SERVICE] startCapture() invocado")
        try {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "[SERVICE] ❌ Excepción al obtener MediaProjection: ${e.message}")
            stopSelf()
            return
        }

        val mediaProjectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "[SERVICE] 🛑 MediaProjection se detuvo por el sistema")
                stopSelf()
            }
        }

        // Aquí estaba el problema: pasar 'null' como handler usa el hilo actual.
        // Al estar ahora en el Main Thread (gracias al fix arriba), esto funcionará.
        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        if (mediaProjection == null) {
            Log.e(TAG, "[SERVICE] ❌ MediaProjection es NULL")
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics
        
        // --- CONFIGURACIÓN DE RESOLUCIÓN ---
        val aspectRatio = metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()
        val width = 480
        val height = (width * aspectRatio).toInt()
        val density = metrics.densityDpi

        Log.d(TAG, "[SERVICE] Configurando VirtualDisplay: ${width}x${height} (Original: ${metrics.widthPixels}x${metrics.heightPixels})")

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        // Alineación a 64 bits para seguridad de hardware
        val safeWidth = width + (64 - (width % 64)) % 64 
        
        imageReader = ImageReader.newInstance(safeWidth, height, PixelFormat.RGBA_8888, 2)

        imageReader?.setOnImageAvailableListener({ reader ->
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastFrameTime < frameInterval) {
                // Descartar
                var img = reader.acquireLatestImage()
                while (img != null) {
                    img.close()
                    img = reader.acquireLatestImage()
                }
                return@setOnImageAvailableListener
            }

            // Procesar
            val image = reader.acquireLatestImage()
            if (image != null) {
                lastFrameTime = currentTime
                processFrame(image)
                image.close() 
            }
        }, handler)

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                safeWidth,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            Log.d(TAG, "[SERVICE] ✅ VirtualDisplay creado exitosamente: ${safeWidth}x${height}")

        } catch (e: Exception) {
            Log.e(TAG, "[SERVICE] ❌ Error fatal creando VirtualDisplay: ${e.message}")
            e.printStackTrace()
            stopSelf()
            return
        }

    }

    private fun processFrame(image: Image) {
        try {
            val bitmap = imageToBitmap(image)
            val stream = ByteArrayOutputStream()
            // Calidad 60
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            val jpegBytes = stream.toByteArray()
            bitmap.recycle()

            val sent = webSocketClient?.sendFrame(jpegBytes) ?: false

            if (sent) {
                frameCount++
                if (frameCount % 60 == 0) Log.d(TAG, "[SERVICE] ⚡ Stream estable. Frames enviados: $frameCount")
            } else {
                Log.w(TAG, "[SERVICE] ⚠️ Frame no enviado (sendFrame retornó false)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[SERVICE] ❌ Excepción en processFrame: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        if (rowPadding == 0) {
            return bitmap
        }

        val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        bitmap.recycle()
        return cropped
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Captura de Pantalla",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Capturando pantalla 📹")
            .setContentText("La captura está activa")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[SERVICE] 🛑 onDestroy() llamado. Limpiando recursos...")
        Log.d(TAG, "[SERVICE] Total frames procesados: $frameCount")

        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
            handlerThread?.quitSafely()
            webSocketClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "[SERVICE] Error en cleanup: ${e.message}")
        }
    }

}