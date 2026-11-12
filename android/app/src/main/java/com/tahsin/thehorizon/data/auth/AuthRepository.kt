package com.tahsin.thehorizon.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the signed-in session. Refresh-token rotation means two
 * simultaneous refreshes would invalidate each other, so every refresh goes through [mutex].
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** Call once at startup. Offline starts keep the saved session and refresh later. */
    suspend fun restore() {
        mutex.withLock {
            val saved = store.load()
            if (saved == null) {
                _session.value = null
                return
            }
            if (!saved.isExpiring(clock())) {
                _session.value = saved
                return
            }
            try {
                adopt(api.refresh(saved.refreshToken))
            } catch (e: AuthException) {
                if (e.code == AuthErrorCode.NETWORK) {
                    _session.value = saved
                } else {
                    store.clear()
                    _session.value = null
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        val s = api.signIn(email.trim(), password)
        mutex.withLock { adopt(s) }
    }

    /** Returns null when signed in immediately, or the email awaiting a confirmation code. */
    suspend fun signUp(email: String, password: String, username: String, displayName: String): String? =
        when (val r = api.signUp(email.trim(), password, username.trim(), displayName.trim())) {
            is SignUpResult.SignedIn -> { mutex.withLock { adopt(r.session) }; null }
            is SignUpResult.ConfirmationRequired -> r.email
        }

    suspend fun confirmSignUp(email: String, code: String) {
        val s = api.verifyOtp(email.trim(), code.trim(), "signup")
        mutex.withLock { adopt(s) }
    }

    suspend fun requestPasswordReset(email: String) = api.recover(email.trim())

    /** Verifies the emailed recovery code, sets the new password, and signs the user in. */
    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val s = api.verifyOtp(email.trim(), code.trim(), "recovery")
        api.updatePassword(s.accessToken, newPassword)
        mutex.withLock { adopt(s) }
    }

    suspend fun usernameAvailable(username: String): Boolean = api.usernameAvailable(username.trim())

    suspend fun validAccessToken(): String = mutex.withLock {
        val current = _session.value ?: throw AuthException(AuthErrorCode.NOT_SIGNED_IN, "Please sign in.")
        if (!current.isExpiring(clock())) return@withLock current.accessToken
        val fresh = try {
            api.refresh(current.refreshToken)
        } catch (e: AuthException) {
            if (e.code != AuthErrorCode.NETWORK && e.code != AuthErrorCode.RATE_LIMITED) {
                store.clear()
                _session.value = null
            }
            throw e
        }
        adopt(fresh).accessToken
    }

    suspend fun signOut() {
        _session.value?.let { s -> try { api.signOut(s.accessToken) } catch (e: AuthException) { /* best effort */ } }
        mutex.withLock {
            store.clear()
            _session.value = null
        }
    }

    private fun adopt(s: Session): Session {
        store.save(s)
        _session.value = s
        return s
    }
}
