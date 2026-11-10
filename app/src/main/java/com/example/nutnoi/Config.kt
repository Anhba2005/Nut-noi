package com.example.nutnoi

/** Restricts editable firewall input to simple INPUT-chain DROP rules. */
object Config {
    const val DEFAULT_BLOCK_COMMANDS = """iptables -A INPUT -i wlan0 -j DROP
iptables -I INPUT 1 -i ccmni+ -j DROP
ip6tables -I INPUT 1 -i ccmni+ -j DROP"""

    const val DEFAULT_UNBLOCK_COMMANDS = """iptables -D INPUT -i wlan0 -j DROP
iptables -D INPUT -i ccmni+ -j DROP
ip6tables -D INPUT -i ccmni+ -j DROP"""

    private val interfaceNamePattern = Regex("[A-Za-z0-9_.:+-]{1,15}")

    /** Validate and return one safe command per line; arbitrary shell syntax is rejected. */
    fun parseCommands(text: String, isBlocking: Boolean): List<String> {
        val commands = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(commands.isNotEmpty()) { "Enter at least one command" }
        require(commands.size <= 12) { "Too many commands" }

        commands.forEach { command ->
            val parts = command.split(Regex("\\s+"))
            // The shortest valid command has seven tokens, e.g.:
            // iptables -A INPUT -i wlan0 -j DROP
            require(parts.size >= 7) { "Invalid firewall command" }
            require(parts[0] == "iptables" || parts[0] == "ip6tables") {
                "Only iptables/ip6tables are allowed"
            }
            val operation = parts[1]
            require(if (isBlocking) operation == "-A" || operation == "-I" else operation == "-D") {
                "Use -A or -I for blocking and -D for unblocking"
            }
            require(parts[2] == "INPUT") { "Only INPUT chain rules are allowed" }
            var index = 3
            if (operation == "-I" && parts.getOrNull(index) != "-i") {
                require(parts.getOrNull(index)?.toIntOrNull()?.let { it in 1..999 } == true) {
                    "Invalid insert position"
                }
                index++
            }
            require(parts.getOrNull(index) == "-i") { "Expected -i interface" }
            val iface = parts.getOrNull(index + 1)
            require(iface != null && interfaceNamePattern.matches(iface)) { "Invalid interface name" }
            require(parts.getOrNull(index + 2) == "-j" && parts.getOrNull(index + 3) == "DROP") {
                "Only DROP targets are allowed"
            }
            require(index + 4 == parts.size) { "Unexpected extra command arguments" }
        }
        return commands
    }

    /** Require the removal list to address the same tools/interfaces as the add list. */
    fun validateCommandPair(blockText: String, unblockText: String) {
        val block = parseCommands(blockText, isBlocking = true).map(::ruleKey).sorted()
        val unblock = parseCommands(unblockText, isBlocking = false).map(::ruleKey).sorted()
        require(block == unblock) { "Block and unblock commands must use matching tools and interfaces" }
    }

    private fun ruleKey(command: String): String {
        val parts = command.split(Regex("\\s+"))
        val interfaceIndex = parts.indexOf("-i")
        return "${parts[0]}:${parts[interfaceIndex + 1]}"
    }
}
