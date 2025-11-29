package com.example.typuscontrolmini
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper

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

    // Modificado: Ahora el callback recibe (Success, Element?)
    fun performTap(x: Float, y: Float, callback: ((Boolean, UIElement?) -> Unit)? = null) {
        Log.d(TAG, "👆 Ejecutando tap en ($x, $y)")

        // 1. Intentar encontrar qué hay en esas coordenadas ANTES del tap
        var tappedElement: UIElement? = null
        val root = rootInActiveWindow
        if (root != null) {
            tappedElement = findNodeAt(root, x.toInt(), y.toInt())
            root.recycle()
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ Tap completado")
                // Pasamos el elemento encontrado
                callback?.invoke(true, tappedElement)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "❌ Tap cancelado")
                callback?.invoke(false, null)
            }
        }, null)
    }

    // Lógica para encontrar el nodo más profundo en (X, Y)
    private fun findNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): UIElement? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (!bounds.contains(x, y)) {
            return null
        }

        // Buscamos en los hijos primero (para encontrar el más específico/profundo)
        // Iteramos al revés (de último a primero) asumiendo que los últimos dibujados están "arriba"
        for (i in node.childCount - 1 downTo 0) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeAt(child, x, y)
                child.recycle() // Importante reciclar el hijo
                if (found != null) {
                    return found
                }
            }
        }

        // Si llegamos aquí, este nodo contiene el punto y sus hijos no (o no tiene hijos)
        return UIElement(
            className = node.className?.toString() ?: "",
            text = node.text?.toString() ?: "",
            contentDescription = node.contentDescription?.toString() ?: "",
            resourceId = node.viewIdResourceName ?: "",
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            x = bounds.centerX(),
            y = bounds.centerY(),
            width = bounds.width(),
            height = bounds.height()
        )
    }

    fun performPress(x: Float, y: Float, duration: Long, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "👆⏳ Ejecutando pulsación en ($x, $y) por ${duration}ms")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✅ Pulsación completada")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "❌ Pulsación cancelada")
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

    fun inputText(text: String, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "⌨️ Iniciando intento de escribir texto: $text")
        // Iniciamos con 5 reintentos (aprox 1 segundo de persistencia)
        retryInputText(text, 5, callback)
    }

    private fun retryInputText(text: String, retriesLeft: Int, callback: ((Boolean) -> Unit)?) {
        val rootNode = rootInActiveWindow
        
        // Si no hay ventana, reintentar si quedan intentos
        if (rootNode == null) {
            if (retriesLeft > 0) {
                Log.w(TAG, "⚠️ No hay ventana activa, reintentando ($retriesLeft)...")
                Handler(Looper.getMainLooper()).postDelayed({
                    retryInputText(text, retriesLeft - 1, callback)
                }, 200)
            } else {
                Log.e(TAG, "❌ No hay ventana activa tras reintentos")
                callback?.invoke(false)
            }
            return
        }

        // Buscar foco
        val focus = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null && focus.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "✅ Texto establecido: $result")
            focus.recycle()
            rootNode.recycle()
            callback?.invoke(result)
        } else {
            // Si no hay foco, liberar nodo y reintentar
            rootNode.recycle()
            if (retriesLeft > 0) {
                Log.d(TAG, "⏳ Foco no encontrado, reintentando en 200ms ($retriesLeft)...")
                Handler(Looper.getMainLooper()).postDelayed({
                    retryInputText(text, retriesLeft - 1, callback)
                }, 200)
            } else {
                Log.w(TAG, "⚠️ No se encontró campo de texto con foco tras reintentos")
                callback?.invoke(false)
            }
        }
    }

    fun getUIHierarchy(): String {
        val rootNode = rootInActiveWindow

        if (rootNode == null) {
            Log.w(TAG, "⚠️ No hay ventana activa")
            return "{\"error\": \"No active window\"}"
        }

        val hierarchy = buildHierarchyJSON(rootNode)
        rootNode.recycle() // Importante: liberar memoria

        return hierarchy
    }

    private fun buildHierarchyJSON(node: AccessibilityNodeInfo, depth: Int = 0): String {
        val json = StringBuilder()
        json.append("{\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"class\": \"${node.className}\",\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"text\": \"${node.text ?: ""}\",\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"contentDescription\": \"${node.contentDescription ?: ""}\",\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"viewIdResourceName\": \"${node.viewIdResourceName ?: ""}\",\n")

        // Propiedades útiles
        json.append("  ".repeat(depth + 1))
        json.append("\"clickable\": ${node.isClickable},\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"enabled\": ${node.isEnabled},\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"focusable\": ${node.isFocusable},\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"scrollable\": ${node.isScrollable},\n")

        // Bounds (coordenadas en pantalla)
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        json.append("  ".repeat(depth + 1))
        json.append("\"bounds\": {")
        json.append("\"left\": ${bounds.left}, \"top\": ${bounds.top}, ")
        json.append("\"right\": ${bounds.right}, \"bottom\": ${bounds.bottom}")
        json.append("},\n")

        json.append("  ".repeat(depth + 1))
        json.append("\"children\": [\n")

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                json.append("  ".repeat(depth + 2))
                json.append(buildHierarchyJSON(child, depth + 2))
                if (i < childCount - 1) json.append(",")
                json.append("\n")
                child.recycle()
            }
        }

        json.append("  ".repeat(depth + 1))
        json.append("]\n")

        json.append("  ".repeat(depth))
        json.append("}")

        return json.toString()
    }

    fun getInteractableElements(): List<UIElement> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UIElement>()

        findInteractableElements(rootNode, elements)
        rootNode.recycle()

        return elements
    }

    private fun findInteractableElements(
        node: AccessibilityNodeInfo,
        elements: MutableList<UIElement>
    ) {
        // Si es clickable o tiene texto, es interesante
        if (node.isClickable || node.text?.isNotEmpty() == true) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)

            elements.add(UIElement(
                className = node.className?.toString() ?: "",
                text = node.text?.toString() ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                resourceId = node.viewIdResourceName ?: "",
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                x = bounds.centerX(),
                y = bounds.centerY(),
                width = bounds.width(),
                height = bounds.height()
            ))
        }

        // Recursión en hijos
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findInteractableElements(child, elements)
                child.recycle()
            }
        }
    }

}

data class UIElement(
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val clickable: Boolean,
    val scrollable: Boolean,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)