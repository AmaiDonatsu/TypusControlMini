package com.example.typuscontrolmini

import android.annotation.SuppressLint
//import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity() {
    private lateinit var btnStartCapture: Button
    private lateinit var btnStopCapture: Button
    private lateinit var tvStatus: TextView
    private lateinit var keySelected: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnClearSelection: ImageView
    private lateinit var rvKeys: RecyclerView
    private lateinit var keyAdapter: KeyAdapter
    private lateinit var auth: FirebaseAuth
    private var apiKeys: List<String> = emptyList()
    private var apiKeysJson: List<JSONObject> = emptyList()
    private var selectedKeyJson: JSONObject? = null
    
    private lateinit var cardAccessibilityWarning: MaterialCardView
    private lateinit var btnEnableAccessibility: MaterialButton



    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var isCapturing: Boolean = false
    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "onActivityResult: ${result.resultCode}")
        Log.d("MainActivity", "onActivityResult: ${result.data}")

        if (result.resultCode == RESULT_OK && result.data != null) {
            tvStatus.text = "State: Connecting to server..."

            // CORREGIDO: Usamos wss (Secure WebSocket) porque el dominio es https
            val serverUrl = AppConfig.WS_URL

            val keysData = selectedKeyJson?.getJSONObject("key")
            Log.d("MainActivity", "Key data: $keysData")
            val device = keysData?.optString("device", "No name")
            val secretKey = keysData?.optString("secretKey", "No secret key")


            // MediaProjection
            val resultCode = result.resultCode
            val data = result.data

            val intent = Intent(this, ScreenCaptureService::class.java).apply  {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(ScreenCaptureService.EXTRA_SERVER_URL, serverUrl)
                putExtra(ScreenCaptureService.EXTRA_DEVICE, device)
                putExtra(ScreenCaptureService.EXTRA_SECRET_KEY, secretKey)
            }
            startForegroundService(intent)

            isCapturing = true
            updateButtons()
            tvStatus.text = "State: Streaming..."

            // TODO: Init capture with this data
            Toast.makeText(this, "Streaming!", Toast.LENGTH_SHORT).show()

        } else {
            tvStatus.text = "State: permission denied 😢 "
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // init views PRIMERO
        btnStartCapture = findViewById(R.id.btnStartCapture)
        btnStopCapture = findViewById(R.id.btnStopCapture)
        tvStatus = findViewById(R.id.tvStatus)
        keySelected = findViewById(R.id.keySelected)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        btnRefresh = findViewById(R.id.btnRefresh)
        rvKeys = findViewById(R.id.rvKeys)
        cardAccessibilityWarning = findViewById(R.id.cardAccessibilityWarning)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)

        if (selectedKeyJson != null) {
            keySelected.text = "android"
        }

        // Configurar RecyclerView
        setupRecyclerView()

        // Configurar botones
        setupButtons()
        
        // Listeners adicionales
        btnEnableAccessibility.setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
        }

        // verify accessibility permission DESPUÉS de init views
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            showAccessibilityDialog()
            // Deshabilitar botones hasta que se active
            disableControls()
            updateAccessibilityWarningVisibility()
        }
        //

        // Cargar las API keys al inicio
        getApiKeys()

        getApiKeyWithDevice("android")
    }

    private fun updateAccessibilityWarningVisibility() {
        val isEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)

        if (isEnabled && cardAccessibilityWarning.visibility == View.VISIBLE) {
            // Fade out
            cardAccessibilityWarning.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    cardAccessibilityWarning.visibility = View.GONE
                    cardAccessibilityWarning.alpha = 1f
                }
        } else if (!isEnabled && cardAccessibilityWarning.visibility == View.GONE) {
            // Fade in
            cardAccessibilityWarning.alpha = 0f
            cardAccessibilityWarning.visibility = View.VISIBLE
            cardAccessibilityWarning.animate()
                .alpha(1f)
                .setDuration(300)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()

        if (AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            enableControls()
            updateAccessibilityWarningVisibility()
        } else if (!supportFragmentManager.findFragmentByTag(AccessibilityPermissionDialog.TAG)?.isVisible!!) {
            disableControls()
            updateAccessibilityWarningVisibility()
        }
    }
    //

    // verify
    private fun showAccessibilityDialog() {
        if (supportFragmentManager.findFragmentByTag(AccessibilityPermissionDialog.TAG)?.isVisible == true) return
        
        AccessibilityPermissionDialog.newInstance()
            .show(supportFragmentManager, AccessibilityPermissionDialog.TAG)
    }

    private fun disableControls() {
        if (::btnStartCapture.isInitialized) {
            btnStartCapture.isEnabled = false
            btnStartCapture.alpha = 0.5f
        }
        if (::tvStatus.isInitialized) {
            tvStatus.text = "Estado: Esperando permisos..."
        }
    }

    private fun enableControls() {
        if (::btnStartCapture.isInitialized) {
            updateButtons()
            btnStartCapture.alpha = 1.0f
        }

        if (::tvStatus.isInitialized) {
            tvStatus.text = "Estado: Listo ✅"
        }
    }

    //

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecyclerView() {
        rvKeys.layoutManager = LinearLayoutManager(this)
        keyAdapter = KeyAdapter(apiKeysJson, auth) {
            getApiKeys()
            getApiKeyWithDevice(android.os.Build.MODEL)
        }
        rvKeys.adapter = keyAdapter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupButtons() {
        btnStartCapture.setOnClickListener {
            startScreenCapture()
        }

        btnStopCapture.setOnClickListener {
            stopScreenCapture()
        }

        btnRefresh.setOnClickListener {
            getApiKeys()
            getApiKeyWithDevice(android.os.Build.MODEL)
        }

        updateButtons()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startScreenCapture() {
        // verify
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(
                this,
                "⚠️ Activa el servicio de accesibilidad primero",
                Toast.LENGTH_LONG
            ).show()
            showAccessibilityDialog()
            return
        }
        if (selectedKeyJson == null) {
            Toast.makeText(
                this,
                "⚠️ Selecciona un dispositivo primero",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        //

        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)  //
        updateButtons()
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

    private fun getApiKeys() {
        val currentUser = auth.currentUser

        currentUser?.getIdToken(false)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                val userId = currentUser.uid

                Thread {
                    try {
                        println("User ID: $userId")
                        println("Token to send: $token")
                        println("making URL: ${AppConfig.BASE_URL}/keys/list_available")
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url("${AppConfig.BASE_URL}/keys/list_available")
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
                                    //auth.signOut()
                                    //startActivity(Intent(this, LoginActivity::class.java))
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
                                        // Toast.makeText(this, "${apiKeys.size} keys loaded!", Toast.LENGTH_SHORT).show()
                                        println("API Keys: $apiKeys")
                                        // Actualizar el adapter manteniendo el callback
                                        rvKeys.adapter = KeyAdapter(apiKeysJson, auth) {
                                            getApiKeys()
                                            getApiKeyWithDevice(android.os.Build.MODEL)
                                        }
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

    private fun getApiKeyWithDevice(device: String) {
        val currentUser = auth.currentUser
        currentUser?.getIdToken(false)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                
                // Encode the device name to handle spaces and special characters safely in the URL
                val encodedDevice = Uri.encode(device)
                val url = "${AppConfig.BASE_URL}/keys/get_by_device/$encodedDevice"

                Thread {
                    try {
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $token")
                            .header("Content-Type", "application/json")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                when (response.code) {
                                    401 -> {
                                        runOnUiThread {
                                            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    404 -> {
                                        runOnUiThread {
                                            // Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show()
                                            keySelected.text = "Ninguno"
                                            btnClearSelection.visibility = View.GONE
                                            selectedKeyJson = null
                                        }
                                    }
                                    else -> {
                                        println("error getting key with device: ${response.code}")
                                        runOnUiThread {
                                            Toast.makeText(this, "Error: ${response.code}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                return@use // Exit the use block early on failure
                            }

                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                println("Response body selected key: $responseBody")
                                val jsonResponse = JSONObject(responseBody)
                                selectedKeyJson = jsonResponse

                                runOnUiThread {
                                    try {
                                        val keysData = selectedKeyJson?.getJSONObject("key")
                                        val deviceName = keysData?.optString("device", "No name")
                                        keySelected.text = deviceName

                                        if (!deviceName.isNullOrEmpty() && deviceName != "Ninguno" && deviceName != "No name") {
                                            btnClearSelection.visibility = View.VISIBLE
                                            btnClearSelection.setOnClickListener {
                                                val keyId = keysData?.optString("id")
                                                if (keyId != null) {
                                                    deselectKey(keyId)
                                                }
                                            }
                                        } else {
                                            btnClearSelection.visibility = View.GONE
                                        }

                                    } catch (e: Exception) {
                                        println("Error al intentar obtener el key mediante device: ${e.message}")
                                    }
                                }


                            } else {
                                println("Response body is null")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        println("Error al intentar obtener el key mediante device: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            } else {
                println("Error al obtener el token")
                runOnUiThread {
                    Toast.makeText(this, "Error al obtener el token", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deselectKey(keyId: String) {
        val currentUser = auth.currentUser
        currentUser?.getIdToken(false)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                Thread {
                    // Reuse ApiCall logic
                    ApiCall().updateAvailability(keyId, true, "", token)
                    runOnUiThread {
                        Toast.makeText(this, "Key liberada", Toast.LENGTH_SHORT).show()
                        keySelected.text = "Ninguno"
                        btnClearSelection.visibility = View.GONE
                        selectedKeyJson = null
                        // Refresh list to show it's available again
                        getApiKeys()
                    }
                }.start()
            }
        }
    }

}
