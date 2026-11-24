package io.niko.ui.compose

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * m-snackbar as it shows, drawn alone (a snapshot): surface-3 with [text] in ink, [action] a ghost button at its end.
 * The text is a polite live region: a screen reader says it as the bar comes.
 */
@Composable
fun NikoSnackbarBar(text: String, action: String?, modifier: Modifier = Modifier, onAction: () -> Unit) {
    val c = Niko.colors
    Row(
        modifier.widthIn(max = 600.dp).fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface3)
            .padding(start = NikoSpace.s4, end = if (action != null) NikoSpace.s2 else NikoSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text, Modifier.weight(1f).padding(vertical = NikoSpace.s3).semantics { liveRegion = LiveRegionMode.Polite },
            style = NikoType.body.copy(color = c.ink), maxLines = 10, overflow = TextOverflow.Ellipsis,
        )
        if (action != null) {
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            Box(
                Modifier.padding(start = NikoSpace.s2).height(NikoSize.control).clip(CircleShape)
                    .background(if (pressed) c.surface2 else Color.Transparent)
                    .clickable(source, indication = null, role = Role.Button, onClick = onAction)
                    .padding(horizontal = NikoSpace.s4),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(action, maxLines = 1, style = NikoType.body.copy(fontWeight = NikoType.headline.fontWeight, color = c.ink))
            }
        }
    }
}

/**
 * A message of [NikoSnackbarHost], built as Material's snackbar was: an action, what follows its going, then [show].
 * Main thread.
 */
class NikoSnackbar internal constructor(private val host: NikoSnackbarHost, internal val text: String) {

    internal var action: String? = null
        private set
    internal var duration = LENGTH_LONG
        private set
    private var onAction: () -> Unit = {}
    private var onDismissed: (Boolean) -> Unit = {}

    /** A button at the bar's end: [action] on its press, which takes the bar away too. */
    fun setAction(text: CharSequence, action: () -> Unit) = apply {
        this.action = text.toString()
        onAction = action
    }

    fun setAction(@StringRes text: Int, action: () -> Unit) = setAction(host.activity.getString(text), action)

    /** How long it stays, in milliseconds: [LENGTH_SHORT] or [LENGTH_LONG] (as it is unless told). */
    fun setDuration(duration: Int) = apply { this.duration = duration }

    /**
     * [callback] once the bar has gone, however it went: true after its action, false after its time, a swipe, a
     * newer message in its place or [dismiss].
     */
    fun onDismissed(callback: (byAction: Boolean) -> Unit) = apply { onDismissed = callback }

    fun show() = host.show(this)

    /** Takes the bar away if it is the one shown. */
    fun dismiss() = host.dismiss(this, byAction = false)

    internal fun act() {
        onAction()
        host.dismiss(this, byAction = true)
    }

    internal fun dismissed(byAction: Boolean) = onDismissed(byAction)

    companion object {
        /** Material's durations. */
        const val LENGTH_SHORT = 1500
        const val LENGTH_LONG = 2750
    }
}

/**
 * An activity's snackbars in Niko's design: one bar at a time at the window's bottom, over the navigation bar and the
 * keyboard, drawn by Compose in a view laid over the activity's content (over its sheets too) once the first message
 * shows. A newer message takes the place of the one shown; a message goes after its time (longer when the
 * accessibility services ask for it), by a swipe to a side or by its action. Main thread.
 */
class NikoSnackbarHost(internal val activity: ComponentActivity) {

    private var current by mutableStateOf<NikoSnackbar?>(null)
    private var overlay: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { current?.let { dismiss(it, byAction = false) } }

    fun make(text: CharSequence) = NikoSnackbar(this, text.toString())

    internal fun show(bar: NikoSnackbar) {
        current?.let { dismiss(it, byAction = false) }
        attach()
        current = bar
        handler.postDelayed(timeout, stays(bar).toLong())
    }

    internal fun dismiss(bar: NikoSnackbar, byAction: Boolean) {
        if (current !== bar) return
        handler.removeCallbacks(timeout)
        current = null
        bar.dismissed(byAction)
    }

    /** The bar's time: its own, or the longer one the accessibility services recommend for what it holds. */
    private fun stays(bar: NikoSnackbar): Int {
        val manager = activity.getSystemService(AccessibilityManager::class.java) ?: return bar.duration
        var holds = AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT
        if (bar.action != null) holds = holds or AccessibilityManager.FLAG_CONTENT_CONTROLS
        return manager.getRecommendedTimeoutMillis(bar.duration, holds)
    }

    /**
     * The view over the activity's content, its last child (drawn over the others). setContentView takes the
     * content's children away: the view goes back on the next message.
     */
    private fun attach() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        // in a frame: ComponentActivity.setContent takes the content's first child for its own when that is a ComposeView
        val view = overlay ?: FrameLayout(activity).apply {
            addView(ComposeView(activity).apply { setContent { NikoTheme { Shown() } } })
        }.also { overlay = it }
        if (view.parent !== content) {
            (view.parent as? ViewGroup)?.removeView(view)
            content.addView(
                view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
            )
        } else if (content.getChildAt(content.childCount - 1) !== view) {
            view.bringToFront()
        }
    }

    @Composable
    private fun Shown() {
        val bar = current ?: return
        // a new bar starts where it lies, not where the last one was dragged to
        key(bar) {
            var dragged by remember { mutableFloatStateOf(0f) }
            var width by remember { mutableIntStateOf(0) }
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = NikoSpace.s4, vertical = NikoSpace.s3),
                contentAlignment = Alignment.Center,
            ) {
                NikoSnackbarBar(
                    bar.text, bar.action,
                    Modifier.onSizeChanged { width = it.width }.offset { IntOffset(dragged.roundToInt(), 0) }
                        .draggable(
                            rememberDraggableState { dragged += it }, Orientation.Horizontal,
                            // far enough to a side takes it away, less puts it back
                            onDragStopped = { if (abs(dragged) > width / 3f) dismiss(bar, byAction = false) else dragged = 0f },
                        )
                        .semantics {
                            dismiss {
                                dismiss(bar, byAction = false)
                                true
                            }
                        },
                    onAction = bar::act,
                )
            }
        }
    }
}
