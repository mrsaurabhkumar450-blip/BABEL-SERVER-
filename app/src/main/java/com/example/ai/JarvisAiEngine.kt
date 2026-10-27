package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

data class JarvisCommandAction(
    val type: ActionType,
    val targetPhone: String = "",
    val messagePayload: String = "",
    val searchQuery: String = "",
    val speechResponse: String = ""
)

enum class ActionType {
    NONE,
    TORCH_ON,
    TORCH_OFF,
    TOGGLE_TORCH,
    OPEN_WHATSAPP,
    SEND_WHATSAPP,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    VOLUME_MAX,
    BATTERY_CHECK,
    OPEN_CAMERA,
    OPEN_ALARM,
    OPEN_MAPS,
    OPEN_SETTINGS,
    OPEN_WIFI,
    OPEN_BLUETOOTH,
    OPEN_YOUTUBE,
    OPEN_DIALER,
    VIBRATE,
    SEARCH_WEB
}

class JarvisAiEngine {

    companion object {
        private const val TAG = "JarvisAiEngine"
        private const val SYSTEM_PROMPT = """
You are J.A.R.V.I.S., the ultimate personal AI assistant.
- You are loyal, crisp, witty, polite, and hyper-competent.
- You speak naturally in English, Hindi, and everyday Hinglish (e.g. "Yes Boss, kaam ho gaya!", "Haan Sir, bilkul!", "System online and standing by").
- When asked to control the device or perform an action, provide concise confirmation ready to be spoken aloud.
- Keep responses engaging, smart, and direct. No filler words or unnecessary disclaimers.
"""
    }

