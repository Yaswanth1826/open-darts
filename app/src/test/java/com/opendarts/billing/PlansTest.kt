package com.opendarts.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlansTest {
    private fun plan(micros: Long, yearly: Boolean) = Plan(if (yearly) "yearly" else "monthly", "token", "price", micros, yearly)

    @Test fun savingComparesYearlyWithTwelveMonths() {
        assertEquals(48, yearlySaving(listOf(plan(3_990_000, false), plan(24_990_000, true))))
        assertEquals(17, yearlySaving(listOf(plan(1_000_000, false), plan(10_000_000, true))))
    }

    @Test fun noSavingWhenYearlyIsNotCheaperOrAPlanIsMissing() {
        assertNull(yearlySaving(listOf(plan(1_000_000, false), plan(12_000_000, true))))
        assertNull(yearlySaving(listOf(plan(1_000_000, false))))
        assertNull(yearlySaving(listOf(plan(24_990_000, true))))
        assertNull(yearlySaving(emptyList()))
    }

    @Test fun buttonTextNamesThePeriodAndTheSaving() {
        val monthly = Plan("monthly", "t", "\u20AC3.99", 3_990_000, false)
        val yearly = Plan("yearly", "t", "\u20AC24.99", 24_990_000, true)
        assertEquals("Monthly \u00B7 \u20AC3.99", monthly.buttonText(48))
        assertEquals("Yearly \u00B7 \u20AC24.99 \u00B7 save 48%", yearly.buttonText(48))
        assertEquals("Yearly \u00B7 \u20AC24.99", yearly.buttonText(null))
    }
}
