package com.sweepiesoftie.earmark.core

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** An amount as written in a notification, kept as shown so "$12.00" stays "$12.00". */
public data class Money(val symbol: String, val amount: BigDecimal) {
    /** "$12.00", "$1,284.50" */
    val full: String get() = symbol + DecimalFormat("#,##0.00", SYMBOLS).format(amount)

    /** "$12", or "$12.50" when there are cents. */
    val short: String
        get() = if (amount.stripTrailingZeros().scale() <= 0) symbol + DecimalFormat("#,##0", SYMBOLS).format(amount) else full

    private companion object {
        // Amounts are shown the way the requesting app wrote them, which is overwhelmingly comma-grouped.
        val SYMBOLS: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.US)
    }
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
