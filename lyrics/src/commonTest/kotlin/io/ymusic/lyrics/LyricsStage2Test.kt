package io.ymusic.lyrics

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LRC = "[00:01.00]Hello\n[00:03.00]World\n[00:06.00]"
private const val PLAIN = "Hello\nWorld"

private fun item(
    synced: String? = null,
    plain: String? = null,
    duration: Int = 200,
    instrumental: Boolean = false,
): String {
    fun q(s: String?) = s?.let { "\"" + it.replace("\n", "\\n") + "\"" } ?: "null"
    return """{"id":1,"duration":$duration,"instrumental":$instrumental,"plainLyrics":${q(plain)},"syncedLyrics":${q(synced)}}"""
}

/** Answers by url prefix; counts calls. */
private class FakeHttp(
    private val routes: List<Pair<String, LyricsHttpResponse>>,
) : LyricsHttp {
    val urls = ArrayList<String>()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): LyricsHttpResponse {
        urls.add(url)
        assertTrue(headers["User-Agent"]!!.isNotBlank())
        return routes.first { url.contains(it.first) }.second
    }
}

private class FakeProvider(
    override val id: String,
    private val result: () -> LyricsResult,
) : LyricsProvider {
    var calls = 0

    override suspend fun fetch(query: LyricsQuery): LyricsResult {
        calls++
        return result()
    }
}

private fun found(
    type: SyncType,
    source: String,
): LyricsResult.Found {
    val doc = LyricsDoc(listOf(LyricLine(0, 1000, "x")), type, source)
    return LyricsResult.Found(doc, "raw-$source", if (type == SyncType.NONE) LyricsFormat.PLAIN else LyricsFormat.LRC)
}

class QueryTest {
    @Test
    fun cleansYoutubeNoise() {
        assertEquals("Dance in the Darkness", LyricsQueryCleaner.title("Dance in the Darkness (Official Video)"))
        assertEquals("Song", LyricsQueryCleaner.title("Song [Lyrics]"))
        assertEquals("Song", LyricsQueryCleaner.title("Song - Official Audio"))
        assertEquals("Song (Remastered 2011)", LyricsQueryCleaner.title("Song (Remastered 2011)"))
        assertEquals("Song", LyricsQueryCleaner.title("RIVER - Song", "RIVER"))
        assertEquals("RIVER", LyricsQueryCleaner.artist("RIVER - Topic"))
        assertEquals("Adele", LyricsQueryCleaner.artist("AdeleVEVO"))
        assertEquals("A", LyricsQueryCleaner.primaryArtist("A, B & C"))
    }

    @Test
    fun cacheKeyPrefersVideoId() {
        assertEquals("yt:abc", LyricsQuery("t", "a", videoId = "abc").cacheKey)
        assertEquals("q:song|river|200", LyricsQuery("Song!", "RIVER", durationMs = 200_500).cacheKey)
    }
}

class LrclibTest {
    private val ua = "Verre/test"

    @Test
    fun getSyncedBecomesLineDoc() =
        runTest {
            val http = FakeHttp(listOf("/get?" to LyricsHttpResponse(200, item(synced = LRC, plain = PLAIN))))
            val r = LrclibProvider(http, ua).fetch(LyricsQuery("Song", "Art & Co", album = "Alb", durationMs = 200_000))
            val f = r as LyricsResult.Found
            assertEquals(SyncType.LINE, f.doc.syncType)
            assertEquals(LyricsFormat.LRC, f.format)
            assertTrue(http.urls.single().contains("track_name=Song&artist_name=Art%20%26%20Co&album_name=Alb&duration=200"))
        }

    @Test
    fun plainOnlyWhenNoSynced() =
        runTest {
            val http = FakeHttp(listOf("/get?" to LyricsHttpResponse(200, item(plain = PLAIN))))
            val f = LrclibProvider(http, ua).fetch(LyricsQuery("Song", "Art")) as LyricsResult.Found
            assertEquals(SyncType.NONE, f.doc.syncType)
        }

    @Test
    fun instrumentalIsNotFound() =
        runTest {
            val http = FakeHttp(listOf("/get?" to LyricsHttpResponse(200, item(instrumental = true))))
            assertEquals(LyricsResult.NotFound, LrclibProvider(http, ua).fetch(LyricsQuery("Song", "Art")))
        }

    @Test
    fun fallsBackToSearchAndMatchesDuration() =
        runTest {
            val arr = "[" + item(synced = LRC, duration = 400) + "," + item(plain = PLAIN, duration = 201) + "," + item(synced = LRC, duration = 199) + "]"
            val http = FakeHttp(listOf("/get?" to LyricsHttpResponse(404, "{}"), "/search?" to LyricsHttpResponse(200, arr)))
            val f = LrclibProvider(http, ua).fetch(LyricsQuery("Song", "A, B", durationMs = 200_000)) as LyricsResult.Found
            // the 400 s candidate is out; of the rest the synced one wins over plain
            assertEquals(SyncType.LINE, f.doc.syncType)
            assertTrue(http.urls.last().contains("artist_name=A&") || http.urls.last().endsWith("artist_name=A"))
        }

    @Test
    fun serverErrorThrowsE93002() =
        runTest {
            val http = FakeHttp(listOf("/get?" to LyricsHttpResponse(500, "")))
            val e = assertFailsWith<LyricsProviderException> { LrclibProvider(http, ua).fetch(LyricsQuery("Song", "Art")) }
            assertEquals("E93002", e.code)
        }

