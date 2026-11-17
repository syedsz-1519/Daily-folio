package com.example.data.social

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class YouTubeChannelsResponse(
    val items: List<YouTubeChannelItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelItem(
    val id: String = "",
    val snippet: YouTubeSnippet? = null,
    val statistics: YouTubeChannelStatistics? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    val title: String = "",
    val description: String = "",
    val customUrl: String? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    val default: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    val url: String = ""
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelStatistics(
    val viewCount: String? = null,
    val subscriberCount: String? = null,
    val videoCount: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItem(
    val id: YouTubeSearchId? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchId(
    val channelId: String? = null,
    val videoId: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeVideosResponse(
    val items: List<YouTubeVideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoItem(
    val id: String = "",
    val snippet: YouTubeSnippet? = null,
    val statistics: YouTubeVideoStatistics? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoStatistics(
    val viewCount: String? = null,
    val likeCount: String? = null,
    val commentCount: String? = null
)

interface YouTubeApiService {
    @GET("channels")
    suspend fun getChannelByHandle(
        @Query("part") part: String = "snippet,statistics",
        @Query("forHandle") forHandle: String,
        @Query("key") apiKey: String
    ): YouTubeChannelsResponse

    @GET("channels")
    suspend fun getChannelById(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): YouTubeChannelsResponse

    @GET("search")
    suspend fun searchChannel(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "channel",
        @Query("maxResults") maxResults: Int = 1,
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse

    @GET("videos")
    suspend fun getVideoById(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): YouTubeVideosResponse
}
