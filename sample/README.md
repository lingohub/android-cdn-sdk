# LingoHub SDK sample app

A minimal Jetpack Compose app demonstrating the LingoHub Android SDK end to end:

* SDK configuration in [`LingoHubApplication.kt`](src/main/java/com/lingohub/android/cdn/example/LingoHubApplication.kt), including a simple once-a-day update throttle
* The `BaseActivity` delegate pattern in [`BaseActivity.kt`](src/main/java/com/lingohub/android/cdn/example/BaseActivity.kt), with `recreate()` on updates
* Runtime language switching (English ↔ German) driven from Compose state in [`MainActivity.kt`](src/main/java/com/lingohub/android/cdn/example/MainActivity.kt)

## Run it

1. Create a *Mobile SDK Android* Distribution in LingoHub and publish a release (see the [main README](../README.md#get-your-api-key)).
2. Replace `YOUR_API_KEY` in `LingoHubApplication.kt` with your `lh-cdn_...` key.
3. Run the `sample` configuration from Android Studio.
4. Tap **Check for updates** after publishing a new release in LingoHub — the screen text updates in place.

The sample depends on the SDK via `implementation(project(":sdk"))` so it always exercises the code in this repository. In your own app, use the Maven Central coordinate from the main README instead.
