package com.example.typuscontrolmini
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class DeviceControlService: AccessibilityService() {
    companion object {
        private const val TAG = "DeviceControlService"
        private var instance: DeviceControlService? = null
        fun getInstance(): DeviceControlService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "✅ Accessibility Service conectado!")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos hacer nada aquí por ahora
    }
    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Servicio interrumpido")
    }
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "🔴 Servicio destruido")
    }

    fun performTap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "👆 Ejecutando tap en ($x, $y)")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ Tap completado")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "❌ Tap cancelado")
                callback?.invoke(false)
            }
        }, null)
    }

    fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "👆➡️ Ejecutando swipe de ($startX, $startY) a ($endX, $endY)")

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ Swipe completado")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "❌ Swipe cancelado")
                callback?.invoke(false)
            }
        }, null)
    }

    fun performBack(callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "⬅️ Ejecutando Back")
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        callback?.invoke(result)
    }
    fun performHome(callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "🏠 Ejecutando Home")
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        callback?.invoke(result)
    }
    fun performRecents(callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "📱 Ejecutando Recents")
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        callback?.invoke(result)
    }
}