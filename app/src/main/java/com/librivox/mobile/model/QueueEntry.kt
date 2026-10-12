package com.librivox.mobile.model

import kotlinx.serialization.Serializable

@Serializable
data class QueueEntry(
    val bookId: String,
    val chapterId: String? = null,
    val addedAtMillis: Long,
)
