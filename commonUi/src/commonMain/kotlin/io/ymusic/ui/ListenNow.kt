package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ymusic.dynamicengine.yt.YtAccountInfo
import io.ymusic.dynamicengine.yt.YtFeedItem
import io.ymusic.dynamicengine.yt.YtSection
import io.ymusic.dynamicengine.yt.YtSectionHeader
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.cd_avatar
import io.ymusic.ui.resources.cd_play
import io.ymusic.ui.resources.playlist_recent_play_desc
import io.ymusic.ui.resources.see_all
import io.ymusic.ui.resources.show_more
import io.ymusic.ui.resources.top_picks
import io.ymusic.ui.resources.top_picks_sub
import org.jetbrains.compose.resources.stringResource

/**
 * The Apple Music home layout over a YT Music feed (design refs: the Listen Now screenshot) — the
 * phone-portrait rendering of every `ytmusic` surface tab: a large title with the account avatar
 * beside it, then shelves: the first section as **Top Picks** (large artwork cards), the rest as
 * square-tile carousels, moods & genres as colored tiles. Wide windows keep [FeedList].
 *
 * Everything scrolls in one list; pull-to-refresh reloads the pager. Item taps keep the feed's
 * contract: a video plays (its whole section as the queue when the engine marked it
 * [YtSection.PLAY_QUEUE]), playlists / artists / tags navigate through [onOpen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppleHomeFeed(
    pager: Pager,
    coordinator: PlaybackCoordinator,
    title: String,
    account: YtAccountInfo?,
    onAvatar: (() -> Unit)?,
    onOpen: ((YtFeedItem) -> Unit)?,
    onDeeplink: ((link: String, title: String?) -> Unit)?,
    emptyMessage: String,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    recent: List<YtFeedItem> = emptyList(),
) {
    val state by pager.state.collectAsStateWithLifecycle()
    val refreshing = (state as? Pager.State.Ready)?.let { it.refreshing } == true
    val reload = { pager.reload() }
    val content: @Composable () -> Unit = {
        when (val s = state) {
            is Pager.State.Loading -> Column(Modifier.fillMaxSize()) {
                AppleLargeHeader(title, account, onAvatar)
                CenteredBox { CircularProgressIndicator() }
            }
            is Pager.State.Failed -> Column(Modifier.fillMaxSize()) {
                AppleLargeHeader(title, account, onAvatar)
                ErrorState(s.code) { pager.refresh() }
            }
            is Pager.State.Ready -> {
                if (s.sections.all { it.items.isEmpty() }) {
                    Column(Modifier.fillMaxSize()) {
                        AppleLargeHeader(title, account, onAvatar)
                        EmptyState(s.emptyMessage ?: emptyMessage, title = s.emptyTitle) { pager.refresh(s.selectedChipToken) }
                    }
                } else {
                    AppleFeedBody(s, pager, coordinator, title, account, onAvatar, onOpen, onDeeplink, listState, recent)
                }
            }
        }
    }
    if (!platformUsesTouch) {
        content()
    } else {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = reload) { content() }
    }
}

@Composable
private fun AppleFeedBody(
    state: Pager.State.Ready,
    pager: Pager,
    coordinator: PlaybackCoordinator,
    title: String,
    account: YtAccountInfo?,
    onAvatar: (() -> Unit)?,
    onOpen: ((YtFeedItem) -> Unit)?,
    onDeeplink: ((link: String, title: String?) -> Unit)?,
    hoistedListState: androidx.compose.foundation.lazy.LazyListState?,
    recent: List<YtFeedItem>,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The first section with items becomes Top Picks; the wide card leaves just over a card of
        // the next one peeking, the way Apple crops its carousel.
        val topPicks = state.sections.firstOrNull { it.items.isNotEmpty() }
        val shelves = if (topPicks != null) state.sections.filter { it !== topPicks } else state.sections
        val recentTitle = stringResource(Res.string.playlist_recent_play_desc)
        val list = hoistedListState ?: rememberLazyListState()
        val moreFocus = remember { LoadMoreFocus() }
        val itemTotal = state.sections.sumOf { it.items.size }
        moreFocus.Settle(loading = state.loadingMore, count = itemTotal, controlShown = state.continuation != null)
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)) {
            item(key = "header") { AppleLargeHeader(title, account, onAvatar) }
            topPicks?.let { picks ->
                item(key = "top-picks-header") { TopPicksHeader() }
                item(key = "top-picks-row") { TopPicksRow(picks, coordinator, maxWidth, onOpen) }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    AppleShelf(
                        YtSection(
                            header = YtSectionHeader(title = recentTitle),
                            items = recent,
                            playMode = YtSection.PLAY_QUEUE,
                        ),
                        coordinator,
                        onOpen,
                        onDeeplink,
                    )
                }
            }
            shelves.forEachIndexed { index, section ->
                item(key = "shelf-$index") { AppleShelf(section, coordinator, onOpen, onDeeplink) }
            }
            if (state.continuation != null) {
                item(key = "show-more") {
                    if (state.loadingMore) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            LoadingMoreIndicator(moreFocus)
                        }
                    } else {
                        ShowMoreButton(
                            stringResource(Res.string.show_more),
                            expanded = false,
                            modifier = moreFocus.control,
                            onClick = {
                                moreFocus.start(itemTotal)
                                pager.loadMore()
                            },
                        )
                    }
                }
            }
        }
        val collapsed by remember(list) {
            derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 40 }
        }
        val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f)
        if (barAlpha > 0f) {
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = barAlpha }.background(DesignColors.Surface)) {
                Text(
                    title,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.InkStrong),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                HorizontalDivider(color = DesignColors.Divider)
            }
        }
    }
}

/**
 * The screen's head: the large title (28sp bold, Apple's Large Title) with the account avatar in a
 * circle beside it — Apple Music's profile picture, which there opens the account page; here it
 * opens Settings.
 */
