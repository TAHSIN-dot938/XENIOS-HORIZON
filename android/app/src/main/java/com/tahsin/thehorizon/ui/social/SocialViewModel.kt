package com.tahsin.thehorizon.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tahsin.thehorizon.data.auth.AuthException
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.http.ApiException
import com.tahsin.thehorizon.data.profile.Profile
import com.tahsin.thehorizon.data.realtime.RealtimeClient
import com.tahsin.thehorizon.data.realtime.RealtimeEvent
import com.tahsin.thehorizon.data.social.Friend
import com.tahsin.thehorizon.data.social.FriendRequest
import com.tahsin.thehorizon.data.social.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    /** Ids of users currently online. Only meaningful while [connected]. */
    val onlineIds: Set<String> = emptySet(),
    val connected: Boolean = false,
    /** Ids (request / friend ids) with an action in flight. */
    val busy: Set<String> = emptySet(),
    val toast: String? = null,
    /** Increments after every successful reload; lets other screens refresh. */
    val revision: Int = 0,
)

/**
 * Owns friends, requests and presence. Realtime runs only while the app is in the foreground
 * (saves battery, and makes "online" mean "actually using the app"). Realtime events are hints:
 * every change triggers a re-query, and every reconnect re-syncs, so nothing is lost offline.
 */
class SocialViewModel(
    private val auth: AuthRepository,
    private val repo: SocialRepository,
    private val realtimeFactory: (CoroutineScope) -> RealtimeClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    private var profile: Profile? = null
    private var foreground = false
    private var realtime: RealtimeClient? = null
    private var eventsJob: Job? = null
    private var refreshJob: Job? = null
    private var stopJob: Job? = null
    private var lastRefreshMs = 0L

    init {
        viewModelScope.launch {
            auth.session.map { it?.userId }.distinctUntilChanged().collect { if (it == null) reset() }
        }
    }

    fun bind(p: Profile) {
        if (profile?.id != p.id) {
            reset()
            profile = p
            _state.value = SocialUiState()
        }
        applyLifecycle()
    }

    /**
     * Foreground = visible. Going to the background tears the socket down after a short grace
     * period, so rotations and quick app switches don't cause reconnect churn, while a user who
     * really left the app stops costing battery and appears offline.
     */
    fun setForeground(value: Boolean) {
        foreground = value
        stopJob?.cancel(); stopJob = null
        if (value) {
            applyLifecycle()
        } else if (realtime != null) {
            stopJob = viewModelScope.launch { delay(STOP_GRACE_MS); applyLifecycle() }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    fun accept(r: FriendRequest) = act(r.id, "You and ${r.user.displayName} are now friends") { repo.respond(r.id, true) }
    fun reject(r: FriendRequest) = act(r.id, null) { repo.respond(r.id, false) }
    fun cancel(r: FriendRequest) = act(r.id, null) { repo.cancel(r.id) }
    fun remove(f: Friend) = act(f.friendshipId, "${f.user.displayName} was removed") { repo.removeFriend(f.user.id) }

    private fun act(id: String, success: String?, block: suspend () -> Unit) {
        if (id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            try {
                block()
                refresh()
                if (success != null) _state.update { it.copy(toast = success) }
            } catch (e: ApiException) {
                _state.update { it.copy(toast = e.message) }
            } catch (e: AuthException) {
                _state.update { it.copy(toast = e.message) }
            } finally {
                _state.update { it.copy(busy = it.busy - id) }
            }
        }
    }

    private suspend fun refresh() {
        val me = profile ?: return
        try {
            val snap = repo.load(me.id)
            lastRefreshMs = System.currentTimeMillis()
            _state.update {
                it.copy(
                    loading = false, loadError = null,
                    friends = snap.friends, incoming = snap.incoming, outgoing = snap.outgoing,
                    revision = it.revision + 1,
                )
            }
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, loadError = e.message) }
        } catch (e: AuthException) {
            _state.update { it.copy(loading = false, loadError = e.message) }
        }
    }

    private fun applyLifecycle() {
        val p = profile
        if (p != null && foreground) startRealtime(p) else stopRealtime()
    }

    private fun startRealtime(p: Profile) {
        if (realtime != null) return
        val client = realtimeFactory(viewModelScope)
        realtime = client
        // UNDISPATCHED: subscribe before the socket can emit its first event.
        eventsJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.collect { onEvent(it) }
        }
        client.start(p.id, listOf("friendships"), trackPresence = p.showOnline)
        viewModelScope.launch { refresh() }
    }

    private fun stopRealtime() {
        eventsJob?.cancel(); eventsJob = null
        realtime?.stop(); realtime = null
        refreshJob?.cancel(); refreshJob = null
        _state.update { it.copy(connected = false, onlineIds = emptySet()) }
    }

    private fun onEvent(e: RealtimeEvent) {
        when (e) {
            RealtimeEvent.Connected -> {
                _state.update { it.copy(connected = true) }
                // Catch up on anything missed while offline (skip if we just loaded).
                if (System.currentTimeMillis() - lastRefreshMs > RECENT_REFRESH_MS) viewModelScope.launch { refresh() }
            }
            RealtimeEvent.Disconnected -> _state.update { it.copy(connected = false, onlineIds = emptySet()) }
            is RealtimeEvent.PresenceSync -> _state.update { it.copy(onlineIds = e.onlineUserIds) }
            is RealtimeEvent.Changed -> if (e.table == "friendships") {
                refreshJob?.cancel()
                refreshJob = viewModelScope.launch { delay(400); refresh() }   // debounce bursts
            }
        }
    }

    private fun reset() {
        stopJob?.cancel(); stopJob = null
        stopRealtime()
        profile = null
        _state.value = SocialUiState()
    }

    override fun onCleared() {
        realtime?.stop()
    }

    private companion object {
        const val STOP_GRACE_MS = 8_000L
        const val RECENT_REFRESH_MS = 3_000L
    }
}
