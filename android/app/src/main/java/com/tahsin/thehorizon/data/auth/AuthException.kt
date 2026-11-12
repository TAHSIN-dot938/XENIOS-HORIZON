package com.tahsin.thehorizon.data.auth

enum class AuthErrorCode {
    INVALID_CREDENTIALS, EMAIL_NOT_CONFIRMED, USER_EXISTS, USERNAME_TAKEN, WEAK_PASSWORD,
    INVALID_CODE, RATE_LIMITED, NETWORK, NOT_SIGNED_IN, UNKNOWN,
}

/** [message] is already user-friendly and safe to show in the UI. */
class AuthException(val code: AuthErrorCode, message: String) : Exception(message)
