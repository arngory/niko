package io.niko.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.ListPage
import io.niko.ui.settings.Segmented
import io.niko.ui.settings.Switch

/** What P-Rules asks of its fragment. */
interface RulesActions {
    fun back()
    /** the routing guide, in the app's language (D-RULES-09) */
    fun guide()
    fun add()
    fun edit(id: Long)
    fun enable(id: Long, on: Boolean)
    /** a step of a drag: the rule at [from] now at [to], shown at once */
    fun move(from: Int, to: Int)
    /** the drag let go: the new order is written */
    fun moved()
    /** a swipe: the rule goes, with an undo */
    fun delete(id: Long)
    /** a preset picked: Russia asks and adds its rules, none asks and removes them (D-RULES-05) */
    fun preset(preset: RulePreset)
    fun lists()
    fun check()
    /** the standard rules instead of these, after a question */
    fun reset()
}

/**
 * Маршрутизация (P-Rules, D-RULES-01): the guide's warning (D-RULES-09), the preset, the rules in their order (a switch
 * each, the grip to drag, a swipe to delete, a press to edit), what no rule takes; then the rule sets (D-RULES-03), the
 * route check (D-CHECK-02) and the reset.
 */
@Composable
fun RulesScreen(state: RulesState, actions: RulesActions) {
    val c = Niko.colors
    ListPage(
        stringResource(R.string.rt_title), stringResource(R.string.screen_back), actions::back,
        actions = {
            IconButton(R.drawable.ic_l_book_open, stringResource(R.string.rt_guide), onClick = actions::guide)
            IconButton(R.drawable.ic_l_plus, stringResource(R.string.rt_add), onClick = actions::add)
        },
    ) {
        Banner(R.drawable.ic_l_triangle_alert, stringResource(R.string.rt_warn), actions::guide)
        Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            val title = stringResource(R.string.rt_preset)
            Text(title.uppercase(), NikoType.label, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
            Segmented(
                null,
                listOf(RulePreset.NONE to stringResource(R.string.rt_preset_none), RulePreset.RUSSIA to stringResource(R.string.rt_preset_russia)),
                state.preset, block = true, name = title, onPick = actions::preset,
            )
        }
        Section(stringResource(R.string.rt_rules)) {
            val rules = state.rules.orEmpty()
            RuleRows(rules, actions)
            ListRow(
                stringResource(R.string.rt_rest),
                divider = rules.isNotEmpty(),
                sub = { Sub("→ " + stringResource(R.string.rt_out_proxy)) },
            )
        }
        ListGroup {
            ListRow(
                stringResource(R.string.rt_lists),
                sub = state.listsDate?.let { date -> { Sub(date) } },
                trail = { Trail(null) },
                onClick = actions::lists,
            )
            ListRow(stringResource(R.string.rt_check), divider = true, trail = { Trail(null) }, onClick = actions::check)
            ListRow(
                stringResource(R.string.rt_reset), divider = true,
                lead = { Icon(R.drawable.ic_l_rotate_ccw, c.ink2) },
                onClick = actions::reset,
            )
        }
    }
}

