package io.niko.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoOpacity
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListRow
import io.niko.ui.home.Sheet
import io.niko.ui.home.Text
import io.niko.ui.home.pressed
import io.niko.ui.route.DangerBanner
import io.niko.ui.settings.IconButton

/**
 * Редактор JSON (D-PROFILE-02): the [value] typed, whether a step can be undone or redone, the [error] of the last
 * format or save, and [leaving] the question back raises over unsaved changes.
 */
data class JsonState(
    val value: TextFieldValue = TextFieldValue(""),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: JsonError? = null,
    val leaving: Boolean = false,
)

/** What the JSON editor asks of its activity. */
interface JsonActions {
    fun back()
    fun value(value: TextFieldValue)
    fun undo()
    fun redo()
    fun format()
    /** a key of the row over the keyboard */
    fun key(text: String)
    /** checked, formatted, stored; the screen closes */
    fun save()
    /** the question over unsaved changes answered: save, leave without, or stay (null) */
    fun leave(save: Boolean?)
}

/** The row over the keyboard: Tab and the characters JSON needs that a keyboard hides on its second page. */
internal val JsonKeys = listOf("Tab" to "    ", "{" to "{", "}" to "}", "," to ",", ":" to ":", "_" to "_", "\"" to "\"")

/**
 * The page: undo, redo and format in the bar; the error in place; the text with its lines numbered, scrolled both
 * ways; the keys and «Сохранить» over the keyboard.
 */
@Composable
fun JsonScreen(title: String, state: JsonState, actions: JsonActions) {
    val c = Niko.colors
    Box(Modifier.fillMaxSize().background(c.surface0)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
            ) {
                IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = actions::back)
                Text(title, NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
                BarButton(R.drawable.ic_l_undo_2, stringResource(R.string.undo), state.canUndo, actions::undo)
                BarButton(R.drawable.ic_l_redo_2, stringResource(R.string.redo), state.canRedo, actions::redo)
                IconButton(R.drawable.ic_l_braces, stringResource(R.string.format_config), onClick = actions::format)
            }
            Column(
                Modifier.weight(1f).padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s4),
                verticalArrangement = Arrangement.spacedBy(NikoSpace.s3),
            ) {
                state.error?.let { e ->
                    DangerBanner(if (e.line != null) stringResource(R.string.json_error_line, e.line, e.message) else e.message)
                }
                Editor(state, actions, Modifier.weight(1f).fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1)) {
                    for ((label, text) in JsonKeys) Key(label, Modifier.weight(1f)) { actions.key(text) }
                }
                io.niko.ui.route.PrimaryButton(stringResource(R.string.save), actions::save, Modifier.fillMaxWidth())
            }
        }
        if (state.leaving) LeaveSheet(actions)
    }
}

/** A bar's icon button that dims while it has nothing to do. */
@Composable
private fun BarButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) =
    IconButton(icon, label, Modifier.alpha(if (enabled) 1f else NikoOpacity.DISABLED), onClick = { if (enabled) onClick() })

/** The text on surface-1: the lines' numbers at its start, the error's line on danger-soft; no wrapping, both scrolls. */
@Composable
private fun Editor(state: JsonState, actions: JsonActions, modifier: Modifier = Modifier) {
    val c = Niko.colors
    val style = NikoType.code.copy(fontSize = 13.sp, lineHeight = 20.sp)
    val lines = remember(state.value.text) { state.value.text.count { it == '\n' } + 1 }
    val numbers = remember(lines) { (1..lines).joinToString("\n") }
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toPx() }
    val errorLine = state.error?.line?.takeIf { it in 1..lines }
    val soft = c.dangerSoft
    val label = stringResource(R.string.json_text)
    Box(modifier.clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1)) {
        Row(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = NikoSpace.s4)
                .drawBehind {
                    if (errorLine != null) drawRect(soft, Offset(0f, (errorLine - 1) * lineHeight), Size(size.width, lineHeight))
                },
        ) {
            Text(
                numbers, style.copy(textAlign = TextAlign.End), c.ink2,
                Modifier.padding(start = NikoSpace.s4, end = NikoSpace.s3).widthIn(min = 16.dp),
            )
            BasicTextField(
                state.value, actions::value,
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = NikoSpace.s4)
                    .semantics { contentDescription = label },
                textStyle = style.copy(color = c.ink),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii,
                ),
                cursorBrush = SolidColor(c.ink),
            )
        }
    }
}

/** m-btn--ghost of the key row: a character in mono, surface-2 pressed. */
@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.height(40.dp).clip(CircleShape).background(if (pressed(source)) c.surface2 else Color.Transparent)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, NikoType.code, c.ink, maxLines = 1)
    }
}

/** Back over unsaved changes: save and leave, leave, or stay. */
@Composable
private fun LeaveSheet(actions: JsonActions) {
    val c = Niko.colors
    val stay = { actions.leave(null) }
    BackHandler(onBack = stay)
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = stay),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(
                stringResource(R.string.unsaved_changes_prompt), NikoType.headline, c.ink,
                Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2),
            )
            Column(Modifier.padding(bottom = NikoSpace.s6)) {
                ListRow(stringResource(R.string.save), side = NikoSpace.s5, onClick = { actions.leave(true) })
                ListRow(stringResource(R.string.json_discard), side = NikoSpace.s5, divider = true, onClick = { actions.leave(false) })
                ListRow(stringResource(android.R.string.cancel), side = NikoSpace.s5, divider = true, onClick = stay)
            }
        }
    }
}
