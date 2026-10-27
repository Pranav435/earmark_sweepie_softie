package com.sweepiesoftie.earmark.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sweepiesoftie.earmark.AppGraph
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.demo.DemoNotifier
import com.sweepiesoftie.earmark.listener.Permissions
import com.sweepiesoftie.earmark.listener.EarmarkListener
import com.sweepiesoftie.earmark.listener.KeepAliveService
import com.sweepiesoftie.earmark.store.Store
import com.sweepiesoftie.earmark.ui.theme.EarmarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private enum class Screen { Onboarding, Recap, Settings, Log }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarmarkTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { Root() }
            }
        }
    }

    /** Android's own "let this app run in the background?" prompt; it switches battery optimisation off for Earmark. */
    @SuppressLint("BatteryLife")
    fun askBatteryUnrestricted(): Unit = openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackage = true)

    /** Opens a system settings page, falling back to the general list when an OEM has moved it. */
    fun openSettings(action: String, withPackage: Boolean = false) {
        val intent = Intent(action).apply { if (withPackage) data = Uri.fromParts("package", packageName, null) }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                AppGraph.debugLog.error("open settings", e)
            }
        }
    }
}

@Composable
private fun Root() {
    val activity = LocalActivity.current as MainActivity
    val graph = AppGraph
    var permissions by remember { mutableStateOf(readPermissions(activity)) }
    LifecycleResumeEffect(Unit) {
        permissions = readPermissions(activity)
        // An open app may always start it, so a start Android refused in the background recovers here.
        if (permissions.notifications) KeepAliveService.start(activity) else KeepAliveService.stop(activity)
        onPauseOrDispose { }
    }
    var screen by rememberSaveable { mutableStateOf(if (readPermissions(activity).required) Screen.Recap else Screen.Onboarding) }
    BackHandler(screen == Screen.Settings || screen == Screen.Log) { screen = Screen.Recap }

    val user by graph.settings.state.collectAsState()
    val listener by graph.listener.collectAsState()
    // Android binds the listener a moment after access is granted, so wait before saying it isn't connected.
    var bindWaitOver by remember { mutableStateOf(false) }
    LaunchedEffect(permissions.notifications) {
        bindWaitOver = false
        delay(LISTENER_BIND_WAIT_MILLIS)
        bindWaitOver = true
    }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions = readPermissions(activity) }
    val requestPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions = readPermissions(activity) }

    val pane = activity.getString(
        when (screen) {
            Screen.Onboarding -> R.string.app_name
            Screen.Recap -> R.string.app_name
            Screen.Settings -> R.string.settings_title
            Screen.Log -> R.string.log_title
        },
    )
    Box(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        when (screen) {
            Screen.Onboarding -> OnboardingScreen(
                permissions,
                OnboardingActions(
                    onNotificationAccess = { activity.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                    onUsageAccess = { activity.openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    onOverlay = { activity.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true) },
                    onBattery = activity::askBatteryUnrestricted,
                    onCalendar = { requestCalendar.launch(Manifest.permission.READ_CALENDAR) },
                    onPostNotifications = { requestPostNotifications(requestPost::launch) },
                    onAppInfo = { activity.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true) },
                    onDone = { screen = Screen.Recap },
                ),
            )
            Screen.Recap -> {
                val rows by recapRows()
                RecapScreen(
                    RecapState(
                        rows = rows,
                        paused = user.paused,
                        missingPermission = !permissions.required,
                        notListening = permissions.notifications && !listener.connected && bindWaitOver,
                        openLoops = listener.openLoops,
                        batteryRestricted = permissions.required && !permissions.battery,
                        demoMode = user.demoMode,
                        canPostNotifications = permissions.postNotifications,
                    ),
                    RecapActions(
                        onSettings = { screen = Screen.Settings },
                        onLog = { screen = Screen.Log },
                        onResume = { graph.settings.setPaused(false) },
                        onFixPermissions = { screen = Screen.Onboarding },
                        onNotificationAccess = { activity.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                        onBattery = activity::askBatteryUnrestricted,
                        onDemoRahul = { DemoNotifier.postRahul(activity) },
                        onDemoAisha = { DemoNotifier.postAisha(activity) },
                        onAllowNotifications = { requestPostNotifications(requestPost::launch) },
                    ),
                )
            }
            Screen.Settings -> {
                var showAll by rememberSaveable { mutableStateOf(false) }
                val feeds by produceState(emptyList(), showAll) {
                    value = withContext(Dispatchers.IO) { if (showAll) graph.apps.launcherApps() else graph.apps.feedCandidates() }
                }
                val muted by remember { graph.store.mutedKinds() }.collectAsState(emptyList())
                SettingsScreen(
                    SettingsState(user = user, feeds = feeds, showAllApps = showAll, muted = muted),
                    SettingsActions(
                        onBack = { screen = Screen.Recap },
                        onPaused = graph.settings::setPaused,
                        onDemo = { on ->
                            graph.settings.setDemoMode(on)
                            if (on) graph.appScope.launch {
                                graph.store.seedAlarmHistory(ZoneId.systemDefault())
                                graph.uiEvents.emit(Event.StatsLoaded(graph.store.stats()))
                            }
                        },
                        onDrift = graph.settings::setDriftSeconds,
                        onFeed = graph.settings::setFeed,
                        onShowAllApps = { showAll = true },
                        onUnmute = { kind -> graph.uiEvents.tryEmit(Event.KindUnmuted(kind)) },
                        onPermissions = { screen = Screen.Onboarding },
                        onLog = { screen = Screen.Log },
                    ),
                )
            }
            Screen.Log -> {
                val lines by graph.debugLog.lines.collectAsState()
                LogScreen(lines, onBack = { screen = Screen.Recap }, onClear = graph.debugLog::clear)
            }
        }
    }
    LaunchedEffect(permissions.notifications, listener.connected) {
        // Nudge the system to bind the listener when access is on but it isn't connected, such as after an
        // update or a force stop, which some phones don't recover from on their own.
        if (permissions.notifications && !listener.connected) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(activity, EarmarkListener::class.java),
                )
            } catch (e: RuntimeException) {
                graph.debugLog.error("requestRebind", e)
            }
        }
    }
}

private const val LISTENER_BIND_WAIT_MILLIS = 3_000L

@Composable
private fun recapRows() = run {
    val context = LocalContext.current
    val since = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val time = remember { android.text.format.DateFormat.getTimeFormat(context) }
    remember(since) { AppGraph.store.things(since) }.collectAsState(emptyList()).let { state ->
        produceState(emptyList<RecapRow>(), state.value) {
            value = state.value.mapNotNull { row ->
                val kind = Store.kindOf(row.kind) ?: return@mapNotNull null
                RecapRow(
                    id = row.loopId,
                    kind = kind,
                    label = row.label,
                    time = time.format(Date(row.closedAt ?: row.guessedAt ?: row.openedAt)),
                    outcome = Store.outcomeOf(row.outcome),
                    broughtBack = row.broughtBack,
                )
            }
        }
    }
}

private fun requestPostNotifications(launch: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launch(Manifest.permission.POST_NOTIFICATIONS)
}

private fun readPermissions(activity: MainActivity) = PermissionState(
    notifications = Permissions.notificationAccess(activity),
    usage = Permissions.usageAccess(activity),
    overlay = Permissions.overlay(activity),
    calendar = Permissions.calendar(activity),
    postNotifications = Permissions.postNotifications(activity),
    battery = Permissions.batteryUnrestricted(activity),
)
