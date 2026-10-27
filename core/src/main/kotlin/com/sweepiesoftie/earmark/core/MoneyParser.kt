package com.sweepiesoftie.earmark.core

import java.math.BigDecimal
import java.math.RoundingMode

/** An amount as written in a notification, kept as shown so "$12.00" stays "$12.00". */
public data class Money(val symbol: String, val amount: BigDecimal) {
    /** "$12.00" */
    val full: String get() = symbol + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** "$12", or "$12.50" when there are cents. */
    val short: String
        get() = if (amount.stripTrailingZeros().scale() <= 0) symbol + amount.setScale(0, RoundingMode.HALF_UP).toPlainString() else full
}

/**
 * Finds the first amount in a line of text. A regex rather than ML Kit Entity Extraction,
 * because that model needs a network download (ADR 0002).
 */
internal object MoneyParser {
    private val symbolFirst = Regex("""(\$|₹|€|£|Rs\.?\s?|INR\s?|USD\s?)\s?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")

    fun find(text: String): Money? {
        val match = symbolFirst.find(text) ?: return null
        val symbol = when (val raw = match.groupValues[1].trim().removeSuffix(".")) {
            "Rs", "INR" -> "₹"
            "USD" -> "$"
            else -> raw
        }
        val amount = match.groupValues[2].replace(",", "").toBigDecimalOrNull() ?: return null
        return Money(symbol, amount)
    }
}
