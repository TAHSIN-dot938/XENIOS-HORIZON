package com.tahsin.thehorizon

import android.content.Context
import com.tahsin.thehorizon.data.auth.AuthApi
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.auth.SessionStore
import com.tahsin.thehorizon.data.http.OkHttpHttpClient
import com.tahsin.thehorizon.data.profile.ProfileRepository
import com.tahsin.thehorizon.data.security.SecureStore

/** Manual dependency container: smaller APK and faster cold start than a DI framework. */
class AppContainer(context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trim()
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

    /** False until SUPABASE_URL / SUPABASE_ANON_KEY are set in android/local.properties. */
    val isConfigured: Boolean = baseUrl.startsWith("https://") && anonKey.isNotEmpty()

    private val clock: () -> Long = { System.currentTimeMillis() / 1000 }
    private val http = OkHttpHttpClient()

    val authRepository: AuthRepository by lazy {
        AuthRepository(AuthApi(http, baseUrl, anonKey, clock), SessionStore(SecureStore(context)), clock)
    }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(http, baseUrl, anonKey, authRepository) }
}
