package com.nodecast.tv.playlist

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Fetches and caches the M3U playlist. The channel list is persisted so the
 * remote sees its channels again right after an app restart.
 */
class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("playlist", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    var channels: List<Channel> = loadCached()
        private set

    val playlistUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()

    fun refresh(url: String, onResult: (Result<List<Channel>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val content = download(url)
                val parsed = M3uParser.parse(content)
                require(parsed.isNotEmpty()) { "playlist is empty" }
                parsed
            }
            result.onSuccess { parsed ->
                channels = parsed
                prefs.edit()
                    .putString(KEY_URL, url)
                    .putString(KEY_CACHE, Channel.listToJson(parsed).toString())
                    .apply()
            }
            onResult(result)
        }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCached(): List<Channel> = runCatching {
        Channel.listFromJson(JSONArray(prefs.getString(KEY_CACHE, "[]").orEmpty()))
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_URL = "url"
        const val KEY_CACHE = "channels"
    }
}
