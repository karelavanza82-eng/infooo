package com.hoisly.launcher.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.produceState
import com.hoisly.launcher.ui.widgets.WidgetProviderGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.LauncherViewModel
import com.hoisly.launcher.data.AppInfo
import com.hoisly.launcher.data.FolderEntry
import com.hoisly.launcher.data.HomeState
import com.hoisly.launcher.data.AppLibraryState
import com.hoisly.launcher.data.WidgetKind
import com.hoisly.launcher.data.WidgetSize
import com.hoisly.launcher.data.WidgetVisualStyle
import com.hoisly.launcher.data.PageWidgetEntry
import com.hoisly.launcher.ui.library.CategoryChooser
import com.hoisly.launcher.ui.library.CategoryOverlay
import com.hoisly.launcher.ui.library.LibraryCallbacks
import com.hoisly.launcher.ui.menu.ContextMenuOverlay
import com.hoisly.launcher.ui.menu.WidgetMenuActions
import com.hoisly.launcher.ui.search.SearchCallbacks
import com.hoisly.launcher.ui.search.SearchOverlay
import com.hoisly.launcher.ui.widgets.WidgetGallery
import com.hoisly.launcher.ui.theme.Motion

/**
 * Everything drawn above the home screen pages: the edit-mode bar, an open folder or App
 * Library category, search, the long-press menu, the widget gallery and dialogs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeOverlays(
    vm: LauncherViewModel,
    actions: LauncherActions,
    state: HomeState,
    drag: DragController,
    library: AppLibraryState?,
    visibleApps: List<AppInfo>,
    folderIcons: HomeIconCallbacks,
    libraryCallbacks: LibraryCallbacks,
    searchCallbacks: SearchCallbacks,
    widgetMenu: WidgetMenuActions,
    onAddWidget: (WidgetKind, WidgetSize, WidgetVisualStyle) -> Unit,
    onAddAppWidget: (AppWidgetProviderInfo) -> Unit,
    onAddStack: (WidgetSize) -> Unit,
) {
    val menu by vm.menu.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val openFolder by vm.openFolder.collectAsStateWithLifecycle()
    val removeRequest by vm.removeRequest.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val openGroupId by vm.library.openGroup.collectAsStateWithLifecycle()
    val chooserApp by vm.library.choosingCategory.collectAsStateWithLifecycle()
    val gallery by vm.widgetGallery.collectAsStateWithLifecycle()
    val widgetManager by vm.widgetManager.collectAsStateWithLifecycle()
    val editingStack by vm.stacks.editing.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = editMode,
        enter = fadeIn(Motion.fade()),
        exit = fadeOut(Motion.fade()),
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(top = 4.dp),
    ) {
        EditTopBar(
            onDone = vm::exitEditMode,
            onAddWidget = vm::openWidgetGallery,
            onCustomize = actions::openSettings,
            onWallpapers = actions::openWallpaperPicker,
            onManageWidgets = vm::openWidgetManager,
            onRemoveEmptyPages = vm::removeEmptyPages,
            onSaveSetup = vm::openHomeSetups,
        )
    }

    val setupDialog by vm.homeSetupDialog.collectAsStateWithLifecycle()
    val setups by vm.homeSetups.collectAsStateWithLifecycle()
    if (setupDialog) {
        HomeSetupDialog(
            setups = setups,
            onSave = vm::saveHomeSetup,
            onApply = vm::applyHomeSetup,
            onDelete = vm::deleteHomeSetup,
            onClose = vm::closeHomeSetups,
        )
    }

    FolderOverlay(
        open = openFolder,
        folder = openFolder?.let { state.findEntry(it.id) as? FolderEntry },
        editMode = editMode,
        drag = drag,
        iconCallbacks = folderIcons,
        onClose = vm::closeFolder,
        onRename = vm::renameFolder,
    )

    // Looked up by id each time: a rename shows at once; a group that was deleted or lost its
    // last app (hidden, uninstalled) closes.
    val openGroup = openGroupId?.let { id -> library?.cards?.firstOrNull { it.id == id } }
    if (openGroupId != null && library != null && openGroup == null) LaunchedEffect(openGroupId) { vm.library.close() }
    CategoryOverlay(group = openGroup, callbacks = libraryCallbacks, onClose = vm.library::close, drag = drag)

    SearchOverlay(
        mode = search,
        apps = visibleApps,
        suggestions = vm.launchStats.suggestions(visibleApps, 8),
        callbacks = searchCallbacks,
    )

    // Apps with widgets, read (off the main thread) each time the gallery opens.
    val appWidgets by produceState(emptyList<WidgetProviderGroup>(), gallery) {
        if (gallery) value = withContext(Dispatchers.IO) { vm.appWidgets.providers() }
    }
    WidgetGallery(
        visible = gallery,
        appWidgets = appWidgets,
        onAdd = onAddWidget,
        onAddApp = onAddAppWidget,
        onAddStack = onAddStack,
        onClose = vm::closeWidgetGallery,
    )

    ManageWidgetsDialog(
        visible = widgetManager,
        widgets = (state.pages.flatten() + state.today).filterIsInstance<PageWidgetEntry>().flattenForManagement(),
        onRemove = vm::removeWidget,
        onClose = vm::closeWidgetManager,
    )

    ContextMenuOverlay(
        target = menu,
        dockIsFull = vm.dockIsFull(),
        repository = vm.repository,
        onDismiss = vm::closeMenu,
        onToggleDock = vm::toggleDock,
        onHide = vm::hideApp,
        onEditHome = vm::enterEditMode,
        onRenameFolder = { entry, bounds -> vm.openFolder(entry.id, bounds, renaming = true) },
        onAddToHome = vm::addToHome,
        library = remember(vm) { libraryMenuActions(vm) },
        widgetActions = widgetMenu,
        actions = actions,
    )

    CategoryChooser(
        app = chooserApp,
        library = library,
        onAssign = vm.library::assign,
        onDismiss = vm.library::closeCategoryChooser,
    )

    StackEditor(
        stack = editingStack?.let(state::findStack),
        actions = remember(vm) { stackEditorActions(vm) },
    )

    removeRequest?.let { app ->
        RemoveAppDialog(
            app = app,
            onRemoveFromHome = { vm.removeFromHome(app) },
            onUninstall = {
                vm.removeRequest.value = null
                actions.uninstall(app)
            },
            onDismiss = { vm.removeRequest.value = null },
        )
    }
}


@Composable
private fun HomeSetupDialog(
    setups: List<com.hoisly.launcher.data.HomeSetupStore.Setup>,
    onSave: (String) -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Simpan Setup Home Screen") },
        text = {
            Column {
                Text("Simpan susunan halaman, folder, widget, dock, dan grid. Setup tersimpan otomatis di Hoisly.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama setup") },
                    placeholder = { Text("Contoh: Liquid Glass") },
                    singleLine = true,
                )
                TextButton(
                    onClick = { if (name.isNotBlank()) { onSave(name.trim()); name = "" } },
                    enabled = name.isNotBlank(),
                ) { Text("Simpan setup ini") }
                if (setups.isNotEmpty()) {
                    Text("Setup tersimpan", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    LazyColumn {
                        items(setups, key = { it.id }) { setup ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onApply(setup.id) }, modifier = Modifier.weight(1f)) {
                                    Text(setup.name)
                                }
                                TextButton(onClick = { onDelete(setup.id) }) { Text("Hapus") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } },
    )
}
