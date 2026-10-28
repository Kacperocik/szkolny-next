package pl.szczodrzynski.edziennik.ui.settings

import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutSectionItem

enum class CornerPosition {
    FIRST,
    MIDDLE,
    LAST,
    SINGLE
}

object ItemCornerPositions {
    private val positions = HashMap<String, CornerPosition>()

    fun set(item: MaterialAboutItem, position: CornerPosition) {
        positions[item.id] = position
    }

    fun get(item: MaterialAboutItem): CornerPosition {
        return positions[item.id] ?: CornerPosition.SINGLE
    }

    fun applyGroupPositions(items: List<MaterialAboutItem>): List<MaterialAboutItem> {
        val currentGroup = mutableListOf<MaterialAboutItem>()

        fun flushGroup() {
            if (currentGroup.isEmpty()) return
            val size = currentGroup.size
            currentGroup.forEachIndexed { index, item ->
                val pos = when {
                    size == 1 -> CornerPosition.SINGLE
                    index == 0 -> CornerPosition.FIRST
                    index == size - 1 -> CornerPosition.LAST
                    else -> CornerPosition.MIDDLE
                }
                set(item, pos)
            }
            currentGroup.clear()
        }

        for (item in items) {
            if (item is MaterialAboutSectionItem) {
                flushGroup()
            } else {
                currentGroup.add(item)
            }
        }
        flushGroup()
        return items
    }
}
