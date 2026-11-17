package com.example.data.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BillingProductOffer(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val planType: String,
    val productDetails: ProductDetails,
    val offerToken: String? = null
)

data class BillingUiState(
    val isConnected: Boolean = false,
    val connectionMessage: String = "Connecting to Google Play Billing…",
    val availableOffers: List<BillingProductOffer> = emptyList(),
    val isQuerying: Boolean = false,
    val lastBillingMessage: String? = null
)

class BillingManager(
    context: Context,
    private val onVerifiedEntitlement: (planType: String, productId: String, purchaseToken: String) -> Unit
) {
    companion object {
        const val SKU_MONTHLY_AD_FREE = "folionote_premium_monthly"
        const val SKU_LIFETIME_AD_FREE = "folionote_ad_free_lifetime"
    }

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(lastBillingMessage = "Purchase flow cancelled.") }
            }
            else -> {
                _state.update {
                    it.copy(
                        lastBillingMessage = "Play Billing returned code ${billingResult.responseCode}: ${billingResult.debugMessage}"
                    )
                }
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            refreshCatalogAndPurchases()
            return
        }
        _state.update {
            it.copy(isQuerying = true, connectionMessage = "Connecting to Google Play Billing…")
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update {
                        it.copy(
                            isConnected = true,
                            isQuerying = false,
                            connectionMessage = "Play Billing connected"
                        )
                    }
                    refreshCatalogAndPurchases()
                } else {
                    _state.update {
                        it.copy(
                            isConnected = false,
                            isQuerying = false,
                            connectionMessage = "Play Store Billing unavailable (code ${billingResult.responseCode}): ${billingResult.debugMessage.ifBlank { "Configure SKUs in Play Console to test live checkout." }}"
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update {
                    it.copy(
                        isConnected = false,
                        connectionMessage = "Play Billing disconnected. Tap Refresh to reconnect."
                    )
                }
            }
        })
    }

    fun refreshCatalogAndPurchases() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        _state.update { it.copy(isQuerying = true, lastBillingMessage = null) }
        queryProducts(
            productType = BillingClient.ProductType.SUBS,
            productIds = listOf(SKU_MONTHLY_AD_FREE)
        ) { subsOffers ->
            queryProducts(
                productType = BillingClient.ProductType.INAPP,
                productIds = listOf(SKU_LIFETIME_AD_FREE)
            ) { inAppOffers ->
                _state.update {
                    it.copy(
                        isQuerying = false,
                        availableOffers = (subsOffers + inAppOffers)
                    )
                }
            }
        }
        restorePurchases()
    }

    private fun queryProducts(
        productType: String,
        productIds: List<String>,
        onResult: (List<BillingProductOffer>) -> Unit
    ) {
        val productList = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val offers = queryResult.productDetailsList.mapNotNull { details ->
                    if (productType == BillingClient.ProductType.SUBS) {
                        val offer = details.subscriptionOfferDetails?.firstOrNull()
                        val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            ?: return@mapNotNull null
                        BillingProductOffer(
                            productId = details.productId,
                            title = details.name,
                            description = details.description,
                            formattedPrice = price,
                            planType = "MONTHLY",
                            productDetails = details,
                            offerToken = offer.offerToken
                        )
                    } else {
                        val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                            ?: return@mapNotNull null
                        BillingProductOffer(
                            productId = details.productId,
                            title = details.name,
                            description = details.description,
                            formattedPrice = price,
                            planType = "LIFETIME",
                            productDetails = details
                        )
                    }
                }
                onResult(offers)
            } else {
                onResult(emptyList())
            }
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) return
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, offer: BillingProductOffer) {
        if (!billingClient.isReady) {
            _state.update { it.copy(lastBillingMessage = "Play Billing is not connected yet.") }
            return
        }
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
        if (offer.offerToken != null) {
            productParamsBuilder.setOfferToken(offer.offerToken)
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(lastBillingMessage = "Could not launch checkout (${result.responseCode}): ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull().orEmpty()
        val planType = if (productId == SKU_MONTHLY_AD_FREE) "MONTHLY" else "LIFETIME"

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onVerifiedEntitlement(planType, productId, purchase.purchaseToken)
                    _state.update { it.copy(lastBillingMessage = "Ad-free upgrade activated!") }
                }
            }
        } else {
            onVerifiedEntitlement(planType, productId, purchase.purchaseToken)
        }
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
