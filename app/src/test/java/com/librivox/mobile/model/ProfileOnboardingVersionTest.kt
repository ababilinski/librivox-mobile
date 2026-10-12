package com.librivox.mobile.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileOnboardingVersionTest {
    @Test fun legacyProfileNeedsCurrentSetupWithoutLosingItsIdentity() {
        val legacy = Json.decodeFromString<Profile>("""
            {"id":"existing-user","displayName":"Adrian","avatarSeed":"Plum","createdAtMillis":123}
        """.trimIndent())
        assertTrue(legacy.requiresOnboarding)
        val completed = legacy.copy(completedOnboardingVersion = Profile.CurrentOnboardingVersion)
        assertFalse(completed.requiresOnboarding)
        assertEquals(legacy.id, completed.id)
        assertEquals(legacy.displayName, completed.displayName)
        assertEquals(legacy.avatarSeed, completed.avatarSeed)
        assertEquals(legacy.createdAtMillis, completed.createdAtMillis)
        assertFalse(Json.decodeFromString<Profile>(Json.encodeToString(Profile.serializer(), completed)).requiresOnboarding)
    }
}
