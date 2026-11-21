package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.auth.VaultAuthSnapshot
import com.example.ui.components.PrivacyShieldBadge
import com.example.ui.components.SpecBadge
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    authSnapshot: VaultAuthSnapshot,
    errorMessage: String?,
    isSubmitting: Boolean,
    onRegister: (number: String, password: String, confirmPassword: String) -> Unit,
    onLogin: (number: String, password: String) -> Unit,
    onResetAccount: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var numberInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmPasswordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val numberFocusRequester = remember { FocusRequester() }

    // Live cooldown ticker if brute-force lockout is active
    var lockoutRemainingSec by remember(authSnapshot.lockoutUntilMs) {
        val diff = ((authSnapshot.lockoutUntilMs - System.currentTimeMillis() + 999L) / 1000L).toInt()
        mutableIntStateOf(diff.coerceAtLeast(0))
    }

    LaunchedEffect(authSnapshot.lockoutUntilMs) {
        while (true) {
            val remaining = ((authSnapshot.lockoutUntilMs - System.currentTimeMillis() + 999L) / 1000L).toInt()
            if (remaining <= 0) {
                lockoutRemainingSec = 0
                break
            }
            lockoutRemainingSec = remaining
            delay(500L)
        }
    }

    LaunchedEffect(authSnapshot.isLoaded) {
        if (authSnapshot.isLoaded) {
            runCatching { numberFocusRequester.requestFocus() }
        }
    }

    fun submitForm() {
        if (isSubmitting || lockoutRemainingSec > 0) return
        if (authSnapshot.isAccountRegistered) {
            onLogin(numberInput, passwordInput)
        } else {
            onRegister(numberInput, passwordInput, confirmPasswordInput)
        }
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
        )
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        val isDesktopWide = maxWidth >= 840.dp

        if (!authSnapshot.isLoaded) {
            CircularProgressIndicator(modifier = Modifier.testTag("auth_loading_indicator"))
            return@BoxWithConstraints
        }

        if (isDesktopWide) {
            // PC / Desktop Side-by-Side Layout
            Row(
                modifier = Modifier
                    .widthIn(max = 980.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginBrandHero(
                    isAccountRegistered = authSnapshot.isAccountRegistered,
                    modifier = Modifier.weight(1f),
                )
                LoginFormCard(
                    authSnapshot = authSnapshot,
                    numberInput = numberInput,
                    passwordInput = passwordInput,
                    confirmPasswordInput = confirmPasswordInput,
                    passwordVisible = passwordVisible,
                    errorMessage = errorMessage,
                    lockoutRemainingSec = lockoutRemainingSec,
                    isSubmitting = isSubmitting,
                    numberFocusRequester = numberFocusRequester,
                    onNumberChange = {
                        numberInput = it
                        onClearError()
                    },
                    onPasswordChange = {
                        passwordInput = it
                        onClearError()
                    },
                    onConfirmPasswordChange = {
                        confirmPasswordInput = it
                        onClearError()
                    },
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                    onNextFocus = { focusManager.moveFocus(FocusDirection.Down) },
                    onSubmit = ::submitForm,
                    onRequestReset = { showResetDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // Mobile & Compact Tablet Centered Layout
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LoginBrandHero(isAccountRegistered = authSnapshot.isAccountRegistered)
                LoginFormCard(
                    authSnapshot = authSnapshot,
                    numberInput = numberInput,
                    passwordInput = passwordInput,
                    confirmPasswordInput = confirmPasswordInput,
                    passwordVisible = passwordVisible,
                    errorMessage = errorMessage,
                    lockoutRemainingSec = lockoutRemainingSec,
                    isSubmitting = isSubmitting,
                    numberFocusRequester = numberFocusRequester,
                    onNumberChange = {
                        numberInput = it
                        onClearError()
                    },
                    onPasswordChange = {
                        passwordInput = it
                        onClearError()
                    },
                    onConfirmPasswordChange = {
                        confirmPasswordInput = it
                        onClearError()
                    },
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                    onNextFocus = { focusManager.moveFocus(FocusDirection.Down) },
                    onSubmit = ::submitForm,
                    onRequestReset = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Vault Account?") },
            text = {
                Text(
                    "To protect your privacy, resetting your number and password will permanently wipe all saved transcriptions from this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        numberInput = ""
                        passwordInput = ""
                        confirmPasswordInput = ""
                        onResetAccount()
                    },
                    modifier = Modifier.testTag("confirm_reset_vault_button"),
                ) {
                    Text("Reset & Wipe", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LoginBrandHero(
    isAccountRegistered: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "VaultScribe Security Shield",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column {
                Text(
                    text = "VaultScribe",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                PrivacyShieldBadge(text = "Offline Vault Lock • Mobile & PC")
            }
        }

        Text(
            text = if (isAccountRegistered) {
                "Sign in with your number and password to access your transcripts, audio decoder, and local export vault."
            } else {
                "Create a local number and password lock so only you can open saved Bangla, English, and Hindi transcriptions on this device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecBadge(label = "PBKDF2-SHA256", accentColor = MaterialTheme.colorScheme.primary)
            SpecBadge(label = "Zero Cloud Auth", accentColor = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun LoginFormCard(
    authSnapshot: VaultAuthSnapshot,
    numberInput: String,
    passwordInput: String,
    confirmPasswordInput: String,
    passwordVisible: Boolean,
    errorMessage: String?,
    lockoutRemainingSec: Int,
    isSubmitting: Boolean,
    numberFocusRequester: FocusRequester,
    onNumberChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onNextFocus: () -> Unit,
    onSubmit: () -> Unit,
    onRequestReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRegistered = authSnapshot.isAccountRegistered

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                val readyToSubmit = if (isRegistered) {
                    numberInput.isNotBlank() && passwordInput.isNotBlank()
                } else {
                    numberInput.isNotBlank() && passwordInput.isNotBlank() && confirmPasswordInput.isNotBlank()
                }
                if (readyToSubmit) {
                    onSubmit()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isRegistered) "Unlock Vault" else "Create Vault Login",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            if (isRegistered && authSnapshot.maskedNumberHint.isNotEmpty()) {
                Text(
                    text = "Registered number ending in ${authSnapshot.maskedNumberHint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = numberInput,
                onValueChange = { raw ->
                    // Allow digits and standard phone separators (+, -, space)
                    val filtered = raw.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                    onNumberChange(filtered)
                },
                label = { Text("Phone or Vault Number") },
                placeholder = { Text("e.g. 01712345678 or 4829") },
                leadingIcon = { Icon(Icons.Filled.Dialpad, contentDescription = "Number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNextFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(numberFocusRequester)
                    .testTag("login_number_input"),
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = onPasswordChange,
                label = { Text(if (isRegistered) "Password" else "Password (min 6 characters)") },
                leadingIcon = { Icon(Icons.Filled.Password, contentDescription = "Password") },
                trailingIcon = {
                    IconButton(
                        onClick = onTogglePasswordVisibility,
                        modifier = Modifier.testTag("toggle_password_visibility"),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isRegistered) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onNextFocus() },
                    onDone = { onSubmit() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
            )

            if (!isRegistered) {
                OutlinedTextField(
                    value = confirmPasswordInput,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Filled.Password, contentDescription = "Confirm password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_confirm_password_input"),
                )
            }

            val displayError = when {
                lockoutRemainingSec > 0 -> "Too many failed attempts. Locked for ${lockoutRemainingSec}s."
                !errorMessage.isNullOrBlank() -> errorMessage
                else -> null
            }

            AnimatedVisibility(visible = displayError != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = displayError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("login_error_text"),
                        )
                    }
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && lockoutRemainingSec == 0,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("login_submit_button"),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        lockoutRemainingSec > 0 -> "Locked (${lockoutRemainingSec}s)"
                        isRegistered -> "Unlock Vault"
                        else -> "Save & Enter Studio"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (isRegistered) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onRequestReset,
                        modifier = Modifier.testTag("reset_vault_button"),
                    ) {
                        Text(
                            text = "Forgot password? Reset Vault",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
