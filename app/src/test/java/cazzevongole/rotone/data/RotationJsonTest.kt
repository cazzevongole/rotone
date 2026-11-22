package cazzevongole.rotone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationJsonTest {

    private val tones = listOf(
        Tone("a", "Alpha", "a.mp3"),
        Tone("b", "Beta", "b.ogg"),
        Tone("c", "Gamma", "c.wav"),
    )

    @Test
    fun `round trip keeps the whole rotation`() {
        val original = RotationState(
            tones = tones,
            mode = PlayMode.SHUFFLE,
            nextIndex = 2,
            enabled = false,
            lastPlayedIndex = 1,
        )

        val restored = RotationJson.decode(RotationJson.encode(original))

        assertEquals(original, restored)
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
    fun `pointer is wrapped back into range`() {
        val restored = RotationJson.decode(
            """{"mode":"order","nextIndex":7,"tones":[
               {"id":"a","name":"Alpha","fileName":"a.mp3"},
               {"id":"b","name":"Beta","fileName":"b.ogg"},
               {"id":"c","name":"Gamma","fileName":"c.wav"}]}""",
        )

        assertEquals(1, restored.nextIndex)
        assertEquals(tones[1].name, restored.nextTone?.name)
    }

    @Test
    fun `negative pointer is wrapped back into range`() {
        val restored = RotationJson.decode(
            """{"nextIndex":-1,"tones":[{"id":"a","name":"A","fileName":"a.mp3"},
               {"id":"b","name":"B","fileName":"b.ogg"}]}""",
        )

        assertEquals(1, restored.nextIndex)
    }

    @Test
    fun `entries without an audio file are dropped`() {
        val restored = RotationJson.decode(
            """{"tones":[{"id":"a","name":"Alpha"},{"id":"b","name":"Beta","fileName":"b.ogg"}]}""",
        )

        assertEquals(1, restored.tones.size)
        assertEquals("b.ogg", restored.tones.first().fileName)
    }

    @Test
    fun `unknown mode falls back to in order`() {
        val restored = RotationJson.decode(
            """{"mode":"reverse","tones":[{"id":"a","name":"A","fileName":"a.mp3"}]}""",
        )

        assertEquals(PlayMode.IN_ORDER, restored.mode)
    }

    @Test
    fun `state is enabled by default`() {
        val restored = RotationJson.decode("""{"tones":[]}""")

        assertTrue(restored.enabled)
        assertFalse(restored.copy(enabled = false).enabled)
    }

    @Test
    fun `empty list always starts at the first sound`() {
        val restored = RotationJson.decode("""{"nextIndex":5,"tones":[]}""")

        assertEquals(0, restored.nextIndex)
        assertEquals(null, restored.nextTone)
    }
}
