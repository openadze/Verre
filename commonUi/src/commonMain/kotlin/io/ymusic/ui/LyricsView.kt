package io.ymusic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.withFrameNanos
import io.ymusic.lyrics.LyricLine
import io.ymusic.lyrics.LyricsDoc
import io.ymusic.lyrics.LyricsQuery
import io.ymusic.lyrics.LyricsTimeline
import io.ymusic.lyrics.SyncType
import io.ymusic.lyrics.lyricsOptions
import io.ymusic.lyrics.withEstimatedWords
import io.ymusic.player.PlayableItem
import io.ymusic.player.Progress
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.min

/** What the lyrics pane shows for the current track. */
sealed interface LyricsUiState {
    data object Loading : LyricsUiState

    data object None : LyricsUiState

    data class Ready(val doc: LyricsDoc) : LyricsUiState
}

/**
 * Loads the lyrics of [item] through the app's repository (cache first, then the providers) and restarts when the
 * track changes. A failure that the repository does not already recover from is logged (not swallowed) and reads as
 * "no lyrics".
 */
@Composable
internal fun rememberLyricsState(graph: AppGraph, item: PlayableItem?): LyricsUiState {
    val repo = graph.lyrics
    val key = item?.let { (it as? PlayableItem.YtMedia)?.videoId ?: "${it.displayTitle}|${it.displaySubtitle}" }
    val state by produceState<LyricsUiState>(LyricsUiState.Loading, repo, key) {
        value = LyricsUiState.Loading
        if (repo == null || item == null) {
            value = LyricsUiState.None
            return@produceState
        }
        val query = LyricsQuery.forTrack(
            title = item.displayTitle,
            artist = item.displaySubtitle,
            durationMs = item.durationMs,
            videoId = (item as? PlayableItem.YtMedia)?.videoId,
        )
        value = try {
            repo.load(query, graph.settings.lyricsOptions())?.let { LyricsUiState.Ready(it) } ?: LyricsUiState.None
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            graph.logger?.fork("lyrics")?.captureError(e, "lyrics: loading failed")
            LyricsUiState.None
        }
    }
    return state
}

/**
 * Playback position in ms, smoothed to the frame: it restarts from every player update and, while playing, advances
 * with the frame clock in between. Read it inside draw / `derivedStateOf` blocks so a frame does not recompose.
 */
@Composable
internal fun rememberLyricsClock(progress: Progress, playing: Boolean): State<Long> {
    val position = remember { mutableLongStateOf(progress.positionMs) }
    LaunchedEffect(progress.positionMs, playing) {
        val base = progress.positionMs
        position.longValue = base
        if (playing) {
            val t0 = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                position.longValue = base + (t - t0) / 1_000_000L
            }
        }
    }
    return position
}

private const val ActiveFontSp = 34
private const val BackgroundFontSp = 21
private const val UserScrollHoldMs = 3_000L

/**
 * Synced lyrics, Apple Music style: the active line is big (34sp), bold and fills word by word; the
 * others hold Apple's ~35% dimness with a slight blur that deepens with distance (blur needs Android
 * 12+; older versions only fade). All lines stay one size — Apple dims and blurs, never shrinks.
 * Scrolls to keep the active line near the top third, unless the user is scrolling; tapping a line
 * seeks to it. Unsynced text is shown plain.
 */
