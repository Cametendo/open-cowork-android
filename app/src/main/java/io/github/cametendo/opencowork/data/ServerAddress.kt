package io.github.cametendo.opencowork.data

/**
 * Builds the server's base URL from what the user typed on the login screen.
 *
 * - **IP + port**: `192.168.1.20` + `3001` -> `http://192.168.1.20:3001`
 * - **Domain**: `cowork.example.com` -> `https://cowork.example.com` (no port needed).
 *   A scheme, port or sub-path typed into the domain field is respected.
 */
object ServerAddress {
    enum class Mode { IP, DOMAIN }

    const val DEFAULT_PORT = "3001"

    class InvalidAddress(message: String) : IllegalArgumentException(message)

    fun build(mode: Mode, host: String, port: String, https: Boolean): String {
        var input = host.trim()
        if (input.isEmpty()) throw InvalidAddress(if (mode == Mode.IP) "Enter the server's IP address" else "Enter the server's domain")

        var scheme = if (https) "https" else "http"
        val explicit = Regex("^(https?)://", RegexOption.IGNORE_CASE).find(input)
        if (explicit != null) {
            scheme = explicit.groupValues[1].lowercase()
            input = input.substring(explicit.value.length)
        }
        input = input.trimEnd('/')
        if (input.isEmpty() || input.any { it.isWhitespace() }) throw InvalidAddress("That address doesn't look right")

        return when (mode) {
            Mode.IP -> {
                // Drop any path or port typed into the IP field; the port field wins.
                var ip = input.substringBefore('/')
                ip = when {
                    ip.startsWith("[") -> ip.substringBefore(']') + "]"
                    ip.count { it == ':' } == 1 -> ip.substringBefore(':')
                    ip.count { it == ':' } > 1 -> "[$ip]" // bare IPv6
                    else -> ip
                }
                val p = port.trim().ifEmpty { DEFAULT_PORT }
                val n = p.toIntOrNull() ?: throw InvalidAddress("The port must be a number")
                if (n !in 1..65535) throw InvalidAddress("The port must be between 1 and 65535")
                "$scheme://$ip:$n"
            }
            Mode.DOMAIN -> "$scheme://$input"
        }
    }
}
