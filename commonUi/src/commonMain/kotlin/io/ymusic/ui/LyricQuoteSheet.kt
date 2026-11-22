package io.ymusic.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.lyrics.LyricsDoc
import io.ymusic.player.PlayableItem
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.feedback_copied
import io.ymusic.ui.resources.feedback_copy
import org.jetbrains.compose.resources.stringResource

/** Apple Music-style lyric quote sheet: pick 1-4 lines to copy/share as a formatted card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricQuoteSheet(
    doc: LyricsDoc,
    initialIndex: Int,
    item: PlayableItem?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember(doc, initialIndex) {
        mutableStateListOf<Int>().apply {
            if (initialIndex in doc.lines.indices) add(initialIndex)
        }
    }
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbar.current
    val copiedMsg = stringResource(Res.string.feedback_copied)

    val cardLines = selected.sorted().mapNotNull { doc.lines.getOrNull(it)?.text?.takeIf(String::isNotBlank) }
    val shareText = buildString {
        cardLines.forEach { appendLine(it) }
        val title = item?.displayTitle.orEmpty()
        val artist = item?.displaySubtitle.orEmpty()
        if (title.isNotBlank()) {
            appendLine()
            append("— $title")
            if (artist.isNotBlank()) append(" · $artist")
        }
    }.trim()

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
            // Preview card styled like Apple Music's lyric share card
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AudioOnlyCover(item?.displayThumbnailUrl, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item?.displayTitle.orEmpty(),
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item?.displaySubtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                            Text(
                                sub,
                                style = TextStyle(fontSize = 13.sp, color = Color(0x99FFFFFF)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cardLines.forEach { line ->
                        Text(
                            line,
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 25.sp),
                        )
                    }
                }
            }

            // Selectable window of nearby lines (up to 4)
            val start = (initialIndex - 2).coerceAtLeast(0)
            val end = (initialIndex + 3).coerceAtMost(doc.lines.lastIndex)
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in start..end) {
                    val text = doc.lines[i].text
                    if (text.isBlank()) continue
                    val isPicked = i in selected
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPicked) DesignColors.Accent.copy(alpha = 0.18f) else DesignColors.Surface)
                            .clickable {
                                if (isPicked) {
                                    if (selected.size > 1) selected.remove(i)
                                } else if (selected.size < 4) {
                                    selected.add(i)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = if (isPicked) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPicked) DesignColors.Accent else DesignColors.Ink,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (shareText.isNotBlank()) {
                        clipboard.setText(AnnotatedString(shareText))
                        snackbar(copiedMsg)
                        onDismiss()
                    }
                },
                enabled = cardLines.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(VerreIcons.ContentCopyOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.feedback_copy), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
