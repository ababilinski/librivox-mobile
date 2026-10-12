package com.librivox.mobile.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource

@Composable
fun BookSourceInfoRow(
    book: AudioBook,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text("Source")
        },
        supportingContent = {
            Text(
                text = book.source.actionSheetLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun BookSource.actionSheetLabel(): String =
    when (this) {
        BookSource.LibriVox -> "LibriVox"
        BookSource.Lit2Go -> "Lit2Go"
        BookSource.WolneLektury -> "Wolne Lektury"
        BookSource.Gutendex -> "Project Gutenberg via Gutendex"
        BookSource.LocalAsset -> "Included sample"
        BookSource.CustomLocal -> "Local file"
    }
