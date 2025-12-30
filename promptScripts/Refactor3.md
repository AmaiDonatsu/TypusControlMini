Objetivo General de la Fase 3
Configurar el sistema para que la notificación permanente del CoreService tenga la capacidad de "inflarse" en una ventana flotante (Bubble) cuando el usuario lo desee o cuando llegue un comando importante.

📋 Script Prompts (Copiar y Pegar)
📝 Prompt 1: Configuración del Manifiesto (La Identidad)
Para ser una burbuja, la actividad debe tener permisos especiales de redimensionado.

Markdown

@Contexto:
Lee: app/src/main/AndroidManifest.xml

@Instrucción:
Actualiza la definición de la `ChatActivity` en el AndroidManifest.xml con los atributos necesarios para Bubbles.

Cambios requeridos en el tag <activity android:name=".ChatActivity" ... >:
1. `android:allowEmbedded="true"`
2. `android:documentLaunchMode="always"`
3. `android:resizeableActivity="true"` (Esto es CRÍTICO).
4. `android:windowSoftInputMode="adjustResize"` (Para que el teclado no rompa la burbuja).
5. Mantén `android:exported="true"` si ya estaba, o ponlo en true.
   🛠️ Prompt 2: Utilidades de Burbuja (Shortcuts & Metadata)
   Android 11+ exige que una burbuja esté vinculada a un "Shortcut" (Acceso directo dinámico). Sin esto, la burbuja nunca aparece. Vamos a crear una función robusta para esto.

Markdown

@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/CoreService.kt
Lee: app/src/main/res/values/strings.xml (si es necesario, sino usa strings duros por ahora)

@Instrucción:
Vamos a modificar `CoreService.kt` para implementar la lógica de notificación avanzada.

1. Añade las importaciones necesarias para `NotificationCompat`, `Person`, `ShortcutInfoCompat`, `IconCompat`, `BubbleMetadata`. (Asegúrate de usar androidx.core si es posible para compatibilidad).

2. Crea una función privada dentro de `CoreService` llamada `updateNotification(content: String)`.

3. Dentro de esa función, implementa la lógica OBLIGATORIA para burbujas:
   A. **El Shortcut:**
    - Crea un `ShortcutInfoCompat` con ID "chat_shortcut".
    - Label: "Control IA".
    - Intent: El intent que abre `ChatActivity` (con acción `Intent.ACTION_VIEW`).
    - Persona: Crea un objeto `Person` (nombre "IA", icono por defecto).
    - Usa `ShortcutManagerCompat.pushDynamicShortcut` para registrarlo.

   B. **El BubbleMetadata:**
    - Crea un PendingIntent que apunte a `ChatActivity` (Flag MUTABLE).
    - Crea el `NotificationCompat.BubbleMetadata`:
        - `.setDesiredHeight(600)`
        - `.setIcon(IconCompat...)`
        - `.setAutoExpandBubble(false)` (o true si quieres que se abra sola al inicio).
        - `.setSuppressNotification(false)`

   C. **La Notificación:**
    - Usa `NotificationCompat.Builder`.
    - `.setBubbleMetadata(bubbleData)`
    - `.setShortcutId("chat_shortcut")`
    - `.addPerson(person)`
    - IMPORTANTE: Usa `.setStyle(NotificationCompat.MessagingStyle(person))` y añade el mensaje recibido. Las burbujas funcionen mejor con MessagingStyle.

4. Finalmente, llama a `manager.notify(NOTIFICATION_ID, builder.build())`.
   🔄 Prompt 3: Conectar el Evento de Mensaje
   Hacer que cuando llegue un mensaje del WebSocket, la notificación se actualice y la burbuja "palpite".

Markdown

@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/CoreService.kt

@Instrucción:
En `CoreService.kt`, localiza donde recibes los mensajes del WebSocket (probablemente en el callback del `CommandHandler` o `WebSocketClient`).

1. Cuando llegue un mensaje nuevo (comando o texto), llama a la nueva función `updateNotification("Nuevo comando: $comando")`.
2. Esto asegurará que la burbuja tenga el contexto más reciente y, si está minimizada, muestre el puntito de notificación.