    // Default versatile model with Google Search tool grounding
    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch()),
            systemInstruction = Content(role = "system", parts = listOf(TextPart(SYSTEM_PROMPT)))
        )
    }

    // High-thinking reasoning model for complex questions
    private val thinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            },
            systemInstruction = Content(
                role = "system",
                parts = listOf(
                    TextPart("$SYSTEM_PROMPT\nUse deep analytical rigor to solve complex logic, coding, and technical inquiries.")
                )
            )
        )
    }

    // Ultra-fast model for quick responses
    private val fastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite",
            systemInstruction = Content(role = "system", parts = listOf(TextPart(SYSTEM_PROMPT)))
        )
    }

    // Multi-turn chat session (lazily initialized on first interaction)
    private var _chatSession: com.google.firebase.ai.Chat? = null
    private val chatSession: com.google.firebase.ai.Chat
        get() {
            if (_chatSession == null) {
                _chatSession = generalModel.startChat()
            }
            return _chatSession!!
        }

    fun resetChat() {
        _chatSession = generalModel.startChat()
    }

    // Streaming response for conversational UI
    fun streamChat(message: String, isHighThinking: Boolean = false): Flow<String> = flow {
        withTimeout(60_000L) {
            try {
                if (isHighThinking) {
                    val stream = thinkingModel.generateContentStream(message)
                    stream.collect { chunk ->
                        val text = chunk.text
                        if (!text.isNullOrEmpty()) {
                            emit(text)
                        }
                    }
                } else {
                    val stream = chatSession.sendMessageStream(message)
                    stream.collect { chunk ->
                        val text = chunk.text
                        if (!text.isNullOrEmpty()) {
                            emit(text)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat streaming failed", e)
                emit(" [Connection Error: ${e.localizedMessage ?: "Unable to complete request"}]")
            }
        }
    }

    // Unary fast generation
    suspend fun askFast(prompt: String): String {
        return try {
            withTimeout(30_000L) {
                val res = fastModel.generateContent(prompt)
                res.text ?: "Ready Sir."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fast AI query failed", e)
            "System error: ${e.localizedMessage}"
        }
    }

    // Multimodal Vision / Live Screen Analysis
    suspend fun analyzeScreenOrImage(bitmap: Bitmap, userQuery: String): String {
        return try {
            withTimeout(60_000L) {
                val promptText = if (userQuery.isNotBlank()) userQuery else "Analyze this screen image in detail and tell me what you see in Hindi and English like Jarvis."
                val response = generalModel.generateContent(
                    Content(
                        role = "user",
                        parts = listOf(
                            ImagePart(bitmap),
                            TextPart(promptText)
                        )
                    )
                )
                response.text ?: "Visual analysis completed, but no details were returned."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            "Vision analysis failed: ${e.localizedMessage}"
        }
    }

    // Classify command for instant device actions
    fun parseDeviceAction(command: String): JarvisCommandAction {
        val lower = command.lowercase().trim()

        // Torch / Flashlight
        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("batti bujhao") || lower.contains("torch band") || lower.contains("turn off torch") || lower.contains("turn off flashlight")) {
            return JarvisCommandAction(
                type = ActionType.TORCH_OFF,
                speechResponse = "Flashlight OFF kar di gayi hai, Sir."
            )
        }
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("batti jalao") || lower.contains("torch chalu") || lower.contains("turn on torch") || lower.contains("turn on flashlight")) {
            return JarvisCommandAction(
                type = ActionType.TORCH_ON,
                speechResponse = "Yes Boss, Flashlight ON kar di hai!"
            )
        }
        if (lower.contains("torch") || lower.contains("flashlight")) {
            return JarvisCommandAction(
                type = ActionType.TOGGLE_TORCH,
                speechResponse = "Toggling flashlight now, Boss."
            )
        }

        // WhatsApp Direct Message
        if ((lower.contains("whatsapp") || lower.contains("message")) && (lower.contains("bhejo") || lower.contains("send"))) {
            // Extract phone number if present
            val phoneMatch = Regex("\\b[6-9]\\d{9}\\b").find(lower)?.value ?: ""
            // Extract message
            val msg = command.replace(Regex("(?i).*?(bhejo|send|message to|saying)"), "").trim()
            return JarvisCommandAction(
                type = ActionType.SEND_WHATSAPP,
                targetPhone = phoneMatch,
                messagePayload = if (msg.isNotEmpty()) msg else "Hello from JARVIS AI!",
                speechResponse = if (phoneMatch.isNotEmpty()) "Opening WhatsApp message to $phoneMatch" else "Opening WhatsApp chat..."
            )
        }

        // WhatsApp App open
        if (lower.contains("whatsapp")) {
            return JarvisCommandAction(
                type = ActionType.OPEN_WHATSAPP,
                speechResponse = "Opening WhatsApp now, Sir."
            )
        }

        // Volume Controls
        if (lower.contains("volume full") || lower.contains("awaj full") || lower.contains("max volume")) {
            return JarvisCommandAction(
                type = ActionType.VOLUME_MAX,
                speechResponse = "Volume set to maximum, Sir!"
            )
        }
        if (lower.contains("volume up") || lower.contains("volume badhao") || lower.contains("awaj badhao") || lower.contains("increase volume")) {
            return JarvisCommandAction(
                type = ActionType.VOLUME_UP,
                speechResponse = "Volume increased, Boss."
            )
        }
        if (lower.contains("volume down") || lower.contains("volume kam") || lower.contains("awaj kam") || lower.contains("decrease volume")) {
            return JarvisCommandAction(
                type = ActionType.VOLUME_DOWN,
                speechResponse = "Volume lowered, Sir."
            )
        }
        if (lower.contains("mute") || lower.contains("chup") || lower.contains("silent")) {
            return JarvisCommandAction(
                type = ActionType.VOLUME_MUTE,
                speechResponse = "Device audio muted, Sir."
            )
        }

        // Battery check
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("charging")) {
            return JarvisCommandAction(
                type = ActionType.BATTERY_CHECK,
                speechResponse = "Checking battery status now, Boss."
            )
        }

        // Camera
        if (lower.contains("camera") || lower.contains("photo") || lower.contains("selfie")) {
            return JarvisCommandAction(
                type = ActionType.OPEN_CAMERA,
                speechResponse = "Launching Camera, Sir."
            )
        }

        // Alarm / Clock
        if (lower.contains("alarm") || lower.contains("timer") || lower.contains("clock")) {
            return JarvisCommandAction(
                type = ActionType.OPEN_ALARM,
                speechResponse = "Opening Clock and Alarms, Sir."
            )
        }

        // Maps
        if (lower.contains("map") || lower.contains("location") || lower.contains("rasta") || lower.contains("navigation")) {
            val query = command.replace(Regex("(?i).*?(maps?|location|rasta|to)"), "").trim()
            return JarvisCommandAction(
                type = ActionType.OPEN_MAPS,
                searchQuery = query,
                speechResponse = "Opening Maps navigation, Sir."
            )
        }

        // Settings / Wi-Fi / Bluetooth
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return JarvisCommandAction(
                type = ActionType.OPEN_WIFI,
                speechResponse = "Opening Wi-Fi settings, Boss."
            )
        }
        if (lower.contains("bluetooth")) {
            return JarvisCommandAction(
                type = ActionType.OPEN_BLUETOOTH,
                speechResponse = "Opening Bluetooth settings, Sir."
            )
        }
        if (lower.contains("settings")) {
            return JarvisCommandAction(
                type = ActionType.OPEN_SETTINGS,
                speechResponse = "Opening System Settings, Boss."
            )
        }

        // YouTube
        if (lower.contains("youtube") || lower.contains("gana") || lower.contains("song")) {
            val query = command.replace(Regex("(?i).*?(youtube|gana|song|play)"), "").trim()
            return JarvisCommandAction(
                type = ActionType.OPEN_YOUTUBE,
                searchQuery = query,
                speechResponse = "Launching YouTube, Sir."
            )
        }

        // Phone call / Dialer
        if (lower.contains("call") || lower.contains("phone") || lower.contains("dial")) {
            val phoneMatch = Regex("\\b[6-9]\\d{9}\\b").find(lower)?.value ?: ""
            return JarvisCommandAction(
                type = ActionType.OPEN_DIALER,
                targetPhone = phoneMatch,
                speechResponse = if (phoneMatch.isNotEmpty()) "Calling $phoneMatch" else "Opening Phone Dialer, Sir."
            )
        }

        // Vibrate
        if (lower.contains("vibrate") || lower.contains("vibration")) {
            return JarvisCommandAction(
                type = ActionType.VIBRATE,
                speechResponse = "Vibrating device now, Sir."
            )
        }

        // Search
        if (lower.startsWith("search ") || lower.contains("google search") || lower.contains("dhundo")) {
            val query = command.replace(Regex("(?i)^(search|google search|dhundo)"), "").trim()
            return JarvisCommandAction(
                type = ActionType.SEARCH_WEB,
                searchQuery = query,
                speechResponse = "Searching the web for $query, Boss."
            )
        }

        return JarvisCommandAction(type = ActionType.NONE)
    }
}
