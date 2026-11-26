# Hiosly widget visual variants patch

This patch adds a per-widget appearance choice to the built-in widget gallery:
- Light
- Dark
- Liquid Glass

The selected appearance is saved in the layout JSON (`visualStyle`) and restored when the launcher reloads the layout. Existing layouts without that field default to Dark, preserving backward compatibility.

Liquid Glass requests `GlassMode.Liquid` through Hiosly's existing `LocalGlassStyle` and existing `glass()` modifier / `GlassNode` / AGSL shader path. It uses a very low tint alpha so the sampled wallpaper remains visible. No replacement shader was introduced.

The gallery lets the user switch the appearance while previewing a widget, before pressing Add Widget. Each newly added widget stores its own selection.

The Android SDK values in `app/build.gradle.kts` are intentionally unchanged.

Build status: not verified. The Gradle wrapper could not download Gradle 9.8.1 in this environment because network DNS access to services.gradle.org was unavailable. Please run the project's Gradle build in the user's Android/GitHub Actions environment before installing.

## Empty-page widget recovery and status-bar airplane alignment

- Added a fourth Edit menu entry, **Manage Widgets**, so widgets can be removed from a central list even after the last widget's contextual menu is no longer available.
- The manager lists widgets on the current launcher layout (home pages and Today), removes built-in widgets, hosted Android widgets, and Smart Stacks through the existing cleanup path, and shows an empty state when none remain.
- Changed the custom status-bar airplane glyph rotation from 45 degrees to 90 degrees so the plane is horizontal and points right, matching the supplied reference.
- Android does not allow a third-party launcher to replace the system status bar globally. Hoisly's custom status-bar drawing can be aligned visually within its own supported overlay, but exact pixel-for-pixel copying of iOS system icons across Android devices cannot be guaranteed.
- Build validation remains pending because the Gradle 9.8.1 distribution could not be downloaded in this environment (DNS/network unavailable).
