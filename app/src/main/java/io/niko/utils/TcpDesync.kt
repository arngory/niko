package io.niko.utils

import io.niko.fmt.DpiDirect
import io.niko.ktx.Logs
import libcore.Libcore

/**
 * What this phone lets the core do for «Обход DPI для прямых соединений» (libcore's TcpDesyncCapabilities: IP_TTL
 * and MSG_OOB probed on a loopback connection, once a process); null where unknown, and nothing is hidden. Slow on
 * its first call: not on the main thread.
 */
object TcpDesync {
    val capabilities: DpiDirect.Capabilities? by lazy {
        try {
            DpiDirect.capabilities(Libcore.tcpDesyncCapabilities())
        } catch (e: Throwable) {
            Logs.w(e)
            null
        }
    }
}
