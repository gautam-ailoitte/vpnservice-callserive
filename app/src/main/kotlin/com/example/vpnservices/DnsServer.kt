package com.example.vpnservices

import org.xbill.DNS.*

class DNSServer(private var blockedDomains: List<String>) {
    private lateinit var server: SimpleServer
    private val upstreamDns = ResolverConfig.getCurrentConfig().servers() // Get system DNS servers

    fun start() {
        try {
            server = SimpleServer()
            server.address = InetAddress.getByName("127.0.0.1") // Listen on localhost
            server.port = 5353 // Choose a local port

            server.addMessageListener { request, response ->
                handleDnsRequest(request, response)
            }

            server.start()
            Log.d("DNSServer", "DNS server started on port ${server.port}")
        } catch (e: Exception) {
            Log.e("DNSServer", "Error starting DNS server: ${e.message}")
        }
    }

    private fun handleDnsRequest(request: Message, response: Message) {
        val question = request.questionSection.firstOrNull()
        if (question!= null) {
            val domain = question.name.toString(true) // Get domain from question
            Log.d("DNSServer", "Received request for domain: $domain")

            if (blockedDomains.contains(domain)) {
                Log.d("DNSServer", "Blocking domain: $domain")
                // Return NXDOMAIN response
                response.header.rcode = Rcode.NXDOMAIN
            } else {
                // Forward request to upstream DNS server
                try {
                    val upstreamResolver = SimpleResolver(upstreamDns.random()) // Use a random upstream server
                    val upstreamResponse = upstreamResolver.send(request)
                    response.copyFrom(upstreamResponse)
                } catch (e: Exception) {
                    Log.e("DNSServer", "Error forwarding DNS request: ${e.message}")
                    response.header.rcode = Rcode.SERVFAIL // Server failure
                }
            }
        } else {
            response.header.rcode = Rcode.FORMERR // Format error
        }
    }

    fun stop() {
        try {
            server.stop()
            Log.d("DNSServer", "DNS server stopped")
        } catch (e: Exception) {
            Log.e("DNSServer", "Error stopping DNS server: ${e.message}")
        }
    }

    fun addBlockedDomain(domain: String) {
        if (!blockedDomains.contains(domain)) {
            blockedDomains = blockedDomains + domain
            Log.d("DNSServer", "Added blocked domain: $domain")
        }
    }

    fun removeBlockedDomain(domain: String) {
        if (blockedDomains.contains(domain)) {
            blockedDomains = blockedDomains - domain
            Log.d("DNSServer", "Removed blocked domain: $domain")
        }
    }
}