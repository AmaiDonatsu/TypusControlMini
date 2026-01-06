Establecer el flujo de datos bidireccional entre la UI (ChatActivity) y el Servicio (CoreService).

Entrada (Input): Que el usuario pueda escribir en el chat y eso viaje al WebSocket.

Salida (Output): Que los mensajes del WebSocket (y las confirmaciones de la IA) aparezcan visualmente en el Chat.

Puente: Crear los adaptadores necesarios para traducir datos crudos (JSON/String) en elementos visuales (Burbujas de texto).

📋 Script Prompts (Copiar y Pegar)
🧬 Prompt 1: El Adaptador Visual
Este paso crea la pieza que traduce los datos en listas visuales. Incluye la creación automática del layout para los mensajitos.

Markdown

@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/ChatRepository.kt (para conocer la clase ChatMessage)
Lee: app/src/main/res/layout/activity_chat.xml

@Instrucción:
Necesitamos mostrar los mensajes en el RecyclerView. Realiza lo siguiente:

1. Crea un nuevo layout XML llamado `item_chat_message.xml`.
   - Debe usar un ConstraintLayout o LinearLayout.
   - Debe tener un TextView con id `@+id/tvMessageContent` y un fondo redondeado.
   - El diseño debe ser flexible para alinear el mensaje a la derecha (si es del usuario) o a la izquierda (si es recibido).

2. Crea una clase `ChatAdapter.kt` en el paquete principal.
   - Debe extender de `ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>`.
   - Usa `DiffUtil` para comparar items eficientemente.
   - En `onBindViewHolder`, ajusta la gravedad/alineación y el color de fondo del `tvMessageContent` dependiendo de `message.isUser`:
     - Si `isUser` es true: Alinear a la derecha, color distintivo (ej. azul o gris oscuro).
     - Si `isUser` es false: Alinear a la izquierda, color gris claro.
🔌 Prompt 2: Exponer el Cerebro (Singleton Access)
Necesitamos que la Activity pueda "llamar" al servicio fácilmente sin hacer un bindService complejo.

Markdown

@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/CoreService.kt

@Instrucción:
Modifica `CoreService.kt` para permitir el acceso estático a su función de envío de mensajes.

1. Añade un `companion object` dentro de la clase `CoreService`.
2. Crea una variable estática: `var instance: CoreService? = null`.
3. En `onCreate()`, asigna `instance = this`.
4. En `onDestroy()`, asigna `instance = null`.
5. Asegúrate de que exista una función pública `fun sendTextToSocket(text: String)` dentro del servicio.
   - Esta función debe llamar al método `sendFrame` o `sendResponse` de tu instancia de `WebSocketClient`.
   - También debe agregar el mensaje enviado al `ChatRepository` para que aparezca en la UI localmente.
📡 Prompt 3: Conexión Neuronal (Activity Logic)
El paso final: hacer que la pantalla de Chat realmente funcione.

Markdown

@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/ChatActivity.kt
Lee: app/src/main/java/com/example/typuscontrolmini/ChatAdapter.kt
Lee: app/src/main/java/com/example/typuscontrolmini/ChatRepository.kt
Lee: app/src/main/java/com/example/typuscontrolmini/CoreService.kt

@Instrucción:
Reescribe la lógica de `ChatActivity.kt` para conectar todo:

1. Inicializa el `ChatAdapter` y asígnalo al `rvMessages`.
2. Configura el envío de mensajes:
   - En el `OnClickListener` del botón de enviar, obtén el texto.
   - Llama a `CoreService.instance?.sendTextToSocket(text)`.
   - Si `CoreService.instance` es null, muestra un Toast indicando "Servicio desconectado".
   - Limpia el EditText tras enviar.
3. Observa los datos reactivos:
   - Usa `lifecycleScope.launch` (asegúrate de importar las dependencias de corrutinas si faltan).
   - Recolecta (collect) el flujo `ChatRepository.messages`.
   - Cada vez que llegue una nueva lista, pásala al adaptador (`adapter.submitList`).
   - Haz scroll automático al último mensaje (`rvMessages.smoothScrollToPosition`) cuando la lista crezca.