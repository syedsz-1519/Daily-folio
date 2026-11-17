package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.SharedPostDraft
import com.example.data.model.SocialSnapshot
import com.example.data.model.UiState
import com.example.data.social.DailySocialUsagePoint
import com.example.data.social.SocialAppUsage
import com.example.data.social.SocialUsageReport
import com.example.data.social.UsageStatsTelemetrySource
import com.example.data.social.YouTubeLookupResult
import com.example.data.social.YouTubeMetrics
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.OtherSocialAccent
import com.example.ui.theme.RedditAccent
import com.example.ui.theme.TikTokAccent
import com.example.ui.theme.XAccent
import com.example.ui.theme.YouTubeAccent
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SocialPulseScreen(
    usageReport: SocialUsageReport?,
    isLoadingUsage: Boolean,
    youtubeQuery: String,
    youtubeState: YouTubeLookupResult?,
    isLoadingYouTube: Boolean,
    syncedSnapshotsState: UiState<List<SocialSnapshot>>,
    onRefreshUsage: (syncToCloud: Boolean) -> Unit,
    onYoutubeQueryChange: (String) -> Unit,
    onLookupYouTube: () -> Unit,
    onSaveYoutubeSnapshot: (YouTubeMetrics) -> Unit,
    onJournalWithSocialData: (SharedPostDraft?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onRefreshUsage(false)
    }

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
                        text = "LIVE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Social Pulse",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real screen-time telemetry across Instagram, YouTube, TikTok & all social apps, plus live YouTube Data API metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Device UsageStats Telemetry Card
            item {
                DeviceSocialUsageCard(
                    report = usageReport,
                    isLoading = isLoadingUsage,
                    onGrantAccess = {
                        context.startActivity(UsageStatsTelemetrySource.createUsageAccessIntent())
                    },
                    onRefresh = { onRefreshUsage(false) },
                    onSyncCloud = { onRefreshUsage(true) },
                    onJournalToday = { onJournalWithSocialData(null) }
                )
            }

            // 2. YouTube Data API v3 Live Card
            item {
                YouTubeLiveMetricsCard(
                    query = youtubeQuery,
                    state = youtubeState,
                    isLoading = isLoadingYouTube,
                    onQueryChange = onYoutubeQueryChange,
                    onSearch = onLookupYouTube,
                    onSaveSnapshot = onSaveYoutubeSnapshot,
                    onJournalAbout = { metrics ->
                        onJournalWithSocialData(
                            SharedPostDraft(
                                url = if (metrics.isVideo) "https://youtube.com/watch?v=${metrics.resourceId}"
                                else "https://youtube.com/channel/${metrics.resourceId}",
                                platform = "YouTube",
                                summary = "${metrics.title} · ${formatCompactNumber(metrics.viewCount)} views · ${formatCompactNumber(metrics.subscriberOrLikeCount)} ${if (metrics.isVideo) "likes" else "subscribers"}"
                            )
                        )
                    }
                )
            }

            // 3. Share Sheet Tip Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Capture from Instagram, TikTok, X & YouTube",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Tap Share on any post, reel, or video in another social app and pick FolioNote to journal about it with source metadata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // 4. Synced Cloud Snapshots
            item {
                Text(
                    text = "Cloud-Synced Telemetry History",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            when (syncedSnapshotsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = syncedSnapshotsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is UiState.Success -> {
                    val snaps = syncedSnapshotsState.data
                    if (snaps.isEmpty()) {
                        item {
                            Text(
                                text = "No snapshots synced yet. Grant Usage Access or look up a YouTube channel and tap Sync.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(snaps, key = { it.id }) { snap ->
                            SyncedSnapshotRow(snap = snap)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSocialUsageCard(
    report: SocialUsageReport?,
    isLoading: Boolean,
    onGrantAccess: () -> Unit,
    onRefresh: () -> Unit,
    onSyncCloud: () -> Unit,
    onJournalToday: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Android Social Usage",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_usage_stats_button")
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh usage stats")
                }
            }

            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    CircularProgressIndicator()
                }
            } else if (report == null || !report.hasPermission) {
                Text(
                    text = "Usage Access is required to read real screen time for Instagram, YouTube, TikTok, X, Reddit, and other installed social apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onGrantAccess,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_usage_access_button")
                ) {
                    Text("Grant Usage Access in Settings")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricStatTile(
                        label = "TODAY",
                        value = "${report.todayTotalMinutes} min",
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatTile(
                        label = "7-DAY TOTAL",
                        value = "${report.sevenDayTotalMinutes} min",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (report.dailyTrend.isNotEmpty()) {
                    Text(
                        text = "7-Day Platform Breakdown",
                        style = MaterialTheme.typography.titleSmall
                    )
                    SocialStackedBarChart(
                        points = report.dailyTrend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                    )
                    SocialChartLegend()
                }

                if (report.appUsages.isEmpty()) {
                    Text(
                        text = "No social app screen time recorded in the last 7 days on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.appUsages.take(6).forEach { app ->
                            AppUsageRow(app = app)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onSyncCloud,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_usage_cloud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Cloud")
                    }
                    Button(
                        onClick = onJournalToday,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reflect_on_usage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Create,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reflect")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SocialStackedBarChart(
    points: List<DailySocialUsagePoint>,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val emptyBarColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val maxVal = (points.maxOfOrNull { it.totalMinutes } ?: 0L).coerceAtLeast(30L).toFloat()
            val barCount = points.size.coerceAtLeast(1)
            val slotWidth = size.width / barCount
            val barWidth = (slotWidth * 0.48f).coerceAtLeast(10f)

            // Horizontal baseline & mid-grid
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, size.height * 0.5f),
                end = Offset(size.width, size.height * 0.5f),
                strokeWidth = 1f
            )

            points.forEachIndexed { index, point ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                if (point.totalMinutes <= 0L) {
                    drawRoundRect(
                        color = emptyBarColor,
                        topLeft = Offset(x, size.height - 8f),
                        size = Size(barWidth, 8f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                } else {
                    var currentBottom = size.height
                    val orderedEntries = point.platformMinutes.entries.sortedBy { it.key }
                    orderedEntries.forEach { (platform, mins) ->
                        val segmentHeight = ((mins / maxVal) * (size.height - 8f)).coerceAtLeast(4f)
                        val top = (currentBottom - segmentHeight).coerceAtLeast(0f)
                        drawRoundRect(
                            color = platformColor(platform),
                            topLeft = Offset(x, top),
                            size = Size(barWidth, currentBottom - top),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        currentBottom = top
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ) {
            points.forEach { point ->
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SocialChartLegend() {
    val legendItems = listOf(
        "Instagram" to InstagramAccent,
        "YouTube" to YouTubeAccent,
        "TikTok" to TikTokAccent,
        "X" to XAccent,
        "Reddit" to RedditAccent
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        legendItems.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: SocialAppUsage) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(platformColor(app.platformName))
            )
            Text(
                text = app.platformName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "${app.minutesToday}m today · ${app.minutesSevenDays}m / 7d",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun YouTubeLiveMetricsCard(
    query: String,
    state: YouTubeLookupResult?,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSaveSnapshot: (YouTubeMetrics) -> Unit,
    onJournalAbout: (YouTubeMetrics) -> Unit
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
            Text(
                text = "YouTube Live Channel & Video Data",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("@handle, channel, or video URL") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("youtube_query_input")
                )
                Button(
                    onClick = onSearch,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("youtube_fetch_button")
                ) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Fetch YouTube metrics")
                }
            }

            if (isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                when (state) {
                    is YouTubeLookupResult.MissingApiKey -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Configure YOUTUBE_API_KEY in the AI Studio Secrets panel to fetch live YouTube Data API v3 subscriber, view, and video analytics.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is YouTubeLookupResult.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is YouTubeLookupResult.Success -> {
                        val m = state.metrics
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!m.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = m.thumbnailUrl,
                                    contentDescription = m.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = m.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MetricStatTile(
                                label = "VIEWS",
                                value = formatCompactNumber(m.viewCount),
                                modifier = Modifier.weight(1f)
                            )
                            MetricStatTile(
                                label = if (m.isVideo) "LIKES" else "SUBS",
                                value = formatCompactNumber(m.subscriberOrLikeCount),
                                modifier = Modifier.weight(1f)
                            )
                            MetricStatTile(
                                label = if (m.isVideo) "COMMENTS" else "VIDEOS",
                                value = formatCompactNumber(m.videoOrCommentCount),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onSaveSnapshot(m) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Snapshot")
                            }
                            Button(
                                onClick = { onJournalAbout(m) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Journal About")
                            }
                        }
                    }

                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SyncedSnapshotRow(snap: SocialSnapshot) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = snap.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = snap.sourceType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (snap.sourceType == "USAGE_STATS") {
                val breakdown = snap.platformMinutes.entries
                    .joinToString(" · ") { "${it.key} ${it.value}m" }
                Text(
                    text = "${snap.totalMinutes} min total" + if (breakdown.isNotEmpty()) " ($breakdown)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${formatCompactNumber(snap.viewCount)} views · ${formatCompactNumber(snap.subscriberCount)} subs/likes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun platformColor(name: String): Color = when {
    name.contains("Instagram", ignoreCase = true) -> InstagramAccent
    name.contains("YouTube", ignoreCase = true) -> YouTubeAccent
    name.contains("TikTok", ignoreCase = true) -> TikTokAccent
    name.startsWith("X", ignoreCase = true) -> XAccent
    name.contains("Reddit", ignoreCase = true) -> RedditAccent
    else -> OtherSocialAccent
}

private fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> NumberFormat.getIntegerInstance().format(value)
    }
}
