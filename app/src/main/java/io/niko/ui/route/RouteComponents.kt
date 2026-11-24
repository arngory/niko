package io.niko.ui.route

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Text
import io.niko.ui.home.TonalIconButton
import io.niko.ui.home.pressed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The routing screens' components beyond HomeComponents' and SettingsComponents' (components/bundle.css: m-btn,
// m-check, m-banner, m-row__index, m-section__head), drawn as theirs are.

/** m-btn m-btn--primary: ink, its text on-ink, the width it is given; ink-pressed while held. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.height(NikoSize.control).clip(CircleShape).alpha(if (enabled) 1f else NikoOpacity.DISABLED)
            .background(if (pressed(source)) c.inkPressed else c.ink)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NikoSpace.s6),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, NikoType.body.copy(fontWeight = NikoType.headline.fontWeight), c.onInk, maxLines = 1)
    }
}

/** m-check: a square of radius-xs, its ring line-strong when off; ink with an on-ink check when on. */
@Composable
internal fun Checkbox(on: Boolean) {
    val c = Niko.colors
    val shape = RoundedCornerShape(NikoRadius.xs)
    Box(
        Modifier.size(20.dp).clip(shape).background(if (on) c.ink else Color.Transparent)
            .border(NikoStroke.bold, if (on) c.ink else c.lineStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(R.drawable.ic_l_check, c.onInk, 16.dp)
    }
}

/** m-row with m-check at its start: the whole row ticks it, a checkbox to a screen reader. */
@Composable
internal fun CheckRow(title: String, on: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = false) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    ListRow(
        title,
        Modifier.background(if (pressed(source)) c.surface2 else Color.Transparent)
            .toggleable(on, source, indication = null, role = Role.Checkbox, onValueChange = onChange),
        divider = divider,
        titleLines = 2,
        lead = { Checkbox(on) },
    )
}

/** m-banner: an icon and its text on surface-1; a press does what [onClick] says (the guide). */
@Composable
internal fun Banner(@DrawableRes icon: Int, text: String, onClick: (() -> Unit)? = null) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md))
            .background(if (onClick != null && pressed(source)) c.surface2 else c.surface1)
            .let { if (onClick != null) it.clickable(source, indication = null, role = Role.Button, onClick = onClick) else it }
            .padding(NikoSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        Icon(icon, c.ink2)
        Text(text, NikoType.bodySm, c.ink, Modifier.weight(1f))
        if (onClick != null) Icon(R.drawable.ic_l_chevron_right, c.ink2)
    }
}

/** m-banner m-banner--danger: what went wrong, in danger on danger-soft. */
@Composable
internal fun DangerBanner(text: String) {
    val c = Niko.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.dangerSoft).padding(NikoSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        Icon(R.drawable.ic_l_triangle_alert, c.danger)
        Text(text, NikoType.bodySm, c.danger, Modifier.weight(1f))
    }
}

/** m-row__index: a step's number in t-label, on surface-2, as wide as an icon. */
@Composable
internal fun IndexBadge(index: Int) {
    val c = Niko.colors
    Box(Modifier.size(NikoSize.icon).background(c.surface2, CircleShape), contentAlignment = Alignment.Center) {
        Text(index.toString(), NikoType.label.copy(textAlign = TextAlign.Center), c.ink2, maxLines = 1)
    }
}

/** An app's tile where its icon would be: its first letter on surface-2, radius-sm (the canvas's apps). */
@Composable
internal fun LetterTile(name: String) {
    val c = Niko.colors
    Box(Modifier.size(32.dp).background(c.surface2, RoundedCornerShape(NikoRadius.sm)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            NikoType.bodySm.copy(fontWeight = NikoType.headline.fontWeight), c.ink, maxLines = 1,
        )
    }
}

/** m-section with m-section__head: the overline and a tonal icon button at its end, over its group. */
@Composable
internal fun SectionWithButton(
    title: String,
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Niko.colors
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Row(Modifier.fillMaxWidth().padding(start = NikoSpace.s4), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), NikoType.label, c.ink2, Modifier.weight(1f))
            TonalIconButton(icon, label, onClick)
        }
        ListGroup(content = content)
    }
}

/**
 * A row of a list that a swipe to the start deletes (the old lists' swipe, with their undo): the row follows the
 * finger over danger-soft and its trash, and past 40% of its width (or flung) [onDelete] takes it. For a screen
 * reader the same is the row's action [label]. Not [enabled]: it does not move.
 */
@Composable
internal fun SwipeToDelete(label: String, enabled: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
    // the same tree on or off: a row's own gestures (a drag of its grip) go on while this one is off
    val c = Niko.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxWidth().onSizeChanged { width = it.width }
            .semantics { if (enabled) customActions = listOf(CustomAccessibilityAction(label) { onDelete(); true }) },
    ) {
        if (offset.value != 0f) {
            Box(
                Modifier.matchParentSize().background(c.dangerSoft).padding(horizontal = NikoSpace.s5),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(R.drawable.ic_l_trash_2, c.danger)
            }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.background(c.surface1)
                .draggable(
                    rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-width.toFloat(), 0f)) }
                    },
                    Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = { velocity ->
                        if (offset.value < -width * 0.4f || velocity < -2000f) {
                            offset.animateTo(-width.toFloat())
                            onDelete()
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
