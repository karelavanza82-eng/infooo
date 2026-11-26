# Hoisly + PrismalAGSL 1.0.7 integration status

This source package includes the original Hoisly app and the PrismalAGSL 1.0.7
library as a local Gradle module (`:Prismal`). The app now declares
`implementation(project(":Prismal"))`, and the module uses the same AGP,
Kotlin Compose plugin, Compose BOM and compile SDK as Hoisly.

## Platform tiers from Prismal source
- API 25–30: Legacy backdrop sampling / frosted treatment.
- API 31–32: RenderEffect-based standard pipeline.
- API 33+: AGSL liquid glass pipeline.

Hoisly's existing glass UI implementation has NOT been replaced or automatically
migrated to Prismal components in this integration package. The Prismal library
is included and linked as a module, but connecting its backdrop/layer APIs to
Hoisly's existing `glass()` surfaces requires UI-specific migration and visual
regression testing. This is intentionally not claimed as complete.

The original Hoisly `minSdk` remains 28. Existing app sources and resources are
preserved. No APK build or device test was performed in this environment.
