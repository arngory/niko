package io.niko.bg.proto

import android.os.RemoteException
import io.niko.aidl.ISagerNetService
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.plugin.PluginManager
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class UrlTest {

    val link = DataStore.connectionTestURL
    private val timeout = 5000

    /** The test here; its time or its failure goes into "Statistics for the last hour" (by the profile's kind). */
    suspend fun doTest(profile: ProxyEntity): Int {
        val kind = mapOf("profile" to io.niko.utils.Stats.kind(profile))
        val ms = try {
            TestInstance(profile, link, timeout).doTest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            io.niko.utils.Stats.event("url_test", kind + ("outcome" to "fail"))
            throw e
        }
        io.niko.utils.Stats.event("url_test", kind + ("outcome" to "ok"), ms.toLong())
        return ms
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
        // the user closed the test (cancelTests): no result, as a test cancelled here has none
        if (result.optBoolean("cancelled")) throw CancellationException("test cancelled")
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
