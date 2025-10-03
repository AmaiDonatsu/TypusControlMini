package com.example.typuscontrolmini
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

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartCapture: Button
    private lateinit var btnStopCapture: Button
    private lateinit var tvStatus: TextView

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var isCapturing: Boolean = false


    //
    @RequiresApi(Build.VERSION_CODES.O)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "onActivityResult: ${result.resultCode}")
        Log.d("MainActivity", "onActivityResult: ${result.data}")

        if (result.resultCode == RESULT_OK && result.data != null) {
            tvStatus.text = "Estado: ¡Permiso concedido! 🎉"

            // MediaProjection en la Fase 2
            val resultCode = result.resultCode
            val data = result.data

            val intent = Intent(this, ScreenCaptureService::class.java).apply  {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }

            // 🔍 DEBUG
            Log.d("MainActivity", "Enviando al service - resultCode: ${result.resultCode}")
            Log.d("MainActivity", "Enviando al service - data: ${result.data}")

            startForegroundService(intent)
            isCapturing = true
            updateButtons()

            // TODO: Iniciar captura con estos datos
            Toast.makeText(this, "¡Listo para capturar!", Toast.LENGTH_SHORT).show()

        } else {
            tvStatus.text = "Estado: Permiso denegado 😢"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartCapture = findViewById(R.id.btnStartCapture)
        btnStopCapture = findViewById(R.id.btnStopCapture)
        tvStatus = findViewById(R.id.tvStatus)

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

    private fun stopScreenCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        isCapturing = false
        tvStatus.text = "Estado: Captura detenida"
        updateButtons()

        // TODO: Detener la captura con estos datos
        Toast.makeText(this, "Captura detenida", Toast.LENGTH_SHORT).show()

    }

    private fun updateButtons() {
        btnStartCapture.isEnabled = !isCapturing
        btnStopCapture.isEnabled = isCapturing
    }


}

