# Release build configuration changes

- The `release` build type remains non-debuggable (`isDebuggable = false`).
- Release R8 minification and resource shrinking remain enabled.
- Release builds no longer fall back to the Android debug signing key.
- If all `HOISLY_KEYSTORE_FILE`, `HOISLY_KEYSTORE_PASSWORD`, `HOISLY_KEY_ALIAS`, and `HOISLY_KEY_PASSWORD` variables are provided, Gradle uses that release keystore. If none are provided, the release output is unsigned and can be signed separately. Supplying only some variables is a configuration error.
- Kotlin null-check removal was removed from the R8 rules to preserve runtime boundary checks.

No APK/AAB build was run as requested.
