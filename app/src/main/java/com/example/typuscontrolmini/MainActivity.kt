package com.example.typuscontrolmini
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.net.URL
import java.net.HttpURLConnection

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartCapture: Button
    private lateinit var btnStopCapture: Button
    private lateinit var tvStatus: TextView
    private lateinit var etServerUrl: EditText

    private lateinit var auth: FirebaseAuth
    private var userToken: String? = null

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

            val serverUrl = etServerUrl.text.toString().ifEmpty {
                "ws://10.0.2.2:8000/ws/stream"
            }

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

        btnStartCapture = findViewById(R.id.btnStartCapture)
        btnStopCapture = findViewById(R.id.btnStopCapture)
        tvStatus = findViewById(R.id.tvStatus)
        etServerUrl = findViewById(R.id.etServerUrl)
        btnStartCapture.setOnClickListener {
            startScreenCapture()
        }
        btnStopCapture.setOnClickListener {
            stopScreenCapture()
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
        etServerUrl.isEnabled = !isCapturing
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
                        val url = URL("http://10.0.2.2:8000/keys/list_available")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"

                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")

                        val responseCode = connection.responseCode
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val inputStream = connection.inputStream
                            val response = inputStream.bufferedReader().use { it.readText() }


                            runOnUiThread {
                                Toast.makeText(this, "Respuesta: $response", Toast.LENGTH_LONG).show()
                                println("API Response: $response")
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "Error: $responseCode", Toast.LENGTH_SHORT).show()
                            }
                        }

                        connection.disconnect()

                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        }


    }




