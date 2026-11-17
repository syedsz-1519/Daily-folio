package com.example.data.social

import android.content.Intent
import com.example.data.model.SharedPostDraft

object ShareIntentParser {
    private val urlRegex = Regex("https?://\\S+")

    fun parse(intent: Intent?): SharedPostDraft? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (sharedText.isEmpty() && subject.isEmpty()) return null

        val extractedUrl = urlRegex.find(sharedText)?.value
        val platform = detectPlatform(extractedUrl ?: sharedText)
        val summary = buildString {
            if (subject.isNotEmpty()) append(subject)
            val cleanedText = sharedText.replace(extractedUrl.orEmpty(), "").trim()
            if (cleanedText.isNotEmpty()) {
                if (isNotEmpty()) append(" — ")
                append(cleanedText)
            }
        }.ifBlank { "Shared from $platform" }.take(500)

        return SharedPostDraft(
            url = extractedUrl,
            platform = platform,
            summary = summary
        )
    }

    fun detectPlatform(text: String): String {
        val lower = text.lowercase()
        return when {
            "instagram.com" in lower || "instagr.am" in lower -> "Instagram"
            "youtube.com" in lower || "youtu.be" in lower -> "YouTube"
            "tiktok.com" in lower -> "TikTok"
            "x.com" in lower || "twitter.com" in lower -> "X"
            "reddit.com" in lower -> "Reddit"
            "threads.net" in lower -> "Threads"
            "facebook.com" in lower || "fb.watch" in lower -> "Facebook"
            "linkedin.com" in lower -> "LinkedIn"
            else -> "Social"
        }
    }
}
