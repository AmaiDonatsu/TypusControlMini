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


class KeyAdapter(private val apiKeys: List<JSONObject>, private val auth: FirebaseAuth) :
    RecyclerView.Adapter<KeyAdapter.KeyViewHolder>() {
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
        val keyName = key.getString("name")
        val keyId = key.getString("id")

        holder.tvKeyName.text = keyName
        holder.tvKeyId.text = keyId

        holder.btnSelectKey.setOnClickListener {
            selectKey("$keyId", holder)
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
                    //val token = currentUser!!.getIdToken(false).toString()
                    apiCall.updateAvailability(key, false, "android", token)
                }.start()
            }
        }



        Toast.makeText(
            holder.itemView.context,
            "ID: $key",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun getItemCount() = apiKeys.size
}
