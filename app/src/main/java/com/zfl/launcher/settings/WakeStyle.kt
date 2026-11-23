package com.zfl.launcher.settings

/** How the icons arrive after the display was off and the home screen is shown again. */
enum class WakeStyle {
    Off,

    /** Every icon grows from small to full size, one after another. */
    Pop,

    /** Icons drop in from above the screen. */
    Top,

    /** Icons slide in from the left and right edges, alternating. */
    Side,
}
