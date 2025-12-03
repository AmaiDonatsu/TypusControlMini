package com.example.typuscontrolmini
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

private val JSON = "application/json; charset=utf-8".toMediaType()

class ApiCall () {

    private val urlBase = "https://izabella-unpearled-clearly.ngrok-free.dev"
    private val updateUrl = "$urlBase/keys/update_availability"


     fun updateAvailability (keyId: String, isAvailable: Boolean, device: String, token: String?) {
        val client = OkHttpClient()

        val bodyJson = JSONObject().apply {
            put("id", keyId)
            put("is_available", isAvailable)
            put("device", device)
        }.toString()

        val request = Request.Builder()
            .url("${updateUrl}/$keyId")
            .header("Authorization", "Bearer $token")
            .put(bodyJson.toRequestBody(JSON))
            .build()


        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    println("Unauthorized")
                }
            }
            val responseBody = response.body?.string()
            if (responseBody != null) {
                println("Response body in updateAvailability: $responseBody")
                //val jsonResponse = JSONObject(responseBody)
            }
        }
    }
}