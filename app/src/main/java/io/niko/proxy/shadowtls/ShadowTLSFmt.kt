package io.niko.proxy.shadowtls

import io.niko.fmt.v2ray.buildSingBoxOutboundTLS
import io.niko.SingBoxOptions

fun buildSingBoxOutboundShadowTLSBean(
    bean: ShadowTLSBean, globalInsecure: Boolean = false,
): SingBoxOptions.Outbound_ShadowTLSOptions {
    return SingBoxOptions.Outbound_ShadowTLSOptions().apply {
        type = "shadowtls"
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean, globalInsecure)
    }
}
