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

    fun closedByPayment(loop: Loop, packageName: String): Boolean =
        loop.kind == LoopKind.Payment && loop.packageName == packageName

    /** Any alarm set for the morning the routine was about closes it. */
    fun closedByAlarm(loop: Loop, nextAlarm: Instant): Boolean =
        loop.kind == LoopKind.Routine && nextAlarm.isBefore(loop.expiresAt.plus(Duration.ofHours(8)))
}
