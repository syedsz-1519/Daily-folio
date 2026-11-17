package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.AuthDiagnostics
import com.example.data.model.UiState
import com.example.data.model.UserEntitlement
import com.example.data.monetization.BillingProductOffer
import com.example.data.monetization.BillingUiState
import com.example.data.security.BiometricAuthHelper
import com.example.data.security.VaultSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAndAuthLabScreen(
    authDiagnostics: AuthDiagnostics,
    vaultSettings: VaultSettings,
    entitlementState: UiState<UserEntitlement>,
    billingState: BillingUiState,
    adMobStatus: String,
    onSignOutClick: () -> Unit,
    onConfigurePin: (pin: String, enableBiometric: Boolean) -> Unit,
    onToggleVaultLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleLockOnBackground: (Boolean) -> Unit,
    onRemovePin: () -> Unit,
    onLockVaultNow: () -> Unit,
    onTestInterstitialAd: () -> Unit,
    onOpenUpgradeSheet: () -> Unit,
    onRestorePurchases: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canUseBiometricHardware = remember(context) {
        BiometricAuthHelper.canAuthenticate(context)
    }
    var showPinDialog by remember { mutableStateOf(false) }

    val entitlement = (entitlementState as? UiState.Success)?.data
    val isAdFree = entitlement?.adFree ?: vaultSettings.cachedAdFree

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "SECURITY · AUTH LAB · BILLING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Vault & Account",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            // 1. Auth Prototype Testing Lab
            item {
                AuthPrototypeLabCard(
                    diagnostics = authDiagnostics,
                    onSignOutClick = onSignOutClick
                )
            }

            // 2. PIN & Biometric Protection Card
            item {
                VaultSecurityCard(
                    vaultSettings = vaultSettings,
                    canUseBiometricHardware = canUseBiometricHardware,
                    onOpenPinSetup = { showPinDialog = true },
                    onToggleVaultLock = onToggleVaultLock,
                    onToggleBiometric = onToggleBiometric,
                    onToggleLockOnBackground = onToggleLockOnBackground,
                    onRemovePin = onRemovePin,
                    onLockVaultNow = onLockVaultNow
                )
            }

            // 3. Monetization (AdMob + Play Billing) Card
            item {
                MonetizationStatusCard(
                    isAdFree = isAdFree,
                    planType = entitlement?.planType ?: "FREE",
                    billingState = billingState,
                    adMobStatus = adMobStatus,
                    onTestInterstitial = onTestInterstitialAd,
                    onOpenUpgradeSheet = onOpenUpgradeSheet,
                    onRestorePurchases = onRestorePurchases
                )
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            canUseBiometricHardware = canUseBiometricHardware,
            initialBiometric = vaultSettings.biometricEnabled,
            onDismiss = { showPinDialog = false },
            onSavePin = { pin, enableBio ->
                onConfigurePin(pin, enableBio)
                showPinDialog = false
            }
        )
    }
}

@Composable
private fun AuthPrototypeLabCard(
    diagnostics: AuthDiagnostics,
    onSignOutClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .testTag("auth_lab_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!diagnostics.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = diagnostics.photoUrl,
                        contentDescription = "Google profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diagnostics.displayName ?: "Authenticated User",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = diagnostics.email ?: "No email available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Auth Prototype Diagnostics",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            DiagnosticRow("Firebase UID", diagnostics.uid ?: "—")
            DiagnosticRow("Providers", diagnostics.providers.joinToString(", ").ifBlank { "google.com" })
            DiagnosticRow("Email Verified", if (diagnostics.emailVerified) "Yes" else "No")
            DiagnosticRow(
                "Last Sign-In",
                diagnostics.lastSignInTimestamp?.let { dateFormat.format(Date(it)) } ?: "—"
            )

            OutlinedButton(
                onClick = onSignOutClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out & Test Re-Authentication")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VaultSecurityCard(
    vaultSettings: VaultSettings,
    canUseBiometricHardware: Boolean,
    onOpenPinSetup: () -> Unit,
    onToggleVaultLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleLockOnBackground: (Boolean) -> Unit,
    onRemovePin: () -> Unit,
    onLockVaultNow: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Vault Password & Biometric Lock",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!vaultSettings.hasPinConfigured) {
                Text(
                    text = "Add a 4–6 digit vault PIN and optional fingerprint/face unlock to protect your journal whenever you step away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenPinSetup,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_button")
                ) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Up Vault PIN")
                }
            } else {
                SettingsSwitchRow(
                    title = "Require PIN to open journal",
                    checked = vaultSettings.vaultLockEnabled,
                    onCheckedChange = onToggleVaultLock
                )
                SettingsSwitchRow(
                    title = "Unlock with Biometrics",
                    subtitle = if (canUseBiometricHardware) "Fingerprint / Face ready" else "No biometric hardware enrolled",
                    enabled = canUseBiometricHardware,
                    checked = vaultSettings.biometricEnabled && canUseBiometricHardware,
                    onCheckedChange = onToggleBiometric
                )
                SettingsSwitchRow(
                    title = "Auto-lock when leaving app",
                    checked = vaultSettings.lockOnBackground,
                    onCheckedChange = onToggleLockOnBackground
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onOpenPinSetup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_pin_button")
                    ) {
                        Text("Change PIN")
                    }
                    Button(
                        onClick = onLockVaultNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lock_now_button")
                    ) {
                        Text("Lock Now")
                    }
                }
                TextButton(
                    onClick = onRemovePin,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Remove Vault PIN", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun MonetizationStatusCard(
    isAdFree: Boolean,
    planType: String,
    billingState: BillingUiState,
    adMobStatus: String,
    onTestInterstitial: () -> Unit,
    onOpenUpgradeSheet: () -> Unit,
    onRestorePurchases: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Ads & Premium Upgrade",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            DiagnosticRow("Current Plan", if (isAdFree) "$planType (Ad-Free)" else "FREE (Ad-Supported)")
            DiagnosticRow("AdMob SDK", adMobStatus)
            DiagnosticRow("Play Billing", billingState.connectionMessage)

            if (!isAdFree) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onTestInterstitial,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_interstitial_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdsClick,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Ad")
                    }
                    Button(
                        onClick = onOpenUpgradeSheet,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_upgrade_sheet_button")
                    ) {
                        Text(stringResource(id = R.string.go_ad_free))
                    }
                }
            }

            TextButton(
                onClick = onRestorePurchases,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Restore Play Purchases")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpgradeSheet(
    isAdFree: Boolean,
    billingState: BillingUiState,
    onDismiss: () -> Unit,
    onRefreshBilling: () -> Unit,
    onPurchaseOffer: (Activity, BillingProductOffer) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "FolioNote Ad-Free",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Remove all banner and interstitial ads via Google Play Billing while keeping unlimited cloud sync and social telemetry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isAdFree) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your account already has active Ad-Free Premium access!",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (billingState.isQuerying) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator()
                }
            } else if (billingState.availableOffers.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Play Billing SKU Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = billingState.connectionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Configured Play Console product IDs: folionote_premium_monthly (Subscription) and folionote_ad_free_lifetime (One-Time). Once published to an internal test track in Google Play Console, live offers appear here automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onRefreshBilling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry Play Billing Connection")
                }
            } else {
                billingState.availableOffers.forEach { offer ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = offer.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = offer.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (activity != null) {
                                        onPurchaseOffer(activity, offer)
                                    }
                                }
                            ) {
                                Text(offer.formattedPrice)
                            }
                        }
                    }
                }
            }

            if (!billingState.lastBillingMessage.isNullOrBlank()) {
                Text(
                    text = billingState.lastBillingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
