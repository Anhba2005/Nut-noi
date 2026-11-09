package com.example.nutnoi

/** Fixed app-owned firewall rules. Arbitrary user-provided shell commands are not executed. */
object Config {
    const val BLOCK_MOBILE_IPV4 = "iptables -C INPUT -i ccmni+ -j DROP || iptables -I INPUT 1 -i ccmni+ -j DROP"
    const val BLOCK_MOBILE_IPV6 = "ip6tables -C INPUT -i ccmni+ -j DROP || ip6tables -I INPUT 1 -i ccmni+ -j DROP"
    const val UNBLOCK_MOBILE_IPV4 = "while iptables -D INPUT -i ccmni+ -j DROP 2>/dev/null; do :; done"
    const val UNBLOCK_MOBILE_IPV6 = "while ip6tables -D INPUT -i ccmni+ -j DROP 2>/dev/null; do :; done"

    const val BLOCK_WIFI_IPV4 = "iptables -C INPUT -i wlan0 -j DROP || iptables -I INPUT 1 -i wlan0 -j DROP"
    const val UNBLOCK_WIFI_IPV4 = "while iptables -D INPUT -i wlan0 -j DROP 2>/dev/null; do :; done"
}
