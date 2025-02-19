package com.example.vpnservices




object BlocklistManager {
    private val blockedSites = mutableSetOf("example.com", "facebook.com", "youtube.com")

    fun getBlockedSites(): Set<String> {
        return blockedSites
    }

    fun addBlockedSite(domain: String) {
        blockedSites.add(domain)
    }

    fun removeBlockedSite(domain: String) {
        blockedSites.remove(domain)
    }
}

