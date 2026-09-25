# LingoHub SDK README structure

The single canonical README layout for all LingoHub SDK repositories
(android-cdn-sdk, ios-cdn-sdk, js-cdn-sdk, future platforms). Defined here first;
apply the same skeleton to every platform, filling the platform-specific
slots. Section names and order are fixed — content inside adapts.

| # | Section | Notes |
| - | ------- | ----- |
| 1 | Title + badges | `LingoHub <Platform> SDK`; badges: package version (Maven Central / SPM / npm), license, platform floor |
| 2 | Intro + contents line | One sentence ("over-the-air localization with LingoHub"), then a one-line linked table of contents |
| 3 | Features | Short emoji bullets |
| 4 | How it works | Numbered 4-step flow (publish → check → download → cache/discard on app version change). Always end with: "nothing published yet is a normal state, not an error." |
| 5 | Requirements | OS or runtime floor, toolchain floor |
| 6 | Installation | Platform package manager (Gradle/Maven Central, SPM, npm); with several packages, which app needs which. Migration notes if distribution channel changed |
| 7 | Get your API key | Distribution setup steps; key prefix `lh-cdn_`; link to developers.lingohub.com/reference/distributions |
| 8 | Quick Start | Numbered minimal integration (configure → platform hook → use strings as usual) |
| 9 | Configuration | Parameter table: `environment`, `logLevel`, with defaults; "environment must match the release" note; debug-only logging tip |
| 10 | Advanced Usage | Subsections: Switch languages at runtime · Update notifications · Reduce network requests · (platform extras, e.g. manual localization on iOS) |
| 11 | Error handling | First: the silent non-errors (up to date; `DISTRIBUTION_NOT_FOUND`). Then the failure table: 401 codes (`CDN_KEY_NOT_FOUND`, `CDN_KEY_EXPIRED`, `TOKEN_EXPIRED`, `JWT_INVALID`), 429 `USAGE_LIMIT_EXCEEDED`, 400, other — each with what to do |
| 12 | Troubleshooting | Sub-section of Error handling: "nothing changes", "updates only after restart", 401, 429 |
| 13 | Platform build notes | Android: R8/ProGuard. iOS: (none / Xcode notes) |
| 14 | Privacy | What is stored on device, what is sent to the CDN, store-declaration pointers (Play Data safety / App privacy manifest; both for React Native apps) |
| 15 | Sample app | Link to the in-repo sample (one per kind of app where an SDK serves several), where to insert the API key, how to run |
| 16 | Support | Issues on GitHub |
| 17 | License | Apache 2.0 |

The platform-specific slots of each SDK:

| Slot | iOS (`ios-cdn-sdk`) | Android (`android-cdn-sdk`) | JavaScript (`js-cdn-sdk`) |
| ---- | ------------------- | --------------------------- | ------------------------- |
| 1 Title | `LingoHub iOS SDK` | `LingoHub Android SDK` | `LingoHub JavaScript SDK` |
| 1 Package badge | GitHub release (SPM) | Maven Central | npm (`@lingohub/cdn-sdk`) |
| 5 Platform floor | iOS and macOS versions | Android API level | Node.js and React Native versions |
| 7 Distribution type | *Mobile SDK iOS* | *Mobile SDK Android* | *Native format* |
| 8 Platform hook | `swizzleMainBundle()` | Wrap your Activities | Connect i18next (React Native: also `updateOnForeground`) |
| 13 Platform build notes | — | R8 / ProGuard | Bundlers and runtimes |
| 14 Store declarations | App privacy manifest | Play Data safety | App Store privacy details and Play Data safety (React Native) |
| 15 Sample app | `DemoAppLingoHub/` | `sample/` | `samples/nextjs/`, `samples/react-native/` |

A repository that publishes several packages (js-cdn-sdk: three npm
packages) keeps this layout in its root README, which covers all of its
packages. Its package READMEs, which the package registry shows, stay
short: what the package is for, installation, a quick start, the
"Failures and retries" section word for word (lingohub/organization#2351),
and a link to the root README.

Terminology used everywhere: **LingoHub** (capital H), *Distribution*,
*release*, *environment*. Error codes are written exactly as the CDN
returns them (`SCREAMING_SNAKE_CASE`).
