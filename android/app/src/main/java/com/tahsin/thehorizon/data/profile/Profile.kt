package com.tahsin.thehorizon.data.profile

data class Profile(
    val id: String,
    /** Unique, zero-padded 14-digit ID assigned by the database. */
    val userNumber: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarStyle: Int,
)
