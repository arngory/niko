package io.niko.ui.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.niko.fmt.AbstractBean

// The profile editor's description of a type (P-Profile): its sections, each of fields, switches, choices and rows,
// bound to the bean's fields the way the old screens' PreferenceBinding and init/serialize did. Kotlin data for this
// editor only (docs/ui/platforms.md, variant А): ProfileScreen draws it, [ProfileSpec.load] and [ProfileSpec.save]
// move a bean into a ProfileForm and back.

/** A text the editor shows: a string resource, or a protocol's own term as it is ("MTU", "ALPN"). */
class Txt private constructor(@StringRes val res: Int, val raw: String?) {
    constructor(@StringRes res: Int) : this(res, null)
    constructor(raw: String) : this(0, raw)

    @Composable
    fun text(): String = raw ?: stringResource(res)
}

/** What a field takes: text, a port (five digits), a number (digits), a secret (dots until shown). */
enum class Input { TEXT, PORT, NUMBER, SECRET }

private val always: (ProfileForm) -> Boolean = { true }

/** One thing on the editor; [visible] decides from the form whether it is drawn (the old screens' isVisible). */
sealed class Item(val key: String, val visible: (ProfileForm) -> Boolean)

/**
 * m-field over the form's text [key]: [label] (or [labelOf] the form, a label that follows another choice), [mono]
 * in Geist Mono, [lines] a text of several lines, [help] a caption under it, [enabled] false dimmed and not typed in.
 */
class TextItem(
    key: String,
    val label: Txt,
    val input: Input = Input.TEXT,
    val mono: Boolean = false,
    val lines: Boolean = false,
    val help: Txt? = null,
    val placeholder: Txt? = null,
    val labelOf: ((ProfileForm) -> Txt)? = null,
    val enabled: (ProfileForm) -> Boolean = always,
    visible: (ProfileForm) -> Boolean = always,
) : Item(key, visible)

/** m-row with m-switch over the form's flag [key], [sub] under its title. */
class SwitchItem(key: String, val title: Txt, val sub: Txt? = null, visible: (ProfileForm) -> Boolean = always) : Item(key, visible)

/**
 * A choice of [options] (value as the form keeps it, its name) over the form's text [key]: m-seg when they are few
 * and short, else a row with the chosen one's name that opens a sheet ([sheet]).
 */
class ChoiceItem(
    key: String,
    val title: Txt,
    val options: List<Pair<String, Txt>>,
    val sheet: Boolean,
    val help: Txt? = null,
    val enabled: (ProfileForm) -> Boolean = always,
    visible: (ProfileForm) -> Boolean = always,
) : Item(key, visible)

/** m-fields-row: two fields side by side, the second narrow (a port beside its server) or, [equal], as wide. */
class PairItem(val first: TextItem, val second: TextItem, val equal: Boolean = false, visible: (ProfileForm) -> Boolean = always) : Item(first.key, visible)

/** A row that opens the JSON editor on the form's text [key] (the custom config's own), «N строк» at its end. */
class JsonItem(key: String, val title: Txt, visible: (ProfileForm) -> Boolean = always) : Item(key, visible)

/** The chain's links (D-PROFILE-04): their order, a press replaces one, × removes it, a row adds one. */
class ChainItem : Item("chain", always)

/**
 * m-section: an m-overline [title] (none: the editor's top, the name and the server) over its items; [page] the key
 * of its own page, opened from a row with [summary] at its end, instead of drawn in place.
 */
class Section(
    val title: Txt?,
    val items: List<Item>,
    val page: String? = null,
    val summary: ((ProfileForm) -> Txt?)? = null,
    val visible: (ProfileForm) -> Boolean = always,
)

/** One field's way between the bean and the form, or a type's own step (a plugin's two halves into one string). */
internal class Bind(val load: (AbstractBean, FormValues) -> Unit, val save: (AbstractBean, ProfileForm) -> Unit)

/** The form as it is read, before it is made a ProfileForm. */
internal class FormValues {
    val text = mutableMapOf<String, String>()
    val flags = mutableMapOf<String, Boolean>()
}

/**
 * A type's editor: [title] its name in the top bar, [create] a new bean of it (the old createEntity), [sections] what
 * the editor draws, [tweaks] whether it has «Тонкая настройка» (a chain does not: its links carry their own).
 */
class ProfileSpec internal constructor(
    val title: Txt,
    val create: () -> AbstractBean,
    val sections: List<Section>,
    private val binds: List<Bind>,
    val tweaks: Boolean,
) {
    /** The bean into a form, as the old init() put it into DataStore, and the settings every type shares. */
    fun load(bean: AbstractBean): ProfileForm {
        val values = FormValues()
        binds.forEach { it.load(bean, values) }
        return ProfileForm(
            text = values.text,
            flags = values.flags,
            tweaks = Tweaks.of(bean),
            outboundJson = bean.customOutboundJson.orEmpty(),
            configJson = bean.customConfigJson.orEmpty(),
            chain = (bean as? io.niko.fmt.internal.ChainBean)?.proxies.orEmpty(),
        )
    }

    /**
     * The form into [bean], as the old serialize() took it from DataStore, the custom JSON, then the connection
     * tweaks ([original] the form as read: tweaks left as they were are kept as they were).
     */
    fun save(form: ProfileForm, bean: AbstractBean, original: ProfileForm) {
        binds.forEach { it.save(bean, form) }
        bean.customOutboundJson = form.outboundJson
        bean.customConfigJson = form.configJson
        form.tweaks.storedAgainst(original.tweaks).applyTo(bean)
    }

    /** Every item, for a lookup by key (an error's field, its page). */
    val items: List<Item> get() = sections.flatMap { s -> s.items.flatMap { if (it is PairItem) listOf(it, it.second) else listOf(it) } }
}

