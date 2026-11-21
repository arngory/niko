package io.niko.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.niko.R
import io.niko.bg.BaseService
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoStroke
import io.niko.ui.compose.NikoType

// The design system's components the main screen takes (the canvas's mdll base, components/bundle.css: m-…),
// drawn from foundation with the tokens of ui/compose. Each says which class it is.

@Composable
internal fun Text(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text, modifier, style.copy(color = color), maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
)

@Composable
internal fun Icon(@DrawableRes icon: Int, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) =
    Image(painterResource(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/** A press shows as the surface one step up (the design has no ripple): m-row:active, m-tile:active. */
@Composable
internal fun pressed(source: MutableInteractionSource): Boolean = source.collectIsPressedAsState().value

/** m-topbar m-topbar--brand: the wordmark and the view switch (D-NAV-01). */
@Composable
internal fun TopBar(view: HomeView, onView: (HomeView) -> Unit, modifier: Modifier = Modifier) {
    val c = Niko.colors
    BoxWithConstraints(modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s5)) {
        // the two choices share what the wordmark leaves, up to 120 each (n-mode, --n-cell)
        val cell = min(120.dp, (maxWidth - 72.dp) / 2)
        Row(Modifier.fillMaxWidth().align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text("niko", NikoType.title.copy(fontSize = 24.sp, letterSpacing = (-0.04).em), c.ink, Modifier.weight(1f))
            val label = androidx.compose.ui.res.stringResource(R.string.home_view_switch)
            Row(
                Modifier.background(c.surface2, CircleShape).padding(NikoSpace.s1).selectableGroup()
                    .semantics { contentDescription = label },
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
            ) {
                for (v in HomeView.entries) {
                    val selected = v == view
                    Box(
                        Modifier.width(cell).height(40.dp).clip(CircleShape)
                            .background(if (selected) c.ink else Color.Transparent)
                            .selectable(selected, role = Role.RadioButton) { onView(v) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                if (v == HomeView.Simple) R.string.home_view_simple else R.string.home_view_advanced
                            ),
                            NikoType.bodySm.copy(fontWeight = NikoType.headline.fontWeight), if (selected) c.onInk else c.ink2,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** m-connect: off, connecting (the ring turns), on, error; its halo a hairline space-4 outside it (space-1 small). */
@Composable
internal fun ConnectButton(
    state: BaseService.State,
    failed: Boolean,
    onClick: () -> Unit,
    size: Dp = NikoSize.connect,
    modifier: Modifier = Modifier,
) {
    val c = Niko.colors
    val small = size <= NikoSize.connectSm
    val halo = if (small) NikoSpace.s1 else NikoSpace.s4
    val on = state == BaseService.State.Connected
    val busy = state == BaseService.State.Connecting || state == BaseService.State.Stopping
    val error = failed && !on && !busy
    val source = remember { MutableInteractionSource() }
    val down = pressed(source)
    val fill = when {
        error -> c.dangerSoft
        on -> if (down) c.inkPressed else c.ink
        else -> if (down) c.surface3 else c.surface2
    }
    val ink = when {
        error -> c.danger
        on -> c.onInk
        else -> c.ink
    }
    val ring = when {
        error -> c.danger
        on -> c.lineStrong
        else -> c.line
    }
    val label = androidx.compose.ui.res.stringResource(if (state.canStop) R.string.home_disconnect else R.string.home_connect)
    Box(modifier.size(size + halo * 2), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + halo * 2).border(NikoStroke.hairline, ring, CircleShape))
        if (busy) {
            // m-connect__ring: an arc of ink over the halo while it connects or stops
            val turn by rememberInfiniteTransition(label = "connect").animateFloat(
                0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "turn",
            )
            val arcInk = c.ink
            Canvas(Modifier.size(size + halo * 2)) {
                val stroke = NikoStroke.bold.toPx()
                drawArc(
                    arcInk, turn - 90f, 90f, false, topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(stroke),
                )
            }
        }
        Box(
            Modifier.size(size).clip(CircleShape).background(fill)
                .clickable(source, indication = null, role = Role.Button, enabled = !busy, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(R.drawable.ic_l_power, ink, if (small) NikoSize.icon else 40.dp)
        }
    }
}

/** m-status: the state in words, and under it the detail (time and speeds, or why it failed). */
@Composable
internal fun Status(label: String, detail: AnnotatedString?, tone: Color, start: Boolean = false, modifier: Modifier = Modifier) {
    val c = Niko.colors
    Column(
        modifier,
        horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NikoSpace.s1),
    ) {
        Text(label, if (start) NikoType.headline else NikoType.title, tone, maxLines = 1)
        if (!detail.isNullOrEmpty()) BasicText(
            detail, style = NikoType.code.copy(color = c.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis,
            inlineContent = rateIcons(c.ink2),
        )
    }
}

// the speeds' arrows in a line of text: the set's icons (arrow-down, arrow-up), not the font's glyphs, 14 by 14
internal const val RATE_DOWN = "rate-down"
internal const val RATE_UP = "rate-up"

@Composable
private fun rateIcons(tint: Color): Map<String, InlineTextContent> {
    fun icon(@DrawableRes res: Int) = InlineTextContent(Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)) {
        Icon(res, tint, size = 14.dp)
    }
    return mapOf(RATE_DOWN to icon(R.drawable.ic_l_arrow_down), RATE_UP to icon(R.drawable.ic_l_arrow_up))
}

/** m-tile: its icon and a chevron on top, the label and the value below; surface-1, surface-2 pressed. */
@Composable
internal fun Tile(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Column(
        modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(NikoRadius.md))
            .background(if (pressed(source)) c.surface2 else c.surface1)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(NikoSpace.s4),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, c.ink2)
            Icon(R.drawable.ic_l_chevron_right, c.ink2)
        }
        Column(Modifier.padding(top = NikoSpace.s4)) {
            Text(label, NikoType.caption, c.ink2, maxLines = 1)
            Text(value, NikoType.headline, c.ink, maxLines = 1)
        }
    }
}

/** m-list: a group of rows on surface-1, a hairline between them from space-4 in. */
@Composable
internal fun ListGroup(modifier: Modifier = Modifier, flush: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = Niko.colors
    Column(
        if (flush) modifier.fillMaxWidth() else modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1),
        content = content,
    )
}

/**
 * m-row: [lead], the title and under it [sub], then [trail]; [divider] draws the hairline above it (a row after
 * another in its group). [onClick] null: a row that is not pressed (the connection card's first row).
 */
@Composable
internal fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: (@Composable RowScope.() -> Unit)? = null,
    titleBold: Boolean = false,
    divider: Boolean = false,
    side: Dp = NikoSpace.s4,
    bottom: Dp = NikoSpace.s3,
    lead: (@Composable () -> Unit)? = null,
    trail: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    val line = c.line
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .drawBehind {
                if (divider) drawRect(line, Offset(side.toPx(), 0f), androidx.compose.ui.geometry.Size(size.width - side.toPx(), NikoStroke.hairline.toPx()))
            }
            .background(if (onClick != null && pressed(source)) c.surface2 else Color.Transparent)
            .let { if (onClick != null) it.clickable(source, indication = null, role = Role.Button, onClick = onClick) else it }
            .padding(start = side, end = side, top = NikoSpace.s3, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, if (titleBold) NikoType.body.copy(fontWeight = NikoType.headline.fontWeight) else NikoType.body, c.ink, maxLines = 1)
            if (sub != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2), content = sub)
        }
        if (trail != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2), content = trail)
    }
}

