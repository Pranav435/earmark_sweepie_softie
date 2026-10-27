package com.sweepiesoftie.earmark.listener

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import com.sweepiesoftie.earmark.core.CuratedApps
import com.sweepiesoftie.earmark.core.Settings
import com.sweepiesoftie.earmark.store.UserSettings
import java.time.Duration
import java.time.ZoneId

/** An app that could count as a feed, for the Settings list. */
data class FeedCandidate(val packageName: String, val label: String, val onByDefault: Boolean)

/** Package lookups through the manifest's `<queries>` block, not QUERY_ALL_PACKAGES. */
class InstalledApps(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun label(packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    /** Launcher apps that are curated feeds or sit in the video, social or game categories, minus messaging apps. */
    fun feedCandidates(): List<FeedCandidate> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val feedCategories = setOf(ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_GAME)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && it.packageName !in CuratedApps.messaging }
            .filter { it.packageName in CuratedApps.feeds || it.category in feedCategories }
            .map { FeedCandidate(it.packageName, pm.getApplicationLabel(it).toString(), onByDefault = true) }
            .sortedBy { it.label.lowercase() }
    }

    /** The full launcher list, so testers can mark any app as a feed. */
    fun launcherApps(): List<FeedCandidate> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val defaults = feedCandidates().map { it.packageName }.toSet()
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { FeedCandidate(it.packageName, pm.getApplicationLabel(it).toString(), it.packageName in defaults) }
            .sortedWith(compareByDescending<FeedCandidate> { it.onByDefault }.thenBy { it.label.lowercase() })
    }

    fun clockPackage(): String? =
        pm.queryIntentActivities(Intent(AlarmClock.ACTION_SET_ALARM), 0).firstOrNull()?.activityInfo?.packageName

    fun rideApp(): String? = CuratedApps.rides.firstOrNull { isInstalled(it) }

    private fun isInstalled(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    fun engineSettings(user: UserSettings): Settings {
        val defaults = feedCandidates().map { it.packageName }.toSet()
        return Settings(
            ownPackage = context.packageName,
            feedApps = (defaults + user.feedsOn) - user.feedsOff,
            driftAfter = Duration.ofSeconds(user.effectiveDriftSeconds.toLong()),
            pillCooldown = if (user.demoMode) Duration.ZERO else Duration.ofMinutes(10),
            demoMode = user.demoMode,
            paused = user.paused,
            use24Hour = DateFormat.is24HourFormat(context),
            zone = ZoneId.systemDefault(),
            clockPackage = clockPackage(),
            rideApp = rideApp(),
        )
    }
}
