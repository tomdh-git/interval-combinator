package com.tomdh.intervalcombinator.model

/**
 * Represents a single valid, collision-free combination of items.
 *
 * A [Timetable] is an output of the solver, containing the selected payloads
 * and an optional free-time metric for ranking purposes.
 *
 * @param T The type of the payload objects.
 * @property items The list of selected payloads forming this timetable.
 * @property freeTimeMinutes The total computed free time in minutes.
 *   This is only meaningful when the solver was configured with `optimizeFreeTime = true`;
 *   otherwise it defaults to `0`.
 */
public data class Timetable<T>(
    val items: List<T>,
    val freeTimeMinutes: Int
)
