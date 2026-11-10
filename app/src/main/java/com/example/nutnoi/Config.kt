package com.example.nutnoi

/** Fixed, app-owned firewall rules. Only validated numeric app UIDs are interpolated. */
object Config {
    private const val WIFI_INTERFACE = "wlan0"
    private const val MOBILE_INTERFACE = "ccmni+"

    fun blockAppCommands(uid: Int, blockWifi: Boolean, blockMobile: Boolean): List<String> {
        require(uid >= 0) { "UID must be non-negative" }
        return selectedInterfaces(blockWifi, blockMobile).flatMap { iface ->
            listOf("iptables", "ip6tables").map { tool -> addRule(tool, iface, uid) }
        }
    }

    fun unblockAppCommands(uid: Int, blockWifi: Boolean, blockMobile: Boolean): List<String> {
        require(uid >= 0) { "UID must be non-negative" }
        return selectedInterfaces(blockWifi, blockMobile).flatMap { iface ->
            listOf("iptables", "ip6tables").map { tool -> removeRule(tool, iface, uid) }
        }
    }

    private fun selectedInterfaces(blockWifi: Boolean, blockMobile: Boolean): List<String> = buildList {
        if (blockWifi) add(WIFI_INTERFACE)
        if (blockMobile) add(MOBILE_INTERFACE)
    }

    private fun addRule(tool: String, iface: String, uid: Int): String =
        "$tool -C OUTPUT -o $iface -m owner --uid-owner $uid -j DROP || " +
            "$tool -I OUTPUT 1 -o $iface -m owner --uid-owner $uid -j DROP"

    private fun removeRule(tool: String, iface: String, uid: Int): String =
        "while $tool -D OUTPUT -o $iface -m owner --uid-owner $uid -j DROP 2>/dev/null; do :; done"
}
