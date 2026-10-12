package com.sandbox.sqlcipher.ui.lock

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sandbox.sqlcipher.R
import com.sandbox.sqlcipher.telephony.TelephonyPermissions

const val PIN_MIN_LENGTH = 4
const val PIN_MAX_LENGTH = 8

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Отказ не блокирует приложение: события будут писаться без номера
        onDone()
    }
    CenteredColumn(icon = Icons.Default.Phone, title = stringResource(R.string.permissions_title)) {
        Text(
            stringResource(R.string.permissions_text),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (TelephonyPermissions.allGranted(context)) onDone()
                else launcher.launch(TelephonyPermissions.required)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.permissions_grant)) }
        TextButton(onClick = onDone) { Text(stringResource(R.string.permissions_skip)) }
    }
}

@Composable
fun PinSetupScreen(onPinSet: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var mismatch by rememberSaveable { mutableStateOf(false) }
    val valid = pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && confirm.isNotEmpty()

    fun submit() {
        if (!valid) return
        if (pin == confirm) onPinSet(pin) else mismatch = true
    }

    CenteredColumn(icon = Icons.Default.Lock, title = stringResource(R.string.pin_setup_title)) {
        Text(
            stringResource(R.string.pin_setup_text, PIN_MIN_LENGTH, PIN_MAX_LENGTH),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        PinField(
            value = pin,
            onValueChange = { pin = it; mismatch = false },
            label = stringResource(R.string.pin_label),
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(8.dp))
        PinField(
            value = confirm,
            onValueChange = { confirm = it; mismatch = false },
            label = stringResource(R.string.pin_confirm_label),
            isError = mismatch,
            supportingText = if (mismatch) stringResource(R.string.pin_mismatch) else null,
            onDone = ::submit,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::submit, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pin_setup_button))
        }
    }
}

@Composable
fun PinEntryScreen(error: Boolean, onSubmit: (String) -> Unit, onInput: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    val valid = pin.length >= PIN_MIN_LENGTH

    fun submit() {
        if (!valid) return
        onSubmit(pin)
        pin = ""
    }

    CenteredColumn(icon = Icons.Default.Lock, title = stringResource(R.string.pin_entry_title)) {
        PinField(
            value = pin,
            onValueChange = { pin = it; if (error) onInput() },
            label = stringResource(R.string.pin_label),
            isError = error,
            supportingText = if (error) stringResource(R.string.pin_wrong) else null,
            onDone = ::submit,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::submit, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pin_entry_button))
        }
    }
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(PIN_MAX_LENGTH)
            onValueChange(digits)
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredColumn(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        content()
    }
}
