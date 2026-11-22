package io.ymusic.lyrics

import io.ymusic.settings.BoolSetting
import io.ymusic.settings.SettingsStore
import io.ymusic.settings.StringSetSetting
import io.ymusic.settings.flow

/** Setting keys for lyrics (the Settings screen comes in step 6). Read them with [lyricsOptions]. */
object LyricsSettings {
    val ENABLED = BoolSetting("lyrics_enabled", true)
    val PREFER_WORD_SYNC = BoolSetting("lyrics_prefer_word_sync", true)

    /** Provider ids, in the order they are tried. Only LRCLIB exists so far. */
    val PROVIDERS = StringSetSetting("lyrics_providers", setOf(LrclibProvider.ID))
}

/** The current lyrics options (a snapshot of the settings' present values). */
fun SettingsStore.lyricsOptions(): LyricsOptions =
    LyricsOptions(
        enabled = flow(LyricsSettings.ENABLED).value,
        preferWordSync = flow(LyricsSettings.PREFER_WORD_SYNC).value,
        providerIds = flow(LyricsSettings.PROVIDERS).value.toList().ifEmpty { LyricsSettings.PROVIDERS.default.toList() },
    )
