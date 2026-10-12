package com.librivox.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.ChapterPresentation

/** Attribution is separate from chapter position, duration and playback status. */
@Composable
fun ChapterSupportingInfo(
    presentation: ChapterPresentation,
    metadata: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        presentation.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        presentation.reader?.let { Text("Read by $it", style = MaterialTheme.typography.bodyMedium) }
        val contextAndMetadata = listOfNotNull(presentation.context, metadata?.takeIf(String::isNotBlank))
        if (contextAndMetadata.isNotEmpty()) {
            Text(
                contextAndMetadata.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
