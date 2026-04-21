package com.tomdh.intervalcombinator.model

import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet

/**
 * A generic wrapper that pairs an arbitrary payload with a set of [TimeWindow]s.
 *
 * This is the primary input unit for the combinatorial solver. The type parameter [T]
 * allows consumers to attach any domain object (e.g., a course, a shift, a meeting)
 * as the payload, keeping the library completely domain-agnostic.
 *
 * @param T The type of the payload object.
 * @property payload The domain object associated with these time windows.
 * @property windows The list of [TimeWindow]s during which this item is active.
 */
public data class CombinatorItem<T>(
    val payload: T,
    val windows: List<TimeWindow>
) {
    /**
     * Pre-computed [RangeSet] of absolute week-minute ranges for fast overlap detection.
     */
    internal val rangeSet: RangeSet<Int> = TreeRangeSet.create<Int>().also { set ->
        windows.forEach { set.add(it.toAbsoluteMinuteRange()) }
    }
}
