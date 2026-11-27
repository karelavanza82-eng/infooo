package com.hoisly.launcher.data

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBadgesTest {
    private fun note(
        key: String,
        packageName: String = "org.telegram",
        profile: String = "0",
        number: Int = 0,
        flags: Int = 0,
        media: Boolean = false,
        canShowBadge: Boolean = true,
        groupKey: String? = null,
    ) = BadgeNotification(key, packageName, profile, number, flags, media, canShowBadge, groupKey)

    private val telegram = badgeKey("org.telegram", "0")

    @Test
    fun countsOnePerNotification() {
        val counts = countBadges(listOf(note("a"), note("b")), OWN)
        assertEquals(mapOf(telegram to 2L), counts)
    }

    @Test
    fun numberReplacesOne() {
        val counts = countBadges(listOf(note("a", number = 5), note("b")), OWN)
        assertEquals(6L, counts[telegram])
    }

    @Test
    fun summaryWithMembersIsNotCounted() {
        val counts = countBadges(listOf(
            note("summary", flags = Notification.FLAG_GROUP_SUMMARY, groupKey = "g"),
            note("chat1", groupKey = "g"),
            note("chat2", groupKey = "g"),
        ), OWN)
        assertEquals(2L, counts[telegram])
    }

    @Test
    fun loneSummaryCountsLikeTheShadeShowsIt() {
        val counts = countBadges(listOf(note("summary", flags = Notification.FLAG_GROUP_SUMMARY, groupKey = "g")), OWN)
        assertEquals(1L, counts[telegram])
    }

    @Test
    fun summaryOfOngoingMembersIsNotCounted() {
        val counts = countBadges(listOf(
            note("summary", flags = Notification.FLAG_GROUP_SUMMARY, groupKey = "g"),
            note("upload", flags = Notification.FLAG_ONGOING_EVENT, groupKey = "g"),
        ), OWN)
        assertTrue(counts.isEmpty())
    }

    @Test
    fun ongoingForegroundAndMediaAreNotCounted() {
        val counts = countBadges(listOf(
            note("ongoing", flags = Notification.FLAG_ONGOING_EVENT),
            note("service", flags = Notification.FLAG_FOREGROUND_SERVICE),
            note("player", media = true),
        ), OWN)
        assertTrue(counts.isEmpty())
    }

    @Test
    fun channelWithoutBadgesIsNotCounted() {
        val counts = countBadges(listOf(note("a", canShowBadge = false), note("b")), OWN)
        assertEquals(1L, counts[telegram])
    }

    @Test
    fun ownNotificationsAreNotCounted() {
        assertTrue(countBadges(listOf(note("a", packageName = OWN)), OWN).isEmpty())
    }

    @Test
    fun sameKeyCountsOnceWithItsLatestState() {
        val counts = countBadges(listOf(note("a", number = 3), note("a", number = 4)), OWN)
        assertEquals(4L, counts[telegram])
    }

    @Test
    fun profilesStayApart() {
        val counts = countBadges(listOf(note("a", profile = "0"), note("b", profile = "10"), note("c", profile = "10")), OWN)
        assertEquals(1L, counts[telegram])
        assertEquals(2L, counts[badgeKey("org.telegram", "10")])
    }

    @Test
    fun folderSumsEachAppOnce() {
        val counts = mapOf(telegram to 3L, badgeKey("com.mail", "0") to 2L, badgeKey("org.telegram", "10") to 7L)
        // Two launcher activities of Telegram share one count; the work profile app is not in the folder.
        assertEquals(5L, badgeSum(listOf(telegram, telegram, badgeKey("com.mail", "0"), badgeKey("com.none", "0")), counts))
        assertEquals(0L, badgeSum(listOf(telegram), emptyMap()))
    }

    @Test
    fun hugeNumbersDoNotOverflow() {
        val counts = countBadges((1..10).map { note("n$it", number = Int.MAX_VALUE) }, OWN)
        assertEquals(21_474_836_470L, counts[telegram])
        assertEquals("21.474.836.470", badgeText(counts.getValue(telegram)))
    }

    @Test
    fun textUsesIndonesianGroupingWithoutCapping() {
        assertEquals("1", badgeText(1))
        assertEquals("999", badgeText(999))
        assertEquals("1.000", badgeText(1000))
        assertEquals("10.000", badgeText(10_000))
        assertEquals("100.000", badgeText(100_000))
    }

    private companion object {
        const val OWN = "com.hoisly.launcher"
    }
}
