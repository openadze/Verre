package io.ymusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One action of a [SongMenuSheet]: a glyph, its label and what it does. */
internal class SongMenuAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The ⋮ song menu on touch platforms, drawn as Apple Music draws it: a flat bottom sheet — no
 * gradient, surface color only — with one icon-led row per action. The rows stagger in on open
 * (each ~20ms after the last), so the menu reads as arriving rather than popping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongMenuSheet(
    actions: List<SongMenuAction>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DesignColors.Surface,
        // Flat sheet: no drag handle, the rows carry the shape.
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            actions.forEachIndexed { index, action ->
                // Every row starts hidden and slides up into place, staggered down the list.
                val appeared = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = appeared,
                    enter = slideInVertically(tween(240, delayMillis = index * 20)) { it / 3 } +
                        fadeIn(tween(240, delayMillis = index * 20)),
                ) {
                    SheetActionRow(action) { action.onClick(); onDismiss() }
                }
                if (index != actions.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 60.dp),
                        thickness = 0.5.dp,
                        color = DesignColors.DividerMini,
                    )
                }
            }
            // The sheet's own bottom breathing room.
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One menu row: a glyph in a rounded tinted square (Apple's setting-icon look, flat fill), then the label. */
@Composable
private fun SheetActionRow(action: SongMenuAction, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(DesignColors.SettingsCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, null, Modifier.size(18.dp), tint = DesignColors.Accent)
        }
        Text(
            action.label,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DesignColors.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
