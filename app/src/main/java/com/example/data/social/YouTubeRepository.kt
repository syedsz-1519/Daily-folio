package com.example.data.social

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class YouTubeMetrics(
    val resourceId: String,
    val isVideo: Boolean,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val viewCount: Long,
    val subscriberOrLikeCount: Long,
    val videoOrCommentCount: Long
)

sealed interface YouTubeLookupResult {
    data object MissingApiKey : YouTubeLookupResult
    data class Success(val metrics: YouTubeMetrics) : YouTubeLookupResult
    data class Error(val message: String) : YouTubeLookupResult
}

class YouTubeRepository {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val api: YouTubeApiService = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YouTubeApiService::class.java)

    fun hasApiKey(): Boolean =
        BuildConfig.YOUTUBE_API_KEY.isNotBlank() &&
            BuildConfig.YOUTUBE_API_KEY != "MY_YOUTUBE_API_KEY"

    suspend fun fetchMetrics(queryOrUrl: String): YouTubeLookupResult {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_YOUTUBE_API_KEY") {
            return YouTubeLookupResult.MissingApiKey
        }
        val trimmed = queryOrUrl.trim()
        if (trimmed.isEmpty()) {
            return YouTubeLookupResult.Error("Enter a YouTube @handle, channel ID, or video URL.")
        }

        return try {
            val videoId = extractVideoId(trimmed)
            if (videoId != null) {
                val res = api.getVideoById(id = videoId, apiKey = apiKey)
                val video = res.items.firstOrNull()
                    ?: return YouTubeLookupResult.Error("No YouTube video found for ID $videoId.")
                val thumb = video.snippet?.thumbnails?.high?.url
                    ?: video.snippet?.thumbnails?.medium?.url
                    ?: video.snippet?.thumbnails?.default?.url
                return YouTubeLookupResult.Success(
                    YouTubeMetrics(
                        resourceId = video.id,
                        isVideo = true,
                        title = video.snippet?.title.orEmpty().ifBlank { "YouTube Video" },
                        subtitle = video.snippet?.description?.take(120).orEmpty(),
                        thumbnailUrl = thumb,
                        viewCount = video.statistics?.viewCount?.toLongOrNull() ?: 0L,
                        subscriberOrLikeCount = video.statistics?.likeCount?.toLongOrNull() ?: 0L,
                        videoOrCommentCount = video.statistics?.commentCount?.toLongOrNull() ?: 0L
                    )
                )
            }

            val channel = when {
                trimmed.startsWith("UC") && trimmed.length >= 20 -> {
                    api.getChannelById(id = trimmed, apiKey = apiKey).items.firstOrNull()
                }
                trimmed.startsWith("@") -> {
                    api.getChannelByHandle(forHandle = trimmed, apiKey = apiKey).items.firstOrNull()
                }
                else -> {
                    val searchRes = api.searchChannel(query = trimmed, apiKey = apiKey)
                    val chId = searchRes.items.firstOrNull()?.id?.channelId
                    if (chId != null) {
                        api.getChannelById(id = chId, apiKey = apiKey).items.firstOrNull()
                    } else null
                }
            } ?: return YouTubeLookupResult.Error("No matching YouTube channel found for \"$trimmed\".")

            val thumb = channel.snippet?.thumbnails?.high?.url
                ?: channel.snippet?.thumbnails?.medium?.url
                ?: channel.snippet?.thumbnails?.default?.url

            YouTubeLookupResult.Success(
                YouTubeMetrics(
                    resourceId = channel.id,
                    isVideo = false,
                    title = channel.snippet?.title.orEmpty().ifBlank { trimmed },
                    subtitle = channel.snippet?.customUrl ?: "YouTube Channel",
                    thumbnailUrl = thumb,
                    viewCount = channel.statistics?.viewCount?.toLongOrNull() ?: 0L,
                    subscriberOrLikeCount = channel.statistics?.subscriberCount?.toLongOrNull() ?: 0L,
                    videoOrCommentCount = channel.statistics?.videoCount?.toLongOrNull() ?: 0L
                )
            )
        } catch (e: Exception) {
            YouTubeLookupResult.Error(e.localizedMessage ?: "Failed to fetch live YouTube data.")
        }
    }

    private fun extractVideoId(input: String): String? {
        val watchRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
        val shortRegex = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})")
        val shortsRegex = Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
        return watchRegex.find(input)?.groupValues?.getOrNull(1)
            ?: shortRegex.find(input)?.groupValues?.getOrNull(1)
            ?: shortsRegex.find(input)?.groupValues?.getOrNull(1)
    }
}
