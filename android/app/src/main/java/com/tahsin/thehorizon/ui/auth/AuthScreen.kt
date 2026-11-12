package com.tahsin.thehorizon.ui.auth

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tahsin.thehorizon.ui.components.AmbientBackground
import com.tahsin.thehorizon.ui.components.GlassCard
import com.tahsin.thehorizon.ui.components.HorizonButton
import com.tahsin.thehorizon.ui.components.HorizonTextField
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    val subtitle = when (s.step) {
        AuthStep.LOGIN -> "Welcome back"
        AuthStep.SIGNUP -> "Create your account"
        AuthStep.CONFIRM -> "Confirm your email"
        AuthStep.FORGOT -> "Reset your password"
        AuthStep.RESET -> "Choose a new password"
    }
    val button = when (s.step) {
        AuthStep.LOGIN -> "SIGN IN"
        AuthStep.SIGNUP -> "CREATE ACCOUNT"
        AuthStep.CONFIRM -> "VERIFY"
        AuthStep.FORGOT -> "SEND CODE"
        AuthStep.RESET -> "SET PASSWORD"
    }
    val showEmail = s.step == AuthStep.LOGIN || s.step == AuthStep.SIGNUP || s.step == AuthStep.FORGOT
    val showUsername = s.step == AuthStep.SIGNUP
    val showPassword = s.step != AuthStep.CONFIRM && s.step != AuthStep.FORGOT
    val showCode = s.step == AuthStep.CONFIRM || s.step == AuthStep.RESET

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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.TextSecondary)
            Spacer(Modifier.height(Spacing.lg))

            GlassCard(Modifier.padding(bottom = Spacing.md)) {
                s.info?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.PurpleSoft)
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (showEmail) {
                    HorizonTextField(
                        value = s.email, onValueChange = viewModel::onEmail, label = "Email",
                        error = if (s.showErrors) s.emailError else null, keyboardType = KeyboardType.Email,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (showUsername) {
                    HorizonTextField(
                        value = s.username, onValueChange = viewModel::onUsername, label = "Username",
                        error = if (s.showErrors) s.usernameError else null,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (showCode) {
                    HorizonTextField(
                        value = s.code, onValueChange = viewModel::onCode, label = "Code from your email",
                        error = if (s.showErrors) s.codeError else null, keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (showPassword) {
                    HorizonTextField(
                        value = s.password, onValueChange = viewModel::onPassword,
                        label = if (s.step == AuthStep.RESET) "New password" else "Password",
                        error = if (s.showErrors) s.passwordError else null,
                        isPassword = true, imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                s.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = HorizonColors.Danger, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Spacing.sm))
                }
                HorizonButton(text = button, onClick = viewModel::submit, loading = s.loading)
            }

            when (s.step) {
                AuthStep.LOGIN -> {
                    TextButton(onClick = { viewModel.goTo(AuthStep.FORGOT) }) {
                        Text("Forgot password?", color = HorizonColors.TextSecondary)
                    }
                    TextButton(onClick = { viewModel.goTo(AuthStep.SIGNUP) }) {
                        Text("New here? Create an account", color = HorizonColors.PurpleSoft)
                    }
                }
                AuthStep.SIGNUP -> TextButton(onClick = { viewModel.goTo(AuthStep.LOGIN) }) {
                    Text("Already have an account? Sign in", color = HorizonColors.PurpleSoft)
                }
                else -> TextButton(onClick = { viewModel.goTo(AuthStep.LOGIN) }) {
                    Text("Back to sign in", color = HorizonColors.PurpleSoft)
                }
            }
        }
    }
}
