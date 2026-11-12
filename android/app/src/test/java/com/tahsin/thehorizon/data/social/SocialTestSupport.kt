package com.tahsin.thehorizon.data.social

import com.tahsin.thehorizon.data.auth.AuthApi
import com.tahsin.thehorizon.data.auth.AuthRepository
import com.tahsin.thehorizon.data.auth.FakeHttpClient
import com.tahsin.thehorizon.data.auth.MemoryStore
import com.tahsin.thehorizon.data.auth.Session
import com.tahsin.thehorizon.data.auth.SessionStore
import com.tahsin.thehorizon.data.rest.SupabaseRest
import kotlinx.coroutines.runBlocking

/** An AuthRepository that is already signed in (token "user-token") and never touches the network. */
fun signedInAuth(nowSec: Long = 1_000, expiresAt: Long = 100_000): AuthRepository {
    val store = MemoryStore()
    SessionStore(store).save(Session("user-token", "refresh", expiresAt, "me", "a@b.co"))
    val api = AuthApi(FakeHttpClient { error("auth endpoint must not be called") }, "https://x.supabase.co", "anon") { nowSec }
    return AuthRepository(api, SessionStore(store), { nowSec }).also { runBlocking { it.restore() } }
}

fun restFor(http: FakeHttpClient) = SupabaseRest(http, "https://x.supabase.co/", "anon", signedInAuth())

fun userJson(id: String, number: String, username: String, name: String, style: Int = 0) =
    """{"id":"$id","user_number":"$number","username":"$username","display_name":"$name","avatar_style":$style}"""

fun friendshipJson(id: String, status: String, createdAt: String, requester: String, addressee: String) =
    """{"id":"$id","status":"$status","created_at":"$createdAt","requester":$requester,"addressee":$addressee}"""

val ME = userJson("me", "00000000000001", "me_u", "Me")
val BOB = userJson("bob", "00000000000002", "bob", "Bob", 2)
val CAROL = userJson("carol", "00000000000003", "carol", "carol the third", 3)
val DAVE = userJson("dave", "00000000000004", "dave", "Dave", 4)