/** Builds a [ProfileSpec] of beans [B]: sections of items, each bound to the bean's field by its getter and setter. */
internal class SpecBuilder<B : AbstractBean>(private val title: Txt, private val create: () -> B) {
    private val sections = mutableListOf<Section>()
    private val binds = mutableListOf<Bind>()
    private var items = mutableListOf<Item>()
    var tweaks = true

    @Suppress("UNCHECKED_CAST")
    private fun bind(load: (B, FormValues) -> Unit, save: (B, ProfileForm) -> Unit) {
        binds += Bind({ b, v -> load(b as B, v) }, { b, f -> save(b as B, f) })
    }

    fun build() = ProfileSpec(title, create, sections.toList(), binds.toList(), tweaks)

    fun section(
        title: Txt?,
        page: String? = null,
        summary: ((ProfileForm) -> Txt?)? = null,
        visible: (ProfileForm) -> Boolean = always,
        content: SpecBuilder<B>.() -> Unit,
    ) {
        val outer = items
        items = mutableListOf()
        content()
        sections += Section(title, items.toList(), page, summary, visible)
        items = outer
    }

    /** A type's own step of load and save, in its place among the fields' (after those above it). */
    fun step(load: (B, FormValues) -> Unit = { _, _ -> }, save: (B, ProfileForm) -> Unit = { _, _ -> }) = bind(load, save)

    /** A text field over a String of the bean ([get] null: the type's own [step] moves it). */
    fun text(
        key: String,
        label: Txt,
        get: (B.() -> String?)? = null,
        set: (B.(String) -> Unit)? = null,
        input: Input = Input.TEXT,
        mono: Boolean = false,
        lines: Boolean = false,
        help: Txt? = null,
        placeholder: Txt? = null,
        labelOf: ((ProfileForm) -> Txt)? = null,
        enabled: (ProfileForm) -> Boolean = always,
        visible: (ProfileForm) -> Boolean = always,
    ): TextItem {
        if (get != null) bind({ b, v -> v.text[key] = b.get().orEmpty() }, { b, f -> set?.invoke(b, f.text(key)) })
        return TextItem(key, label, input, mono, lines, help, placeholder, labelOf, enabled, visible).also { items += it }
    }

    /**
     * A number typed as text over an Int of the bean: shown as it is stored, read back as the old screens read their
     * TextToInt and stringToInt keys, [default] when it is not a number.
     */
    fun int(
        key: String,
        label: Txt,
        get: B.() -> Int?,
        set: B.(Int) -> Unit,
        default: Int = 0,
        input: Input = Input.NUMBER,
        help: Txt? = null,
        placeholder: Txt? = null,
        visible: (ProfileForm) -> Boolean = always,
    ): TextItem {
        bind({ b, v -> v.text[key] = b.get()?.toString().orEmpty() }, { b, f -> b.set(f.text(key).toIntOrNull() ?: default) })
        return TextItem(key, label, input, mono = true, help = help, placeholder = placeholder, visible = visible).also { items += it }
    }

    /** A switch over a Boolean of the bean. */
    fun flag(key: String, title: Txt, get: B.() -> Boolean?, set: B.(Boolean) -> Unit, sub: Txt? = null, visible: (ProfileForm) -> Boolean = always): SwitchItem {
        bind({ b, v -> v.flags[key] = b.get() == true }, { b, f -> b.set(f.flag(key)) })
        return SwitchItem(key, title, sub, visible).also { items += it }
    }

    /** A choice over a String of the bean ([get] null: the type's own [step] moves it). */
    fun choice(
        key: String,
        title: Txt,
        options: List<Pair<String, Txt>>,
        get: (B.() -> String?)? = null,
        set: (B.(String) -> Unit)? = null,
        sheet: Boolean = options.size > 3,
        help: Txt? = null,
        enabled: (ProfileForm) -> Boolean = always,
        visible: (ProfileForm) -> Boolean = always,
    ): ChoiceItem {
        if (get != null) bind({ b, v -> v.text[key] = b.get().orEmpty() }, { b, f -> set?.invoke(b, f.text(key)) })
        return ChoiceItem(key, title, options, sheet, help, enabled, visible).also { items += it }
    }

    /** A choice over an Int of the bean, its values as the old menus kept them ("0", "1", "2"). */
    fun intChoice(
        key: String,
        title: Txt,
        options: List<Pair<Int, Txt>>,
        get: B.() -> Int?,
        set: B.(Int) -> Unit,
        default: Int = 0,
        sheet: Boolean = options.size > 3,
        visible: (ProfileForm) -> Boolean = always,
    ): ChoiceItem {
        bind({ b, v -> v.text[key] = b.get()?.toString().orEmpty() }, { b, f -> b.set(f.text(key).toIntOrNull() ?: default) })
        return ChoiceItem(key, title, options.map { (v, t) -> v.toString() to t }, sheet, visible = visible).also { items += it }
    }

    /** Two fields side by side: [content] adds them, the second narrow unless [equal]. */
    fun pair(equal: Boolean = false, visible: (ProfileForm) -> Boolean = always, content: SpecBuilder<B>.() -> Unit) {
        val outer = items
        items = mutableListOf()
        content()
        val (first, second) = items.map { it as TextItem }
        items = outer
        items += PairItem(first, second, equal, visible)
    }

    fun json(key: String, title: Txt) {
        items += JsonItem(key, title)
    }

    fun chain() {
        items += ChainItem()
    }
}
