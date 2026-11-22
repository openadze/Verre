package io.ymusic.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

/**
 * Disk cache: one small text file per key under [dir] (name = SHA-256 of the key), written through a temp file and
 * moved into place so a crash never leaves a half file. An unreadable file is logged (E93004), deleted and read as a
 * miss. Expired files are left to the repository to overwrite; [clear] wipes the directory.
 */
class FileLyricsCache(
    private val fs: FileSystem,
    private val dir: Path,
    private val log: LyricsLogger? = null,
) : LyricsCache {
    private fun fileFor(key: String): Path = dir / (key.encodeUtf8().sha256().hex() + ".lyrics")

    override suspend fun get(key: String): LyricsCacheEntry? =
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (!fs.exists(file)) return@withContext null
            try {
                LyricsCacheCodec.decode(fs.read(file) { readUtf8() })
            } catch (e: LyricsCacheException) {
                log?.warn("lyrics cache: dropping $file", e)
                fs.delete(file)
                null
            }
        }

    override suspend fun put(
        key: String,
        entry: LyricsCacheEntry,
    ) = withContext(Dispatchers.IO) {
        fs.createDirectories(dir)
        val file = fileFor(key)
        val tmp = dir / (file.name + ".tmp")
        fs.write(tmp) { writeUtf8(LyricsCacheCodec.encode(entry)) }
        fs.atomicMove(tmp, file)
    }

    override suspend fun clear() =
        withContext(Dispatchers.IO) {
            if (fs.exists(dir)) fs.deleteRecursively(dir)
        }
}
