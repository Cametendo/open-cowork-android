package io.github.cametendo.opencowork.data

/**
 * Incremental server-sent-events parser. Feed it one line at a time (without the
 * trailing newline); it returns the event's `data` payload when a blank line ends
 * an event. Comment lines (`: ping`) and other fields are ignored.
 */
class SseParser {
    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): String? {
        if (line.isEmpty()) {
            if (!hasData) return null
            val out = data.toString()
            data.setLength(0)
            hasData = false
            return out
        }
        if (line.startsWith(":")) return null
        if (line.startsWith("data:")) {
            if (hasData) data.append('\n')
            data.append(line.substring(5).trimStart())
            hasData = true
        }
        return null
    }

    /** Payload of an event that was cut off by the end of the stream, if any. */
    fun flush(): String? = if (hasData) data.toString().also { data.setLength(0); hasData = false } else null
}
