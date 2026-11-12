package com.tahsin.thehorizon.data.auth

import com.tahsin.thehorizon.data.security.KeyValueStore

class SessionStore(private val store: KeyValueStore) {
    fun load(): Session? = store.get(KEY)?.let { Session.fromJson(it) }
    fun save(session: Session) = store.put(KEY, session.toJson())
    fun clear() = store.remove(KEY)

    private companion object { const val KEY = "session" }
}
