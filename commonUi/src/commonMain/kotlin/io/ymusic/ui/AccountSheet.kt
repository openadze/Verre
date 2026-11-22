package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import io.ymusic.dynamicengine.yt.YtAccountInfo
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.account_sign_out
import io.ymusic.ui.resources.account_switch
import io.ymusic.ui.resources.cd_avatar
import io.ymusic.ui.resources.section_downloads
import io.ymusic.ui.resources.section_play_again
import io.ymusic.ui.resources.section_settings
import io.ymusic.ui.resources.signin_button
import org.jetbrains.compose.resources.stringResource

/** Apple Music-style account sheet opened from the header avatar: Google sign-in/switch/sign-out and quick app links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppleAccountSheet(
    guest: Boolean,
    signedIn: Boolean,
    account: YtAccountInfo?,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlayAgain: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    if (account != null && account.thumbs.isNotEmpty()) {
                        ThumbImage(account.thumbs, CircleShape) { ThumbPlaceholder(VerreIcons.PersonOutline, 24.dp) }
                    } else {
                        Box(Modifier.fillMaxSize().background(DesignColors.Placeholder), contentAlignment = Alignment.Center) {
                            Icon(VerreIcons.PersonOutline, null, Modifier.size(26.dp), tint = DesignColors.InkSecondary)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        account?.name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.cd_avatar),
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DesignColors.InkStrong),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = account?.handle?.takeIf { it.isNotBlank() } ?: account?.email?.takeIf { it.isNotBlank() }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = TextStyle(fontSize = 14.sp, color = DesignColors.InkSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (!guest) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DesignColors.Surface)) {
                    if (!signedIn) {
                        AccountActionRow(
                            icon = VerreIcons.AccountCircleOutline,
                            label = stringResource(Res.string.signin_button),
                            tintAccent = true,
                        ) {
                            onDismiss()
                            onLogin()
                        }
                    } else {
                        AccountActionRow(
                            icon = VerreIcons.AccountCircleOutline,
                            label = stringResource(Res.string.account_switch),
                            tintAccent = true,
                        ) {
                            onDismiss()
                            onSwitchAccount()
                        }
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = DesignColors.Divider)
                        AccountActionRow(
                            icon = VerreIcons.PersonOutline,
                            label = stringResource(Res.string.account_sign_out),
                            tintAccent = true,
                        ) {
                            onDismiss()
                            onSignOut()
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DesignColors.Surface)) {
                AccountActionRow(
                    icon = VerreIcons.SettingsOutline,
                    label = stringResource(Res.string.section_settings),
                ) {
                    onDismiss()
                    onOpenSettings()
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = DesignColors.Divider)
                AccountActionRow(
                    icon = VerreIcons.Download,
                    label = stringResource(Res.string.section_downloads),
                ) {
                    onDismiss()
                    onOpenDownloads()
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = DesignColors.Divider)
                AccountActionRow(
                    icon = VerreIcons.HistoryOutline,
                    label = stringResource(Res.string.section_play_again),
                ) {
                    onDismiss()
                    onOpenPlayAgain()
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    tintAccent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (tintAccent) DesignColors.Accent else DesignColors.Ink)
        Text(
            label,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (tintAccent) DesignColors.Accent else DesignColors.Ink),
            modifier = Modifier.weight(1f),
        )
        if (!tintAccent) {
            Icon(VerreIcons.KeyboardArrowRight, null, Modifier.size(20.dp), tint = DesignColors.InkSecondary)
        }
    }
}
