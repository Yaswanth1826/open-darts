package com.opendarts.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** Google Play subscription for the camera. Every purchase is handed to [account], which has the server check it. */
class PlayBilling(context: Context, private val account: RemoteEntitlements) {
    /** The plans Play offers with their prices (with currency). Empty while unknown or when the product is not set up. */
    var plans by mutableStateOf<List<Plan>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)

    private var details: ProductDetails? = null

    private val client: BillingClient? = runCatching {
        BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }.getOrNull()

    /** Connects to Play, loads the price and restores a purchase made earlier (new phone, reinstall). */
    fun connect() {
        val billing = client ?: return
        if (billing.isReady) { loadPrice(); restore(); return }
        runCatching {
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) { loadPrice(); restore() }
                }

                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private fun loadPrice() {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()
        client?.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, list ->
            details = list.firstOrNull()
            plans = details?.subscriptionOfferDetails.orEmpty()
                .filter { it.offerId == null }
                .mapNotNull { offer ->
                    val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                    if (phase.billingPeriod != "P1M" && phase.billingPeriod != "P1Y") return@mapNotNull null
                    Plan(offer.basePlanId, offer.offerToken, phase.formattedPrice, phase.priceAmountMicros, phase.billingPeriod == "P1Y")
                }
                .sortedBy { it.yearly }
        }
    }

    fun purchase(activity: Activity, plan: Plan) {
        val product = details
        val owner = account.accountHash()
        if (client == null || product == null || owner == null) { message = "Subscriptions are not available right now."; return }
        message = null
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(plan.offerToken).build()))
            .setObfuscatedAccountId(owner)
            .build()
        client.launchBillingFlow(activity, params)
    }

    /** Looks for a subscription the Google account already has and gives it to the server. */
    fun restore(manual: Boolean = false) {
        val billing = client ?: return
        if (!billing.isReady || !account.signedIn) return
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { _, list ->
            if (manual && list.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) message = "No subscription found for this Google account."
            handle(list)
        }
    }

    private fun handle(purchases: List<Purchase>) {
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            account.verifyPurchase(purchase.purchaseToken) { ok -> message = if (ok) "Camera scoring unlocked. Thank you!" else "This subscription belongs to another account." }
        }
    }

    companion object {
        const val PRODUCT = "camera_scoring"
    }
}
