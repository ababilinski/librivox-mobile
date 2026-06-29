package com.librivox.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.activeDownloadBookAssetCount
import com.librivox.mobile.model.coverResId
import com.librivox.mobile.model.downloadProgressFraction
import com.librivox.mobile.model.hasDownloadedBookAssets
import com.librivox.mobile.model.isAudioFullyDownloaded
import com.librivox.mobile.model.localCoverFile
import com.librivox.mobile.model.remoteCoverImageUrl
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.ui.navigation.BookSharedElementKey
import com.librivox.mobile.ui.navigation.bookSharedBounds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookCover(
    book: AudioBook,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
    preferFullImage: Boolean = false,
    displayMode: CoverArtDisplayMode = CoverArtDisplayMode.Default,
    onOpenReader: (() -> Unit)? = null,
    showReadShortcut: Boolean = true,
    onCoverClick: (() -> Unit)? = null,
    onCoverLongClick: (() -> Unit)? = null,
    sharedElementKey: BookSharedElementKey? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius.dp)
    val localCoverModel = book.localCoverFile(context.filesDir)
    val coverModel = localCoverModel ?: book.remoteCoverImageUrl(preferFullImage)
    val fallbackCoverRes = if (coverModel == null) book.coverResId() else 0
    val contentScale = displayMode.coverArtContentScale
    val fillUncroppedBackdrop = !displayMode.cropsImage && (coverModel != null || fallbackCoverRes != 0)
    val coverFrameModifier = Modifier
        .fillMaxSize()
        .clip(shape)
        .then(
            if (fillUncroppedBackdrop) {
                Modifier
            } else {
                Modifier.background(book.placeholderBrush())
            },
        )
        .bookSharedBounds(
            sharedElementKey = sharedElementKey,
            shape = shape,
        )
    Box(modifier = modifier) {
        Box(
            modifier = coverFrameModifier.then(
                if (onCoverClick != null || onCoverLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onCoverClick?.invoke() },
                        onClickLabel = "View cover art",
                        onLongClick = onCoverLongClick,
                        onLongClickLabel = "Audiobook options",
                        role = Role.Button,
                    )
                } else {
                    Modifier
                },
            ),
        ) {
            if (coverModel != null) {
                CoverAsyncImage(
                    model = coverModel,
                    contentDescription = book.title,
                    contentScale = contentScale,
                    fillUncroppedBackdrop = fillUncroppedBackdrop,
                )
            } else {
                if (fallbackCoverRes != 0) {
                    CoverResourceImage(
                        resId = fallbackCoverRes,
                        contentDescription = book.title,
                        contentScale = contentScale,
                        fillUncroppedBackdrop = fillUncroppedBackdrop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        Text(
                            text = book.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        DownloadStateBadge(
            book = book,
            modifier = Modifier
                .align(
                    if (onOpenReader != null && showReadShortcut) {
                        Alignment.BottomStart
                    } else {
                        Alignment.BottomEnd
                    },
                )
                .padding(3.dp),
        )
        onOpenReader?.takeIf { showReadShortcut }?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Read book",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverAsyncImage(
    model: Any,
    contentDescription: String,
    contentScale: ContentScale,
    fillUncroppedBackdrop: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (fillUncroppedBackdrop) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .blur(18.dp),
            )
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CoverResourceImage(
    resId: Int,
    contentDescription: String,
    contentScale: ContentScale,
    fillUncroppedBackdrop: Boolean,
) {
    val painter = painterResource(resId)
    Box(modifier = Modifier.fillMaxSize()) {
        if (fillUncroppedBackdrop) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .blur(18.dp),
            )
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DownloadStateBadge(book: AudioBook, modifier: Modifier = Modifier) {
    val isDownloading = book.activeDownloadBookAssetCount() > 0
    val hasDownloads = book.hasDownloadedBookAssets()
    if (!isDownloading && !hasDownloads) return

    val isComplete = !isDownloading && book.isAudioFullyDownloaded()
    Box(
        modifier = modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(18.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (isComplete) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            },
            contentColor = if (isComplete) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isComplete) Icons.Filled.CheckCircle else Icons.Outlined.Download,
                    contentDescription = when {
                        isDownloading -> "Download in progress"
                        isComplete -> "Fully downloaded"
                        else -> "Partially downloaded"
                    },
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (isDownloading) {
            CircularProgressIndicator(
                progress = { book.downloadProgressFraction() },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun AudioBook.placeholderBrush() =
    androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(
            lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.1f),
            MaterialTheme.colorScheme.tertiary,
        ),
    )
