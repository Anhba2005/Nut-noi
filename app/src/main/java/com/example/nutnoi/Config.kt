package com.example.nutnoi

/** Fixed, app-owned firewall rules. Only validated numeric app UIDs are interpolated. */
object Config {
    private const val WIFI_INTERFACE = "wlan0"
    private const val MOBILE_INTERFACE = "ccmni+"

    fun blockAppCommands(uid: Int): List<String> {
        require(uid >= 0) { "UID must be non-negative" }
        return listOf(
            addRule("iptables", WIFI_INTERFACE, uid),
            addRule("ip6tables", WIFI_INTERFACE, uid),
            addRule("iptables", MOBILE_INTERFACE, uid),
            addRule("ip6tables", MOBILE_INTERFACE, uid)
        )
    }

    fun unblockAppCommands(uid: Int): List<String> {
        require(uid >= 0) { "UID must be non-negative" }
        return listOf(
            removeRule("iptables", WIFI_INTERFACE, uid),
            removeRule("ip6tables", WIFI_INTERFACE, uid),
            removeRule("iptables", MOBILE_INTERFACE, uid),
            removeRule("ip6tables", MOBILE_INTERFACE, uid)
        )
    }

    private fun addRule(tool: String, iface: String, uid: Int): String {
        val rule = "$tool -o $iface -m owner --uid-owner $uid -j DROP"
        return "$tool -C OUTPUT -o $iface -m owner --uid-owner $uid -j DROP || $tool -I OUTPUT 1 -o $iface -m owner --uid-owner $uid -j DROP"
    }

    private fun removeRule(tool: String, iface: String, uid: Int): String =
        "while $tool -D OUTPUT -o $iface -m owner --uid-owner $uid -j DROP 2>/dev/null; do :; done"
}
