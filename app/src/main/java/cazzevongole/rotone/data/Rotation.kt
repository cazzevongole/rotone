package cazzevongole.rotone.data

import kotlin.random.Random

/**
 * The rotation maths, kept free of Android types so it can be unit tested on the JVM.
 *
 * [pointer] is the stored "next" position (`RotationState.nextIndex`) and [lastPlayed] is the
 * index that sounded last, so shuffle can avoid repeating it.
 */
object Rotation {

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
}
