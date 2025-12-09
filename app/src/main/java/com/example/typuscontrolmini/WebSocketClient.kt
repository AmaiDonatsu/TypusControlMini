package com.example.typuscontrolmini
import android.util.Log
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private val JSON = "application/json; charset=utf-8".toMediaType()

class WebSocketClient(private val serverUrl: String) {

    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var isConnected = false
    private var frameNumber = 0

    private var onCommandReceived: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "DEBUG_WS"
    }

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
        Log.d(TAG, "🚀 Iniciando proceso de conexión a: $serverUrl")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "❌ Error Fatal: No hay usuario logueado en Firebase (currentUser es null)")
            return
        }

        currentUser.getIdToken(true).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                // Log parcial del token para verificar sin exponer todo
                val tokenPreview = token?.take(10) + "..." + token?.takeLast(5)
                Log.d(TAG, "🔑 Token obtenido: $tokenPreview")
                Log.d(TAG, "📱 Dispositivo: $device | SecretKey length: ${secretKey.length}")

                val wsUrl = "$serverUrl?token=$token&secretKey=$secretKey&device=$device"

                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                Log.d(TAG, "🌐 Abriendo WebSocket...")

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "✅ ✅ ON_OPEN: WebSocket conectado exitosamente! Code: ${response.code}")
                        isConnected = true
                        frameNumber = 0
                        onConnected()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "📨 ON_MESSAGE (Texto): $text")
                        try {
                            val jsonMessage = JSONObject(text)
                            if (jsonMessage.has("type") && jsonMessage.getString("type") == "ping") {
                                Log.d(TAG, "❤️ Recibido PING del servidor, respondiendo PONG...")

                                val pongResponse = JSONObject().apply {
                                    put("type", "pong")
                                    if (jsonMessage.has("sequence")) {
                                        put("sequence", jsonMessage.get("sequence"))
                                    }
                                }
                                webSocket.send(pongResponse.toString())
                                return
                            }
                            onCommandReceived?.invoke(text)

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error procesando mensaje de texto: ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        Log.d(TAG, "📨 ON_MESSAGE (Binario): Recibidos ${bytes.size} bytes")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.w(TAG, "⚠️ ON_CLOSING: El servidor quiere cerrar. Código: $code, Razón: $reason")
                        webSocket.close(1000, null)
                        isConnected = false
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.w(TAG, "🛑 ON_CLOSED: Conexión cerrada completamente. Código: $code, Razón: $reason")
                        isConnected = false
                        onDisconnected()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "❌ ❌ ON_FAILURE: Error crítico en WebSocket!")
                        Log.e(TAG, "➡️ Exception: ${t.message}")
                        t.printStackTrace()
                        if (response != null) {
                            Log.e(TAG, "➡️ Response Code: ${response.code}")
                            Log.e(TAG, "➡️ Response Message: ${response.message}")
                            try {
                                Log.e(TAG, "➡️ Response Body: ${response.body?.string()}")
                            } catch (e: Exception) {
                                Log.e(TAG, "No se pudo leer el body de error")
                            }
                        }
                        isConnected = false
                        onDisconnected()
                    }
                })
            } else {
                Log.e(TAG, "❌ Error obteniendo token de Firebase: ${task.exception?.message}")
                Log.e(TAG, "💡 SUGERENCIA: Intenta hacer Logout y volver a Loguearte, o borrar datos de la app.")
            }
        }
    }

    fun sendFrame(frameBytes: ByteArray): Boolean {
        if (!isConnected) {
            // Reducimos log spam, solo logueamos una vez cada tanto si está desconectado
            if (frameNumber % 30 == 0) Log.w(TAG, "⚠️ Intento de enviar frame pero NO hay conexión")
            return false
        }

        try {
            frameNumber++
            // Loguear solo frames clave para no saturar, pero ver que fluye
            if (frameNumber % 30 == 0 || frameNumber == 1) {
                Log.d(TAG, "📤 Enviando Frame #$frameNumber (${frameBytes.size} bytes)")
            }

            val sent = webSocket?.send(ByteString.of(*frameBytes)) ?: false

            if (!sent) {
                 Log.e(TAG, "❌ send() retornó false para frame #$frameNumber")
            }

            return sent
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al enviar frame #$frameNumber: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun sendResponse(response: String): Boolean {
        Log.d(TAG, "📤 Enviando respuesta JSON: $response")
        return try {
            val sent = webSocket?.send(response) ?: false
            if (!sent) Log.e(TAG, "❌ Falló el envío de la respuesta JSON")
            sent
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción enviando respuesta JSON: ${e.message}")
            false
        }
    }

    fun disconnect() {
        Log.d(TAG, "🛑 Solicitando desconexión manual (disconnect())...")
        try {
            webSocket?.close(1000, "App closed connection")
            isConnected = false
        } catch (e: Exception) {
            Log.e(TAG, "Error durante disconnect: ${e.message}")
        }
    }

    fun isConnected(): Boolean = isConnected

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket necesita timeout infinito o 0
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
