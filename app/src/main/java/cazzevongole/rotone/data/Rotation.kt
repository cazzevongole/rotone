// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone.data

import kotlin.random.Random

/**
 * The rotation maths, kept free of Android types so it can be unit tested on the JVM.
 *
 * [pointer] is the stored "next" position (`ToneList.nextIndex`) and [lastPlayed] is the index
 * that sounded last, so shuffle can avoid repeating it.
 */
object Rotation {

    const val MAX_KNOWN_PACKAGES = 50

    /** Index of the tone that should play now. */
    fun pick(
        mode: PlayMode,
        pointer: Int,
        size: Int,
        lastPlayed: Int = -1,
        random: Random = Random.Default,
    ): Int {
        if (size <= 0) return 0
        val next = pointer.mod(size)
        if (mode == PlayMode.IN_ORDER || size == 1) return next
        var picked = random.nextInt(size)
        if (picked == lastPlayed && lastPlayed in 0 until size) picked = (picked + 1).mod(size)
        return picked
    }

    /** Pointer to store after [index] has played, so the sequence loops back to the start. */
    fun advance(index: Int, size: Int): Int = if (size <= 0) 0 else (index + 1).mod(size)

    /** Records that [index] of [packageName]'s rotation played. */
    fun recordPlayed(state: RotationState, packageName: String, index: Int): RotationState {
        // A disabled app rule means silence, so nothing is recorded for it.
        if (state.rotationFor(packageName) == null) return state
        val rule = state.ruleFor(packageName)
        val usesAppList = rule != null && rule.enabled && rule.rotation.tones.isNotEmpty()
        val target = if (usesAppList) rule?.rotation ?: state.fallback else state.fallback
        val size = target.tones.size
        if (size == 0 || index !in 0 until size) return state

        val updated = target.copy(
            nextIndex = advance(index, size),
            lastPlayedIndex = index,
        )
        return if (usesAppList) {
            state.copy(
                apps = state.apps.map {
                    if (it.packageName == packageName) it.copy(rotation = updated) else it
                },
            )
        } else {
            state.copy(fallback = updated)
        }
    }

    /** Remembers an app that posted a notification, so it can be given its own rotation. */
    fun recordKnown(state: RotationState, packageName: String, label: String): RotationState {
        if (packageName.isEmpty()) return state
        val entry = KnownPackage(packageName = packageName, label = label.ifEmpty { packageName })
        val existing = state.knownPackages.firstOrNull { it.packageName == packageName }
        if (existing == entry) return state
        val known = if (existing == null) {
            (listOf(entry) + state.knownPackages).take(MAX_KNOWN_PACKAGES)
        } else {
            state.knownPackages.map { if (it.packageName == packageName) entry else it }
        }
        return state.copy(knownPackages = known)
    }
}
