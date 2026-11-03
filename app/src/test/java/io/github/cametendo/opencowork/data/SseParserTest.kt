package io.github.cametendo.opencowork.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    private fun parse(text: String): List<String> {
        val p = SseParser()
        val out = text.split("\n").mapNotNull { p.feed(it) }.toMutableList()
        p.flush()?.let { out += it }
        return out
    }

    @Test fun singleEvents() = assertEquals(listOf("""{"a":1}""", """{"b":2}"""), parse("data: {\"a\":1}\n\ndata: {\"b\":2}\n\n"))
    @Test fun ignoresPings() = assertEquals(listOf("x"), parse(": ping\n\ndata: x\n\n: ping\n\n"))
    @Test fun multiLineData() = assertEquals(listOf("one\ntwo"), parse("data: one\ndata: two\n\n"))
    @Test fun flushesUnterminated() = assertEquals(listOf("tail"), parse("data: tail"))
    @Test fun blankLinesAlone() { val p = SseParser(); assertNull(p.feed("")); assertNull(p.feed("")) }
    @Test fun noSpaceAfterColon() = assertEquals(listOf("v"), parse("data:v\n\n"))
}
