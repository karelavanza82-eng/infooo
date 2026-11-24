package com.hoisly.launcher.ui.lockscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternLogicTest {
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun dotsAreHitInTheirCellsAndMissedBetween() {
        assertEquals(0, nodeAt(50f, 50f, 300f))
        assertEquals(4, nodeAt(150f, 150f, 300f))
        assertEquals(8, nodeAt(250f, 250f, 300f))
        assertNull(nodeAt(100f, 100f, 300f))
        assertNull(nodeAt(10f, 10f, 0f))
    }

    @Test
    fun patternNeedsFourDistinctDots() {
        assertFalse(isValidPattern(listOf(0, 1, 2)))
        assertFalse(isValidPattern(listOf(0, 1, 2, 2)))
        assertFalse(isValidPattern(listOf(0, 1, 2, 9)))
        assertTrue(isValidPattern(listOf(0, 1, 2, 5)))
    }

    @Test
    fun hashedPatternVerifiesOnlyTheSameOrder() {
        val stored = hashPattern(listOf(0, 1, 2, 5), salt)
        assertTrue(verifyPattern(stored, listOf(0, 1, 2, 5)))
        assertFalse(verifyPattern(stored, listOf(5, 2, 1, 0)))
        assertFalse(verifyPattern(stored, listOf(0, 1, 2)))
        assertFalse("0125" in stored)
    }

    @Test
    fun differentSaltsGiveDifferentHashes() {
        val pattern = listOf(3, 4, 5, 8)
        assertNotEquals(hashPattern(pattern, salt), hashPattern(pattern, ByteArray(16) { 9 }))
    }

    @Test
    fun malformedStoredValueNeverMatches() {
        assertFalse(verifyPattern("", listOf(0, 1, 2, 5)))
        assertFalse(verifyPattern("zz:zz", listOf(0, 1, 2, 5)))
        assertFalse(verifyPattern("00:11:22", listOf(0, 1, 2, 5)))
    }

    @Test
    fun lockoutStartsAfterTooManyFailuresAndEnds() {
        assertEquals(0L, lockoutRemaining(MAX_FAILED_ATTEMPTS - 1, 1_000L, 1_000L))
        assertEquals(LOCKOUT_MILLIS, lockoutRemaining(MAX_FAILED_ATTEMPTS, 1_000L, 1_000L))
        assertEquals(10_000L, lockoutRemaining(MAX_FAILED_ATTEMPTS, 1_000L, 1_000L + LOCKOUT_MILLIS - 10_000L))
        assertEquals(0L, lockoutRemaining(MAX_FAILED_ATTEMPTS, 1_000L, 1_000L + LOCKOUT_MILLIS + 1L))
    }
}
