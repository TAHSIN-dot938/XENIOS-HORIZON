package com.tahsin.thehorizon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tahsin.thehorizon.data.auth.AuthException
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.profile.Profile
import com.tahsin.thehorizon.data.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object NotConfigured : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val profile: Profile) : SessionState
    data class ProfileError(val message: String) : SessionState
}

/** Root state holder: restores the saved session, then follows sign-in/sign-out. */
class SessionViewModel(
    configured: Boolean,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(if (configured) SessionState.Loading else SessionState.NotConfigured)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        if (configured) {
            viewModelScope.launch {
                auth.restore()
                // Only react to who is signed in, not to token refreshes.
                auth.session.map { it?.userId }.distinctUntilChanged().collectLatest { userId ->
                    if (userId == null) _state.value = SessionState.SignedOut else loadProfile(userId)
                }
            }
        }
    }

    private suspend fun loadProfile(userId: String) {
        _state.value = SessionState.Loading
        _state.value = try {
            SessionState.SignedIn(profiles.loadOwn(userId))
        } catch (e: AuthException) {
            if (auth.session.value == null) SessionState.SignedOut else SessionState.ProfileError(e.message ?: GENERIC)
        } catch (e: ApiException) {
            SessionState.ProfileError(e.message ?: GENERIC)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SessionState.ProfileError(GENERIC)
        }
    }

    fun retry() {
        val userId = auth.session.value?.userId ?: return
        viewModelScope.launch { loadProfile(userId) }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private companion object { const val GENERIC = "Something went wrong. Please try again." }
}
