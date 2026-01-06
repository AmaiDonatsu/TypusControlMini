package com.example.typuscontrolmini

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class CommandHandler {



    companion object {
        private const val TAG = "MCP_Handler"
        private val gson = Gson()
    }

    var onResponseCallback: ((String) -> Unit)? = null

    // 📜 Catálogo de Herramientas (El "Menú" para el Agente)
    private val availableTools = listOf(
        McpTool(
            name = "execute_tap",
            description = "Realiza un toque (tap) en la pantalla en las coordenadas (x, y).",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "x" to mapOf("type" to "number", "description" to "Coordenada X"),
                    "y" to mapOf("type" to "number", "description" to "Coordenada Y")
                ),
                "required" to listOf("x", "y")
            )
        ),
        McpTool(
            name = "input_text",
            description = "Escribe texto en el campo que tenga el foco actual. Requiere que el teclado o campo esté activo.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "text" to mapOf("type" to "string", "description" to "El texto a escribir")
                ),
                "required" to listOf("text")
            )
        ),
        McpTool(
            name = "get_screen_tree",
            description = "Obtiene la jerarquía de la interfaz (XML) actual para analizar botones y textos.",
            inputSchema = mapOf("type" to "object") // Sin parámetros
        )
    )

    // 🎯 Cerebro principal: Procesa el JSON-RPC
    fun handleCommand(message: String) {
        try {
            // 1. Parseamos la petición cruda
            // Usamos JSONObject para un primer pase rápido y seguro de tipos dinámicos
            val requestJson = JSONObject(message)
            
            // Verificamos si es un mensaje JSON-RPC válido (o al menos intentamos adaptarnos)
            val method = requestJson.optString("method")
            val id = requestJson.opt("id")
            val params = requestJson.optJSONObject("params")

            Log.d(TAG, "🤖 MCP Request: $method [ID: $id]")

            when (method) {
                // 🔍 Fase 1: Autodescubrimiento
                "tools/list" -> {
                    val result = mapOf("tools" to availableTools)
                    sendSuccess(id, result)
                }

                // 🛠️ Fase 2: Ejecución de Herramientas
                "tools/call" -> {
                    val toolName = params?.optString("name")
                    val args = params?.optJSONObject("arguments")
                    handleToolCall(id, toolName, args)
                }

                // 📚 Fase 3: Recursos (Preparado para el futuro)
                "resources/read" -> {
                    val uri = params?.optString("uri")
                    handleResourceRead(id, uri)
                }

                // 👻 Compatibilidad con tu sistema anterior (Legacy)
                "" -> {
                    if (requestJson.has("command")) {
                        Log.w(TAG, "⚠️ Detectado comando legacy. Redirigiendo...")
                        // Aquí podrías llamar a una función legacy si fuera necesario
                        // Por ahora solo logueamos, ya que estamos migrando a MCP
                        sendError(id, -32600, "Legacy commands not supported in this version. Use MCP.")
                    }

                }

                else -> sendError(id, -32601, "Method not found: $method")
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error crítico parsing JSON: ${e.message}")
            // Si no tenemos ID, no podemos responder, así que solo logueamos
        }
    }

    // 🏗️ Ejecutor de Herramientas
    private fun handleToolCall(id: Any?, toolName: String?, args: JSONObject?) {
        val service = DeviceControlService.getInstance()
        if (service == null) {
            sendError(id, -32000, "Accessibility Service not connected")
            return
        }

        when (toolName) {
            "execute_tap" -> {
                val x = args?.optDouble("x")?.toFloat() ?: 0f
                val y = args?.optDouble("y")?.toFloat() ?: 0f
                
                service.performTap(x, y) { success, element ->
                    if (success) {
                        // Respondemos con algo de contexto útil (qué se tocó)
                        val content = mapOf(
                            "status" to "success", 
                            "tapped_element_class" to (element?.className ?: "unknown")
                        )
                        sendToolResult(id, content)
                    } else {
                        sendToolError(id, "Tap failed dispatch")
                    }
                }
            }

            "input_text" -> {
                val text = args?.optString("text") ?: ""
                service.inputText(text) { success ->
                    if (success) {
                        sendToolResult(id, mapOf("status" to "text_injected", "text" to text))
                    } else {
                        // 🧠 Feedback inteligente para el Agente
                        sendToolError(id, "Failed to input text. Hint: Ensure an input field is refocused/clicked first.")
                    }
                }

            }
            
            "get_screen_tree" -> {
                 val hierarchy = service.getUIHierarchy()
                 // Enviamos el XML como texto dentro del resultado
                 sendToolResult(id, mapOf("xml_tree" to hierarchy))
            }

            else -> sendError(id, -32601, "Tool not found: $toolName")
        }

    }

    // 🔮 Manejador de Recursos (Fase 3 - Placeholder)
    private fun handleResourceRead(id: Any?, uri: String?) {
        val service = DeviceControlService.getInstance()
        if (uri == "mobile://screen/xml") {
             val hierarchy = service?.getUIHierarchy() ?: "<error>No service</error>"
             
             // Estructura de respuesta de Recurso MCP
             val resourceResponse = mapOf(
                 "contents" to listOf(
                     mapOf(
                         "uri" to uri,
                         "mimeType" to "application/xml",
                         "text" to hierarchy
                     )
                 )
             )
             sendSuccess(id, resourceResponse)
        } else {
            sendError(id, -32002, "Resource not found: $uri")
        }
    }

    // 📤 Helpers para Responder en JSON-RPC
    private fun sendSuccess(id: Any?, result: Any) {
        val response = McpResponse(id = id, result = result)
        val json = gson.toJson(response) // Usando Gson para serializar rápido
        onResponseCallback?.invoke(json)
    }

    // Para tools/call, el resultado va dentro de una estructura específica content/text
    private fun sendToolResult(id: Any?, data: Map<String, Any>) {
        val toolResult = mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to gson.toJson(data)
                )
            )
        )
        sendSuccess(id, toolResult)
    }

    private fun sendToolError(id: Any?, errorMessage: String) {
        val toolError = mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to "Error: $errorMessage"
                )
            ),
            "isError" to true
        )
        // Note: For tool errors (execution failure), we often still return a success JSON-RPC response
        // but with the tool's error content, UNLESS it's a protocol error.
        // However, standard MCP usually returns success with isError=true in the content.
        sendSuccess(id, toolError)
    }

    private fun sendError(id: Any?, code: Int, message: String) {
        val response = McpResponse(id = id, error = McpError(code, message))
        val json = gson.toJson(response)
        Log.e(TAG, "❌ Enviando Error RPC: $message")
        onResponseCallback?.invoke(json)
    }
}
