package io.niko.bg.proto

import io.niko.BuildConfig
import io.niko.bg.BaseService
import io.niko.bg.ServiceNotification
import io.niko.database.ProxyEntity
import io.niko.ktx.Logs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import io.niko.utils.JavaUtil

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var notTmp = true

    var lastSelectorGroupId = -1L
    var displayProfileName = ServiceNotification.genTitle(profile)

    // for TrafficLooper
    var looper: TrafficLooper? = null

    override fun buildConfig() {
        super.buildConfig()
        lastSelectorGroupId = super.config.selectorGroupId
        //
        if (notTmp) Logs.d(config.config)
        if (notTmp && BuildConfig.DEBUG) Logs.d(JavaUtil.gson.toJson(config.trafficMap))
    }

    // only use this in temporary instance
    fun buildConfigTmp() {
        notTmp = false
        buildConfig()
    }

    override suspend fun init() {
        super.init()
        pluginConfigs.forEach { (_, plugin) ->
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
        runBlocking {
            looper?.stop()
            looper = null
        }
    }
}
