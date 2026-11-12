package com.tahsin.thehorizon.domain

/** Client-side validation. The database enforces the same rules; this is for instant feedback. */
object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val USERNAME = Regex("^[A-Za-z0-9_]{3,20}$")

    fun email(value: String): String? = when {
        value.isBlank() -> "Enter your email"
        !EMAIL.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun username(value: String): String? = when {
        value.isBlank() -> "Choose a username"
        !USERNAME.matches(value.trim()) -> "3–20 characters: letters, numbers, underscore"
        else -> null
    }

    fun newPassword(value: String): String? = when {
        value.length < 8 -> "At least 8 characters"
        value.none { it.isLetter() } -> "Include at least one letter"
        value.none { it.isDigit() } -> "Include at least one number"
        else -> null
    }

    /** Emailed one-time code (Supabase default is 6 digits; configurable up to 10). */
    fun code(value: String): String? =
        if (value.trim().let { it.length in 6..10 && it.all(Char::isDigit) }) null else "Enter the code from your email"

    fun existingPassword(value: String): String? = if (value.isEmpty()) "Enter your password" else null
}
