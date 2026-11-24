package io.niko.widget

import io.niko.R
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoSnackbar

/**
 * @param activity ThemedActivity.
 * //@param view The view to find a parent from.
 * @param undo Callback for undoing removals.
 * @param commit Callback for committing removals.
 * @tparam T Item type.
 */
class UndoSnackbarManager<in T>(
    private val activity: ThemedActivity,
    private val callback: Interface<T>,
) {

    interface Interface<in T> {
        fun undo(actions: List<Pair<Int, T>>)
        fun commit(actions: List<Pair<Int, T>>)
    }

    private val recycleBin = ArrayList<Pair<Int, T>>()

    // the latest bar gone by anything but its action commits; an earlier one a newer removal replaced leaves its
    // items in the bin, for the newer bar to undo or commit with its own
    private fun removed(bar: NikoSnackbar, byAction: Boolean) {
        if (last === bar && !byAction) {
            callback.commit(recycleBin)
            recycleBin.clear()
            last = null
        }
    }

    private var last: NikoSnackbar? = null

    fun remove(items: Collection<Pair<Int, T>>) {
        recycleBin.addAll(items)
        val count = recycleBin.size
        activity.snackbar(activity.resources.getQuantityString(R.plurals.removed, count, count))
            .apply {
                onDismissed { byAction -> removed(this, byAction) }
                setAction(R.string.undo) {
                    callback.undo(recycleBin.reversed())
                    recycleBin.clear()
                }
                last = this
                show()
            }
    }

    fun remove(vararg items: Pair<Int, T>) = remove(items.toList())

    fun flush() = last?.dismiss()
}