/** m-row__sub: the second line of a row. */
@Composable
internal fun Sub(text: String, mono: Boolean = false) =
    Text(text, if (mono) NikoType.code else NikoType.bodySm, Niko.colors.ink2, maxLines = 1)

/** m-row__trail: a value and a chevron. */
@Composable
internal fun Trail(value: String?, @DrawableRes icon: Int = R.drawable.ic_l_chevron_right, mono: Boolean = false) {
    val c = Niko.colors
    if (value != null) Text(value, if (mono) NikoType.code else NikoType.bodySm, c.ink2, maxLines = 1)
    Icon(icon, c.ink2)
}

/** m-tag: a transport or a kind, in capitals on surface-2. */
@Composable
internal fun Tag(text: String) {
    val c = Niko.colors
    Box(Modifier.height(20.dp).background(c.surface2, CircleShape).padding(horizontal = NikoSpace.s2), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), NikoType.label, c.ink2, maxLines = 1)
    }
}

/** m-section: an m-overline over its group. */
@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Niko.colors
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Text(title.uppercase(), NikoType.label, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
        ListGroup(content = content)
    }
}

/** m-sheet: surface-1, its top corners rounded, the grip with its handle on top. */
@Composable
internal fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Niko.colors
    Column(modifier.fillMaxWidth().background(c.surface1, RoundedCornerShape(topStart = NikoRadius.md, topEnd = NikoRadius.md))) {
        Box(Modifier.fillMaxWidth().padding(vertical = NikoSpace.s2), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(c.lineStrong, CircleShape))
        }
        content()
    }
}

/** m-tab: its name and count; the active one in ink, underlined in stroke-bold. */
@Composable
internal fun Tab(text: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val c = Niko.colors
    val ink = c.ink
    Row(
        Modifier.height(NikoSize.tap)
            .drawBehind {
                if (active) drawRect(ink, Offset(0f, size.height - NikoStroke.bold.toPx()), androidx.compose.ui.geometry.Size(size.width, NikoStroke.bold.toPx()))
            }
            .clickable(role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2),
    ) {
        Text(text, NikoType.bodySm.copy(fontWeight = NikoType.headline.fontWeight), if (active) c.ink else c.ink2, maxLines = 1)
        Text(count.toString(), NikoType.label, c.ink2, maxLines = 1)
    }
}

/** m-iconbtn m-iconbtn--tonal: an icon on surface-2, size-tap round. */
@Composable
internal fun TonalIconButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(NikoSize.tap).clip(CircleShape).background(if (pressed(source)) c.surface3 else c.surface2)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, c.ink, NikoSize.icon)
    }
}

/** m-radio: a ring, and the dot inside it when on. */
@Composable
internal fun Radio(on: Boolean) {
    val c = Niko.colors
    Box(Modifier.size(20.dp).border(NikoStroke.bold, if (on) c.ink else c.lineStrong, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(10.dp).background(c.ink, CircleShape))
    }
}
