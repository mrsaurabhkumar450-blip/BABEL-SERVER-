package com.example.data

data class CommandLogItem(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val command: String = "",
    val response: String = "",
    val actionType: String = "NONE",
    val isVoice: Boolean = false,
    val isEncrypted: Boolean = true
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isThinking: Boolean = false,
    val actionTaken: String? = null
)

enum class MessageSender {
    USER,
    JARVIS,
    SYSTEM
}

data class QuickActionMacro(
    val id: String,
    val title: String,
    val hindiTitle: String,
    val command: String,
    val iconName: String
)
