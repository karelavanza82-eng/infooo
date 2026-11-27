package com.hoisly.launcher.ui.settings

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoisly.launcher.data.AppInfo
import com.hoisly.launcher.data.AppRepository
import com.hoisly.launcher.data.HomeState
import com.hoisly.launcher.data.LayoutRepository
import com.hoisly.launcher.data.LayoutStore
import com.hoisly.launcher.data.buildHomeLayout
import com.hoisly.launcher.data.AppLibraryState
import com.hoisly.launcher.data.AppSnapshot
import com.hoisly.launcher.data.CategoryEdit
import com.hoisly.launcher.data.GroupKind
import com.hoisly.launcher.data.LaunchStats
import com.hoisly.launcher.data.LibraryConfig
import com.hoisly.launcher.data.LibraryIds
import com.hoisly.launcher.data.LibraryTitles
import com.hoisly.launcher.data.createCategory
import com.hoisly.launcher.data.libraryStates
import com.hoisly.launcher.data.renameCategory
import com.hoisly.launcher.data.syncedWith
import com.hoisly.launcher.data.visibleApps
import com.hoisly.launcher.data.GridChange
import com.hoisly.launcher.data.GridSpec
import com.hoisly.launcher.data.LayoutData
import com.hoisly.launcher.data.withGrid
import com.hoisly.launcher.settings.GridAlignment
import com.hoisly.launcher.settings.GridMargins
import com.hoisly.launcher.settings.LauncherSettings
import com.hoisly.launcher.settings.SettingsState
import com.hoisly.launcher.ui.glass.GlassSource
import com.hoisly.launcher.ui.glass.WallpaperBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State shared by all settings screens. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val settings: LauncherSettings = LauncherSettings.get(app)

    /** All installed apps, including hidden ones (the Hidden Apps screen needs them). */
    val repository: AppRepository = AppRepository(app)

    private val layoutStore = LayoutStore(app)

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    /** Sharp wallpaper for the live home screen preview. */
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper

    private val _glass = MutableStateFlow<GlassSource?>(null)
    /** Blurred wallpaper used as the background of sub pages. */
    val glass: StateFlow<GlassSource?> = _glass

    // Declared before init, which loads the layout.
    private val layoutRepository = LayoutRepository.get(app)

    /** The saved Home Screen layout; null until loaded or on a fresh install that never showed it. */
    val layout: StateFlow<LayoutData?> = layoutRepository.layout

    init {
        refreshGlass()
        viewModelScope.launch { layoutRepository.load() }
    }

    /** Reloads wallpaper-based images (e.g. after the user granted access). */
    fun refreshGlass() {
        viewModelScope.launch {
            // The sharp preview doubles as the rim of Liquid glass in the glass preview.
            val sharp = WallpaperBlur.loadPreview(getApplication())
            _wallpaper.value = sharp
            _glass.value = WallpaperBlur.load(getApplication(), sharp)
        }
    }

    /** The layout to show and change: the saved one, or what a fresh install would start with. */
    private fun currentLayout(apps: List<AppInfo>, prefs: SettingsState): LayoutData {
        val snapshot = repository.snapshot.value.takeIf { it.apps === apps } ?: AppSnapshot(apps, emptySet())
        return (layout.value ?: layoutStore.initialLayout(visibleApps(apps, prefs))).syncedWith(snapshot, prefs)
    }

    /** Grid of the saved layout, or the one a fresh install starts with. */
    fun savedGrid(): GridSpec = layout.value?.grid ?: layoutStore.legacyGrid()

    /** The home screen as it is, for previews. */
    fun homePreview(apps: List<AppInfo>, prefs: SettingsState): HomeState = buildHomeLayout(currentLayout(apps, prefs), apps, prefs)

    /**
     * The home screen moved to [grid], for the Screen Grid preview; nothing is saved. Null
     * when an item doesn't fit [grid] ([GridChange.Blocked]).
     */
    fun gridPreview(apps: List<AppInfo>, prefs: SettingsState, grid: GridSpec): HomeState? =
        (currentLayout(apps, prefs).withGrid(grid) as? GridChange.Applied)?.let { buildHomeLayout(it.layout, apps, prefs) }

    /**
     * "Apply" on the Screen Grid page: the grid change is one transformation and one save of
     * the layout (grid and cells in the same file), the margins one settings update.
     * Returns false and changes nothing when an item doesn't fit [grid].
     */
    suspend fun applyGrid(grid: GridSpec, margins: GridMargins, alignment: GridAlignment): Boolean {
        val prefs = settings.state.value
        val change = layoutRepository.changeGrid(grid) { currentLayout(repository.apps.value, prefs) }
        if (change is GridChange.Blocked) return false
        if (margins != prefs.gridMargins || alignment != prefs.gridAlignment) {
            settings.update { it.copy(gridMargins = margins, gridAlignment = alignment) }
        }
        return true
    }

    // region App Library

    private val launchStats = LaunchStats.get(app)

    /** Visible apps with custom labels, for the category and pin editors. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(repository.apps, settings.state.map { it.hiddenApps to it.customLabels }.distinctUntilChanged()) { apps, _ ->
            visibleApps(apps, settings.state.value)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Group names in the UI language; set by the settings activity's composition. */
    val libraryTitles: MutableStateFlow<LibraryTitles?> = MutableStateFlow(null)

    /** The App Library as the launcher shows it, built by the same code. */
    val library: StateFlow<AppLibraryState?> =
        libraryStates(visibleApps, settings.state, launchStats.scores, libraryTitles).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One App Library change, one save; see [LauncherSettings.updateLibrary]. */
    fun editLibrary(transform: (LibraryConfig) -> LibraryConfig) {
        val apps = repository.snapshot.value
        settings.updateLibrary(apps.apps.mapTo(HashSet()) { it.key }, apps.unavailableUsers, transform)
    }

    /** Names a user category may not take: the system groups shown now. */
    private fun reservedNames(): List<String> =
        library.value?.allGroups.orEmpty().filter { it.kind == GroupKind.System }.map { it.title }

    /** Creates an empty category; saved only when the name is accepted. */
    fun createCategory(name: String): CategoryEdit {
        val config = settings.state.value.library
        val id = LibraryIds.newUser(System.currentTimeMillis(), config.categories.map { it.id })
        return config.createCategory(name, id, reservedNames()).also { edit ->
            if (edit is CategoryEdit.Done) editLibrary { edit.config }
        }
    }

    fun renameCategory(id: String, name: String): CategoryEdit =
        settings.state.value.library.renameCategory(id, name, reservedNames()).also { edit ->
            if (edit is CategoryEdit.Done) editLibrary { edit.config }
        }

    // endregion


    override fun onCleared() {
        repository.close()
    }
}

/** Things only the hosting Activity can do. */
interface SettingsActions {
    /** Whether Hoisly is currently the default home app. */
    val isDefaultHome: Boolean
    val versionName: String
    /** Whether the wallpaper can be read (needed for the glass effect). */
    val hasWallpaperAccess: Boolean
    /** Whether Android lets Hoisly ask to become the home app. */
    val isHomeRoleAvailable: Boolean

    fun requestDefaultHome()
    fun requestWallpaperAccess()
    fun requestIslandWindowAccess(): Unit
    /** App info, where Android lifts "restricted settings" for apps installed from a file. */
    fun openIslandAppInfo(): Unit
    fun requestIslandMusicAccess(): Unit
    fun reconnectIsland(): Unit
    /** Switches Hoisly's UI language (`null` = follow the system); recreates the activity. */
    fun setAppLanguage(tag: String?)
    /** "Get Started": marks the onboarding as done and goes home. */
    fun completeOnboarding()
}
