package io.niko.ui.compose

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** The resource id the positive button answers to, an alert dialog's own. */
const val POSITIVE_ID = "android:id/button1"

/** A dialog's text field: its [hint], what it starts with, [digits] the longest run of digits it takes (0: any text). */
data class NikoDialogField(val hint: String = "", val initial: String = "", val digits: Int = 0)

/** A dialog's button: [primary] the ink one, the others ghosts; [danger] a primary that destroys. */
data class NikoDialogButton(val text: String, val primary: Boolean = false, val danger: Boolean = false)

/**
 * m-dialog as it shows: [icon] over the [title], the [message] ([selectable] to copy from), a [field], [choices] to
 * pick one of (radios, [chosen] at first) or [items] that each answer at once, then the [buttons] at its end.
 */
data class NikoDialogSpec(
    @DrawableRes val icon: Int? = null,
    val title: String? = null,
    val message: String? = null,
    val selectable: Boolean = false,
    val field: NikoDialogField? = null,
    val choices: List<String> = emptyList(),
    val chosen: Int = 0,
    val items: List<String> = emptyList(),
    val buttons: List<NikoDialogButton> = emptyList(),
)

/**
 * The card of [NikoDialog], drawn alone (a snapshot): [onButton] with the button's index, the field's text and the
 * choice; [onItem] an item's index.
 */
// FlowRow and testTagsAsResourceId are still marked experimental
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun NikoDialogCard(spec: NikoDialogSpec, onButton: (Int, String, Int) -> Unit, onItem: (Int) -> Unit) {
    val c = Niko.colors
    var text by remember { mutableStateOf(spec.field?.initial.orEmpty()) }
    var chosen by remember { mutableIntStateOf(spec.chosen) }
    Column(
        Modifier.semantics { testTagsAsResourceId = true }
            .widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1)
            .verticalScroll(rememberScrollState()).padding(NikoSpace.s6),
        verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
    ) {
        if (spec.icon != null) DialogIcon(spec.icon, c.ink2)
        if (spec.title != null) BasicText(spec.title, style = NikoType.headline.copy(color = c.ink))
        if (spec.message != null) {
            if (spec.selectable) SelectionContainer { BasicText(spec.message, style = NikoType.bodySm.copy(color = c.ink2)) }
            else BasicText(spec.message, style = NikoType.bodySm.copy(color = c.ink2))
        }
        spec.field?.let { field ->
            val source = remember { MutableInteractionSource() }
            val focused by source.collectIsFocusedAsState()
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Box(
                Modifier.fillMaxWidth().height(NikoSize.control).clip(RoundedCornerShape(NikoRadius.sm)).background(c.surface2)
                    .border(NikoStroke.bold, if (focused) c.ink else Color.Transparent, RoundedCornerShape(NikoRadius.sm))
                    .padding(horizontal = NikoSpace.s4),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) BasicText(field.hint, style = NikoType.code.copy(color = c.ink2), maxLines = 1)
                BasicTextField(
                    text, { text = if (field.digits > 0) it.filter(Char::isDigit).take(field.digits) else it },
                    Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = spec.title.orEmpty() },
                    textStyle = NikoType.code.copy(color = c.ink), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (field.digits > 0) KeyboardType.Number else KeyboardType.Text),
                    interactionSource = source, cursorBrush = SolidColor(c.ink),
                )
            }
        }
        if (spec.choices.isNotEmpty()) Column {
            spec.choices.forEachIndexed { i, choice ->
                DialogRow(choice, divider = i > 0, radio = i == chosen) { chosen = i }
            }
        }
        if (spec.items.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(NikoRadius.md)).background(c.surface2)) {
            spec.items.forEachIndexed { i, item -> DialogRow(item, divider = i > 0, chevron = true, side = NikoSpace.s4) { onItem(i) } }
        }
        if (spec.buttons.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s2),
        ) {
            spec.buttons.forEachIndexed { i, button ->
                // the positive button under the id an alert dialog gives it: what drives the app from outside (the
                // end-to-end run's uiautomator) finds it as before
                val positive = button.primary || button.danger
                DialogButton(button, if (positive) Modifier.testTag(POSITIVE_ID) else Modifier) { onButton(i, text, chosen) }
            }
        }
    }
}

@Composable
private fun DialogIcon(@DrawableRes icon: Int, tint: Color) =
    androidx.compose.foundation.Image(painterResource(icon), null, Modifier.size(NikoSize.icon), colorFilter = ColorFilter.tint(tint))

