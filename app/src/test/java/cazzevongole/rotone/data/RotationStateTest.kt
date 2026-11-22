package cazzevongole.rotone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationStateTest {

    @Test
    fun `empty rotation has no next tone`() {
        // Regression: nextTone used to divide by the list size and crashed on first launch.
        assertNull(RotationState().nextTone)
    }

    @Test
    fun `next tone follows the pointer`() {
        val state = RotationState(
            tones = listOf(
                Tone("a", "Alpha", "a.mp3"),
                Tone("b", "Beta", "b.ogg"),
            ),
            nextIndex = 1,
        )

        assertEquals("Beta", state.nextTone?.name)
        assertEquals("Alpha", state.copy(nextIndex = 2).nextTone?.name)
        assertEquals("Beta", state.copy(nextIndex = -1).nextTone?.name)
    }
}
