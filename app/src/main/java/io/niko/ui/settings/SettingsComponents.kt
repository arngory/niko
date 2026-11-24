package io.niko.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoOpacity
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoStroke
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListRow
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.home.pressed

// The design system's components of the settings screens (components/bundle.css: m-field, m-switch, m-topbar), drawn
// as HomeComponents' are. The list screens are made of them by hand (InboundScreen; docs/ui/platforms.md, variant А).

/** What a field takes: text as typed, a port (digits, five at most), a secret (shown as dots). */
enum class FieldInput { TEXT, PORT, SECRET }

/** m-field__trail's icon button: what it does, for a screen reader, and its action. */
class FieldButton(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

/** m-fields-row > m-field--narrow: the width of a narrow field beside a wide one. */
internal val NarrowField = 104.dp

/**
 * A nested settings page: m-topbar with the way back and its [actions] (icon buttons at its end), then m-body, its
 * parts space-6 apart; [scroll] its scroll, for a page that moves it itself (the log, to its end).
 */
@Composable
internal fun ListPage(
    title: String,
    back: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Niko.colors
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
        ) {
            IconButton(R.drawable.ic_l_arrow_left, back, onClick = onBack)
            Text(title, NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
            actions()
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).navigationBarsPadding()
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
            content = content,
        )
    }
}

/**
 * m-seg: two or three choices in a pill, the chosen one in ink; [label] over it, none in a row's trail; [block] the
 * width of the page, the choices sharing it (m-seg--block); [name] for a screen reader without [label].
 */
@Composable
internal fun <T> Segmented(
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    block: Boolean = false,
    name: String? = label,
    onPick: (T) -> Unit,
) {
    val c = Niko.colors
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        if (label != null) Text(label, NikoType.caption, c.ink2, maxLines = 1)
        Row(
            (if (block) Modifier.fillMaxWidth() else Modifier).background(c.surface2, CircleShape).padding(NikoSpace.s1)
                .selectableGroup().let { m -> if (name != null) m.semantics { contentDescription = name } else m },
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
        ) {
            for ((value, text) in options) {
                val on = value == selected
                Box(
                    (if (block) Modifier.weight(1f) else Modifier).heightIn(min = 40.dp).clip(CircleShape)
                        .background(if (on) c.ink else Color.Transparent)
                        .selectable(on, role = Role.RadioButton) { onPick(value) }
                        .padding(horizontal = NikoSpace.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, NikoType.bodySm.copy(fontWeight = NikoType.headline.fontWeight), if (on) c.onInk else c.ink2, maxLines = 1)
                }
            }
        }
    }
}

/** m-iconbtn: an icon alone, size-tap round, surface-2 pressed; [muted] in ink-2, [small] the 20 icon (m-icon--sm). */
@Composable
internal fun IconButton(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.requiredSize(NikoSize.tap).clip(CircleShape).background(if (pressed(source)) c.surface2 else Color.Transparent)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (muted) c.ink2 else c.ink, if (small) 20.dp else NikoSize.icon)
    }
}

/** m-fields-row: fields side by side, space-3 apart; each takes Modifier.weight(1f) or the [NarrowField] width. */
@Composable
internal fun FieldsRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3), content = content)

/**
 * m-field: [label] over the control (surface-2, the stroke-bold border in ink while it has the focus), [mono] in
 * Geist Mono (m-field__control--mono), [button] at its end (m-field__trail, a muted small icon button); [multiLine]
 * a list, a line for each, three lines high at least; [onGo] the keyboard's Go does it (a check's field).
 */
@Composable
internal fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    input: FieldInput = FieldInput.TEXT,
    mono: Boolean = false,
    button: FieldButton? = null,
    multiLine: Boolean = false,
    onGo: (() -> Unit)? = null,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Text(label, NikoType.caption, c.ink2, maxLines = 1)
        Row(
            Modifier.fillMaxWidth().let { if (multiLine) it.heightIn(min = NikoSize.control) else it.height(NikoSize.control) }
                .clip(RoundedCornerShape(NikoRadius.sm)).background(c.surface2)
                .border(NikoStroke.bold, if (focused) c.ink else Color.Transparent, RoundedCornerShape(NikoRadius.sm))
                .padding(start = NikoSpace.s4, end = NikoSpace.s2, top = if (multiLine) NikoSpace.s3 else 0.dp, bottom = if (multiLine) NikoSpace.s3 else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2),
        ) {
            BasicTextField(
                value,
                { onValue(if (input == FieldInput.PORT) it.filter(Char::isDigit).take(5) else it) },
                Modifier.weight(1f).semantics { contentDescription = label },
                textStyle = (if (mono) NikoType.code else NikoType.body).copy(color = c.ink),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (input) {
                        FieldInput.TEXT -> KeyboardType.Text
                        FieldInput.PORT -> KeyboardType.Number
                        FieldInput.SECRET -> KeyboardType.Password
                    },
                    imeAction = if (onGo != null) ImeAction.Go else ImeAction.Default,
                ),
                keyboardActions = if (onGo != null) KeyboardActions(onGo = { onGo() }) else KeyboardActions.Default,
                singleLine = !multiLine,
                minLines = if (multiLine) 3 else 1,
                visualTransformation = if (input == FieldInput.SECRET) PasswordVisualTransformation('•') else VisualTransformation.None,
                interactionSource = source,
                cursorBrush = SolidColor(c.ink),
            )
            if (button != null) IconButton(button.icon, button.label, muted = true, small = true, onClick = button.onClick)
        }
    }
}

/** m-switch: off, a small ink-2 knob on surface-2 in a line-strong ring; on, a larger on-ink knob on ink. */
@Composable
internal fun Switch(on: Boolean) {
    val c = Niko.colors
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape).background(if (on) c.ink else c.surface2)
            .border(NikoStroke.bold, if (on) c.ink else c.lineStrong, CircleShape),
    ) {
        val knob = if (on) 24.dp else 16.dp
        Box(
            Modifier.offset(if (on) 24.dp else 8.dp, if (on) 4.dp else 8.dp).size(knob)
                .background(if (on) c.onInk else c.ink2, CircleShape),
        )
    }
}

/**
 * m-row with m-switch at its end: the whole row turns it, a switch to a screen reader; not [enabled], at
 * opacity-disabled (m-row.is-disabled). [divider] as ListRow's, [sub] a line under the title.
 */
@Composable
internal fun SwitchRow(
    title: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    divider: Boolean = false,
    sub: String? = null,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    ListRow(
        title,
        Modifier.alpha(if (enabled) 1f else NikoOpacity.DISABLED)
            .background(if (pressed(source)) c.surface2 else Color.Transparent)
            .toggleable(on, source, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        divider = divider,
        titleLines = 2,
        sub = sub?.let { { Sub(it) } },
        trail = { Switch(on) },
    )
}
