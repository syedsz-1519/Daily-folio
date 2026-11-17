package com.example.data.social

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SocialAppUsage(
    val platformName: String,
    val packageName: String,
    val minutesToday: Long,
    val minutesSevenDays: Long,
    val lastUsedMillis: Long
)

data class DailySocialUsagePoint(
    val dayLabel: String,
    val dateKey: String,
    val totalMinutes: Long,
    val platformMinutes: Map<String, Long>
)

data class SocialUsageReport(
    val hasPermission: Boolean,
    val todayDateKey: String,
    val todayTotalMinutes: Long,
    val sevenDayTotalMinutes: Long,
    val appUsages: List<SocialAppUsage>,
    val dailyTrend: List<DailySocialUsagePoint>
)

object UsageStatsTelemetrySource {

    private val trackedSocialPackages: Map<String, String> = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.twitter.android" to "X (Twitter)",
        "com.reddit.frontpage" to "Reddit",
        "com.facebook.katana" to "Facebook",
        "com.snapchat.android" to "Snapchat",
        "com.instagram.barcelona" to "Threads",
        "com.linkedin.android" to "LinkedIn",
        "com.pinterest" to "Pinterest",
        "com.discord" to "Discord"
    )

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun createUsageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun querySocialUsage(context: Context): SocialUsageReport {
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayShortFormat = SimpleDateFormat("EEE", Locale.US)
        val todayKey = dayKeyFormat.format(Calendar.getInstance().time)

        if (!hasUsageAccessPermission(context)) {
            return SocialUsageReport(
                hasPermission = false,
                todayDateKey = todayKey,
                todayTotalMinutes = 0L,
                sevenDayTotalMinutes = 0L,
                appUsages = emptyList(),
                dailyTrend = emptyList()
            )
        }

        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return SocialUsageReport(
                hasPermission = true,
                todayDateKey = todayKey,
                todayTotalMinutes = 0L,
                sevenDayTotalMinutes = 0L,
                appUsages = emptyList(),
                dailyTrend = emptyList()
            )

        val todayPlatformMinutes = linkedMapOf<String, Long>()
        val sevenDayPlatformMinutes = linkedMapOf<String, Long>()
        val platformLastUsed = mutableMapOf<String, Long>()
        val platformPackage = mutableMapOf<String, String>()
        val dailyPoints = mutableListOf<DailySocialUsagePoint>()

        // Query each of the last 7 calendar days accurately
        for (offset in 6 downTo 0) {
            val dayStartCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = dayStartCal.timeInMillis
            val endMs = if (offset == 0) {
                System.currentTimeMillis()
            } else {
                startMs + 24L * 60L * 60L * 1000L - 1L
            }

            val dayMap = linkedMapOf<String, Long>()
            val statsList = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startMs,
                endMs
            ).orEmpty()

            for (stat in statsList) {
                val platform = trackedSocialPackages[stat.packageName] ?: continue
                val foregroundMin = (stat.totalTimeInForeground / 60_000L).coerceAtLeast(0L)
                if (foregroundMin <= 0L && stat.lastTimeUsed <= 0L) continue

                dayMap[platform] = (dayMap[platform] ?: 0L) + foregroundMin
                sevenDayPlatformMinutes[platform] =
                    (sevenDayPlatformMinutes[platform] ?: 0L) + foregroundMin
                if (offset == 0) {
                    todayPlatformMinutes[platform] =
                        (todayPlatformMinutes[platform] ?: 0L) + foregroundMin
                }
                val prevLast = platformLastUsed[platform] ?: 0L
                if (stat.lastTimeUsed > prevLast) {
                    platformLastUsed[platform] = stat.lastTimeUsed
                }
                platformPackage.putIfAbsent(platform, stat.packageName)
            }

            dailyPoints.add(
                DailySocialUsagePoint(
                    dayLabel = dayShortFormat.format(dayStartCal.time),
                    dateKey = dayKeyFormat.format(dayStartCal.time),
                    totalMinutes = dayMap.values.sum(),
                    platformMinutes = dayMap
                )
            )
        }

        val allPlatforms = (todayPlatformMinutes.keys + sevenDayPlatformMinutes.keys).distinct()
        val usages = allPlatforms.map { platform ->
            SocialAppUsage(
                platformName = platform,
                packageName = platformPackage[platform].orEmpty(),
                minutesToday = todayPlatformMinutes[platform] ?: 0L,
                minutesSevenDays = sevenDayPlatformMinutes[platform] ?: 0L,
                lastUsedMillis = platformLastUsed[platform] ?: 0L
            )
        }.sortedByDescending { it.minutesToday * 10L + it.minutesSevenDays }

        return SocialUsageReport(
            hasPermission = true,
            todayDateKey = todayKey,
            todayTotalMinutes = todayPlatformMinutes.values.sum(),
            sevenDayTotalMinutes = sevenDayPlatformMinutes.values.sum(),
            appUsages = usages,
            dailyTrend = dailyPoints
        )
    }
}
