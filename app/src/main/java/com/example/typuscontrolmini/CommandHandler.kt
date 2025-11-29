package com.example.typuscontrolmini

import android.util.Log
import org.json.JSONObject
import org.json.JSONArray

class CommandHandler {

    companion object {
        private const val TAG = "CommandHandler"
    }

    // 🎯 Procesa el comando recibido
    fun handleCommand(commandJson: String, onResponse: (String) -> Unit) {
        try {
            val json = JSONObject(commandJson)
            val type = json.optString("type", "")

            if (type != "command") {
                Log.w(TAG, "⚠️ Mensaje no es un comando: $type")
                return
            }

            val command = json.getString("command")
            val commandId = json.optString("id", "") // track responses

            Log.d(TAG, "🎮 Procesando comando: $command")

            val service = DeviceControlService.getInstance()
            if (service == null) {
                Log.e(TAG, "❌ AccessibilityService no disponible")
                sendErrorResponse(commandId, "Service not available", onResponse)
                return
            }


            when (command) {
                "tap" -> {
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()

                    // send element taped
                    service.performTap(x, y) { success, element ->
                        if (success) {
                            val response = JSONObject().apply {
                                put("type", "response")
                                put("id", commandId)
                                put("status", "success")
                                put("message", "Tap ejecutado")


                                if (element != null) {
                                    val elementJson = JSONObject().apply {
                                        put("class", element.className)
                                        put("text", element.text)
                                        put("id", element.resourceId)
                                        put("description", element.contentDescription)
                                        // Agregamos info extra útil
                                        put("clickable", element.clickable)
                                        put("bounds", JSONObject().apply {
                                            put("x", element.x)
                                            put("y", element.y)
                                            put("w", element.width)
                                            put("h", element.height)
                                        })
                                    }
                                    put("tapped_element", elementJson)
                                }
                            }
                            Log.d(TAG, "✅ Tap exitoso con info de elemento: ${element?.className}")
                            onResponse(response.toString())
                        } else {
                            sendErrorResponse(commandId, "Tap falló", onResponse)
                        }
                    }
                }

                "press" -> {
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    val duration = json.optLong("duration", 500)

                    service.performPress(x, y, duration) { success ->
                        if (success) {
                            sendSuccessResponse(commandId, "Press ejecutado", onResponse)
                        } else {
                            sendErrorResponse(commandId, "Press falló", onResponse)
                        }
                    }
                }

                "swipe" -> {
                    val startX = json.getDouble("startX").toFloat()
                    val startY = json.getDouble("startY").toFloat()
                    val endX = json.getDouble("endX").toFloat()
                    val endY = json.getDouble("endY").toFloat()
                    val duration = json.optLong("duration", 300)

                    service.performSwipe(startX, startY, endX, endY, duration) { success ->
                        if (success) {
                            sendSuccessResponse(commandId, "Swipe ejecutado", onResponse)
                        } else {
                            sendErrorResponse(commandId, "Swipe falló", onResponse)
                        }
                    }
                }

                "inputText" -> {
                    val text = json.getString("text")
                    service.inputText(text) { success ->
                        if (success) {
                            sendSuccessResponse(commandId, "Texto escrito", onResponse)
                        } else {
                            sendErrorResponse(commandId, "Fallo al escribir texto (¿hay foco?)", onResponse)
                        }
                    }
                }

                "back" -> {
                    service.performBack { success ->
                        if (success) {
                            sendSuccessResponse(commandId, "Back ejecutado", onResponse)
                        } else {
                            sendErrorResponse(commandId, "Back falló", onResponse)
                        }
                    }
                }

                "home" -> {
                    service.performHome { success ->
                        if (success) {
                            sendSuccessResponse(commandId, "Home ejecutado", onResponse)
                        } else {
                            sendErrorResponse(commandId, "Home falló", onResponse)
                        }
                    }
                }

                "recents" -> {
                    service.performRecents { success ->
                        if (success) {
                            sendSuccessResponse(commandId, "Recents ejecutado", onResponse)
                        } else {
                            sendErrorResponse(commandId, "Recents falló", onResponse)
                        }
                    }
                }

                "getUI" -> {
                    val hierarchy = service.getUIHierarchy()

                    val response = JSONObject().apply {
                        put("type", "ui_data")
                        put("id", commandId)
                        put("hierarchy", JSONObject(hierarchy))
                    }

                    onResponse(response.toString())
                }

                "getElements" -> {
                    val elements = service.getInteractableElements()

                    val elementsArray = JSONArray()
                    elements.forEach { element ->
                        val elementJson = JSONObject().apply {
                            put("class", element.className)
                            put("text", element.text)
                            put("description", element.contentDescription)
                            put("id", element.resourceId)
                            put("clickable", element.clickable)
                            put("x", element.x)
                            put("y", element.y)
                            put("width", element.width)
                            put("height", element.height)
                        }
                        elementsArray.put(elementJson)
                    }

                    val response = JSONObject().apply {
                        put("type", "elements_data")
                        put("id", commandId)
                        put("elements", elementsArray)
                    }

                    onResponse(response.toString())
                }

                else -> {
                    Log.w(TAG, "⚠️ Comando desconocido: $command")
                    sendErrorResponse(commandId, "Unknown command: $command", onResponse)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando comando: ${e.message}")
            e.printStackTrace()
        }
    }

    // 📤 Enviar respuesta exitosa
    private fun sendSuccessResponse(commandId: String, message: String, onResponse: (String) -> Unit) {
        val response = JSONObject().apply {
            put("type", "response")
            put("id", commandId)
            put("status", "success")
            put("message", message)
        }

        Log.d(TAG, "✅ Enviando respuesta exitosa: $message")
        onResponse(response.toString())
    }

    // 📤 Enviar respuesta de error
    private fun sendErrorResponse(commandId: String, error: String, onResponse: (String) -> Unit) {
        val response = JSONObject().apply {
            put("type", "response")
            put("id", commandId)
            put("status", "error")
            put("error", error)
        }

        Log.e(TAG, "❌ Enviando respuesta de error: $error")
        onResponse(response.toString())
    }
}