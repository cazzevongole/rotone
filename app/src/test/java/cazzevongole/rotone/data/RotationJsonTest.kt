// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationJsonTest {

    private val tones = listOf(
        Tone("a", "Alpha", "a.mp3"),
        Tone("b", "Beta", "b.ogg"),
        Tone("c", "Gamma", "c.wav"),
    )

    @Test
    fun `round trip keeps the fallback rotation`() {
        val original = RotationState(
            enabled = false,
            fallback = ToneList(
                tones = tones,
                mode = PlayMode.SHUFFLE,
                nextIndex = 2,
                lastPlayedIndex = 1,
            ),
        )

        assertEquals(original, RotationJson.decode(RotationJson.encode(original)))
    }

    @Test
    fun `round trip keeps per-app rules and candidates`() {
        val original = RotationState(
            fallback = ToneList(tones = tones),
            apps = listOf(
                AppRule(
                    packageName = "com.example.chat",
                    label = "Chat",
                    enabled = false,
                    rotation = ToneList(tones = tones.take(1), nextIndex = 0, lastPlayedIndex = 0),
                ),
            ),
            knownPackages = listOf(
                KnownPackage("com.example.chat", "Chat"),
                KnownPackage("com.example.mail", "Mail"),
            ),
        )

        val restored = RotationJson.decode(RotationJson.encode(original))

        assertEquals(original, restored)
        assertEquals(1, restored.apps.size)
        assertEquals("com.example.chat", restored.apps.first().packageName)
        assertFalse(restored.apps.first().enabled)
        assertEquals(2, restored.knownPackages.size)
    }

    @Test
    fun `version 1 state is migrated to the fallback rotation`() {
        val legacy = """
            {"mode":"shuffle","nextIndex":2,"enabled":true,
             "tones":[{"id":"a","name":"Alpha","fileName":"a.mp3"},
                      {"id":"b","name":"Beta","fileName":"b.ogg"},
                      {"id":"c","name":"Gamma","fileName":"c.wav"}]}
        """.trimIndent()

        val restored = RotationJson.decode(legacy)

        assertEquals(tones, restored.fallback.tones)
        assertEquals(PlayMode.SHUFFLE, restored.fallback.mode)
        assertEquals(2, restored.fallback.nextIndex)
        assertEquals("Gamma", restored.fallback.nextTone?.name)
        assertTrue(restored.apps.isEmpty())
        assertTrue(restored.enabled)
    }

    @Test
    fun `version 1 pointer past the end is wrapped`() {
        val legacy = """
            {"nextIndex":7,"tones":[{"id":"a","name":"A","fileName":"a.mp3"},
                                    {"id":"b","name":"B","fileName":"b.ogg"},
                                    {"id":"c","name":"C","fileName":"c.mp3"}]}
        """.trimIndent()

        assertEquals(1, RotationJson.decode(legacy).fallback.nextIndex)
    }

    @Test
    fun `missing state falls back to empty defaults`() {
        assertEquals(RotationState(), RotationJson.decode(null))
        assertEquals(RotationState(), RotationJson.decode(""))
        assertEquals(RotationState(), RotationJson.decode("   "))
    }

    @Test
    fun `corrupt json falls back to empty defaults`() {
        assertEquals(RotationState(), RotationJson.decode("{not json"))
        assertEquals(RotationState(), RotationJson.decode("[]"))
    }

    @Test
    fun `entries without an audio file are dropped`() {
        val restored = RotationJson.decode(
            """{"fallback":{"tones":[{"id":"a","name":"Alpha"},
               {"id":"b","name":"Beta","fileName":"b.ogg"}]}}""",
        )

        assertEquals(1, restored.fallback.tones.size)
        assertEquals("b.ogg", restored.fallback.tones.first().fileName)
    }

    @Test
    fun `unknown mode falls back to in order`() {
        val restored = RotationJson.decode(
            """{"fallback":{"mode":"reverse","tones":[{"id":"a","name":"A","fileName":"a.mp3"}]}}""",
        )

        assertEquals(PlayMode.IN_ORDER, restored.fallback.mode)
    }

    @Test
    fun `app rules without a package are dropped`() {
        val restored = RotationJson.decode(
            """{"fallback":{"tones":[]},
               "apps":[{"label":"Nameless"},{"packageName":"com.example.chat","label":"Chat"}]}""",
        )

        assertEquals(1, restored.apps.size)
        assertEquals("com.example.chat", restored.apps.first().packageName)
    }

    @Test
    fun `empty list always starts at the first sound`() {
        val restored = RotationJson.decode("""{"fallback":{"nextIndex":5,"tones":[]}}""")

        assertEquals(0, restored.fallback.nextIndex)
        assertNull(restored.fallback.nextTone)
    }
}
