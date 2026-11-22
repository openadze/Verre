# Verre plan

Goal: an Apple Music look-alike for Android over YouTube Music only, with word-by-word lyrics (LRCLIB) and Liquid Glass.

| Step | What | Where |
|---|---|---|
| 0 | Fork and rebrand | done |
| 1 | `:lyrics` core (parsers, selector, timeline) | `lyrics/` |
| 2 | Providers, cache, offline lyrics; LRCLIB `lyricsfile` (word timing) | `lyrics/` |
| 3 | Lyrics screen, word fill, estimated words for line-synced lyrics | `LyricsView.kt` |
| 4 | Liquid Glass | `Glass.kt` |
| 5 | Theme and design | `CoverPalette.kt`, `AppleNowPlaying.kt`, `AppleTabBar.kt` |
| 6 | YouTube removal (YT Music only) | `App.kt`, `Drawer.kt` |
| 7 | Apple Music redesign, screen by screen (see PROGRESS.md) | `commonUi` |
| 8 | Polish, localisation of the Apple labels, compile and device test | all |

Design references (Apple Music iOS 16): Listen Now (Top Picks cards, Recently Played, mini player over a 5-tab bar),
Now Playing (big cover that shrinks when paused, title / artist / star / more, 4dp progress with -remaining time,
bare rewind / pause / forward glyphs, volume bar, bottom row lyrics · output pill · queue), Playing Next (shuffle /
repeat / autoplay chips, 44dp covers, drag handles), lyrics (large bold, active line white, others 35% and blurred).
