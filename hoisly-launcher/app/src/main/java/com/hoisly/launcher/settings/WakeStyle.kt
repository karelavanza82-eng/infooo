package com.hoisly.launcher.settings

/** How the icons arrive after the phone was unlocked and the home screen is shown again. */
enum class WakeStyle {
    Off,

    /**
     * iOS 27 style: icons fly in from the top and from both sides with a springy overshoot, so
     * they bounce into place one after another.
     */
    Bounce,

    /** Every icon grows from small to full size, one after another. */
    Pop,

    /** Icons drop in from above the screen. */
    Top,

    /** Icons slide in from the left and right edges, alternating. */
    Side,
}
