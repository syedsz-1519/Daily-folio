package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.EntitlementRepository
import com.example.data.model.UiState
import com.example.data.model.UserEntitlement
import com.example.data.monetization.BillingManager
import com.example.data.monetization.BillingProductOffer
import com.example.data.monetization.BillingUiState
import com.example.data.security.VaultPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonetizationViewModel(
    appContext: Context,
    private val entitlementRepository: EntitlementRepository,
    private val vaultPreferencesRepository: VaultPreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    private val billingManager = BillingManager(appContext) { planType, productId, purchaseToken ->
        viewModelScope.launch {
            vaultPreferencesRepository.setCachedAdFree(true)
            entitlementRepository.syncEntitlement(
                userId = currentUserId,
                adFree = true,
                planType = planType,
                productId = productId,
                purchaseToken = purchaseToken
            )
        }
    }

    val billingState: StateFlow<BillingUiState> = billingManager.state

    val entitlementState: StateFlow<UiState<UserEntitlement>> =
        entitlementRepository.observeEntitlement(currentUserId)
            .onEach { entitlement ->
                vaultPreferencesRepository.setCachedAdFree(entitlement.adFree)
            }
            .map<UserEntitlement, UiState<UserEntitlement>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing entitlement", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load entitlement"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    init {
        billingManager.startConnection()
    }

    fun refreshBilling() {
        billingManager.refreshCatalogAndPurchases()
    }

    fun restorePurchases() {
        billingManager.restorePurchases()
    }

    fun launchPurchase(activity: Activity, offer: BillingProductOffer) {
        billingManager.launchPurchaseFlow(activity, offer)
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.destroy()
    }

    private companion object {
        const val TAG = "MonetizationVM"
    }
}
