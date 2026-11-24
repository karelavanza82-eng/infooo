# Project-specific R8 rules. AndroidX, Compose and Kotlin ship their own consumer rules;
# the app itself uses no reflection or name-based loading, so nothing extra is kept.

# Keep AGP's aggressive class repackaging and access widening explicit.
-repackageclasses
-allowaccessmodification

# Keep Kotlin null-check behavior intact in release builds. Removing checks can
# move failures deeper into runtime code and make release-only defects harder to diagnose.
