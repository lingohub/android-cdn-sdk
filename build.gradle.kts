plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    alias(libs.plugins.signing)
}

group = "com.lingohub.android.cdn"
version = "1.0.0"

android {
    namespace = "com.lingohub.android.cdn"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

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