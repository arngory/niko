package io.niko.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON editor's error line and its undo history. */
class JsonEditTest {

    private val text = "{\n  \"type\": \"vless\",\n  \"server\": \"203.0.113.7\"\n  \"server_port\": 443\n}"

    @Test
    fun orgJsonMessageGivesTheLineAndLosesItsTail() {
        // org.json stops on the quote that opens line 4 and reports the character after it
        val at = text.indexOf("\"server_port\"") + 1
        val e = JsonError.of("Unterminated object at character $at of $text", text)
        assertEquals(4, e.line)
        assertEquals("Unterminated object", e.message)
    }

    @Test
    fun firstAndLastCharacters() {
        assertEquals(1, JsonError.of("Value x at character 1 of x", "x").line)
        assertEquals(5, JsonError.of("End of input at character ${text.length} of $text", text).line)
        // a place past the text is held to it
        assertEquals(5, JsonError.of("End of input at character 9999 of $text", text).line)
    }

    @Test
    fun gsonMessage() {
        val e = JsonError.of("Expected ':' at line 3 column 12 path \$.server", text)
        assertEquals(3, e.line)
        assertEquals("Expected ':'", e.message)
    }

    @Test
    fun messageWithoutPlace() {
        val e = JsonError.of("Value null of type org.json.JSONObject\$1 cannot be converted to JSONObject", "null")
        assertNull(e.line)
        assertTrue(e.message.startsWith("Value null"))
    }

    @Test
    fun typingJoinsIntoOneStepAfterAPauseANewOne() {
        val h = JsonHistory(JsonText(""))
        h.set(JsonText("a"), 1000)
        h.set(JsonText("ab"), 1100)
        h.set(JsonText("abc"), 1200)
        h.set(JsonText("abc d"), 5000)
        assertEquals("abc", h.undo()!!.text)
        assertEquals("", h.undo()!!.text)
        assertNull(h.undo())
        assertEquals("abc", h.redo()!!.text)
        assertEquals("abc d", h.redo()!!.text)
        assertFalse(h.canRedo)
    }

    @Test
    fun aCaretMoveIsNoStepAndAnEditDropsTheRedo() {
        val h = JsonHistory(JsonText("ab"))
        h.set(JsonText("ab", 1), 1000)
        assertFalse(h.canUndo)
        h.set(JsonText("abc"), 2000)
        h.undo()
        assertTrue(h.canRedo)
        h.set(JsonText("abX"), 9000)
        assertFalse(h.canRedo)
        assertEquals("ab", h.undo()!!.text)
    }

    @Test
    fun aKeyReplacesTheSelectionAndIsAStepOfItsOwn() {
        val h = JsonHistory(JsonText("{ab}", 1, 3))
        val next = h.insert("\"", 1000)
        assertEquals(JsonText("{\"}", 2), next)
        // a key right after another does not join it
        h.insert(":", 1001)
        assertEquals("{\":}", h.current.text)
        assertEquals("{\"}", h.undo()!!.text)
        assertEquals("{ab}", h.undo()!!.text)
        // typing after a key starts its own step too
        h.redo()
        h.set(JsonText("{\"x}", 3), 1002)
        assertEquals("{\"}", h.undo()!!.text)
    }

    @Test
    fun theHistoryIsBounded() {
        val h = JsonHistory(JsonText(""), limit = 3)
        for (i in 1..10) h.set(JsonText("x".repeat(i)), i * 10_000L)
        var steps = 0
        while (h.undo() != null) steps++
        assertEquals(3, steps)
        assertEquals("x".repeat(7), h.current.text)
    }
}
