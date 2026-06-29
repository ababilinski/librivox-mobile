package com.librivox.mobile.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val displayName: String,
    val avatarSeed: AvatarSeed,
    val createdAtMillis: Long,
)

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
