package com.example.typuscontrolmini

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

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

        // 5. Add touch listener for drag and click functionality
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var lastAction: Int = MotionEvent.ACTION_UP
            private var clickStartTime: Long = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastAction = MotionEvent.ACTION_DOWN
                        clickStartTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        lastAction = MotionEvent.ACTION_MOVE
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val clickDuration = System.currentTimeMillis() - clickStartTime
                        val distanceX = Math.abs(event.rawX - initialTouchX)
                        val distanceY = Math.abs(event.rawY - initialTouchY)
                        
                        // Check if it was a click (short duration and minimal movement)
                        if (clickDuration < 200 && distanceX < 10 && distanceY < 10) {
                            openChatActivity()
                        }
                        lastAction = MotionEvent.ACTION_UP
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun openChatActivity() {
        val intent = Intent(this, ChatActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
