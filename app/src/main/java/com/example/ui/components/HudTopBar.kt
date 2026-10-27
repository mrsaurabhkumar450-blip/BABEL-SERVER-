package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.device.BatteryInfo
import com.example.device.SystemMemoryInfo
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun HudTopBar(
    batteryInfo: BatteryInfo,
    memoryInfo: SystemMemoryInfo,
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinkingMode: Boolean,
    onToggleThinking: () -> Unit,
    onLockApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        isListening -> "VOICE INPUT ACTIVE"
        isSpeaking -> "VOCALIZING"
        else -> "SYSTEM READY"
    }

    val statusColor = when {
        isListening -> JarvisCyan
        isSpeaking -> JarvisAmber
        else -> JarvisNeonGreen
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JarvisBackground.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Jarvis Core Identifier & Live State Dot
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "J.A.R.V.I.S.",
                color = JarvisCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(JarvisSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Right: Telemetry Chips & Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Thinking Mode Toggle Pill
            Row(
                modifier = Modifier
                    .testTag("thinking_mode_toggle")
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isThinkingMode) JarvisCyan.copy(alpha = 0.2f) else JarvisSurface)
                    .border(
                        1.dp,
                        if (isThinkingMode) JarvisCyan else JarvisCardBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onToggleThinking() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Thinking Mode",
                    tint = if (isThinkingMode) JarvisCyan else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isThinkingMode) "THINKING" else "FAST",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isThinkingMode) JarvisCyan else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }

            // Battery Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurface)
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    contentDescription = "Battery",
                    tint = if (batteryInfo.percentage <= 20) Color(0xFFEF4444) else JarvisNeonGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${batteryInfo.percentage}%",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quick Lock Icon Button
            IconButton(
                onClick = onLockApp,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("lock_app_button")
                    .background(JarvisSurface, CircleShape)
                    .border(1.dp, JarvisCardBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Jarvis",
                    tint = JarvisCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
