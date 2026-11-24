package io.niko.ui.add

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.database.ProxyEntity
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Sheet
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.home.pressed
import io.niko.ui.route.PrimaryButton
import io.niko.ui.settings.IconButton

/** The camera on the screen: not yet allowed or refused, [ON] its picture, [DENIED] the user said no. */
enum class AddCamera { WAITING, ON, DENIED }

/**
 * A profile made by hand (D-PROFILE-01): its ProxyEntity [type] (VLESS is VMess's with [vless]) and its name, a
 * string or, for a name no language changes, [plain].
 */
enum class AddManual(val type: Int, @StringRes val title: Int, val plain: String? = null, val vless: Boolean = false) {
    SOCKS(ProxyEntity.TYPE_SOCKS, R.string.action_socks),
    HTTP(ProxyEntity.TYPE_HTTP, R.string.action_http),
    SHADOWSOCKS(ProxyEntity.TYPE_SS, R.string.action_shadowsocks),
    VMESS(ProxyEntity.TYPE_VMESS, R.string.action_vmess),
    VLESS(ProxyEntity.TYPE_VMESS, 0, plain = "VLESS", vless = true),
    TROJAN(ProxyEntity.TYPE_TROJAN, R.string.action_trojan),
    TROJAN_GO(ProxyEntity.TYPE_TROJAN_GO, R.string.action_trojan_go),
    MIERU(ProxyEntity.TYPE_MIERU, R.string.action_mieru),
    NAIVE(ProxyEntity.TYPE_NAIVE, R.string.action_naive),
    HYSTERIA(ProxyEntity.TYPE_HYSTERIA, R.string.action_hysteria),
    TUIC(ProxyEntity.TYPE_TUIC, R.string.action_tuic),
    SHADOWTLS(ProxyEntity.TYPE_SHADOWTLS, R.string.action_shadowtls),
    ANYTLS(ProxyEntity.TYPE_ANYTLS, R.string.action_anytls),
    SSH(ProxyEntity.TYPE_SSH, R.string.action_ssh),
    WIREGUARD(ProxyEntity.TYPE_WG, R.string.action_wireguard),
    CONFIG(ProxyEntity.TYPE_CONFIG, R.string.custom_config),
    CHAIN(ProxyEntity.TYPE_CHAIN, R.string.proxy_chain),
}

/**
 * Добавить (M-ADD-01, M-ADD-02): the [camera] and its [torch]; [busy] an import running (the rows wait for it);
 * [manual] the sheet of types open.
 */
data class AddState(
    val camera: AddCamera = AddCamera.WAITING,
    val torch: Boolean = false,
    val busy: Boolean = false,
    val manual: Boolean = false,
)

/** What the add screen asks of its activity. */
interface AddActions {
    fun back()
    fun torch()
    /** the camera asked for again, or the app's settings where the system no longer asks */
    fun allow()
    /** links, an invite or a subscription from the clipboard */
    fun clipboard()
    /** QR codes in pictures */
    fun qrFile()
    /** a file: links, JSON, WireGuard's .zip, a device package */
    fun file()
    fun manual(open: Boolean)
    fun create(kind: AddManual)
}

/**
 * The page: the viewfinder ([content] the camera's picture under its corners and the torch), or why there is none;
 * then the ways that need no camera.
 */
