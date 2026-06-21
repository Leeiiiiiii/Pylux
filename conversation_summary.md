# Build Fixes & Performance Overlay Rewrite (2026-06-21)

_(Updated 2026-06-22 with language picker + datacenter auto-match + drops fix)_

---

# Cloud Language Picker + Datacenter Auto-Match (2026-06-22)

## Problem
Games in Pylux were stuck in English regardless of PSN account region. User's account was Finnish (FI), not German — Kamaji session returned `country: FI, language: en`. Finnish cloud datacenters only carry English game SKUs.

## Root cause discovery
Game language is **tied to the datacenter server**. Selecting "Deutsch" without a German datacenter has no effect. The `spec["language"]` field must match the datacenter region for Gaikai to serve the correct game version.

## Gaikai language format fix
- Gaikai expects bare language code (`"de"`) not full locale (`"de-DE"`)
- Changed in `PSGaikaiStreaming.kt:buildRequestGameSpec()`: `locale.split("-").firstOrNull()?.lowercase()`

## Language-to-datacenter mapping
| Datacenter prefix | Server(s) | Language |
|---|---|---|
| fra | fraa, frab (Frankfurt) | Deutsch |
| lon | lonb (London) | English (UK) |
| sto | stoa (Stockholm) | English, Suomi |
| par | parb (Paris) | Français |

## Changes in Pylux (PR #21)
- `strings.xml` — `cloud_language_pscloud` key, 5 language entries (English, English UK, Deutsch, Français, Suomi)
- `preferences.xml` — `ListPreference` under General, after PSN login
- `Preferences.kt` — `cloudLanguageKey` property, `setCloudLanguage()` invalidates catalog cache
- `SettingsFragment.kt` — DataStore `getString`/`putString` for cloud language; `bindCloudLanguageToDatacenter()`:
  - Filters language list to only show languages with matching datacenters in saved ping results
  - Auto-selects datacenter when language changes via `onPreferenceChangeListener`
