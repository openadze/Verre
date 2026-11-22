package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.cd_lyrics
import io.ymusic.ui.resources.cd_vocal_level
import io.ymusic.ui.resources.up_next
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Apple Music Sing's vertical pill: a vocal level, 1 = the vocal as mastered, 0 = lead vocal reduced. Drag or tap; the
 * fill rises from the mic at the bottom. The effect is centre-channel cancellation in the vocal band (`:mse`
 * `audio_fx_set_vocal`), not stem separation, so it dulls what else sits in the centre.
 */
@Composable
internal fun VocalLevelPill(level: Float, onLevel: (Float) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    var heightPx by remember { mutableFloatStateOf(1f) }
    val label = stringResource(Res.string.cd_vocal_level)
    fun fractionAt(y: Float) = (1f - y / heightPx).coerceIn(0f, 1f)
    Box(
        modifier
            .width(56.dp)
            .height(176.dp)
            .clip(shape)
            .glassPanel(shape, fallback = DesignColors.InkStrong.copy(alpha = 0.22f))
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onLevel(fractionAt(it.y)) } }
            .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> onLevel(fractionAt(change.position.y)) } }
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = "${(level * 100).roundToInt()}%"
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(level.coerceIn(0f, 1f)).background(DesignColors.InkStrong.copy(alpha = 0.9f)))
        Icon(
            Icons.Outlined.Mic,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 14.dp).size(26.dp),
            tint = if (level > 0.12f) DesignColors.Accent else DesignColors.InkStrong,
        )
    }
}

/** Speaker icons around a slim bar: the app volume (`Player.volume`), as in Apple Music's lower edge. */
@Composable
internal fun PlayerVolumeRow(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, Modifier.size(18.dp), tint = DesignColors.InkMuted)
        SlimSeekBar(
            fraction = volume,
            enabled = true,
            onSeek = onVolume,
            trackColor = DesignColors.InkStrong.copy(alpha = 0.25f),
            fillColor = DesignColors.InkStrong,
            thumbColor = DesignColors.InkStrong,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp), tint = DesignColors.InkMuted)
    }
}

/** Lyrics / Up next switch under the transport, the active one filled with the accent like Apple Music's bottom row. */
@Composable
internal fun LyricsBottomBar(showQueue: Boolean, onShowLyrics: () -> Unit, onShowQueue: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(
            Icons.Outlined.Lyrics,
            stringResource(Res.string.cd_lyrics),
            tint = if (!showQueue) DesignColors.Accent else DesignColors.InkMuted,
            onClick = onShowLyrics,
        )
        ActionIcon(
            Icons.AutoMirrored.Outlined.QueueMusic,
            stringResource(Res.string.up_next),
            tint = if (showQueue) DesignColors.Accent else DesignColors.InkMuted,
            onClick = onShowQueue,
        )
    }
}
