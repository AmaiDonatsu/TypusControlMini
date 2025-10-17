package com.example.typuscontrolmini

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
import android.util.Log

class LoginActivity: AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            Log.d(TAG, "User is already signed in with UID: ${auth.currentUser!!.uid}")
            auth.currentUser!!.getIdToken(true).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "User token already authenticated: ${task.result?.token}")
                } else {
                    Log.w(TAG, "Failed to get token.", task.exception)
                }
            }
            goToMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, ingrese un correo electrónico y una contraseña.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firebaseLogin(email, password)
        }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun firebaseLogin(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Inicio de sesión exitoso.", Toast.LENGTH_SHORT).show()
                    println("User ID: ${auth.currentUser!!.uid}")
                    println("user token auth: ${auth.currentUser!!.getIdToken(true)}")


                    goToMainActivity()
                } else {
                    val error = task.exception?.message ?: "Error desconocido."
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
    }
}