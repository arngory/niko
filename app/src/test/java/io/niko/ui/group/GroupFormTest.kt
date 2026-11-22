package io.niko.ui.group

import io.niko.GroupOrder
import io.niko.GroupType
import io.niko.database.ProxyGroup
import io.niko.database.SubscriptionBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The group's screen's form: read from a group and written back as the old screen did. */
class GroupFormTest {

    private fun subscribed(link: String) = ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
        name = "provider.net"
        subscription = SubscriptionBean().apply {
            initializeDefaultValues()
            this.link = link
            fallbackLink = "https://spare.example/sub"
            hwidHost = "provider.net"
            subscriptionUserinfo = "upload=1; download=2; total=3"
            announce = "hello"
            autoUpdate = true
            autoUpdateDelay = 720
        }
    }

    @Test
    fun readAndWrittenBackUnchanged() {
        val group = subscribed("https://provider.net/sub/abc")
        val form = GroupForm.of(group)
        assertTrue(form.autoUpdate)
        assertEquals(720, form.autoUpdateDelay)
        form.applyTo(group, "Own group")
        assertEquals("https://spare.example/sub", group.subscription!!.fallbackLink)
        assertEquals("upload=1; download=2; total=3", group.subscription!!.subscriptionUserinfo)
        assertEquals(GroupForm.of(group), form)
    }

    @Test
    fun anotherLinkDropsWhatTheOldOneGave() {
        val group = subscribed("https://provider.net/sub/abc")
        GroupForm.of(group).copy(link = " https://other.example/sub ").applyTo(group, "Own group")
        val sub = group.subscription!!
        assertEquals("https://other.example/sub", sub.link)
        assertEquals("", sub.fallbackLink)
        assertEquals("", sub.hwidHost)
        assertEquals("", sub.subscriptionUserinfo)
        assertEquals("", sub.announce)
    }

    @Test
    fun aBlankNameTakesTheDefaultAndTheDelayItsFloor() {
        val group = ProxyGroup()
        GroupForm(type = GroupType.SUBSCRIPTION, link = "https://p.example/s", autoUpdateDelay = 5).applyTo(group, "Own group")
        assertEquals("Own group", group.name)
        assertEquals(GroupForm.MIN_DELAY, group.subscription!!.autoUpdateDelay)
    }

    @Test
    fun aNodesGroupStaysBasic() {
        val group = ProxyGroup(type = GroupType.BASIC).apply { name = "arngory" }
        GroupForm.of(group).copy(type = GroupType.SUBSCRIPTION, order = GroupOrder.BY_DELAY).applyTo(group, "x", locked = true)
        assertEquals(GroupType.BASIC, group.type)
        assertEquals(GroupOrder.BY_DELAY, group.order)
        assertEquals(null, group.subscription)
    }

    @Test
    fun blankIsNothingTyped() {
        assertTrue(GroupForm().blank)
        assertTrue(GroupForm(type = GroupType.SUBSCRIPTION).blank)
        assertFalse(GroupForm(name = "Mine").blank)
        assertFalse(GroupForm(type = GroupType.SUBSCRIPTION, link = "https://p.example/s").blank)
        assertTrue(GroupForm(link = "https://p.example/s").blank) // an own group's link is not kept
    }
}