@Composable
internal fun AppleLargeHeader(title: String, account: YtAccountInfo?, onAvatar: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DesignColors.InkStrong),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onAvatar != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onAvatar,
                    )
                    .a11yActionLabel(stringResource(Res.string.cd_avatar)),
                contentAlignment = Alignment.Center,
            ) {
                if (account != null && account.thumbs.isNotEmpty()) {
                    ThumbImage(account.thumbs, CircleShape) { ThumbPlaceholder(VerreIcons.PersonOutline, 18.dp) }
                } else {
                    Box(Modifier.fillMaxSize().background(DesignColors.Placeholder), contentAlignment = Alignment.Center) {
                        Icon(VerreIcons.PersonOutline, null, Modifier.size(20.dp), tint = DesignColors.InkSecondary)
                    }
                }
            }
        }
    }
}

/** "Top Picks" with its gray subtitle, like Apple's "Top Picks / Just Launched". */
@Composable
private fun TopPicksHeader() {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 22.dp, bottom = 12.dp)) {
        Text(
            stringResource(Res.string.top_picks),
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(Res.string.top_picks_sub),
            style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The Top Picks carousel: large cards, most of one card on screen and the next peeking. */
@Composable
private fun TopPicksRow(
    section: YtSection,
    coordinator: PlaybackCoordinator,
    maxWidth: androidx.compose.ui.unit.Dp,
    onOpen: ((YtFeedItem) -> Unit)?,
) {
    val rowState = rememberLazyListState()
    val cardWidth = (maxWidth - 44.dp).coerceAtMost(340.dp)
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(section.items.take(TOP_PICKS_COUNT)) { item ->
            TopPickCard(item, cardWidth) { appleItemClick(item, section, coordinator, onOpen) }
        }
    }
}

private const val TOP_PICKS_COUNT = 8

/**
 * One Top Picks card: full-bleed artwork with a bottom scrim, the title and artist in white over
 * it, and a glass play chip in the corner — Apple's Music Replay / featured-playlist card.
 */
@Composable
private fun TopPickCard(item: YtFeedItem, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(1.45f)
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .a11yRowLabel(listOfNotNull(item.title, item.subtitleLine1).joinToString(", ")),
    ) {
        ThumbImage(item.thumbs, shape) { ThumbPlaceholder(VerreIcons.MusicNoteOutline, 30.dp) }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color(0x00000000), Color(0x66000000), Color(0xCC000000))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                item.title,
                style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(item.subtitleLine1, item.subtitleLine2).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = TextStyle(fontSize = 13.sp, color = Color(0xB3FFFFFF)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VerreIcons.PlayArrow, stringResource(Res.string.cd_play), Modifier.size(20.dp), tint = Color.White)
        }
    }
}

/**
 * Every shelf below Top Picks: Apple's section header (20sp bold title, red chevron when the
 * section links on) over a horizontal row of tiles — square covers for music, round avatars for
 * artists, colored tiles for moods & genres, or the avatar carousel the engine asked for.
 */
@Composable
internal fun AppleShelf(
    section: YtSection,
    coordinator: PlaybackCoordinator,
    onOpen: ((YtFeedItem) -> Unit)?,
    onDeeplink: ((link: String, title: String?) -> Unit)?,
) {
    if (section.items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        AppleSectionHeaderRow(
            title = sectionTitleText(section),
            onSeeAll = section.header?.ctaDeeplink?.let { link -> onDeeplink?.let { cb -> { cb(link, section.header?.title) } } },
        )
        when {
            section.layout == YtSection.LAYOUT_TAGS -> TagTileGrid(section.items, onOpen)
            section.layout == YtSection.LAYOUT_CAROUSEL -> ChannelCarousel(section.items, onOpen)
            else -> ShelfRow(section, coordinator, onOpen)
        }
    }
}