/** The rules, a drag of a grip moving its rule a step each time it passes half of its neighbour. */
@Composable
private fun RuleRows(rules: List<RuleRow>, actions: RulesActions) {
    val current by rememberUpdatedState(rules)
    var dragging by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    val delete = stringResource(R.string.rt_delete)
    val up = stringResource(R.string.rt_move_up)
    val down = stringResource(R.string.rt_move_down)
    rules.forEachIndexed { i, rule ->
        key(rule.id) {
            val dragged = dragging == rule.id
            Box(
                Modifier.zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragged) dragY else 0f }
                    .onSizeChanged { heights[rule.id] = it.height },
            ) {
                SwipeToDelete(delete, enabled = dragging == null, onDelete = { actions.delete(rule.id) }) {
                    val grip = Modifier.pointerInput(rule.id) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = rule.id; dragY = 0f },
                            onDragEnd = { dragging = null; dragY = 0f; actions.moved() },
                            onDragCancel = { dragging = null; dragY = 0f; actions.moved() },
                        ) { change, dy ->
                            change.consume()
                            dragY += dy
                            val list = current
                            val at = list.indexOfFirst { it.id == rule.id }
                            if (at < 0) return@detectVerticalDragGestures
                            if (dragY > 0 && at < list.lastIndex) {
                                val next = heights[list[at + 1].id] ?: return@detectVerticalDragGestures
                                if (dragY > next / 2f) {
                                    actions.move(at, at + 1)
                                    dragY -= next
                                }
                            } else if (dragY < 0 && at > 0) {
                                val previous = heights[list[at - 1].id] ?: return@detectVerticalDragGestures
                                if (-dragY > previous / 2f) {
                                    actions.move(at, at - 1)
                                    dragY += previous
                                }
                            }
                        }
                    }.semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(up) { actions.move(i, i - 1); actions.moved(); true }.takeIf { i > 0 },
                            CustomAccessibilityAction(down) { actions.move(i, i + 1); actions.moved(); true }.takeIf { i < rules.lastIndex },
                        )
                    }
                    RuleRowView(rule, divider = i > 0 && !dragged, lifted = dragged, actions = actions) { Grip(rule.name, grip) }
                }
            }
        }
    }
}

/** A rule's row: its name and summary, its switch, then [content], the grip it is dragged by. */
@Composable
private fun RuleRowView(rule: RuleRow, divider: Boolean, lifted: Boolean, actions: RulesActions, content: @Composable () -> Unit) {
    val c = Niko.colors
    val summary = ruleSummary(rule)
    ListRow(
        rule.name,
        Modifier.background(if (lifted) c.surface2 else androidx.compose.ui.graphics.Color.Transparent),
        divider = divider,
        sub = { Text(summary, NikoType.bodySm, c.ink2, maxLines = 2) },
        trail = {
            Box(
                Modifier.height(NikoSize.tap)
                    .toggleable(rule.enabled, remember { MutableInteractionSource() }, indication = null, role = Role.Switch) {
                        actions.enable(rule.id, it)
                    }
                    .semantics { contentDescription = rule.name },
                contentAlignment = Alignment.Center,
            ) {
                Switch(rule.enabled)
            }
            content()
        },
        onClick = { actions.edit(rule.id) },
    )
}

/** m-row__grip: the handle a rule is dragged by, [modifier] its drag. */
@Composable
private fun Grip(name: String, modifier: Modifier = Modifier) {
    val drag = stringResource(R.string.rt_drag, name)
    Box(
        modifier.size(NikoSize.icon, NikoSize.tap).semantics { contentDescription = drag },
        contentAlignment = Alignment.Center,
    ) {
        Icon(R.drawable.ic_l_menu, Niko.colors.ink2)
    }
}

/** The row's second line: the apps and the conditions, then where it goes; «→ Напрямую» for a rule of none. */
@Composable
private fun ruleSummary(rule: RuleRow): String {
    val parts = buildList {
        if (rule.apps > 0) add(pluralStringResource(R.plurals.rt_apps, rule.apps, rule.apps))
        addAll(rule.conditions)
    }
    val out = outName(rule.out, rule.profile)
    return if (parts.isEmpty()) "→ $out" else parts.joinToString(" · ") + " → " + out
}

/** Where a rule sends what it matches, as the canvas names it; a profile by its name. */
@Composable
internal fun outName(out: RuleOut, profile: String?) = when (out) {
    RuleOut.PROXY -> stringResource(R.string.rt_out_proxy)
    RuleOut.DIRECT -> stringResource(R.string.rt_out_direct)
    RuleOut.BLOCK -> stringResource(R.string.rt_out_block)
    RuleOut.PROFILE -> profile ?: stringResource(R.string.error_title)
}
