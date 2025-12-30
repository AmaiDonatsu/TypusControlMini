# Objetivo:
es transformar la arquitectura de tu aplicación de un modelo "basado en la interfaz" a uno "basado en la persistencia". Actualmente, la lógica del WebSocket y el control están ligados a la visibilidad de componentes volátiles.
Establecer un Núcleo de Operaciones Inmortal capaz de mantener una conexión bidireccional constante con el servidor de IA, procesar comandos de accesibilidad y gestionar el historial de mensajes, independientemente de si el usuario está viendo el chat o si la burbuja está activa.

# Objetivo General
Establecer un Núcleo de Operaciones Inmortal capaz de mantener una conexión bidireccional constante con el servidor de IA, procesar comandos de accesibilidad y gestionar el historial de mensajes, independientemente de si el usuario está viendo el chat o si la burbuja está activa.

🔍 Desglose Técnico del Objetivo
Persistencia de Grado Industrial: Migrar la instancia de WebSocketClient desde componentes de UI (como tu actual FloatingService) hacia un Foreground Service. Esto evita que Android mate la conexión para ahorrar batería mientras la IA ejecuta procesos largos.

Desacoplamiento (Separación de Preocupaciones):

El Cerebro (CoreService): Se encarga solo de la red y la lógica de negocio.

Las Manos (DeviceControlService): Se encargan solo de la ejecución física de gestos.

La Cara (UI/Burbuja): Se encarga solo de mostrar los datos.

Gestión Centralizada de Mensajes: Crear un ChatRepository que actúe como la "única fuente de verdad". Esto asegura que cuando la burbuja se abra, los mensajes ya estén allí porque el servicio los guardó en tiempo real.

Ejecución de Comandos en Segundo Plano: Asegurar que el CommandHandler pueda recibir un JSON, llamar a DeviceControlService.getInstance() y realizar un performTap o performSwipe incluso con la pantalla apagada o con otra aplicación en primer plano.

# Fase 1: La Capa de Datos (El puente entre Servicio y UI)
Este prompt crea el Singleton que guardará los mensajes. Es necesario crearlo primero para que el Servicio tenga donde guardar la info.

@Contexto:
No necesito leer archivos existentes para este paso.

@Instrucción:
Crea un nuevo archivo llamado "ChatRepository.kt" en el paquete "com.example.typuscontrolmini".
Este debe ser un "object" (Singleton) en Kotlin.

Requisitos:
1. Debe tener una variable `private val _messages` que sea un `MutableStateFlow<List<ChatMessage>>`.
2. Debe exponer `val messages: StateFlow<List<ChatMessage>>` para ser observado.
3. Crea una data class `ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())`.
4. Implementa una función `addMessage(text: String, isUser: Boolean)` que actualice el StateFlow agregando el nuevo mensaje a la lista actual.
5. Este repositorio servirá para comunicar el Servicio en segundo plano con la UI más adelante.

# Fase 2: El Esqueleto del Servicio Inmortal
Aquí creamos la base del servicio que no morirá. Necesita leer los iconos para la notificación.
@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/DeviceControlService.kt (solo para ver referencias de imports)
Lee: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml (para saber el icono)

@Instrucción:
Crea un nuevo archivo llamado "CoreService.kt" en el paquete principal.
Este servicio será el "Cerebro" de la aplicación y debe extender de `Service`.

Funcionalidad requerida:
1. Implementa `onBind` retornando null (es un Started Service, no Bound).
2. En `onCreate`, debe crear un NotificationChannel (ID: "core_channel", Name: "Typus Core") compatible con Android O+.
3. Crea una función `startForegroundService()` que construya una Notificación persistente.
    - Título: "TypusControl Activo"
    - Texto: "Esperando comandos..."
    - Icono: R.mipmap.ic_launcher
4. En `onStartCommand`, llama a `startForegroundService()` y retorna `START_STICKY` para asegurar que el sistema intente revivirlo si lo mata por memoria.
5. Prepara el método `onDestroy` para limpieza futura.

# Fase 3: Integración de WebSocket y Lógica
@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/WebSocketClient.kt
Lee: app/src/main/java/com/example/typuscontrolmini/CommandHandler.kt
Lee: app/src/main/java/com/example/typuscontrolmini/CoreService.kt (el que acabas de crear)
Lee: app/src/main/java/com/example/typuscontrolmini/ChatRepository.kt

@Instrucción:
Refactoriza "CoreService.kt" para integrar la lógica del WebSocket y el manejo de comandos.

Cambios a realizar en CoreService:
1. Instancia `WebSocketClient` y `CommandHandler` como propiedades de la clase.
2. En `onStartCommand` (o una función `initConnection`), configura el WebSocket:
    - Usa los datos de conexión (puedes usar placeholders o leer de Config si existe, por ahora usa strings vacíos para URL/Token que luego llenaré).
    - En el callback `onMessage` del WebSocket:
      (a) Pasa el mensaje al `CommandHandler.handleCommand`.
      (b) Guarda el mensaje en `ChatRepository.addMessage(text, isUser=false)`.
      (c) Actualiza la notificación con el último comando recibido (opcional).
3. Implementa un método público (companion object o broadcast receiver) o simplemente una función dentro del servicio `sendMessage(text: String)` que:
    - Envíe el texto por el WebSocket.
    - Lo guarde en `ChatRepository.addMessage(text, isUser=true)`.

Nota: Asegúrate de manejar el Scope de Corutinas adecuado si es necesario para el Repository.

# Fase 4: Registro en el Manifiesto
@Contexto:
Lee: app/src/main/AndroidManifest.xml

@Instrucción:
Actualiza el AndroidManifest.xml para registrar el nuevo servicio.

1. Dentro del tag <application>, añade la declaración para `CoreService`.
2. IMPORTANTE: Debes añadir el atributo `android:foregroundServiceType="dataSync|remoteMessaging"` (o "specialUse" si es Android 14, pero usa "dataSync" por compatibilidad general por ahora) a la declaración del servicio.
3. Asegúrate de que los permisos `FOREGROUND_SERVICE` y `POST_NOTIFICATIONS` ya estén declarados (según el archivo actual parecen estarlo, pero verifícalo).

# Fase 5: Limpieza
@Contexto:
Lee: app/src/main/java/com/example/typuscontrolmini/FloatingService.kt

@Instrucción:
Como ya hemos movido la lógica "cerebral" al CoreService, marca el archivo `FloatingService.kt` como "@Deprecated" añadiendo la anotación en la clase, o comenta el código que inicializaba el WebSocket allí para evitar conexiones duplicadas.