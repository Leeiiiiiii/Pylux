// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.common

/**
 * REMOTE_PLAY_ONLY mode — hides all Cloud Play and PSN login UI.
 *
 * To fully revert this feature flag and restore Cloud Play:
 *
 * 1. Set REMOTE_PLAY_ONLY = false below (all UI will re-appear automatically).
 *
 * If you also want to remove the flag code entirely:
 *
 * 2. MainActivity.kt — remove the two `isGone` lines for cloudPlayButton/cloudPlayIcon,
 *    and remove the `if (FeatureFlags.REMOTE_PLAY_ONLY) 1 else` prefix in ViewPagerAdapter.getItemCount().
 *
 * 3. RemotePlayFragment.kt — remove the two `isGone` lines for refreshPsnButton/refreshPsnLabelButton.
 *
 * 4. SettingsFragment.kt — remove the block guarded by `if (FeatureFlags.REMOTE_PLAY_ONLY)` that
 *    hides the psn_login preference and the cloud preference categories.
 *
 * 5. Delete this file.
 */
object FeatureFlags {
	const val REMOTE_PLAY_ONLY = false
}
