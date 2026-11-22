package cazzevongole.rotone.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON form of [RotationState], kept separate from [ToneStore] so persistence can be tested
 * without an Android runtime.
 *
 * Version 1 stored a single rotation at the top level; version 2 wraps it in [RotationState.fallback]
 * and adds per-app rules. Version 1 data is migrated on read.
 */
object RotationJson {

    private const val CURRENT_VERSION = 2
    private const val SHUFFLE = "shuffle"

    fun encode(state: RotationState): String {
        val apps = JSONArray()
        state.apps.forEach { rule ->
            apps.put(
                JSONObject()
                    .put("packageName", rule.packageName)
                    .put("label", rule.label)
                    .put("enabled", rule.enabled)
                    .put("rotation", encodeList(rule.rotation)),
            )
        }
        val known = JSONArray()
        state.knownPackages.forEach { candidate ->
            known.put(
                JSONObject()
                    .put("packageName", candidate.packageName)
                    .put("label", candidate.label),
            )
        }
        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("enabled", state.enabled)
            .put("fallback", encodeList(state.fallback))
            .put("apps", apps)
            .put("knownPackages", known)
            .toString()
    }

    fun decode(raw: String?): RotationState {
        if (raw.isNullOrBlank()) return RotationState()
        return try {
            val json = JSONObject(raw)
            val enabled = json.optBoolean("enabled", true)
            val fallbackJson = json.optJSONObject("fallback")
            if (fallbackJson == null) {
                // Version 1: one rotation, stored at the top level.
                RotationState(enabled = enabled, fallback = decodeList(json))
            } else {
                RotationState(
                    enabled = enabled,
                    fallback = decodeList(fallbackJson),
                    apps = decodeApps(json.optJSONArray("apps")),
                    knownPackages = decodeKnown(json.optJSONArray("knownPackages")),
                )
            }
        } catch (error: Throwable) {
            RotationState()
        }
    }

    private fun decodeApps(array: JSONArray?): List<AppRule> {
        if (array == null) return emptyList()
        val apps = ArrayList<AppRule>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val packageName = item.optString("packageName")
            if (packageName.isEmpty()) continue
            apps.add(
                AppRule(
                    packageName = packageName,
                    label = item.optString("label").ifEmpty { packageName },
                    enabled = item.optBoolean("enabled", true),
                    rotation = decodeList(item.optJSONObject("rotation")),
                ),
            )
        }
        return apps
    }

    private fun decodeKnown(array: JSONArray?): List<KnownPackage> {
        if (array == null) return emptyList()
        val known = ArrayList<KnownPackage>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val packageName = item.optString("packageName")
            if (packageName.isEmpty()) continue
            known.add(KnownPackage(packageName, item.optString("label").ifEmpty { packageName }))
        }
        return known
    }

    private fun encodeList(list: ToneList): JSONObject {
        val tones = JSONArray()
        list.tones.forEach { tone ->
            tones.put(
                JSONObject()
                    .put("id", tone.id)
                    .put("name", tone.name)
                    .put("fileName", tone.fileName),
            )
        }
        return JSONObject()
            .put("tones", tones)
            .put("mode", if (list.mode == PlayMode.SHUFFLE) SHUFFLE else "order")
            .put("nextIndex", list.nextIndex)
            .put("lastPlayedIndex", list.lastPlayedIndex)
    }

    private fun decodeList(json: JSONObject?): ToneList {
        if (json == null) return ToneList()
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
        return ToneList(
            tones = tones,
            mode = if (json.optString("mode") == SHUFFLE) PlayMode.SHUFFLE else PlayMode.IN_ORDER,
            nextIndex = if (tones.isEmpty()) 0 else json.optInt("nextIndex", 0).mod(tones.size),
            lastPlayedIndex = if (lastPlayed in tones.indices) lastPlayed else -1,
        )
    }
}
