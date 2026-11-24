package io.niko.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.ui.ThemedActivity
import java.nio.charset.StandardCharsets

/** The code's side where the screen leaves room for it: what the card's width (360) leaves inside its padding. */
private val QR_SIDE = 312.dp

/** The caption's two lines and the space over them, left under the code on a low screen. */
private val QR_CAPTION = 64.dp

/**
 * [text] as a QR code's modules, the quiet zone (4 modules) around them; UTF-8 for what ISO 8859-1 cannot write.
 * Throws [WriterException] for a text no code holds.
 */
fun qrMatrix(text: String): BitMatrix {
    val hints = HashMap<EncodeHintType, Any>()
    if (!StandardCharsets.ISO_8859_1.newEncoder().canEncode(text)) hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8.name()
    return MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
}

/**
 * The card of the QR dialog, drawn alone (a snapshot): the code of [side], dark modules on white in both themes (a
 * scanner reads it in the dark theme too), and [caption] under it.
 */
@Composable
fun NikoQrCard(matrix: BitMatrix, caption: String, side: Dp = QR_SIDE) {
    val c = Niko.colors
    val label = stringResource(R.string.share_qr_nfc)
    Column(
        Modifier.clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1)
            .verticalScroll(rememberScrollState()).padding(NikoSpace.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
    ) {
        Canvas(Modifier.size(side).clip(RoundedCornerShape(NikoRadius.sm)).background(Color.White).semantics { contentDescription = label }) {
            val cell = size.width / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                // a hair over the cell, so that no seam shows between neighbours
                if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
        if (caption.isNotEmpty()) BasicText(
            caption, Modifier.width(side),
            style = NikoType.body.copy(color = c.ink, textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [text] as a QR code in a Niko dialog over [activity], [name] under the code; back and a tap outside close it. A
 * text no code holds is said in a snackbar instead.
 */
fun showQrDialog(activity: ThemedActivity, text: String, name: String) {
    val matrix = try {
        qrMatrix(text)
    } catch (e: WriterException) {
        Logs.w(e)
        activity.snackbar(e.readableMessage).show()
        return
    }
    nikoDialogWindow(activity) {
        // as large as the screen lets it be: its width on a phone held upright, its height on one held sideways
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val room = minOf(maxWidth, maxHeight - QR_CAPTION) - NikoSpace.s6 * 2
            NikoQrCard(matrix, name, room.coerceIn(120.dp, QR_SIDE))
        }
    }.show()
}
