package io.niko.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
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
import io.niko.ui.home.ListRow
import io.niko.ui.home.Text
import io.niko.ui.home.pressed
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.Switch

// The editor's own forms of the settings components (SettingsComponents.kt): a field that can say what is wrong with
// it and be locked, a switch row whose explanation is not cut, the primary button of the action bar.

/**
 * m-field: [label] (two lines at most) over the control, [placeholder] in it while empty; a secret in dots with
 * m-field__trail's eye to show it; [error] in danger under it and as its border; [help] a caption under it; not
 * [enabled], at opacity-disabled and not typed in.
 */
@Composable
internal fun ProfileField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    input: Input = Input.TEXT,
    mono: Boolean = false,
    lines: Boolean = false,
    placeholder: String? = null,
    help: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    var shown by rememberSaveable { mutableStateOf(false) }
    val secret = input == Input.SECRET && !shown
    val shape = RoundedCornerShape(NikoRadius.sm)
    Column(modifier.alpha(if (enabled) 1f else NikoOpacity.DISABLED), verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Text(label, NikoType.caption, c.ink2, maxLines = 2)
        Row(
            Modifier.fillMaxWidth().let { if (lines) it.heightIn(min = NikoSize.control) else it.height(NikoSize.control) }
                .clip(shape).background(c.surface2)
                .border(NikoStroke.bold, if (error != null) c.danger else if (focused) c.ink else Color.Transparent, shape)
                .padding(start = NikoSpace.s4, end = NikoSpace.s2, top = if (lines) NikoSpace.s3 else 0.dp, bottom = if (lines) NikoSpace.s3 else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2),
        ) {
            val style = (if (mono) NikoType.code else NikoType.body)
            Box(Modifier.weight(1f).padding(end = if (input == Input.SECRET) 0.dp else NikoSpace.s2)) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style, c.ink2, maxLines = 1)
                BasicTextField(
                    value,
                    {
                        onValue(
                            when (input) {
                                Input.PORT -> it.filter(Char::isDigit).take(5)
                                Input.NUMBER -> it.filter(Char::isDigit)
                                else -> it
                            }
                        )
                    },
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = label
                        if (error != null) error(error)
                    },
                    enabled = enabled,
                    textStyle = style.copy(color = c.ink),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (input) {
                            Input.TEXT -> KeyboardType.Text
                            Input.PORT, Input.NUMBER -> KeyboardType.Number
                            // a password keyboard shown or not: the keyboard neither learns nor suggests a secret
                            Input.SECRET -> KeyboardType.Password
                        },
                        autoCorrectEnabled = input != Input.SECRET,
                    ),
                    singleLine = !lines,
                    minLines = if (lines) 3 else 1,
                    visualTransformation = if (secret) PasswordVisualTransformation('•') else VisualTransformation.None,
                    interactionSource = source,
                    cursorBrush = SolidColor(c.ink),
                )
            }
            if (input == Input.SECRET) {
                IconButton(
                    if (shown) R.drawable.ic_l_eye_off else R.drawable.ic_l_eye,
                    stringResource(if (shown) R.string.pe_hide else R.string.pe_show, label), muted = true, small = true,
                ) { shown = !shown }
            }
        }
        if (error != null) Text(error, NikoType.caption, c.danger)
        if (help != null) Text(help, NikoType.caption, c.ink2)
    }
}

/** m-row with m-switch at its end, [sub] under its title in full (an explanation, not a value). */
@Composable
internal fun ToggleRow(title: String, on: Boolean, onChange: (Boolean) -> Unit, divider: Boolean, sub: String? = null, enabled: Boolean = true) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    ListRow(
        title,
        Modifier.alpha(if (enabled) 1f else NikoOpacity.DISABLED)
            .background(if (pressed(source)) c.surface2 else Color.Transparent)
            .toggleable(on, source, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        divider = divider,
        titleLines = 2,
        sub = sub?.let { { Text(it, NikoType.bodySm, c.ink2) } },
        trail = { Switch(on) },
    )
}

/** m-btn m-btn--primary: ink, on-ink text, round; inkPressed while pressed. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.height(NikoSize.control).clip(CircleShape).background(if (pressed(source)) c.inkPressed else c.ink)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = NikoSpace.s6),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, NikoType.body.copy(fontWeight = NikoType.headline.fontWeight), c.onInk, maxLines = 1)
    }
}

/** A card of surface-1 (the canvas's «Port knocking» block): [title] in headline, its fields space-3 apart. */
@Composable
internal fun Card(title: String, content: @Composable () -> Unit) {
    val c = Niko.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1).padding(NikoSpace.s4),
        verticalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        Text(title, NikoType.headline, c.ink)
        content()
    }
}
