package com.sweepiesoftie.earmark.core

import java.math.BigDecimal
import java.math.RoundingMode

/** An amount as written in a notification: "$12.00" stays "$12.00", and rupees are grouped the Indian way, "₹1,25,000". */
public data class Money(val symbol: String, val amount: BigDecimal) {
    /** "$12.00", "$1,284.50", "₹1,25,000.00" */
    val full: String get() = symbol + group(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())

    /** "$12", or "$12.50" when there are cents. */
    val short: String
        get() = if (amount.stripTrailingZeros().scale() <= 0) symbol + group(amount.setScale(0, RoundingMode.DOWN).toPlainString()) else full

    private fun group(plain: String): String {
        val whole = plain.substringBefore('.')
        val cents = plain.substringAfter('.', "").let { if (it.isEmpty()) "" else ".$it" }
        if (whole.length <= 3) return whole + cents
        // Lakh and crore: the last three digits, then pairs.
        val size = if (symbol == "₹") 2 else 3
        return whole.dropLast(3).reversed().chunked(size).joinToString(",").reversed() + "," + whole.takeLast(3) + cents
    }
}

/**
 * Finds the first amount in a line of text. A regex rather than ML Kit Entity Extraction,
 * because that model needs a network download (ADR 0002).
 */
internal object MoneyParser {
    /**
     * 1,234.50 or 1,23,450 (grouped, then any decimals), 1.234,50 (European), 12,50 (a decimal comma), or plain 1500.
     * Grouped is tried first, so a comma never cuts a number short.
     */
    private const val NUMBER = """(?:\d{1,3}(?:\.\d{3})+,\d{1,2}|\d{1,3}(?:(?:,\d{3})+|(?:,\d{2})+,\d{3})(?:\.\d{1,2})?|\d+,\d{2}(?![\d,])|\d+(?:\.\d{1,2})?)(?![\d])"""

    /** "2.5 lakh", "₹1.2L", "₹1 crore", "₹4k". */
    private const val SCALE = """(?:\s?(lakhs?|lacs?|l|crores?|cr|k)(?![\p{L}\p{M}]))?"""

    private val symbolFirst = Regex(
        """(?<![\p{L}\p{M}])(₹|Rs\.?|INR|USD|US\$|\$|€|EUR|£|GBP|रु\.?)\s?($NUMBER)$SCALE""",
        RegexOption.IGNORE_CASE,
    )
    private val symbolAfter = Regex(
        """(?<![\d.,])($NUMBER)$SCALE\s?(₹|rs\.?|rupees?|rupaye|rupaiye|rupay|रुपये|रुपए|रुपया|रु\.?|inr|€|eur|euros?|\$|usd|dollars?|bucks)(?![\p{L}\p{M}])""",
        RegexOption.IGNORE_CASE,
    )

    private val scales = mapOf("l" to 100_000, "lakh" to 100_000, "lac" to 100_000, "cr" to 10_000_000, "crore" to 10_000_000, "k" to 1_000)

    fun find(text: String): Money? {
        val first = symbolFirst.find(text)?.let { Found(it.range.first, it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        val after = symbolAfter.find(text)?.let { Found(it.range.first, it.groupValues[3], it.groupValues[1], it.groupValues[2]) }
        val found = listOfNotNull(first, after).minByOrNull { it.at } ?: return null
        val amount = number(found.number) ?: return null
        val scale = found.scale.lowercase().removeSuffix("s").let { scales[it] } ?: 1
        return Money(symbol(found.symbol), amount.multiply(BigDecimal(scale)))
    }

    private class Found(val at: Int, val symbol: String, val number: String, val scale: String)

    private fun symbol(raw: String): String = when (raw.trim().removeSuffix(".").lowercase()) {
        "₹", "rs", "inr", "rupee", "rupees", "rupaye", "rupaiye", "rupay", "रुपये", "रुपए", "रुपया", "रु" -> "₹"
        "$", "usd", "us$", "dollar", "dollars", "bucks" -> "$"
        "€", "eur", "euro", "euros" -> "€"
        "£", "gbp" -> "£"
        else -> raw.trim()
    }

    /** The last separator before one or two digits is the decimal point, whichever mark it is. */
    private fun number(raw: String): BigDecimal? {
        val plain = when {
            ',' in raw && '.' in raw && raw.lastIndexOf(',') > raw.lastIndexOf('.') -> raw.replace(".", "").replace(',', '.')
            ',' in raw && decimalComma.matches(raw) -> raw.replace(',', '.')
            else -> raw.replace(",", "")
        }
        return plain.toBigDecimalOrNull()
    }

    private val decimalComma = Regex("""\d+,\d{2}""")
}
