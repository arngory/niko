package io.niko.bg.proto

import io.niko.aidl.SpeedDisplayData
import io.niko.aidl.TrafficData
import io.niko.bg.BaseService
import io.niko.bg.SagerConnection
import io.niko.database.DataStore
import io.niko.database.ProfileManager
import io.niko.fmt.TAG_BYPASS
import io.niko.fmt.TAG_PROXY
import io.niko.ktx.Logs
import io.niko.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.*

class TrafficLooper
    (
    val data: BaseService.Data, private val sc: CoroutineScope
) {

    private var job: Job? = null
    private val idMap = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // id to 1 data
    private val tagMap = mutableMapOf<String, TrafficUpdater.TrafficLooperData>() // tag to 1 data

    /**
     * Stops the loop (waited for: it may still be filling the counts) and writes the last counts of the core whose
     * profiles are [trafficMap] (its own, not whatever core runs by then) to the database, so a restart reads them;
     * returns them for [postFinal]. Takes no binder lock.
     */
    suspend fun stopAndSave(trafficMap: Map<String, List<io.niko.database.ProxyEntity>>?): Map<Long, TrafficData> {
        job?.cancelAndJoin()
        if (!DataStore.profileTrafficStatistics) return emptyMap()
        val traffic = mutableMapOf<Long, TrafficData>()
        trafficMap?.forEach { (_, ents) ->
            for (ent in ents) {
                val item = idMap[ent.id] ?: return@forEach
                ent.rx = item.rx
                ent.tx = item.tx
                ProfileManager.updateTraffic(ent) // update DB
                traffic[ent.id] = TrafficData(
                    id = ent.id,
                    rx = ent.rx,
                    tx = ent.tx,
                )
            }
        }
        return traffic
    }

    /** The last counts to the UI: the binder's broadcast lock, never waited for on the main thread. */
    suspend fun postFinal(traffic: Map<Long, TrafficData>) {
        if (traffic.isEmpty()) return
        data.binder.broadcast { b ->
            for (t in traffic) {
                b.cbTrafficUpdate(t.value)
            }
        }
        Logs.d("finally traffic post done")
    }

    fun start() {
        job = sc.launch { loop() }
    }

    var selectorNowId = -114514L
    var selectorNowFakeTag = ""

    fun selectMain(id: Long) {
        Logs.d("select traffic count $TAG_PROXY to $id, old id is $selectorNowId")
        val oldData = idMap[selectorNowId]
        val newData = idMap[id] ?: return
        oldData?.apply {
            tag = selectorNowFakeTag
            ignore = true
            // post traffic when switch
            if (DataStore.profileTrafficStatistics) {
                data.proxy?.config?.trafficMap?.get(tag)?.firstOrNull()?.let {
                    it.rx = rx
                    it.tx = tx
                    runOnDefaultDispatcher {
                        ProfileManager.updateTraffic(it) // update DB
                    }
                }
            }
        }
        selectorNowFakeTag = newData.tag
        selectorNowId = id
        newData.apply {
            tag = TAG_PROXY
            ignore = false
        }
    }

    private suspend fun loop() {
        val delayMs = DataStore.speedInterval.toLong()
        val showDirectSpeed = DataStore.showDirectSpeed
        val profileTrafficStatistics = DataStore.profileTrafficStatistics
        if (delayMs == 0L) return

        var trafficUpdater: TrafficUpdater? = null
        var proxy: ProxyInstance?

        // for display
        val itemBypass = TrafficUpdater.TrafficLooperData(tag = TAG_BYPASS)

        while (sc.isActive) {
            proxy = data.proxy
            if (proxy == null) {
                delay(delayMs)
                continue
            }

            if (trafficUpdater == null) {
                if (!proxy.isInitialized()) {
                    delay(delayMs) // the config is still being built
                    continue
                }
                idMap.clear()
                idMap[-1] = itemBypass
                //
                val tags = hashSetOf(TAG_PROXY, TAG_BYPASS)
                proxy.config.trafficMap.forEach { (tag, ents) ->
                    tags.add(tag)
                    for (ent in ents) {
                        val item = TrafficUpdater.TrafficLooperData(
                            tag = tag,
                            rx = ent.rx,
                            tx = ent.tx,
                            rxBase = ent.rx,
                            txBase = ent.tx,
                            ignore = proxy.config.selectorGroupId >= 0L,
                        )
                        idMap[ent.id] = item
                        tagMap[tag] = item
                        Logs.d("traffic count $tag to ${ent.id}")
                    }
                }
                if (proxy.config.selectorGroupId >= 0L) {
                    selectMain(proxy.config.mainEntId)
                }
                // a group in Auto mode routes through a urltest tagged "proxy": count its traffic
                // for the speed display, which profile carried it is not known
                val extraItems = mutableListOf<TrafficUpdater.TrafficLooperData>()
                if (proxy.config.selectorGroupId < 0L && TAG_PROXY !in proxy.config.trafficMap) {
                    val autoItem = TrafficUpdater.TrafficLooperData(tag = TAG_PROXY)
                    tagMap[TAG_PROXY] = autoItem
                    extraItems += autoItem
                }
                //
                trafficUpdater = TrafficUpdater(
                    box = proxy.box, items = idMap.values.toList() + extraItems
                )
                proxy.box.setV2rayStats(tags.joinToString("\n"))
            }

            trafficUpdater.updateAll()
            if (!sc.isActive) return

            // add all non-bypass to "main"
            var mainTxRate = 0L
            var mainRxRate = 0L
            var mainTx = 0L
            var mainRx = 0L
            tagMap.forEach { (_, it) ->
                if (!it.ignore) {
                    mainTxRate += it.txRate
                    mainRxRate += it.rxRate
                }
                mainTx += it.tx - it.txBase
                mainRx += it.rx - it.rxBase
            }

            // speed
            val speed = SpeedDisplayData(
                mainTxRate,
                mainRxRate,
                if (showDirectSpeed) itemBypass.txRate else 0L,
                if (showDirectSpeed) itemBypass.rxRate else 0L,
                mainTx,
                mainRx
            )

            // broadcast (MainActivity)
            if (data.state == BaseService.State.Connected
                && data.binder.callbackIdMap.containsValue(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
            ) {
                data.binder.broadcast { b ->
                    if (data.binder.callbackIdMap[b] == SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND) {
                        b.cbSpeedUpdate(speed)
                        if (profileTrafficStatistics) {
                            idMap.forEach { (id, item) ->
                                b.cbTrafficUpdate(
                                    TrafficData(id = id, rx = item.rx, tx = item.tx) // display
                                )
                            }
                        }
                    }
                }
            }

            // ServiceNotification
            data.notification?.apply {
                if (listenPostSpeed) postNotificationSpeedUpdate(speed)
            }

            delay(delayMs)
        }
    }
}