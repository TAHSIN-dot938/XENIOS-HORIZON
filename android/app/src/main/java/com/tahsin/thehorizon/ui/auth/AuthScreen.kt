package com.tahsin.thehorizon.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tahsin.thehorizon.domain.Validators
import com.tahsin.thehorizon.ui.components.AmbientBackground
import com.tahsin.thehorizon.ui.components.GlassCard
import com.tahsin.thehorizon.ui.components.HorizonButton
import com.tahsin.thehorizon.ui.components.HorizonTextField
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

private enum class AuthMode { LOGIN, SIGNUP }

/**
 * Phase 2: UI + real validation only. Submitting valid input shows an honest notice;
 * the Supabase call is wired in Phase 3.
 */
@Composable
fun AuthScreen() {
    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    val signup = mode == AuthMode.SIGNUP
    val emailError = Validators.email(email)
    val usernameError = if (signup) Validators.username(username) else null
    val passwordError = if (signup) Validators.newPassword(password) else Validators.existingPassword(password)
    val valid = emailError == null && usernameError == null && passwordError == null

    AmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "THE HORIZON",
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp),
                color = HorizonColors.TextPrimary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (signup) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.bodyMedium,
                color = HorizonColors.TextSecondary,
            )
            Spacer(Modifier.height(Spacing.lg))

            GlassCard(Modifier.padding(bottom = Spacing.md)) {
                HorizonTextField(
                    value = email,
                    onValueChange = { email = it; notice = null },
                    label = "Email",
                    error = if (showErrors) emailError else null,
                    keyboardType = KeyboardType.Email,
                )
                AnimatedVisibility(visible = signup) {
                    Column {
                        Spacer(Modifier.height(Spacing.sm))
                        HorizonTextField(
                            value = username,
                            onValueChange = { username = it; notice = null },
                            label = "Username",
                            error = if (showErrors) usernameError else null,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                HorizonTextField(
                    value = password,
                    onValueChange = { password = it; notice = null },
                    label = "Password",
                    error = if (showErrors) passwordError else null,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(Spacing.md))
                HorizonButton(
                    text = if (signup) "CREATE ACCOUNT" else "SIGN IN",
                    onClick = {
                        showErrors = true
                        notice = if (valid) "Input is valid. Account connection is added in the next build phase." else null
                    },
                )
                notice?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.PurpleSoft, textAlign = TextAlign.Center)
                }
            }

            TextButton(onClick = {
                mode = if (signup) AuthMode.LOGIN else AuthMode.SIGNUP
                showErrors = false
                notice = null
            }) {
                Text(
                    if (signup) "Already have an account? Sign in" else "New here? Create an account",
                    color = HorizonColors.PurpleSoft,
                )
            }
        }
    }
}
