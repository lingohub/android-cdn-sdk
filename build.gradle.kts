// Top-level build file for the project
plugins {
    // Apply plugins to subprojects but not to the root project.
    // Kotlin itself is built into AGP 9+ (no org.jetbrains.kotlin.android needed);
    // the Kotlin compiler-plugin wrappers below pin the Kotlin version (see libs.versions.toml).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
