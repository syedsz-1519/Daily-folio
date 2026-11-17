package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

enum class JournalMood(val label: String) {
    CALM("Calm"),
    GRATEFUL("Grateful"),
    FOCUSED("Focused"),
    REFLECTIVE("Reflective"),
    ENERGIZED("Energized"),
    TIRED("Tired");

    companion object {
        fun fromRaw(raw: String?): JournalMood =
            entries.firstOrNull { it.name == raw } ?: CALM
    }
}

@Keep
@IgnoreExtraProperties
data class JournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val mood: String = JournalMood.CALM.name,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val sharedUrl: String? = null,
    val sharedPlatform: String? = null,
    val sharedSummary: String? = null,
    val socialSnapshotSummary: String? = null,
    val totalSocialMinutes: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val moodEnum: JournalMood
        get() = JournalMood.fromRaw(mood)

    fun toCreateMap(ownerUid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to ownerUid,
            "title" to title.trim().take(140),
            "content" to content.trim().take(20000),
            "mood" to JournalMood.fromRaw(mood).name,
            "tags" to tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.take(10),
            "pinned" to pinned,
            "sharedUrl" to sharedUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(1000),
            "sharedPlatform" to sharedPlatform?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
            "sharedSummary" to sharedSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
            "socialSnapshotSummary" to socialSnapshotSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "totalSocialMinutes" to totalSocialMinutes.coerceIn(0L, 1440L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class SocialSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = "USAGE_STATS",
    val label: String = "",
    val totalMinutes: Long = 0L,
    val subscriberCount: Long = 0L,
    val viewCount: Long = 0L,
    val videoCount: Long = 0L,
    val platformMinutes: Map<String, Long> = emptyMap(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserEntitlement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val adFree: Boolean = false,
    val planType: String = "FREE",
    val productId: String = "",
    val purchaseToken: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class SharedPostDraft(
    val url: String? = null,
    val platform: String = "Social",
    val summary: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
