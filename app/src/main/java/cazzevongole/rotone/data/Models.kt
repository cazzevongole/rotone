// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone.data

enum class PlayMode { IN_ORDER, SHUFFLE }

data class Tone(
    val id: String,
    val name: String,
    val fileName: String,
)

/** One independent rotation: its sounds, mode, pointer and the index that played last. */
data class ToneList(
    val tones: List<Tone> = emptyList(),
    val mode: PlayMode = PlayMode.IN_ORDER,
    val nextIndex: Int = 0,
    val lastPlayedIndex: Int = -1,
) {
    /** The tone the next notification will play, or null when the list is empty. */
    val nextTone: Tone?
        get() = if (tones.isEmpty()) null else tones.getOrNull(nextIndex.mod(tones.size))
}

/** A rotation that applies to one app instead of the fallback list. */
data class AppRule(
    val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    val rotation: ToneList = ToneList(),
)

/** An app seen posting a notification, offered as a candidate for its own rotation. */
data class KnownPackage(
    val packageName: String,
    val label: String,
)

data class RotationState(
    val enabled: Boolean = true,
    val fallback: ToneList = ToneList(),
    val apps: List<AppRule> = emptyList(),
    val knownPackages: List<KnownPackage> = emptyList(),
) {
    fun ruleFor(packageName: String): AppRule? =
        apps.firstOrNull { it.packageName == packageName }

    /**
     * The rotation that should sound for [packageName], or null when that app must stay silent.
     * An app rule with its own sounds wins; without sounds it follows the fallback list, and a
     * disabled rule silences the app entirely.
     */
    fun rotationFor(packageName: String): ToneList? {
        val rule = ruleFor(packageName) ?: return fallback
        if (!rule.enabled) return null
        return if (rule.rotation.tones.isEmpty()) fallback else rule.rotation
    }
}
