package com.example.typuscontrolmini

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import android.app.Activity

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    private var frameCount = 0
    private val targetFps = 15
    private val frameInterval = 1000L / targetFps // 66ms entre frames
    private var lastFrameTime = 0L

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ⚡ PRIMERO: Notificación INMEDIATA
        startForeground(NOTIFICATION_ID, createNotification())

        // 🔍 DEBUG: Ver qué recibimos
        Log.d(TAG, "Intent recibido: $intent")
        Log.d(TAG, "Extras: ${intent?.extras}")

        // DESPUÉS: Procesamos los datos
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0  // Default 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        // 🔍 DEBUG: Ver qué obtuvimos
        Log.d(TAG, "ResultCode recibido: $resultCode")
        Log.d(TAG, "ResultData recibido: $resultData")

        if (resultCode == Activity.RESULT_OK && resultData != null) {  // 👈 ESTO!
            Log.d(TAG, "✅ Datos válidos, iniciando captura...")
            startCapture(resultCode, resultData)
        } else {
            Log.e(TAG, "❌ Datos inválidos para iniciar captura")
            Log.e(TAG, "   resultCode: $resultCode (esperaba ${Activity.RESULT_OK})")
            Log.e(TAG, "   resultData: $resultData")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val mediaProjectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "🛑 Captura detenida")
                stopSelf()
            }
        }

        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        if (mediaProjection == null) {
            Log.e(TAG, "❌ No se pudo obtener el MediaProjection")
            stopSelf()
            return
        }

        val metrics = resources.displayMetrics

        // Resolución al 50%
        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2
        val density = metrics.densityDpi

        Log.d(TAG, "Capturando a: ${width}x${height}")

        // frames dir
        val outputDir = File(getExternalFilesDir(null), "captures")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Thread to avoid UI
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
                    processFrame(image!!, outputDir)
                    image?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando frame: ${e.message}")
                image?.close()
            }
            val currentTime = System.currentTimeMillis()

            // ⏱️ Control de FPS: solo procesar cada ~66ms (15 FPS)
            if (currentTime - lastFrameTime >= frameInterval) {
                lastFrameTime = currentTime

                val image = reader.acquireLatestImage()
                if (image != null) {
                    processFrame(image, outputDir)
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

    private fun processFrame(image: Image, outputDir: File) {
        try {
            //  Image to Bitmap
            val bitmap = imageToBitmap(image)

            // save as JPEG
            val file = File(outputDir, "frame_${frameCount}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            frameCount++

            if (frameCount % 15 == 0) {
                Log.d(TAG, "📸 Frames capturados: $frameCount")
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

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
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