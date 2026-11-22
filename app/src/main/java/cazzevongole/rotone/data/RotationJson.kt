package cazzevongole.rotone.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON form of [RotationState], kept separate from [ToneStore] so persistence can be tested
 * without an Android runtime.
 */
object RotationJson {

    private const val SHUFFLE = "shuffle"

    fun encode(state: RotationState): String {
        val tones = JSONArray()
        state.tones.forEach { tone ->
            tones.put(
                JSONObject()
                    .put("id", tone.id)
                    .put("name", tone.name)
                    .put("fileName", tone.fileName),
            )
        }
        return JSONObject()
            .put("mode", if (state.mode == PlayMode.SHUFFLE) SHUFFLE else "order")
            .put("nextIndex", state.nextIndex)
            .put("lastPlayedIndex", state.lastPlayedIndex)
            .put("enabled", state.enabled)
            .put("tones", tones)
            .toString()
    }

    fun decode(raw: String?): RotationState {
        if (raw.isNullOrBlank()) return RotationState()
        return try {
            val json = JSONObject(raw)
            val array = json.optJSONArray("tones") ?: JSONArray()
            val tones = ArrayList<Tone>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val fileName = item.optString("fileName")
                if (id.isEmpty() || fileName.isEmpty()) continue
                tones.add(
                    Tone(
                        id = id,
                        name = item.optString("name").ifEmpty { "Sound" },
                        fileName = fileName,
                    ),
                )
            }
            val lastPlayed = json.optInt("lastPlayedIndex", -1)
            RotationState(
                tones = tones,
                mode = if (json.optString("mode") == SHUFFLE) PlayMode.SHUFFLE else PlayMode.IN_ORDER,
                nextIndex = if (tones.isEmpty()) 0 else json.optInt("nextIndex", 0).mod(tones.size),
                enabled = json.optBoolean("enabled", true),
                lastPlayedIndex = if (lastPlayed in tones.indices) lastPlayed else -1,
            )
        } catch (error: Throwable) {
            RotationState()
        }
    }
}
