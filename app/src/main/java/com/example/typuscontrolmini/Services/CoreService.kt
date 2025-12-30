package com.example.typuscontrolmini.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.typuscontrolmini.AppConfig
import com.example.typuscontrolmini.ChatActivity
import com.example.typuscontrolmini.ChatRepository
import com.example.typuscontrolmini.CommandHandler
import com.example.typuscontrolmini.R
import com.example.typuscontrolmini.WebSocketClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CoreService : Service() {

    private val CHANNEL_ID = "core_channel"
    private val NOTIFICATION_ID = 1

    private lateinit var webSocketClient: WebSocketClient
    private lateinit var commandHandler: CommandHandler
    private lateinit var firebaseAuth: FirebaseAuth

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        var instance: CoreService? = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a started service, not a bound service
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("CoreService", "CoreService created")
        createNotificationChannel()

        firebaseAuth = FirebaseAuth.getInstance()
        webSocketClient = WebSocketClient(AppConfig.WS_URL)
        commandHandler = CommandHandler()

        // Set up command handler's response callback to send messages via WebSocket
        commandHandler.onResponseCallback = { response ->
            webSocketClient.sendResponse(response)
        }

        // Set up WebSocket onMessage callback
        webSocketClient.setOnCommandReceived { message ->
            serviceScope.launch {
                Log.d("CoreService", "Received message from WebSocket: $message")
                ChatRepository.addMessage(message, false) // Save AI message
                commandHandler.handleCommand(message)
                updateNotification("Nuevo comando: $message") // Update notification with command preview
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CoreService", "CoreService started")
        startForegroundService()
        connectWebSocket()
        return START_STICKY // Ensures the system tries to recreate the service if it gets killed
    }

    private fun connectWebSocket() {
        // Placeholder values for device and secretKey.
        // In a real app, these would be retrieved securely (e.g., from preferences or a config file).
        // For now, hardcoding for demonstration.
        val deviceId = "android_device" // Replace with actual device ID logic
        val secretKey = "your_secret_key" // Replace with actual secret key logic

        if (!webSocketClient.isConnected()) {
            webSocketClient.connect(
                onConnected = {
                    Log.d("CoreService", "WebSocket Connected!")
                    updateNotification("Conectado al servidor.")
                },
                onDisconnected = {
                    Log.d("CoreService", "WebSocket Disconnected!")
                    updateNotification("Desconectado del servidor.")
                },
                auth = firebaseAuth,
                device = deviceId,
                secretKey = secretKey
            )
        }
    }

    fun sendTextToSocket(text: String) {
        serviceScope.launch {
            if (webSocketClient.sendResponse(text)) { // Using sendResponse for sending user messages
                ChatRepository.addMessage(text, true) // Save user message
            } else {
                Log.e("CoreService", "Failed to send message via WebSocket.")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Typus Core"
            val descriptionText = "Channel for TypusControl Core Service"
            val importance = NotificationManager.IMPORTANCE_HIGH // Increased importance for Bubbles
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true) // Explicitly allow bubbles
                }
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        // Initial simple notification to satisfy Foreground Service requirement immediately
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TypusControl Activo")
            .setContentText("Esperando comandos...")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(content: String) {
        val context = this
        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        // A. Create the Person
        val person = Person.Builder()
            .setName("IA")
            .setIcon(icon)
            .setBot(true)
            .build()

        // B. Create the Dynamic Shortcut
        val shortcutId = "chat_shortcut"
        val intent = Intent(context, ChatActivity::class.java).apply {
            action = Intent.ACTION_VIEW
        }

        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel("Control IA")
            .setLongLabel("Chat de Control IA")
            .setIcon(icon)
            .setIntent(intent)
            .setPerson(person)
            .setLongLived(true)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

        // C. Create PendingIntent for the Bubble
        val bubbleIntent = Intent(context, ChatActivity::class.java)
        val bubblePendingIntent = PendingIntent.getActivity(
            context,
            0,
            bubbleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // D. Create BubbleMetadata
        val bubbleData = NotificationCompat.BubbleMetadata.Builder(bubblePendingIntent, icon)
            .setDesiredHeight(600)
            .setAutoExpandBubble(false)
            .setSuppressNotification(false)
            .build()

        // E. Build the Notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Control IA")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setBubbleMetadata(bubbleData)
            .setShortcutId(shortcutId)
            .addPerson(person)
            .setStyle(NotificationCompat.MessagingStyle(person)
                .addMessage(NotificationCompat.MessagingStyle.Message(
                    content,
                    System.currentTimeMillis(),
                    person
                ))
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("CoreService", "CoreService destroyed")
        webSocketClient.disconnect()
        serviceJob.cancel() // Cancel all coroutines started by serviceScope
    }
}

