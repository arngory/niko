package io.niko.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.fmt.TAG_BLOCK
import io.niko.fmt.TAG_BYPASS
import io.niko.fmt.TAG_PROXY
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.settings.Field
import io.niko.ui.settings.FieldButton
import io.niko.ui.settings.ListPage

/** What P-Check asks of its fragment. */
interface CheckActions {
    fun back()
    fun form(form: CheckForm)
    /** «Ещё» opened or closed */
    fun more(open: Boolean)
    /** the core is asked: which rule the connection would match */
    fun run()
}

/**
 * Проверка маршрута (P-Check, D-CHECK-02…05): the address and its check, «Ещё» with the app it comes from, UDP and
 * whether the core's DNS is asked (D-CHECK-08); then the core's answer: the rule that decided and where it goes, the
 * outbound, what DNS gave and the rules saw; the rules on the way (Шаги) and the core's notes. No connection is
 * opened (ui/MainActivity.routeTest).
 */
@Composable
fun CheckScreen(state: CheckState, actions: CheckActions) {
    val c = Niko.colors
    val form = state.form
    ListPage(stringResource(R.string.rt_check), stringResource(R.string.screen_back), actions::back) {
        Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            Field(
                stringResource(R.string.rt_address), form.target, { actions.form(form.copy(target = it)) }, mono = true,
                button = FieldButton(R.drawable.ic_l_search, stringResource(R.string.route_test_run), actions::run),
                onGo = actions::run,
            )
            if (state.outcome is CheckOutcome.BadTarget) {
                Text(stringResource(R.string.route_test_bad_target), NikoType.caption, c.danger)
            } else {
                Text(stringResource(R.string.route_test_target_hint), NikoType.caption, c.ink2)
            }
        }
        More(state, actions)
        when (val outcome = state.outcome) {
            null, CheckOutcome.BadTarget -> {}
            CheckOutcome.Running -> ListGroup { ListRow(stringResource(R.string.rt_checking)) }
            is CheckOutcome.Failed -> DangerBanner(outcome.message)
            is CheckOutcome.Done -> Answer(outcome.result)
        }
    }
}

/** «Ещё» (D-CHECK-08): closed, a row; open, the app (with the installed ones that match), UDP, the core's DNS. */
@Composable
private fun More(state: CheckState, actions: CheckActions) {
    val c = Niko.colors
    val form = state.form
    ListGroup {
        ListRow(
            stringResource(R.string.rt_more),
            trail = { Icon(R.drawable.ic_l_chevron_right, c.ink2, modifier = Modifier.rotate(if (state.more) 270f else 90f)) },
            onClick = { actions.more(!state.more) },
        )
        if (state.more) {
            Column(Modifier.padding(start = NikoSpace.s4, end = NikoSpace.s4, bottom = NikoSpace.s3)) {
                Field(stringResource(R.string.route_test_app_hint), form.app, { actions.form(form.copy(app = it)) })
            }
            for (app in appSuggestions(state.apps, form.app)) {
                val pkg = packageOfChoice(app)
                ListRow(
                    if (pkg != null) app.removeSuffix(" ($pkg)") else app,
                    divider = true,
                    sub = pkg?.let { { Sub(it, mono = true) } },
                    onClick = { actions.form(form.copy(app = app)) },
                )
            }
            CheckRow(stringResource(R.string.route_test_udp), form.udp, { actions.form(form.copy(udp = it)) }, divider = true)
            CheckRow(stringResource(R.string.route_test_dns_query), form.resolve, { actions.form(form.copy(resolve = it)) }, divider = true)
        }
    }
}

/** The core's answer: what decided and where to, then the steps before it and the notes. */
@Composable
private fun Answer(r: CheckResult) {
    val c = Niko.colors
    val failed = stringResource(R.string.route_test_failed)
    val out = r.outbound.takeIf { it.isNotEmpty() }?.let { tagName(it) }
    ListGroup {
        when {
            r.index < 0 && r.error.isNotEmpty() -> ListRow(failed, sub = { Lines(r.error, tone = c.danger) })
            r.index < 0 -> ListRow(
                stringResource(R.string.route_test_no_rule), titleLines = 2,
                trail = { if (out != null) Text(out, NikoType.bodySm, c.ink2, maxLines = 1) },
            )
            else -> {
                ListRow(
                    stringResource(R.string.rt_rule_n, r.index),
                    sub = r.rule.takeIf { it.isNotEmpty() }?.let { { Lines(it, mono = true) } },
                    trail = { Text(out ?: r.action, NikoType.bodySm, c.ink2, maxLines = 1) },
                )
                if (r.error.isNotEmpty()) ListRow(failed, divider = true, sub = { Lines(r.error, tone = c.danger) })
            }
        }
        val exit = r.outbound.ifEmpty { r.action }
        if (exit.isNotEmpty()) {
            ListRow(stringResource(R.string.rt_exit), divider = true, trail = { Text(exit, NikoType.code, c.ink2, maxLines = 1) })
        }
        if (r.resolved.isNotEmpty()) {
            ListRow("DNS", divider = true, sub = { Lines(stringResource(R.string.route_test_dns) + ": " + r.resolved.joinToString(", ")) })
        }
        if (r.destination.isNotEmpty() && r.error.isEmpty()) {
            ListRow(
                stringResource(R.string.route_test_seen), divider = true,
                sub = { Lines(r.destination + if (r.fakeIp) " (FakeDNS)" else "", mono = true) },
            )
        }
    }
    if (r.steps.isNotEmpty()) {
        Section(stringResource(R.string.rt_steps)) {
            r.steps.forEachIndexed { i, step ->
                val detail = buildList {
                    if (step.rule.isNotEmpty()) add(step.rule)
                    if (step.note.isNotEmpty()) add(step.note)
                    if (step.resolved.isNotEmpty()) add(step.resolved.joinToString(", "))
                }.joinToString("\n")
                ListRow(
                    step.action,
                    divider = i > 0,
                    lead = { IndexBadge(step.index) },
                    sub = detail.takeIf { it.isNotEmpty() }?.let { { Lines(it, mono = true) } },
                )
            }
        }
    }
    if (r.notes.isNotEmpty()) {
        Section(stringResource(R.string.rt_notes)) {
            r.notes.forEachIndexed { i, note -> ListRow(note, divider = i > 0, titleLines = 8) }
        }
    }
}

/** An outbound's tag as the rules name it: the proxy, direct, block; a profile's own tag as it is. */
@Composable
private fun tagName(tag: String) = when (tag) {
    TAG_PROXY -> stringResource(R.string.rt_out_proxy)
    TAG_BYPASS -> stringResource(R.string.rt_out_direct)
    TAG_BLOCK -> stringResource(R.string.rt_out_block)
    else -> tag
}

/** m-row__sub of several lines: a rule, a note, an error. */
@Composable
private fun Lines(text: String, mono: Boolean = false, tone: androidx.compose.ui.graphics.Color = Niko.colors.ink2) =
    Text(text, if (mono) NikoType.code else NikoType.bodySm, tone, maxLines = 4)
