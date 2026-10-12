package com.librivox.mobile.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val displayName: String,
    val avatarSeed: AvatarSeed,
    val createdAtMillis: Long,
    val completedOnboardingVersion: Int = 0,
) {
    val requiresOnboarding: Boolean
        get() = completedOnboardingVersion < CurrentOnboardingVersion

    companion object {
        const val CurrentOnboardingVersion = 2
    }
}

@Serializable
enum class AvatarSeed {
    Aqua,
    Citrus,
    Plum,
    Coral,
    Olive,
    Slate;

    companion object {
        val Default: AvatarSeed = Aqua
    }
}
