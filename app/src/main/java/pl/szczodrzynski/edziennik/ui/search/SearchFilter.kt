/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-14.
 */

package pl.szczodrzynski.edziennik.ui.search

import android.widget.Filter
import pl.szczodrzynski.edziennik.data.db.entity.Note
import pl.szczodrzynski.edziennik.ext.cleanDiacritics
import java.util.*
import kotlin.math.min

class SearchFilter<T : Searchable<T>>(
    private val adapter: SearchableAdapter<T>,
) : Filter() {
    companion object {
        private const val NO_MATCH = 1000
    }

    private var prevCount = -1

    private val allItems
        get() = adapter.allItems

    private fun getMatchWeight(name: CharSequence?, prefix: CharSequence): Int {
        if (name == null)
            return NO_MATCH

        val prefixClean = prefix.cleanDiacritics()
        val nameClean = name.cleanDiacritics()

        return when {
            // First match against the whole, non-split value
            nameClean.startsWith(prefixClean, ignoreCase = true) -> 1
            // check if prefix matches any of the words
            nameClean.split(" ").any {
                it.startsWith(prefixClean, ignoreCase = true)
            } -> 2
            // finally check if the prefix matches any part of the name
            nameClean.contains(prefixClean, ignoreCase = true) -> 3

            else -> NO_MATCH
        }
    }

    override fun performFiltering(prefix: CharSequence?): FilterResults {
        val results = FilterResults()

        if (prevCount == -1)
            prevCount = allItems.size

        if (prefix.isNullOrBlank()) {
            allItems.forEach {
                it.searchPriority = NO_MATCH
                it.searchHighlightText = null
            }
            results.values = allItems.toList()
            results.count = allItems.size
            return results
        }

        // 1. Evaluate matching content items (non-header, non-SearchField)
        val matchingContentItems = mutableListOf<T>()
        allItems.forEach { item ->
            if (item is SearchField || item.isCategoryHeader) return@forEach
            val searchKeywords = item.searchKeywords
            if (searchKeywords.isEmpty()) return@forEach

            item.searchPriority = NO_MATCH
            item.searchHighlightText = null

            var matchWeight: Int
            searchKeywords.forEachIndexed { priority, keywords ->
                keywords ?: return@forEachIndexed
                keywords.forEach { keyword ->
                    matchWeight = getMatchWeight(keyword, prefix)
                    if (matchWeight != NO_MATCH) {
                        if (matchWeight == 3)
                            matchWeight = 100
                        item.searchPriority = min(item.searchPriority, priority * 10 + matchWeight)
                    }
                }
            }

            if (item.searchPriority != NO_MATCH) {
                if (adapter.isReversed)
                    item.searchPriority *= -1
                item.searchHighlightText = prefix.toString()
                matchingContentItems.add(item)
            }
        }

        // 2. Build final list: SearchField + matching Category Headers + matching Content Items
        val finalItems = mutableListOf<T>()
        allItems.forEach { item ->
            when {
                item is SearchField -> {
                    finalItems.add(item)
                }
                item.isCategoryHeader -> {
                    val hasMatchingChild = if (item is Note) {
                        matchingContentItems.any { it is Note && !it.isCategoryItem && it.ownerType == item.ownerType }
                    } else {
                        true
                    }
                    if (hasMatchingChild) {
                        finalItems.add(item)
                    }
                }
                item in matchingContentItems -> {
                    finalItems.add(item)
                }
            }
        }

        results.values = finalItems.sorted()
        results.count = finalItems.size
        return results
    }

    override fun publishResults(constraint: CharSequence?, results: FilterResults) {
        results.values?.let {
            @Suppress("UNCHECKED_CAST") // yes I know it's checked.
            adapter.setFilteredItems(it as List<T>)
        }
        // do not re-bind the search box
        val count = results.count - 1

        // this tries to update every item except the search field
        with(adapter) {
            when {
                count > prevCount -> {
                    notifyItemRangeInserted(prevCount + 1, count - prevCount)
                    notifyItemRangeChanged(1, prevCount)
                }
                count < prevCount -> {
                    notifyItemRangeRemoved(prevCount + 1, prevCount - count)
                    notifyItemRangeChanged(1, count)
                }
                else -> {
                    notifyItemRangeChanged(1, count)
                }
            }
        }

        prevCount = count
    }
}
