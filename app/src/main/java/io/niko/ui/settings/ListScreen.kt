package io.niko.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.ui.home.ListGroup
import io.niko.ui.home.Section

// Variant В of docs/ui/platforms.md: a list screen of shared/screens, described once and drawn here and in the window
// (desktop/lib/settings/list_screen.dart). shared/tool/gen_screens.py writes the descriptions as ListScreens; this
// draws them with the components the hand-made screens take.

/** A list screen: its name in the top bar, the way back's label, its lines top to bottom. */
class ListSpec(@StringRes val title: Int, @StringRes val back: Int, val lines: List<Line>)

/** One part of the page (m-body's child). */
sealed interface Line

/** m-fields-row: fields side by side. */
class FieldsLine(val fields: List<FieldSpec>) : Line

/** One m-field across the page. */
class FieldLine(val field: FieldSpec) : Line

/** m-list: rows in a group of their own. */
class GroupLine(val items: List<Item>) : Line

/** m-section: rows under an overline. */
class SectionLine(@StringRes val title: Int, val items: List<Item>) : Line

/** A row of a group. */
sealed interface Item

/** A switch of the setting [key]; [enabledBy] the switch it needs on. */
class SwitchItem(val key: String, @StringRes val title: Int, val enabledBy: String? = null) : Item

/** A field of the setting [key]; [action] an icon button at its end, which the screen's host answers by its id. */
class FieldSpec(
    val key: String,
    @StringRes val label: Int,
    val input: FieldInput = FieldInput.TEXT,
    val mono: Boolean = false,
    val narrow: Boolean = false,
    val action: FieldAction? = null,
)

class FieldAction(val id: String, @DrawableRes val icon: Int, @StringRes val label: Int)

/** [spec] drawn over [values]; [onAction] gets the id of a field's action. */
@Composable
fun ListScreen(spec: ListSpec, values: SettingValues, onBack: () -> Unit, onAction: (String) -> Unit) =
    ListPage(stringResource(spec.title), stringResource(spec.back), onBack) {
        for (line in spec.lines) when (line) {
            is FieldsLine -> FieldsRow {
                for (field in line.fields) SpecField(field, values, onAction, if (field.narrow) Modifier.width(NarrowField) else Modifier.weight(1f))
            }
            is FieldLine -> SpecField(line.field, values, onAction)
            is GroupLine -> ListGroup { Items(line.items, values) }
            is SectionLine -> Section(stringResource(line.title)) { Items(line.items, values) }
        }
    }

@Composable
private fun SpecField(field: FieldSpec, values: SettingValues, onAction: (String) -> Unit, modifier: Modifier = Modifier) =
    Field(
        stringResource(field.label), values.text(field.key), { values.setText(field.key, it) }, modifier, field.input, field.mono,
        trail = field.action?.let { action ->
            { IconButton(action.icon, stringResource(action.label), muted = true, small = true) { onAction(action.id) } }
        },
    )

@Composable
private fun Items(items: List<Item>, values: SettingValues) = items.forEachIndexed { i, item ->
    when (item) {
        is SwitchItem -> SwitchRow(
            stringResource(item.title), values.flag(item.key), { values.setFlag(item.key, it) },
            enabled = item.enabledBy?.let(values::flag) ?: true, divider = i > 0,
        )
    }
}
