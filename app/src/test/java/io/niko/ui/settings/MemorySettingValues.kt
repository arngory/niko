package io.niko.ui.settings

import androidx.compose.runtime.mutableStateMapOf

/** Values held in memory alone, with nothing stored: the screens' snapshots. */
class MemorySettingValues(
    flags: Map<String, Boolean> = emptyMap(),
    texts: Map<String, String> = emptyMap(),
) : SettingValues {
    private val flags = mutableStateMapOf<String, Boolean>().apply { putAll(flags) }
    private val texts = mutableStateMapOf<String, String>().apply { putAll(texts) }

    override fun flag(key: String) = flags[key] ?: false
    override fun setFlag(key: String, value: Boolean) {
        flags[key] = value
    }

    override fun text(key: String) = texts[key].orEmpty()
    override fun setText(key: String, value: String) {
        texts[key] = value
    }
}
