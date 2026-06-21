
![Pylux Logo](pylux-logo.png)

# Pylux — Community Fork

> This fork extends the upstream [Pylux](https://github.com/ForWard-Technologies-LLC/Pylux) with Android-specific enhancements focused on cloud streaming quality-of-life, performance diagnostics, and build stability.

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

Latest fork APK: **[Pylux-v2.10.21-beta.apk](https://github.com/Leeiiiiiii/Pylux/releases/tag/v1.0-beta)**

For upstream downloads see the [official releases page](https://github.com/ForWard-Technologies-LLC/Pylux/releases).

---

## Upstream PR

All fork changes are submitted to upstream via **[PR #21](https://github.com/ForWard-Technologies-LLC/Pylux/pull/21)**.

---

## Key Discoveries (2026-06-22)

- **Game language is tied to datacenter region.** Selecting a language without a matching datacenter has no effect — Gaikai ignores the mismatch.
- **Gaikai expects bare language codes** (`"de"`, `"en"`) not full locales (`"de-DE"`, `"en-US"`), though both are accepted.
- **PSN account region determines available datacenters.** A Finnish account (`country: FI`) gets Nordic servers (Stockholm), not German ones.
- **Sony caps cloud streams at ~50 Mbps** for 4K. The measured bitrate in the overlay confirms actual throughput.

---

## Contributing

This fork targets Android enhancements. Fork the repo, create a branch, and open a PR. See upstream [CONTRIBUTING.md](CONTRIBUTING.md) for the full workflow.

---

## Credits

Pylux is built on [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) and [chiaki-ng](https://github.com/streetpea/chiaki-ng). This fork extends [ForWard-Technologies-LLC/Pylux](https://github.com/ForWard-Technologies-LLC/Pylux) with Android-specific features.

---

## Legal

Pylux is intended for use with games and content you own or are licensed to use, on hardware you own, with a valid account or subscription. It does not circumvent copy protection or facilitate piracy. This project is not endorsed or certified by the console manufacturer. All trademarks belong to their respective owners.
