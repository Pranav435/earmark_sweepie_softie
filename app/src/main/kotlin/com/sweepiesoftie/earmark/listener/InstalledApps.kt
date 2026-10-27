package com.sweepiesoftie.earmark.listener

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.core.net.toUri
import com.sweepiesoftie.earmark.core.CuratedApps
import com.sweepiesoftie.earmark.core.DriftTimer
import com.sweepiesoftie.earmark.core.Settings
import com.sweepiesoftie.earmark.store.UserSettings
import java.time.Duration
import java.time.Instant
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

    /**
     * Launcher apps that are curated feeds or sit in the video, social or game categories, minus messaging apps and
     * browsers. The Play Store files Brave and Tor Browser under social, and a browser is a tool, not a feed (ADR 0037).
     */
    fun feedCandidates(): List<FeedCandidate> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val feedCategories = setOf(ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_GAME)
        val browsers = browsers()
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && it.packageName !in CuratedApps.messaging }
            .filter { it.packageName in CuratedApps.feeds || (it.category in feedCategories && it.packageName !in browsers) }
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

    /** Apps that open any web link, which only browsers do. */
    private fun browsers(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()

    fun clockPackage(): String? =
        pm.queryIntentActivities(Intent(AlarmClock.ACTION_SET_ALARM), 0).firstOrNull()?.activityInfo?.packageName

    fun rideApp(): String? = CuratedApps.rides.firstOrNull { isInstalled(it) }

    private fun isInstalled(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    fun engineSettings(user: UserSettings): Settings {
        val defaults = feedCandidates().map { it.packageName }.toSet()
        return Settings(
            ownPackage = context.packageName,
            feedApps = (defaults + user.feedsOn) - user.feedsOff,
            // Demo mode is always right away, so every story runs the same on any phone.
            driftTimer = if (user.demoMode) DriftTimer.RightAway else user.driftTimer,
            driftAfter = Duration.ofSeconds(user.driftSeconds.toLong()),
            demoMode = user.demoMode,
            pausedUntil = user.pausedUntil?.let { if (it == UserSettings.UNTIL_RESUMED) Instant.MAX else Instant.ofEpochMilli(it) },
            use24Hour = DateFormat.is24HourFormat(context),
            zone = ZoneId.systemDefault(),
            clockPackage = clockPackage(),
            rideApp = rideApp(),
            // The listener adds the names on your contact card.
            myNames = user.typedNames,
            homeApps = homeApps(),
            triggers = user.triggers,
        )
    }

    /** Launchers, and the system UI that recents and the shade belong to on some phones: none is an app to earmark. */
    fun homeApps(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet() +
            "com.android.systemui"
}
