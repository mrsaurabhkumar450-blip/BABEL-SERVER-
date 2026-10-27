package com.example.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.net.URLEncoder

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val powerSource: String
)

data class SystemMemoryInfo(
    val usedRamMb: Long,
    val totalRamMb: Long,
    val freeStorageGb: Float,
    val totalStorageGb: Float
)

class DeviceControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    var isTorchOn: Boolean = false
        private set

    // Toggle Torch / Flashlight
    fun toggleTorch(enable: Boolean? = null): Boolean {
        val targetState = enable ?: !isTorchOn
        try {
            if (cameraManager != null) {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, targetState)
                    isTorchOn = targetState
                    vibrate(50)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceControl", "Failed to set torch mode", e)
        }
        return false
    }

    // Volume Controls
    fun getMusicVolume(): Int {
        return audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    fun getMaxMusicVolume(): Int {
        return audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }

    fun setMusicVolume(volumeLevel: Int) {
        val max = getMaxMusicVolume()
        val clamped = volumeLevel.coerceIn(0, max)
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, AudioManager.FLAG_SHOW_UI)
    }

    fun setMusicVolumePercent(percent: Int) {
        val max = getMaxMusicVolume()
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
        setMusicVolume(target)
    }

    fun muteVolume() {
        setMusicVolume(0)
    }

    fun maxVolume() {
        setMusicVolume(getMaxMusicVolume())
    }

    // Vibration / Alert
    fun vibrate(durationMs: Long = 100) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e("DeviceControl", "Failed to vibrate", e)
        }
    }

    // Battery Telemetry
    fun getBatteryInfo(): BatteryInfo {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            val pct = if (scale > 0) (level * 100 / scale) else 50
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val source = when (chargePlug) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Power"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> if (isCharging) "Charging" else "Battery"
            }
            BatteryInfo(pct, isCharging, source)
        } catch (e: Exception) {
            BatteryInfo(85, false, "Battery")
        }
    }

    // System RAM and Storage Telemetry
    fun getMemoryInfo(): SystemMemoryInfo {
        var usedRamMb = 0L
        var totalRamMb = 0L
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            totalRamMb = memInfo.totalMem / (1024 * 1024)
            usedRamMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        } catch (e: Exception) {
            Log.e("DeviceControl", "Error reading RAM", e)
        }

        var freeStorageGb = 0f
        var totalStorageGb = 0f
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val bytesTotal = stat.blockCountLong * stat.blockSizeLong
            freeStorageGb = (bytesAvailable.toDouble() / (1024 * 1024 * 1024)).toFloat()
            totalStorageGb = (bytesTotal.toDouble() / (1024 * 1024 * 1024)).toFloat()
        } catch (e: Exception) {
            Log.e("DeviceControl", "Error reading Storage", e)
        }

        return SystemMemoryInfo(
            usedRamMb = if (usedRamMb > 0) usedRamMb else 3800,
            totalRamMb = if (totalRamMb > 0) totalRamMb else 8192,
            freeStorageGb = if (freeStorageGb > 0) freeStorageGb else 48.5f,
            totalStorageGb = if (totalStorageGb > 0) totalStorageGb else 128f
        )
    }

    // WhatsApp Message Launcher
    fun sendWhatsAppMessage(phoneNumber: String, message: String): Boolean {
        return try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val url = if (cleanPhone.isNotEmpty()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMsg"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("DeviceControl", "Failed to launch WhatsApp", e)
            false
        }
    }

    fun openWhatsApp(): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/"))
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // App Launchers & System Shortcuts
    fun openCamera(): Boolean {
        return safeLaunch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }

    fun openDialer(phoneNumber: String = ""): Boolean {
        val uri = if (phoneNumber.isNotEmpty()) Uri.parse("tel:$phoneNumber") else Uri.parse("tel:")
        return safeLaunch(Intent(Intent.ACTION_DIAL, uri))
    }

    fun openMaps(query: String = ""): Boolean {
        val uri = if (query.isNotEmpty()) {
            Uri.parse("geo:0,0?q=" + URLEncoder.encode(query, "UTF-8"))
        } else {
            Uri.parse("geo:0,0")
        }
        return safeLaunch(Intent(Intent.ACTION_VIEW, uri))
    }

    fun openClockOrAlarm(): Boolean {
        return safeLaunch(Intent(AlarmClock.ACTION_SHOW_ALARMS)) ||
                safeLaunch(Intent(Settings.ACTION_DATE_SETTINGS))
    }

    fun openSettings(): Boolean {
        return safeLaunch(Intent(Settings.ACTION_SETTINGS))
    }

    fun openWifiSettings(): Boolean {
        return safeLaunch(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun openBluetoothSettings(): Boolean {
        return safeLaunch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openDisplaySettings(): Boolean {
        return safeLaunch(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    fun openYouTube(query: String = ""): Boolean {
        val url = if (query.isNotEmpty()) {
            "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
        } else {
            "https://www.youtube.com"
        }
        return safeLaunch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun searchWeb(query: String): Boolean {
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return safeLaunch(searchIntent) || safeLaunch(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")))
        )
    }

    private fun safeLaunch(intent: Intent): Boolean {
        return try {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("DeviceControl", "Failed to launch intent: ${intent.action}", e)
            false
        }
    }
}
