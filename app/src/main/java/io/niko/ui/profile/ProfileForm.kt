package io.niko.ui.profile

import android.os.Parcelable
import androidx.annotation.StringRes
import io.niko.R
import io.niko.fmt.AbstractBean
import io.niko.fmt.DnsTunnel
import io.niko.fmt.parseCertificatePins
import io.niko.fmt.parseKnockSequence
import kotlinx.parcelize.Parcelize

/**
 * A profile as its editor (P-Profile) holds it while it is changed: the type's fields as typed ([text], [flags], by
 * the keys of its [ProfileSpec]), the connection tweaks, the custom JSON of the outbound and of the config, a chain's
 * links. Made by [ProfileSpec.load], written back by [ProfileSpec.save].
 */
@Parcelize
data class ProfileForm(
    val text: Map<String, String> = emptyMap(),
    val flags: Map<String, Boolean> = emptyMap(),
    val tweaks: Tweaks = Tweaks(),
    val outboundJson: String = "",
    val configJson: String = "",
    val chain: List<Long> = emptyList(),
) : Parcelable {
    fun text(key: String) = text[key].orEmpty()
    fun flag(key: String) = flags[key] == true
    fun withText(key: String, value: String) = copy(text = text + (key to value))
    fun withFlag(key: String, value: Boolean) = copy(flags = flags + (key to value))
}

/**
 * «Тонкая настройка подключения» (D-PROFILE-03) as typed, every type's but a chain's: TLS fragmentation (0 off, 1 TLS
 * records, 2 TCP segments), certificate pins, the DNS tunnel ([dnsEngine] as stored: "", dnstt, masterdnsvpn or
 * slipstream, the last from a link and not edited here), QUIC DPI evasion, port knocking. The numbers as text, as the
 * old dialog's fields held them.
 */
@Parcelize
data class Tweaks(
    val tlsFragment: Int = 0,
    val certPins: String = "",
    val dnsEngine: String = "",
    val dnsResolver: String = "",
    val dnsDomain: String = "",
    val dnsKey: String = "",
    val dnsMethod: String = "2",
    val dnsFixedUpstream: Boolean = false,
    val udpPrelude: String = "0",
    val udpPreludeSni: String = "",
    val udpPreludeTtl: String = "0",
    val udpReorderInitial: Boolean = false,
    val knockSequence: String = "",
    val knockInterval: String = "200",
    val knockTtl: String = "10",
    val knockSilence: String = "15",
) : Parcelable {

    /** A slipstream tunnel comes from its link (raycontrol slipstream-v1): shown, kept as it is. */
    val slipstream get() = dnsEngine == DnsTunnel.SLIPSTREAM

    /** The engine the tunnel's choice shows: MasterDnsVPN, else dnstt (none set too, as the old dialog's radio). */
    val shownEngine get() = if (dnsEngine == DnsTunnel.MASTERDNSVPN) DnsTunnel.MASTERDNSVPN else DnsTunnel.DNSTT

    companion object {
        /** The bean's tweaks, as the old loadConnectionTweaks put them into DataStore. */
        fun of(bean: AbstractBean) = Tweaks(
            tlsFragment = bean.tlsFragment ?: 0,
            certPins = bean.certPins.orEmpty(),
            dnsEngine = bean.dnsTunnel.orEmpty(),
            dnsResolver = bean.dnsTunnelResolver.orEmpty(),
            dnsDomain = bean.dnsTunnelDomain.orEmpty(),
            dnsKey = bean.dnsTunnelKey.orEmpty(),
            dnsMethod = (bean.dnsTunnelMethod ?: 2).toString(),
            dnsFixedUpstream = bean.dnsTunnelFixedUpstream == true,
            udpPrelude = (bean.udpPrelude ?: 0).toString(),
            udpPreludeSni = bean.udpPreludeSni.orEmpty(),
            udpPreludeTtl = (bean.udpPreludeTtl ?: 0).toString(),
            udpReorderInitial = bean.udpReorderInitial == true,
            knockSequence = bean.knockSequence.orEmpty(),
            knockInterval = (bean.knockInterval ?: 200).toString(),
            knockTtl = (bean.knockTtl ?: 10).toString(),
            knockSilence = (bean.knockSilence ?: 15).toString(),
        )
    }

    /** The tunnel as the old dialog's OK built it from its fields; none for slipstream's. */
    private fun tunnel(): DnsTunnel? = if (slipstream) null else DnsTunnel(
        engine = shownEngine,
        resolver = dnsResolver.trim(),
        domain = dnsDomain.trim(),
        key = dnsKey.trim(),
        method = dnsMethod.toIntOrNull() ?: -1,
        fixedUpstream = dnsFixedUpstream,
    )

    /** What the old dialog's OK refused, in its order, with its words; none: they may be kept. */
    fun check(): FieldError? {
        try {
            parseKnockSequence(knockSequence.trim())
        } catch (e: IllegalArgumentException) {
            return FieldError(KNOCK_SEQUENCE, R.string.port_knocking_invalid, e.message)
        }
        try {
            parseCertificatePins(certPins)
        } catch (e: IllegalArgumentException) {
            return FieldError(CERT_PINS, arg = e.message)
        }
        tunnel()?.validate()?.let { return FieldError(DNS_DOMAIN, arg = it) }
        val sni = udpPreludeSni.trim()
        if (sni.isNotEmpty() && !Regex("^[A-Za-z0-9.-]+$").matches(sni)) return FieldError(PRELUDE_SNI, R.string.udp_prelude_sni_invalid)
        return null
    }

    /**
     * The values stored for the tweaks as typed. Left as they were read ([original]), the bean's own, untouched (the
     * old dialog never opened); changed, as its OK took them: trimmed, the numbers brought into their ranges, the
     * tunnel off when its domain is empty.
     */
    internal fun storedAgainst(original: Tweaks): StoredTweaks {
        if (this == original) return StoredTweaks(
            tlsFragment, certPins, dnsEngine, dnsResolver, dnsDomain, dnsKey, dnsMethod.toIntOrNull() ?: 2, dnsFixedUpstream,
            udpPrelude.toIntOrNull() ?: 0, udpPreludeSni, udpPreludeTtl.toIntOrNull() ?: 0, udpReorderInitial,
            knockSequence, knockInterval.toIntOrNull() ?: 200, knockTtl.toIntOrNull() ?: 10, knockSilence.toIntOrNull() ?: 15,
        )
        val tunnel = tunnel()
        return StoredTweaks(
            tlsFragment = if (tlsFragment in 1..2) tlsFragment else 0,
            certPins = certPins.trim(),
            dnsTunnel = if (tunnel == null) dnsEngine else if (tunnel.enabled) tunnel.engine else "",
            dnsResolver = tunnel?.resolver ?: dnsResolver,
            dnsDomain = tunnel?.domain ?: dnsDomain,
            dnsKey = tunnel?.key ?: dnsKey,
            dnsMethod = tunnel?.method?.coerceIn(0, 5) ?: (dnsMethod.toIntOrNull() ?: 2),
            dnsFixedUpstream = tunnel?.fixedUpstream ?: dnsFixedUpstream,
            udpPrelude = udpPrelude.toIntOrNull()?.coerceIn(0, 8) ?: 0,
            udpPreludeSni = udpPreludeSni.trim(),
            udpPreludeTtl = udpPreludeTtl.toIntOrNull()?.coerceIn(0, 255) ?: 0,
            udpReorderInitial = udpReorderInitial,
            knockSequence = knockSequence.trim(),
            knockInterval = knockInterval.toIntOrNull()?.coerceIn(10, 10_000) ?: 200,
            knockTtl = knockTtl.toIntOrNull()?.coerceIn(1, 3600) ?: 10,
            knockSilence = knockSilence.toIntOrNull()?.coerceIn(0, 3600) ?: 15,
        )
    }
}

