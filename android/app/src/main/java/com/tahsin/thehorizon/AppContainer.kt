package com.tahsin.thehorizon

import android.content.Context
import com.tahsin.thehorizon.data.auth.AuthApi
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.auth.SessionStore
import com.tahsin.thehorizon.data.http.OkHttpHttpClient
import com.tahsin.thehorizon.data.network.NetworkMonitor
import com.tahsin.thehorizon.data.profile.ProfileRepository
import com.tahsin.thehorizon.data.realtime.RealtimeClient
import com.tahsin.thehorizon.data.rest.SupabaseRest
import com.tahsin.thehorizon.data.security.SecureStore
import com.tahsin.thehorizon.data.social.SocialRepository
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container: smaller APK and faster cold start than a DI framework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val baseUrl = BuildConfig.SUPABASE_URL.trim()
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

    /** False until SUPABASE_URL / SUPABASE_ANON_KEY are set in android/local.properties. */
    val isConfigured: Boolean = baseUrl.startsWith("https://") && anonKey.isNotEmpty()

    private val clock: () -> Long = { System.currentTimeMillis() / 1000 }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
    private val http = OkHttpHttpClient(okHttp)
    /** WebSockets idle between heartbeats, so reads must never time out. */
    private val socketClient: OkHttpClient by lazy { okHttp.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(AuthApi(http, baseUrl, anonKey, clock), SessionStore(SecureStore(appContext)), clock)
    }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(http, baseUrl, anonKey, authRepository) }
    val rest: SupabaseRest by lazy { SupabaseRest(http, baseUrl, anonKey, authRepository) }
    val socialRepository: SocialRepository by lazy { SocialRepository(rest) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    fun newRealtime(scope: CoroutineScope): RealtimeClient = RealtimeClient(
        okHttp = socketClient,
        baseUrl = baseUrl,
        anonKey = anonKey,
        // A token handed to the long-lived socket must stay valid until its next refresh tick.
        tokenProvider = { authRepository.validAccessToken(minValiditySec = 15 * 60L) },
        networkAvailable = networkMonitor.online,
        scope = scope,
    )
}
