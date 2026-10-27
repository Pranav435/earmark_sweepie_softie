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

    @Test
    fun `reads the whole number, with or without separators`() {
        assertEquals("₹2,400", MoneyParser.find("Rs2400 sent to Ola")?.short)
        assertEquals("\$3,600", MoneyParser.find("\$3600 for the deposit")?.short)
        assertEquals("₹18,000.75", MoneyParser.find("INR 18000.75 debited")?.short)
    }

    @Test
    fun `rupees are grouped the Indian way`() {
        assertEquals("₹3,40,000", MoneyParser.find("₹3,40,000 credited to your account")?.short)
        assertEquals("₹12,00,000", MoneyParser.find("Loan of Rs.12,00,000 approved")?.short)
        assertEquals("₹2,05,610.00", MoneyParser.find("Total Amount Due INR 2,05,610.00")?.full)
    }

    @Test
    fun `lakh, crore and k multiply the number`() {
        assertEquals("₹3,00,000", MoneyParser.find("₹3 lakh ka offer")?.short)
        assertEquals("₹2,50,000", MoneyParser.find("Rs. 2.5 lakh FD")?.short)
        assertEquals("₹1,20,000", MoneyParser.find("₹1.2L package")?.short)
        assertEquals("₹1,00,00,000", MoneyParser.find("₹1 crore cover")?.short)
        assertEquals("₹4,000", MoneyParser.find("₹4k for the trip")?.short)
    }

    @Test
    fun `reads a currency written after the number`() {
        assertEquals("₹700", MoneyParser.find("dinner ke 700 rs de dena")?.short)
        assertEquals("₹300", MoneyParser.find("300 रुपये भेजो")?.short)
        assertEquals("₹250", MoneyParser.find("kal 250 rupaye leke aana")?.short)
        assertEquals("€12.50", MoneyParser.find("Tu me dois 12,50 € pour le taxi")?.short)
        assertEquals("€2,340.10", MoneyParser.find("Summe: 2.340,10 € fällig")?.short)
        assertEquals("\$15", MoneyParser.find("can u spot me 15 bucks")?.short)
    }

    @Test
    fun `the first amount wins, and numbers that aren't money don't count`() {
        assertEquals("₹450", MoneyParser.find("Paid ₹450 to Aisha, balance ₹1,200")?.short)
        assertNull(MoneyParser.find("Your OTP is 448812"))
        assertNull(MoneyParser.find("meet at 5, 2 hrs only"))
        assertNull(MoneyParser.find("Order #40211 out for delivery"))
    }
}
