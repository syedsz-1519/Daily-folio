package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.JournalRepository
import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.example.data.model.SharedPostDraft
import com.example.data.model.UiState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JournalViewModel(
    private val repository: JournalRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMoodFilter = MutableStateFlow<JournalMood?>(null)
    val selectedMoodFilter: StateFlow<JournalMood?> = _selectedMoodFilter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _pendingSharedDraft = MutableStateFlow<SharedPostDraft?>(null)
    val pendingSharedDraft: StateFlow<SharedPostDraft?> = _pendingSharedDraft.asStateFlow()

    private val rawEntries: StateFlow<UiState<List<JournalEntry>>> =
        repository.observeEntries(currentUserId)
            .map<List<JournalEntry>, UiState<List<JournalEntry>>> { entries ->
                UiState.Success(
                    entries.sortedWith(
                        compareByDescending<JournalEntry> { it.pinned }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing journal entries", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load journal entries"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val filteredEntriesState: StateFlow<UiState<List<JournalEntry>>> =
        combine(rawEntries, _searchQuery, _selectedMoodFilter) { state, query, moodFilter ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> state
                is UiState.Success -> {
                    val q = query.trim().lowercase()
                    val filtered = state.data.filter { entry ->
                        val matchesMood = moodFilter == null || entry.moodEnum == moodFilter
                        val matchesQuery = q.isEmpty() ||
                            entry.title.lowercase().contains(q) ||
                            entry.content.lowercase().contains(q) ||
                            entry.tags.any { it.lowercase().contains(q) } ||
                            (entry.sharedPlatform?.lowercase()?.contains(q) == true)
                        matchesMood && matchesQuery
                    }
                    UiState.Success(filtered)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMoodFilter(mood: JournalMood?) {
        _selectedMoodFilter.value = mood
    }

    fun setPendingSharedDraft(draft: SharedPostDraft?) {
        _pendingSharedDraft.value = draft
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun saveNewEntry(
        title: String,
        content: String,
        mood: JournalMood,
        tagsRaw: String,
        pinned: Boolean,
        sharedDraft: SharedPostDraft?,
        socialSnapshotSummary: String?,
        totalSocialMinutes: Long,
        onComplete: (Boolean) -> Unit
    ) {
        val tags = tagsRaw.split(",")
            .map { it.trim().take(32) }
            .filter { it.isNotEmpty() }
            .take(10)

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createEntry(
                userId = currentUserId,
                title = title,
                content = content,
                mood = mood,
                tags = tags,
                pinned = pinned,
                sharedUrl = sharedDraft?.url,
                sharedPlatform = sharedDraft?.platform,
                sharedSummary = sharedDraft?.summary,
                socialSnapshotSummary = socialSnapshotSummary,
                totalSocialMinutes = totalSocialMinutes
            )
            _isSaving.value = false
            result.onSuccess {
                _pendingSharedDraft.value = null
                _feedbackMessage.value = "Entry synced to your cloud journal"
                onComplete(true)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not save entry"
                onComplete(false)
            }
        }
    }

    fun updateExistingEntry(
        entryId: String,
        title: String,
        content: String,
        mood: JournalMood,
        tagsRaw: String,
        pinned: Boolean,
        socialSnapshotSummary: String?,
        totalSocialMinutes: Long,
        onComplete: (Boolean) -> Unit
    ) {
        val tags = tagsRaw.split(",")
            .map { it.trim().take(32) }
            .filter { it.isNotEmpty() }
            .take(10)

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateEntry(
                userId = currentUserId,
                entryId = entryId,
                title = title,
                content = content,
                mood = mood,
                tags = tags,
                pinned = pinned,
                socialSnapshotSummary = socialSnapshotSummary,
                totalSocialMinutes = totalSocialMinutes
            )
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "Entry updated in cloud"
                onComplete(true)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not update entry"
                onComplete(false)
            }
        }
    }

    fun togglePinned(entry: JournalEntry) {
        viewModelScope.launch {
            repository.togglePinned(currentUserId, entry.id, !entry.pinned)
                .onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to update pin"
                }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            repository.deleteEntry(currentUserId, entryId)
                .onSuccess {
                    _feedbackMessage.value = "Entry deleted"
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to delete entry"
                }
        }
    }

    private companion object {
        const val TAG = "JournalVM"
    }
}
