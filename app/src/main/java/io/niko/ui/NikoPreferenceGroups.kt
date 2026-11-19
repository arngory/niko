package io.niko.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import io.niko.R

/**
 * The settings' rows in groups (reskin step 2, D-SET; on where the theme sets nikoGroupedPreferences): the rows between
 * two group titles sit in one surface-1 container of radius-md, the screen's side margin (space-5) around it and a
 * hairline (line) between its rows; a group title stays on the screen's surface-0. The colour themes keep the plain
 * list.
 */
object NikoPreferenceGroups {

    fun install(fragment: PreferenceFragmentCompat) {
        val list = fragment.listView ?: return
        val attrs = list.context.obtainStyledAttributes(intArrayOf(R.attr.nikoGroupedPreferences))
        val grouped = attrs.getBoolean(0, false)
        attrs.recycle()
        if (!grouped) return
        fragment.setDivider(null)
        decorate(list) { fragment.preferenceScreen }
    }

    /** Draws [list]'s rows of [screen] in groups (the snapshots draw a screen without a fragment). */
    fun decorate(list: RecyclerView, screen: () -> PreferenceGroup?) =
        list.addItemDecoration(Decoration(list.context, screen))

    /**
     * The preferences in the list's order: the visible ones of [group], a category's own children right after it (on
     * the same screen), a nested screen as one row. PreferenceGroupAdapter lays them out so.
     */
    fun rows(group: PreferenceGroup?, out: MutableList<Preference> = mutableListOf()): List<Preference> {
        if (group == null) return out
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            if (!preference.isVisible) continue
            out += preference
            if (preference is PreferenceCategory) rows(preference, out)
        }
        return out
    }

    private class Decoration(context: Context, private val screen: () -> PreferenceGroup?) :
        RecyclerView.ItemDecoration() {
        private val side = context.resources.getDimensionPixelSize(R.dimen.niko_space_5)
        private val gap = context.resources.getDimensionPixelSize(R.dimen.niko_space_2)
        private val inset = context.resources.getDimension(R.dimen.niko_space_4)
        private val radius = context.resources.getDimension(R.dimen.niko_radius_md)
        private val hairline = context.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.niko_surface_1)
        }
        private val line = Paint().apply {
            color = ContextCompat.getColor(context, R.color.niko_line)
            strokeWidth = hairline
        }
        private val path = Path()
        private val rect = RectF()

        // a row's place in its group: whether a group title (or the list's edge) comes before it, after it
        private fun place(rows: List<Preference>, position: Int): Pair<Boolean, Boolean>? {
            val row = rows.getOrNull(position) ?: return null
            if (row is PreferenceCategory) return null
            val first = rows.getOrNull(position - 1).let { it == null || it is PreferenceCategory }
            val last = rows.getOrNull(position + 1).let { it == null || it is PreferenceCategory }
            return first to last
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            outRect.left = side
            outRect.right = side
            // a group's end: a little air before the next group's title
            if (place(rows(screen()), position)?.second == true) outRect.bottom = gap
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val rows = rows(screen())
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) continue
                val (first, last) = place(rows, position) ?: continue
                val top = child.top + child.translationY
                val bottom = child.bottom + child.translationY
                rect.set(child.left.toFloat(), top, child.right.toFloat(), bottom)
                val upper = if (first) radius else 0f
                val lower = if (last) radius else 0f
                path.reset()
                path.addRoundRect(
                    rect, floatArrayOf(upper, upper, upper, upper, lower, lower, lower, lower), Path.Direction.CW
                )
                canvas.drawPath(path, fill)
                if (!last) {
                    val y = bottom - hairline / 2
                    canvas.drawLine(rect.left + inset, y, rect.right - inset, y, line)
                }
            }
        }
    }
}
