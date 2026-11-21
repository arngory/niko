package io.niko.ui.settings

import androidx.compose.runtime.mutableStateMapOf

/**
 * The values a settings screen shows and changes, by their keys in the settings store (Key.*): switches as flags,
 * fields as text. A screen reads them while it composes, so a change redraws what shows it.
 */
interface SettingValues {
    fun flag(key: String): Boolean
    fun setFlag(key: String, value: Boolean)
    fun text(key: String): String
    fun setText(key: String, value: String)
}

/** Values held in memory alone: the probe's screens and their snapshots, with nothing stored. */
class MemorySettingValues(flags: Map<String, Boolean> = emptyMap(), texts: Map<String, String> = emptyMap()) : SettingValues {
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
