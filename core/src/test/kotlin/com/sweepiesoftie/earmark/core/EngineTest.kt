package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineTest {
    @Test
    fun `an unknown event has no effects`() {
        assertEquals(emptyList<Any>(), Engine().handle(Unit))
    }
}
