package com.tomdh.intervalcombinator.core

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.tomdh.intervalcombinator.model.TimeWindow

/**
 * Internal utility for computing free time from a set of occupied minute ranges.
 */
internal object FreeTimeCalculator {

    private const val DAY_START_HOUR = 7
    private const val DAY_END_HOUR = 23

    /**
     * Calculates total free time in minutes across all seven days of a week,
     * given a [RangeSet] of occupied absolute-minute ranges.
     *
     * For days with classes, free time is computed as gaps between busy blocks
     * plus padding from [DAY_START_HOUR] to the first class and from the last class
     * to [DAY_END_HOUR]. For days with no classes, a full day of free time
     * ([DAY_START_HOUR] to [DAY_END_HOUR]) is assumed.
     */
    fun calculate(rangeSet: RangeSet<Int>): Int {
        var totalFreeMinutes = 0
        val fullDayFree = (DAY_END_HOUR - DAY_START_HOUR) * 60

        for (dayIndex in 0 until 7) {
            val dayStart = dayIndex * TimeWindow.MINUTES_PER_DAY
            val dayEnd = dayStart + TimeWindow.MINUTES_PER_DAY - 1
            val dayRange = Range.closed(dayStart, dayEnd)

            val classRanges = rangeSet.subRangeSet(dayRange).asRanges()
            if (classRanges.isEmpty()) {
                totalFreeMinutes += fullDayFree
            } else {
                var previousEnd = -1
                for (r in classRanges) {
                    if (previousEnd != -1) {
                        totalFreeMinutes += (r.lowerEndpoint() - previousEnd)
                    }
                    previousEnd = r.upperEndpoint()
                }

                val firstStart = classRanges.first().lowerEndpoint() % TimeWindow.MINUTES_PER_DAY
                val lastEnd = classRanges.last().upperEndpoint() % TimeWindow.MINUTES_PER_DAY

                if (firstStart > DAY_START_HOUR * 60) {
                    totalFreeMinutes += firstStart - (DAY_START_HOUR * 60)
                }
                if (DAY_END_HOUR * 60 > lastEnd) {
                    totalFreeMinutes += (DAY_END_HOUR * 60) - lastEnd
                }
            }
        }
        return totalFreeMinutes
    }
}
