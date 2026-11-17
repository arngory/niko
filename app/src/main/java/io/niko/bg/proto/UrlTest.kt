package io.niko.bg.proto

import android.os.RemoteException
import io.niko.aidl.ISagerNetService
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.plugin.PluginManager
import org.json.JSONObject

class UrlTest {

    val link = DataStore.connectionTestURL
    private val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): Int {
        return TestInstance(profile, link, timeout).doTest()
    }

    /**
     * The same test in the service's process, which [service] is: its core's code is in memory there already. In
     * the window's process a group's test left ~25 MB behind, more than a third of it the cores' code
     * (docs/research/memory.md). A service gone meanwhile: the test here, as without one.
     */
    suspend fun doTest(profile: ProxyEntity, service: ISagerNetService?): Int {
        val answer = try {
            service?.testProfile(profile.id, link, timeout)
        } catch (e: RemoteException) {
            null
        } ?: return doTest(profile)
        val result = JSONObject(answer)
        if (result.has("ping")) return result.getInt("ping")
        if (result.has("plugin")) throw PluginManager.PluginNotFoundException(result.getString("plugin"))
        throw TestFailure(result.optString("error"))
    }

    /** The service's test failed with these words; shown as the test's own error is. */
    class TestFailure(message: String) : Exception(message)

    companion object {
        /** Binder threads the service holds at once for a group's test: a few, its other calls need some too. */
        const val inServiceAtOnce = 6
    }

}
