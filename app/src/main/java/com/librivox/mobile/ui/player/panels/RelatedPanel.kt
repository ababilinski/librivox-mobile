package com.librivox.mobile.ui.player.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.navigation.LocalAppGraph

@Composable
fun RelatedPanel(
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    headerDragModifier: Modifier = Modifier,
    bodyDragModifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val state by graph.playerStateRepository.state.collectAsState()
    val library by graph.app.libraryRepository.state.collectAsState(initial = null)
    val book = library?.bookById(state.bookId)

    var byAuthor by remember { mutableStateOf<List<AudioBook>>(emptyList()) }
    var byGenre by remember { mutableStateOf<List<AudioBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val author = book?.author?.takeIf { it.isNotBlank() && it != "Unknown author" }
    val genre = book?.genres?.firstOrNull()

    LaunchedEffect(state.bookId, author, genre) {
        if (author == null && genre == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching {
            val authorResults = author?.let {
                graph.app.catalogClient.byAuthor(it, limit = 12)
            }.orEmpty().filter { it.id != state.bookId }
            val genreResults = genre?.let {
                graph.app.catalogClient.byGenre(it, limit = 12)
            }.orEmpty().filter { it.id != state.bookId }
            authorResults to genreResults
        }.onSuccess { (authors, genres) ->
            byAuthor = authors
            byGenre = genres
            loading = false
        }.onFailure {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(bodyDragModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Related",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .then(headerDragModifier)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            }
            byAuthor.isEmpty() && byGenre.isEmpty() -> {
                Text(
                    text = "No related books found for this title.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            else -> {
                if (byAuthor.isNotEmpty() && author != null) {
                    RelatedShelf(
                        title = "More by $author",
                        books = byAuthor,
                        onOpenBook = onOpenBook,
                    )
                }
                if (byGenre.isNotEmpty() && genre != null) {
                    RelatedShelf(
                        title = "More in $genre",
                        books = byGenre,
                        onOpenBook = onOpenBook,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedShelf(
    title: String,
    books: List<AudioBook>,
    onOpenBook: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { books.size },
            itemWidth = 156.dp,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp),
        ) { index ->
            RelatedCard(book = books[index], onClick = { onOpenBook(books[index].id) })
        }
    }
}

@Composable
private fun RelatedCard(book: AudioBook, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            BookCover(
                book = book,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = { shareAudiobook(context, book) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share audiobook",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
