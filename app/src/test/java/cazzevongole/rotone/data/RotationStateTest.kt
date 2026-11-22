// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationStateTest {

    private val tones = listOf(
        Tone("a", "Alpha", "a.mp3"),
        Tone("b", "Beta", "b.ogg"),
    )

    @Test
    fun `empty rotation has no next tone`() {
        // Regression: nextTone used to divide by the list size and crashed on first launch.
        assertNull(ToneList().nextTone)
    }

    @Test
    fun `next tone follows the pointer`() {
        val rotation = ToneList(tones = tones, nextIndex = 1)

        assertEquals("Beta", rotation.nextTone?.name)
        assertEquals("Alpha", rotation.copy(nextIndex = 2).nextTone?.name)
        assertEquals("Beta", rotation.copy(nextIndex = -1).nextTone?.name)
    }

    @Test
    fun `unknown app uses the fallback rotation`() {
        val fallback = ToneList(tones = tones, nextIndex = 1)
        val state = RotationState(fallback = fallback)

        assertEquals(fallback, state.rotationFor("com.example.other"))
    }

    @Test
    fun `app rule overrides the fallback rotation`() {
        val appList = ToneList(tones = listOf(Tone("c", "Gamma", "c.mp3")))
        val state = RotationState(
            fallback = ToneList(tones = tones),
            apps = listOf(AppRule("com.example.chat", "Chat", rotation = appList)),
        )

        assertEquals(appList, state.rotationFor("com.example.chat"))
        assertEquals("Gamma", state.rotationFor("com.example.chat")?.nextTone?.name)
    }

    @Test
    fun `app rule without its own sounds follows the fallback`() {
        val fallback = ToneList(tones = tones)
        val state = RotationState(
            fallback = fallback,
            apps = listOf(AppRule("com.example.chat", "Chat")),
        )

        assertEquals(fallback, state.rotationFor("com.example.chat"))
    }

    @Test
    fun `disabled app rule keeps the app silent`() {
        val state = RotationState(
            fallback = ToneList(tones = tones),
            apps = listOf(
                AppRule("com.example.chat", "Chat", enabled = false, rotation = ToneList(tones)),
            ),
        )

        assertNull(state.rotationFor("com.example.chat"))
        assertEquals(tones, state.rotationFor("com.example.other")?.tones)
    }

    @Test
    fun `rule lookup is exact`() {
        val state = RotationState(apps = listOf(AppRule("com.example.chat", "Chat")))

        assertEquals("Chat", state.ruleFor("com.example.chat")?.label)
        assertNull(state.ruleFor("com.example.chat.extra"))
    }
}
