package com.pat.app.holidayplanner.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money stays in the currency it was spent in. Your plan rules out conversion, so a trip with two
 * currencies has two totals and neither is invented.
 */
class CostsTest {

    @Test
    fun `amounts in one currency add up`() {
        val totals = Costs.byCurrency(
            listOf(Cost(24_950, "EUR"), Cost(1_250, "EUR"), Cost(80, "EUR")),
        )

        assertEquals(listOf(CurrencyTotal("EUR", 26_280)), totals)
    }

    @Test
    fun `currencies are kept apart rather than converted`() {
        val totals = Costs.byCurrency(
            listOf(Cost(10_000, "EUR"), Cost(5_000, "GBP"), Cost(2_000, "EUR")),
        )

        assertEquals(
            listOf(CurrencyTotal("EUR", 12_000), CurrencyTotal("GBP", 5_000)),
            totals,
        )
    }

    @Test
    fun `the biggest total leads`() {
        val totals = Costs.byCurrency(listOf(Cost(100, "EUR"), Cost(9_000, "JPY")))

        assertEquals("JPY", totals.first().currency)
    }

    @Test
    fun `equal totals fall back to the currency code so the order never wobbles`() {
        val totals = Costs.byCurrency(listOf(Cost(500, "USD"), Cost(500, "EUR")))

        assertEquals(listOf("EUR", "USD"), totals.map { it.currency })
    }

    @Test
    fun `a currency written in lower case is the same currency`() {
        val totals = Costs.byCurrency(listOf(Cost(100, "eur"), Cost(100, " EUR ")))

        assertEquals(listOf(CurrencyTotal("EUR", 200)), totals)
    }

    @Test
    fun `costs with no currency are left out rather than lumped together`() {
        val totals = Costs.byCurrency(listOf(Cost(100, ""), Cost(250, "EUR")))

        assertEquals(listOf(CurrencyTotal("EUR", 250)), totals)
    }

    @Test
    fun `nothing spent totals nothing`() {
        assertEquals(emptyList<CurrencyTotal>(), Costs.byCurrency(emptyList()))
    }

    @Test
    fun `a refund can make a total negative`() {
        val totals = Costs.byCurrency(listOf(Cost(5_000, "EUR"), Cost(-2_000, "EUR")))

        assertEquals(listOf(CurrencyTotal("EUR", 3_000)), totals)
    }

    @Test
    fun `minor units become major ones at the currency's own scale`() {
        assertTrue(Costs.format(CurrencyTotal("EUR", 24_950), Locale.GERMANY).contains("249,50"))
        assertTrue(Costs.format(CurrencyTotal("GBP", 1_05), Locale.UK).contains("1.05"))
    }

    @Test
    fun `a currency with no decimal places is not given any`() {
        val formatted = Costs.format(CurrencyTotal("JPY", 9_000), Locale.JAPAN)

        assertTrue("yen has no minor unit, so 9000 stays 9,000: $formatted", formatted.contains("9,000"))
    }

    @Test
    fun `an unrecognised code is shown as typed rather than dropped`() {
        assertEquals("XYZ 1234", Costs.format(CurrencyTotal("XYZ", 1234), Locale.UK))
    }
}
