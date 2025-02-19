package com.example.vpnservices



object BlocklistManager {
    private val blockedSites = mutableSetOf(
        "facebook.com",
        "youtube.com",
        "example.com"
    )

    fun getBlockedSites(): Set<String> = blockedSites

    fun addBlockedSite(domain: String) {
        blockedSites.add(domain)
    }

    fun removeBlockedSite(domain: String) {
        blockedSites.remove(domain)
    }
}