/** A row of the dialog: a radio at its start or a chevron at its end. */
@Composable
private fun DialogRow(text: String, divider: Boolean, radio: Boolean? = null, chevron: Boolean = false, side: androidx.compose.ui.unit.Dp = 0.dp, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val line = c.line
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .drawBehind { if (divider) drawRect(line, Offset(side.toPx(), 0f), Size(size.width - side.toPx(), NikoStroke.hairline.toPx())) }
            .background(if (pressed) c.surface3 else Color.Transparent)
            .clickable(source, indication = null, role = if (radio != null) Role.RadioButton else Role.Button, onClick = onClick)
            .padding(horizontal = side, vertical = NikoSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        if (radio != null) Box(
            Modifier.size(20.dp).border(NikoStroke.bold, if (radio) c.ink else c.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (radio) Box(Modifier.size(10.dp).background(c.ink, CircleShape))
        }
        BasicText(text, Modifier.weight(1f), style = NikoType.body.copy(color = c.ink))
        if (chevron) DialogIcon(io.niko.R.drawable.ic_l_chevron_right, c.ink2)
    }
}

/** m-btn: primary in ink (danger: in danger), a ghost in text alone. */
@Composable
private fun DialogButton(button: NikoDialogButton, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = when {
        button.danger -> c.danger
        button.primary -> if (pressed) c.inkPressed else c.ink
        else -> if (pressed) c.surface2 else Color.Transparent
    }
    Box(
        modifier.height(NikoSize.control).clip(CircleShape).background(fill)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (button.primary || button.danger) NikoSpace.s6 else NikoSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            button.text, maxLines = 1,
            style = NikoType.body.copy(fontWeight = NikoType.headline.fontWeight, color = if (button.primary || button.danger) c.onInk else c.ink),
        )
    }
}

/**
 * A dialog in Niko's design (m-dialog), for the questions screens ask from code: built like an alert dialog's builder,
 * drawn by Compose in a window of its own. A button or an item closes it after its action; outside it and back cancel
 * it ([cancelable]), which runs [onCancel].
 */
class NikoDialog(private val context: Context) {

    private var spec = NikoDialogSpec()
    private val actions = ArrayList<(String, Int) -> Unit>()
    private var onItem: (Int) -> Unit = {}
    private var cancelable = true
    private var onCancel: () -> Unit = {}
    private var onDismiss: () -> Unit = {}

    fun icon(@DrawableRes icon: Int) = apply { spec = spec.copy(icon = icon) }
    fun title(title: String) = apply { spec = spec.copy(title = title) }
    fun title(title: Int) = title(context.getString(title))
    fun message(message: String, selectable: Boolean = false) = apply { spec = spec.copy(message = message, selectable = selectable) }
    fun message(message: Int) = message(context.getString(message))
    fun field(hint: String = "", initial: String = "", digits: Int = 0) = apply { spec = spec.copy(field = NikoDialogField(hint, initial, digits)) }
    fun choices(choices: List<String>, chosen: Int = 0) = apply { spec = spec.copy(choices = choices, chosen = chosen) }
    fun items(items: List<String>, onItem: (Int) -> Unit) = apply {
        spec = spec.copy(items = items)
        this.onItem = onItem
    }

    /** The ink button, at the end; [action] gets the field's text and the choice. */
    fun primary(text: String, danger: Boolean = false, action: (String, Int) -> Unit = { _, _ -> }) = apply {
        spec = spec.copy(buttons = spec.buttons + NikoDialogButton(text, primary = !danger, danger = danger))
        actions += action
    }

    fun primary(text: Int, danger: Boolean = false, action: (String, Int) -> Unit = { _, _ -> }) = primary(context.getString(text), danger, action)

    /** A ghost button before the ink one. */
    fun ghost(text: String, action: () -> Unit = {}) = apply {
        spec = spec.copy(buttons = spec.buttons + NikoDialogButton(text))
        actions += { _, _ -> action() }
    }

    fun ghost(text: Int, action: () -> Unit = {}) = ghost(context.getString(text), action)
    fun cancelable(on: Boolean) = apply { cancelable = on }
    fun onCancel(action: () -> Unit) = apply { onCancel = action }

    /** Whatever closes it: a button, an item, a cancel. */
    fun onDismiss(action: () -> Unit) = apply { onDismiss = action }

    fun show(): Dialog {
        val dialog = ComponentDialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        val shown = spec
        dialog.setContentView(
            ComposeView(context).apply {
                setContent {
                    NikoTheme {
                        val c = Niko.colors
                        Box(
                            Modifier.fillMaxWidth().background(c.scrim)
                                .clickable(remember { MutableInteractionSource() }, indication = null) { if (cancelable) dialog.cancel() }
                                .padding(NikoSpace.s6),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }) {
                                NikoDialogCard(
                                    shown,
                                    // the answer first: the dismissal that follows must not read as a cancel
                                    onButton = { i, text, chosen ->
                                        actions[i](text, chosen)
                                        dialog.dismiss()
                                    },
                                    onItem = { i ->
                                        onItem(i)
                                        dialog.dismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        dialog.setCancelable(cancelable)
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnDismissListener { onDismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (shown.field != null) {
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }
        }
        dialog.show()
        return dialog
    }
}