- `CloudLocale.kt` — `SUPPORTED_LOCALES`, `DATACENTER_LOCALE_MAP` (6 entries), `localeToDatacenterPrefix()`, `datacenterToLocales()`, `filterLocalesByDatacenters()`
- `PSGaikaiStreaming.kt` — language code format fix, `datacenterName` added to `AllocationResult`
- `CloudStreamSession.kt` — `datacenterName` field
- `CloudStreamingBackend.kt` — passes `allocationResult.datacenterName` to session
- `CloudPlayFragment.kt` — passes `session.datacenterName` to `ConnectInfo.serverName`
- `video-decoder.c` — removed ignored `(void)frames_lost`; now increments `cumulative_drops` on actual frame loss (was only counting codec buffer overflow)
- `chiaki-jni.c` — `server_rtt` → `0.0` (field doesn't exist in current lib)
- `StreamViewModel.kt` — removed orphaned `input.release()` call
- `gradle.properties` — fixed Java home path (`Android Studio2` → `Android Studio`)

## Changes ported to Pylux-Cloud-Android
Same as above plus:
- `CloudLocale.kt` — new file (didn't exist)
- `gradle.properties` — added `org.gradle.java.home` (was missing)
- Overlay datacenter display already existed (`CloudStreamSession.datacenterName` → `ConnectInfo.cloudServerName`)

## Key findings
1. PSN account region (FI vs DE) determines which datacenters are available
2. Language code must be bare (`de`, `en`, `fr`) not locale (`de-DE`, `en-US`)
3. Language and datacenter must match — Gaikai ignores mismatched requests
4. Sony caps cloud streams at ~50 Mbps for 4K
5. Masking as PlayStation Portal (`"model":"portal"`, `"platform":"qlite"`) limits to 1080p naturally
6. DUID is randomly generated per launch (prefix `0000000700410080` + 16 random bytes), not persisted
7. `Accept-Language: jp` is hardcoded in chiaki's `holepunch.c` (remote play device discovery only, not cloud)
8. Sony sees: Portal device, chosen datacenter, language code, HEVC codec, DS4/DS5/xinput controller
9. Performance overlay drops counter was broken (ignored chiaki's `frames_lost`); fixed to count actual frame loss

## GitHub
- **Release APK**: https://github.com/Leeiiiiiii/Pylux/releases/tag/v1.0-beta
- **PR #21**: https://github.com/ForWard-Technologies-LLC/Pylux/pull/21
- Title: "Android: cloud language picker + datacenter auto-match + overlay server display + build fixes"

---

## Complete file-by-file change log for Pylux/android (2026-06-22)

### Language picker + datacenter auto-match

| File | Change |
|------|--------|
| `values/strings.xml` | `preferences_cloud_language_key` (`cloud_language_pscloud`), title/summary; 5-entry arrays (English→en-US, Deutsch→de-DE, Français→fr-FR, Suomi→fi-FI, English UK→en-GB) |
| `xml/preferences.xml` | `ListPreference` for cloud language under General, after PSN login |
| `common/Preferences.kt:271` | `cloudLanguageKey` property; `setCloudLanguage()` invalidates CloudGameRepository cache |
| `settings/SettingsFragment.kt` | DataStore `getString`/`putString` wired for `cloudLanguageKey`; `bindCloudLanguageToDatacenter()` filters by available DCs + auto-selects |
| `cloudplay/CloudLocale.kt` | `SUPPORTED_LOCALES` (5), `DATACENTER_LOCALE_MAP` (4 prefixes: fra/lon/sto/par), `localeToDatacenterPrefix()`, `datacenterToLocales()`, `filterLocalesByDatacenters()` |
| `cloudplay/api/PSGaikaiStreaming.kt:1385` | Language format: `"de-DE"` → `"de"` (bare code); `AllocationResult` gets `datacenterName` field |
| `cloudplay/model/CloudStreamSession.kt` | Added `datacenterName` field |
| `cloudplay/api/CloudStreamingBackend.kt:244` | Passes `allocationResult.datacenterName` to `CloudStreamSession` |
| `main/CloudPlayFragment.kt:1597` | Passes `session.datacenterName` to `ConnectInfo.serverName` |

### Build fixes

| File | Change |
|------|--------|
| `gradle.properties:13` | Java home path: `Android Studio2` → `Android Studio` |
| `cpp/chiaki-jni.c:777` | `server_rtt` → `0.0` (field doesn't exist in lib) |
| `stream/StreamViewModel.kt:118` | Removed `input.release()` (method doesn't exist) |

### Drops counter fix

| File | Change |
|------|--------|
| `cpp/video-decoder.c:167` | Removed `(void)frames_lost`; now `cumulative_drops += frames_lost` (was only counting codec buffer overflow) |

### Final state
- 5 languages match 4 datacenter prefixes
- English → stoa, English UK → lonb, Deutsch → fraa/frab, Français → parb, Suomi → stoa
- All filtered dynamically based on saved ping results
- Overlay shows actual server name
- Gaikai receives bare language code

## CMake version mismatch
- `android/app/build.gradle` line 124: changed `version "3.30.4"` → `version "3.22.1"`
- CMake 3.30.4 was not installed in SDK. AGP 8.5.2 ships CMake 3.22.1.

## C++ standard conflict (oboe library)
- `android/app/CMakeLists.txt` line 4: changed `set(CMAKE_CXX_STANDARD 14)` → `set(CMAKE_CXX_STANDARD 17)`
- oboe requires C++17; parent CMakeLists.txt C++14 flag overrode it.

## Performance overlay rewrite (ported from Pylux-Cloud-Android)
Reference: `C:\Users\lei\Documents\Workplace\Pylux-Cloud-Android\android`

### Design decisions
1. **EMA-smoothed decode time** (`0.9/0.1`) measured at output thread from submit→render timestamps via ring buffer (not CPU queueing wall-clock)
2. **Kotlin-side jitter** — std dev of last 30 `ping` samples via `ArrayDeque<Double>`, not native JNI
3. **TextViews overlay** — LinearLayout with monospace TextViews and `String.format(Locale.US, ...)`, replaces custom Canvas
4. **RxJava polling** — `Observable.interval(1, TimeUnit.SECONDS)` replaces `Handler.postDelayed`
5. **`ping_ms = rtt_us / 1000.0` (static senkusha)** — `server_rtt` from QoS feedback includes server-side jitter buffers and is inherently noisy. Senkusha ping is the actual network RTT measured once during handshake. Values stay stable, won't flash red on good connections.
6. **`latency_ms = server_rtt`** — captured in SessionMetrics but not displayed. Available for future use.
7. **9-field SessionMetrics** — `(IIFDDDDDJ)V` JNI signature: width, height, fps, bitrate, ping, latency, packetLoss, decodeTime, drops

### Files (15 in commit, pushed to PR #21)
- `android/app/build.gradle:124` — CMake `"3.22.1"`
- `android/app/CMakeLists.txt:4` — `CMAKE_CXX_STANDARD 17`
- `android/app/src/main/cpp/chiaki-jni.c` — `sessionGetMetrics` with ping/latency/decode/drops, `ping_ms = rtt_us/1000.0`
- `android/app/src/main/cpp/video-decoder.c` — EMA decode time via output thread `submit_times[256]` ring buffer
- `android/app/src/main/cpp/video-decoder.h` — `DECODER_SUBMIT_RING_SIZE 256`, `ema_decode_time_ms`, `cumulative_drops`, `current_fps`
- `android/.../stream/PerformanceOverlayView.kt` — new: LinearLayout+TextViews, `SparklineView`, `updateOverlay(OverlayData)`, `dpToPx()`
- `android/.../stream/StreamViewModel.kt` — `OverlayData` data class, RxJava polling, `ArrayDeque<Double>` jitter, fpsHistory, `startMetricsPolling()`
- `android/.../stream/StreamActivity.kt` — observes `overlayData`, toggle button wiring, `showPerformanceOverlay` observer
- `android/.../lib/Chiaki.kt` — `SessionMetrics` 9-field data class `(width, height, fps, bitrate, ping, latency, packetLoss, decodeTime, drops)`
- `android/.../common/Preferences.kt` — `showPerformanceOverlay` boolean preference
- `android/.../settings/SettingsFragment.kt` — DataStore wiring for performance overlay toggle
- `android/app/src/main/res/layout/activity_stream.xml` — `PerformanceOverlayView` in layout, `performanceOverlayToggle` button
- `android/app/src/main/res/values/strings.xml` — overlay preference strings
- `android/app/src/main/res/xml/preferences.xml` — `show_performance_overlay` SwitchPreference
- `android/app/src/main/res/drawable/ic_performance.xml` — new: bar chart icon

### PR #21 details
- **Branch**: `master` → `release/beta`
- **Remote**: `myfork` (Leeiiiiiii/Pylux) — user has no write access to origin (ForWard-Technologies-LLC)
- **Push**: `git push --force-with-lease myfork master`
- **PR URL**: https://github.com/ForWard-Technologies-LLC/Pylux/pull/21

### Commit hygiene
Original commit `be04d2fa` included unrelated changes (dpad touch, fast scroller removal, PSN login text, etc.). These were stripped out before final force-push. Clean commit contains ONLY overlay + build fixes.

## Environment
- SDK: `C:\Users\lei\AppData\Local\Android\Sdk`
- NDK: 28.2.13676358 (r28.2)
- CMake in SDK: 3.22.1
- AGP: 8.5.2, Kotlin: 1.9.24
- compileSdk: 35, targetSdk: 35, minSdk: 24

---

## Sonar detection & safety assessment (2026-06-22)

### What Sony sees
- Device: PlayStation Portal (`"model":"portal"`, `"platform":"qlite"`, `"gaikaiPlayer":"16.4.0"`, `User-Agent: PlayStation Portal/6.0.0...`)
- DUID: `0000000700410080` + 32 random hex (regenerated per launch, not persisted)
- Auth: NPSSO token → OAuth → `gkCloudAuthCode` + `streamServerAuthCode`
- No `Accept-Language: jp` in cloud streaming path (only in chiaki holepunch.c for device discovery)
- Cloud streaming headers: `User-Agent`, `X-Gaikai-Session`, `Content-Type: application/json`

### What triggers fraud detection (likely)
- **Rapid region-hopping**: 20+ datacenter switches in minutes — impossible travel
- **Concurrent sessions**: same account streaming from two datacenters at once — account sharing
- **Region mismatch persistence**: Finnish account always requesting German language — suspicious but not definitive
- **Mechanical patterns**: back-to-back max-duration sessions — looks like a server farm

### What doesn't (likely)
- Same IP, same account, human-paced test sessions
- European datacenters only — same continent
- Account language matches datacenter (Finnish account → Stockholm)
- Pylux has existed for years with zero reported bans for client impersonation

### Sony escalation ladder (if detected)
1. Silent throttle — higher latency, lower bitrate
2. Captcha/re-login — forced verification
3. Temp session ban — blocked for hours/days
4. Account suspension — loss of purchases, trophies (rare, usually only for payment fraud)

### Safest practice
Pick one language + one datacenter, never change them. English + stoa for Finnish account. Consistency is the best camouflage.
