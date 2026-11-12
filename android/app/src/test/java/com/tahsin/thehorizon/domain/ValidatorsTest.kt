package com.tahsin.thehorizon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test fun emailAcceptsValidAndRejectsInvalid() {
        assertNull(Validators.email("ava@example.com"))
        assertEquals("Enter your email", Validators.email("  "))
        assertEquals("Enter a valid email address", Validators.email("ava@"))
    }

    @Test fun usernameRules() {
        assertNull(Validators.username("ava_01"))
        assertEquals("Choose a username", Validators.username(""))
        assertEquals("3–20 characters: letters, numbers, underscore", Validators.username("ab"))
        assertEquals("3–20 characters: letters, numbers, underscore", Validators.username("has space"))
        assertEquals("3–20 characters: letters, numbers, underscore", Validators.username("a".repeat(21)))
    }

    @Test fun passwordRules() {
        assertNull(Validators.newPassword("horizon42"))
        assertEquals("At least 8 characters", Validators.newPassword("abc123"))
        assertEquals("Include at least one letter", Validators.newPassword("12345678"))
        assertEquals("Include at least one number", Validators.newPassword("abcdefgh"))
        assertEquals("Enter your password", Validators.existingPassword(""))
    }

    @Test fun codeRules() {
        assertNull(Validators.code("123456"))
        assertNull(Validators.code(" 12345678 "))
        assertEquals("Enter the code from your email", Validators.code("12345"))
        assertEquals("Enter the code from your email", Validators.code("12ab56"))
    }
}
