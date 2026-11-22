package io.ymusic.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import io.ymusic.dynamicengine.yt.YtVideoDetails
import io.ymusic.dynamicengine.yt.extractVideoDetails
import io.ymusic.dynamicengine.yt.rateVideo
import io.ymusic.ui.resources.cd_like
import io.ymusic.ui.resources.like_failed
import kmpcommon.log.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import projectcommon.error.errorCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ymusic.lyrics.LyricsDoc
import io.ymusic.player.LoopMode
import io.ymusic.player.PlayableItem
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.cd_lyrics
import io.ymusic.ui.resources.cd_next
import io.ymusic.ui.resources.cd_pause
import io.ymusic.ui.resources.cd_play
import io.ymusic.ui.resources.cd_previous
import io.ymusic.ui.resources.cd_shuffle
import io.ymusic.ui.resources.lyrics_not_found
import io.ymusic.ui.resources.up_next
import org.jetbrains.compose.resources.stringResource

/** The three faces of the Apple Music player: the cover, the lyrics and "Playing Next". */
internal enum class AppleNowPlayingMode { Cover, Lyrics, Queue }

private val AmWhite = Color.White
private val AmWhite60 = Color(0x99FFFFFF)
private val AmWhite30 = Color(0x4DFFFFFF)
private val AmChip = Color(0x33FFFFFF)

/**
 * Now Playing as Apple Music on iPhone draws it (design refs: cover, Playing Next, lyrics screenshots). One screen over
 * the cover gradient with white ink: a grabber, then either the big cover with title / artist / star / more, the
 * lyrics under a compact track header, or the queue under the same header; below that always the thin progress
 * bar with elapsed and remaining time, the three bare transport glyphs, the volume bar and the bottom row
 * (lyrics Â· sleep timer Â· queue). The cover shrinks while paused, as on iOS.
 */
@Composable
internal fun AppleNowPlaying(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    onClose: () -> Unit,
    lyricsAvailable: Boolean,
) {
    val player = coordinator.player
    val status by player.status.collectAsStateWithLifecycle()
    val progress by player.progress.collectAsStateWithLifecycle()
    val queue by player.queue.collectAsStateWithLifecycle()
    val sleepMs by player.sleepTimerMs.collectAsStateWithLifecycle()
    val config by player.config.collectAsStateWithLifecycle()
    val current = queue.currentItem
    val playing = status.showsPause()
    var mode by rememberSaveable { mutableStateOf(AppleNowPlayingMode.Cover) }
    var lyricsFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mode) { if (mode != AppleNowPlayingMode.Lyrics) lyricsFocus = false }
    val sheenTransition = rememberInfiniteTransition(label = "np-sheen")
    val sheenAlpha by sheenTransition.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.09f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "np-sheen-alpha",
    )

    Column(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(AmWhite.copy(alpha = sheenAlpha)) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!lyricsFocus) Grabber(onClose)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(mode, label = "apple-now-playing-mode") { m ->
                when (m) {
                    AppleNowPlayingMode.Cover -> CoverFace(graph, coordinator, current, status, playing)
                    AppleNowPlayingMode.Lyrics -> Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            if (!lyricsFocus) {
                                CompactTrackHeader(graph, coordinator, current, status) {
                                    CircleChip {
                                        Icon(
                                            VerreIcons.Fullscreen,
                                            null,
                                            Modifier.size(18.dp).clickable(role = Role.Button) { lyricsFocus = true },
                                            tint = AmWhite,
                                        )
                                    }
                                }
                            }
                            LyricsFace(graph, coordinator, current, lyricsAvailable)
                        }
                        if (lyricsFocus) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 12.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AmChip)
                                    .clickable(role = Role.Button) { lyricsFocus = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(VerreIcons.FullscreenExit, null, Modifier.size(20.dp), tint = AmWhite)
                            }
                        }
                    }
                    AppleNowPlayingMode.Queue -> Column(Modifier.fillMaxSize()) {
                        CompactTrackHeader(graph, coordinator, current, status)
                        QueueFace(coordinator, queue)
                    }
                }
            }
        }
        if (!lyricsFocus) {
            if (mode == AppleNowPlayingMode.Cover) {
                Spacer(Modifier.height(4.dp))
            }
            AppleProgress(coordinator, progress)
            AppleTransport(coordinator, playing)
            AppleVolume(config.volume) { player.volume = it }
            AppleBottomRow(
                mode = mode,
                lyricsAvailable = lyricsAvailable,
                sleepActive = sleepMs != null,
                onMode = { target -> mode = if (mode == target) AppleNowPlayingMode.Cover else target },
                sleepButton = { PlayerSleepTimerButton(coordinator, sleepMs) },
            )
        }
    }
}

