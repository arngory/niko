package io.niko.ui.group

import android.os.Parcelable
import io.niko.GroupOrder
import io.niko.GroupType
import io.niko.database.ProxyGroup
import io.niko.database.SubscriptionBean
import io.niko.ktx.applyDefaultValues
import kotlinx.parcelize.Parcelize

/**
 * What the group's screen edits (P-SubSettings: D-SUB-08, D-GROUP-01): the group's own fields and, for a provider's
 * subscription, the subscription's. Read from a group with [of], written back with [applyTo]; the subscription's
 * fields stay in the form when the type is switched to an own group and back, as the old screen kept them.
 */
@Parcelize
data class GroupForm(
    val name: String = "",
    val type: Int = GroupType.BASIC,
    val order: Int = GroupOrder.ORIGIN,
    val selector: Boolean = false,
    val autoSelect: Boolean = false,
    /** a profile's id, -1: none */
    val frontProxy: Long = -1,
    val landingProxy: Long = -1,
    val link: String = "",
    val autoUpdate: Boolean = false,
    /** minutes, [MIN_DELAY] at least */
    val autoUpdateDelay: Int = 1440,
    val whenConnectedOnly: Boolean = false,
    val userAgent: String = "",
    val sendHwid: Boolean = false,
    val forceResolve: Boolean = false,
    val deduplication: Boolean = false,
) : Parcelable {
    val subscription get() = type == GroupType.SUBSCRIPTION

    /** Something typed to keep: a new group with neither a name nor a link is not made. */
    val blank get() = name.isBlank() && (!subscription || link.isBlank())

    companion object {
        const val MIN_DELAY = 15

        /** The intervals offered, in minutes; another value already set is shown as it is. */
        val DELAYS = listOf(15, 30, 60, 180, 360, 720, 1440)

        fun of(group: ProxyGroup): GroupForm {
            val sub = group.subscription ?: SubscriptionBean().applyDefaultValues()
            return GroupForm(
                name = group.name.orEmpty(),
                type = group.type,
                order = group.order,
                selector = group.isSelector,
                autoSelect = group.autoSelect,
                frontProxy = group.frontProxy,
                landingProxy = group.landingProxy,
                link = sub.link.orEmpty(),
                autoUpdate = sub.autoUpdate == true,
                autoUpdateDelay = sub.autoUpdateDelay ?: 1440,
                whenConnectedOnly = sub.updateWhenConnectedOnly == true,
                userAgent = sub.customUserAgent.orEmpty(),
                sendHwid = sub.sendHwid == true,
                forceResolve = sub.forceResolve == true,
                deduplication = sub.deduplication == true,
            )
        }
    }

    /**
     * The form into [group], as the old screen's serialize: a blank name becomes [defaultName]; a subscription's link
     * changed drops the provider's spare address and the device id's host (they were the old address's), and with them
     * its traffic and message (Subscription-Userinfo, announce); a raycontrol node's group ([locked]) stays basic.
     */
    fun applyTo(group: ProxyGroup, defaultName: String, locked: Boolean = false) {
        val type = if (locked) GroupType.BASIC else type
        val wasSubscription = group.type == GroupType.SUBSCRIPTION
        group.name = name.trim().ifEmpty { defaultName }
        group.type = type
        group.order = order
        group.isSelector = selector
        group.autoSelect = autoSelect
        group.frontProxy = frontProxy
        group.landingProxy = landingProxy
        if (type != GroupType.SUBSCRIPTION) return
        val sub = group.subscription ?: SubscriptionBean().applyDefaultValues()
        val newLink = link.trim()
        if (!wasSubscription || sub.link != newLink) {
            sub.fallbackLink = ""
            sub.hwidHost = ""
            sub.subscriptionUserinfo = ""
            sub.announce = ""
        }
        sub.link = newLink
        sub.forceResolve = forceResolve
        sub.deduplication = deduplication
        sub.updateWhenConnectedOnly = whenConnectedOnly
        sub.customUserAgent = userAgent.trim()
        sub.sendHwid = sendHwid
        sub.autoUpdate = autoUpdate
        sub.autoUpdateDelay = autoUpdateDelay.coerceAtLeast(MIN_DELAY)
        group.subscription = sub
    }
}
