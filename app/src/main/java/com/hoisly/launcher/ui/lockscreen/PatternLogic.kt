package com.hoisly.launcher.ui.lockscreen

import java.security.MessageDigest
import kotlin.math.hypot

/** The pattern pad is a 3x3 grid; nodes are numbered 0..8 row by row, like LockDroid's 3x3 matrix. */
internal const val PATTERN_SIZE: Int = 3

/** A pattern needs at least this many dots to be accepted. */
internal const val MIN_PATTERN_NODES: Int = 4

/** Wrong attempts in a row before the pad is blocked for [LOCKOUT_MILLIS]. */
internal const val MAX_FAILED_ATTEMPTS: Int = 5
internal const val LOCKOUT_MILLIS: Long = 30_000L

/** A dot reacts to a finger inside this share of a cell, so neighbouring dots never overlap. */
private const val HIT_RADIUS_SHARE: Float = 0.38f

/** Centre of [node] on a pad that is [side] pixels wide and tall. */
internal fun nodeCenter(node: Int, side: Float): Pair<Float, Float> {
    val cell = side / PATTERN_SIZE
    return Pair((node % PATTERN_SIZE + 0.5f) * cell, (node / PATTERN_SIZE + 0.5f) * cell)
}

/** The dot under ([x], [y]) on a pad that is [side] pixels wide and tall, or null between dots. */
internal fun nodeAt(x: Float, y: Float, side: Float): Int? {
    if (side <= 0f) return null
    val cell = side / PATTERN_SIZE
    for (node in 0 until PATTERN_SIZE * PATTERN_SIZE) {
        val (cx, cy) = nodeCenter(node, side)
        if (hypot(x - cx, y - cy) <= cell * HIT_RADIUS_SHARE) return node
    }
    return null
}

/** A pattern is accepted when it has enough distinct, in-range dots. */
internal fun isValidPattern(nodes: List<Int>): Boolean =
    nodes.size >= MIN_PATTERN_NODES &&
        nodes.distinct().size == nodes.size &&
        nodes.all { it in 0 until PATTERN_SIZE * PATTERN_SIZE }

/** Stored form: `salt:hash`, both hex. The pattern itself is never stored. */
internal fun hashPattern(nodes: List<Int>, salt: ByteArray): String =
    "${salt.toHex()}:${digest(salt, nodes).toHex()}"

/** True when [nodes] is the pattern that produced [stored]; an empty or malformed [stored] never matches. */
internal fun verifyPattern(stored: String, nodes: List<Int>): Boolean {
    val parts = stored.split(':')
    if (parts.size != 2) return false
    val salt = parts[0].fromHex() ?: return false
    val expected = parts[1].fromHex() ?: return false
    return MessageDigest.isEqual(expected, digest(salt, nodes))
}

/** Seconds of lockout left, or 0 when the pad can be used. */
internal fun lockoutRemaining(failedAttempts: Int, lastFailureMs: Long, nowMs: Long): Long {
    if (failedAttempts < MAX_FAILED_ATTEMPTS) return 0L
    return (lastFailureMs + LOCKOUT_MILLIS - nowMs).coerceIn(0L, LOCKOUT_MILLIS)
}

private fun digest(salt: ByteArray, nodes: List<Int>): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(salt + nodes.joinToString("").toByteArray(Charsets.UTF_8))

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.fromHex(): ByteArray? {
    if (isEmpty() || length % 2 != 0) return null
    return try {
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    } catch (_: NumberFormatException) {
        null
    }
}
