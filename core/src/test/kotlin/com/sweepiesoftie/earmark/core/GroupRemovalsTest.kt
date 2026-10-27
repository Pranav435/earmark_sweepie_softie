package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupRemovalsTest {
    private val group = "0|com.whatsapp|g:group"

    private fun GroupRemovals.chats(vararg keys: String) = keys.forEach { posted(it, group, isSummary = false) }

    @Test
    fun `tapping a group with several chats opens the app, so each chat counts as cleared by it, not tapped`() {
        val groups = GroupRemovals()
        groups.chats("avani", "rahul", "priya")
        groups.posted("summary", group, isSummary = true)

        assertEquals(RemovalReason.Click, groups.removed("summary", group, isSummary = true, reason = RemovalReason.Click, bySummary = false))
        listOf("avani", "rahul", "priya").forEach {
            assertEquals(it, RemovalReason.AppCancel, groups.removed(it, group, isSummary = false, reason = RemovalReason.Other, bySummary = true))
        }
    }

    @Test
    fun `tapping a group with one chat is tapping that chat`() {
        val groups = GroupRemovals()
        groups.chats("avani")
        groups.removed("summary", group, isSummary = true, reason = RemovalReason.Click, bySummary = false)
        assertEquals(RemovalReason.Click, groups.removed("avani", group, isSummary = false, reason = RemovalReason.Other, bySummary = true))
    }

    @Test
    fun `an app clearing its group, or you swiping it away, passes that reason to every chat in it`() {
        val groups = GroupRemovals()
        groups.chats("avani", "rahul")
        groups.removed("summary", group, isSummary = true, reason = RemovalReason.AppCancel, bySummary = false)
        assertEquals(RemovalReason.AppCancel, groups.removed("avani", group, isSummary = false, reason = RemovalReason.Other, bySummary = true))

        groups.chats("avani", "rahul")
        groups.removed("summary", group, isSummary = true, reason = RemovalReason.UserDismiss, bySummary = false)
        assertEquals(RemovalReason.UserDismiss, groups.removed("rahul", group, isSummary = false, reason = RemovalReason.Other, bySummary = true))
    }

    @Test
    fun `a chat removed on its own keeps its own reason, and the group no longer counts it`() {
        val groups = GroupRemovals()
        groups.chats("avani", "rahul")
        assertEquals(RemovalReason.Click, groups.removed("avani", group, isSummary = false, reason = RemovalReason.Click, bySummary = false))
        // Only Rahul's chat is left, so tapping the group now is tapping his chat.
        groups.removed("summary", group, isSummary = true, reason = RemovalReason.Click, bySummary = false)
        assertEquals(RemovalReason.Click, groups.removed("rahul", group, isSummary = false, reason = RemovalReason.Other, bySummary = true))
    }

    @Test
    fun `an update to a chat that's already showing is still one chat`() {
        val groups = GroupRemovals()
        groups.chats("avani", "avani")
        groups.removed("summary", group, isSummary = true, reason = RemovalReason.Click, bySummary = false)
        assertEquals(RemovalReason.Click, groups.removed("avani", group, isSummary = false, reason = RemovalReason.Other, bySummary = true))
    }

    @Test
    fun `a chat removed with its summary before Earmark saw the summary go isn't counted as anything`() {
        assertEquals(RemovalReason.Other, GroupRemovals().removed("avani", group, isSummary = false, reason = RemovalReason.Other, bySummary = true))
    }
}
