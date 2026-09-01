# Wanderly — LingoHub SDK sample app

A small Jetpack Compose app for **Wanderly**, a fictional trip-planning product,
demonstrating the LingoHub Android SDK end to end:

* SDK configuration in [`LingoHubApplication.kt`](src/main/java/com/lingohub/android/cdn/example/LingoHubApplication.kt) (Development environment, debug-only full logging, a once-a-day update throttle)
* The mandatory `BaseActivity` delegate pattern with `recreate()` on updates in [`BaseActivity.kt`](src/main/java/com/lingohub/android/cdn/example/BaseActivity.kt)
* Placeholders (`%1$s`), plurals (the travelers stepper), and runtime language switching across en · de · es · fr · ja in [`MainActivity.kt`](src/main/java/com/lingohub/android/cdn/example/MainActivity.kt)

Only **English and German ship inside the APK** — Spanish, French, and Japanese
exist purely over the air, so seeing them on screen proves the OTA pipeline is
working. Fallback behavior is part of the demo too: untranslated keys fall back
to the packaged (English) strings.

## Run it

1. Create a LingoHub project with the Wanderly content and add a
   **Mobile SDK Android** distribution (see the
   [main README](../README.md#get-your-api-key)); publish a release for the
   *Development* environment.
2. Replace `YOUR_API_KEY` in `LingoHubApplication.kt` with your `lh-cdn_...` key.
3. Run the `sample` configuration from Android Studio.

## Demo script

1. Launch — the app downloads the published release on first start.
2. Tap **ES** — Spanish appears even though the APK contains none.
3. Tap **+** — plural forms come from the OTA bundle.
4. Change a string in LingoHub (e.g. the tagline), publish a new release,
   tap **Check for updates** — the text changes on screen within seconds.

The sample depends on the SDK via `implementation(project(":sdk"))` so it always
exercises the code in this repository. In your own app, use the Maven Central
coordinate from the main README instead.
