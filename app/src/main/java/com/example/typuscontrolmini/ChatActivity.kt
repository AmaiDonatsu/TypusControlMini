package com.example.typuscontrolmini

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.typuscontrolmini.Services.CoreService
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize views
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        // Setup RecyclerView
        chatAdapter = ChatAdapter()
        rvMessages.adapter = chatAdapter
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Start filling from bottom
        }

        // Setup send button
        btnSend.setOnClickListener {
            val messageText = etMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                val service = CoreService.instance
                if (service != null) {
                    service.sendTextToSocket(messageText)
                    etMessage.text?.clear()
                } else {
                    Toast.makeText(this, "Servicio desconectado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe messages
        lifecycleScope.launch {
            ChatRepository.messages.collect { messages ->
                chatAdapter.submitList(messages) {
                    // Scroll to bottom when list is updated
                    if (messages.isNotEmpty()) {
                        rvMessages.smoothScrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }
}
