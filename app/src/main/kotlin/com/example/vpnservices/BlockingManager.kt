package com.example.vpnservices

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object BlocklistManager {
    private val blockedSites = mutableSetOf(
        "example.com",
        "facebook.com",
        "youtube.com"
    )
    private var prefs: SharedPreferences? = null
    private const val PREFS_NAME = "vpn_blocklist"
    private const val KEY_BLOCKED_SITES = "blocked_sites"

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadBlockedSites()
        }
    }
    fun isDomainBlocked(domain: String): Boolean {
        val normalizedDomain = domain.trim().lowercase()

        // Check for exact domain match
        if (blockedSites.contains(normalizedDomain)) {
            return true
        }

        // Check if domain ends with any blocked site
        for (blockedSite in blockedSites) {
            if (normalizedDomain == blockedSite || normalizedDomain.endsWith(".$blockedSite")) {
                return true
            }
        }

        return false
    }
    private fun loadBlockedSites() {
        prefs?.let { preferences ->
            val savedSites = preferences.getStringSet(KEY_BLOCKED_SITES, null)
            if (savedSites != null) {
                blockedSites.clear()
                blockedSites.addAll(savedSites)
                Log.d("BlocklistManager", "Loaded ${blockedSites.size} blocked sites")
            } else {
                Log.d("BlocklistManager", "No saved blocklist found, using defaults")
            }
        }
    }

    private fun saveBlockedSites() {
        prefs?.edit()?.putStringSet(KEY_BLOCKED_SITES, blockedSites)?.apply()
        Log.d("BlocklistManager", "Saved ${blockedSites.size} blocked sites")
    }

    fun getBlockedSites(): Set<String> {
        return blockedSites.toSet()
    }

    fun addBlockedSite(domain: String) {
        val normalizedDomain = domain.trim().lowercase()
        if (normalizedDomain.isNotEmpty()) {
            blockedSites.add(normalizedDomain)
            saveBlockedSites()
            Log.d("BlocklistManager", "Added $normalizedDomain to blocklist")
        }
    }

    fun removeBlockedSite(domain: String) {
        val normalizedDomain = domain.trim().lowercase()
        if (blockedSites.remove(normalizedDomain)) {
            saveBlockedSites()
            Log.d("BlocklistManager", "Removed $normalizedDomain from blocklist")
        }
    }

    fun resetToDefault() {
        blockedSites.clear()
        blockedSites.addAll(setOf("example.com", "facebook.com", "youtube.com"))
        saveBlockedSites()
        Log.d("BlocklistManager", "Reset blocklist to defaults")
    }
}