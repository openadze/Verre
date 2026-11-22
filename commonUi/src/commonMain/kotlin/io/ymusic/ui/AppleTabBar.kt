package io.ymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.cd_search
import io.ymusic.ui.resources.section_library
import io.ymusic.ui.resources.tab_browse
import io.ymusic.ui.resources.tab_listen_now
import io.ymusic.ui.resources.tab_radio
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Apple Music's red, the selected tab and the accent of the whole phone UI. */
internal val AppleRed = Color(0xFFFA233B)

/**
 * The five bottom tabs, drawn with Material Symbols ([VerreIcons]; outlined, filled when active): Listen Now = YT Music
 * Home, Browse = Explore, Radio = Charts, Library = the Library section, Search = YT Music search.
 * Labels use the Apple wording; every other locale falls back to the base strings until translated.
 */
internal enum class AppleTab(val icon: ImageVector, val selectedIcon: ImageVector, val label: StringResource) {
    ListenNow(VerreIcons.PlayCircleOutline, VerreIcons.PlayCircle, Res.string.tab_listen_now),
    Browse(VerreIcons.GridViewOutline, VerreIcons.GridView, Res.string.tab_browse),
    Radio(VerreIcons.SensorsOutline, VerreIcons.Sensors, Res.string.tab_radio),
    Library(VerreIcons.LibraryMusicOutline, VerreIcons.LibraryMusic, Res.string.section_library),
    Search(VerreIcons.SearchOutline, VerreIcons.Search, Res.string.cd_search),
}

/**
 * The bottom tab bar: a solid pane with a hairline on top, 49dp tall plus the navigation-bar inset.
 * The active tab tints the accent and swaps its outlined glyph for the filled one.
 */
@Composable
internal fun AppleTabBar(selected: AppleTab, onSelect: (AppleTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(DesignColors.SurfaceDim),
    ) {
        HorizontalDivider(color = DesignColors.Divider)
        Row(Modifier.fillMaxWidth().height(49.dp)) {
            for (tab in AppleTab.entries) {
                TabItem(tab, tab == selected) { onSelect(tab) }
            }
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun RowScope.TabItem(tab: AppleTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) DesignColors.Accent else DesignColors.InkSecondary
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .semantics { this.selected = selected }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, null, Modifier.size(25.dp), tint = tint)
        Text(
            stringResource(tab.label),
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tint),
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
