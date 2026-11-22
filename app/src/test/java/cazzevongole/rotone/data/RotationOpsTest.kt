// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationOpsTest {

    private val tones = listOf(
        Tone("a", "Alpha", "a.mp3"),
        Tone("b", "Beta", "b.ogg"),
        Tone("c", "Gamma", "c.wav"),
    )

    @Test
    fun `playing advances the fallback pointer`() {
        val state = RotationState(fallback = ToneList(tones = tones))

        val updated = Rotation.recordPlayed(state, "com.example.other", 1)

        assertEquals(2, updated.fallback.nextIndex)
        assertEquals(1, updated.fallback.lastPlayedIndex)
        assertEquals("Gamma", updated.fallback.nextTone?.name)
    }

    @Test
    fun `playing advances the app pointer and leaves the fallback alone`() {
        val appList = ToneList(tones = tones)
        val fallback = ToneList(tones = tones, nextIndex = 1)
        val state = RotationState(
            fallback = fallback,
            apps = listOf(AppRule("com.example.chat", "Chat", rotation = appList)),
        )

        val updated = Rotation.recordPlayed(state, "com.example.chat", 2)

        assertEquals(fallback, updated.fallback)
        val rule = updated.ruleFor("com.example.chat")
        assertEquals(0, rule?.rotation?.nextIndex)
        assertEquals(2, rule?.rotation?.lastPlayedIndex)
    }

    @Test
    fun `the last sound loops back to the first`() {
        val state = RotationState(fallback = ToneList(tones = tones, nextIndex = 2))

        val updated = Rotation.recordPlayed(state, "com.example.other", 2)

        assertEquals(0, updated.fallback.nextIndex)
        assertEquals("Alpha", updated.fallback.nextTone?.name)
    }

    @Test
    fun `an app turned off does not advance anything`() {
        val state = RotationState(
            fallback = ToneList(tones = tones),
            apps = listOf(
                AppRule("com.example.chat", "Chat", enabled = false, rotation = ToneList(tones)),
            ),
        )

        assertEquals(state, Rotation.recordPlayed(state, "com.example.chat", 0))
    }

    @Test
    fun `an app without its own sounds advances the fallback`() {
        val state = RotationState(
            fallback = ToneList(tones = tones),
            apps = listOf(AppRule("com.example.chat", "Chat")),
        )

        val updated = Rotation.recordPlayed(state, "com.example.chat", 1)

        assertEquals(2, updated.fallback.nextIndex)
        assertEquals(ToneList(), updated.ruleFor("com.example.chat")?.rotation ?: ToneList())
    }

    @Test
    fun `an empty rotation is left untouched`() {
        val state = RotationState()

        assertEquals(state, Rotation.recordPlayed(state, "com.example.chat", 0))
    }

    @Test
    fun `known apps are recorded once and update their label`() {
        val state = Rotation.recordKnown(RotationState(), "com.example.chat", "Chat")

        assertEquals(1, state.knownPackages.size)
        assertEquals("Chat", state.knownPackages.first().label)

        val same = Rotation.recordKnown(state, "com.example.chat", "Chat")
        assertEquals(state, same)

        val renamed = Rotation.recordKnown(state, "com.example.chat", "Chat 2")
        assertEquals(1, renamed.knownPackages.size)
        assertEquals("Chat 2", renamed.knownPackages.first().label)
    }

    @Test
    fun `known apps keep the newest first`() {
        var state = RotationState()
        listOf("com.a", "com.b", "com.c").forEach { packageName ->
            state = Rotation.recordKnown(state, packageName, packageName)
        }

        assertEquals(
            listOf("com.c", "com.b", "com.a"),
            state.knownPackages.map { it.packageName },
        )
    }

    @Test
    fun `known apps are capped`() {
        var state = RotationState()
        repeat(Rotation.MAX_KNOWN_PACKAGES + 10) { index ->
            state = Rotation.recordKnown(state, "com.example.$index", "App $index")
        }

        assertEquals(Rotation.MAX_KNOWN_PACKAGES, state.knownPackages.size)
        assertEquals("com.example.${Rotation.MAX_KNOWN_PACKAGES + 9}", state.knownPackages.first().packageName)
    }

    @Test
    fun `blank package names are ignored`() {
        assertEquals(RotationState(), Rotation.recordKnown(RotationState(), "", "Empty"))
    }

    @Test
    fun `missing label falls back to the package name`() {
        val state = Rotation.recordKnown(RotationState(), "com.example.chat", "")

        assertEquals("com.example.chat", state.knownPackages.first().label)
        assertTrue(state.apps.isEmpty())
        assertNull(state.ruleFor("com.example.chat"))
    }
}
