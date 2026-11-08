package io.niko.bg.proto

import io.niko.BuildConfig
import io.niko.bg.BaseService
import io.niko.bg.ServiceNotification
import io.niko.database.ProxyEntity
import io.niko.ktx.Logs
import io.niko.raycontrol.RayNodes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.niko.utils.JavaUtil

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var notTmp = true

    var lastSelectorGroupId = -1L
    var displayProfileName = ServiceNotification.genTitle(profile)

    // for TrafficLooper
    var looper: TrafficLooper? = null

    override fun buildConfig() {
        // the node list may have changed in the main process since this one started
        if (notTmp) RayNodes.syncLogRedactions()
        super.buildConfig()
        lastSelectorGroupId = super.config.selectorGroupId
        //
        // the whole config (secrets, keys) only in debug builds; the log redacts, but this is the one place that
        // would carry everything at once
        if (notTmp && BuildConfig.DEBUG) Logs.d(config.config)
        if (notTmp && BuildConfig.DEBUG) Logs.d(JavaUtil.gson.toJson(config.trafficMap))
    }

    override val runs: Boolean get() = notTmp

    // only use this in temporary instance
    fun buildConfigTmp() {
        notTmp = false
        buildConfig()
    }

    override suspend fun init() {
        super.init()
        if (BuildConfig.DEBUG) pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override suspend fun loadConfig() {
        super.loadConfig()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun launch() {
        box.setAsMain()
        // the mesh nodes this core runs: a test with one of their keys would be a second node on that address
        io.niko.fmt.setRunningYggKeys(config.yggKeys)
        super.launch() // start box
        // created right here, not in a coroutine: a close() after launch() must find it to stop it
        looper = service?.let { TrafficLooper(it.data, GlobalScope) }
        looper?.start()
    }

    override fun close() {
        super.close()
        io.niko.fmt.setRunningYggKeys(emptySet())
        // the loop stops now; its last post waits for the binder's broadcast lock, which a coroutine of the main
        // thread may hold or be handed next: never waited for on this (main) thread, or neither could go on
        val stopped = looper ?: return
        looper = null
        stopped.cancel()
        val trafficMap = if (isInitialized()) config.trafficMap else null
        GlobalScope.launch(Dispatchers.IO) { stopped.finalPost(trafficMap) }
    }
}
