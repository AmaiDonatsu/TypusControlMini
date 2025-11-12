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
import android.util.Log
import androidx.core.app.NotificationCompat
//import java.io.File
//import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.app.Activity
import android.util.Base64
import androidx.core.graphics.createBitmap
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
        private const val TAG = "ScreenCaptureService"
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
        startForeground(NOTIFICATION_ID, createNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: "ws://10.0.2.2:8000/ws/stream"
        val auth: FirebaseAuth? = FirebaseAuth.getInstance()
        val device = intent?.getStringExtra(EXTRA_DEVICE)
        val secretKey = intent?.getStringExtra(EXTRA_SECRET_KEY)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            Log.d(TAG, "✅ Datos válidos, iniciando captura...")

            webSocketClient = WebSocketClient(serverUrl)

            // 🆕 AGREGAR ESTO: Configurar callback de comandos
            webSocketClient?.setOnCommandReceived { commandJson ->
                Log.d(TAG, "📨 Comando recibido: $commandJson")

                // Procesar el comando con el handler
                commandHandler.handleCommand(commandJson) { response ->

                    webSocketClient?.sendResponse(response)
                    Log.d(TAG, "📤 Respuesta enviada: $response")
                }
            }
            // FIN DE LO NUEVO 🆕

            webSocketClient?.connect(
                onConnected = {
                    Log.d(TAG, "✅ WebSocket conectado y listo para comandos!")
                    startCapture(resultCode, resultData)
                },
                onDisconnected = {
                    Log.d(TAG, "🔴 WebSocket disconnected!")
                },
                auth = auth!!,
                device = "$device",
                secretKey = "$secretKey",
            )

        } else {
            Log.e(TAG, "❌ Datos inválidos para iniciar captura")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val mediaProjectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "🛑 Capture stopping")
                stopSelf()
            }
        }

        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        if (mediaProjection == null) {
            Log.e(TAG, "❌ cannot get MediaProjection")
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics

        // Resolución al 50%
        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2
        val density = metrics.densityDpi

        Log.d(TAG, "Capturing in: ${width}x${height}")

        // Thread to avoid block UI
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        // ImageReader: "buzón" de frames
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                while (reader.acquireLatestImage().also { image = it } != null) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFrameTime >= frameInterval) {
                        image?.close()
                        continue
                    }
                    lastFrameTime = currentTime
                    processFrame(image!! )
                    image.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando frame: ${e.message}")
                image?.close()
            }
            val currentTime = System.currentTimeMillis()

            // ⏱️ Fps control: ~66ms (15 FPS)
            if (currentTime - lastFrameTime >= frameInterval) {
                lastFrameTime = currentTime

                val image = reader.acquireLatestImage()
                if (image != null) {
                    processFrame(image)
                    image.close()
                }
            }
        }, handler)

        // VirtualDisplay: conect MediaProjection -> ImageReader
        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            Log.d(TAG, "✅ Captura iniciada!")

        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ Error al crear VirtualDisplay: ${e.message}")
            stopSelf()
            return
        }

    }

    private fun processFrame(image: Image) {
        try {
            // Image to Bitmap
            val bitmap = imageToBitmap(image)

            // Bitmap to JPEG bytes
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val jpegBytes = stream.toByteArray()

            val sent = webSocketClient?.sendFrame(jpegBytes) ?: false

            if (sent) {
                frameCount++
            } else {
                Log.w(TAG, "⚠️ No se pudo enviar frame")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando frame: ${e.message}")
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = createBitmap(image.width + rowPadding / pixelStride, image.height)
        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
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
        Log.d(TAG, "🛑 Deteniendo captura. Total de frames: $frameCount")

        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        handlerThread?.quitSafely()
    }

}