// Top-level build file for the project
plugins {
    // Apply plugins to subprojects but not to the root project
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

val versionProps = java.util.Properties()
rootProject.file("version.properties").inputStream().use { versionProps.load(it) }

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("SONATYPE_USERNAME") ?: findProperty("SONATYPE_USERNAME") as String? ?: "")
            password.set(System.getenv("SONATYPE_PASSWORD") ?: findProperty("SONATYPE_PASSWORD") as String? ?: "")
        }
    }
}

// Common configurations for all projects
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}