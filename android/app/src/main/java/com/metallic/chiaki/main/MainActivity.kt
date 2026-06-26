// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.main

import com.metallic.chiaki.common.ext.alertDialogBuilder
import com.metallic.chiaki.common.ext.enableFocusableInTouchModeForTv
import com.metallic.chiaki.common.ext.isTv
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import androidx.core.view.isGone
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pylux.stream.R
import com.metallic.chiaki.common.AppIntegrityManager
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.common.getDatabase
import com.pylux.stream.databinding.ActivityMainBinding
import com.metallic.chiaki.settings.SettingsActivity

class MainActivity : AppCompatActivity()
{

	private lateinit var viewModel: MainViewModel
	private lateinit var binding: ActivityMainBinding
	private lateinit var preferences: Preferences
	private var integrityManager: AppIntegrityManager? = null

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		// Initialize SSL CA bundle for native curl+mbedTLS (must happen before any holepunch calls)
		try { com.metallic.chiaki.lib.initNativeSsl(cacheDir.absolutePath) }
		catch(e: Exception) { android.util.Log.e("MainActivity", "Failed to init native SSL", e) }

		preferences = Preferences(this)
		
		integrityManager = AppIntegrityManager(this)
		integrityManager?.validateAppState(this) { isValid ->
			if (isValid) {
				android.util.Log.w("MainActivity", "✓ Application integrity verified - proceeding with launch")
			} else {
				android.util.Log.e("MainActivity", "✗ Application integrity check FAILED - blocking launch")
			}
		}
		
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		title = ""
		setSupportActionBar(binding.toolbar)
		binding.toolbar.setContentInsetsRelative(0, 0)

		viewModel = ViewModelProvider(this, viewModelFactory {
			MainViewModel(getDatabase(this), preferences)
		}).get(MainViewModel::class.java)

		setupNavigation()
		observeViewModel()

		binding.viewPager.setCurrentItem(1, false)
		binding.bottomNavigation.menu.findItem(R.id.nav_cloud_play).isChecked = true

