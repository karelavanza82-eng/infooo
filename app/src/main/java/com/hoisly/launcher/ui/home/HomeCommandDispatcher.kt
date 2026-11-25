package com.hoisly.launcher.ui.home

import com.hoisly.launcher.settings.AppTarget
import com.hoisly.launcher.settings.HomeAction
import com.hoisly.launcher.settings.HomeReturn

/** Actual execution boundary, also used by JVM tests with observable command calls. */
interface HomeCommands {
    fun search()
    fun library()
    fun settings()
    fun mainPage()
    fun launch(target: AppTarget)
    /** Panels default to nothing so a host without them (the routing test) stays valid. */
    fun notificationCenter() {}
    /** Closes the existing top state, returning true if it handled Home. */
    fun closeTop(): Boolean
}

class HomeCommandDispatcher(private val commands: HomeCommands) {
    fun execute(action: HomeAction, libraryEnabled: Boolean, blocked: Boolean = false) {
        if (blocked) return
        when (action) {
            HomeAction.None -> Unit
            HomeAction.Search -> commands.search()
            HomeAction.AppLibrary -> if (libraryEnabled) commands.library()
            HomeAction.Settings -> commands.settings()
            HomeAction.NotificationCenter -> commands.notificationCenter()
            HomeAction.MainPage -> commands.mainPage()
            is HomeAction.OpenApp -> commands.launch(action.target)
        }
    }

    fun home(action: HomeAction, libraryEnabled: Boolean, dragging: Boolean) {
        if (commands.closeTop() || dragging) return
        execute(action, libraryEnabled)
    }

    fun returned(policy: HomeReturn) {
        if (policy == HomeReturn.MainPage) commands.mainPage()
    }
}
