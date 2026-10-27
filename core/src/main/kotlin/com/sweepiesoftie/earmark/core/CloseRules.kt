package com.sweepiesoftie.earmark.core

import java.time.Duration
import java.time.Instant

/** Which evidence closes which loop, and with what outcome. Loops close on evidence, never on a guess. */
internal object CloseRules {
    /** Tapped or cleared by its app means dealt with; swiped away by you means dismissed. */
    fun onRemoved(reason: RemovalReason): Outcome = when (reason) {
        RemovalReason.Click, RemovalReason.AppCancel -> Outcome.Done
        RemovalReason.UserDismiss -> Outcome.Dismissed
        RemovalReason.Other -> Outcome.Expired
    }

    fun closedByBooking(loop: Loop, packageName: String): Boolean =
        (loop.kind == LoopKind.Flow || loop.kind == LoopKind.LeaveBy) && loop.packageName == packageName

    /**
     * A payment in the loop's app. A bill closes only on a payment of its amount: a bank's text that you sent a
     * friend ₹450 doesn't pay the Jio bill in the same Messages app (ADR 0031). A request closes on its amount too,
     * or on any payment there when either side doesn't say how much.
     */
    fun closedByPayment(loop: Loop, packageName: String, amount: Money? = null): Boolean {
        if (loop.kind != LoopKind.Payment || loop.packageName != packageName) return false
        val same = amount != null && loop.amount != null && amount.symbol == loop.amount.symbol && amount.amount.compareTo(loop.amount.amount) == 0
        return same || (!loop.bill && (amount == null || loop.amount == null))
    }

    /** Any alarm set for the morning the routine was about closes it. */
    fun closedByAlarm(loop: Loop, nextAlarm: Instant): Boolean =
        loop.kind == LoopKind.Routine && nextAlarm.isBefore(loop.expiresAt.plus(Duration.ofHours(8)))
}
