import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
    alias(libs.plugins.signing)
}

android {
    compileSdk = 34
    namespace = "com.lingohub.android.cdn"

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk7)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    api(libs.viewpump)

    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit2.kotlinx.serialization.converter)

    // Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Test dependencies
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlin.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test.v173)
    testImplementation(libs.kluent.android.v159)
    // Android testing dependencies
    androidTestImplementation(libs.androidx.junit.v112)
    androidTestImplementation(libs.androidx.espresso.core.v330)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

              groupId = "com.lingohub.android.cdn"
              version = rootProject.file("version.properties").inputStream().use {
                  val props = Properties()
                  props.load(it)
                  props.getProperty("VERSION_NAME") ?: "0.0.0"
              }

              pom {
                name.set("Lingohub Android CDN SDK")
                description.set("A lightweight Android SDK that retrieves up-to-date translations from Lingohub, enabling real-time multilingual content delivery without requiring app updates.")
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
                        name.set("lingohub GmbH")
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
      }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}