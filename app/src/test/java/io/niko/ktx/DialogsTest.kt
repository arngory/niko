package io.niko.ktx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogsTest {

    private class Node(val name: String, var base: Node? = null)

    // a dialog's context wraps the Activity, perhaps more than once: the walk finds it under the wrappers, and a
    // chain that leads nowhere or loops ends without one
    @Test
    fun theActivityUnderTheWrappers() {
        val activity = Node("activity")
        val theme = Node("theme", Node("wrapper", activity))
        assertEquals(activity, firstInChain(theme, next = { it.base }, match = { it.name == "activity" }))
        assertEquals(activity, firstInChain(activity, next = { it.base }, match = { it.name == "activity" }))
        assertNull(firstInChain(Node("app", Node("base")), next = { it.base }, match = { it.name == "activity" }))
        val self = Node("self").also { it.base = it }
        assertNull(firstInChain(self, next = { it.base }, match = { it.name == "activity" }))
        val a = Node("a")
        val b = Node("b", a).also { a.base = it }
        assertNull(firstInChain(a, next = { it.base }, match = { it.name == "activity" }))
    }
}
