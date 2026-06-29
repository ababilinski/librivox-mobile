package com.librivox.mobile.model

import kotlinx.serialization.Serializable

@Serializable
data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterId: String,
    val positionMs: Long,
    val note: String = "",
    val createdAtMillis: Long,
)
