package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.security.BiometricAuthHelper

@Composable
fun VaultLockScreen(
    biometricEnabled: Boolean,
    pinError: String?,
    onVerifyPin: (String) -> Unit,
    onBiometricSuccess: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canUseBiometric = remember(context, biometricEnabled) {
        biometricEnabled && BiometricAuthHelper.canAuthenticate(context)
    }

    var enteredPin by remember { mutableStateOf("") }
    var biometricStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && activity != null) {
            BiometricAuthHelper.promptBiometricUnlock(
                activity = activity,
                onSuccess = onBiometricSuccess,
                onError = { msg -> biometricStatus = msg }
            )
        }
    }

    LaunchedEffect(pinError) {
        if (pinError != null) {
            enteredPin = ""
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Vault locked",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.unlock_vault_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Enter your 4–6 digit vault PIN to open your private reflections.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        val filled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                AnimatedVisibility(visible = pinError != null || biometricStatus != null) {
                    Text(
                        text = pinError ?: biometricStatus.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                // Keypad Grid
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "BIO" -> {
                                        if (canUseBiometric && activity != null) {
                                            KeypadCircleButton(
                                                tag = "vault_biometric_button",
                                                onClick = {
                                                    biometricStatus = null
                                                    BiometricAuthHelper.promptBiometricUnlock(
                                                        activity = activity,
                                                        onSuccess = onBiometricSuccess,
                                                        onError = { biometricStatus = it }
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Fingerprint,
                                                    contentDescription = "Unlock with biometrics",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(68.dp))
                                        }
                                    }

                                    "DEL" -> {
                                        KeypadCircleButton(
                                            tag = "vault_backspace_button",
                                            onClick = {
                                                onClearError()
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace PIN",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    else -> {
                                        KeypadCircleButton(
                                            tag = "vault_key_$key",
                                            onClick = {
                                                onClearError()
                                                biometricStatus = null
                                                if (enteredPin.length < 6) {
                                                    enteredPin += key
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { onVerifyPin(enteredPin) },
                    enabled = enteredPin.length >= 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("vault_unlock_button")
                ) {
                    Text(
                        text = "Unlock Folio",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadCircleButton(
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .size(68.dp)
            .minimumInteractiveComponentSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun PinSetupDialog(
    canUseBiometricHardware: Boolean,
    initialBiometric: Boolean,
    onDismiss: () -> Unit,
    onSavePin: (pin: String, enableBiometric: Boolean) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(canUseBiometricHardware && initialBiometric) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Vault PIN",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose a 4 to 6 digit numeric PIN. It is stored locally as a salted SHA-256 hash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            pin = input
                            validationError = null
                        }
                    },
                    label = { Text("Enter 4–6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_input")
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            confirmPin = input
                            validationError = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_pin_input")
                )
                if (canUseBiometricHardware) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enableBiometric = !enableBiometric }
                    ) {
                        Checkbox(
                            checked = enableBiometric,
                            onCheckedChange = { enableBiometric = it }
                        )
                        Text(
                            text = "Also allow fingerprint / face unlock",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length !in 4..6 -> validationError = "PIN must be 4 to 6 digits."
                        pin != confirmPin -> validationError = "PINs do not match."
                        else -> onSavePin(pin, enableBiometric)
                    }
                },
                modifier = Modifier.testTag("save_pin_confirm_button")
            ) {
                Text("Save Vault PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