@Composable
fun AddScreen(state: AddState, actions: AddActions, content: @Composable () -> Unit) {
    val c = Niko.colors
    Box(Modifier.fillMaxSize().background(c.surface0)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
            ) {
                IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = actions::back)
                Text(stringResource(R.string.add_title), NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
            }
            Column(
                Modifier.weight(1f).padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
            ) {
                val shape = RoundedCornerShape(NikoRadius.md)
                // the viewfinder takes what the rows leave, and no less than a code needs
                Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 200.dp).clip(shape)) {
                    when (state.camera) {
                        AddCamera.DENIED -> Denied(actions::allow)
                        else -> {
                            Box(Modifier.fillMaxSize().background(c.surface3)) { if (state.camera == AddCamera.ON) content() }
                            Corners()
                            if (state.camera == AddCamera.ON) Torch(state.torch, actions::torch, Modifier.align(Alignment.BottomEnd).padding(NikoSpace.s3))
                        }
                    }
                }
                ListGroup {
                    val go = !state.busy
                    ListRow(
                        stringResource(R.string.add_clipboard), lead = { Icon(R.drawable.ic_l_clipboard, c.ink2) },
                        onClick = if (go) actions::clipboard else null,
                    )
                    ListRow(
                        stringResource(R.string.add_qr_file), divider = true, lead = { Icon(R.drawable.ic_l_image, c.ink2) },
                        onClick = if (go) actions::qrFile else null,
                    )
                    ListRow(
                        stringResource(R.string.add_file), divider = true, lead = { Icon(R.drawable.ic_l_file_up, c.ink2) },
                        onClick = if (go) actions::file else null,
                    )
                    ListRow(
                        stringResource(R.string.add_profile_methods_manual_settings), divider = true,
                        lead = { Icon(R.drawable.ic_l_pencil, c.ink2) }, trail = { Trail(null) },
                        onClick = if (go) ({ actions.manual(true) }) else null,
                    )
                }
            }
        }
        if (state.manual) ManualSheet(actions)
    }
}

/** The camera refused: what it is for, that the rest works without it, and the way to allow it. */
@Composable
private fun Denied(onAllow: () -> Unit) {
    val c = Niko.colors
    Column(
        Modifier.fillMaxSize().background(c.surface1).padding(NikoSpace.s6),
        verticalArrangement = Arrangement.spacedBy(NikoSpace.s4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(R.drawable.ic_l_triangle_alert, c.ink2, 32.dp)
        Text(stringResource(R.string.add_camera_denied), NikoType.headline.copy(textAlign = TextAlign.Center), c.ink)
        Text(stringResource(R.string.add_camera_denied_sub), NikoType.bodySm.copy(textAlign = TextAlign.Center), c.ink2)
        PrimaryButton(stringResource(R.string.add_camera_allow), onAllow)
    }
}

/** The viewfinder's four corners, 220 across at most, in ink over the picture. */
@Composable
private fun Corners() {
    val ink = Niko.colors.ink
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(NikoSpace.s6).sizeIn(maxWidth = 220.dp, maxHeight = 220.dp).aspectRatio(1f).drawBehind {
                val arm = 36.dp.toPx()
                val r = 12.dp.toPx()
                val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                val (w, h) = size.width to size.height
                // each corner: an arm, the round turn, an arm
                for ((x, y, dx, dy) in listOf(listOf(0f, 0f, 1f, 1f), listOf(w, 0f, -1f, 1f), listOf(0f, h, 1f, -1f), listOf(w, h, -1f, -1f))) {
                    val path = Path().apply {
                        moveTo(x, y + dy * arm)
                        lineTo(x, y + dy * r)
                        quadraticTo(x, y, x + dx * r, y)
                        lineTo(x + dx * arm, y)
                    }
                    drawPath(path, ink, style = stroke)
                }
            },
        )
    }
}

/** m-iconbtn--tonal over the picture: the torch, ink when it is on. */
@Composable
private fun Torch(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    val label = stringResource(R.string.add_torch)
    val state = stringResource(if (on) R.string.add_torch_on else R.string.add_torch_off)
    Box(
        modifier.requiredSize(NikoSize.tap).clip(CircleShape)
            .background(if (on) c.ink else if (pressed(source)) c.surface3 else c.surface2)
            .clickable(source, indication = null, role = Role.Switch, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(R.drawable.ic_l_flashlight, if (on) c.onInk else c.ink, NikoSize.icon)
    }
}

/** Вручную: the kinds of profile the editor makes, a row each. */
@Composable
private fun ManualSheet(actions: AddActions) {
    val c = Niko.colors
    val close = { actions.manual(false) }
    BackHandler(onBack = close)
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(
            Modifier.fillMaxHeight(0.8f).clickable(remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.add_manual_title), NikoType.headline, c.ink,
                Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2),
            )
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = NikoSpace.s6)) {
                AddManual.entries.forEachIndexed { i, kind ->
                    ListRow(
                        kind.plain ?: stringResource(kind.title), side = NikoSpace.s5, divider = i > 0,
                        onClick = { actions.create(kind) },
                    )
                }
            }
        }
    }
}
