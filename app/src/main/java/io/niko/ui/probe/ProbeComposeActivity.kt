package io.niko.ui.probe

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.niko.R

/** The UI probe's Compose half (see [ProbeContent]): the same screen as res/layout/probe_simple.xml. */
class ProbeComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SagerNet_Niko)
        super.onCreate(savedInstanceState)
        setContent { ProbeSimple() }
    }
}

private val Sans = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)
private val Mono = FontFamily(Font(R.font.geist_mono, FontWeight.Normal), Font(R.font.geist_mono_medium, FontWeight.Medium))

/** The design system's colours, from the resources shared/tokens.json generates (values and values-night). */
private class Palette(
    val surface0: Color, val surface1: Color, val surface2: Color, val ink: Color, val ink2: Color,
    val onInk: Color, val lineStrong: Color,
)

@Composable
private fun palette() = Palette(
    surface0 = colorResource(R.color.niko_surface_0),
    surface1 = colorResource(R.color.niko_surface_1),
    surface2 = colorResource(R.color.niko_surface_2),
    ink = colorResource(R.color.niko_ink),
    ink2 = colorResource(R.color.niko_ink_2),
    onInk = colorResource(R.color.niko_on_ink),
    lineStrong = colorResource(R.color.niko_line_strong),
)

private fun sans(size: Int, line: Int, weight: FontWeight, color: Color, tracking: Double = 0.0) = TextStyle(
    fontFamily = Sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em, color = color,
)

private fun mono(color: Color) = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp, color = color)

@Composable
private fun ProbeSimple() {
    val p = palette()
    Box(Modifier.fillMaxSize().background(p.surface0).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(p)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Connect(p, Modifier.align(Alignment.TopCenter).padding(top = 152.dp))
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 376.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(ProbeContent.STATUS, style = sans(24, 30, FontWeight.SemiBold, p.ink, -0.02))
                    BasicText(ProbeContent.DETAIL, style = mono(p.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 138.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Tile(p, R.drawable.ic_l_plug, ProbeContent.CONNECTION_LABEL, ProbeContent.CONNECTION_VALUE, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(p, R.drawable.ic_l_shield, ProbeContent.MODE_LABEL, ProbeContent.MODE_VALUE, Modifier.weight(1f))
                        Tile(p, R.drawable.ic_l_route, ProbeContent.ROUTE_LABEL, ProbeContent.ROUTE_VALUE, Modifier.weight(1f))
                    }
                }
            }
        }
        Sheet(p, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopBar(p: Palette) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(ProbeContent.WORDMARK, Modifier.weight(1f), style = sans(24, 30, FontWeight.SemiBold, p.ink, -0.04))
        Row(
            Modifier.background(p.surface2, CircleShape).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Segment(ProbeContent.SIMPLE, selected = true, p)
            Segment(ProbeContent.ADVANCED, selected = false, p)
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, p: Palette) {
    Box(
        Modifier.width(120.dp).height(40.dp).clip(CircleShape)
            .background(if (selected) p.ink else Color.Transparent).clickable(role = Role.RadioButton) { },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = sans(14, 20, FontWeight.SemiBold, if (selected) p.onInk else p.ink2))
    }
}

@Composable
private fun Connect(p: Palette, modifier: Modifier = Modifier) {
    Box(modifier.size(192.dp).border(1.dp, p.lineStrong, CircleShape), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(160.dp).clip(CircleShape).background(p.ink)
                .semantics { contentDescription = ProbeContent.DISCONNECT }
                .clickable(role = Role.Button) { },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_l_power), null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(p.onInk))
        }
    }
}

@Composable
private fun Tile(p: Palette, icon: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.heightIn(min = 104.dp).clip(RoundedCornerShape(16.dp)).background(p.surface1)
            .clickable(role = Role.Button) { }.padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Image(painterResource(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(p.ink2))
            Image(painterResource(R.drawable.ic_l_chevron_right), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(p.ink2))
        }
        Spacer(Modifier.height(16.dp))
        Column {
            BasicText(label, style = sans(12, 16, FontWeight.Medium, p.ink2, 0.01))
            BasicText(value, style = sans(17, 24, FontWeight.SemiBold, p.ink, -0.01), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Sheet(p: Palette, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(p.surface1, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(p.lineStrong, CircleShape))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { }
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(ProbeContent.NODE, style = sans(16, 24, FontWeight.SemiBold, p.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row {
                    BasicText(ProbeContent.NODE_UNTIL, style = sans(14, 20, FontWeight.Normal, p.ink2))
                    BasicText(ProbeContent.NODE_TRAFFIC, style = mono(p.ink2))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(ProbeContent.NODE_PING, style = mono(p.ink2))
                Image(painterResource(R.drawable.ic_l_chevrons_up_down), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(p.ink2))
            }
        }
    }
}
