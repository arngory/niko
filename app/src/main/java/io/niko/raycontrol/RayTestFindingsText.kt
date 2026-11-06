package io.niko.raycontrol

import android.content.Context
import io.niko.R
import io.niko.raycontrol.RayTestFindings.Cause
import io.niko.raycontrol.RayTestFindings.Note

/** What a test run found, worded for the user: the verdict, then what holds for the network, then each cause. */
object RayTestFindingsText {

    fun text(context: Context, result: RayTestFindings.Result): String = lines(context, result).joinToString("\n\n")

    fun lines(context: Context, result: RayTestFindings.Result): List<String> {
        if (result.has(Note.NO_NETWORK)) return listOf(context.getString(R.string.raycontrol_findings_no_network))
        val out = mutableListOf<String>()
        val working = result.working
        val measured = working.size + result.failing.size
        out += when {
            working.isNotEmpty() -> working.first().let { best ->
                context.getString(
                    R.string.raycontrol_findings_works, working.size, measured, best.name,
                    "%.1f".format(best.kbps / 1000.0), best.firstByteMs.toInt(),
                )
            }

            measured > 0 -> context.getString(R.string.raycontrol_findings_none, measured)
            else -> context.getString(R.string.raycontrol_findings_unmeasured)
        }
        for (finding in result.notes) {
            out += "• " + when (finding.note) {
                Note.NO_NETWORK -> continue
                Note.CLOCK -> context.getString(R.string.raycontrol_clock_wrong, finding.number)
                Note.NO_SUBSCRIPTION -> context.getString(R.string.raycontrol_findings_no_sub, finding.detail ?: "?")
                // the relay's note says it, and more
                Note.NO_DIRECT -> if (result.has(Note.RELAY_ONLY)) continue else {
                    context.getString(R.string.raycontrol_findings_no_direct) + (finding.detail?.let { " ($it)" } ?: "")
                }

                Note.RELAY_ONLY -> context.getString(R.string.raycontrol_findings_relay_only)
                Note.DNS_ONLY -> context.getString(R.string.raycontrol_findings_dns_only)
                Note.UDP_BLOCKED -> context.getString(R.string.raycontrol_findings_udp_blocked)
                Note.UDP_SMALL -> context.getString(R.string.raycontrol_findings_udp_small, finding.number.toInt())
                Note.DNS_NONE -> context.getString(R.string.raycontrol_findings_dns_none)
                Note.DNS_SPARE -> context.getString(R.string.raycontrol_findings_dns_spare, finding.number.toInt())
            }
        }
        for ((cause, profiles) in result.byCause) {
            val names = profiles.joinToString(", ") { it.name }
            out += "• " + when (cause) {
                Cause.STALLS -> context.getString(
                    R.string.raycontrol_findings_stalls, names, profiles.minOf { it.stalledAtKb }.toInt()
                )

                Cause.TIMEOUT -> context.getString(R.string.raycontrol_findings_timeout, names)
                Cause.RESET -> context.getString(R.string.raycontrol_findings_reset, names)
                Cause.REFUSED -> context.getString(R.string.raycontrol_findings_refused, names)
                Cause.TLS -> context.getString(R.string.raycontrol_findings_tls, names)
                Cause.NAME -> context.getString(R.string.raycontrol_findings_name, names)
                Cause.UNREACHABLE -> context.getString(R.string.raycontrol_findings_unreachable, names)
                Cause.BUSY -> context.getString(R.string.raycontrol_findings_busy, names)
                Cause.HTTP -> context.getString(R.string.raycontrol_findings_http, names, profiles.first().error ?: "?")
                Cause.IN_USE -> continue
                Cause.OTHER -> profiles.joinToString("\n• ") {
                    context.getString(R.string.raycontrol_findings_other, it.name, it.error ?: "?")
                }
            }
        }
        result.dnsFailing.takeIf { it.isNotEmpty() }?.let { profiles ->
            out += "• " + context.getString(R.string.raycontrol_findings_dns_failed, profiles.joinToString(", ") { it.name })
        }
        result.noUdp.takeIf { it.isNotEmpty() }?.let { profiles ->
            out += "• " + context.getString(R.string.raycontrol_findings_no_udp, profiles.joinToString(", ") { it.name })
        }
        if (working.isEmpty() && measured > 0) out += context.getString(R.string.raycontrol_findings_share)
        return out
    }
}