/** The drag handle at the top; a tap closes the player (a swipe down is handled by the surrounding dismiss box). */
@Composable
private fun Grabber(onClose: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(28.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClose,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(AmWhite30))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverFace(graph: AppGraph, coordinator: PlaybackCoordinator, current: PlayableItem?, status: io.ymusic.player.PlaybackStatus, playing: Boolean) {
    val haptic = LocalHapticFeedback.current
    var vinylMode by rememberSaveable { mutableStateOf(false) }
    var rotationDeg by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, vinylMode) {
        if (playing && vinylMode) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                rotationDeg = (rotationDeg + ((now - last) / 1_000_000_000f) * 36f) % 360f
                last = now
            }
        }
    }
    // iOS shrinks the artwork while paused and springs it back on play.
    val scale by animateFloatAsState(if (playing) 1f else 0.82f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "cover-scale")
    val shape = if (vinylMode) CircleShape else RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(coordinator) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragCancel = { drag = 0f },
                        onDragEnd = {
                            if (drag < -90f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coordinator.player.skipNext()
                            } else if (drag > 90f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coordinator.player.skipPrev()
                            }
                            drag = 0f
                        },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        if (vinylMode) rotationZ = rotationDeg
                    }
                    .shadow(24.dp, shape, clip = false)
                    .clip(shape)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onDoubleClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vinylMode = !vinylMode
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AudioOnlyCover(current?.displayThumbnailUrl, Modifier.fillMaxSize())
                if (vinylMode) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC1C1C1E))
                            .border(2.dp, AmWhite30, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(AmWhite))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    current?.displayTitle.orEmpty(),
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AmWhite),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ArtistPill(current, status)
            }
            TrackButtons(graph, coordinator, current)
        }
    }
}

/**
 * Apple's artist credit under the title: the artist name in a translucent capsule with a person
 * glyph (the round badge beside the artwork in the reference screenshots).
 */
@Composable
private fun ArtistPill(current: PlayableItem?, status: io.ymusic.player.PlaybackStatus) {
    val text = artistText(current, status) ?: return
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(AmChip)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(VerreIcons.PersonOutline, null, Modifier.size(14.dp), tint = AmWhite60)
        Text(text, style = TextStyle(fontSize = 15.sp, color = AmWhite60), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The star and the "more" circle on the right of the title, glass chips like Apple's. */
@Composable
private fun TrackButtons(graph: AppGraph, coordinator: PlaybackCoordinator, current: PlayableItem?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        current?.videoId?.takeIf { !graph.guest }?.let { LikeStar(graph, it) }
        CircleChip { PlayerMoreMenu(coordinator) }
    }
}

@Composable
private fun CircleChip(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(AmChip), contentAlignment = Alignment.Center, content = content)
}

/** Lyrics / queue header: a 56dp cover, the title and artist, the star and more chips. */
@Composable
private fun CompactTrackHeader(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    current: PlayableItem?,
    status: io.ymusic.player.PlaybackStatus,
    extraAction: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AudioOnlyCover(current?.displayThumbnailUrl, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                current?.displayTitle.orEmpty(),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AmWhite),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ArtistLine(current, status, TextStyle(fontSize = 17.sp, color = AmWhite60), Modifier)
        }
        if (extraAction != null) {
            extraAction()
            Spacer(Modifier.width(10.dp))
        }
        TrackButtons(graph, coordinator, current)
    }
}

@Composable
private fun LyricsFace(graph: AppGraph, coordinator: PlaybackCoordinator, current: PlayableItem?, available: Boolean) {
    val player = coordinator.player
    val progress by player.progress.collectAsStateWithLifecycle()
    val status by player.status.collectAsStateWithLifecycle()
    val lyrics = rememberLyricsState(graph, current)
    val clock = rememberLyricsClock(progress, status.showsPause())
    var quoteTarget by remember(current) { mutableStateOf<Pair<LyricsDoc, Int>?>(null) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !available -> Text(stringResource(Res.string.lyrics_not_found), style = TextStyle(fontSize = 16.sp, color = AmWhite60), textAlign = TextAlign.Center)
            lyrics is LyricsUiState.Loading -> CircularProgressIndicator(Modifier.size(32.dp), color = AmWhite60)
            lyrics is LyricsUiState.None -> Text(stringResource(Res.string.lyrics_not_found), style = TextStyle(fontSize = 16.sp, color = AmWhite60), textAlign = TextAlign.Center)
            lyrics is LyricsUiState.Ready -> {
                LyricsView(
                    doc = lyrics.doc,
                    positionMs = { clock.value },
                    onSeek = { player.seekTo(it) },
                    onLineLongClick = { index, _ -> quoteTarget = lyrics.doc to index },
                )
            }
        }
    }
    quoteTarget?.let { (doc, idx) ->
        LyricQuoteSheet(doc, idx, current) { quoteTarget = null }
    }
}

