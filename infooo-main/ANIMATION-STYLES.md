# Hoisly app-opening animation styles

The Animation settings page now offers eight selectable app-open/app-close styles:

1. **Default Hoisly** (`HOISLY`) — keeps Hoisly's existing icon-to-screen surface transition.
2. **iOS 27** (`IOS27`) — the Launchii Zoom In geometry adapted to Hoisly's tapped icon bounds and home content.
3. **Aura** (`AURA`) — app-colour glow grows from the tapped icon while Home dims.
4. **Shutter** (`SHUTTER`) — staggered horizontal slats expand from the icon row.
5. **Orbit** (`ORBIT`) — six coloured lights orbit from the icon origin toward screen centre.
6. **Anticipate** (`ANTICIPATE`) — a brief pull-back followed by a forward lunge.
7. **Scan** (`SCAN`) — two light rails split from the icon row and reveal a frosted band.
8. **Mist** (`MIST`) — soft coloured clouds move inward from the screen edges.

The Launchii Smooth preset is represented by **Default Hoisly** so the existing Hoisly animation remains available without a duplicate-looking choice. Launchii Zoom In is represented by **iOS 27**. The six distinct Launchii effect presets are included individually, giving eight choices total.

The settings preview uses Hoisly's own `HomePreview`, including the user's current wallpaper, Home layout, dock and installed app icons. The same drawing function is shared by the preview and the live launcher transition so each selected style is demonstrated in the settings screen and used for app opening/closing.

The selected style is stored in Hoisly preferences and survives app restarts. Animation speed scales each style's timing. Notification badge code and listener declarations are retained in this source.
