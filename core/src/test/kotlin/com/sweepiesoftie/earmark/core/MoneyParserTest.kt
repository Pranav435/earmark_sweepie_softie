package com.sweepiesoftie.earmark.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {
    @Test
    fun `reads the amounts payment apps write`() {
        assertEquals("\$12", MoneyParser.find("Aisha requested \$12.00")?.short)
        assertEquals("\$12.00", MoneyParser.find("Aisha requested \$12.00")?.full)
        assertEquals("₹1,250.50", MoneyParser.find("Rohan is requesting Rs. 1,250.50 for cab")?.short)
        assertEquals("₹450", MoneyParser.find("₹450 requested by Meera")?.short)
        assertNull(MoneyParser.find("free at 5?"))
    }

    @Test
    fun `large amounts keep their thousands separator`() {
        assertEquals("\$1,284.50", MoneyParser.find("Bartholomew requested \$1,284.50")?.short)
        assertEquals("\$1,200", MoneyParser.find("Rent share: \$1,200.00 requested")?.short)
    }
}
