# :lyrics

Verre's lyrics core. Pure Kotlin (commonMain), no UI, no network, no dependencies. Providers, the cache and the
lyrics screen are separate steps (see `docs/verre/PLAN.md`, steps 2 and 3).

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

## Tests

`./gradlew :lyrics:jvmTest` (13 tests in `commonTest`).
