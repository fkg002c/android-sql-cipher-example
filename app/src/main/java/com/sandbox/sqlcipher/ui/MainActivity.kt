package com.sandbox.sqlcipher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandbox.sqlcipher.ui.lock.PermissionsScreen
import com.sandbox.sqlcipher.ui.lock.PinEntryScreen
import com.sandbox.sqlcipher.ui.lock.PinSetupScreen
import com.sandbox.sqlcipher.ui.main.MainScreen
import com.sandbox.sqlcipher.ui.theme.SQLCipherSandboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SQLCipherSandboxTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(appViewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Поворот экрана не блокирует, уход в фон/другое приложение — блокирует
        if (!isChangingConfigurations) appViewModel.lock()
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.stage) {
        Stage.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Stage.PERMISSIONS -> PermissionsScreen(onDone = viewModel::onPermissionsHandled)
        Stage.SETUP_PIN -> PinSetupScreen(onPinSet = viewModel::setPin)
        Stage.LOCKED -> PinEntryScreen(
            error = state.pinError,
            onSubmit = viewModel::unlock,
            onInput = viewModel::clearPinError,
        )
        Stage.UNLOCKED -> MainScreen(
            destination = state.destination,
            onNavigate = viewModel::navigate,
        )
    }
}
