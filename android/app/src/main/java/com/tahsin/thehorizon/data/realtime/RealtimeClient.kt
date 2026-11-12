package com.tahsin.thehorizon.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface RealtimeEvent {
    /** Both channels joined. */
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
    /** A row changed in [table]. Treated as a hint: callers re-query instead of trusting the payload. */
    data class Changed(val table: String, val type: String) : RealtimeEvent
    data class PresenceSync(val onlineUserIds: Set<String>) : RealtimeEvent
}

/**
 * Supabase Realtime over a plain OkHttp WebSocket (no extra library, zero cost).
 *
 * One socket, two channels (the same multiplexing supabase-js uses):
 *  - [PhoenixProtocol.DB_TOPIC]       Postgres Changes, filtered by RLS using the user's JWT.
 *  - [PhoenixProtocol.PRESENCE_TOPIC] shared presence; each user tracks under key = their user id.
 *
 * Battery/network behaviour: sleeps (zero wake-ups) while [networkAvailable] is false, reconnects
 * immediately when it returns, otherwise uses jittered exponential backoff. [okHttp] must have
 * readTimeout = 0 (the socket idles between heartbeats).
 */
class RealtimeClient(
    private val okHttp: OkHttpClient,
    baseUrl: String,
    private val anonKey: String,
    private val tokenProvider: suspend () -> String,
    private val networkAvailable: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val random: () -> Double = { Math.random() },
) {
    private val wsBase = baseUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()
    private var job: Job? = null

    @Synchronized
    fun start(userId: String, tables: List<String>, trackPresence: Boolean) {
        stop()
        job = scope.launch {
            var attempt = 0
            while (isActive) {
                networkAvailable.first { it }                 // sleep here while offline
                var joined = false
                try {
                    joined = runSession(userId, tables, trackPresence)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // token fetch or socket error: fall through to backoff
                }
                _events.tryEmit(RealtimeEvent.Disconnected)
                if (!networkAvailable.value) {
                    attempt = 0                               // connection lost with the network: retry instantly on return
                    continue
                }
                attempt = if (joined) 0 else attempt + 1
                delay(Backoff.delayMs(attempt, random()))
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Runs one socket until it closes. Returns true if both channels joined successfully. */
    private suspend fun runSession(userId: String, tables: List<String>, trackPresence: Boolean): Boolean = coroutineScope {
        val token = tokenProvider()
        val done = CompletableDeferred<Unit>()
        val dbJoined = AtomicBoolean(false)
        val presenceJoined = AtomicBoolean(false)
        val connectedEmitted = AtomicBoolean(false)
        val refs = AtomicInteger(0)
        val dbRef = refs.incrementAndGet().toString()
        val presenceRef = refs.incrementAndGet().toString()
        val presence = PresenceTracker()
        fun nextRef() = refs.incrementAndGet().toString()

        val request = Request.Builder().url("$wsBase/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0").build()
        val socket = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(PhoenixProtocol.encode(PhoenixProtocol.DB_TOPIC, "phx_join", PhoenixProtocol.joinPayload(tables, userId, token), dbRef))
                webSocket.send(PhoenixProtocol.encode(PhoenixProtocol.PRESENCE_TOPIC, "phx_join", PhoenixProtocol.joinPayload(emptyList(), userId, token), presenceRef))
            }

            private fun maybeConnected() {
                if (dbJoined.get() && presenceJoined.get() && connectedEmitted.compareAndSet(false, true)) {
                    _events.tryEmit(RealtimeEvent.Connected)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = PhoenixProtocol.decode(text) ?: return
                val ok = frame.payload.optString("status") == "ok"
                when (frame.topic) {
                    PhoenixProtocol.DB_TOPIC -> when (frame.event) {
                        "phx_reply" -> if (frame.ref == dbRef) {
                            if (ok) { dbJoined.set(true); maybeConnected() } else webSocket.close(1000, "db join rejected")
                        }
                        "postgres_changes" -> {
                            val data: JSONObject = frame.payload.optJSONObject("data") ?: return
                            _events.tryEmit(RealtimeEvent.Changed(data.optString("table"), data.optString("type")))
                        }
                        "phx_close", "phx_error" -> webSocket.close(1000, "channel closed")
                    }
                    PhoenixProtocol.PRESENCE_TOPIC -> when (frame.event) {
                        "phx_reply" -> if (frame.ref == presenceRef) {
                            if (ok) {
                                presenceJoined.set(true)
                                if (trackPresence) {
                                    webSocket.send(
                                        PhoenixProtocol.encode(
                                            PhoenixProtocol.PRESENCE_TOPIC, "presence",
                                            PhoenixProtocol.presenceTrackPayload(Instant.now().toString()), nextRef(),
                                        ),
                                    )
                                }
                                maybeConnected()
                            } else {
                                webSocket.close(1000, "presence join rejected")
                            }
                        }
                        "presence_state" -> {
                            presence.setState(frame.payload)
                            _events.tryEmit(RealtimeEvent.PresenceSync(presence.online()))
                        }
                        "presence_diff" -> {
                            presence.applyDiff(frame.payload)
                            _events.tryEmit(RealtimeEvent.PresenceSync(presence.online()))
                        }
                        "phx_close", "phx_error" -> webSocket.close(1000, "channel closed")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                done.complete(Unit)
            }
        })

        val heartbeat = launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                socket.send(PhoenixProtocol.encode(PhoenixProtocol.HEARTBEAT_TOPIC, "heartbeat", JSONObject(), nextRef()))
            }
        }
        val tokenRefresh = launch {
            while (isActive) {
                delay(TOKEN_REFRESH_MS)
                val fresh = try { tokenProvider() } catch (e: Exception) { continue }
                socket.send(PhoenixProtocol.encode(PhoenixProtocol.DB_TOPIC, "access_token", PhoenixProtocol.accessTokenPayload(fresh), nextRef()))
                socket.send(PhoenixProtocol.encode(PhoenixProtocol.PRESENCE_TOPIC, "access_token", PhoenixProtocol.accessTokenPayload(fresh), nextRef()))
            }
        }
        // Drop the socket the instant the network disappears instead of waiting for a TCP timeout.
        val networkWatch = launch {
            networkAvailable.first { !it }
            socket.cancel()
            done.complete(Unit)
        }
        try {
            done.await()
        } finally {
            heartbeat.cancel()
            tokenRefresh.cancel()
            networkWatch.cancel()
            socket.cancel()
        }
        dbJoined.get() && presenceJoined.get()
    }

    private companion object {
        const val HEARTBEAT_MS = 25_000L
        /** Paired with a 15-minute token margin in the container: a token sent here lives >= 5 more minutes. */
        const val TOKEN_REFRESH_MS = 10 * 60_000L
    }
}
