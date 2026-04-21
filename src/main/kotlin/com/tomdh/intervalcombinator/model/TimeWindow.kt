package com.tomdh.intervalcombinator.model

import com.google.common.collect.Range
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder

/**
 * Represents a single time block on a specific day of the week.
 *
 * A [TimeWindow] is the fundamental unit of scheduling in this library.
 * It is immutable and defines a half-open interval `[start, end)` on a given [DayOfWeek].
 *
 * @property day The day of the week this window falls on.
 * @property start The inclusive start time.
 * @property end The exclusive end time (must not be before [start]).
 * @throws IllegalArgumentException if [end] is before [start].
 */
public data class TimeWindow(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime
) {
    init {
        require(!end.isBefore(start)) { "End time ($end) cannot be before start time ($start)." }
    }

    /**
     * Converts this window into an absolute minute-based [Range] within a week grid.
     *
     * The week grid maps Monday 00:00 to minute 0 and Sunday 23:59 to minute 10079.
     * This enables fast overlap detection via Guava's [com.google.common.collect.RangeSet].
     *
     * @return A `Range.closedOpen` of absolute week-minutes.
     */
    internal fun toAbsoluteMinuteRange(): Range<Int> {
        val baseOffset = (day.value - 1) * MINUTES_PER_DAY
        val startMin = baseOffset + start.hour * 60 + start.minute
        val endMin = baseOffset + end.hour * 60 + end.minute
        return Range.closedOpen(startMin, endMin)
    }

    public companion object {
        internal const val MINUTES_PER_DAY: Int = 1440

        private val TIME_FORMATTER = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mma")
            .toFormatter()

        /**
         * Parses a [TimeWindow] from a single-character day code and time strings.
         *
         * Supported day codes: `M` (Monday), `T` (Tuesday), `W` (Wednesday),
         * `R` (Thursday), `F` (Friday), `S` (Saturday), `U` (Sunday).
         *
         * @param dayChar A single character representing the day.
         * @param startTime The start time string, e.g. `"10:30am"`.
         * @param endTime The end time string, e.g. `"11:20am"`.
         * @return A new [TimeWindow].
         * @throws IllegalArgumentException if [dayChar] is unrecognized.
         * @throws java.time.format.DateTimeParseException if the time strings are malformed.
         */
        public fun parse(dayChar: Char, startTime: String, endTime: String): TimeWindow {
            val day = charToDay(dayChar)
                ?: throw IllegalArgumentException("Unknown day character: '$dayChar'. Expected one of: M, T, W, R, F, S, U.")
            val start = LocalTime.parse(startTime.replace(" ", ""), TIME_FORMATTER)
            val end = LocalTime.parse(endTime.replace(" ", ""), TIME_FORMATTER)
            return TimeWindow(day, start, end)
        }

        /**
         * Converts a single-character day code to a [DayOfWeek].
         *
         * @return The corresponding [DayOfWeek], or `null` if the character is unrecognized.
         */
        public fun charToDay(c: Char): DayOfWeek? = when (c.uppercaseChar()) {
            'M' -> DayOfWeek.MONDAY
            'T' -> DayOfWeek.TUESDAY
            'W' -> DayOfWeek.WEDNESDAY
            'R' -> DayOfWeek.THURSDAY
            'F' -> DayOfWeek.FRIDAY
            'S' -> DayOfWeek.SATURDAY
            'U' -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}
