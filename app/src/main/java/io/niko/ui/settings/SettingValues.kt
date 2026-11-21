package io.niko.ui.settings

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
