package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.BodyMetricsViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.BodyMetricsTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: BodyMetricsViewModel by viewModels()

    private var backgroundTimestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Observe secure screen flag
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSecureScreenEnabled.collect { isSecure ->
                    if (isSecure) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        // On first launch, if biometric lock is enabled, lock app and prompt unlock
        if (viewModel.isBiometricLockEnabled.value) {
            viewModel.lockApp()
            promptBiometricAuthentication()
        }

        setContent {
            BodyMetricsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = viewModel,
                        onPromptUnlock = { promptBiometricAuthentication() }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isBiometricLockEnabled.value) {
            val now = System.currentTimeMillis()
            // Lock if returned after 1 minute (60,000 ms) in background
            if (backgroundTimestamp > 0L && (now - backgroundTimestamp) >= 60_000L) {
                viewModel.lockApp()
                promptBiometricAuthentication()
            }
        }
    }

    private fun promptBiometricAuthentication() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.unlockApp()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Remains locked; user can tap "Разблокировать" button on the lock screen
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Remains locked
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход в BodyMetrics")
            .setSubtitle("Подтвердите личность для доступа к вашим данным")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (_: Exception) {
            // If biometric hardware or lock screen security is not set up on device, allow graceful unlock
            viewModel.unlockApp()
        }
    }
}
