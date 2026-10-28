/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-14.
 */

package pl.szczodrzynski.edziennik.ui.search

import android.text.Editable
import android.text.TextWatcher
import pl.szczodrzynski.edziennik.R

class SearchTextWatcher(
    private val holder: SearchViewHolder,
    private val adapter: SearchableAdapter<*>,
    private val item: SearchField,
) : TextWatcher {

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        holder.onTextChanged(s)
        item.searchText = s?.toString() ?: ""
        adapter.filter.filter(s) { _ ->
            val count = adapter.getMatchingContentCount()
            if (s.isNullOrBlank() || count == 0) {
                holder.b.searchLayout.helperText = " "
            } else {
                val resId = adapter.searchResultsPluralRes
                holder.b.searchLayout.helperText = holder.b.root.context.resources.getQuantityString(resId, count, count)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is SearchTextWatcher
    }

    override fun hashCode(): Int {
        var result = holder.hashCode()
        result = 31 * result + adapter.hashCode()
        result = 31 * result + item.hashCode()
        return result
    }
}
