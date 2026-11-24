package io.niko.ui.profile

/** A JSON text that did not parse: the [line] the parser stopped on (1-based, null when it named no place) and why. */
data class JsonError(val line: Int?, val message: String) {
    companion object {
        private val AT = Regex("""\s+at character (\d+)(?: of .*)?$""", RegexOption.DOT_MATCHES_ALL)
        private val GSON = Regex("""\s+at line (\d+) column \d+.*$""", RegexOption.DOT_MATCHES_ALL)

        /**
         * The parser's [message] about [text] as an error with its line: org.json ends its message with «at character
         * N of <the text>», Gson with «at line N column M path …»; either tail is dropped from what is shown.
         */
        fun of(message: String, text: String): JsonError {
            AT.find(message)?.let { m ->
                val at = m.groupValues[1].toIntOrNull()?.coerceIn(0, text.length) ?: 0
                // the parser reports the character after the one it stopped on
                val line = text.take((at - 1).coerceAtLeast(0)).count { it == '\n' } + 1
                return JsonError(line, message.removeRange(m.range).trim())
            }
            GSON.find(message)?.let { m ->
                return JsonError(m.groupValues[1].toIntOrNull(), message.removeRange(m.range).trim())
            }
            return JsonError(null, message.trim())
        }
    }
}

/** A text with its selection, as the editor's history keeps it. */
data class JsonText(val text: String, val start: Int = text.length, val end: Int = start)

/**
 * The editor's undo and redo: the texts before [current] and those undone. A change of the text adds a step (typing
 * within [JOIN_MS] of the last step joins it: a word is one step, not a letter); a move of the caret alone does not.
 */
class JsonHistory(first: JsonText, private val limit: Int = 200) {

    companion object {
        const val JOIN_MS = 700L
    }

    private val undo = ArrayDeque<JsonText>()
    private val redo = ArrayDeque<JsonText>()
    private var lastStep = 0L

    var current = first
        private set

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** [next] is what the field shows now, at [now] ms; [typed]: a keystroke, which may join the step before it. */
    fun set(next: JsonText, now: Long, typed: Boolean = true) {
        if (next.text != current.text) {
            val join = typed && undo.isNotEmpty() && now - lastStep < JOIN_MS
            if (!join) {
                undo.addLast(current)
                if (undo.size > limit) undo.removeFirst()
            }
            lastStep = if (typed) now else 0L
            redo.clear()
        }
        current = next
    }

    fun undo(): JsonText? {
        val back = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        current = back
        lastStep = 0L
        return back
    }

    fun redo(): JsonText? {
        val forward = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        current = forward
        lastStep = 0L
        return forward
    }

    /** [insert] in place of the selection, the caret after it: a key of the row over the keyboard. */
    fun insert(insert: String, now: Long): JsonText {
        val c = current
        val (a, b) = minOf(c.start, c.end).coerceIn(0, c.text.length) to maxOf(c.start, c.end).coerceIn(0, c.text.length)
        val next = JsonText(c.text.replaceRange(a, b, insert), a + insert.length)
        set(next, now, typed = false)
        return next
    }
}