/** "Playing Next": shuffle and repeat on the right, covers at 44dp, the drag handle on each row. */
@Composable
private fun QueueFace(coordinator: PlaybackCoordinator, queue: io.ymusic.player.QueueState) {
    val player = coordinator.player
    val listState = rememberLazyListState()
    val locate = rememberLocateCurrent(listState, queue.currentIndex, indexOffset = 0)
    val dragState = rememberReorderDragState(listState, indexOffset = 0) { from, to -> player.move(from, to) }
    FollowCurrentQueueItem(listState, queue.currentIndex)
    val moreFocus = remember { LoadMoreFocus() }
    QueueMoreSettle(moreFocus, queue)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.up_next), style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AmWhite))
                Text("${queue.currentIndex + 1} / ${queue.displayTotal}", style = TextStyle(fontSize = 13.sp, color = AmWhite60))
            }
            ToggleChip(VerreIcons.Shuffle, stringResource(Res.string.cd_shuffle), queue.shuffle) { player.setShuffle(!queue.shuffle) }
            Spacer(Modifier.width(10.dp))
            ToggleChip(
                if (queue.loopMode == LoopMode.One) VerreIcons.RepeatOne else VerreIcons.Repeat,
                stringResource(queue.loopMode.label()),
                queue.loopMode != LoopMode.None,
            ) { player.setLoopMode(queue.loopMode.cycled()) }
            locate?.let {
                Spacer(Modifier.width(10.dp))
                ToggleChip(VerreIcons.QueueMusicOutline, null, false, it)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(queue.items) { index, item ->
                QueueRow(coordinator, index, item, current = index == queue.currentIndex, dragState, modifier = moreFocus.item(index))
            }
            queue.more?.let { more -> item { QueueMoreRow(player, more, queue.items.size, moreFocus) } }
        }
    }
}

/** A 32dp circle chip; an active one is filled white with a dark glyph, like Apple's shuffle / repeat toggles. */
@Composable
private fun ToggleChip(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String?, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(if (active) AmWhite else AmChip)
            .clickable(role = Role.Button, onClick = onClick).a11yActionLabel(description),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (active) Color(0xFF3A3A3C) else AmWhite)
    }
}

/** The 4dp bar (it thickens while touched, via [SeekSlider]) with elapsed on the left and remaining on the right. */
@Composable
private fun AppleProgress(coordinator: PlaybackCoordinator, progress: io.ymusic.player.Progress) {
    val player = coordinator.player
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val shown = scrubMs?.let { progress.copy(positionMs = it) } ?: progress
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SeekSlider(player, progress, trackColor = AmWhite30, bufferedColor = Color(0x4DFFFFFF), onScrubMs = { scrubMs = it })
        MediaLtr {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                val time = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AmWhite60)
                Text(shown.progressLabel.orEmpty(), style = time)
                Spacer(Modifier.weight(1f))
                val remaining = if (progress.durationMs > 0 && !progress.durationIsLive) {
                    "-" + formatMillis((progress.durationMs - shown.positionMs).coerceAtLeast(0L))
                } else {
                    null
                }
                if (remaining != null) Text(remaining, style = time) else DurationLabel(player, progress, time)
            }
        }
    }
}

/** Rewind Â· play/pause Â· forward: bare white glyphs, no circle, as on iOS. */
@Composable
private fun AppleTransport(coordinator: PlaybackCoordinator, playing: Boolean) {
    val player = coordinator.player
    val haptic = LocalHapticFeedback.current
    MediaLtr {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigGlyph(VerreIcons.FastRewind, stringResource(Res.string.cd_previous), 44.dp) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                player.skipPrev()
            }
            BigGlyph(
                if (playing) VerreIcons.Pause else VerreIcons.PlayArrow,
                stringResource(if (playing) Res.string.cd_pause else Res.string.cd_play),
                60.dp,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                player.togglePlay()
            }
            BigGlyph(VerreIcons.FastForward, stringResource(Res.string.cd_next), 44.dp) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                player.skipNext()
            }
        }
    }
}

@Composable
private fun BigGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size + 24.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).a11yActionLabel(description),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size), tint = AmWhite)
    }
}

@Composable
private fun AppleVolume(volume: Float, onVolume: (Float) -> Unit) {
    PlayerVolumeRow(volume, onVolume, Modifier.padding(horizontal = 0.dp))
}

