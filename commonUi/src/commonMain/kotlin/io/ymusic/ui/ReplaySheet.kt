package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ymusic.dynamicengine.yt.YtFeedItem
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.cd_next
import io.ymusic.ui.resources.cd_pause
import io.ymusic.ui.resources.cd_play
import io.ymusic.ui.resources.cd_previous
import io.ymusic.ui.resources.mini_player_nothing_playing
import org.jetbrains.compose.resources.stringResource

/** Mini Replay stats sheet built from local PlayHistoryStore top tracks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerreReplaySheet(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var topTracks by remember { mutableStateOf<List<YtFeedItem>>(emptyList()) }

    LaunchedEffect(graph.playHistory, graph.guest) {
        loading = true
        topTracks = if (graph.guest) emptyList() else graph.playHistory?.youtubeTopChart(10).orEmpty()
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DesignColors.SurfaceElevated,
        contentColor = DesignColors.Ink,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DesignColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VerreIcons.StarBorder, null, Modifier.size(20.dp), tint = Color.White)
                }
                Column {
                    Text("Verre Replay", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DesignColors.InkStrong))
                    Text(
                        "${topTracks.size} top tracks",
                        style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                    )
                }
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesignColors.Accent)
                }
                topTracks.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.mini_player_nothing_playing),
                        style = TextStyle(fontSize = 14.sp, color = DesignColors.InkSecondary),
                    )
                }
                else -> LazyColumn(Modifier.fillMaxWidth().height(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(topTracks) { index, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DesignColors.Surface)
                                .clickable {
                                    coordinator.playAll(topTracks, startIndex = index)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignColors.Accent),
                                modifier = Modifier.width(22.dp),
                                textAlign = TextAlign.Center,
                            )
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                ThumbImage(item.thumbs, RoundedCornerShape(6.dp)) {
                                    ThumbPlaceholder(VerreIcons.MusicNote, 20.dp)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.InkStrong),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.subtitleLine1?.takeIf { it.isNotBlank() }?.let { artist ->
                                    Text(
                                        artist,
                                        style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Retro iPod Classic click-wheel Easter egg dialog. */
@Composable
internal fun IpodEasterEggDialog(
    coordinator: PlaybackCoordinator,
    onDismiss: () -> Unit,
) {
    val player = coordinator.player
    val status by player.status.collectAsStateWithLifecycle()
    val queue by player.queue.collectAsStateWithLifecycle()
    val progress by player.progress.collectAsStateWithLifecycle()
    val current = queue.currentItem
    val playing = status.showsPause()
    val haptic = LocalHapticFeedback.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(276.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFE6E8EC))
                .border(2.dp, Color(0xFFB8BCC4), RoundedCornerShape(28.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Classic monochrome LCD screen
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFAEC0B4))
                    .border(2.dp, Color(0xFF454A4E), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("iPod", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF1A241E)))
                    Text(if (playing) "▶" else "❚❚", style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1A241E)))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        current?.displayTitle ?: stringResource(Res.string.mini_player_nothing_playing),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF1A241E)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current?.displaySubtitle.orEmpty(),
                        style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF2D3A32)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val fraction = if (progress.durationMs > 0L) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(6.dp).border(1.dp, Color(0xFF1A241E))) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Color(0xFF1A241E)))
                }
            }

            // Click Wheel
            Box(
                Modifier
                    .size(184.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFD0D3D9), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "MENU",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF70757D)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                Icon(
                    VerreIcons.FastRewind,
                    stringResource(Res.string.cd_previous),
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            player.skipPrev()
                        }
                        .padding(14.dp)
                        .size(24.dp),
                    tint = Color(0xFF70757D),
                )
                Icon(
                    VerreIcons.FastForward,
                    stringResource(Res.string.cd_next),
                    Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            player.skipNext()
                        }
                        .padding(14.dp)
                        .size(24.dp),
                    tint = Color(0xFF70757D),
                )
                Icon(
                    if (playing) VerreIcons.Pause else VerreIcons.PlayArrow,
                    stringResource(if (playing) Res.string.cd_pause else Res.string.cd_play),
                    Modifier
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            player.togglePlay()
                        }
                        .padding(12.dp)
                        .size(24.dp),
                    tint = Color(0xFF70757D),
                )
                // Center Select button
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDADDE3))
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            player.togglePlay()
                        },
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
