// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.cloudplay

object CloudLocale
{
	const val DEFAULT = "en-US"

	/** Locale codes supported for cloud streaming language selection. */
	val SUPPORTED_LOCALES = listOf(
		"en-US", "en-GB", "de-DE", "fr-FR", "fi-FI"
	)

	fun toImagicLocale(stored: String): String = stored.lowercase()

	private val STORE_COUNTRY_MAP: Map<String, String> = mapOf(
		"FI" to "GB",
		"SE" to "GB",
		"NO" to "GB",
		"DK" to "GB",
		"IS" to "GB",
		"IE" to "GB",
		"AT" to "DE",
		"CH" to "DE",
		"BE" to "FR",
		"PT" to "ES",
	)

	fun parseStorePath(stored: String): Pair<String, String>
	{
		val parts = stored.split("-", limit = 2)
		val language = parts.getOrNull(0)?.lowercase()?.takeIf { it.isNotEmpty() } ?: "en"
		val rawCountry = parts.getOrNull(1)?.uppercase()?.takeIf { it.isNotEmpty() } ?: "US"
		val country = STORE_COUNTRY_MAP[rawCountry] ?: rawCountry
		return country to language
	}

	fun fromSession(language: String?, country: String?): String?
	{
		val lang = language?.trim().orEmpty()
		val cty = country?.trim().orEmpty()
		if (lang.isEmpty() || cty.isEmpty())
			return null
		return "$lang-${cty.uppercase()}"
	}

	/**
	 * Map datacenter prefix (first 3 chars of 4-letter code) to the locales it serves.
	 * Game language is tied to the datacenter region.
	 */
	private val DATACENTER_LOCALE_MAP: Map<String, List<String>> = mapOf(
		"fra" to listOf("de-DE"),           // Frankfurt, Germany
		"lon" to listOf("en-GB"),           // London, UK
		"sto" to listOf("en-US", "fi-FI"),  // Stockholm, Nordic (English + Finnish)
		"par" to listOf("fr-FR"),           // Paris, France
	)

	/** Get the datacenter prefix for a given locale (e.g. "de-DE" → "fra"). */
	fun localeToDatacenterPrefix(locale: String): String? =
		DATACENTER_LOCALE_MAP.entries.firstOrNull { locale in it.value }?.key

	/** Get all locales served by a given datacenter name (e.g. "stoa" → ["en-GB", "fi-FI"]). */
	fun datacenterToLocales(datacenterName: String): List<String> =
		DATACENTER_LOCALE_MAP[datacenterName.take(3).lowercase()] ?: emptyList()

	/**
	 * Filter locales to only those that have at least one matching datacenter
	 * in the provided list of datacenter names.
	 */
	fun filterLocalesByDatacenters(locales: List<String>, datacenterNames: List<String>): List<String>
	{
		if (datacenterNames.isEmpty()) return locales
		val availableLocales = datacenterNames.flatMap { datacenterToLocales(it) }.toSet()
		if (availableLocales.isEmpty()) return locales
		return locales.filter { it in availableLocales }
	}

	/** Non-fatal warning when locale could not be learned from Kamaji (catalog may use en-US). */
	fun unconfiguredWarning(): String =
		"Could not detect your PlayStation region. The catalog may not match your store."
}
