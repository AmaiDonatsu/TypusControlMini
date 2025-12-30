package com.example.typuscontrolmini

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar WindowManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 2. Inflar el diseño de la burbuja desde XML
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_chat_head, null)

        // 3. Definir los parámetros de diseño para la ventana flotante
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Permite tocar fuera de la burbuja
            PixelFormat.TRANSLUCENT
        )

        // Posición inicial
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // 4. Añadir la vista al WindowManager
        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
