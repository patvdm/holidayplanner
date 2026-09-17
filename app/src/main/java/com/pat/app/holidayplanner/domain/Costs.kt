package com.pat.app.holidayplanner.domain

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** One amount, in the smallest unit of its currency, exactly as it is stored. */
data class Cost(val amountMinor: Long, val currency: String)

/** What a set of costs comes to in one currency. */
data class CurrencyTotal(val currency: String, val amountMinor: Long)

/**
 * Adds up money without ever converting it.
 *
 * Currencies are kept apart on purpose: a trip billed partly in euros and partly in pounds has two
 * totals, not one invented number that depends on the day's rate. Everything is integer minor units,
 * so nothing drifts the way floating point money does.
 */
object Costs {

    /** Totals per currency, largest first, so the currency that matters leads. */
    fun byCurrency(costs: List<Cost>): List<CurrencyTotal> = costs
        .filter { it.currency.isNotBlank() }
        .groupBy { it.currency.trim().uppercase(Locale.ENGLISH) }
        .map { (currency, entries) -> CurrencyTotal(currency, entries.sumOf(Cost::amountMinor)) }
        .sortedWith(compareByDescending<CurrencyTotal> { it.amountMinor }.thenBy { it.currency })

    /**
     * Formats an amount in its own currency's convention, which decides where the decimal point
     * goes: most currencies have two places, yen has none, and dinar has three.
     */
    fun format(total: CurrencyTotal, locale: Locale = Locale.getDefault()): String {
        val currency = currencyOf(total.currency)
            ?: return "${total.currency} ${total.amountMinor}"
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(total.amountMinor.toDouble() / factor(digits))
    }

    private fun factor(digits: Int): Double {
        var result = 1.0
        repeat(digits) { result *= DECIMAL_BASE }
        return result
    }

    /**
     * An unrecognised code is not a failure: it is shown as it was typed. Suppressed here alone
     * rather than whitelisting IllegalArgumentException everywhere, which would hide real mistakes.
     */
    @Suppress("SwallowedException")
    private fun currencyOf(code: String): Currency? = try {
        Currency.getInstance(code.trim().uppercase(Locale.ENGLISH))
    } catch (e: IllegalArgumentException) {
        null
    }

    private const val DECIMAL_BASE = 10.0
}
