# :lyrics

Verre's lyrics module. commonMain, no UI. Step 1 = model + parsers (pure Kotlin); step 2 = providers, cache,
repository and settings keys. The lyrics screen is step 3 (see `docs/verre/PLAN.md`).

## Model

`LyricsDoc(lines, syncType, source, language)`; `SyncType` is `RICH` (word by word) > `LINE` > `NONE`.
`LyricLine` has `startMs/endMs/text/parts/agent/isBackground/translation/romanization`;
`LyricPart` is one word (or one CJK syllable) with `startMs/durationMs/text/isBackground`.
`LyricPart.text` carries a trailing space when a space follows the word, so joining the parts rebuilds the line.

## Parsers

| Input | Parser | Result |
|---|---|---|
| LRC (`[mm:ss.xx]`, several stamps per line, `[offset:]`) | `LrcParser` | `LINE` |
| Enhanced LRC (`<mm:ss.xx>` before each word) | `LrcParser` | `RICH` |
| TTML (Apple style: `p` lines, `span` syllables, `x-bg`, agents, translation / romanization) | `TtmlParser` (+ internal `MiniXml`) | `RICH`, or `LINE` without spans |
| Plain text | `PlainParser` | `NONE` |

`LyricsParsers.parse(text)` detects the format. A blank timed LRC line ends the previous line.
TTML syllables are grouped into words; CJK syllables stay one by one.
Every parse failure throws `LyricsParseException` (**E93001**): the caller (a provider) must log it, never swallow it.

## Choosing and playing

- `LyricsSelector.select(candidates, trackDurationMs)`: word > line > plain; a tie goes to the best length match.
- `LyricsTimeline`: `activeIndex(pos)`, `isInstrumentalGap` / `gapProgress` (the three dots), `partProgress`, `lineProgress`.

## Providers, cache, repository (step 2)

```kotlin
val http = KmpLyricsHttp(kmpHttpClient)                       // your KmpHttpClient
val log = LyricsLogger { msg, err -> logger.log(WARN, msg, err) }
val repo = LyricsRepository(
    providers = listOf(LrclibProvider(http, "Verre/<version> (https://github.com/openadze/Verre)", log)),
    cache = FileLyricsCache(FileSystem.SYSTEM, cacheDir / "lyrics", log),
    log = log,
    clock = { currentTimeMillis },
)
val doc = repo.load(LyricsQuery.forTrack(title, artist, durationMs = ms, videoId = id), settings.lyricsOptions())
```

- `LyricsQuery.forTrack` cleans "(Official Video)", "- Topic", "Artist - Title". `cacheKey` = `yt:<videoId>`.
- `LyricsProvider.fetch` -> `Found(doc, raw, format)` / `NotFound`; failures throw `LyricsProviderException` (E93002/E93003)
  or `LyricsParseException` (E93001). The repository logs them and tries the next provider (8 s timeout each).
- `LrclibProvider`: `/api/get`, then `/api/search` on 404 (duration within 5 s, synced > plain). Instrumental = not found.
- `LyricsRepository`: stops early on a word-synced hit (or any synced hit when `preferWordSync` is off), picks with
  `LyricsSelector`. Cache TTL: word-synced 30 d, other 3 d, miss 6 h; a miss is NOT cached after a provider failure.
- Cache stores the raw payload (re-parsed on read). `FileLyricsCache` = one text file per key, atomic write, E93004 on a bad file.
- Settings: `LyricsSettings.ENABLED`, `PREFER_WORD_SYNC`, `PROVIDERS` (ordered ids); read with `SettingsStore.lyricsOptions()`.

## Tests

`./gradlew :lyrics:jvmTest` (31 tests in `commonTest`).
