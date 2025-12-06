package com.example.typuscontrolmini

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth


class KeyAdapter(
    private val apiKeys: List<JSONObject>,
    private val auth: FirebaseAuth,
    private val onKeyUpdated: () -> Unit
) : RecyclerView.Adapter<KeyAdapter.KeyViewHolder>() {
    val apiCall = ApiCall()

    class KeyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvKeyName: TextView = itemView.findViewById(R.id.tvKeyName)
        val tvKeyId: TextView = itemView.findViewById(R.id.tvKeyId)
        val btnSelectKey: Button = itemView.findViewById(R.id.SelectKeyButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.key_item, parent, false)
        return KeyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: KeyViewHolder, position: Int) {
        val key = apiKeys[position]
        // MEJORA: Usar optString para evitar crash si faltan campos
        val keyName = key.optString("name", "Unknown Name")
        val keyId = key.optString("id", "No ID")

        holder.tvKeyName.text = keyName
        holder.tvKeyId.text = keyId

        // Click normal: Seleccionar (Reclamar) la key
        holder.btnSelectKey.setOnClickListener {
            selectKey(keyId, holder)
        }

        // Click largo: Deseleccionar (Liberar) la key
        holder.btnSelectKey.setOnLongClickListener {
            deselectKey(keyId, holder)
            true // Consumir el evento
        }
    }

    private fun selectKey(key: String, holder: KeyViewHolder) {
        println("Selected key ID: $key")

        val currentUser = auth.currentUser
        currentUser?.getIdToken(false)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                println("Token to send to updateAvailability: $token")
                Thread {
                    // isAvailable = false (Ocupado), device = Modelo actual
                    apiCall.updateAvailability(key, false, android.os.Build.MODEL, token)
                    
                    // Ejecutar en UI thread después de que termine la llamada a la API
                    holder.itemView.post {
                        Toast.makeText(holder.itemView.context, "Key seleccionada: $key", Toast.LENGTH_SHORT).show()
                        onKeyUpdated() // Notificar a la actividad para refrescar
                    }
                }.start()
            }
        }
    }

    private fun deselectKey(key: String, holder: KeyViewHolder) {
        println("Deselecting key ID: $key")
        val currentUser = auth.currentUser
        currentUser?.getIdToken(false)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token
                println("Token to send to updateAvailability: $token")
                Thread {
                    // MEJORA: isAvailable = true (Disponible), device = "" (Vacio)
                    apiCall.updateAvailability(key, true, "", token)
                    
                    holder.itemView.post {
                        Toast.makeText(holder.itemView.context, "Key liberada: $key", Toast.LENGTH_SHORT).show()
                        onKeyUpdated() // También refrescar al liberar (opcional pero consistente)
                    }
                }.start()
            }
        }
    }

    override fun getItemCount() = apiKeys.size
}
