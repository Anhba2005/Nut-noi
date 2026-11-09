package com.example.nutnoi

/** Fixed, app-owned actions for the floating button. No user-provided shell text is executed. */
object Config {
    const val BLOCK_IPV4 = "iptables -C INPUT -i ccmni+ -j DROP || iptables -I INPUT 1 -i ccmni+ -j DROP"
    const val BLOCK_IPV6 = "ip6tables -C INPUT -i ccmni+ -j DROP || ip6tables -I INPUT 1 -i ccmni+ -j DROP"

    const val UNBLOCK_IPV4 = "while iptables -D INPUT -i ccmni+ -j DROP 2>/dev/null; do :; done"
    const val UNBLOCK_IPV6 = "while ip6tables -D INPUT -i ccmni+ -j DROP 2>/dev/null; do :; done"
}
