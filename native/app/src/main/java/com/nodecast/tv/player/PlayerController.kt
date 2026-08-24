package com.nodecast.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * Wraps ExoPlayer plus a MediaSession so the physical TV remote's play/pause
 * keys and Google Assistant work alongside the phone remote (TV-PP/TV-VC of
 * the TV app quality guidelines).
 */
class PlayerController(
    context: Context,
    private val onChanged: () -> Unit,
) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val mediaSession: MediaSession = MediaSession.Builder(context, player).build()

    var channelName: String = ""
        private set
    var channelGroup: String = ""
        private set
    var errorMessage: String? = null
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = onChanged()

            override fun onIsPlayingChanged(isPlaying: Boolean) = onChanged()

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.errorCodeName
                onChanged()
            }
        })
    }

    val state: String
        get() = when {
            errorMessage != null -> "error"
            player.playbackState == Player.STATE_BUFFERING -> "buffering"
            player.playbackState == Player.STATE_READY && player.playWhenReady -> "playing"
            player.playbackState == Player.STATE_READY -> "paused"
            else -> "idle"
        }

    val hasMedia: Boolean
        get() = player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE

    fun play(url: String, name: String, group: String) {
        if (url.isEmpty()) return
        errorMessage = null
        channelName = name.ifEmpty { url }
        channelGroup = group
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(channelName).build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    fun toggle() {
        if (!hasMedia) return
        if (player.isPlaying) player.pause() else resume()
    }

    fun pause() {
        if (player.isPlaying) player.pause()
    }

    fun resume() {
        if (!hasMedia) return
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        channelName = ""
        channelGroup = ""
        errorMessage = null
        onChanged()
    }

    fun seekBy(deltaSeconds: Long) {
        if (!hasMedia || !player.isCurrentMediaItemSeekable) return
        val target = (player.currentPosition + deltaSeconds * 1000)
            .coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
    }

    fun release() {
        mediaSession.release()
        player.release()
    }
}
