# Changelog

All notable changes to this project will be documented in this file. Releases up to 1.2.0 are described in the [GitHub releases](https://github.com/lingohub/android-cdn-sdk/releases).

## [Unreleased]

### Changed
- **Fewer update checks after a 429.** When the CDN reports an exhausted usage budget (`429 USAGE_LIMIT_EXCEEDED`), the SDK now pauses update checks for an hour, or for longer when the CDN's `Retry-After` asks for it. The pause is stored on the device and survives app restarts. While it lasts, `LingoHub.update()` reports the 429 to `onFailure` without contacting the CDN. Previously every `update()` call checked again. The iOS SDK behaves the same way.
- **`LingoHub.update()` paces its own checks.** Within the minimum interval after a successful update check (15 minutes; no interval in debuggable builds), `update()` returns without contacting the CDN and without calling your listener. Call it whenever your app starts or comes to the foreground: the once-a-day `CacheManager` throttle the README used to suggest is no longer needed.
- Failed update checks follow one retry and pause policy, shared with the iOS SDK and documented in the README's new "Failures and retries" section (lingohub/organization#2351):
  - A `5xx` from the CDN is retried once, after the delay the response's `Retry-After` asks for, or after a random 2–5 seconds. When the retry fails as well, update checks pause for 5 minutes, doubling with each further failed update in a row up to an hour.
  - A download the storage refuses (an expired download URL, a `5xx`) gets one fresh check for a new download URL.
  - A new app version, another environment or another CDN key starts without a pause and without the minimum interval.
  - An update check that is running when the app calls `configure()` again stops at its next step without changes: no further request (not even its retry), no install, nothing recorded over what the new configuration recorded, and no report to the listeners. The next `update()` call checks with the new configuration.
  - The check request is sent exactly as often as this policy says: OkHttp no longer repeats it on its own after a `408` or a `503` with `Retry-After: 0`.
  - Client errors (`400`, `401`, a `404` other than `DISTRIBUTION_NOT_FOUND`) are logged once per process instead of on every check.

### Added
- `LingoHub.setMinimumCheckInterval(interval, unit)`: the minimum time between update checks.
