package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CloseRulesTest {
    @Test
    fun `how a removed notification ends its loop`() {
        assertEquals(Outcome.Done, CloseRules.onRemoved(RemovalReason.Click))
        assertEquals(Outcome.Done, CloseRules.onRemoved(RemovalReason.AppCancel))
        assertEquals(Outcome.Dismissed, CloseRules.onRemoved(RemovalReason.UserDismiss))
        assertEquals(Outcome.Expired, CloseRules.onRemoved(RemovalReason.Other))
    }
}
