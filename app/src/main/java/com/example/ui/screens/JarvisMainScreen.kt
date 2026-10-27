package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MessageSender
import com.example.ui.JarvisViewModel
import com.example.ui.components.HudTopBar
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

enum class MainNavTab(val title: String, val icon: ImageVector) {
    HUD("Core HUD", Icons.Default.Dashboard),
    CHAT("Voice Chat", Icons.Default.ChatBubble),
    ACTIONS("Controls", Icons.Default.PhoneAndroid),
    VISION("Vision", Icons.Default.Visibility),
    SECURITY("Security", Icons.Default.Security)
}

@Composable
fun JarvisMainScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(MainNavTab.HUD.ordinal) }
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isThinkingMode by viewModel.isThinkingMode.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val memoryInfo by viewModel.memoryInfo.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val musicVolume by viewModel.musicVolume.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val visionState by viewModel.visionState.collectAsState()
    val hudNotification by viewModel.hudNotification.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val lastUserMsg = messages.lastOrNull { it.sender == MessageSender.USER }?.text
    val lastJarvisMsg = messages.lastOrNull { it.sender == MessageSender.JARVIS }?.text

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        topBar = {
            HudTopBar(
                batteryInfo = batteryInfo,
                memoryInfo = memoryInfo,
                isListening = isListening,
                isSpeaking = isSpeaking,
                isThinkingMode = isThinkingMode,
                onToggleThinking = { viewModel.setThinkingMode(!isThinkingMode) },
                onLockApp = { viewModel.lockApp() }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = JarvisSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = JarvisCardBorder,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                MainNavTab.entries.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisCyan,
                            selectedTextColor = JarvisCyan,
                            indicatorColor = JarvisCyan.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainNavTab.HUD.ordinal -> {
                    HudDashboardTab(
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        batteryInfo = batteryInfo,
                        memoryInfo = memoryInfo,
                        isTorchOn = isTorchOn,
                        musicVolume = musicVolume,
                        maxVolume = viewModel.maxVolume,
                        lastVoiceCommand = lastUserMsg,
                        lastJarvisResponse = lastJarvisMsg,
                        onStartVoice = { viewModel.startListening() },
                        onStopVoice = { viewModel.stopListening() },
                        onToggleTorch = { viewModel.toggleTorchDirect() },
                        onVolumeChange = { viewModel.setVolumeDirect(it) },
                        onQuickCommand = { cmd -> viewModel.executeUserCommand(cmd, isVoice = false) }
                    )
                }
                MainNavTab.CHAT.ordinal -> {
                    VoiceChatTab(
                        messages = messages,
                        isGenerating = isGenerating,
                        isListening = isListening,
                        isThinkingMode = isThinkingMode,
                        onSendMessage = { text -> viewModel.executeUserCommand(text, isVoice = false) },
                        onStartVoice = { viewModel.startListening() },
                        onSpeakMessage = { text -> viewModel.speakText(text) },
                        onToggleThinking = { viewModel.setThinkingMode(!isThinkingMode) }
                    )
                }
                MainNavTab.ACTIONS.ordinal -> {
                    DeviceActionsTab(
                        isTorchOn = isTorchOn,
                        musicVolume = musicVolume,
                        maxVolume = viewModel.maxVolume,
                        onToggleTorch = { viewModel.toggleTorchDirect() },
                        onVolumeChange = { viewModel.setVolumeDirect(it) },
                        onVibrate = { viewModel.deviceManager.vibrate(350) },
                        onSendWhatsApp = { phone, msg ->
                            val sent = viewModel.deviceManager.sendWhatsAppMessage(phone, msg)
                            if (sent) viewModel.speakText("WhatsApp dispatch launched, Boss.")
                        },
                        onOpenWhatsApp = {
                            viewModel.deviceManager.openWhatsApp()
                        },
                        onExecuteAction = { actionType ->
                            viewModel.executeUserCommand("open ${actionType.name}", isVoice = false)
                        }
                    )
                }
                MainNavTab.VISION.ordinal -> {
                    VisionScreenTab(
                        visionState = visionState,
                        onAnalyzeScreen = { bmp, prompt ->
                            viewModel.analyzeScreenOrImage(bmp, prompt)
                        },
                        onSpeakResult = { text ->
                            viewModel.speakText(text)
                        }
                    )
                }
                MainNavTab.SECURITY.ordinal -> {
                    SecurityAndSyncTab(
                        isSecurityConfigured = viewModel.isSecurityConfigured.collectAsState().value,
                        encryptionManager = viewModel.encryptionManager,
                        currentUser = currentUser,
                        onToggleSecurity = { viewModel.toggleSecurity(it) },
                        onSetNewPin = { viewModel.setPin(it) },
                        onLockApp = { viewModel.lockApp() },
                        onSyncCloud = { viewModel.syncCloudData() }
                    )
                }
            }

            // HUD Alert Notification Banner
            AnimatedVisibility(
                visible = hudNotification != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, JarvisCyan, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = hudNotification ?: "",
                        color = JarvisCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LaunchedEffect(hudNotification) {
                    if (hudNotification != null) {
                        kotlinx.coroutines.delay(3500)
                        viewModel.dismissNotification()
                    }
                }
            }
        }
    }
}
