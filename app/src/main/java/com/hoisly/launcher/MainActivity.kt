package com.hoisly.launcher

import com.hoisly.launcher.ui.theme.Motion
import kotlinx.coroutines.delay
import android.Manifest
import android.content.ContentUris
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.core.net.toUri
import com.hoisly.launcher.data.WidgetTarget
import android.app.SearchManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import com.hoisly.launcher.data.AppInfo
import com.hoisly.launcher.island.NotificationBadgeFeed
import com.hoisly.launcher.settings.AppLanguage
import com.hoisly.launcher.settings.BadgeStyle
import com.hoisly.launcher.statusbar.StatusBarStore
import com.hoisly.launcher.settings.LocalSettingsState
import com.hoisly.launcher.ui.glass.GlassLight
import com.hoisly.launcher.ui.glass.GlassMode
import com.hoisly.launcher.ui.glass.GlassTiltSensor
import com.hoisly.launcher.ui.glass.LocalGlassLight
import com.hoisly.launcher.ui.glass.LocalGlassSource
import com.hoisly.launcher.ui.glass.glassStyle
import com.hoisly.launcher.ui.glass.WallpaperBlur
import com.hoisly.launcher.ui.glass.isLiquidGlassReady
import com.hoisly.launcher.ui.glass.resolveGlassMode
import com.hoisly.launcher.ui.glass.LocalGlassStyle
import com.hoisly.launcher.ui.home.HomeScreen
import com.hoisly.launcher.ui.lockscreen.HoislyLockScreen
import com.hoisly.launcher.ui.home.LauncherActions
import com.hoisly.launcher.ui.icon.BadgeSource
import com.hoisly.launcher.ui.icon.IconLoader
import com.hoisly.launcher.ui.icon.LocalBadges
import com.hoisly.launcher.ui.icon.LocalIconLoader
import com.hoisly.launcher.ui.theme.AccentColors
import com.hoisly.launcher.ui.theme.LocalAccent
import com.hoisly.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * The home screen. Declared with the HOME intent filter, so Android offers it as the
 * default launcher. The window is transparent and shows the system wallpaper behind it.
 */
class MainActivity : ComponentActivity(), LauncherActions {

