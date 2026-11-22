package io.ymusic.lyrics

import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.toHttpUrlOrNull
import kmphttp.utf8String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.use
import kotlin.coroutines.cancellation.CancellationException

/** [LyricsHttp] over the app's [KmpHttpClient]. Every failure of the request itself becomes E93002. */
class KmpLyricsHttp(
    private val client: KmpHttpClient,
) : LyricsHttp {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): LyricsHttpResponse {
        val httpUrl = url.toHttpUrlOrNull() ?: throw LyricsProviderException("not a url: $url")
        val builder = RequestBuilder().url(httpUrl)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.get().build()
        try {
            return withContext(Dispatchers.IO) {
                client.execute(request).use { LyricsHttpResponse(it.code, it.body.utf8String()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LyricsProviderException("request failed: $url", e)
        }
    }
}
