package com.tahsin.thehorizon.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tahsin.thehorizon.data.auth.AuthException
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.domain.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { LOGIN, SIGNUP, CONFIRM, FORGOT, RESET }

data class AuthUiState(
    val step: AuthStep = AuthStep.LOGIN,
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val code: String = "",
    val loading: Boolean = false,
    val showErrors: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val emailError: String? get() = Validators.email(email)
    val usernameError: String? get() = Validators.username(username)
    val codeError: String? get() = Validators.code(code)
    val passwordError: String?
        get() = if (step == AuthStep.SIGNUP || step == AuthStep.RESET) Validators.newPassword(password)
        else Validators.existingPassword(password)

    val isValid: Boolean
        get() = when (step) {
            AuthStep.LOGIN -> emailError == null && passwordError == null
            AuthStep.SIGNUP -> emailError == null && usernameError == null && passwordError == null
            AuthStep.CONFIRM -> codeError == null
            AuthStep.FORGOT -> emailError == null
            AuthStep.RESET -> codeError == null && passwordError == null
        }
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onCode(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(10), error = null) }

    fun goTo(step: AuthStep) = _state.update {
        it.copy(step = step, password = "", code = "", showErrors = false, error = null, info = null, loading = false)
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        if (!s.isValid) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(loading = true, showErrors = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                when (s.step) {
                    AuthStep.LOGIN -> auth.signIn(s.email, s.password)
                    AuthStep.SIGNUP -> {
                        if (!auth.usernameAvailable(s.username)) {
                            fail("That username is taken. Try another one.")
                            return@launch
                        }
                        val pendingEmail = auth.signUp(s.email, s.password, s.username, s.username)
                        if (pendingEmail != null) {
                            _state.update {
                                it.copy(step = AuthStep.CONFIRM, loading = false, password = "", showErrors = false,
                                    info = "We emailed a code to $pendingEmail. Enter it below.")
                            }
                            return@launch
                        }
                    }
                    AuthStep.CONFIRM -> auth.confirmSignUp(s.email, s.code)
                    AuthStep.FORGOT -> {
                        auth.requestPasswordReset(s.email)
                        _state.update {
                            it.copy(step = AuthStep.RESET, loading = false, showErrors = false,
                                info = "If an account exists for that email, a code is on its way.")
                        }
                        return@launch
                    }
                    AuthStep.RESET -> auth.resetPassword(s.email, s.code, s.password)
                }
                // Signed in: SessionViewModel swaps the screen; wipe sensitive fields.
                _state.value = AuthUiState()
            } catch (e: AuthException) {
                fail(e.message ?: GENERIC)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(GENERIC)
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(loading = false, error = message) }

    private companion object { const val GENERIC = "Something went wrong. Please try again." }
}