    private val vm: LauncherViewModel by viewModels()
    private var showWakeLockScreen by mutableStateOf(false)
    private var screenWasTurnedOff = false
    private var screenReceiverRegistered = false
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                screenWasTurnedOff = true
                // Mark the launcher as allowed above the swipe keyguard before the display wakes.
                // The system keyguard remains responsible for secure PIN/pattern/biometric auth.
                if (vm.settings.state.value.showLockScreenAfterSleep) {
                    setShowWhenLocked(true)
                }
            }
        }
    }
    private val launcherApps: LauncherApps by lazy { getSystemService(LauncherApps::class.java) }

    /** Photo widget waiting for the picker result; kept across process death. */
    private var pendingPhotoWidget: String? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val widgetId = pendingPhotoWidget
        pendingPhotoWidget = null
        if (uri != null && widgetId != null) vm.setWidgetPhoto(widgetId, uri)
    }

    /** Another app's widget being added; kept across the bind/configure screens and process death. */
    private var pendingWidget: PendingAppWidget? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrPlace(pending) else cancelWidget(pending)
    }

    private val pickGlassWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && WallpaperBlur.setFallbackWallpaper(this, uri)) vm.refreshWallpaper(force = true)
    }
    private val requestCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshWidgets()
    }

    /** Light on Liquid glass, moved by [tiltSensor] while the home screen is started. */
    private val glassLight = GlassLight()
    private val tiltSensor by lazy { GlassTiltSensor(this, glassLight) }

    private val wallpaperListener = WallpaperManager.OnColorsChangedListener { _: WallpaperColors?, _: Int ->
        vm.refreshWallpaper(force = true)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        pendingPhotoWidget = savedInstanceState?.getString(KEY_PENDING_PHOTO)
        StatusBarStore.init(this)
        pendingWidget = savedInstanceState?.let(PendingAppWidget::restore)

        val iconSizePx = (Tokens.IconSize.value * resources.displayMetrics.density).roundToInt()
        val iconLoader = IconLoader(this, iconSizePx, vm.settings)
        icons = iconLoader
        this.iconSizePx = iconSizePx

        setContent {
            val glass by vm.glass.collectAsStateWithLifecycle()
            val settings by vm.settings.state.collectAsStateWithLifecycle()
            val editMode by vm.editMode.collectAsStateWithLifecycle()
            // Not read here: each icon reads its own count from it (see BadgeSource).
            val badgeCounts = NotificationBadgeFeed.counts.collectAsStateWithLifecycle()
            val badges = remember(badgeCounts, settings.badges) {
                settings.badges.takeIf { it != BadgeStyle.Off }?.let { BadgeSource(badgeCounts, it) }
            }
            // Like iOS27L, the status bar makes room for the Edit/Done bar in edit mode.
            LaunchedEffect(editMode) {
                applyStatusBarVisibility(hidden = editMode)
            }
            CompositionLocalProvider(
                LocalSettingsState provides settings,
                LocalGlassSource provides glass.takeIf { settings.blurEffect },
                LocalIconLoader provides iconLoader,
                LocalBadges provides badges,
                LocalGlassStyle provides settings.glassStyle(),
                LocalGlassLight provides glassLight,
                LocalAccent provides AccentColors.resolve(settings.dynamicColor, iconLoader.wallpaperColor),
            ) {
                androidx.compose.foundation.layout.Box {
                    HomeScreen(vm, actions = this@MainActivity)
                    if (showWakeLockScreen && settings.showLockScreenAfterSleep) {
                        HoislyLockScreen(onDismiss = { dismissHoislyLockScreen() }, onCamera = { openCameraFromLockScreen() })
                    }
                }
            }
        }
        WallpaperManager.getInstance(this)
            .addOnColorsChangedListener(wallpaperListener, Handler(Looper.getMainLooper()))
        trackTilt()
    }

    /**
     * Runs the tilt sensor only while the home screen is started (stopped when another app
     * covers it or the screen turns off) and Liquid glass with a wallpaper to refract asks for it.
     */
    private fun trackTilt() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    combine(vm.settings.state, vm.glass) { settings, source ->
                        val mode = resolveGlassMode(settings.glassMode, Build.VERSION.SDK_INT, isLiquidGlassReady())
                        Triple(mode, settings.glassTiltLight, settings.blurEffect && source != null)
                    }.distinctUntilChanged().collect { (mode, tilt, hasGlass) ->
                        tiltSensor.update(mode, tilt, visible = hasGlass)
                    }
                } finally {
                    tiltSensor.update(GlassMode.Frosted, tiltEnabled = false, visible = false)
                }
            }
        }
    }

    /** True after another activity covered the home screen; used for the resume animation. */
    private var leftHome = false
    private val homeIntents = HomeIntentRouter()
    private lateinit var icons: IconLoader
    private var iconSizePx: Int = 0

    override fun onResume() {
        super.onResume()
        applyStatusBarVisibility(hidden = vm.editMode.value)
        // Android 12: the language was changed in settings while the home screen was paused.
        if (AppLanguage.isStale(this)) {
            recreate()
            return
        }
        // Cheap when nothing changed; also picks up a freshly granted file access permission.
        vm.refreshWallpaper()
        vm.refreshWidgets()
        if (screenWasTurnedOff) {
            screenWasTurnedOff = false
            if (vm.settings.state.value.showLockScreenAfterSleep) {
                // Show Hoisly above the swipe-only keyguard as soon as the launcher resumes.
                setShowWhenLocked(true)
                showWakeLockScreen = true
            } else {
                showWakeLockScreen = false
                setShowWhenLocked(false)
            }
        }
        if (homeIntents.resume()) vm.returnToHome.tryEmit(Unit)
        var closing = false
        var returning = false
        if (leftHome) {
            leftHome = false
            returning = true
            closing = vm.beginAppClosing()
        }
        // The surface shrinks into the icon; otherwise the icons zoom into place.
        if (returning && !closing) vm.returnedHome.tryEmit(Unit)
    }

    /** The secure camera opens above the keyguard; devices without one get the normal camera. */
    private fun openCameraFromLockScreen() {
        val secure = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(secure) }.onFailure {
            runCatching {
                startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /**
     * Dismiss the decorative Hoisly surface, then ask Android to dismiss its own keyguard.
     * On swipe-only devices this completes without credentials; secure keyguards still require
     * the system's normal PIN, pattern, password, or biometric flow.
     */
    private fun dismissHoislyLockScreen() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                showWakeLockScreen = false
                setShowWhenLocked(false)
            }

            override fun onDismissCancelled() {
                showWakeLockScreen = false
                setShowWhenLocked(false)
            }

            override fun onDismissError() {
                showWakeLockScreen = false
                setShowWhenLocked(false)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!screenReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
        }
        // Widgets of other apps only update while their host listens.
        vm.appWidgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        vm.appWidgets.stopListening()
        leftHome = true
        homeIntents.stop()
    }

    private fun applyStatusBarVisibility(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            applyStatusBarVisibility(hidden = vm.editMode.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        // React only when Home is pressed while the home screen is already showing, like iOS:
        // returning from an app keeps the page you left. The activity is paused during
        // onNewIntent, so the lifecycle can't tell us that; window focus can (same as Launcher3).
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        if (homeIntents.intent(isHome, hasWindowFocus(), broughtToFront)) {
            vm.homePressed.tryEmit(Unit)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_PHOTO, pendingPhotoWidget)
        pendingWidget?.save(outState)
    }

    override fun onDestroy() {
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener)
        super.onDestroy()
    }

    // region LauncherActions

    private fun openExternal(intent: Intent): Result<Unit> = runCatching {
        startActivity(intent)
        homeIntents.appLaunched()
    }

    override fun webSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        openExternal(intent)
            .onFailure { openExternal(Intent(Intent.ACTION_VIEW, ("https://duckduckgo.com/?q=" + Uri.encode(query)).toUri())) }
    }

    override fun launch(app: AppInfo, bounds: Rect) {
        // A surface is still growing or shrinking: ignore the extra tap.
        if (vm.appTransition.value != null) return
        vm.launchStats.recordLaunch(app.key)
        val source = bounds.toAndroidRect()
        val animate = vm.settings.state.value.openCloseAnimation
        if (animate && !bounds.isEmpty && animatorScale() > 0f) {
            launchThroughSurface(app, bounds, source)
            return
        }
        // If there is no icon rectangle (for example a search command), do not ask Android
        // to run its own scale-up animation. Icon launches use the launcher-drawn surface below;
        // launches without an origin should simply appear without a second system animation.
        val options = ActivityOptions.makeCustomAnimation(this, 0, 0)
            .withoutSplashAnimation()
            .toBundle()
        startApp(app, source, options)
    }

    /**
     * The icon grows into a rounded surface that fills the screen while the home screen blurs;
     * the app starts shortly before it is full and fades in over it. Returning shrinks the
     * surface back into the icon ([onResume]).
     */
    private fun launchThroughSurface(app: AppInfo, bounds: Rect, source: android.graphics.Rect) {
        vm.beginAppTransition(bounds, icons.cached(app, iconSizePx))
        lifecycleScope.launch {
            delay((Motion.APP_LEAD_MS * animatorScale()).toLong())
            // The icon-origin surface is the only opening animation. Explicitly disable
            // the app-window transition (0, 0) so Android cannot add a second animation on top.
            // The solid-color splash style also prevents a centered app icon from scaling/fading.
            val options = ActivityOptions.makeCustomAnimation(this@MainActivity, 0, 0)
                .withoutSplashAnimation()
                .toBundle()
            if (!startApp(app, source, options)) {
                vm.beginAppClosing()
                return@launch
            }
            // Still in front after this long: the app did not open, so the surface goes home again.
            delay(Motion.APP_START_TIMEOUT_MS)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.beginAppClosing()
        }
    }

    /** Android 13+: solid-color splash, so the system draws no centered app icon that animates. */
    private fun ActivityOptions.withoutSplashAnimation(): ActivityOptions = apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            splashScreenStyle = android.window.SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR
        }
    }

    private fun startApp(app: AppInfo, source: android.graphics.Rect, options: Bundle?): Boolean = runCatching {
        if (!launcherApps.isActivityEnabled(app.component, app.user)) return@runCatching false
        launcherApps.startMainActivity(app.component, app.user, source, options)
        if (app.packageName != packageName) homeIntents.appLaunched()
        true
    }.getOrDefault(false)

    /** The system animation speed (Developer options); 0 means animations are off. */
    private fun animatorScale(): Float = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    override fun launchShortcut(shortcut: ShortcutInfo, bounds: Rect) {
        runCatching { launcherApps.startShortcut(shortcut, bounds.toAndroidRect(), null) }.onSuccess { homeIntents.appLaunched() }
    }

    override fun openAppInfo(app: AppInfo) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    override fun uninstall(app: AppInfo) {
        startActivity(Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri()))
    }

    override fun requestGlassAccess() {
        // Match ZFL's wallpaper access route. Android 13+/14+ WallpaperManager image access
        // requires MANAGE_EXTERNAL_STORAGE; a photo-picker grant cannot replace this permission.
        val appAccess = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(appAccess) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun openWallpaperPicker() {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null)) }
    }

    override fun openClock() {
        // Opening ACTION_SHOW_ALARMS directly needs the SET_ALARM permission on some ROMs (MIUI),
        // so find the clock app that handles it and start it like from its icon.
        val clockPackage = packageManager.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)?.activityInfo?.packageName
        val launch = clockPackage?.let { packageManager.getLaunchIntentForPackage(it) } ?: return
        openExternal(launch)
    }

    override fun openCalendar() {
        // The system calendar's "today" view, whichever calendar app handles it.
        val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()
        openExternal(Intent(Intent.ACTION_VIEW, today))
    }

    override fun requestCalendarPermission() {
        requestCalendar.launch(Manifest.permission.READ_CALENDAR)
    }

    override fun openBatterySettings() {
        runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) } }
    }

    override fun openWeatherSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_WEATHER))
    }

    override fun pickWidgetPhoto(widgetId: String) {
        pendingPhotoWidget = widgetId
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun addAppWidget(info: AppWidgetProviderInfo, target: WidgetTarget) {
        val widgets = vm.appWidgets
        val pending = PendingAppWidget(widgets.allocateId(), info.provider.flattenToString(), target)
        pendingWidget = pending
        if (widgets.manager.bindAppWidgetIdIfAllowed(pending.appWidgetId, info.profile, info.provider, null)) {
            configureOrPlace(pending)
        } else {
            // Android asks the user once whether this launcher may create widgets.
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.appWidgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    /** Opens the widget's own setup screen when it has a required one, then places it. */
    private fun configureOrPlace(pending: PendingAppWidget) {
        val info = vm.appWidgets.info(pending.appWidgetId) ?: return cancelWidget(pending)
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure != null && !optional) {
            // The system starts the (possibly non-exported) configure activity for us, through a
            // PendingIntent; Android 14 blocks that start unless we, the visible sender, allow it.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            runCatching {
                vm.appWidgets.host.startAppWidgetConfigureActivityForResult(this, pending.appWidgetId, 0, REQUEST_CONFIGURE_WIDGET, options)
            }.onFailure { cancelWidget(pending) }
            return
        }
        placeWidget(pending)
    }

    private fun placeWidget(pending: PendingAppWidget) {
        pendingWidget = null
        val info = vm.appWidgets.info(pending.appWidgetId) ?: return cancelWidget(pending)
        vm.addAppWidget(pending.appWidgetId, info, pending.target)
    }

    private fun cancelWidget(pending: PendingAppWidget) {
        pendingWidget = null
        vm.appWidgets.deleteId(pending.appWidgetId)
    }

    /** Result of the widget configure activity (started by AppWidgetHost, which only offers the request-code API). */
    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult reports through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_WIDGET) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) placeWidget(pending) else cancelWidget(pending)
    }

    // endregion

    private companion object {
        const val REQUEST_CONFIGURE_WIDGET = 41
        const val KEY_PENDING_PHOTO = "pending_photo_widget"
    }

    private fun Rect.toAndroidRect(): android.graphics.Rect =
        android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
}
