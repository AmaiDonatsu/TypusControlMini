package com.example.typuscontrolmini

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class KeyAdapter(private val apiKeys: List<JSONObject>) :
    RecyclerView.Adapter<KeyAdapter.KeyViewHolder>() {

    class KeyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvKeyName: TextView = itemView.findViewById(R.id.tvKeyName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.key_item, parent, false)
        return KeyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: KeyViewHolder, position: Int) {
        val key = apiKeys[position]
        holder.tvKeyName.text = key.getString("name")
    }

    override fun getItemCount() = apiKeys.size
}
