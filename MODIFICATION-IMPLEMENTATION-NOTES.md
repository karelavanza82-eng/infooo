# Hoisly integration notes

## CleanBar integration
- CleanBar is integrated into the existing Hoisly Status Bar settings screen; no separate CleanBar activity or branding is added.
- The `Hide System Status Bar` switch uses the Shizuku binder already provided by Hoisly.
- The controller writes Android's global `policy_control` immersive status policy, so this is not limited to Hoisly's Activity window.
- The prior `global policy_control` value is backed up before enabling and restored when disabling.
- Shizuku availability, permission state, permission request, command failure, and post-write verification are surfaced in the settings UI.

## Notification Pill
- The existing drawing node, colors, typeface, animation, sizing ratios, and placement are retained.
- The listener now stores a cumulative per-app `Long` total in private SharedPreferences.
- A notification key is counted once while active; updates with the same key do not increment it. Removal only clears the active-key deduplication entry and never decrements the total.
- Counts survive app process death and device restart. The value is formatted with `id-ID` grouping (for example `1.000`) only at draw time.
- The badge width continues to be measured from the rendered text, so larger values expand the existing pill.

## Verification
- Source edits and archive creation completed.
- A local Gradle compile was attempted, but the wrapper could not download Gradle 9.8.1 because this environment has no DNS/network access to `services.gradle.org`. Therefore this archive is **not claimed as build-verified**. Run the repository's GitHub Actions workflow to perform the actual build.