    @Test
    fun badJsonThrowsE93003() =
        runTest {
            val http = FakeHttp(listOf("/get?" to LyricsHttpResponse(200, "{oops")))
            val e = assertFailsWith<LyricsProviderException> { LrclibProvider(http, ua).fetch(LyricsQuery("Song", "Art")) }
            assertEquals("E93003", e.code)
        }

    @Test
    fun blankTitleIsNotFoundWithoutRequest() =
        runTest {
            val http = FakeHttp(emptyList())
            assertEquals(LyricsResult.NotFound, LrclibProvider(http, ua).fetch(LyricsQuery("", "Art")))
            assertTrue(http.urls.isEmpty())
        }
}

class MiniJsonTest {
    @Test
    fun parsesEscapesAndNesting() {
        val v = MiniJson.parse("""{"a":[1,2.5,true,null],"s":"x\né\"","o":{}}""") as Map<*, *>
        assertEquals(listOf(1.0, 2.5, true, null), v["a"])
        assertEquals("x\né\"", v["s"])
        assertEquals(emptyMap<String, Any?>(), v["o"])
    }
}

class CacheCodecTest {
    @Test
    fun roundTrip() {
        val e = LyricsCacheEntry(true, 123L, "lrclib", LyricsFormat.LRC, "[00:01.00]a\n[00:02.00]b|c")
        assertEquals(e, LyricsCacheCodec.decode(LyricsCacheCodec.encode(e)))
        val miss = LyricsCacheEntry(false, 9L, "", null, "")
        assertEquals(miss, LyricsCacheCodec.decode(LyricsCacheCodec.encode(miss)))
    }

    @Test
    fun garbageThrowsE93004() {
        val e = assertFailsWith<LyricsCacheException> { LyricsCacheCodec.decode("hello") }
        assertTrue(e.message!!.startsWith("E93004"))
        assertFailsWith<LyricsCacheException> { LyricsCacheCodec.decode("VERRE-LYRICS-1|hit|x|LRC|s\nraw") }
    }
}

class RepositoryTest {
    private var now = 1_000_000L
    private val warnings = ArrayList<String>()
    private val log = LyricsLogger { m, _ -> warnings.add(m) }
    private val q = LyricsQuery("Song", "Art", durationMs = 200_000, videoId = "v1")

    private fun repo(
        vararg p: LyricsProvider,
        cache: LyricsCache = InMemoryLyricsCache(),
    ) = LyricsRepository(p.toList(), cache, log, { now })

    private val opts = LyricsOptions(providerIds = listOf("a", "b"))

    @Test
    fun hitIsCachedAndParsedAgain() =
        runTest {
            val real = LyricsParsers.parse(LRC, "lrclib", LyricsFormat.LRC)
            val a = FakeProvider("a") { LyricsResult.Found(real, LRC, LyricsFormat.LRC) }
            val r = repo(a)
            assertNotNull(r.load(q, opts))
            assertEquals(SyncType.LINE, r.load(q, opts)!!.syncType)
            assertEquals(1, a.calls)
        }

    @Test
    fun expiredEntryRefetches() =
        runTest {
            val a = FakeProvider("a") { found(SyncType.LINE, "a") }
            val cache = InMemoryLyricsCache()
            val r = repo(a, cache = cache)
            // stored payload "raw-a" is not LRC → plain parse happens to succeed; what matters is the call count
            r.load(q, opts)
            now += 4 * LyricsRepository.DAY_MS
            r.load(q, opts)
            assertEquals(2, a.calls)
        }

    @Test
    fun stopsEarlyOnWordSyncButKeepsLookingForLine() =
        runTest {
            val a = FakeProvider("a") { found(SyncType.LINE, "a") }
            val b = FakeProvider("b") { found(SyncType.RICH, "b") }
            assertEquals(SyncType.RICH, repo(a, b).load(q, opts)!!.syncType)
            assertEquals(1, a.calls)
            assertEquals(1, b.calls)

            val a2 = FakeProvider("a") { found(SyncType.LINE, "a") }
            val b2 = FakeProvider("b") { found(SyncType.RICH, "b") }
            repo(a2, b2).load(q.copy(videoId = "v2"), opts.copy(preferWordSync = false))
            assertEquals(0, b2.calls)
        }

    @Test
    fun missIsCachedOnlyWhenNoProviderFailed() =
        runTest {
            val a = FakeProvider("a") { LyricsResult.NotFound }
            val r = repo(a)
            assertNull(r.load(q, opts))
            assertNull(r.load(q, opts))
            assertEquals(1, a.calls)

            val bad = FakeProvider("a") { throw LyricsProviderException("down") }
            val r2 = repo(bad)
            assertNull(r2.load(q, opts))
            assertNull(r2.load(q, opts))
            assertEquals(2, bad.calls)
            assertTrue(warnings.any { it.contains("failed") })
        }

    @Test
    fun parseFailureIsLoggedAndNextProviderUsed() =
        runTest {
            val a = FakeProvider("a") { throw LyricsParseException("broken") }
            val b = FakeProvider("b") { found(SyncType.NONE, "b") }
            assertEquals(SyncType.NONE, repo(a, b).load(q, opts)!!.syncType)
            assertTrue(warnings.any { it.contains("do not parse") })
        }

    @Test
    fun disabledAndUnknownProviders() =
        runTest {
            val a = FakeProvider("a") { found(SyncType.LINE, "a") }
            assertNull(repo(a).load(q, opts.copy(enabled = false)))
            assertNull(repo(a).load(q, LyricsOptions(providerIds = listOf("zzz"))))
            assertEquals(0, a.calls)
        }
}
