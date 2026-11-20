package io.niko.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.ktx.setField

class MTUPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.dropdownPreferenceStyle
) : ListPreference(context, attrs, defStyle, 0) {

    init {
        setSummaryProvider {
            value.toString()
        }
        dialogLayoutResource = R.layout.layout_mtu_help
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val itemView: View = holder.itemView
        itemView.setOnLongClickListener {
            val view = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                setText(preferenceDataStore?.getString(key, "") ?: "")
            }

            MaterialAlertDialogBuilder(context).setTitle("MTU")
                .setField(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // an empty field or a number too long for an Int was a crash
                    val mtu = view.text.toString().toIntOrNull() ?: return@setPositiveButton
                    if (mtu < 1000 || mtu > 10000) return@setPositiveButton
                    value = mtu.toString()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

}
