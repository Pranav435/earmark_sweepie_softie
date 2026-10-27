package com.sweepiesoftie.earmark.core

/**
 * Why a chat's notification went, when Android only says that its group's summary went (ADR 0026).
 * Removing a summary removes every chat in its group, and those removals carry no reason of their
 * own, so each takes the summary's. A tap is the exception: a tap on a group with several chats
 * opens the app at its chat list, not every chat in it, so those chats count as cleared by their
 * app. Not every chat in it counts as opened. Main thread only, like the listener's callbacks.
 */
public class GroupRemovals {
    /** The chats showing in each group, by notification key. */
    private val showing = HashMap<String, MutableSet<String>>()

    /** The reason each group's chats take when its summary goes. */
    private val inherited = HashMap<String, RemovalReason>()

    public fun posted(key: String, groupKey: String, isSummary: Boolean) {
        if (!isSummary) showing.getOrPut(groupKey) { HashSet() } += key
    }

    /** [bySummary] is true when Android removed it only because its group's summary went. */
    public fun removed(key: String, groupKey: String, isSummary: Boolean, reason: RemovalReason, bySummary: Boolean): RemovalReason {
        if (isSummary) {
            val chats = showing[groupKey].orEmpty().size
            inherited[groupKey] = if (reason == RemovalReason.Click && chats > 1) RemovalReason.AppCancel else reason
            return reason
        }
        showing[groupKey]?.let { it -= key; if (it.isEmpty()) showing -= groupKey }
        return if (bySummary) inherited[groupKey] ?: RemovalReason.Other else reason
    }
}
