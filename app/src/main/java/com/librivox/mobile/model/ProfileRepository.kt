package com.librivox.mobile.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.audiobookProfileDataStore by preferencesDataStore(name = "audiobook_profile")

class ProfileRepository(private val context: Context) {

    val profile: Flow<Profile?> =
        context.audiobookProfileDataStore.data
            .map { preferences -> decode(preferences[PROFILE_JSON]) }
            .catch { emit(null) }

    suspend fun snapshot(): Profile? = profile.first()

    suspend fun create(displayName: String, avatarSeed: AvatarSeed): Profile {
        val next = Profile(
            id = UUID.randomUUID().toString(),
            displayName = displayName.trim().ifBlank { "Listener" },
            avatarSeed = avatarSeed,
            createdAtMillis = System.currentTimeMillis(),
        )
        context.audiobookProfileDataStore.edit { it[PROFILE_JSON] = json.encodeToString(next) }
        return next
    }

    suspend fun update(profile: Profile) {
        context.audiobookProfileDataStore.edit { it[PROFILE_JSON] = json.encodeToString(profile) }
    }

    suspend fun signOut() {
        context.audiobookProfileDataStore.edit { it.remove(PROFILE_JSON) }
    }

    private fun decode(raw: String?): Profile? =
        raw?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }

    private companion object {
        val PROFILE_JSON = stringPreferencesKey("profile_json")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
