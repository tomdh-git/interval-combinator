package com.tomdh.intervalcombinator.core

import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.tomdh.intervalcombinator.model.CombinatorItem
import com.tomdh.intervalcombinator.model.Timetable
import java.time.LocalTime
import java.util.PriorityQueue

/**
 * Internal backtracking engine that generates all valid, non-overlapping combinations
 * by selecting exactly one [CombinatorItem] from each group.
 */
internal class BacktrackSolver<T> {

    /**
     * Executes the backtracking search.
     *
     * @param groups Pre-filtered and sorted groups of candidate items.
     * @param optimizeFreeTime Whether to rank results by free time.
     * @param limit Maximum number of results to keep.
     * @return A list of valid [Timetable]s.
     */
    fun solve(
        groups: List<List<CombinatorItem<T>>>,
        optimizeFreeTime: Boolean,
        limit: Int
    ): List<Timetable<T>> {
        if (groups.isEmpty()) return emptyList()

        val priorityQueue = if (optimizeFreeTime) {
            PriorityQueue<Timetable<T>>(limit, compareBy { it.freeTimeMinutes })
        } else null

        val normalResults = if (!optimizeFreeTime) mutableListOf<Timetable<T>>() else null

        val currentItems = ArrayList<CombinatorItem<T>>(groups.size)
        val currentRanges = TreeRangeSet.create<Int>()

        backtrack(
            groupIndex = 0,
            currentItems = currentItems,
            currentRanges = currentRanges,
            groups = groups,
            priorityQueue = priorityQueue,
            listResults = normalResults,
            limit = limit,
            optimizeFreeTime = optimizeFreeTime
        )

        return if (optimizeFreeTime) {
            priorityQueue!!.sortedByDescending { it.freeTimeMinutes }
        } else {
            normalResults!!
        }
    }

    private fun backtrack(
        groupIndex: Int,
        currentItems: MutableList<CombinatorItem<T>>,
        currentRanges: RangeSet<Int>,
        groups: List<List<CombinatorItem<T>>>,
        priorityQueue: PriorityQueue<Timetable<T>>?,
        listResults: MutableList<Timetable<T>>?,
        limit: Int,
        optimizeFreeTime: Boolean
    ) {
        if (groupIndex == groups.size) {
            val payloads = currentItems.map { it.payload }
            val timetable = if (optimizeFreeTime) {
                Timetable(payloads, FreeTimeCalculator.calculate(currentRanges))
            } else {
                Timetable(payloads, 0)
            }

            if (optimizeFreeTime) {
                if (priorityQueue!!.size < limit) {
                    priorityQueue.add(timetable)
                } else if (timetable.freeTimeMinutes > priorityQueue.peek()!!.freeTimeMinutes) {
                    priorityQueue.poll()
                    priorityQueue.add(timetable)
                }
            } else {
                if (listResults!!.size < limit) {
                    listResults.add(timetable)
                }
            }
            return
        }

        val currentResultCount = if (optimizeFreeTime) priorityQueue!!.size else listResults!!.size
        if (!optimizeFreeTime && currentResultCount >= limit) return

        val candidateGroup = groups[groupIndex]
        for (item in candidateGroup) {
            if (item.windows.isEmpty()) {
                currentItems.add(item)
                backtrack(groupIndex + 1, currentItems, currentRanges, groups, priorityQueue, listResults, limit, optimizeFreeTime)
                currentItems.removeAt(currentItems.lastIndex)
                continue
            }

            var conflict = false
            for (range in item.rangeSet.asRanges()) {
                if (!currentRanges.subRangeSet(range).isEmpty) {
                    conflict = true
                    break
                }
            }

            if (!conflict) {
                currentItems.add(item)
                currentRanges.addAll(item.rangeSet)

                backtrack(groupIndex + 1, currentItems, currentRanges, groups, priorityQueue, listResults, limit, optimizeFreeTime)

                currentItems.removeAt(currentItems.lastIndex)
                currentRanges.removeAll(item.rangeSet)
            }
        }
    }
}
