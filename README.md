# Lingohub Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.lingohub/android-cdn-sdk?style=flat-square)](https://central.sonatype.com/artifact/com.lingohub/android-cdn-sdk)
[![License](https://img.shields.io/github/license/lingohub/android-cdn-sdk?style=flat-square)](./LICENSE)

The Lingohub Android SDK provides seamless integration of Lingohub's localization services into your Android applications. It supports both traditional XML-based resources and Jetpack Compose applications.

## Features

- Real-time translation updates without app releases
- Support for both XML resources and Jetpack Compose
- Automatic locale handling and switching
- Support for string resources, plurals, and string arrays
- Background bundle updates
- Comprehensive logging system
- Configurable environments (Production/Development)

## Requirements

- Android API level 24+
- AndroidX

## Installation

The SDK is available on [Maven Central](https://central.sonatype.com/artifact/com.lingohub/android-cdn-sdk):

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.lingohub:android-cdn-sdk:1.1.0")
}
```

<details>
<summary>Groovy DSL</summary>

```groovy
// build.gradle
dependencies {
    implementation 'com.lingohub:android-cdn-sdk:1.1.0'
}
```

</details>

`mavenCentral()` is part of the default repository setup of every current Android project, so no extra repository configuration is needed.

> **Migrating from JitPack?** Earlier versions were distributed via JitPack under `com.github.lingohub` coordinates. JitPack is no longer supported — switch to the Maven Central coordinates above and remove the `maven { url 'https://jitpack.io' }` repository if nothing else uses it.

## Quick Start

### 1. Configure the SDK in your `Application` class

```kotlin
import android.app.Application
import com.lingohub.android.cdn.core.Lingohub

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure Lingohub with your distribution API key (starts with "lh-cdn_")
        Lingohub.configure(
            context = this,
            apiKey = "your-api-key"
        )

        // Fetch the latest translations
        Lingohub.update()
    }
}
```

### 2. Wrap your Activities

Lingohub replaces strings by wrapping the Activity context, so every Activity — XML **and** Compose based — needs the Lingohub `AppCompatDelegate`. The easiest way is a `BaseActivity` that all your Activities extend:

```kotlin
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lingohub.android.cdn.core.Lingohub

abstract class BaseActivity : AppCompatActivity() {

    private val lingohubDelegate: AppCompatDelegate by lazy {
        Lingohub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingohubDelegate
    }
}
```

```kotlin
class MainActivity : BaseActivity() {
    // Your activity code
}
```

Without the delegate, translations still download but are only applied from the next app start.

## Usage

### String resources

Access string resources exactly as you normally would:

```kotlin
// XML / programmatic
context.getString(R.string.your_string)

// Jetpack Compose
Text(text = stringResource(R.string.your_string))
```

Plurals (`getQuantityString`) and string arrays (`getStringArray`) are supported the same way.

### Switching languages

Change the app's language at runtime:

```kotlin
import java.util.Locale

Lingohub.setLocale(Locale.GERMAN)
```

Already-rendered screens don't re-render themselves: recreate the Activity (or drive your Compose UI from a locale state, as shown in the [sample app](sample/src/main/java/com/lingohub/android/cdn/example/MainActivity.kt)) to see the change immediately.

## Advanced Configuration

### Optional parameters

```kotlin
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.LingohubLogLevel

Lingohub.configure(
    context = this,
    apiKey = "your-api-key",
    environment = Environment.PRODUCTION,   // optional, defaults to PRODUCTION
    logLevel = LingohubLogLevel.FULL        // optional, defaults to NONE — avoid FULL in production
)
```

| Parameter     | Values                                                                                         | Default                  |
| ------------- | ---------------------------------------------------------------------------------------------- | ------------------------ |
| `environment` | `Environment.PRODUCTION`, `Environment.STAGING`, `Environment.DEVELOPMENT`, `Environment.TEST` | `Environment.PRODUCTION` |
| `logLevel`    | `LingohubLogLevel.NONE`, `LingohubLogLevel.FULL`                                               | `LingohubLogLevel.NONE`  |

### Update notifications

Implement `LingohubUpdateListener` to react when a new translation bundle has been downloaded. Recreating the Activity is the simplest way to apply updates immediately — make sure your Activity state survives recreation:

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lingohub.android.cdn.core.Lingohub
import com.lingohub.android.cdn.core.LingohubUpdateListener

abstract class BaseActivity : AppCompatActivity(), LingohubUpdateListener {

    private val lingohubDelegate: AppCompatDelegate by lazy {
        Lingohub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingohubDelegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lingohub.addUpdateListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Lingohub.removeUpdateListener(this)
    }

    override fun onUpdate() {
        // Recreate the activity to reload all resources with the new translations.
        // If you skip this, the new strings are applied on the next app start.
        runOnUiThread {
            recreate()
        }
    }

    override fun onFailure(throwable: Throwable) {
        // Handle failure if needed
    }
}
```

### Reducing network requests

`Lingohub.update()` performs a network request each time it is called. If you don't need instant updates, check only periodically — for example once a day:

```kotlin
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class CacheManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lingohub_prefs", Context.MODE_PRIVATE)

    fun shouldUpdate(): Boolean {
        val lastFetchTime = prefs.getLong("last_fetch_time", 0)
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastFetchTime >= oneDayInMillis
    }

    fun updateLastFetchTime() {
        prefs.edit {
            putLong("last_fetch_time", System.currentTimeMillis())
        }
    }
}
```

```kotlin
class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Lingohub.configure(
            context = this,
            apiKey = "your-api-key"
        )

        val cacheManager = CacheManager(this)
        if (cacheManager.shouldUpdate()) {
            Lingohub.update()
            cacheManager.updateLastFetchTime()
        }
    }
}
```

## R8 / ProGuard

No configuration needed — the SDK ships its consumer rules inside the AAR.

## Data collected by the SDK

When checking for translation updates, the SDK sends the following to Lingohub's CDN; include it in your Play Console *Data safety* declaration as applicable:

| Field                | Content                                          |
| -------------------- | ------------------------------------------------ |
| `clientUser`         | The device's `ANDROID_ID` (device identifier)    |
| `clientVersion`      | Your app's version name                          |
| `clientLanguageCode` | The current app language                         |
| `clientAgent`        | SDK name and version                             |

## Sample app

The [`sample`](sample/) module is a small Compose app showing configuration, locale switching, and update handling end to end.

## Support

For bug reports and feature requests, please open an issue on GitHub.

## License

Apache License Version 2.0, January 2004. More info in the [`LICENSE`](LICENSE) file.
