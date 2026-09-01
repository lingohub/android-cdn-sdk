import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

val sdkVersion: String = rootProject.file("version.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }.getProperty("VERSION_NAME") ?: "0.0.0"
}

android {
    compileSdk = 36
    namespace = "com.lingohub.android.cdn"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION_NAME", "\"$sdkVersion\"")
    }

    buildTypes {
        release {
            // Library AARs are published unminified; consumers' R8 shrinks them
            // using the consumer rules shipped in the AAR.
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        // Built-in Kotlin derives jvmTarget from targetCompatibility.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // AppCompat types (AppCompatDelegate, ComponentActivity) appear in the
    // public API, so consumers need it on their compile classpath.
    api(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ViewPump is an implementation detail; no public signature exposes it.
    implementation(libs.viewpump)

    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    // Test dependencies
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android testing dependencies
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral()

    // Sign when a key is provided (in CI via ORG_GRADLE_PROJECT_signingInMemoryKey);
    // local builds without a key skip signing so publishToMavenLocal keeps working.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates("com.lingohub", "android-cdn-sdk", sdkVersion)

    pom {
        name.set("LingoHub Android CDN SDK")
        description.set("A lightweight Android SDK that retrieves up-to-date translations from LingoHub, enabling real-time multilingual content delivery without requiring app updates.")
        url.set("https://github.com/lingohub/android-cdn-sdk")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("lingohub")
                name.set("LingoHub GmbH")
                email.set("office@lingohub.com")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/lingohub/android-cdn-sdk.git")
            developerConnection.set("scm:git:git@github.com:lingohub/android-cdn-sdk.git")
            url.set("https://github.com/lingohub/android-cdn-sdk")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
