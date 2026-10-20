package app.slidedictate

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors the string surgery DictationService.insert() performs on the focused field. */
class InsertionLogicTest {

    private fun build(cur: String, selStart: Int, selEnd: Int, text: String): String {
        val s = if (selStart < 0) cur.length else selStart.coerceAtMost(cur.length)
        val e = if (selEnd < 0) s else selEnd.coerceIn(s, cur.length)
        val lead = if (s > 0 && !cur[s - 1].isWhitespace()) " " else ""
        val added = lead + text
        return cur.substring(0, s) + added + cur.substring(e)
    }

    @Test fun appendsAtEndWithSmartSpace() {
        assertEquals("hello world", build("hello", 5, 5, "world"))
    }

    @Test fun noLeadingSpaceAfterWhitespace() {
        assertEquals("hello, world", build("hello, ", 7, 7, "world"))
    }

    @Test fun emptyFieldInsertsPlainText() {
        assertEquals("world", build("", 0, 0, "world"))
    }

    @Test fun replacesSelection() {
        assertEquals("hi there", build("hello there", 0, 5, "hi"))
    }

    @Test fun negativeSelectionFallsBackToEnd() {
        assertEquals("hey you", build("hey", -1, -1, "you"))
    }

    @Test fun multilineKeepsSmartSpace() {
        assertEquals("line one\nnext", build("line one\n", 9, 9, "next"))
    }
}
