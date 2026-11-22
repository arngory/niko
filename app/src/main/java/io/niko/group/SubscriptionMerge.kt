package io.niko.group

import io.niko.Protocols
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.SubscriptionBean
import io.niko.fmt.AbstractBean
import io.niko.fmt.keepDnsTunnelOf
import io.niko.ktx.Logs
import io.niko.proxy.config.ConfigBean
import io.niko.utils.Util

/**
 * What an update does to a subscription's group, without the database: RawUpdater.doUpdate calls it with the
 * group's profiles and writes what it gives, the golden test (io.niko.golden.SubscriptionsGoldenTest) runs it on the
 * fixtures of shared/golden/subscriptions, which libcore/nikosub, the desktop's subscriptions, must match.
 */
object SubscriptionMerge {

    /**
     * What an answer's headers change of a group and its subscription: the traffic and expiry, the announcement, the
     * provider's name for a freshly imported subscription (the default name), the provider's interval on the first
     * update. [header] is the answer's header of a name, "" for none.
     */
    fun applyAnswer(proxyGroup: ProxyGroup, subscription: SubscriptionBean, header: (String) -> String) {
        subscription.subscriptionUserinfo = header("Subscription-Userinfo")

        subscription.announce = decodeSubscriptionHeader(header("announce"))

        // a freshly imported subscription (default name) takes the provider's name and interval
        if (proxyGroup.name?.startsWith("Subscription #") == true) {
            var remoteName = decodeSubscriptionHeader(header("profile-title"))
            if (remoteName.isBlank()) {
                remoteName = header("content-disposition")
                if (remoteName.isNotBlank()) remoteName = Util.decodeFilename(remoteName)
            }
            if (remoteName.isNotBlank()) {
                proxyGroup.name = remoteName
            }
        }
        // the provider's interval only on the first update: later the user's choice wins
        if (subscription.lastUpdated == 0) subscriptionUpdateIntervalMinutes(header("profile-update-interval"))?.let {
            subscription.autoUpdate = true
            subscription.autoUpdateDelay = it
        }
    }

    /** The beans an update takes of a body's: no full sing-box config, the provider's custom configs dropped, names made unique. */
    fun prepare(parsed: List<AbstractBean>): List<AbstractBean> {
        // the provider must not rewrite the whole local config (inbounds, DNS, Clash API) of the app
        val proxies = parsed.filter { bean ->
            if (bean is ConfigBean && bean.type == 0) {
                Logs.w("skip ${bean.displayName()}: a subscription can not ship a full sing-box config")
                false
            } else {
                bean.customConfigJson = ""
                true
            }
        }

        val proxiesMap = LinkedHashMap<String, AbstractBean>()
        for (proxy in proxies) {
            var index = 0
            var name = proxy.displayName()
            while (proxiesMap.containsKey(name)) {
                println("Exists name: $name")
                index++
                name = name.replace(" (${index - 1})", "")
                name = "$name ($index)"
                proxy.name = name
            }
            proxiesMap[proxy.displayName()] = proxy
        }
        return proxiesMap.values.toList()
    }

    /**
     * The group's profiles after an update: [toUpdate] in the order the update met them, [reordered] those of them
     * that only moved, [toDelete]; the new ones went to the add given to [plan], in their order. The rest is what the
     * user is told.
     */
    class Plan(
        val toUpdate: List<ProxyEntity>,
        val reordered: List<ProxyEntity>,
        val toDelete: List<ProxyEntity>,
        val changed: Int,
        val added: List<String>,
        val updated: Map<String, String>,
        val deleted: List<String>,
        val duplicate: List<String>,
    )

    /**
     * Merges [proxies] (prepared, their names resolved if the subscription says so) into the group's profiles
     * [exists] (by userOrder): one profile of each server where [deduplication] says so, then each matched to the
     * group's by its name. [add] takes each new profile as it comes.
     */
    fun plan(
        groupId: Long,
        exists: List<ProxyEntity>,
        proxies: List<AbstractBean>,
        deduplication: Boolean,
        add: (ProxyEntity) -> Unit,
    ): Plan {
        @Suppress("NAME_SHADOWING") var proxies = proxies
        val duplicate = ArrayList<String>()
        if (deduplication) {
            Logs.d("Before deduplication: ${proxies.size}")
            val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
            val uniqueNames = HashMap<Protocols.Deduplication, String>()
            for (_proxy in proxies) {
                val proxy = Protocols.Deduplication(_proxy, _proxy.javaClass.toString())
                if (!uniqueProxies.add(proxy)) {
                    val index = uniqueProxies.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotBlank()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(_proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = _proxy.displayName()
                }
            }
            uniqueProxies.retainAll(uniqueNames.keys)
            proxies = uniqueProxies.toList().map { it.bean }
        }

        Logs.d("New profiles: ${proxies.size}")

        val nameMap = proxies.associateBy { bean ->
            bean.displayName()
        }

        Logs.d("Unique profiles: ${nameMap.size}")

        val toDelete = ArrayList<ProxyEntity>()
        val toReplace = exists.mapNotNull { entity ->
            val name = entity.displayName()
            if (nameMap.contains(name)) name to entity else let {
                toDelete.add(entity)
                null
            }
        }.toMap()

        Logs.d("toDelete profiles: ${toDelete.size}")
        Logs.d("toReplace profiles: ${toReplace.size}")

        val toUpdate = ArrayList<ProxyEntity>()
        val reordered = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size
        for ((name, bean) in nameMap.entries) {
            if (toReplace.contains(name)) {
                val entity = toReplace[name]!!
                val existsBean = entity.requireBean()
                // 更新订阅，保留自定义覆写设置
                bean.customOutboundJson = existsBean.customOutboundJson
                bean.customConfigJson = existsBean.customConfigJson
                bean.knockSequence = existsBean.knockSequence
                bean.knockInterval = existsBean.knockInterval
                bean.knockTtl = existsBean.knockTtl
                bean.knockSilence = existsBean.knockSilence
                bean.tlsFragment = existsBean.tlsFragment
                bean.certPins = existsBean.certPins
                bean.keepDnsTunnelOf(existsBean)
                bean.udpPrelude = existsBean.udpPrelude
                bean.udpPreludeSni = existsBean.udpPreludeSni
                bean.udpPreludeTtl = existsBean.udpPreludeTtl
                bean.udpReorderInitial = existsBean.udpReorderInitial
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name

                        Logs.d("Updated profile: $name")
                    }

                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        reordered.add(entity)
                        entity.userOrder = userOrder

                        Logs.d("Reordered profile: $name")
                    }

                    else -> {
                        Logs.d("Ignored profile: $name")
                    }
                }
            } else {
                changed++
                add(
                    ProxyEntity(
                        groupId = groupId, userOrder = userOrder
                    ).apply {
                        putBean(bean)
                    })
                added.add(name)
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }
        return Plan(toUpdate, reordered, toDelete, changed, added, updated, deleted, duplicate)
    }
}
