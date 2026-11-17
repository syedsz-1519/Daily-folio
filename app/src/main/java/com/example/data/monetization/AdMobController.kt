package com.example.data.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdMobController {
    private const val TAG = "FolioAdMob"
    private var initialized = false
    private var interstitialAd: InterstitialAd? = null
    private var lastInterstitialShownMs = 0L
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 90_000L

    private val _adStatus = MutableStateFlow("AdMob idle")
    val adStatus: StateFlow<String> = _adStatus.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        initialized = true
        MobileAds.initialize(context.applicationContext) {
            _adStatus.value = "AdMob SDK initialized (Test Ad Units active)"
            preloadInterstitial(context.applicationContext)
        }
    }

    fun preloadInterstitial(context: Context) {
        val unitId = context.getString(R.string.admob_interstitial_unit_id)
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    _adStatus.value = "Banner + Interstitial test ad ready"
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            preloadInterstitial(context.applicationContext)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            Log.w(TAG, "Interstitial show failed: ${adError.message}")
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Interstitial load failed: ${error.message}")
                    _adStatus.value = "Test banner active (interstitial code ${error.code})"
                }
            }
        )
    }

    fun showInterstitialIfEligible(activity: Activity?, isAdFree: Boolean, force: Boolean = false) {
        if (isAdFree || activity == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastInterstitialShownMs < MIN_INTERSTITIAL_INTERVAL_MS) return
        val ad = interstitialAd
        if (ad != null) {
            lastInterstitialShownMs = now
            ad.show(activity)
        } else {
            preloadInterstitial(activity)
        }
    }
}

@Composable
fun AdMobBannerSlot(
    isAdFree: Boolean,
    modifier: Modifier = Modifier
) {
    if (isAdFree) return

    val context = LocalContext.current
    val unitId = remember(context) { context.getString(R.string.admob_banner_unit_id) }
    var bannerLoaded by remember { mutableStateOf(false) }
    var bannerError by remember { mutableStateOf<String?>(null) }

    val adView = remember(context, unitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = unitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    bannerLoaded = true
                    bannerError = null
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    bannerLoaded = false
                    bannerError = error.message
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .testTag("admob_banner_container")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            AndroidView(
                factory = { adView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
            if (!bannerLoaded) {
                Text(
                    text = if (bannerError == null) {
                        "Loading AdMob test banner…"
                    } else {
                        "AdMob banner standby (${bannerError})"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}
