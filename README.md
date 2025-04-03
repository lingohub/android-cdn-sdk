# Lingohub Android SDK

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

Add the Lingohub SDK to your project's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.lingohub:sdk:latest.version'
}
```

## Quick Start

### 1. Initialize the SDK in your `Application` class:

```kotlin
class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure Lingohub with your API key
        Lingohub.configure(
            context = this,
            apiKey = "your-api-key"
        )

        // Sync with latest changes
        Lingohub.update()
    }
}
```

Lingohub should be wrapped around the activity context in order to replace strings. If you want instant updates, we recommend implementing a BaseActivity class from which all your other Activity classes extend. Otherwise an App restart will make changes take effect.

```kotlin
abstract class BaseActivity : AppCompatActivity(), LingohubUpdateListener {

    private val lingohubDelegate: AppCompatDelegate by lazy {
        Lingohub.getAppCompatDelegate(this, AppCompatDelegate.create(this, null))
    }

    override fun getDelegate(): AppCompatDelegate {
        return lingohubDelegate
    }
}
```

Then extend your `Activities` with `BaseActivity`:

```kotlin
class MainActivity : BaseActivity() {
    // Your activity code
}
```

## 2. Usage

### String Resources

Access string resources as you normally would:

```kotlin
// XML-based
context.getString(R.string.your_string)

// Compose
Text(text = stringResource(R.string.your_string))
```

### Switch Languages

Change the app's language at runtime:

```kotlin
Lingohub.setLocale(Locale.GERMAN) // Switch to German
```

## Advanced Configuration

### Environment Configuration

Initialize the Lingohub SDK with optional parameters:

| Parameter   | Example Value | Description                                                     | Default     |
| ----------- | ------------- | --------------------------------------------------------------- | ----------- |
| environment | .production   | Environment to use (.production, .staging, .development, .test) | .production |
| logLevel    | .none         | Control debug logging output (.none or .full)                   | .none       |

```kotlin
class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure Lingohub with your API key
        Lingohub.configure(
            context = this,
            apiKey = "your-api-key",
            environment = Environment.PRODUCTION // Optional, defaults to PRODUCTION
            logLevel = LingohubLogLevel.FULL // Not recommended for PRODUCTION
        )
    }
}
```

### Update Notifications

Implement `LingohubUpdateListener` in to handle bundle updates. You can listen for changes anywhere. For example in a wrapper around `AppCompatActivity` and react:

```kotlin
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

        // Possible solution (Not recommended)
        // Recreate the activity to reload all resources with new translations
        // State needs to be saved
        // runOnUiThread {
        //    recreate()
        // }

        // If unhandeled here, the strings are updated with the next app start
    }

    override fun onFailure(throwable: Throwable) {
        // Handle failure if needed
    }
}
```

### Optimizing Network Requests with CacheManager

Optional caching is possible to reduce network requests, you can implement a simple CacheManager:

```kotlin
class CacheManager(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("lingohub_prefs", Context.MODE_PRIVATE)
    }

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

        // Configure Lingohub with your API key
        Lingohub.configure(
            context = this,
            apiKey = "your-api-key",
            environment = Environment.PRODUCTION // Optional, defaults to PRODUCTION
        )

        // Access the cache manager
        val cacheManager = CacheManager(context)

        // Check if strings should be fetched
        if (cacheManager.shouldUpdate()) {
        Lingohub.update()
        cacheManager.updateLastFetchTime()
        }
    }
}
```

### Manual Bundle Updates

For example on every app start

```kotlin
Lingohub.update()
```

## Support

For bug reports and feature requests, please open an issue on GitHub.

## License

Apache License Version 2.0, January 2004. More infos in the `LICENSE` file.
