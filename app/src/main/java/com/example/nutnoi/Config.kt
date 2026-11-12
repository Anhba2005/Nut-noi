package com.nutnoi

/** Default firewall command fields start empty so users can enter their own configuration. */
object Config {
    const val DEFAULT_BLOCK_COMMANDS = ""
    const val DEFAULT_UNBLOCK_COMMANDS = ""

    fun parseCommands(text: String, isBlocking: Boolean): List<String> {
        return text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }

    fun validateCommandPair(blockText: String, unblockText: String) {
        // No command-pair validation is performed here.
    }
}
