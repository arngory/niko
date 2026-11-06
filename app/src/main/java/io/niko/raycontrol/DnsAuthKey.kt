package io.niko.raycontrol

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * raycontrol dnsauth-v1 §2's key of a device for a service: K = HKDF-SHA256(ikm = the device's SPA key, salt = the
 * node's id (16 bytes), info = "raycontrol-dnsauth-v1" || uint16_be(service id), 32 bytes). The core derives it for its
 * own tunnels (transport/dnstt AuthKey); the slipstream client, a process of its own, takes it from here.
 */
object DnsAuthKey {
    fun derive(deviceKey: ByteArray, nodeIdHex: String, serviceId: Int): ByteArray {
        require(deviceKey.size == 32) { "dnsauth: a device key of ${deviceKey.size} bytes, want 32" }
        require(nodeIdHex.length == 32 && nodeIdHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "dnsauth: the node id is 32 hex digits"
        }
        require(serviceId in 0..0xffff) { "dnsauth: service id $serviceId" }
        val salt = ByteArray(16) { nodeIdHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        // extract, then one block of expand: L = 32 is the hash's length
        val prk = hmac(salt, deviceKey)
        val info = "raycontrol-dnsauth-v1".toByteArray() + byteArrayOf((serviceId shr 8).toByte(), serviceId.toByte(), 1)
        return hmac(prk, info).also { prk.fill(0) }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
}
