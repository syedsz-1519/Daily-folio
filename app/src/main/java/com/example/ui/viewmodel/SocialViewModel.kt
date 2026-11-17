package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SocialSnapshotRepository
import com.example.data.model.SocialSnapshot
import com.example.data.model.UiState
import com.example.data.security.VaultPreferencesRepository
import com.example.data.social.SocialUsageReport
import com.example.data.social.UsageStatsTelemetrySource
import com.example.data.social.YouTubeLookupResult
import com.example.data.social.YouTubeMetrics
import com.example.data.social.YouTubeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SocialViewModel(
    private val snapshotRepository: SocialSnapshotRepository,
    private val youtubeRepository: YouTubeRepository,
    private val preferencesRepository: VaultPreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _usageReport = MutableStateFlow<SocialUsageReport?>(null)
    val usageReport: StateFlow<SocialUsageReport?> = _usageReport.asStateFlow()

    private val _isLoadingUsage = MutableStateFlow(false)
    val isLoadingUsage: StateFlow<Boolean> = _isLoadingUsage.asStateFlow()

    private val _youtubeQuery = MutableStateFlow("@GoogleDevelopers")
    val youtubeQuery: StateFlow<String> = _youtubeQuery.asStateFlow()

    private val _youtubeState = MutableStateFlow<YouTubeLookupResult?>(null)
    val youtubeState: StateFlow<YouTubeLookupResult?> = _youtubeState.asStateFlow()

    private val _isLoadingYouTube = MutableStateFlow(false)
    val isLoadingYouTube: StateFlow<Boolean> = _isLoadingYouTube.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val hasYouTubeApiKey: Boolean
        get() = youtubeRepository.hasApiKey()

    val syncedSnapshots: StateFlow<UiState<List<SocialSnapshot>>> =
        snapshotRepository.observeSnapshots(currentUserId)
            .map<List<SocialSnapshot>, UiState<List<SocialSnapshot>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing social snapshots", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load synced snapshots"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            val saved = preferencesRepository.vaultSettings.first().savedYoutubeQuery
            _youtubeQuery.value = saved
            if (hasYouTubeApiKey && saved.isNotBlank()) {
                lookupYouTube(saved)
            } else if (!hasYouTubeApiKey) {
                _youtubeState.value = YouTubeLookupResult.MissingApiKey
            }
        }
    }

    fun refreshUsageStats(context: Context, syncToCloud: Boolean = false) {
        viewModelScope.launch {
            _isLoadingUsage.value = true
            val report = withContext(Dispatchers.Default) {
                UsageStatsTelemetrySource.querySocialUsage(context.applicationContext)
            }
            _usageReport.value = report
            _isLoadingUsage.value = false

            if (syncToCloud && report.hasPermission) {
                val todayMap = report.appUsages.associate { it.platformName to it.minutesToday }
                snapshotRepository.upsertSnapshot(
                    userId = currentUserId,
                    docId = "usage_${report.todayDateKey}",
                    sourceType = "USAGE_STATS",
                    label = "Device Social Usage (${report.todayDateKey})",
                    totalMinutes = report.todayTotalMinutes,
                    subscriberCount = 0L,
                    viewCount = 0L,
                    videoCount = 0L,
                    platformMinutes = todayMap
                ).onSuccess {
                    _statusMessage.value = "Today's social telemetry synced to cloud"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Sync failed"
                }
            }
        }
    }

    fun updateYoutubeQuery(value: String) {
        _youtubeQuery.value = value
    }

    fun lookupYouTube(queryOverride: String? = null) {
        val target = (queryOverride ?: _youtubeQuery.value).trim()
        viewModelScope.launch {
            _isLoadingYouTube.value = true
            preferencesRepository.setSavedYoutubeQuery(target)
            val result = withContext(Dispatchers.IO) {
                youtubeRepository.fetchMetrics(target)
            }
            _youtubeState.value = result
            _isLoadingYouTube.value = false
        }
    }

    fun saveYouTubeSnapshotToCloud(metrics: YouTubeMetrics) {
        viewModelScope.launch {
            val docId = "yt_${metrics.resourceId}"
            snapshotRepository.upsertSnapshot(
                userId = currentUserId,
                docId = docId,
                sourceType = "YOUTUBE_API",
                label = metrics.title,
                totalMinutes = 0L,
                subscriberCount = metrics.subscriberOrLikeCount,
                viewCount = metrics.viewCount,
                videoCount = metrics.videoOrCommentCount,
                platformMinutes = mapOf("YouTube" to 1L)
            ).onSuccess {
                _statusMessage.value = "Saved ${metrics.title} metrics to cloud"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not save YouTube snapshot"
            }
        }
    }

    fun buildTodaySummaryForJournal(): Pair<String?, Long> {
        val report = _usageReport.value ?: return null to 0L
        if (!report.hasPermission || report.appUsages.isEmpty()) return null to 0L
        val summary = report.appUsages
            .filter { it.minutesToday > 0L }
            .take(4)
            .joinToString(" · ") { "${it.platformName} ${it.minutesToday}m" }
            .ifBlank { "0m social screen time today" }
        return summary to report.todayTotalMinutes
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private companion object {
        const val TAG = "SocialVM"
    }
}