/** The header of a shelf: bold title, and Apple's red "See All" chevron when the section links on. */
@Composable
internal fun AppleSectionHeaderRow(title: String, onSeeAll: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (title.isEmpty() && onSeeAll == null) return
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onSeeAll != null) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onSeeAll,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.see_all),
                    style = TextStyle(fontSize = 15.sp, color = DesignColors.Accent),
                )
                Icon(
                    VerreIcons.KeyboardArrowRight,
                    null,
                    Modifier.padding(start = 1.dp).size(18.dp),
                    tint = DesignColors.Accent,
                )
            }
        }
    }
}

/** A shelf's horizontal tile row: square covers (or round artist avatars), 160dp wide. */
@Composable
private fun ShelfRow(section: YtSection, coordinator: PlaybackCoordinator, onOpen: ((YtFeedItem) -> Unit)?) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(section.items) { item ->
            AppleTile(item, onClick = appleItemClick(item, section, coordinator, onOpen))
        }
    }
}

/** One shelf tile: a 160dp cover (square for music, round for artists) over title and subtitle. */
@Composable
internal fun AppleTile(item: YtFeedItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val artist = item.kind == YtFeedItem.KIND_CHANNEL
    Column(
        modifier
            .width(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .a11yRowLabel(listOfNotNull(item.title, item.subtitleLine1).joinToString(", ")),
    ) {
        Box(
            Modifier.size(160.dp).clip(if (artist) CircleShape else RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ThumbImage(item.thumbs, if (artist) CircleShape else RoundedCornerShape(8.dp)) {
                ThumbPlaceholder(VerreIcons.PersonOutline, 30.dp)
            }
        }
        Text(
            item.title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = if (item.subtitleLine1 != null) 0.dp else 2.dp),
        )
        item.subtitleLine1?.let {
            Text(
                it,
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * Moods & genres as Apple's Browse tiles: two per row, each a colored gradient with the mood's
 * name in white. The engine sends YouTube Music's per-mood accent (8-hex AARRGGBB); without one a
 * fixed vibrant ladder keeps the grid colorful.
 */
@Composable
private fun TagTileGrid(items: List<YtFeedItem>, onOpen: ((YtFeedItem) -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { column, item ->
                    TagTile(item, column, Modifier.weight(1f)) { onOpen?.invoke(item) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Apple's vibrant tile ladder for moods the engine sent no accent for. */
private val TAG_TILE_COLORS = listOf(
    Color(0xFF6482F5), Color(0xFFDD4FA6), Color(0xFFF09A37), Color(0xFF34A853),
    Color(0xFF9333EA), Color(0xFFEC5B5B), Color(0xFF0EA5B7), Color(0xFF8B5CF6),
)

@Composable
private fun TagTile(item: YtFeedItem, column: Int, modifier: Modifier, onClick: () -> Unit) {
    // Int.mod is always non-negative — a plain % on a negative hashCode would index-crash.
    val base = remember(item.accent, item.title) {
        item.accent?.let { runCatching { Color(it.toLong(16)) }.getOrNull() }
            ?: TAG_TILE_COLORS[(item.title.hashCode() + column).mod(TAG_TILE_COLORS.size)]
    }
    Box(
        modifier
            .height(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(base)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .a11yRowLabel(item.title),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            item.title,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

/**
 * The feed's tap contract, in the Apple layout: a video plays (its whole section as the queue when
 * the engine marked it [YtSection.PLAY_QUEUE]); a playlist, artist or mood navigates. No tap when
 * there is nothing to navigate with.
 */
private fun appleItemClick(
    item: YtFeedItem,
    section: YtSection,
    coordinator: PlaybackCoordinator,
    onOpen: ((YtFeedItem) -> Unit)?,
): () -> Unit = when (item.kind) {
    YtFeedItem.KIND_VIDEO -> {
        {
            if (section.playMode == YtSection.PLAY_QUEUE) {
                val videos = section.items.filter { it.kind == YtFeedItem.KIND_VIDEO }
                coordinator.playAll(videos, startIndex = videos.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
            } else {
                coordinator.playNow(item)
            }
        }
    }
    YtFeedItem.KIND_PLAYLIST, YtFeedItem.KIND_CHANNEL, YtFeedItem.KIND_TAG -> {
        { onOpen?.invoke(item) }
    }
    else -> {
        {}
    }
}

/** A shelf's display title — localized like the feed's own headers (engine titleKeys resolve here too). */
@Composable
private fun sectionTitleText(section: YtSection): String = sectionTitle(section.header)
