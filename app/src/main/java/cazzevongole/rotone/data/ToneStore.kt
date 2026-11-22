package cazzevongole.rotone.data

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
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

    fun load(): RotationState {
        val raw = prefs.getString(KEY_STATE, null) ?: return RotationState()
        return try {
            val json = JSONObject(raw)
            val array = json.optJSONArray("tones") ?: JSONArray()
            val tones = ArrayList<Tone>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val fileName = item.optString("fileName")
                if (id.isEmpty() || fileName.isEmpty()) continue
                val name = item.optString("name").ifEmpty { "Sound" }
                tones.add(Tone(id = id, name = name, fileName = fileName))
            }
            RotationState(
                tones = tones,
                mode = if (json.optString("mode") == "shuffle") PlayMode.SHUFFLE else PlayMode.IN_ORDER,
                nextIndex = if (tones.isEmpty()) 0 else json.optInt("nextIndex", 0).mod(tones.size),
                enabled = json.optBoolean("enabled", true),
            )
        } catch (error: Throwable) {
            RotationState()
        }
    }

    fun save(state: RotationState) {
        val json = JSONObject()
            .put("mode", if (state.mode == PlayMode.SHUFFLE) "shuffle" else "order")
            .put("nextIndex", state.nextIndex)
            .put("enabled", state.enabled)
        val array = JSONArray()
        state.tones.forEach { tone ->
            array.put(
                JSONObject()
                    .put("id", tone.id)
                    .put("name", tone.name)
                    .put("fileName", tone.fileName),
            )
        }
        json.put("tones", array)
        prefs.edit().putString(KEY_STATE, json.toString()).apply()
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
