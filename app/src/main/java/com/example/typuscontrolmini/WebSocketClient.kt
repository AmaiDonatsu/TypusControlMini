package com.example.typuscontrolmini
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(private val serverUrl: String) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var isConnected = false
    private var frameNumber = 0

    companion object {
        private const val TAG = "WebSocketClient"
    }

    fun connect(onConnected: () -> Unit = {}, onDisconnected: () -> Unit = {}) {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket conectado!")
                isConnected = true
                frameNumber = 0
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📨 Mensaje recibido: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "📨 Mensaje binario recibido: ${ bytes.size } bytes")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "⚠️ WebSocket cerrando: $code - $reason")
                webSocket.close(1000, null)
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "🔴 WebSocket cerrado: $code - $reason")
                isConnected = false
                onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ Error in WebSocket: ${t.message}")
                isConnected = false
                onDisconnected()
            }
        })
    }

    fun sendFrame(base64Data: String, width: Int, height: Int): Boolean {
        Log.d(TAG, "📤 Enviando frame...")

        if (!isConnected) {
            Log.w(TAG, "⚠️ No connected, cannot send frame")
            return false
        }

        try {
            val json = JSONObject().apply {
                put("type", "frame")
                put("timestamp", System.currentTimeMillis())
                put("frame_number", frameNumber++)
                put("data", base64Data)
                put("width", width)
                put("height", height)
            }

            val jsonStr = json.toString()
            Log.d(TAG, "📦 Preparando frame ${frameNumber}: ${jsonStr.length} caracteres")

            val sent = webSocket?.send(jsonStr) ?: false

            if (frameNumber % 15 == 0) {  // Log cada segundo
                Log.d(TAG, "📤 Frame $frameNumber send")
            }

            return sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame: ${e.message}")
            return false
        }
    }

    fun disconnect() {
        Log.d(TAG, "🛑 Close WebSocket...")
        webSocket?.close(1000, "Client disconnect")
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}