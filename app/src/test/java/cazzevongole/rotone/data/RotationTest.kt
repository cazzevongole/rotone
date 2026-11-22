package cazzevongole.rotone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RotationTest {

    @Test
    fun `in order walks the list and loops back to the first`() {
        assertEquals(0, Rotation.pick(PlayMode.IN_ORDER, pointer = 0, size = 3))
        assertEquals(1, Rotation.pick(PlayMode.IN_ORDER, pointer = 1, size = 3))
        assertEquals(2, Rotation.pick(PlayMode.IN_ORDER, pointer = 2, size = 3))
        assertEquals(0, Rotation.pick(PlayMode.IN_ORDER, pointer = 3, size = 3))
    }

    @Test
    fun `in order tolerates a stale pointer`() {
        assertEquals(2, Rotation.pick(PlayMode.IN_ORDER, pointer = -1, size = 3))
        assertEquals(1, Rotation.pick(PlayMode.IN_ORDER, pointer = 7, size = 3))
    }

    @Test
    fun `advance loops back to the start`() {
        assertEquals(1, Rotation.advance(0, 3))
        assertEquals(2, Rotation.advance(1, 3))
        assertEquals(0, Rotation.advance(2, 3))
    }

    @Test
    fun `empty rotation does not divide by zero`() {
        assertEquals(0, Rotation.pick(PlayMode.IN_ORDER, pointer = 0, size = 0))
        assertEquals(0, Rotation.pick(PlayMode.SHUFFLE, pointer = 0, size = 0))
        assertEquals(0, Rotation.advance(0, 0))
    }

    @Test
    fun `single sound shuffle stays on that sound`() {
        repeat(20) {
            assertEquals(0, Rotation.pick(PlayMode.SHUFFLE, pointer = 0, size = 1))
        }
    }

    @Test
    fun `shuffle never repeats the sound that just played`() {
        val random = Random(7)
        repeat(500) {
            val picked = Rotation.pick(
                mode = PlayMode.SHUFFLE,
                pointer = 3,
                size = 4,
                lastPlayed = 2,
                random = random,
            )
            assertTrue("out of range: $picked", picked in 0 until 4)
            assertTrue("repeated the last sound: $picked", picked != 2)
        }
    }

    @Test
    fun `shuffle can reach every sound`() {
        val random = Random(11)
        val seen = mutableSetOf<Int>()
        repeat(300) {
            seen += Rotation.pick(
                mode = PlayMode.SHUFFLE,
                pointer = 0,
                size = 5,
                lastPlayed = -1,
                random = random,
            )
        }
        assertEquals(setOf(0, 1, 2, 3, 4), seen)
    }

    @Test
    fun `shuffle ignores an out of range last played index`() {
        val random = Random(3)
        val seen = mutableSetOf<Int>()
        repeat(200) {
            seen += Rotation.pick(
                mode = PlayMode.SHUFFLE,
                pointer = 0,
                size = 3,
                lastPlayed = 99,
                random = random,
            )
        }
        assertEquals(setOf(0, 1, 2), seen)
    }
}
