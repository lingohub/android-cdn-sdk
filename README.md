# LingoHub Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.lingohub/android-cdn-sdk?style=flat-square)](https://central.sonatype.com/artifact/com.lingohub/android-cdn-sdk)
[![License](https://img.shields.io/github/license/lingohub/android-cdn-sdk?style=flat-square)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20API%2024%2B-brightgreen?style=flat-square)](#requirements)

A Kotlin SDK for over-the-air (OTA) localization with [LingoHub](https://lingohub.com). Update your app's translations without releasing a new app version.

**Contents:** [How it works](#how-it-works) · [Installation](#installation) · [Get your API key](#get-your-api-key) · [Quick Start](#quick-start) · [Configuration](#configuration) · [Advanced Usage](#advanced-usage) · [Error handling](#error-handling) · [Privacy](#privacy) · [Sample app](#sample-app)

## Features

* 🚀 Over-the-air localization updates via the LingoHub CDN
* 🔄 Runtime language switching
* 📱 Works with XML resources **and** Jetpack Compose
* 🛠 Seamless integration — keep using `getString(...)` and `stringResource(...)` as usual
* 📦 Supports string resources, plurals, and string arrays
* 🔒 Descriptive error reporting
* 📝 Optional debug logging

## How it works

1. Publish a release for a **Distribution** in LingoHub.
2. The SDK asks the LingoHub CDN whether a release matching your app version is available. Releases can target app version ranges, with an optional fallback release for all other versions.
3. If there is a new release, the SDK downloads it and serves the updated strings through the standard Android resource APIs.
4. Downloaded translations are cached on disk and discarded automatically when your app version changes, so a fresh app release always starts from its bundled strings.

If nothing has been published yet for your app version and environment, the SDK simply reports that no update is available — that is a normal state, not an error.

## Requirements

* Android API level 24+
* AndroidX
* Your app must compile against `compileSdk` 36 or later

## Installation

The SDK is available on [Maven Central](https://central.sonatype.com/artifact/com.lingohub/android-cdn-sdk):

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.lingohub:android-cdn-sdk:1.2.0")
}
```

<details>
<summary>Groovy DSL</summary>

```groovy
// build.gradle
dependencies {
    implementation 'com.lingohub:android-cdn-sdk:1.2.0'
}
```

</details>

`mavenCentral()` is part of the default repository setup of every current Android project, so no extra repository configuration is needed.

> **Migrating from JitPack?** Earlier versions were distributed via JitPack under `com.github.lingohub` coordinates. JitPack is no longer supported — switch to the Maven Central coordinates above and remove the `maven { url 'https://jitpack.io' }` repository if nothing else uses it.
>
> The public API was also renamed to the LingoHub brand spelling in 1.1.0. Update your imports and references:
>
> | Old (JitPack era)         | New                       |
> | ------------------------- | ------------------------- |
> | `Lingohub`                | `LingoHub`                |
> | `LingohubUpdateListener`  | `LingoHubUpdateListener`  |
> | `LingohubSDKError`        | `LingoHubSDKError`        |
> | `LingohubLogLevel`        | `LingoHubLogLevel`        |
>
> Package names are unchanged (`com.lingohub.android.cdn.*`), so this is a find-and-replace of the type names.

## Get your API key

1. In LingoHub, open your project and create a **Distribution** (type: *Mobile SDK Android*).
2. Publish a release for the environment you want to use (or mark one release as the fallback).
3. Copy the distribution's CDN API key — it starts with `lh-cdn_`.

See the [LingoHub CDN documentation](https://developers.lingohub.com/reference/distributions) for details.

## Quick Start

### 1. Configure the SDK in your `Application` class

```kotlin
import android.app.Application
import com.lingohub.android.cdn.core.LingoHub

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        LingoHub.configure(
            context = this,
            apiKey = "lh-cdn_..."
        )

        // Fetch the latest translations
        LingoHub.update()
    }
}
```

### 2. Wrap your Activities

LingoHub replaces strings by wrapping the Activity context, so every Activity — XML **and** Compose based — needs the LingoHub `AppCompatDelegate`. The easiest way is a `BaseActivity` that all your Activities extend:

```kotlin
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lingohub.android.cdn.core.LingoHub

abstract class BaseActivity : AppCompatActivity() {

    private val lingoHubDelegate: AppCompatDelegate by lazy {
        LingoHub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingoHubDelegate
    }
}
```

```kotlin
class MainActivity : BaseActivity() {
    // Your activity code
}
```

The delegate is **mandatory**: it is what routes resource lookups through LingoHub. Activities without it keep showing the strings packaged in your APK — downloaded translations are never applied to them, not even after an app restart.

### 3. Use your strings as usual

```kotlin
// XML / programmatic
context.getString(R.string.your_string)

// Jetpack Compose
Text(text = stringResource(R.string.your_string))
```

Plurals (`getQuantityString`) and string arrays (`getStringArray`) work the same way.

## Configuration

```kotlin
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.data.model.Environment
import com.lingohub.android.cdn.utils.LingoHubLogLevel

LingoHub.configure(
    context = this,
    apiKey = "lh-cdn_...",
    environment = Environment.PRODUCTION,   // optional, defaults to PRODUCTION
    logLevel = LingoHubLogLevel.FULL        // optional, defaults to NONE
)
```

| Parameter     | Values                                                                                         | Default                  |
| ------------- | ---------------------------------------------------------------------------------------------- | ------------------------ |
| `environment` | `Environment.PRODUCTION`, `Environment.STAGING`, `Environment.DEVELOPMENT`, `Environment.TEST` | `Environment.PRODUCTION` |
| `logLevel`    | `LingoHubLogLevel.NONE`, `LingoHubLogLevel.FULL`                                               | `LingoHubLogLevel.NONE`  |

The `environment` must match the environment of the release you published. Enable `FULL` logging only in debug builds:

```kotlin
logLevel = if (BuildConfig.DEBUG) LingoHubLogLevel.FULL else LingoHubLogLevel.NONE
```

## Advanced Usage

### Switch languages at runtime

```kotlin
import java.util.Locale

LingoHub.setLocale(Locale.GERMAN)
```

Already-rendered screens don't re-render themselves: recreate the Activity, or drive your Compose UI from a locale state as shown in the [sample app](sample/src/main/java/com/lingohub/android/cdn/example/MainActivity.kt). `LingoHub.getCurrentLocale()` returns the active locale — use it to initialize that state so it survives Activity recreation.

### Update notifications

Implement `LingoHubUpdateListener` to react when a new translation bundle has been downloaded. Recreating the Activity is the simplest way to apply updates immediately — make sure your Activity state survives recreation:

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lingohub.android.cdn.core.LingoHub
import com.lingohub.android.cdn.core.LingoHubUpdateListener

abstract class BaseActivity : AppCompatActivity(), LingoHubUpdateListener {

    private val lingoHubDelegate: AppCompatDelegate by lazy {
        LingoHub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingoHubDelegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LingoHub.addUpdateListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        LingoHub.removeUpdateListener(this)
    }

    override fun onUpdate() {
        // Recreate the activity to reload all resources with the new translations.
        // If you skip this, the new strings are applied on the next app start.
        runOnUiThread {
            recreate()
        }
    }

    override fun onFailure(throwable: Throwable) {
        // See "Error handling" below
    }
}
```

### Reduce network requests

`LingoHub.update()` performs a network request each time it is called. If you don't need instant updates, check only periodically — for example once a day:

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

        LingoHub.configure(
            context = this,
            apiKey = "lh-cdn_..."
        )

        val cacheManager = CacheManager(this)
        if (cacheManager.shouldUpdate()) {
            LingoHub.update()
            cacheManager.updateLastFetchTime()
        }
    }
}
```

## Error handling

Two situations are **not** errors and never reach your listener — the SDK just logs them at info level:

* **Already up to date** — the CDN answered that you have the latest release.
* **Nothing published yet** — no release exists for your environment and app version (`DISTRIBUTION_NOT_FOUND`). Publish a release in your Distribution to resolve this.

Real failures are delivered to `LingoHubUpdateListener.onFailure(throwable)` as a `LingoHubSDKError` carrying the HTTP status and the server's error codes as structured fields — `statusCode: Int?` and `errorCodes: List<String>` — so you can react without parsing the message:

| Status | Error codes                                          | Meaning and what to do                                                                       |
| ------ | ---------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| 401    | `CDN_KEY_NOT_FOUND`, `CDN_KEY_EXPIRED`, `TOKEN_EXPIRED`, `JWT_INVALID` | The API key is missing, invalid, revoked, or rotated — check the key you pass to `configure` |
| 429    | `USAGE_LIMIT_EXCEEDED`                               | Your CDN usage budget is exhausted; translation updates are paused. Consider throttling your own checks (see "Reduce network requests") |
| 400    | —                                                    | Malformed request — usually an SDK/backend version mismatch, please report it                |
| other  | —                                                    | Network errors and unexpected responses                                                      |

```kotlin
override fun onFailure(throwable: Throwable) {
    val error = throwable as? LingoHubSDKError
    when {
        error?.statusCode == 429 -> scheduleRetryTomorrow()
        error != null && "CDN_KEY_EXPIRED" in error.errorCodes -> alertKeyRotationNeeded()
        else -> Log.w("MyApp", "LingoHub update failed: ${throwable.message}")
    }
}
```

`statusCode` is `null` for local and network errors (no response was received).

### Troubleshooting

* **`onUpdate` never fires and nothing changes** — most likely no release is published yet for your app version and environment. Publish a release in your Distribution (or mark one as the fallback), and double-check that the `environment` you configure matches the release's environment. Enable `LingoHubLogLevel.FULL` in a debug build to see what the SDK is doing.
* **Strings never change, not even after an app restart** — your Activities don't use the LingoHub delegate. It is mandatory; see [Quick Start step 2](#2-wrap-your-activities).
* **Strings change only after leaving and reopening a screen** — the delegate is in place, but you don't `recreate()` the visible Activity in `onUpdate`.
* **Error 401** — the CDN key is missing, invalid, or was revoked. The error message contains the reason (for example `CDN_KEY_NOT_FOUND`).
* **Error 429** — your CDN usage budget is exhausted. Throttle your update checks.

## R8 / ProGuard

No configuration needed — the SDK ships its consumer rules inside the AAR.

## Privacy

What the SDK touches on the device and network — relevant for your Play Console *Data safety* declaration:

* `SharedPreferences` — stores the installed release ID, app version, and the SDK's client identifier.
* Downloaded translation bundles — stored in the app's internal files directory.
* Each update check sends to the LingoHub CDN: your app's version name, the current app language, the SDK version, and a random per-install identifier (a UUID generated on first launch and kept in the SDK's private `SharedPreferences`) as the client identifier for usage metering. The identifier is not derived from any hardware or device ID, cannot be correlated across apps, and resets when the app is uninstalled or its data is cleared. In your Play Console *Data safety* form it falls under **Device or other IDs** (the same category as app-generated identifiers like a Firebase installation ID). No hardware identifier such as `ANDROID_ID` is read or transmitted.

## Sample app

The [`sample`](sample/) module in this repository shows a complete Compose integration, including runtime language switching and update notifications. Open the project in Android Studio, insert your CDN API key in [`LingoHubApplication.kt`](sample/src/main/java/com/lingohub/android/cdn/example/LingoHubApplication.kt), and run the `sample` configuration.

## Support

For bug reports and feature requests, please open an issue on GitHub.

## License

Apache License Version 2.0, January 2004. More info in the [LICENSE](./LICENSE) file.
