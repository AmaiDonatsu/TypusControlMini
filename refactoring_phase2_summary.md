# Refactoring Phase 2 Summary: Bidirectional Data Flow

The following changes have been implemented to establish a bidirectional data flow between the UI (ChatActivity) and the Service (CoreService):

1.  **Visual Adapter (`ChatAdapter.kt` & `item_chat_message.xml`)**:
    -   Created a flexible layout for chat messages that can display user and AI messages distinctly.
    -   Implemented `ChatAdapter` with `DiffUtil` for efficient list updates and dynamic styling (alignment and color) based on the message sender.

2.  **Service Exposure (`CoreService.kt`)**:
    -   Added a Singleton `instance` to `CoreService` for easy access from Activities.
    -   Implemented `sendTextToSocket(text: String)` to handle sending messages to the WebSocket and simultaneously updating the local `ChatRepository`.

3.  **Activity Logic (`ChatActivity.kt`)**:
    -   Initialized `ChatAdapter` and connected it to the RecyclerView.
    -   Implemented the Send button logic to utilize `CoreService.instance.sendTextToSocket`.
    -   Added a reactive observer for `ChatRepository.messages` to automatically update the UI and scroll to the latest message.

The project builds successfully, and the Chat UI is now fully wired to the backend logic.