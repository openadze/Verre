# Verre progress

Last updated 2026-11-22. **Nothing below has been compiled on a device or with Gradle** (no SDK in the session that
wrote it); only the `:lyrics` parser files were compiled and run with a standalone `kotlinc` harness.

## Done

- **LRCLIB word timing.** `LyricsfileParser` reads LRCLIB's `lyricsfile` YAML (lines + optional per-word
  `start_ms` / `end_ms`, inline or block form, missing `end_ms` interpolated). `LrclibProvider` prefers
  `lyricsfile` > `syncedLyrics` > `plainLyrics` for `/api/get` and every `/api/search` candidate; a bad lyricsfile is
  logged and the legacy fields are used. `LyricsFormat.LYRICSFILE` is new (old cache entries still decode).
  Tests: `lyrics/src/commonTest/.../LyricsfileTest.kt`.
- **Estimated words.** `LyricsDoc.withEstimatedWords()` spreads a line's time over its words (weighted by length,
  max 6 s, 8% breath) for line-synced docs, so the word fill works on every song. The doc stays `SyncType.LINE`, so
  the selector never ranks an estimate above real word timing. Applied in `LyricsView`.
- **YT Music only.** `AppSection.Discover` is no longer listed (drawer / rail), no longer resumed, and every
  fallback (back, deep links, "search online", feedback dismiss, search) now lands on YT Music with
  `SearchSession.music`. The Discover screen code is still there but unreachable.
- **Apple Music player** (`AppleNowPlaying.kt`, used on touch phones in portrait): one screen with Cover / Lyrics /
  Playing Next modes, cover shrinks while paused, star + more chips, progress with elapsed / -remaining, bare
  rewind / play-pause / forward, volume bar, bottom row lyrics · sleep timer · queue. Landscape / desktop keep the
  old player.
- **Apple mini player** (`AppleMiniPlayer`, floating rounded bar) and **tab bar** (`AppleTabBar.kt`: Listen Now =
  YT Home, Browse = Explore, Radio = Charts, Library, Search). The YT Music top tab row is hidden on phones; the
  bar drives it through `AppSettings.lastYtMusicTab`.
- **Apple Music redesign pass** (2026-11, still not compiled): the five tab glyphs are drawn as Apple's own
  (SF Symbols' music notes / grid / radiowaves / books / magnifier — `AppleIcons.kt`, SVG path data through
  `addPathNodes`), the bar is a frosted pane (blur + veil over the screens backdrop), and the tab labels use the
  Apple wording (`tab_listen_now` / `tab_browse` / `tab_radio`, base + pt so far). Phones render the music feed
  tabs as Apple's home (`ListenNow.kt` -> `AppleHomeFeed`): large title + account avatar (opens Settings), the
  first section as **Top Picks** large cards, the rest as square-tile shelves with red See-All chevrons, moods &
  genres as colored gradient tiles; the shell top bar is hidden there (it owns nothing). Lyrics bumped to the
  reference (34sp, others ~35% + blur, no shrink), the player's artist line moved to Apple's artist credit pill,
  and both palettes re-grounded on Apple's system colors (dark = true black + #1C1C1E, light = black ink on
  white, dividers #D1D1D6; the default accent is Apple's #FA233B).

## Next (in order)

1. Compile (`./gradlew :androidApp:installDebug`, `:lyrics:jvmTest`, `spotlessApply`) and fix whatever the compiler says;
   check `PlayerMoreMenu` (now `internal`) renders as an icon inside the 32dp chip.
2. Listen Now polish: a real "Recently Played" shelf from the play-history store, Top Picks eyebrow labels, and
   the large title collapsing into a small pinned bar while scrolling (like Apple's).
3. Browse / Radio beyond the shared home layout: category tiles grid on Browse, featured-station cards on Radio;
   Search scope pills (Apple Music / Library).
4. Library: Apple's list (Playlists, Artists, Albums, Songs, Downloaded) with 44dp indexed covers.
5. Playing Next: autoplay (infinity) chip needs a player-side radio toggle; star = favourite (needs a store).
6. Strings: add `tab_listen_now` / `tab_browse` / `tab_radio` / `top_picks` / `see_all` / `cd_avatar` to the
   remaining locales (only base and pt have them; the rest fall back to English).
8. Verify on a device that audio-only playback works with no video surface in the mini player and Now Playing
   (`needsSurface`); if the MSE engine needs the surface, host it off-screen.
9. Remove the dead Discover code and its tests (`AppNavigationTest`).
10. Confirm LRCLIB really returns `lyricsfile` on `/api/get` (not reachable from the session sandbox).
