package com.nodecast.tv.server

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nodecast.tv.pairing.Pairing
import com.nodecast.tv.playlist.Channel
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded HTTP + WebSocket server. Serves the phone remote (a single HTML
 * page) over HTTP and takes playback commands over a WebSocket. A client
 * authorizes itself with the four-digit pairing code from the QR code.
 */
class ControlServer(
    private val context: Context,
    private val listener: Listener,
) : NanoWSD(Pairing.PORT) {

    interface Listener {
        fun onPlay(url: String, name: String, group: String)
        fun onTogglePlay()
        fun onPause()
        fun onResume()
        fun onStopCast()
        fun onSeek(deltaSeconds: Long)
        fun onVolume(value: Float)
        fun onSetPlaylist(url: String)
        fun onClientsChanged(count: Int, newestName: String?)
        fun currentStatus(): JSONObject
        fun currentChannels(): List<Channel>
        fun currentPlaylistUrl(): String
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clients = CopyOnWriteArrayList<RemoteSocket>()
    private val pairingCode = Pairing.code(context)
    private val pairingToken = Pairing.token(context)
    private var pingTimer: Timer? = null

    // Rate limit for the human-typable 4-digit code (the QR token is not
    // brute-forceable and stays exempt).
    private val codeAttempts = ArrayDeque<Long>()

    @Synchronized
    private fun codeAttemptAllowed(): Boolean {
        val now = System.currentTimeMillis()
        while (codeAttempts.isNotEmpty() && now - codeAttempts.first() > CODE_ATTEMPT_WINDOW_MS) {
            codeAttempts.removeFirst()
        }
        if (codeAttempts.size >= CODE_ATTEMPT_MAX) return false
        codeAttempts.addLast(now)
        return true
    }

    fun startServer() {
        start(0, true)
        pingTimer = Timer("ws-ping", true).also {
            it.schedule(object : TimerTask() {
                override fun run() = pingClients()
            }, PING_INTERVAL_MS, PING_INTERVAL_MS)
        }
    }

    fun stopServer() {
        pingTimer?.cancel()
        pingTimer = null
        stop()
    }

    // --- HTTP ---

    override fun serveHttp(session: IHTTPSession): Response {
        return when (session.uri) {
            "/", "/index.html" -> {
                val html = context.assets.open("remote/index.html").bufferedReader().use { it.readText() }
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html).apply {
                    addHeader("Cache-Control", "no-store")
                }
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "not found")
        }
    }

    // --- WebSocket ---

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = RemoteSocket(handshake)

    fun broadcast(message: JSONObject) {
        val payload = message.toString()
        clients.forEach { client ->
            if (client.authorized) client.trySend(payload)
        }
    }

    fun broadcastStatus() {
        broadcast(listener.currentStatus().put("type", "status"))
    }

    fun broadcastChannels() {
        broadcast(channelsMessage())
    }

    fun broadcastToast(message: String) {
        broadcast(JSONObject().put("type", "toast").put("message", message))
    }

    private fun channelsMessage(): JSONObject = JSONObject()
        .put("type", "channels")
        .put("playlistUrl", listener.currentPlaylistUrl())
        .put("channels", Channel.listToJson(listener.currentChannels()))

    private fun pingClients() {
        clients.forEach { client ->
            try {
                client.ping(PING_PAYLOAD)
            } catch (e: IOException) {
                Log.d(TAG, "ping failed, dropping client", e)
                clients.remove(client)
            }
        }
        notifyClientsChanged(null)
    }

    private fun notifyClientsChanged(newestName: String?) {
        val count = clients.count { it.authorized }
        mainHandler.post { listener.onClientsChanged(count, newestName) }
    }

    inner class RemoteSocket(handshake: IHTTPSession) : WebSocket(handshake) {

        @Volatile
        var authorized = false
            private set

        private var deviceName: String = ""

        fun trySend(payload: String) {
            try {
                send(payload)
            } catch (e: IOException) {
                Log.d(TAG, "send failed, dropping client", e)
                clients.remove(this)
            }
        }

        override fun onOpen() {
            clients.add(this)
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            clients.remove(this)
            notifyClientsChanged(null)
        }

        override fun onMessage(message: WebSocketFrame) {
            val msg = runCatching { JSONObject(message.textPayload) }.getOrNull() ?: return
            val type = msg.optString("type")
            if (!authorized) {
                if (type == "hello") handleHello(msg)
                return
            }
            when (type) {
                "play" -> post {
                    listener.onPlay(
                        msg.optString("url"),
                        msg.optString("name"),
                        msg.optString("group"),
                    )
                }
                "toggle" -> post { listener.onTogglePlay() }
                "pause" -> post { listener.onPause() }
                "resume" -> post { listener.onResume() }
                "stop" -> post { listener.onStopCast() }
                "seek" -> post { listener.onSeek(msg.optLong("delta")) }
                "volume" -> post { listener.onVolume(msg.optDouble("value", 1.0).toFloat().coerceIn(0f, 1f)) }
                "set_playlist" -> post { listener.onSetPlaylist(msg.optString("url")) }
                "get_state" -> {
                    trySend(listener.currentStatus().put("type", "status").toString())
                    trySend(channelsMessage().toString())
                }
                else -> Unit
            }
        }

        override fun onPong(pong: WebSocketFrame?) = Unit

        override fun onException(exception: IOException?) {
            clients.remove(this)
        }

        private fun handleHello(msg: JSONObject) {
            val token = msg.optString("token")
            val tokenOk = token.isNotEmpty() && token == pairingToken
            val codeOk = !tokenOk && msg.optString("code").let { code ->
                code.isNotEmpty() && when {
                    !codeAttemptAllowed() -> {
                        trySend(JSONObject().put("type", "error").put("error", "rate_limited").toString())
                        runCatching { close(WebSocketFrame.CloseCode.PolicyViolation, "rate limited", false) }
                        return
                    }
                    else -> code == pairingCode
                }
            }
            if (!tokenOk && !codeOk) {
                trySend(JSONObject().put("type", "error").put("error", "bad_code").toString())
                runCatching { close(WebSocketFrame.CloseCode.PolicyViolation, "bad code", false) }
                return
            }
            authorized = true
            deviceName = msg.optString("name").ifEmpty { "Handy" }
            trySend(
                JSONObject()
                    .put("type", "welcome")
                    .put("device", android.os.Build.MODEL)
                    .put("token", pairingToken)
                    .put("status", listener.currentStatus())
                    .put("playlistUrl", listener.currentPlaylistUrl())
                    .put("channels", Channel.listToJson(listener.currentChannels()))
                    .toString()
            )
            notifyClientsChanged(deviceName)
        }

        private fun post(action: () -> Unit) {
            mainHandler.post(action)
        }
    }

    private companion object {
        const val TAG = "ControlServer"
        const val PING_INTERVAL_MS = 8_000L
        const val CODE_ATTEMPT_WINDOW_MS = 60_000L
        const val CODE_ATTEMPT_MAX = 5
        val PING_PAYLOAD = byteArrayOf(0x6e, 0x63)
    }
}