/** Lyrics Â· sleep timer Â· queue. The active mode sits on a white-ish rounded square, like the iOS bottom row. */
@Composable
private fun AppleBottomRow(
    mode: AppleNowPlayingMode,
    lyricsAvailable: Boolean,
    sleepActive: Boolean,
    onMode: (AppleNowPlayingMode) -> Unit,
    sleepButton: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab(VerreIcons.LyricsOutline, stringResource(Res.string.cd_lyrics), mode == AppleNowPlayingMode.Lyrics, lyricsAvailable) {
            onMode(AppleNowPlayingMode.Lyrics)
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (sleepActive) AmWhite30 else AmChip).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { sleepButton() }
        BottomTab(VerreIcons.QueueMusicOutline, stringResource(Res.string.up_next), mode == AppleNowPlayingMode.Queue, true) {
            onMode(AppleNowPlayingMode.Queue)
        }
    }
}

@Composable
private fun BottomTab(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) AmWhite30 else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .a11yActionLabel(description),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) AmWhite else AmWhite30)
    }
}

/**
 * Apple Music's mini player: a rounded translucent bar floating over the tab bar with a 40dp cover, the title and
 * artist, play / pause and next. Tap opens Now Playing, a swipe down dismisses the queue.
 */
@Composable
internal fun AppleMiniPlayer(
    coordinator: PlaybackCoordinator,
    item: PlayableItem,
    status: io.ymusic.player.PlaybackStatus,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    SwipeDownDismissBox(
        onDismiss = {
            coordinator.player.stop()
            coordinator.player.clearQueue()
        },
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        commitFractionOfHeight = 1f / 3f,
        restingShadow = false,
        enabled = true,
    ) {
        val haptic = LocalHapticFeedback.current
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(DesignColors.SurfaceElevated, shape)
                .pointerInput(coordinator) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragCancel = { drag = 0f },
                        onDragEnd = {
                            if (drag < -80f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coordinator.player.skipNext()
                            } else if (drag > 80f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coordinator.player.skipPrev()
                            }
                            drag = 0f
                        },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                }
                .clickable(onClick = onExpand)
                .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioOnlyCover(item.displayThumbnailUrl, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
            Text(
                item.displayTitle,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DesignColors.InkStrong),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            val playing = status.showsPause()
            if (playing) {
                MiniVisualizerBars()
            }
            BigGlyph2(if (playing) VerreIcons.Pause else VerreIcons.PlayArrow, stringResource(if (playing) Res.string.cd_pause else Res.string.cd_play)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coordinator.player.togglePlay()
            }
            BigGlyph2(VerreIcons.FastForward, stringResource(Res.string.cd_next)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coordinator.player.skipNext()
            }
        }
    }
}

@Composable
private fun BigGlyph2(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).a11yActionLabel(description),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = DesignColors.InkStrong)
    }
}

@Composable
private fun LikeStar(graph: AppGraph, videoId: String) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val failed = stringResource(Res.string.like_failed)
    var details by remember(videoId) { mutableStateOf<YtVideoDetails?>(null) }
    var status by remember(videoId) { mutableStateOf(YtVideoDetails.INDIFFERENT) }
    LaunchedEffect(videoId) {
        try {
            val loaded = graph.engines.extractVideoDetails(videoId, graph.logger)
            details = loaded
            status = loaded.likeStatus
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            graph.logger?.log(Logger.Level.WARN, "like status failed", error)
        }
    }
    val loaded = details ?: return
    if (loaded.rateTokens.isEmpty()) return
    val liked = status == YtVideoDetails.LIKE
    val label = stringResource(Res.string.cd_like)
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(AmChip)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val target = if (liked) YtVideoDetails.INDIFFERENT else YtVideoDetails.LIKE
                val token = loaded.rateTokens[target] ?: return@clickable
                val previous = status
                status = target
                scope.launch {
                    try {
                        graph.engines.rateVideo(videoId, target, token, graph.logger)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        graph.logger?.log(Logger.Level.WARN, "rate video failed", error)
                        status = previous
                        snackbar("$failed (${error.errorCode() ?: "E20166"})")
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (liked) VerreIcons.StarBorder else VerreIcons.StarBorderOutline,
            label,
            Modifier.size(20.dp),
            tint = AmWhite,
        )
    }
}
@Composable
private fun MiniVisualizerBars() {
    val transition = rememberInfiniteTransition(label = "mini-viz")
    val b1 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "b1")
    val b2 by transition.animateFloat(0.95f, 0.3f, infiniteRepeatable(tween(340), RepeatMode.Reverse), label = "b2")
    val b3 by transition.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b3")
    Row(
        Modifier.padding(end = 4.dp).height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(b1, b2, b3).forEach { frac ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(14.dp * frac)
                    .clip(RoundedCornerShape(1.dp))
                    .background(DesignColors.Accent),
            )
        }
    }
}