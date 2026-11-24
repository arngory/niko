package io.niko.ui.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Text
import io.niko.ui.route.DangerBanner
import io.niko.ui.route.PrimaryButton
import io.niko.ui.settings.Field
import io.niko.ui.settings.ListPage

/** How a NAT treats the far address (RFC 5780), as the core names it; [OTHER] a name this version does not know. */
enum class NatBehavior { ENDPOINT, ADDRESS, ADDRESS_AND_PORT, OTHER }

/**
 * What a STUN check found, read out of the core's text: the NAT's [type] (RFC 3489's name), its [mapping] and
 * [filtering] with the core's own words for them, the [address] the server saw, and [notes], the lines that are none
 * of these (a test's error, the fake full cone).
 */
data class NatResult(
    val type: String? = null,
    val mapping: NatBehavior? = null,
    val mappingText: String? = null,
    val filtering: NatBehavior? = null,
    val filteringText: String? = null,
    val address: String? = null,
    val notes: List<String> = emptyList(),
) {
    companion object {
        private fun behavior(name: String) = when (name) {
            "EndpointIndependent" -> NatBehavior.ENDPOINT
            "AddressDependent" -> NatBehavior.ADDRESS
            "AddressAndPortDependent" -> NatBehavior.ADDRESS_AND_PORT
            else -> NatBehavior.OTHER
        }

        /** The core's lines («NAT Type: …», «Mapping Behavior: …») as a result; a line it does not know is a note. */
        fun parse(text: String): NatResult {
            val values = LinkedHashMap<String, String>()
            val notes = ArrayList<String>()
            for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
                val key = line.substringBefore(": ", "")
                when (key) {
                    "NAT Type", "Normal NAT Type", "External IP", "External Port", "Mapping Behavior", "Filtering Behavior" ->
                        values[key] = line.substringAfter(": ").trim()
                    // the family repeats what the address shows
                    "External IP Family" -> {}
                    else -> notes += line
                }
            }
            val ip = values["External IP"]
            val port = values["External Port"]
            return NatResult(
                // the behaviour test's name where it has one, the older test's otherwise
                type = values["Normal NAT Type"]?.takeIf { it != "Undefined" } ?: values["NAT Type"],
                mapping = values["Mapping Behavior"]?.let(::behavior),
                mappingText = values["Mapping Behavior"],
                filtering = values["Filtering Behavior"]?.let(::behavior),
                filteringText = values["Filtering Behavior"],
                address = when {
                    ip == null -> null
                    port == null -> ip
                    ':' in ip -> "[$ip]:$port"
                    else -> "$ip:$port"
                },
                notes = notes,
            )
        }
    }

    /** Nothing a row shows: the check ran and learned nothing. */
    val empty get() = type == null && mapping == null && filtering == null && address == null
}

/** Тип NAT (D-TOOLS-01): the [server] asked, the check [running], what it found or why it did not. */
data class NatState(
    val server: String = DEFAULT_SERVER,
    val running: Boolean = false,
    val result: NatResult? = null,
    val error: String? = null,
) {
    companion object {
        const val DEFAULT_SERVER = "stun.voipgate.com:3478"
    }
}

@Composable
private fun behaviorName(behavior: NatBehavior, raw: String?) = when (behavior) {
    NatBehavior.ENDPOINT -> stringResource(R.string.nat_endpoint)
    NatBehavior.ADDRESS -> stringResource(R.string.nat_address)
    NatBehavior.ADDRESS_AND_PORT -> stringResource(R.string.nat_address_port)
    NatBehavior.OTHER -> raw.orEmpty()
}

/** A row's value at its end, selectable text being for the notes only. */
@Composable
private fun Value(text: String, mono: Boolean = false) =
    Text(text, if (mono) NikoType.code else NikoType.bodySm, Niko.colors.ink2, maxLines = 1)

/** The page: the server's field, the check, then its result by rows, the error in place of it. */
@Composable
fun NatScreen(state: NatState, onBack: () -> Unit, onServer: (String) -> Unit, onCheck: () -> Unit) {
    val c = Niko.colors
    ListPage(stringResource(R.string.stun_test), stringResource(R.string.screen_back), onBack) {
        Field(
            stringResource(R.string.nat_server), state.server, onServer, mono = true,
            onGo = if (state.running) null else onCheck,
        )
        PrimaryButton(
            stringResource(if (state.running) R.string.nat_running else R.string.nat_check), onCheck,
            Modifier.fillMaxWidth(), enabled = !state.running && state.server.isNotBlank(),
        )
        state.error?.let { DangerBanner(it) }
        state.result?.let { r ->
            if (!r.empty) Section(stringResource(R.string.nat_result)) {
                var n = 0
                r.type?.let { ListRow(stringResource(R.string.nat_type), divider = n++ > 0, trail = { Value(it) }) }
                r.mapping?.let { m ->
                    val text = behaviorName(m, r.mappingText)
                    ListRow(stringResource(R.string.nat_mapping), divider = n++ > 0, trail = { Value(text) })
                }
                r.filtering?.let { f ->
                    val text = behaviorName(f, r.filteringText)
                    ListRow(stringResource(R.string.nat_filtering), divider = n++ > 0, trail = { Value(text) })
                }
                r.address?.let { a ->
                    ListRow(stringResource(R.string.nat_external), divider = n++ > 0, trail = { Value(a, mono = true) })
                }
            }
            if (r.notes.isNotEmpty()) SelectionContainer {
                Text(r.notes.joinToString("\n"), NikoType.code, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
            }
        }
        Text(stringResource(R.string.nat_note), NikoType.bodySm, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
    }
}
