package cazzevongole.rotone.data

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

enum class PlayMode { IN_ORDER, SHUFFLE }

data class Tone(
    val id: String,
    val name: String,
    val fileName: String,
)

data class RotationState(
    val tones: List<Tone> = emptyList(),
    val mode: PlayMode = PlayMode.IN_ORDER,
    val nextIndex: Int = 0,
    val enabled: Boolean = true,
    val lastPlayedIndex: Int = -1,
) {
    /** The tone the next notification will play, or null when the list is empty. */
    val nextTone: Tone?
        get() = if (tones.isEmpty()) null else tones.getOrNull(nextIndex.mod(tones.size))
}

/**
 * Persists the rotation (order, mode, pointer) and owns the audio files.
 *
 * Picked sounds are copied into app storage on import so playback keeps working after the
 * picker's temporary read permission expires or the original file is moved.
 */
class ToneStore(context: Context) {

    companion object {
        const val KEY_STATE = "rotation_state"
        private const val PREFS = "rotone"
    }

    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val toneDir = File(this.context.filesDir, "tones").apply { mkdirs() }

    val preferences: SharedPreferences get() = prefs

    fun load(): RotationState = RotationJson.decode(prefs.getString(KEY_STATE, null))

    fun save(state: RotationState) {
        prefs.edit().putString(KEY_STATE, RotationJson.encode(state)).apply()
    }

    fun fileFor(tone: Tone): File = File(toneDir, tone.fileName)

    fun deleteFile(tone: Tone) {
        runCatching { fileFor(tone).delete() }
    }

    /** Copies [uri] into the app's tone folder. Returns null when nothing playable was copied. */
    fun import(uri: Uri, fallbackName: String?): Tone? {
        val display = displayName(uri) ?: fallbackName ?: "Sound"
        val fileName = UUID.randomUUID().toString() + extensionFor(uri, display)
        val target = File(toneDir, fileName)
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (error: Throwable) {
            target.delete()
            return null
        }
        if (!target.exists() || target.length() == 0L) {
            target.delete()
            return null
        }
        return Tone(id = UUID.randomUUID().toString(), name = cleanName(display), fileName = fileName)
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
            }
        }
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()
    }

    private fun extensionFor(uri: Uri, display: String): String {
        val fromName = display.substringAfterLast('.', "").lowercase()
        if (fromName.length in 2..5 && fromName.all { it.isLetterOrDigit() }) return ".$fromName"
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val fromMime = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (fromMime.isNullOrBlank()) "" else ".$fromMime"
    }

    private fun cleanName(raw: String): String {
        val base = raw.substringBeforeLast('.').trim().ifEmpty { "Sound" }
        return if (base.length > 64) base.take(61).trimEnd() + "..." else base
    }
}
