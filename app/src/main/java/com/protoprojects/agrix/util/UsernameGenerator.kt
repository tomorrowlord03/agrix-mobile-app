package com.protoprojects.agrix.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates unique username suggestions for Hugging Face (and social media).
 * 
 * Strategy:
 * - Base: farmer's name + location + farming context
 * - Suffixes: numbers, years, farming terms
 * - Checks HF availability via public API endpoint
 * - Instagram: provides checking guidance (no public API for availability)
 */
object UsernameGenerator {

    private const val HF_API_CHECK = "https://huggingface.co/api/users/%s"
    private const val MAX_SUGGESTIONS = 10
    private const val REQUEST_TIMEOUT_MS = 5000

    data class UsernameSuggestion(
        val username: String,
        val hfAvailable: Boolean? = null,  // null = not checked yet
        val source: String = "generated",
        val instagramCheckUrl: String = "https://instagram.com/%s"
    )

    /**
     * Generates username suggestions based on farmer profile.
     * Call checkAvailability() on the results to verify HF availability.
     */
    fun generateSuggestions(
        firstName: String,
        lastName: String = "",
        village: String = "",
        district: String = "",
        crop: String = "",
        phoneLast4: String = ""
    ): List<UsernameSuggestion> {
        val baseParts = mutableListOf<String>()
        val cleanFirst = sanitize(firstName)
        val cleanLast = sanitize(lastName)
        val cleanVillage = sanitize(village)
        val cleanDistrict = sanitize(district)
        val cleanCrop = sanitize(crop)

        // Priority 1: Name-based
        if (cleanFirst.isNotEmpty()) baseParts.add(cleanFirst)
        if (cleanFirst.isNotEmpty() && cleanLast.isNotEmpty()) baseParts.add("$cleanFirst$cleanLast")
        if (cleanFirst.isNotEmpty() && cleanVillage.isNotEmpty()) baseParts.add("${cleanFirst}_${cleanVillage}")
        if (cleanFirst.isNotEmpty() && cleanDistrict.isNotEmpty()) baseParts.add("${cleanFirst}_${cleanDistrict}")

        // Priority 2: Farming context
        if (cleanCrop.isNotEmpty() && cleanFirst.isNotEmpty()) baseParts.add("${cleanFirst}_${cleanCrop}")
        if (cleanCrop.isNotEmpty() && cleanVillage.isNotEmpty()) baseParts.add("${cleanCrop}_${cleanVillage}")

        // Priority 3: Generic farmer identities
        baseParts.addAll(listOf(
            "farmer_$cleanFirst",
            "kisan_$cleanFirst",
            "khet_$cleanFirst",
            "farm_$cleanFirst",
            "agri_$cleanFirst"
        ))

        // Generate variations with suffixes
        val suggestions = mutableListOf<UsernameSuggestion>()
        val currentYear = java.time.Year.now().value
        val yearSuffix = currentYear % 100
        val yearSuffix4 = currentYear.toString()
        val phoneSuffix = if (phoneLast4.length >= 4) phoneLast4.takeLast(4) else ""

        val suffixes = listOf(
            "", "_agrix", "_kisan", "_farm", "_26", "_2026",
            "_$yearSuffix", "_$yearSuffix4",
            if (phoneSuffix.isNotEmpty()) "_$phoneSuffix" else "",
            "_v1", "_01", "_001"
        ).filter { it.isNotEmpty() }

        for (base in baseParts.distinct()) {
            for (suffix in suffixes) {
                val username = (base + suffix).lowercase()
                if (username.length <= 39 && username.length >= 3) {  // HF limits
                    suggestions.add(UsernameSuggestion(
                        username = username,
                        source = if (suffix.isEmpty()) "base" else "suffix:$suffix"
                    ))
                    if (suggestions.size >= MAX_SUGGESTIONS) break
                }
            }
            if (suggestions.size >= MAX_SUGGESTIONS) break
        }

        // Add some random unique ones as fallback
        while (suggestions.size < MAX_SUGGESTIONS) {
            val random = "agrix_${cleanFirst}_${(Math.random() * 10000).toInt()}"
            if (!suggestions.any { it.username == random }) {
                suggestions.add(UsernameSuggestion(username = random, source = "random"))
            }
        }

        return suggestions.take(MAX_SUGGESTIONS)
    }

    /**
     * Checks Hugging Face username availability via public API.
     * Returns true if available, false if taken, null if check failed.
     */
    suspend fun checkHFAvailability(username: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(HF_API_CHECK.format(username))
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = REQUEST_TIMEOUT_MS
            connection.readTimeout = REQUEST_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            
            val responseCode = connection.responseCode
            return@withContext when (responseCode) {
                404 -> true   // User doesn't exist = available
                200 -> false  // User exists = taken
                301, 302 -> false  // Redirect = taken
                else -> null  // Uncertain
            }
        } catch (e: Exception) {
            null  // Network error, can't determine
        }
    }

    /**
     * Checks multiple usernames in parallel, returns first available ones.
     */
    suspend fun findAvailable(
        suggestions: List<UsernameSuggestion>,
        maxResults: Int = 3
    ): List<UsernameSuggestion> {
        val checked = suggestions.map { suggestion ->
            val available = checkHFAvailability(suggestion.username)
            suggestion.copy(hfAvailable = available)
        }
        
        // Sort: available first, then unchecked, then taken
        return checked.sortedWith(
            compareByDescending<UsernameSuggestion> { it.hfAvailable == true }
                .thenBy { if (it.hfAvailable == null) 1 else 2 }
        ).take(maxResults)
    }

    /**
     * Instagram availability check - no public API exists.
     * Returns URL for manual checking.
     */
    fun getInstagramCheckUrl(username: String): String {
        return "https://instagram.com/$username"
    }

    private fun sanitize(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_\\-]"), "")  // HF allows alphanumeric, underscore, hyphen
            .replace(Regex("^[-_]+|[-_]+$"), "")   // No leading/trailing separators
            .take(30)  // Leave room for suffixes
    }
}