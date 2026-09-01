# LingoHub SDK README structure

The single canonical README layout for all LingoHub SDK repositories
(android-cdn-sdk, ios-cdn-sdk, future platforms). Defined here first;
apply the same skeleton to every platform, filling the platform-specific
slots. Section names and order are fixed — content inside adapts.

| # | Section | Notes |
| - | ------- | ----- |
| 1 | Title + badges | `LingoHub <Platform> SDK`; badges: package version (Maven Central / SPM), license, platform floor |
| 2 | Intro + contents line | One sentence ("over-the-air localization with LingoHub"), then a one-line linked table of contents |
| 3 | Features | Short emoji bullets |
| 4 | How it works | Numbered 4-step flow (publish → check → download → cache/discard on app version change). Always end with: "nothing published yet is a normal state, not an error." |
| 5 | Requirements | OS floor, toolchain floor |
| 6 | Installation | Platform package manager (Gradle/Maven Central, SPM). Migration notes if distribution channel changed |
| 7 | Get your API key | Distribution setup steps; key prefix `lh-cdn_`; link to developers.lingohub.com/reference/distributions |
| 8 | Quick Start | Numbered minimal integration (configure → platform hook → use strings as usual) |
| 9 | Configuration | Parameter table: `environment`, `logLevel`, with defaults; "environment must match the release" note; debug-only logging tip |
| 10 | Advanced Usage | Subsections: Switch languages at runtime · Update notifications · Reduce network requests · (platform extras, e.g. manual localization on iOS) |
| 11 | Error handling | First: the silent non-errors (up to date; `DISTRIBUTION_NOT_FOUND`). Then the failure table: 401 codes (`CDN_KEY_NOT_FOUND`, `CDN_KEY_EXPIRED`, `TOKEN_EXPIRED`, `JWT_INVALID`), 429 `USAGE_LIMIT_EXCEEDED`, 400, other — each with what to do |
| 12 | Troubleshooting | Sub-section of Error handling: "nothing changes", "updates only after restart", 401, 429 |
| 13 | Platform build notes | Android: R8/ProGuard. iOS: (none / Xcode notes) |
| 14 | Privacy | What is stored on device, what is sent to the CDN, store-declaration pointers (Play Data safety / App privacy manifest) |
| 15 | Sample app | Link to the in-repo sample, where to insert the API key, how to run |
| 16 | Support | Issues on GitHub |
| 17 | License | Apache 2.0 |

Terminology used everywhere: **LingoHub** (capital H), *Distribution*,
*release*, *environment*. Error codes are written exactly as the CDN
returns them (`SCREAMING_SNAKE_CASE`).
