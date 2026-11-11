@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.librivox.mobile.readalong.ReadAlongChapter
import com.librivox.mobile.readalong.ReadAlongSegment
import com.librivox.mobile.readalong.ReadAlongSegmentRole
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderSelectionActionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun timedParagraphReadDispatchesSelectedChapterAndTimeThenDismissesSelection() {
        val block = textBlock(
            segment("first", "Pierwszy fragment.", positionMs = 12_000L),
            segment("second", "Drugi fragment.", positionMs = 42_000L),
        )
        val toolbar = RecordingTextToolbar()
        var selection by mutableStateOf<ReaderSelection?>(null)
        var readSelection: ReaderSelection? = null
        showBlock(
            block = block,
            toolbar = toolbar,
            selectedSegmentId = { selection?.segmentId },
            onSelectionChange = { start, end -> selection = block.selection(start, end) },
            onRead = {
                readSelection = selection
                selection = null
            },
        )

        longPressWord(block.displaySegment.text, "Drugi")
        waitForMenuItem(toolbar, "Read")

        composeRule.runOnIdle {
            assertNotNull(selection)
            assertEquals("Drugi", selection?.selectedText)
            toolbar.click("Read")
            assertEquals("chapter-6", readSelection?.chapterId)
            assertEquals("second", readSelection?.segmentId)
            assertEquals(42_000L, readSelection?.clipBeginMs)
        }
        composeRule.onNodeWithText(block.displaySegment.text)
            .assertIsNotFocused()
            .assert(SemanticsMatcher("Text selection is collapsed") {
                it.config[SemanticsProperties.TextSelectionRange].collapsed
            })
        composeRule.runOnIdle {
            assertNull(selection)
            assertTrue(toolbar.items().isEmpty())
        }
    }

    @Test fun headingLongPressRetainsPlatformCopyAction() {
        val block = textBlock(
            segment("heading", "Rozdział szósty", role = ReadAlongSegmentRole.Heading2),
        )
        val toolbar = RecordingTextToolbar()
        showBlock(block, toolbar)

        longPressWord(block.displaySegment.text, "Rozdział")
        waitForMenuItem(toolbar, "Copy")

        composeRule.runOnIdle {
            assertTrue(toolbar.items().any { it.key == TextContextMenuKeys.CopyKey })
        }
    }

    @Test fun untimedParagraphAllowsCopyWithoutOfferingRecordedAudioRead() {
        val block = textBlock(segment("static", "Tekst bez nagrania."))
        val toolbar = RecordingTextToolbar()
        var selection by mutableStateOf<ReaderSelection?>(null)
        showBlock(
            block = block,
            toolbar = toolbar,
            selectedSegmentId = { selection?.segmentId },
            onSelectionChange = { start, end -> selection = block.selection(start, end) },
        )

        longPressWord(block.displaySegment.text, "Tekst")
        waitForMenuItem(toolbar, "Copy")

        composeRule.runOnIdle {
            assertFalse(toolbar.items().any { it.label == "Read" })
        }
    }

    private fun showBlock(
        block: ReaderItem.TextBlock,
        toolbar: RecordingTextToolbar,
        selectedSegmentId: () -> String? = { null },
        onSelectionChange: (Int, Int) -> Unit = { _, _ -> },
        onRead: () -> Unit = {},
    ) {
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides toolbar) {
                    Box(Modifier.padding(top = 80.dp).width(320.dp)) {
                        SelectableTextBlock(
                            textBlock = block,
                            highlightRole = ReaderSegmentHighlightRole.None,
                            activeTextRange = null,
                            selectedSegmentId = selectedSegmentId(),
                            isSearchHit = false,
                            searchQuery = null,
                            textScale = 1f,
                            onRead = onRead,
                            onCast = {},
                            onBookmark = {},
                            onSelectionChange = { onSelectionChange(it.start, it.end) },
                        )
                    }
                }
            }
        }
    }

    private fun longPressWord(text: String, word: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val textNode = composeRule.onNodeWithText(text)
        textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val character = text.indexOf(word) + word.length / 2
        val position = layouts.single().getBoundingBox(character).center
        textNode.performTouchInput { longClick(position) }
    }

    private fun waitForMenuItem(toolbar: RecordingTextToolbar, label: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            var available = false
            composeRule.runOnIdle { available = toolbar.items().any { it.label == label } }
            available
        }
    }

    private fun textBlock(vararg segments: ReadAlongSegment): ReaderItem.TextBlock {
        val chapter = ReadAlongChapter(
            chapterId = "chapter-6",
            title = "Rozdział szósty",
            displayIndex = 6,
            fullText = segments.joinToString(" ") { it.text },
            segments = segments.toList(),
        )
        return ReaderItem.TextBlock(chapter, segments.toList())
    }

    private fun segment(
        id: String,
        text: String,
        positionMs: Long? = null,
        role: ReadAlongSegmentRole = ReadAlongSegmentRole.Paragraph,
    ) = ReadAlongSegment(
        id = id,
        chapterId = "chapter-6",
        text = text,
        textRef = "chapter6.xhtml#$id",
        audioRef = positionMs?.let { "chapter6.mp3" },
        clipBeginMs = positionMs,
        clipEndMs = positionMs?.plus(10_000L),
        textEnd = text.length,
        role = role,
    )
}

/** Uses the real selection/menu pipeline without depending on Android's floating-toolbar layout. */
private class RecordingTextToolbar : TextContextMenuProvider {
    private var dataProvider: TextContextMenuDataProvider? = null
    private var dismissed: CompletableDeferred<Unit>? = null

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val currentDismissal = CompletableDeferred<Unit>()
        this.dataProvider = dataProvider
        dismissed = currentDismissal
        try {
            currentDismissal.await()
        } finally {
            if (dismissed === currentDismissal) {
                this.dataProvider = null
                dismissed = null
            }
        }
    }

    fun items(): List<TextContextMenuItem> =
        dataProvider?.data()?.components.orEmpty().filterIsInstance<TextContextMenuItem>()

    fun click(label: String) {
        val session = object : TextContextMenuSession {
            override fun close() {
                dismissed?.complete(Unit)
            }
        }
        items().single { it.label == label }.onClick(session)
    }
}