@Composable
fun LyricsView(
    doc: LyricsDoc,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Line-synced lyrics (most of LRCLIB) still fill word by word, with the line's time spread over its words.
    val shown = remember(doc) { doc.withEstimatedWords() }
    val timeline = remember(shown) { LyricsTimeline(shown) }
    val synced = shown.syncType != SyncType.NONE
    val activeIndex by remember(timeline) { derivedStateOf { timeline.activeIndex(positionMs()) } }
    val inGap by remember(timeline) { derivedStateOf { timeline.isInstrumentalGap(positionMs()) } }
    val listState = rememberLazyListState()

    var userScrolling by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (moving && !autoScrolling) userScrolling = true
        }
    }
    LaunchedEffect(userScrolling, listState.isScrollInProgress) {
        if (userScrolling && !listState.isScrollInProgress) {
            delay(UserScrollHoldMs)
            userScrolling = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
        val anchor = maxHeight * 0.28f

        LaunchedEffect(activeIndex, userScrolling, synced) {
            if (!synced || userScrolling) return@LaunchedEffect
            // Item 0 is the top spacer, lines start at 1.
            val target = if (activeIndex < 0) 0 else activeIndex + 1
            autoScrolling = true
            try {
                listState.animateScrollToItem(target, scrollOffset = if (activeIndex < 0) 0 else -(viewportPx * 0.28f).toInt())
            } finally {
                autoScrolling = false
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            item(key = "top") { Box(Modifier.height(anchor)) }
            itemsIndexed(shown.lines, key = { index, _ -> index }) { index, line ->
                LyricLineRow(
                    line = line,
                    index = index,
                    activeIndex = activeIndex,
                    synced = synced,
                    showGap = synced && inGap && index == activeIndex + 1,
                    timeline = timeline,
                    positionMs = positionMs,
                    onClick = { if (synced) onSeek(line.startMs) },
                )
            }
            item(key = "bottom") { Box(Modifier.height(maxHeight * 0.7f)) }
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    index: Int,
    activeIndex: Int,
    synced: Boolean,
    showGap: Boolean,
    timeline: LyricsTimeline,
    positionMs: () -> Long,
    onClick: () -> Unit,
) {
    if (line.text.isBlank() && line.parts.isEmpty()) {
        Box(Modifier.height(if (synced) 0.dp else 16.dp))
        return
    }
    // Signed distance from the active line; before the first line everything counts as "ahead".
    val d = when {
        !synced -> 0
        activeIndex < 0 -> index + 1
        else -> index - activeIndex
    }
    val active = synced && d == 0
    // Apple's reference: the active line is white, the rest sit around 35% with a slight blur that
    // deepens with distance; the line right after the active one is a step brighter (~45%).
    val targetAlpha = when {
        !synced -> 0.92f
        d == 0 -> 1f
        d == 1 -> 0.45f
        else -> (0.4f - 0.025f * abs(d)).coerceAtLeast(0.3f)
    }
    val targetBlur = if (!synced || d == 0 || d == 1) 0f else min(1.2f + abs(d) * 0.9f, 5f)
    val lineAlpha by animateFloatAsState(targetAlpha, tween(350))
    val blurDp by animateFloatAsState(targetBlur, tween(350))

    val baseSp = if (line.isBackground) BackgroundFontSp else ActiveFontSp
    val ink = DesignColors.InkStrong
    val style = TextStyle(fontSize = baseSp.sp, lineHeight = (baseSp * 1.22f).sp, fontWeight = FontWeight.Bold)
    val text = remember(line) { lineText(line, baseSp) }
    // Word-by-word fill only for the active, word-timed line.
    val wordFill = active && line.parts.isNotEmpty()

    Column(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = synced, onClick = onClick)
            .padding(vertical = 10.dp)
            .graphicsLayer {
                alpha = lineAlpha
            }
            .then(if (blurDp > 0.1f) Modifier.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
    ) {
        if (showGap) {
            GapDots(progress = { timeline.gapProgress(positionMs()) ?: 0f }, Modifier.padding(bottom = 12.dp))
        }
        line.romanization?.let {
            Text(it, style = TextStyle(fontSize = 15.sp, color = ink.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold))
        }
        if (wordFill) {
            WordFilledText(line, text, style, ink, timeline, positionMs)
        } else {
            Text(text, style = style.copy(color = ink))
        }
        line.translation?.let {
            Text(
                it,
                style = TextStyle(fontSize = 16.sp, color = ink.copy(alpha = 0.65f), fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The line's text; background-vocal words are set smaller. Parts carry their own trailing space, so joining rebuilds the line. */
private fun lineText(line: LyricLine, baseSp: Int) =
    buildAnnotatedString {
        if (line.parts.isEmpty()) {
            append(line.text)
        } else {
            for (p in line.parts) {
                if (p.isBackground && !line.isBackground) {
                    withStyle(SpanStyle(fontSize = (baseSp * 0.72f).sp)) { append(p.text) }
                } else {
                    append(p.text)
                }
            }
        }
    }

/**
 * Two layers of the same text: a dim one and a bright one clipped to what has been sung — finished words whole, the
 * current word up to its progress. The clip is computed in the draw phase, so a frame never recomposes.
 */
@Composable
private fun WordFilledText(
    line: LyricLine,
    text: androidx.compose.ui.text.AnnotatedString,
    style: TextStyle,
    ink: androidx.compose.ui.graphics.Color,
    timeline: LyricsTimeline,
    positionMs: () -> Long,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Char offset where each part starts (the last entry is the text length).
    val offsets = remember(line) {
        IntArray(line.parts.size + 1).also { o -> line.parts.forEachIndexed { i, p -> o[i + 1] = o[i] + p.text.length } }
    }
    Box {
        Text(text, style = style.copy(color = ink.copy(alpha = 0.38f)), onTextLayout = { layout = it })
        Text(
            text,
            style = style.copy(color = ink),
            modifier = Modifier.drawWithContent {
                val l = layout ?: return@drawWithContent
                val pos = positionMs()
                var finishedEnd = 0
                var current = -1
                for (i in line.parts.indices) {
                    val p = line.parts[i]
                    if (pos >= p.endMs) {
                        finishedEnd = offsets[i + 1]
                    } else {
                        if (pos > p.startMs) current = i
                        break
                    }
                }
                if (finishedEnd > 0) {
                    clipPath(l.getPathForRange(0, finishedEnd.coerceAtMost(l.layoutInput.text.length))) {
                        this@drawWithContent.drawContent()
                    }
                }
                if (current >= 0) {
                    val start = offsets[current]
                    var end = offsets[current + 1]
                    while (end > start && line.parts[current].text[end - start - 1].isWhitespace()) end--
                    if (end > start && end <= l.layoutInput.text.length) {
                        val first = l.getBoundingBox(start)
                        val last = l.getBoundingBox(end - 1)
                        val frac = timeline.partProgress(line.parts[current], pos)
                        val width = (last.right - first.left).coerceAtLeast(0f)
                        clipRect(first.left, first.top, first.left + width * frac, first.bottom) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
            },
        )
    }
}

/** The three dots of an instrumental break: they light up one by one as [progress] goes 0..1. */
@Composable
private fun GapDots(progress: () -> Float, modifier: Modifier = Modifier) {
    val ink = DesignColors.InkStrong
    Canvas(modifier.height(16.dp).fillMaxWidth(0.25f)) {
        val p = progress()
        val r = 6.dp.toPx()
        for (i in 0 until 3) {
            val lit = (p * 3f - i).coerceIn(0f, 1f)
            drawCircle(
                color = ink.copy(alpha = 0.3f + 0.7f * lit),
                radius = r * (0.8f + 0.2f * lit),
                center = Offset(r + i * (r * 3f), size.height / 2f),
            )
        }
    }
}
