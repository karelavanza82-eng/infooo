# Hoisly: Home Setups and icon-pack fallback update

## Home Screen Setups
- Adds **Simpan Setup** as the sixth action in the Edit Home Screen menu.
- Saves named snapshots privately under the app's internal `filesDir/home_setups` folder.
- A snapshot contains the Home Screen pages, folders, dock, widgets, grid, cell placement, and related layout metadata.
- Users can save, apply, overwrite by reusing a name, and delete snapshots in Hoisly. No user-facing JSON export/download is used.

## Third-party icon packs
- Keeps the existing standard `appfilter.xml` icon-pack mapping support.
- When a third-party pack is selected, apps without a mapping now fall back to their own launcher icon rather than Hoisly's bundled iOS monochrome icon pack. This avoids unrelated white/embossed substitute icons for unmapped apps.
- Icon packs must expose standard ADW/Nova-compatible `appfilter.xml` mappings to be applied automatically; unsupported proprietary pack formats are not guaranteed.

## Verification
- Source changes were inspected.
- Gradle compilation could not be run in this environment because the Gradle distribution host (`services.gradle.org`) was unreachable (`UnknownHostException`).
- Run GitHub Actions to verify Kotlin compilation and APK packaging before installing.
- Third-party pack drawables are rendered directly on a transparent canvas (without the legacy-icon white plate) and their authored colors/transparency are preserved. This specifically prevents transparent Liquid Glass PNG icons from becoming white rounded-square tiles.
