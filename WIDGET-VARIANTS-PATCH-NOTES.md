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
