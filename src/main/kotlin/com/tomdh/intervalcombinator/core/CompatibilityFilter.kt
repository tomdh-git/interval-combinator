package com.tomdh.intervalcombinator.core

import com.google.common.collect.TreeRangeSet
import com.tomdh.intervalcombinator.model.CombinatorItem
import java.time.LocalTime

/**
 * Internal utility for filtering compatible filler items against an existing timetable.
 */
internal object CompatibilityFilter {

    /**
     * Returns all [fillers] whose time windows do not overlap with any [existingItems]
     * and whose windows fall within the specified global bounds.
     */
    fun <T> filter(
        existingItems: List<CombinatorItem<T>>,
        fillers: List<CombinatorItem<T>>,
        globalStartBounds: LocalTime,
        globalEndBounds: LocalTime
    ): List<CombinatorItem<T>> {
        val currentRanges = TreeRangeSet.create<Int>()
        existingItems.forEach { currentRanges.addAll(it.rangeSet) }

        return fillers.filter { filler ->
            val boundsValid = filler.windows.all { w ->
                !w.start.isBefore(globalStartBounds) && !w.end.isAfter(globalEndBounds)
            }
            if (!boundsValid) return@filter false

            val hasConflict = filler.rangeSet.asRanges().any { range ->
                !currentRanges.subRangeSet(range).isEmpty
            }
            !hasConflict
        }
    }
}