		binding.root.post {
			applyViewPagerPageFocusIsolation(1)
		}
	}

	private fun setupNavigation()
	{
		val adapter = ViewPagerAdapter(this)
		binding.viewPager.adapter = adapter
		binding.viewPager.offscreenPageLimit = 1
		binding.viewPager.isUserInputEnabled = false

		binding.bottomNavigation.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.nav_cloud_play -> binding.viewPager.setCurrentItem(1, true)
				R.id.nav_remote_play -> binding.viewPager.setCurrentItem(0, true)
				R.id.nav_settings -> {
					startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
					false
				}
				else -> false
			}
			true
		}

		binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				super.onPageSelected(position)
				applyViewPagerPageFocusIsolation(position)
				val navItem = when (position) {
					0 -> R.id.nav_remote_play
					else -> R.id.nav_cloud_play
				}
				binding.bottomNavigation.menu.findItem(navItem).isChecked = true
			}
		})

		// WiFi discovery toggle
		binding.wifiIcon.setOnClickListener {
			viewModel.discoveryManager.active = !(viewModel.discoveryActive.value ?: false)
		}
		
		// Settings
		binding.settingsIcon.setOnClickListener {
			Intent(this, SettingsActivity::class.java).also {
				startActivity(it)
			}
		}

		if (isTv()) {
			binding.root.enableFocusableInTouchModeForTv(this)
			val primaryFocusHighlight = View.OnFocusChangeListener { v, hasFocus ->
				if (hasFocus) {
					v.background = android.graphics.drawable.GradientDrawable().apply {
						shape = android.graphics.drawable.GradientDrawable.RECTANGLE
						cornerRadius = 50f
						setColor(0x30FFD700.toInt())
						setStroke(3, 0xCCFFD700.toInt())
					}
				} else {
					v.setBackgroundColor(0x00000000)
				}
			}
			binding.wifiIcon.onFocusChangeListener = primaryFocusHighlight
			binding.settingsIcon.onFocusChangeListener = primaryFocusHighlight
		}
	}

	/** Keyboard/gamepad routing — always on Cloud Play */
	override fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

		// LB/RB: switch Catalog/Library tabs
		when (event.keyCode) {
			KeyEvent.KEYCODE_BUTTON_L1 -> {
				window.decorView.findViewById<View>(R.id.catalogTabButton)?.performClick()
				return true
			}
			KeyEvent.KEYCODE_BUTTON_R1 -> {
				window.decorView.findViewById<View>(R.id.libraryTabButton)?.performClick()
				return true
			}
		}
		if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)

		if (refocusIfWrongViewPagerPage()) return true

		val focused = currentFocus
		val cloudRv = window.decorView.findViewById<RecyclerView>(R.id.gamesRecyclerView)

		if (focused == null) {
			val lm = cloudRv?.layoutManager as? GridLayoutManager
			lm?.findViewByPosition(lm.findFirstVisibleItemPosition())?.let {
				it.isFocusableInTouchMode = true
				it.requestFocusFromTouch()
			}
			return true
		}

		val secondaryIds = setOf(
			R.id.catalogTabButton, R.id.libraryTabButton, R.id.ownedToggleButton,
			R.id.headerFavoritesButton, R.id.headerSortButton,
			R.id.headerSearchButton, R.id.headerRefreshButton
		)
		val primaryIds = setOf(R.id.settingsIcon, R.id.wifiIcon)

		val focusedInCloud = cloudRv?.findContainingItemView(focused)
		val isFab         = focused.id == R.id.floatingActionButton
		val isLoginButton = focused.id == R.id.loginButton

		fun focusSecondaryHeader() {
			window.decorView.findViewById<View>(R.id.catalogTabButton)?.let {
				it.isFocusableInTouchMode = true
				it.requestFocusFromTouch()
			}
		}

		fun focusLoginButton() {
			window.decorView.findViewById<View>(R.id.loginButton)?.let {
				if (it.isShown) {
					it.isFocusableInTouchMode = true
					it.requestFocusFromTouch()
				}
			}
		}

		when (event.keyCode) {
			KeyEvent.KEYCODE_DPAD_UP -> {
				when {
					focused.id in primaryIds -> return true
					focused.id in secondaryIds -> return true
					isFab -> return true
					isLoginButton -> { focusSecondaryHeader(); return true }
					focusedInCloud != null -> {
						val pos  = cloudRv!!.getChildAdapterPosition(focusedInCloud)
						val span = (cloudRv.layoutManager as? GridLayoutManager)?.spanCount ?: 2
						if (pos in 0 until span) { focusSecondaryHeader(); return true }
						return super.dispatchKeyEvent(event)
					}
				}
			}

			KeyEvent.KEYCODE_DPAD_DOWN -> {
				when {
					focused.id in primaryIds -> {
						focusSecondaryHeader()
						return true
					}
					focused.id in secondaryIds -> {
						val lm    = cloudRv?.layoutManager as? GridLayoutManager
						val first = lm?.findViewByPosition(lm.findFirstVisibleItemPosition())
						if (first != null) {
							first.isFocusableInTouchMode = true
							first.requestFocusFromTouch()
							return true
						}
						focusLoginButton()
						return true
					}
					isFab -> return true
					isLoginButton -> return true
					focusedInCloud != null -> {
						val pos        = cloudRv!!.getChildAdapterPosition(focusedInCloud)
						val lastLoaded = (cloudRv.adapter?.itemCount ?: 0) - 1
						if (pos < 0 || pos >= lastLoaded) return true
						return super.dispatchKeyEvent(event)
					}
					else -> return super.dispatchKeyEvent(event)
				}
			}
		}

		return super.dispatchKeyEvent(event)
	}

	@Suppress("OVERRIDE_DEPRECATION")
	override fun onBackPressed()
	{
		val focused = currentFocus
		val primaryIds = setOf(R.id.settingsIcon, R.id.wifiIcon)

		when {
			focused == null || focused.id in primaryIds -> showExitConfirmation()
			focused?.let { window.decorView.findViewById<RecyclerView>(R.id.gamesRecyclerView)?.findContainingItemView(it) } != null -> {
				focused!!.requestFocus()
			}
			else -> showExitConfirmation()
		}
	}

	private fun showExitConfirmation()
	{
		alertDialogBuilder()
			.setMessage("Exit app?")
			.setPositiveButton("Exit") { _, _ -> finish() }
			.setNegativeButton("Cancel", null)
			.show()
	}

	private fun observeViewModel()
	{
		viewModel.discoveryActive.observe(this, Observer { active ->
			binding.wifiIcon.setImageResource(
				if (active) R.drawable.ic_discover_on else R.drawable.ic_discover_off
			)
		})
	}
	
	override fun onDestroy()
	{
		super.onDestroy()
		integrityManager?.release()
	}

	private fun isDescendantOf(descendant: View, ancestor: View): Boolean
	{
		var p: ViewParent? = descendant.parent
		while (p != null)
		{
			if (p === ancestor) return true
			p = p.parent
		}
		return false
	}

	/**
	 * ViewPager2 keeps the off-screen page attached; catalog views were still in the focus
	 * tree and could steal focus from the Remote Play tab. Block descendants on the hidden page.
	 */
	private fun applyViewPagerPageFocusIsolation(selectedPage: Int)
	{
		val remoteRoot = supportFragmentManager.fragments.filterIsInstance<RemotePlayFragment>().firstOrNull()?.view as? ViewGroup
		val cloudRoot = supportFragmentManager.fragments.filterIsInstance<CloudPlayFragment>().firstOrNull()?.view as? ViewGroup
		remoteRoot?.descendantFocusability =
			if (selectedPage == 0) ViewGroup.FOCUS_BEFORE_DESCENDANTS
			else ViewGroup.FOCUS_BLOCK_DESCENDANTS
		cloudRoot?.descendantFocusability =
			if (selectedPage == 1) ViewGroup.FOCUS_BEFORE_DESCENDANTS
			else ViewGroup.FOCUS_BLOCK_DESCENDANTS
		val focused = currentFocus
		if (focused != null && remoteRoot != null && selectedPage == 1 && isDescendantOf(focused, remoteRoot))
		{
			binding.bottomNavigation.menu.getItem(0).let {
				binding.bottomNavigation.isFocusableInTouchMode = true
				binding.bottomNavigation.requestFocus()
			}
		}
	}

	/** If focus landed on the inactive ViewPager page, pull it back. */
	private fun refocusIfWrongViewPagerPage(): Boolean
	{
		val focused = currentFocus ?: return false
		val remoteRoot = supportFragmentManager.fragments.filterIsInstance<RemotePlayFragment>().firstOrNull()?.view
			?: return false
		if (!isDescendantOf(focused, remoteRoot)) return false
		binding.bottomNavigation.isFocusableInTouchMode = true
		binding.bottomNavigation.requestFocusFromTouch()
		return true
	}

	private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity)
	{
		override fun getItemCount(): Int = 2

		override fun createFragment(position: Int): Fragment
		{
			return when(position)
			{
				0 -> RemotePlayFragment()
				1 -> CloudPlayFragment()
				else -> RemotePlayFragment()
			}
		}
	}
}
