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
import io.ymusic.ui.resources.up_next
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Speaker icons around a slim bar: the app volume (`Player.volume`), as in Apple Music's lower edge. */
@Composable
internal fun PlayerVolumeRow(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VerreIcons.VolumeDown, null, Modifier.size(18.dp), tint = DesignColors.InkMuted)
        SlimSeekBar(
            fraction = volume,
            enabled = true,
            onSeek = onVolume,
            trackColor = DesignColors.InkStrong.copy(alpha = 0.25f),
            fillColor = DesignColors.InkStrong,
            thumbColor = DesignColors.InkStrong,
            modifier = Modifier.weight(1f),
        )
        Icon(VerreIcons.VolumeUp, null, Modifier.size(18.dp), tint = DesignColors.InkMuted)
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
            VerreIcons.LyricsOutline,
            stringResource(Res.string.cd_lyrics),
            tint = if (!showQueue) DesignColors.Accent else DesignColors.InkMuted,
            onClick = onShowLyrics,
        )
        ActionIcon(
            VerreIcons.QueueMusicOutline,
            stringResource(Res.string.up_next),
            tint = if (showQueue) DesignColors.Accent else DesignColors.InkMuted,
            onClick = onShowQueue,
        )
    }
}
