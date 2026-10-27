package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.JarvisViewModel
import com.example.ui.screens.JarvisMainScreen
import com.example.ui.screens.LockScreen
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = JarvisBackground
                ) {
                    JarvisAppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
fun JarvisAppRoot(viewModel: JarvisViewModel) {
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val isSecurityConfigured by viewModel.isSecurityConfigured.collectAsState()

    // Request necessary runtime permissions for voice recording and camera/torch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Gracefully handled in viewModel
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        )
    }

    if (!isUnlocked) {
        LockScreen(
            hasPinConfigured = isSecurityConfigured,
            onUnlockWithPin = { pin -> viewModel.unlockWithPin(pin) },
            onSetPin = { pin -> viewModel.setPin(pin) },
            onBypassForDemo = { viewModel.toggleSecurity(false) }
        )
    } else {
        JarvisMainScreen(viewModel = viewModel)
    }
}
