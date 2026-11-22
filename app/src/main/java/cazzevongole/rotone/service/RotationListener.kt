package cazzevongole.rotone.service

import android.app.Notification
import android.app.NotificationManager
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cazzevongole.rotone.audio.TonePlayer
import cazzevongole.rotone.data.Rotation
import cazzevongole.rotone.data.RotationState
import cazzevongole.rotone.data.ToneStore

/**
 * Advances the rotation that belongs to the app that posted the notification.
 *
 * Android gives no API to change another app's notification sound, so RoTone plays the tone
 * itself and the other apps' channels are expected to be silent.
 */
class RotationListener : NotificationListenerService() {

    private companion object {
        const val SAME_NOTIFICATION_WINDOW_MS = 2_000L
        const val BURST_WINDOW_MS = 800L
        const val SEEN_LIMIT = 64
    }

    private lateinit var store: ToneStore
    private lateinit var player: TonePlayer
    private val seen = HashMap<String, Long>()
    private var lastPlayedAt = 0L

    override fun onCreate() {
        super.onCreate()
        store = ToneStore(this)
        player = TonePlayer(this)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val notification = statusBarNotification.notification ?: return
        if (statusBarNotification.packageName == packageName) return
        if (!shouldRotate(statusBarNotification, notification)) return

        var current = store.load()
        val remembered = Rotation.recordKnown(
            state = current,
            packageName = statusBarNotification.packageName,
            label = appLabel(statusBarNotification.packageName),
        )
        if (remembered != current) {
            store.save(remembered)
            current = remembered
        }

        if (!isAudible()) return
        playNext(current, statusBarNotification.packageName)
    }

    /**
     * Filters out the notifications that should never make a sound: group summaries,
     * ongoing/foreground-service entries, and repeated updates of the same notification.
     */
    private fun shouldRotate(sbn: StatusBarNotification, notification: Notification): Boolean {
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        if (flags and Notification.FLAG_LOCAL_ONLY != 0) return false

        val now = System.currentTimeMillis()
        val key = sbn.key
        val previous = seen.put(key, now)
        if (previous != null && now - previous < SAME_NOTIFICATION_WINDOW_MS) return false

        if (seen.size > SEEN_LIMIT) {
            val cutoff = now - 60_000L
            seen.values.removeAll { it < cutoff }
        }
        return true
    }

    /** Silent, vibrate and Do Not Disturb modes stay quiet. */
    private fun isAudible(): Boolean {
        val audio = getSystemService(AudioManager::class.java) ?: return true
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val notifications = getSystemService(NotificationManager::class.java) ?: return true
        return runCatching {
            notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(true)
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun playNext(current: RotationState, packageName: String) {
        if (!current.enabled) return
        val rotation = current.rotationFor(packageName) ?: return
        val size = rotation.tones.size
        if (size == 0) return

        val now = System.currentTimeMillis()
        if (now - lastPlayedAt < BURST_WINDOW_MS || player.isPlaying()) return

        val index = Rotation.pick(
            mode = rotation.mode,
            pointer = rotation.nextIndex,
            size = size,
            lastPlayed = rotation.lastPlayedIndex,
        )
        if (!player.play(store.fileFor(rotation.tones[index]))) return

        lastPlayedAt = now
        store.save(Rotation.recordPlayed(current, packageName, index))
    }
}
