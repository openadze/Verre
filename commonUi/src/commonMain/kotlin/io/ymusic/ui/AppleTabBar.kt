package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Sensors
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
import io.ymusic.ui.resources.ytmusic_tab_charts
import io.ymusic.ui.resources.ytmusic_tab_explore
import io.ymusic.ui.resources.ytmusic_tab_home
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Apple Music's red, the selected tab and the accent of the whole phone UI. */
internal val AppleRed = Color(0xFFFA233B)

/**
 * The five bottom tabs of Apple Music. They map onto what Verre has: Listen Now = YT Music Home, Browse = Explore,
 * Radio = Charts, Library = the Library section, Search = YT Music search. Labels reuse existing strings so no locale
 * needs a new one (rename them to the Apple wording together with all locales when the strings are translated).
 */
internal enum class AppleTab(val icon: ImageVector, val iconSelected: ImageVector, val label: StringResource) {
    ListenNow(Icons.Outlined.PlayCircleOutline, Icons.Filled.PlayCircle, Res.string.ytmusic_tab_home),
    Browse(Icons.Outlined.GridView, Icons.Outlined.GridView, Res.string.ytmusic_tab_explore),
    Radio(Icons.Outlined.Sensors, Icons.Outlined.Sensors, Res.string.ytmusic_tab_charts),
    Library(Icons.Outlined.LibraryMusic, Icons.Outlined.LibraryMusic, Res.string.section_library),
    Search(Icons.Filled.Search, Icons.Filled.Search, Res.string.cd_search),
}

/** Translucent bar with a hairline on top, 49dp tall plus the navigation-bar inset; the mini-player sits right above it. */
@Composable
internal fun AppleTabBar(selected: AppleTab, onSelect: (AppleTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(DesignColors.SurfaceDim.copy(alpha = 0.94f))) {
        HorizontalDivider(color = DesignColors.Divider)
        Row(Modifier.fillMaxWidth().height(49.dp)) {
            for (tab in AppleTab.entries) {
                TabItem(tab, tab == selected) { onSelect(tab) }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun RowScope.TabItem(tab: AppleTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) AppleRed else DesignColors.InkSecondary
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .semantics { this.selected = selected }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(if (selected) tab.iconSelected else tab.icon, null, Modifier.size(26.dp), tint = tint)
        Text(stringResource(tab.label), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tint), maxLines = 1)
    }
}