/** The tweaks as they are stored, the old DataStore keys' values. */
internal class StoredTweaks(
    val tlsFragment: Int,
    val certPins: String,
    val dnsTunnel: String,
    val dnsResolver: String,
    val dnsDomain: String,
    val dnsKey: String,
    val dnsMethod: Int,
    val dnsFixedUpstream: Boolean,
    val udpPrelude: Int,
    val udpPreludeSni: String,
    val udpPreludeTtl: Int,
    val udpReorderInitial: Boolean,
    val knockSequence: String,
    val knockInterval: Int,
    val knockTtl: Int,
    val knockSilence: Int,
) {
    /** Into [bean], as the old saveConnectionTweaks: another tunnel drops what the link's signed for the old one. */
    fun applyTo(bean: AbstractBean) {
        bean.knockSequence = knockSequence.trim()
        bean.knockInterval = knockInterval
        bean.knockTtl = knockTtl
        bean.knockSilence = knockSilence
        bean.tlsFragment = tlsFragment
        bean.certPins = certPins
        // dnsauth-v1 belongs to the node's tunnel the link gave: another server, domain or key is not that tunnel
        if (bean.dnsTunnel != dnsTunnel || bean.dnsTunnelResolver != dnsResolver ||
            bean.dnsTunnelDomain != dnsDomain || bean.dnsTunnelKey != dnsKey
        ) {
            bean.dnsTunnelAuth = ""
        }
        // dnsseal-v1's key is the zone's, and the spare zones (dnszones-v1) the service's: another engine, zone or
        // server key is not that service (a resolver is)
        if (bean.dnsTunnel != dnsTunnel || bean.dnsTunnelDomain != dnsDomain || bean.dnsTunnelKey != dnsKey) {
            bean.dnsTunnelSeal = ""
            bean.dnsTunnelSpare = ""
        }
        bean.dnsTunnel = dnsTunnel
        bean.dnsTunnelResolver = dnsResolver
        bean.dnsTunnelDomain = dnsDomain
        bean.dnsTunnelKey = dnsKey
        bean.dnsTunnelMethod = dnsMethod
        bean.dnsTunnelFixedUpstream = dnsFixedUpstream
        bean.udpPrelude = udpPrelude
        bean.udpPreludeSni = udpPreludeSni
        bean.udpPreludeTtl = udpPreludeTtl
        bean.udpReorderInitial = udpReorderInitial
    }
}

/** What stops a save: the field [key] (of the form, or of the tweaks), the message [res] with [arg], or [arg] alone. */
@Parcelize
data class FieldError(val key: String, @StringRes val res: Int = 0, val arg: String? = null) : Parcelable

// the tweaks' fields that an error names, and the pages they are on
const val KNOCK_SEQUENCE = "knockSequence"
const val CERT_PINS = "certPins"
const val DNS_DOMAIN = "dnsDomain"
const val PRELUDE_SNI = "udpPreludeSni"
