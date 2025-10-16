package com.example.typuscontrolmini
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartCapture: Button
    private lateinit var btnStopCapture: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button

    private lateinit var auth: FirebaseAuth
    private var userToken: String? = null
    private var apiKeys: List<String> = emptyList()
    private var apiKeysJson: List<JSONObject> = emptyList()

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var isCapturing: Boolean = false
    @RequiresApi(Build.VERSION_CODES.O)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "onActivityResult: ${result.resultCode}")
        Log.d("MainActivity", "onActivityResult: ${result.data}")

        if (result.resultCode == RESULT_OK && result.data != null) {
            tvStatus.text = "Estate: Connecting..."

            val serverUrl = "ws://10.0.2.2:8000/ws/stream"

            // MediaProjection
            val resultCode = result.resultCode
            val data = result.data

            val intent = Intent(this, ScreenCaptureService::class.java).apply  {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(ScreenCaptureService.EXTRA_SERVER_URL, serverUrl)
            }
            startForegroundService(intent)

            isCapturing = true
            updateButtons()
            tvStatus.text = "State: Streaming..."

            // TODO: Iniciar captura con estos datos
            Toast.makeText(this, "Streaming!", Toast.LENGTH_SHORT).show()

        } else {
            tvStatus.text = "Estado: Permiso denegado 😢 "
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        userToken = getFirebaseToken()

        btnStartCapture = findViewById(R.id.btnStartCapture)
        btnStopCapture = findViewById(R.id.btnStopCapture)
        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnStartCapture.setOnClickListener {
            startScreenCapture()
        }
        btnStopCapture.setOnClickListener {
            stopScreenCapture()
        }
        btnRefresh.setOnClickListener {
            getApiKeys()
        }

        updateButtons()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startScreenCapture() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)  //
    }

    @SuppressLint("SetTextI18n")
    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        isCapturing = false
        tvStatus.text = "State: Stopped ⏹️"
        updateButtons()
        Toast.makeText(this, "⏹️ Streaming stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtons() {
        btnStartCapture.isEnabled = !isCapturing
        btnStopCapture.isEnabled = isCapturing
    }

    private fun getFirebaseToken(): String? {
        var token: String? = null
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            user.getIdToken(true).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    token = task.result?.token
                    println("User token: $token")
                }
            }
        }
        return token
    }

    private fun getApiKeys() {
        val currentUser = auth.currentUser

        currentUser?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                val userId = currentUser.uid

                Thread {
                    try {
                        println("User ID: $userId")
                        println("Token: $token")
                        println("making URL: http://10.0.2.2:8000/keys/list_available")
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url("http://10.0.2.2:8000/keys/list_available")
                            .header("Authorization", "Bearer $token")
                            .header("Content-Type", "application/json")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                if (response.code == 401) {
                                    runOnUiThread {
                                        Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
                                    }
                                    println("Unauthorized")
                                    auth.signOut()
                                    startActivity(Intent(this, LoginActivity::class.java))
                                }
                            }

                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                println("Response body: $responseBody")
                                val jsonResponse = JSONObject(responseBody)
                                if (jsonResponse.getBoolean("success")) {
                                    val keysArray = jsonResponse.getJSONArray("keys")
                                    val keyNames = mutableListOf<String>()
                                    val keyObjects = mutableListOf<JSONObject>()
                                    for (i in 0 until keysArray.length()) {
                                        val keyObject = keysArray.getJSONObject(i)
                                        keyNames.add(keyObject.getString("name"))
                                        keyObjects.add(keyObject)
                                    }
                                    apiKeys = keyNames
                                    apiKeysJson = keyObjects
                                    runOnUiThread {
                                        Toast.makeText(this, "${apiKeys.size} keys loaded!", Toast.LENGTH_SHORT).show()
                                        println("API Keys: $apiKeys")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("Error al intentar obtener el token: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Error al obtener el token", Toast.LENGTH_SHORT).show()
                }
                println("Error al obtener el token")
            }
        }
    }
}