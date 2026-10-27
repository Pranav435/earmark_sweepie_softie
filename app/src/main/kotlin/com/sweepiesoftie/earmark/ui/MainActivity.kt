package com.sweepiesoftie.earmark.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.mutableLongStateOf
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
import com.sweepiesoftie.earmark.DebugLog
import com.sweepiesoftie.earmark.R
import com.sweepiesoftie.earmark.core.Event
import com.sweepiesoftie.earmark.demo.DemoNotifier
import com.sweepiesoftie.earmark.listener.Permissions
import com.sweepiesoftie.earmark.listener.EarmarkListener
import com.sweepiesoftie.earmark.listener.EarmarkTile
import com.sweepiesoftie.earmark.listener.KeepAliveService
import com.sweepiesoftie.earmark.store.Store
import com.sweepiesoftie.earmark.store.UserSettings
import com.sweepiesoftie.earmark.ui.theme.EarmarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private enum class Screen { Onboarding, Recap, Settings, Advanced, Trigger, Log }

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

    /** Android 13 and later ask, in a system dialog, whether to add the "Earmark this" tile to Quick Settings (ADR 0035). */
    fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, EarmarkTile::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_glyph),
                mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) Toast.makeText(this, R.string.settings_tile_added, Toast.LENGTH_SHORT).show()
            }
        } catch (e: RuntimeException) {
            AppGraph.debugLog.error("request tile", e)
        }
    }

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
    var screenReading by remember { mutableStateOf(Permissions.screenReading(activity)) }
    LifecycleResumeEffect(Unit) {
        permissions = readPermissions(activity)
        screenReading = Permissions.screenReading(activity)
        // An open app may always start it, so a start Android refused in the background recovers here.
        if (permissions.notifications) KeepAliveService.start(activity) else KeepAliveService.stop(activity)
        onPauseOrDispose { }
    }
    var screen by rememberSaveable { mutableStateOf(if (readPermissions(activity).required) Screen.Recap else Screen.Onboarding) }
    var setupStart by rememberSaveable { mutableStateOf(SetupStep.Intro) }
    val setup = { setupStart = permissions.firstMissing; screen = Screen.Onboarding }
    // The trigger being edited, or null for a new one (ADR 0038), and where the Log was opened from.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var logFrom by rememberSaveable { mutableStateOf(Screen.Recap) }
    val openLog = { from: Screen -> logFrom = from; screen = Screen.Log }
    // Setup opened from home or Settings goes back there; on a first run it is where the app starts.
    BackHandler(screen == Screen.Settings || (screen == Screen.Onboarding && setupStart != SetupStep.Intro)) { screen = Screen.Recap }
    BackHandler(screen == Screen.Advanced) { screen = Screen.Settings }
    BackHandler(screen == Screen.Trigger) { screen = Screen.Advanced }
    BackHandler(screen == Screen.Log) { screen = logFrom }

    val user by graph.settings.state.collectAsState()
    val pausedUntil = pausedUntil(user)
    val listener by graph.listener.collectAsState()
    // Android binds the listener a moment after access is granted, so wait before saying it isn't connected.
    var bindWaitOver by remember { mutableStateOf(false) }
    LaunchedEffect(permissions.notifications) {
        bindWaitOver = false
        delay(LISTENER_BIND_WAIT_MILLIS)
        bindWaitOver = true
    }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions = readPermissions(activity) }
    val requestContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions = readPermissions(activity) }
    val requestPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions = readPermissions(activity) }

    val pane = activity.getString(
        when (screen) {
            Screen.Onboarding -> R.string.app_name
            Screen.Recap -> R.string.app_name
            Screen.Settings -> R.string.settings_title
            Screen.Advanced -> R.string.advanced_title
            Screen.Trigger -> if (editing == null) R.string.form_new else R.string.form_edit
            Screen.Log -> R.string.log_title
        },
    )
    Box(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        when (screen) {
            Screen.Onboarding -> OnboardingScreen(
                permissions,
                setupStart,
                OnboardingActions(
                    onNotificationAccess = { activity.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                    onUsageAccess = { activity.openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    onOverlay = { activity.openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true) },
                    onBattery = activity::askBatteryUnrestricted,
                    onCalendar = { requestCalendar.launch(Manifest.permission.READ_CALENDAR) },
                    onContacts = { requestContacts.launch(Manifest.permission.READ_CONTACTS) },
                    onPostNotifications = { requestPostNotifications(requestPost::launch) },
                    onAppInfo = { activity.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true) },
                    onDone = { screen = Screen.Recap },
                ),
            )
            Screen.Recap -> {
                val rows by recapRows()
                val waiting = listener.waiting
                RecapScreen(
                    RecapState(
                        waiting = waiting,
                        rows = rows.filter { row -> waiting.none { it.loopId.value == row.id } },
                        pausedUntil = pausedUntil,
                        missingPermission = !permissions.required,
                        notListening = permissions.notifications && !listener.connected && bindWaitOver,
                        openLoops = listener.openLoops,
                        batteryRestricted = permissions.required && !permissions.battery,
                        demoMode = user.demoMode,
                        canPostNotifications = permissions.postNotifications,
                    ),
                    RecapActions(
                        onOpen = { graph.uiEvents.tryEmit(Event.WaitingOpened(it)) },
                        onDone = { graph.uiEvents.tryEmit(Event.WaitingDone(it)) },
                        onSettings = { screen = Screen.Settings },
                        onLog = { openLog(Screen.Recap) },
                        onResume = { graph.settings.pause(null) },
                        onFixPermissions = setup,
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
                val mutedChats by remember { graph.store.mutedChats() }.collectAsState(0)
                val triggerStats by remember { graph.store.triggerStats() }.collectAsState(emptyMap())
                SettingsScreen(
                    SettingsState(
                        user = user,
                        feeds = feeds,
                        showAllApps = showAll,
                        muted = muted,
                        mutedChats = mutedChats,
                        pausedUntil = pausedUntil,
                        canAddTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        mutedTriggers = user.triggers.filter { triggerStats[it.id]?.muted == true },
                    ),
                    SettingsActions(
                        onBack = { screen = Screen.Recap },
                        onPause = graph.settings::pause,
                        onAddTile = activity::requestTile,
                        onDrift = graph.settings::setDriftSeconds,
                        onDriftTimer = graph.settings::setDriftTimer,
                        onFeed = graph.settings::setFeed,
                        onNames = graph.settings::setNames,
                        onShowAllApps = { showAll = true },
                        onUnmute = { kind -> graph.uiEvents.tryEmit(Event.KindUnmuted(kind)) },
                        onUnmuteChats = { graph.uiEvents.tryEmit(Event.ChatsUnmuted) },
                        onUnmuteTrigger = { id -> graph.uiEvents.tryEmit(Event.TriggerUnmuted(id)) },
                        onAdvanced = { screen = Screen.Advanced },
                    ),
                )
            }
            Screen.Advanced -> {
                val triggerStats by remember { graph.store.triggerStats() }.collectAsState(emptyMap())
                AdvancedScreen(
                    AdvancedState(user = user, screenReading = screenReading, stats = triggerStats),
                    AdvancedActions(
                        onBack = { screen = Screen.Settings },
                        onAccessibility = { activity.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                        onAppInfo = { activity.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true) },
                        onAdd = { editing = null; screen = Screen.Trigger },
                        onEdit = { editing = it.id; screen = Screen.Trigger },
                        onTriggerOn = graph.settings::setTriggerOn,
                        onDemo = { on ->
                            graph.settings.setDemoMode(on)
                            if (on) graph.appScope.launch {
                                graph.store.seedAlarmHistory(ZoneId.systemDefault())
                                graph.uiEvents.emit(Event.StatsLoaded(graph.store.stats()))
                            }
                        },
                        onPermissions = setup,
                        onLog = { openLog(Screen.Advanced) },
                    ),
                )
            }
            Screen.Trigger -> {
                val apps by produceState(emptyList()) { value = withContext(Dispatchers.IO) { graph.apps.launcherApps() } }
                TriggerFormScreen(
                    initial = user.triggers.find { it.id == editing },
                    apps = apps,
                    screenReading = screenReading,
                    TriggerFormActions(
                        onBack = { screen = Screen.Advanced },
                        onSave = { trigger -> graph.settings.saveTrigger(trigger); screen = Screen.Advanced },
                        onDelete = { id ->
                            graph.settings.deleteTrigger(id)
                            graph.appScope.launch { graph.store.forgetTrigger(id) }
                            screen = Screen.Advanced
                        },
                        onTurnOnScreenReading = { activity.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    ),
                )
            }
            Screen.Log -> {
                val lines by graph.debugLog.lines.collectAsState()
                LogScreen(lines, onBack = { screen = logFrom }, onCopy = { copyLog(activity, lines) }, onClear = graph.debugLog::clear)
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

/** When the pause in force ends, or null. Recomposes when a pause runs out by itself, so the banner goes then. */
@Composable
private fun pausedUntil(user: UserSettings): Long? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(user.pausedUntil) {
        now = System.currentTimeMillis()
        val until = user.pausedUntil ?: return@LaunchedEffect
        if (until != UserSettings.UNTIL_RESUMED && until > now) {
            delay(until - now)
            now = System.currentTimeMillis()
        }
    }
    return user.pausedUntil?.takeIf { it > now }
}

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

/** The log never carries message text (ADR 0004), so a tester can paste it into a bug report as it is. */
private fun copyLog(activity: MainActivity, lines: List<DebugLog.Line>) {
    val format = java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
    val version = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        null
    }
    val text = buildString {
        appendLine("Earmark $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        lines.forEach { appendLine("${format.format(Date(it.atMillis))} ${it.text}") }
    }
    activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.log_title), text))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(activity, R.string.log_copied, Toast.LENGTH_SHORT).show()
}

private fun requestPostNotifications(launch: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launch(Manifest.permission.POST_NOTIFICATIONS)
}

private fun readPermissions(activity: MainActivity) = PermissionState(
    notifications = Permissions.notificationAccess(activity),
    usage = Permissions.usageAccess(activity),
    overlay = Permissions.overlay(activity),
    calendar = Permissions.calendar(activity),
    contacts = Permissions.contacts(activity),
    postNotifications = Permissions.postNotifications(activity),
    battery = Permissions.batteryUnrestricted(activity),
)
