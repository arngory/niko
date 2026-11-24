package io.niko.ui.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.launchCustomTab
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListRow
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.settings.IconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A line of the list as it shows: its section's title (null: above the groups), its name and detail. */
class LicenseRow(val section: Int, val index: Int, val title: Int?, val name: String, val detail: String)

/**
 * Лицензии (D-SET-34): what the APK carries and under which licenses, by section, a line each; [rows] null while read.
 * The groups are drawn row by row, the list being lazy (Go's modules are hundreds).
 */
@Composable
fun LicensesScreen(rows: List<LicenseRow>?, onBack: () -> Unit, onOpen: (LicenseRow) -> Unit) {
    val c = Niko.colors
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        Bar(stringResource(R.string.licenses), null, onBack)
        LazyColumn(
            Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
        ) {
            val all = rows.orEmpty()
            itemsIndexed(all, key = { _, r -> "${r.section}/${r.index}" }) { i, row ->
                val first = i == 0 || all[i - 1].section != row.section
                val last = i == all.size - 1 || all[i + 1].section != row.section
                if (first && row.title != null) {
                    Text(
                        stringResource(row.title).uppercase(), NikoType.label, c.ink2,
                        Modifier.padding(start = NikoSpace.s4, end = NikoSpace.s4, top = if (i > 0) NikoSpace.s6 else 0.dp, bottom = NikoSpace.s2),
                    )
                }
                val r = NikoRadius.md
                val shape = RoundedCornerShape(
                    topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
                    bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
                )
                ListRow(
                    row.name,
                    Modifier.clip(shape).background(c.surface1),
                    sub = row.detail.takeIf { it.isNotBlank() }?.let { { Sub(it) } },
                    divider = !first,
                    titleLines = 2,
                    onClick = { onOpen(row) },
                )
            }
        }
    }
}

/**
 * A license's text: its [pieces] one after another (a long notice in parts, laid out as they scroll in), selectable;
 * [site] the «Сайт» button in the bar where the line has an address.
 */
@Composable
fun LicenseTextScreen(title: String, detail: String?, pieces: List<String>, onBack: () -> Unit, site: (() -> Unit)?) {
    val c = Niko.colors
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        Bar(title, detail, onBack) {
            if (site != null) IconButton(R.drawable.ic_l_link, stringResource(R.string.licenses_site), onClick = site)
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
            ) {
                itemsIndexed(pieces) { _, piece -> Text(piece, NikoType.code.copy(fontSize = NikoType.caption.fontSize), c.ink) }
            }
        }
    }
}

/** m-topbar: the way back, the title and under it [detail], then [content] (its buttons). */
@Composable
private fun Bar(title: String, detail: String?, onBack: () -> Unit, content: @Composable () -> Unit = {}) {
    val c = Niko.colors
    Row(
        Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
    ) {
        IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = NikoSpace.s2)) {
            Text(title, NikoType.headline, c.ink, maxLines = 1)
            if (detail != null) Text(detail, NikoType.caption, c.ink2, maxLines = 1)
        }
        content()
    }
}

/**
 * Лицензии on Compose, in place of the RecyclerView list: [Licenses]' sections read off the main thread; a line opens
 * its text in [LicenseTextActivity].
 */
class LicensesActivity : ThemedActivity() {

    private val rows = mutableStateOf<List<LicenseRow>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            io.niko.ui.compose.NikoTheme {
                LicensesScreen(rows.value, ::finish) { row ->
                    startActivity(
                        Intent(this, LicenseTextActivity::class.java)
                            .putExtra(LicenseTextActivity.SECTION, row.section)
                            .putExtra(LicenseTextActivity.ENTRY, row.index),
                    )
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val sections = try {
                Licenses.sections(this@LicensesActivity)
            } catch (e: Exception) {
                Logs.w(e)
                emptyList()
            }
            val all = sections.flatMapIndexed { s, section ->
                section.entries.mapIndexed { i, entry -> LicenseRow(s, i, section.title, entry.name, entry.detail) }
            }
            withContext(Dispatchers.Main) { rows.value = all }
        }
    }
}

/**
 * A line of [LicensesActivity] opened, or a text of the assets by [ofAsset] (Niko's LICENSE from О программе): its
 * texts one after another, each under its name when there are several; its site from the bar.
 */
class LicenseTextActivity : ThemedActivity() {

    companion object {
        const val SECTION = "section"
        const val ENTRY = "entry"
        private const val ASSET = "asset"
        private const val TITLE = "title"

        /** The text of the asset at [path], under [title]. */
        fun ofAsset(context: Context, path: String, title: String) =
            Intent(context, LicenseTextActivity::class.java).putExtra(ASSET, path).putExtra(TITLE, title)
    }

    private class Shown(val title: String, val detail: String?, val url: String?, val pieces: List<String>)

    private val shown = mutableStateOf<Shown?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            io.niko.ui.compose.NikoTheme {
                val s = shown.value
                LicenseTextScreen(
                    s?.title.orEmpty(), s?.detail, s?.pieces.orEmpty(), ::finish,
                    s?.url?.let { url -> { launchCustomTab(url) } },
                )
            }
        }
        val asset = intent.getStringExtra(ASSET)
        val section = intent.getIntExtra(SECTION, -1)
        val index = intent.getIntExtra(ENTRY, -1)
        lifecycleScope.launch(Dispatchers.IO) {
            val read = try {
                if (asset != null) {
                    val text = assets.open(asset).bufferedReader().use { it.readText() }
                    Shown(intent.getStringExtra(TITLE) ?: asset, null, null, Licenses.chunks(text))
                } else {
                    val entry = Licenses.sections(this@LicenseTextActivity).getOrNull(section)?.entries?.getOrNull(index)
                    val files = entry?.files?.invoke()
                    if (entry == null || files == null) null else Shown(
                        entry.name, entry.detail.ifBlank { null }, entry.url,
                        // several files: each under its name; in pieces, laid out as they scroll in (Licenses.chunks)
                        Licenses.chunks(
                            if (files.size == 1) files[0].second else files.joinToString("\n\n") { (name, text) ->
                                "── $name ──\n\n${text.trimEnd()}"
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                Logs.w(e)
                null
            }
            withContext(Dispatchers.Main) {
                if (read == null) finish() else shown.value = read
            }
        }
    }
}
