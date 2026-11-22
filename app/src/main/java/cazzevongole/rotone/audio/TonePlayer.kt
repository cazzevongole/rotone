package cazzevongole.rotone.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Plays one tone at a time on the notification stream, so the notification volume
 * slider (and silent mode) applies to it.
 */
class TonePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    @Synchronized
    fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** Plays [file]; returns false when it cannot be decoded or does not exist. */
    @Synchronized
    fun play(file: File): Boolean {
        stop()
        if (!file.exists() || file.length() == 0L) return false
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener { finished -> releasePlayer(finished) }
            mediaPlayer.setOnErrorListener { failed, _, _ ->
                releasePlayer(failed)
                true
            }
            mediaPlayer.start()
            player = mediaPlayer
            true
        } catch (error: Throwable) {
            runCatching { mediaPlayer.release() }
            false
        }
    }

    @Synchronized
    fun stop() {
        player?.let { releasePlayer(it) }
        player = null
    }

    @Synchronized
    fun release() {
        stop()
    }

    private fun releasePlayer(mediaPlayer: MediaPlayer) {
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.release() }
        if (player === mediaPlayer) player = null
    }
}
