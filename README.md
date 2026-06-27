
![Pylux Logo](pylux-logo.png)

# Pylux — Community Fork (Android only)

> This is an **Android-only fork** of [Pylux](https://github.com/ForWard-Technologies-LLC/Pylux). It adds cloud streaming quality-of-life features, performance diagnostics, and build fixes. Non-Android platforms are untouched.

[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue)](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/LICENSES/AGPL-3.0-only-OpenSSL.txt)
[![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20macOS%20%7C%20Windows%20%7C%20Linux-brightgreen)](https://github.com/ForWard-Technologies-LLC/Pylux/releases)

**Pylux is a free, open-source, community build PS4 and PS5 Remote Play client for Android, Android TV, iOS, macOS, Windows, Linux, and Steam Deck.** It focuses on app-store installs, Internet Play (streaming the game catalog or your owned games), automatic console discovery, and a touch-friendly mobile UI — all from one community-maintained codebase.

---

## Fork Features (Android)

These features are **exclusive to this fork** — not yet present in the upstream release.

### Performance Overlay
Three-column diagnostic overlay showing real-time stream quality metrics during all session types (Remote Play, PS Now, PS Cloud).

| Column | Metrics |
|--------|---------|
| **Latency** | Total latency (network + decode), network RTT, decode time |
| **Stream** | FPS with sparkline, bitrate in Mbps, resolution |
| **Quality** | RTT, jitter, decode time, video packet loss, cumulative frame drops |

- Toggle from Settings → Performance overlay or in-stream button
- Polled every 1s via RxJava from JNI SessionMetrics
- EMA-smoothed decode time measured at video decoder output thread
- Kotlin-side jitter computed as std dev over 30-sample sliding window
- Monospace TextViews + SparklineView (no Canvas)

### Cloud Language Picker
Settings → General → Cloud Language dropdown. Select the language for cloud streamed games.

- **5 languages** matching available datacenters: English, Deutsch, Français, Suomi, English (UK)
- **Auto-datacenter matching**: selecting a language automatically locks the corresponding server (Deutsch → Frankfurt, English → Stockholm, etc.)
- **Language filtering**: only shows languages that have matching datacenters in your ping results
- Games now respect your chosen language when paired with the correct datacenter

### Datacenter Display in Overlay
The overlay header shows the actual selected server name (e.g. `Cloud Play • fraa`) instead of a generic label. Wired through the full allocation pipeline: `AllocationResult → CloudStreamSession → ConnectInfo`.

### Frame Drops Tracking
The drops counter in the overlay now reflects **actual frame loss** from chiaki's video receiver, not just codec buffer overflows. Previously, `frames_lost` was ignored — drops stayed at zero even on unstable connections.

### Build Stability
- Fixed CMake version mismatch (3.30.4 → 3.22.1 for AGP 8.5.2)
- Fixed C++ standard conflict (C++14 → C++17 for oboe)
- Fixed Java home path in gradle.properties
- Removed stale `server_rtt` reference in JNI
- Removed orphaned `input.release()` call

---

## Cloud Streaming Fixes (2026-06-26/27)

### Bloodborne PS Now
- **Store country mapping** — Sony's store doesn't work in some countries (Nordic region, etc). Added automatic country fallbacks so game lookups don't fail.
- **Streaming SKU detection** — Sony changed how they tag streamable games. Updated the scan to handle both old and new formats.
- **Country retry** — if your region's store can't find a game, automatically retries with US.

### Games Ownership Detection
Free-to-play and cross-gen titles (Fortnite, GTA V PS5) were not showing as owned, blocking streaming. Fixed by improving how owned games are matched against the catalog:
- **Fuzzy ID matching** — handles the different ID formats Sony's catalog and entitlement API use for the same game
- **Sibling ID fallback** — tries related product IDs when an exact match fails

### Stream Quality
- **HQ mode** — tells Sony's servers to prioritize quality over bitrate savings
- Patched video decoder issues causing stutters (corrupted frames now recover instead of cascading)
- PSNOW streams are capped at **25 Mbps** in Europe; PSCLOUD is uncapped

---

## PS5 Cloud Streaming UI

- **PS5 Portal-style design** — dark navy-black background (`#13141B`), dark surface cards (`#1A1C24`), white accent throughout, frosted bottom nav
- **Game cards** — full cover art with gradient overlay, game name on gradient with shadow, platform and ownership badges, scale-down press animation, 12dp rounded corners
- **Bottom navigation** — Cloud Play | Remote Play | Settings wired to ViewPager
- **Catalog / Library tabs** — PS5 pill-style, LB/RB controller shortcuts
- **Fullscreen browsing** — toolbar, logo, donation and settings icons removed; games fill the screen
- **Card focus animations** — programmatic 1.04x scale on focus, focused stroke selector, state list animator
- **Image loading** — fixed covers appearing blank during scroll, now shows instantly
- **Scroll & focus** — fixed controller navigation getting stuck and scrolling jumping around

---

## Original Features (upstream)

- **Internet Play** — stream games from the game catalog or your owned game library
- **Remote Play** — low-latency streaming of your PlayStation console to any supported device
- **Cross-platform** — Android, Android TV, iOS, iPadOS, macOS, Windows, Linux, Steam Deck
- **App-store installs** — Google Play, App Store, Mac App Store, Flathub
- **Automatic console discovery and registration**
- **Touch-friendly controls** — mobile-optimized UI

---

## Download

<a href="https://github.com/Leeiiiiiii/Pylux/releases"><img src="assets/github-release-badge.svg" height="50" alt="Download from GitHub Releases"></a>

Latest fork APK: **[Download from GitHub Releases](https://github.com/Leeiiiiiii/Pylux/releases)**

For upstream downloads see the [official releases page](https://github.com/ForWard-Technologies-LLC/Pylux/releases).

---

## Contributing

This fork targets Android enhancements. Fork the repo, create a branch, and open a PR. See upstream [CONTRIBUTING.md](CONTRIBUTING.md) for the full workflow.

---

## Credits

Pylux is built on [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) and [chiaki-ng](https://github.com/streetpea/chiaki-ng). This fork extends [ForWard-Technologies-LLC/Pylux](https://github.com/ForWard-Technologies-LLC/Pylux) with Android-specific features.

---

## Legal

Pylux is intended for use with games and content you own or are licensed to use, on hardware you own, with a valid account or subscription. It does not circumvent copy protection or facilitate piracy. This project is not endorsed or certified by the console manufacturer. All trademarks belong to their respective owners.
