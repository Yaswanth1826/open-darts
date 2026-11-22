package com.opendarts.billing

import kotlin.math.roundToInt

/** One way to pay for the camera: a base plan of the Play subscription. Prices come from Play, never from the app. */
data class Plan(val basePlanId: String, val offerToken: String, val price: String, val micros: Long, val yearly: Boolean)

/** How much cheaper the yearly plan is than twelve months of the monthly one, in whole percent, or null when there is no saving. */
fun yearlySaving(plans: List<Plan>): Int? {
    val monthly = plans.firstOrNull { !it.yearly }?.micros ?: return null
    val yearly = plans.firstOrNull { it.yearly }?.micros ?: return null
    if (monthly <= 0 || yearly <= 0) return null
    return ((1.0 - yearly / (12.0 * monthly)) * 100).roundToInt().takeIf { it > 0 }
}

fun Plan.buttonText(saving: Int?): String =
    if (yearly) "Yearly \u00B7 $price" + (saving?.let { " \u00B7 save $it%" } ?: "") else "Monthly \u00B7 $price"
