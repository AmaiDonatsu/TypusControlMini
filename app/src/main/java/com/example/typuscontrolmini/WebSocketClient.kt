package com.example.typuscontrolmini
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON = "application/json; charset=utf-8".toMediaType()

class WebSocketClient(private val serverUrl: String) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var isConnected = false
    private var frameNumber = 0

    // 🆕 NUEVO: Callback para recibir comandos
    private var onCommandReceived: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "WebSocketClient"
    }

    // 🆕 NUEVO: Método para setear el callback
    fun setOnCommandReceived(callback: (String) -> Unit) {
        this.onCommandReceived = callback
    }

    fun connect(
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {},
        auth: FirebaseAuth,
        device: String,
        secretKey: String
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "❌ No user logged in")
            return
        }

        currentUser.getIdToken(false).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                Log.d(TAG, "Token to send: $token")

                val wsUrl = "$serverUrl?token=$token&secretKey=$secretKey&device=$device"

                val request = Request.Builder()
                    .url(wsUrl)
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
                        // 🆕 MODIFICADO: Procesar el comando
                        onCommandReceived?.invoke(text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        Log.d(TAG, "📨 Mensaje binario recibido: ${bytes.size} bytes")
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
            } else {
                Log.e(TAG, "❌ Error getting token: ${task.exception?.message}")
            }
        }
    }

    fun sendFrame(frameBytes: ByteArray): Boolean {
        Log.d(TAG, "📤 Enviando frame...")

        if (!isConnected) {
            Log.w(TAG, "⚠️ No connected, cannot send frame")
            return false
        }

        try {
            frameNumber++
            Log.d(TAG, "📦 Preparando frame ${frameNumber}: ${frameBytes.size} bytes")

            val sent = webSocket?.send(ByteString.of(*frameBytes)) ?: false

            if (frameNumber % 15 == 0) {
                Log.d(TAG, "📤 Frame $frameNumber enviado (${frameBytes.size} bytes)")
            }

            return sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame: ${e.message}")
            return false
        }
    }

    // 🆕 NUEVO: Método para enviar respuestas/confirmaciones
    fun sendResponse(response: String): Boolean {
        return webSocket?.send(response) ?: false
    }

    fun disconnect() {
        Log.d(TAG, "🛑 Close WebSocket...")
        webSocket?.close(1000, "Client disconnect")
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}
