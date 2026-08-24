package com.nodecast.tv

import android.app.Activity
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.nodecast.tv.pairing.Pairing
import com.nodecast.tv.pairing.Qr
import com.nodecast.tv.player.PlayerController
import com.nodecast.tv.playlist.Channel
import com.nodecast.tv.playlist.PlaylistRepository
import com.nodecast.tv.server.ControlServer
import org.json.JSONObject

class MainActivity : Activity(), ControlServer.Listener {

    private lateinit var playerController: PlayerController
    private lateinit var server: ControlServer
    private lateinit var playlist: PlaylistRepository
    private lateinit var audioManager: AudioManager

    private lateinit var pairingScreen: View
    private lateinit var playerScreen: View
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var overlayChannel: TextView
    private lateinit var overlayState: TextView
    private lateinit var overlayLiveDot: View
    private lateinit var overlayLiveLabel: TextView
    private lateinit var overlayProgress: View
    private lateinit var deviceChip: View
    private lateinit var deviceName: TextView
    private lateinit var pairingStatus: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.animate().alpha(0f).setDuration(400).start() }
    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            server.broadcastStatus()
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }
    private var connectedName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        playlist = PlaylistRepository(this)
        playerController = PlayerController(this) { onPlaybackChanged() }

        bindViews()
        setupPairingScreen()

        server = ControlServer(this, this)
        server.startServer()
    }

    private fun bindViews() {
        pairingScreen = findViewById(R.id.pairing_screen)
        playerScreen = findViewById(R.id.player_screen)
        playerView = findViewById(R.id.player_view)
        overlay = findViewById(R.id.overlay)
        overlayChannel = findViewById(R.id.overlay_channel)
        overlayState = findViewById(R.id.overlay_state)
        overlayLiveDot = findViewById(R.id.overlay_live_dot)
        overlayLiveLabel = findViewById(R.id.overlay_live_label)
        overlayProgress = findViewById(R.id.overlay_progress)
        deviceChip = findViewById(R.id.device_chip)
        deviceName = findViewById(R.id.device_name)
        pairingStatus = findViewById(R.id.pairing_status)

        playerView.useController = false
        playerView.player = playerController.player
        overlayProgress.pivotX = 0f
    }

    private fun setupPairingScreen() {
        val address = Pairing.lanAddress()
        val qrImage = findViewById<ImageView>(R.id.qr_image)
        val urlText = findViewById<TextView>(R.id.pairing_url)
        val codeText = findViewById<TextView>(R.id.pairing_code)
        codeText.text = getString(R.string.pairing_code, Pairing.code(this))
        if (address != null) {
            urlText.text = Pairing.remoteUrl(address)
            qrImage.setImageBitmap(
                Qr.encode(Pairing.pairingUrl(this, address), QR_SIZE_PX, Color.parseColor("#101216"))
            )
        } else {
            urlText.text = getString(R.string.no_network)
        }
    }

    // --- playback state → UI + remote ---

    private fun onPlaybackChanged() {
        val showPlayer = playerController.hasMedia
        pairingScreen.visibility = if (showPlayer) View.GONE else View.VISIBLE
        playerScreen.visibility = if (showPlayer) View.VISIBLE else View.GONE
        val active = playerController.state == "playing" || playerController.state == "buffering"
        playerScreen.keepScreenOn = active

        mainHandler.removeCallbacks(ticker)
        if (showPlayer) {
            overlayChannel.text = playerController.channelName
            val live = playerController.player.isCurrentMediaItemLive
            overlayLiveDot.visibility = if (live) View.VISIBLE else View.GONE
            overlayLiveLabel.text = if (live) getString(R.string.live) else ""
            overlayState.text = when (playerController.state) {
                "paused" -> getString(R.string.paused)
                "buffering" -> getString(R.string.buffering)
                "error" -> getString(R.string.playback_error, playerController.errorMessage.orEmpty())
                else -> getString(R.string.playing)
            }
            updateProgress()
            showOverlay(autoHide = playerController.state == "playing")
            if (active) mainHandler.postDelayed(ticker, TICK_INTERVAL_MS)
        }
        onClientsChanged(-1, null)
        server.broadcastStatus()
    }

    private fun updateProgress() {
        val player = playerController.player
        val fraction = if (player.isCurrentMediaItemLive || player.duration <= 0) {
            1f
        } else {
            (player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f)
        }
        overlayProgress.scaleX = fraction
    }

    private fun showOverlay(autoHide: Boolean) {
        mainHandler.removeCallbacks(hideOverlay)
        overlay.animate().alpha(1f).setDuration(200).start()
        if (autoHide) mainHandler.postDelayed(hideOverlay, OVERLAY_HIDE_DELAY_MS)
    }

    // --- ControlServer.Listener (called on main thread) ---

    override fun onPlay(url: String, name: String, group: String) {
        playerController.play(url, name, group)
    }

    override fun onTogglePlay() = playerController.toggle()

    override fun onPause() = playerController.pause()

    override fun onResume() = playerController.resume()

    override fun onStopCast() = playerController.stop()

    override fun onSeek(deltaSeconds: Long) {
        playerController.seekBy(deltaSeconds)
        showOverlay(autoHide = true)
    }

    override fun onVolume(value: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).toInt().coerceIn(0, max), 0)
        server.broadcastStatus()
    }

    override fun onSetPlaylist(url: String) {
        playlist.refresh(url) { result ->
            mainHandler.post {
                result
                    .onSuccess { server.broadcastChannels() }
                    .onFailure { server.broadcastToast(getString(R.string.playlist_error)) }
            }
        }
    }

    override fun onClientsChanged(count: Int, newestName: String?) {
        if (newestName != null) connectedName = newestName
        if (count >= 0) {
            pairingStatus.text = if (count > 0) {
                getString(R.string.paired_with, connectedName.orEmpty())
            } else {
                getString(R.string.ready_to_pair)
            }
        }
        val chipVisible = connectedName != null && playerController.hasMedia
        deviceChip.visibility = if (chipVisible) View.VISIBLE else View.GONE
        deviceName.text = connectedName.orEmpty()
    }

    override fun currentStatus(): JSONObject {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val player = playerController.player
        return JSONObject()
            .put("state", playerController.state)
            .put("channel", playerController.channelName)
            .put("group", playerController.channelGroup)
            .put("live", player.isCurrentMediaItemLive)
            .put("seekable", player.isCurrentMediaItemSeekable)
            .put("position", player.currentPosition.coerceAtLeast(0))
            .put("duration", player.duration.coerceAtLeast(0))
            .put("volume", if (max > 0) vol.toDouble() / max else 0.0)
            .put("error", playerController.errorMessage ?: "")
    }

    override fun currentChannels(): List<Channel> = playlist.channels

    override fun currentPlaylistUrl(): String = playlist.playlistUrl

    // --- TV remote keys (TV-PC / TV-PP) ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (playerController.hasMedia) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    playerController.toggle()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    playerController.resume()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    playerController.pause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    playerController.stop()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    onSeek(-SEEK_STEP_SECONDS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    onSeek(SEEK_STEP_SECONDS)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    playerController.stop()
                    return true
                }
                else -> Unit
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // --- lifecycle ---

    override fun onStop() {
        super.onStop()
        // TV-NP: video must not keep playing when the user leaves the app.
        playerController.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        server.stopServer()
        playerController.release()
    }

    private companion object {
        const val QR_SIZE_PX = 512
        const val OVERLAY_HIDE_DELAY_MS = 4_000L
        const val TICK_INTERVAL_MS = 2_000L
        const val SEEK_STEP_SECONDS = 10L
    }
}
