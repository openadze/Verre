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

## Next (in order)

1. Compile (`./gradlew :androidApp:installDebug`, `:lyrics:jvmTest`, `spotlessApply`) and fix whatever the compiler says;
   check `PlayerMoreMenu` (now `internal`) renders as an icon inside the 32dp chip.
2. Listen Now home: replace the YT Home feed list with Apple's layout (Top Picks large cards, Recently Played row,
   section titles with chevron, profile avatar top-right that opens Settings; drawer becomes secondary).
3. Browse / Radio / Search screens in Apple style (category tiles, large title, search field with
   Apple Music / Library scope).
4. Library: Apple's list (Playlists, Artists, Albums, Songs, Downloaded) with 44dp indexed covers.
5. Playing Next: autoplay (infinity) chip needs a player-side radio toggle; star = favourite (needs a store).
6. Lyrics view: bump to 34sp, tune blur / alpha to the reference, translation + romanization toggles.
7. Strings: tabs reuse Home / Explore / Charts / Library / Search; rename to Listen Now / Browse / Radio and add to
   ALL locales. `up_next` is used for "Playing Next".
8. Verify on a device that audio-only playback works with no video surface in the mini player and Now Playing
   (`needsSurface`); if the MSE engine needs the surface, host it off-screen.
9. Remove the dead Discover code and its tests (`AppNavigationTest`).
10. Confirm LRCLIB really returns `lyricsfile` on `/api/get` (not reachable from the session sandbox).
