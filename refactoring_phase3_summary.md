# Refactoring Phase 3 Summary: Bubble Notification Support

The following changes have been implemented to enable "Bubble" notifications for the CoreService:

1.  **Manifest Configuration (`AndroidManifest.xml`)**:
    -   Updated `ChatActivity` with `android:allowEmbedded="true"`, `android:documentLaunchMode="always"`, `android:resizeableActivity="true"`, and `android:windowSoftInputMode="adjustResize"`.
    -   These attributes are critical for the Activity to launch within a bubble window.

2.  **Notification Logic (`CoreService.kt`)**:
    -   Updated `CoreService.kt` to include robust notification building logic.
    -   Implemented `updateNotification` to:
        -   Create a `Person` object representing the "IA".
        -   Push a dynamic `ShortcutInfoCompat` linked to `ChatActivity`. This is a strict requirement for Bubbles on Android 11+.
        -   Create `BubbleMetadata` pointing to `ChatActivity` with a mutable `PendingIntent`.
        -   Use `NotificationCompat.MessagingStyle` for the notification content, which is the preferred style for conversation bubbles.
    -   Updated `createNotificationChannel` to explicitly allow bubbles on supported API levels (Q+).
    -   Connected the WebSocket `onCommandReceived` callback to `updateNotification`, ensuring that new messages trigger the bubble/notification update.

The project builds successfully, and the application is now equipped to display incoming AI commands and messages as interactive Bubbles.
