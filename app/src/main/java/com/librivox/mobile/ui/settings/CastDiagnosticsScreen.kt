package com.librivox.mobile.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.librivox.mobile.cast.diagnostics.CastDiagnosticsLogger
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDiagnosticsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val diagnostics = graph.app.castDiagnostics
    val entries by diagnostics.entries.collectAsStateWithLifecycle()
    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Cast diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { diagnostics.clear() }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Clear log")
                    }
                    IconButton(
                        onClick = {
                            val file = diagnostics.snapshotLogFile() ?: return@IconButton
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share Cast log"))
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = "Share log")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No Cast diagnostics yet.\nEnable logging in Cast settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true,
            ) {
                items(entries, key = { it.timestampMs.toString() + it.tag + it.message.hashCode() }) { entry ->
                    LogRow(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: CastDiagnosticsLogger.Entry) {
    val color = when (entry.level) {
        CastDiagnosticsLogger.Level.Verbose -> MaterialTheme.colorScheme.onSurfaceVariant
        CastDiagnosticsLogger.Level.Debug -> MaterialTheme.colorScheme.onSurface
        CastDiagnosticsLogger.Level.Info -> MaterialTheme.colorScheme.primary
        CastDiagnosticsLogger.Level.Warn -> MaterialTheme.colorScheme.tertiary
        CastDiagnosticsLogger.Level.Error -> MaterialTheme.colorScheme.error
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "${TIMESTAMP_FORMAT.format(Date(entry.timestampMs))} ${entry.level.short}/${entry.tag}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        entry.throwable?.let { tr ->
            Text(
                text = tr.stackTraceToString(),
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.85f),
            )
        }
    }
}

@Suppress("ktlint:standard:property-naming")
private val TIMESTAMP_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
