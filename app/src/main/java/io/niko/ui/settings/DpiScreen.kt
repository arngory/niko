package io.niko.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.niko.Key
import io.niko.R
import io.niko.fmt.DpiDirect
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoOpacity
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Radio
import io.niko.ui.home.Sub
import io.niko.ui.home.Text

/** What the DPI page shows beside the settings: the platform's [capabilities] (null: unknown), the flagged [rules]. */
class DpiInfo(val capabilities: DpiDirect.Capabilities? = null, val rules: Int = 0)

/** A preset's name. */
@Composable
internal fun dpiPresetName(id: String) = when (id) {
    "tlsrec_sni" -> stringResource(R.string.dpi_preset_tlsrec_sni)
    "split_sni" -> stringResource(R.string.dpi_preset_split_sni)
    "disoob_sld" -> stringResource(R.string.dpi_preset_disoob_sld)
    "disorder_1" -> stringResource(R.string.dpi_preset_disorder_1)
    else -> id
}

/** The setting as the Experiments page's row shows it. */
@Composable
internal fun dpiSummary(values: SettingValues) = when (values.text(Key.DPI_DIRECT).toIntOrNull()) {
    DpiDirect.PRESET -> dpiPresetName(values.text(Key.DPI_DIRECT_PRESET))
    DpiDirect.CUSTOM -> stringResource(R.string.dpi_mode_custom)
    else -> stringResource(R.string.set_off)
}

/** Why a ByeDPI string is not taken, for the user. */
@Composable
internal fun dpiError(error: DpiDirect.Parsed.Error) = when (error.kind) {
    DpiDirect.ErrorKind.EMPTY -> stringResource(R.string.dpi_error_empty)
    DpiDirect.ErrorKind.NFQWS -> stringResource(R.string.dpi_error_nfqws)
    DpiDirect.ErrorKind.FAKE -> stringResource(R.string.dpi_error_fake, error.flag)
    DpiDirect.ErrorKind.AUTO -> stringResource(R.string.dpi_error_auto, error.flag)
    DpiDirect.ErrorKind.UNSUPPORTED -> stringResource(R.string.dpi_error_unsupported, error.flag)
    DpiDirect.ErrorKind.NO_VALUE -> stringResource(R.string.dpi_error_no_value, error.flag)
    DpiDirect.ErrorKind.BAD_POSITION -> stringResource(R.string.dpi_error_bad_position, error.flag, error.value)
    DpiDirect.ErrorKind.BAD_OOB_DATA -> stringResource(R.string.dpi_error_bad_oob_data, error.flag, error.value)
    DpiDirect.ErrorKind.UNEXPECTED -> stringResource(R.string.dpi_error_unexpected, error.value)
    DpiDirect.ErrorKind.NOTHING -> stringResource(R.string.dpi_error_nothing)
    DpiDirect.ErrorKind.TOO_MANY -> stringResource(R.string.dpi_error_too_many, DpiDirect.MAX_CUTS)
}

/**
 * Настройки › Опыты › Обход DPI для прямых соединений (docs/design/dpi.md §5.2): off, a ready-made strategy or the
 * user's ByeDPI string, shown with what it gives or why it is not taken. A preset the phone cannot send is dimmed
 * with what it lacks; a string that needs it says so and is kept.
 */
@Composable
internal fun DpiScreen(values: SettingValues, info: DpiInfo, onBack: () -> Unit) =
    ListPage(stringResource(R.string.dpi_title), stringResource(R.string.screen_back), onBack) {
        val c = Niko.colors
        val mode = values.text(Key.DPI_DIRECT).toIntOrNull() ?: DpiDirect.OFF
        Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            Text(stringResource(R.string.dpi_about), NikoType.bodySm, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
            if (mode != DpiDirect.OFF) {
                Text(stringResource(R.string.dpi_rules, info.rules), NikoType.bodySm, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
            }
        }
        Segmented(
            null,
            listOf(
                DpiDirect.OFF to stringResource(R.string.dpi_mode_off),
                DpiDirect.PRESET to stringResource(R.string.dpi_mode_preset),
                DpiDirect.CUSTOM to stringResource(R.string.dpi_mode_custom),
            ),
            mode,
            block = true,
            name = stringResource(R.string.set_dpi_direct),
        ) { values.setChoice(Key.DPI_DIRECT, it.toString()) }
        when (mode) {
            DpiDirect.PRESET -> {
                val stored = values.text(Key.DPI_DIRECT_PRESET)
                ListGroup(Modifier.selectableGroup()) {
                    DpiDirect.presets.forEachIndexed { i, preset ->
                        val on = preset.id == stored
                        val lacks = info.capabilities?.lacks(preset.strategy).orEmpty()
                        val sub = if (lacks.isEmpty()) DpiDirect.describe(preset.strategy) else stringResource(R.string.dpi_unavailable, lacks.joinToString(", "))
                        ListRow(
                            dpiPresetName(preset.id),
                            Modifier.alpha(if (lacks.isEmpty()) 1f else NikoOpacity.DISABLED)
                                .selectable(on, enabled = lacks.isEmpty(), role = Role.RadioButton) { values.setChoice(Key.DPI_DIRECT_PRESET, preset.id) },
                            sub = { Sub(sub, mono = lacks.isEmpty()) },
                            titleBold = on,
                            divider = i > 0,
                            titleLines = 2,
                            lead = { Radio(on) },
                        )
                    }
                }
            }

            DpiDirect.CUSTOM -> Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                val text = values.text(Key.DPI_DIRECT_CUSTOM)
                Field(stringResource(R.string.dpi_custom_label), text, { values.setText(Key.DPI_DIRECT_CUSTOM, it) }, mono = true)
                when (val parsed = DpiDirect.parseByeDpi(text)) {
                    is DpiDirect.Parsed.Ok -> {
                        Text("→ " + DpiDirect.describe(parsed.strategy), NikoType.code, c.ink)
                        val lacks = info.capabilities?.lacks(parsed.strategy).orEmpty()
                        if (lacks.isNotEmpty()) {
                            Text(stringResource(R.string.dpi_unavailable, lacks.joinToString(", ")), NikoType.caption, c.danger)
                        }
                    }

                    is DpiDirect.Parsed.Error -> Text(dpiError(parsed), NikoType.caption, c.danger)
                }
                Text(stringResource(R.string.dpi_custom_help), NikoType.caption, c.ink2)
            }
        }
    }
