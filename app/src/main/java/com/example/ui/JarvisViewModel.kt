package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ActionType
import com.example.ai.JarvisAiEngine
import com.example.ai.JarvisCommandAction
import com.example.data.ChatMessage
import com.example.data.CommandLogItem
import com.example.data.JarvisRepository
import com.example.data.MessageSender
import com.example.device.BatteryInfo
import com.example.device.SystemMemoryInfo
import com.example.device.DeviceControlManager
import com.example.security.EncryptionManager
import com.example.voice.SpeechAndTtsManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface VisionUiState {
    data object Idle : VisionUiState
    data object Analyzing : VisionUiState
    data class Success(val result: String) : VisionUiState
    data class Error(val error: String) : VisionUiState
}

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    val encryptionManager = EncryptionManager(application)
    val deviceManager = DeviceControlManager(application)
    val repository = JarvisRepository(application, encryptionManager)
    val aiEngine = JarvisAiEngine()

    private val _isUnlocked = MutableStateFlow(encryptionManager.isUnlocked)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isSecurityConfigured = MutableStateFlow(encryptionManager.hasPin())
    val isSecurityConfigured: StateFlow<Boolean> = _isSecurityConfigured.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isThinkingMode = MutableStateFlow(false)
    val isThinkingMode: StateFlow<Boolean> = _isThinkingMode.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.JARVIS,
                text = "Welcome Boss! J.A.R.V.I.S. is online. Main aapki kya madad kar sakta hoon? Device control, WhatsApp message, screen analysis, ya koi bhi query boliye!",
                timestamp = System.currentTimeMillis()
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Live Telemetry
    private val _batteryInfo = MutableStateFlow(deviceManager.getBatteryInfo())
    val batteryInfo: StateFlow<com.example.device.BatteryInfo> = _batteryInfo.asStateFlow()

    private val _memoryInfo = MutableStateFlow(deviceManager.getMemoryInfo())
    val memoryInfo: StateFlow<com.example.device.SystemMemoryInfo> = _memoryInfo.asStateFlow()

    private val _isTorchOn = MutableStateFlow(deviceManager.isTorchOn)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _musicVolume = MutableStateFlow(deviceManager.getMusicVolume())
    val musicVolume: StateFlow<Int> = _musicVolume.asStateFlow()

    val maxVolume: Int get() = deviceManager.getMaxMusicVolume()

    // Vision UI State
    private val _visionState = MutableStateFlow<VisionUiState>(VisionUiState.Idle)
    val visionState: StateFlow<VisionUiState> = _visionState.asStateFlow()

    // Toast/Alert notification message for UI
    private val _hudNotification = MutableStateFlow<String?>(null)
    val hudNotification: StateFlow<String?> = _hudNotification.asStateFlow()

    val currentUser: StateFlow<FirebaseUser?> = repository.authStateFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, repository.currentUser)

    val commandLogs: StateFlow<List<CommandLogItem>> = repository.localLogs

    val speechManager = SpeechAndTtsManager(
        context = application,
        onSpeechRecognized = { spokenText ->
            processSpokenCommand(spokenText)
        },
        onError = { errorMsg ->
            _hudNotification.value = errorMsg
        },
        onListeningStateChanged = { listening ->
            _isListening.value = listening
        }
    )

    init {
        refreshTelemetry()
    }

    fun dismissNotification() {
        _hudNotification.value = null
    }

    fun setThinkingMode(enabled: Boolean) {
        _isThinkingMode.value = enabled
        showNotification(if (enabled) "Thinking Mode (Gemini 4 Argon) Enabled" else "Standard Fast Mode Enabled")
    }

    fun refreshTelemetry() {
        _batteryInfo.value = deviceManager.getBatteryInfo()
        _memoryInfo.value = deviceManager.getMemoryInfo()
        _isTorchOn.value = deviceManager.isTorchOn
        _musicVolume.value = deviceManager.getMusicVolume()
    }

    // Security Actions
    fun unlockWithPin(pin: String): Boolean {
        val success = encryptionManager.verifyPin(pin)
        if (success) {
            _isUnlocked.value = true
            showNotification("Access Granted. Systems Online.")
            speechManager.speak("Security verified. Welcome back, Sir.")
        } else {
            showNotification("Access Denied: Invalid Security PIN")
            deviceManager.vibrate(250)
        }
        return success
    }

    fun setPin(pin: String): Boolean {
        val success = encryptionManager.setPin(pin)
        if (success) {
            _isSecurityConfigured.value = true
            _isUnlocked.value = true
            showNotification("Security PIN Configured & Encrypted.")
        }
        return success
    }

    fun toggleSecurity(enabled: Boolean) {
        encryptionManager.setSecurityEnabled(enabled)
        _isSecurityConfigured.value = encryptionManager.hasPin()
        _isUnlocked.value = encryptionManager.isUnlocked
        showNotification(if (enabled) "Security Vault Lock Enabled" else "Security Lock Disabled")
    }

    fun lockApp() {
        encryptionManager.lock()
        _isUnlocked.value = false
        speechManager.stopSpeaking()
        speechManager.stopListening()
    }

    // Voice Input Controls
    fun startListening() {
        refreshTelemetry()
        val prefLang = encryptionManager.getPreferredLanguage()
        val locale = when (prefLang) {
            "english" -> "en-US"
            "hindi" -> "hi-IN"
            else -> "hi-IN"
        }
        speechManager.startListening(locale)
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun speakText(text: String) {
        speechManager.speak(text)
    }

    fun stopSpeaking() {
        speechManager.stopSpeaking()
    }

    // Process spoken voice command directly
    private fun processSpokenCommand(commandText: String) {
        executeUserCommand(commandText, isVoice = true)
    }

    // User command pipeline (checks instant device actions, then streams AI response)
    fun executeUserCommand(commandText: String, isVoice: Boolean = false) {
        if (commandText.isBlank()) return

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = commandText,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMsg

        // Check if command is a device action
        val action = aiEngine.parseDeviceAction(commandText)
        if (action.type != ActionType.NONE) {
            handleDeviceAction(action, commandText, isVoice)
        } else {
            // General query - stream via Gemini
            streamAiResponse(commandText, isVoice)
        }
    }

    private fun handleDeviceAction(action: JarvisCommandAction, rawCommand: String, isVoice: Boolean) {
        viewModelScope.launch {
            var executionNote = action.speechResponse

            when (action.type) {
                ActionType.TORCH_ON -> {
                    deviceManager.toggleTorch(true)
                    _isTorchOn.value = true
                }
                ActionType.TORCH_OFF -> {
                    deviceManager.toggleTorch(false)
                    _isTorchOn.value = false
                }
                ActionType.TOGGLE_TORCH -> {
                    val newState = !_isTorchOn.value
                    deviceManager.toggleTorch(newState)
                    _isTorchOn.value = newState
                    executionNote = if (newState) "Torch switched ON, Boss" else "Torch switched OFF, Boss"
                }
                ActionType.SEND_WHATSAPP -> {
                    deviceManager.sendWhatsAppMessage(action.targetPhone, action.messagePayload)
                }
                ActionType.OPEN_WHATSAPP -> {
                    deviceManager.openWhatsApp()
                }
                ActionType.VOLUME_MAX -> {
                    deviceManager.maxVolume()
                    _musicVolume.value = deviceManager.getMusicVolume()
                }
                ActionType.VOLUME_UP -> {
                    deviceManager.setMusicVolume(_musicVolume.value + 2)
                    _musicVolume.value = deviceManager.getMusicVolume()
                }
                ActionType.VOLUME_DOWN -> {
                    deviceManager.setMusicVolume(_musicVolume.value - 2)
                    _musicVolume.value = deviceManager.getMusicVolume()
                }
                ActionType.VOLUME_MUTE -> {
                    deviceManager.muteVolume()
                    _musicVolume.value = 0
                }
                ActionType.BATTERY_CHECK -> {
                    val b = deviceManager.getBatteryInfo()
                    _batteryInfo.value = b
                    executionNote = "Battery level is ${b.percentage}%. Status: ${if (b.isCharging) "Charging via ${b.powerSource}" else "Not charging"}."
                }
                ActionType.OPEN_CAMERA -> {
                    deviceManager.openCamera()
                }
                ActionType.OPEN_ALARM -> {
                    deviceManager.openClockOrAlarm()
                }
                ActionType.OPEN_MAPS -> {
                    deviceManager.openMaps(action.searchQuery)
                }
                ActionType.OPEN_SETTINGS -> {
                    deviceManager.openSettings()
                }
                ActionType.OPEN_WIFI -> {
                    deviceManager.openWifiSettings()
                }
                ActionType.OPEN_BLUETOOTH -> {
                    deviceManager.openBluetoothSettings()
                }
                ActionType.OPEN_YOUTUBE -> {
                    deviceManager.openYouTube(action.searchQuery)
                }
                ActionType.OPEN_DIALER -> {
                    deviceManager.openDialer(action.targetPhone)
                }
                ActionType.VIBRATE -> {
                    deviceManager.vibrate(300)
                }
                ActionType.SEARCH_WEB -> {
                    deviceManager.searchWeb(action.searchQuery)
                }
                ActionType.NONE -> {}
            }

            // Speak confirmation
            speechManager.speak(executionNote)

            // Add response message
            val jarvisMsg = ChatMessage(
                sender = MessageSender.JARVIS,
                text = executionNote,
                actionTaken = action.type.name
            )
            _messages.value = _messages.value + jarvisMsg

            // Log command
            val logItem = CommandLogItem(
                id = UUID.randomUUID().toString(),
                command = rawCommand,
                response = executionNote,
                actionType = action.type.name,
                isVoice = isVoice
            )
            repository.logCommand(logItem)
        }
    }

    private fun streamAiResponse(prompt: String, isVoice: Boolean) {
        viewModelScope.launch {
            _isGenerating.value = true
            val msgId = UUID.randomUUID().toString()
            val initialJarvisMsg = ChatMessage(
                id = msgId,
                sender = MessageSender.JARVIS,
                text = "",
                isThinking = _isThinkingMode.value
            )
            _messages.value = _messages.value + initialJarvisMsg

            val accumulated = StringBuilder()
            try {
                aiEngine.streamChat(prompt, _isThinkingMode.value).collect { chunk ->
                    accumulated.append(chunk)
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == msgId) msg.copy(text = accumulated.toString()) else msg
                    }
                }

                val finalOutput = accumulated.toString().ifBlank { "Task completed, Sir." }
                if (isVoice) {
                    speechManager.speak(finalOutput)
                }

                // Log to repository
                val logItem = CommandLogItem(
                    id = msgId,
                    command = prompt,
                    response = finalOutput,
                    actionType = if (_isThinkingMode.value) "THINKING_AI" else "CHAT_AI",
                    isVoice = isVoice
                )
                repository.logCommand(logItem)
            } catch (e: Exception) {
                val errorMsg = "Apologies Sir, I encountered an issue: ${e.localizedMessage}"
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == msgId) msg.copy(text = errorMsg) else msg
                }
                if (isVoice) speechManager.speak(errorMsg)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // Multimodal Vision / Live Screen Analysis
    fun analyzeScreenOrImage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _visionState.value = VisionUiState.Analyzing
            try {
                val query = if (prompt.isNotBlank()) prompt else "Explain what is visible on this screen in detail in Hindi and English."
                val result = aiEngine.analyzeScreenOrImage(bitmap, query)
                _visionState.value = VisionUiState.Success(result)

                // Add to chat and speak
                _messages.value = _messages.value + ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "🖥️ Screen Analysis:\n$result",
                    actionTaken = "SCREEN_VISION"
                )
                speechManager.speak("Screen analysis complete. $result")
            } catch (e: Exception) {
                _visionState.value = VisionUiState.Error(e.localizedMessage ?: "Analysis failed")
            }
        }
    }

    fun toggleTorchDirect() {
        val newState = !_isTorchOn.value
        deviceManager.toggleTorch(newState)
        _isTorchOn.value = newState
        val msg = if (newState) "Flashlight turned ON" else "Flashlight turned OFF"
        showNotification(msg)
        speechManager.speak(msg)
    }

    fun setVolumeDirect(percent: Int) {
        deviceManager.setMusicVolumePercent(percent)
        _musicVolume.value = deviceManager.getMusicVolume()
    }

    fun syncCloudData() {
        viewModelScope.launch {
            showNotification("Syncing with secure cloud...")
            val success = repository.syncRemoteCommands()
            if (success) {
                showNotification("Cloud sync complete: Commands up-to-date")
            } else {
                showNotification("Cloud sync failed: Check network connection")
            }
        }
    }

    private fun showNotification(msg: String) {
        _hudNotification.value = msg
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}
