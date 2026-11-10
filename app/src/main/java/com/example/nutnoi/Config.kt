package com.example.nutnoi

/** Global inbound firewall rules for explicitly selected, detected interfaces. */
object Config {
    private val interfaceNamePattern = Regex("[A-Za-z0-9_.:+-]{1,15}")

    fun blockCommands(interfaces: Collection<String>): List<String> = rulesFor(interfaces) { tool, iface ->
        "$tool -C INPUT -i $iface -j DROP || $tool -I INPUT 1 -i $iface -j DROP"
    }

    fun unblockCommands(interfaces: Collection<String>): List<String> = rulesFor(interfaces) { tool, iface ->
        "while $tool -D INPUT -i $iface -j DROP 2>/dev/null; do :; done"
    }

    private fun rulesFor(
        interfaces: Collection<String>,
        command: (tool: String, iface: String) -> String
    ): List<String> {
        val validated = interfaces.distinct().onEach { iface ->
            require(interfaceNamePattern.matches(iface)) { "Invalid network interface name" }
        }
        return validated.flatMap { iface ->
            listOf("iptables", "ip6tables").map { tool -> command(tool, iface) }
        }
    }
}
