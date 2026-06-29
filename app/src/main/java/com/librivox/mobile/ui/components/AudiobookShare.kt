package com.librivox.mobile.ui.components

import android.content.Context
import android.content.Intent
import com.librivox.mobile.audiobookApp
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.shareSubject
import com.librivox.mobile.model.shareText

fun shareAudiobook(context: Context, book: AudioBook) {
    val shareLinkMode = context.audiobookApp.playbackSettingsStore.shareLinkMode
    shareText(
        context = context,
        subject = book.shareSubject(),
        text = book.shareText(shareLinkMode),
    )
}

fun shareAudiobookChapter(
    context: Context,
    book: AudioBook,
    chapter: AudioBookChapter,
) {
    val shareLinkMode = context.audiobookApp.playbackSettingsStore.shareLinkMode
    shareText(
        context = context,
        subject = chapter.shareSubject(book),
        text = chapter.shareText(book, shareLinkMode),
    )
}

fun shareText(
    context: Context,
    subject: String,
    text: String,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, "Share audiobook").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}
