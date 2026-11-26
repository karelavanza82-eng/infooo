# Hoisly animation preview update

This update changes only the animation settings preview UI.

- The preview is animated in-app (not a bundled Launchii recording).
- It uses Hoisly's own `HomePreview`, the saved Hoisly home layout, installed app icons, and wallpaper loaded by `SettingsViewModel`.
- The two selectable styles remain `Default Hoisly` and `iOS 27`.
- Selecting a style changes the live looping preview.
- The existing Hoisly/Launchii app open-close motion integration and notification badge source are retained.

Build verification: not run in this environment. Run the repository GitHub Actions build before publishing.
