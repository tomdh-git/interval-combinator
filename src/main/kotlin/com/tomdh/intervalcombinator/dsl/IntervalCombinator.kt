package com.tomdh.intervalcombinator.dsl

import com.tomdh.intervalcombinator.core.BacktrackSolver
import com.tomdh.intervalcombinator.core.CompatibilityFilter
import com.tomdh.intervalcombinator.exception.InvalidConfigurationException
import com.tomdh.intervalcombinator.model.CombinatorItem
import com.tomdh.intervalcombinator.model.Timetable
import java.time.LocalTime

/**
 * Annotation to restrict DSL scope leaking.
 *
 * Prevents accidentally accessing outer receiver methods from nested builder blocks.
 */
@DslMarker
public annotation class CombinatorDslMarker

/**
 * Configuration block for time-boundary constraints.
 *
 * Used inside the [CombinatorConfig] DSL to specify the earliest and latest
 * allowed times for any item in the generated timetable.
 */
@CombinatorDslMarker
public class ConstraintsConfig {
    /** The earliest allowed start time for any item. Defaults to [LocalTime.MIN]. */
    public var globalStart: LocalTime = LocalTime.MIN

    /** The latest allowed end time for any item. Defaults to [LocalTime.MAX]. */
    public var globalEnd: LocalTime = LocalTime.MAX
}

/**
 * Configuration block for solver algorithm behavior.
 *
 * Used inside the [CombinatorConfig] DSL to tune the backtracking engine.
 */
@CombinatorDslMarker
public class AlgorithmConfig {
    /** If `true`, results are ranked by maximizing free time between items. Defaults to `false`. */
    public var optimizeFreeTime: Boolean = false

    /** Maximum number of timetable results to return. Defaults to `100`. */
    public var maxResults: Int = 100
}

/**
 * Top-level configuration for a combination generation request.
 *
 * This is the receiver for the [IntervalCombinator.generate] DSL block.
 *
 * ```kotlin
 * val results = IntervalCombinator.generate<MyPayload> {
 *     groups = listOf(groupA, groupB)
 *
 *     constraints {
 *         globalStart = LocalTime.of(8, 0)
 *         globalEnd = LocalTime.of(22, 0)
 *     }
 *
 *     algorithms {
 *         optimizeFreeTime = true
 *         maxResults = 50
 *     }
 * }
 * ```
 */
@CombinatorDslMarker
public class CombinatorConfig<T> {
    /**
     * The groups of items to combine. The solver picks exactly one item from each group.
     * Must not be empty.
     */
    public var groups: List<List<CombinatorItem<T>>> = emptyList()

    internal val constraintsConfig = ConstraintsConfig()
    internal val algorithmConfig = AlgorithmConfig()

    /** Configure time-boundary constraints. */
    public fun constraints(block: ConstraintsConfig.() -> Unit) {
        constraintsConfig.apply(block)
    }

    /** Configure solver algorithm parameters. */
    public fun algorithms(block: AlgorithmConfig.() -> Unit) {
        algorithmConfig.apply(block)
    }
}

/**
 * Configuration for a filler-compatibility query.
 *
 * This is the receiver for the [IntervalCombinator.findFillers] DSL block.
 *
 * ```kotlin
 * val compatible = IntervalCombinator.findFillers<MyPayload> {
 *     existing = currentScheduleItems
 *     candidates = allFillerItems
 *
 *     constraints {
 *         globalStart = LocalTime.of(8, 0)
 *         globalEnd = LocalTime.of(22, 0)
 *     }
 * }
 * ```
 */
@CombinatorDslMarker
public class FillerConfig<T> {
    /** The items already present in the timetable. */
    public var existing: List<CombinatorItem<T>> = emptyList()

    /** The candidate filler items to check for compatibility. */
    public var candidates: List<CombinatorItem<T>> = emptyList()

    internal val constraintsConfig = ConstraintsConfig()

    /** Configure time-boundary constraints for fillers. */
    public fun constraints(block: ConstraintsConfig.() -> Unit) {
        constraintsConfig.apply(block)
    }
}

/**
 * The primary public entry point for the interval-combinator library.
 *
 * Provides a fluent Kotlin DSL for generating non-overlapping timetable combinations
 * and finding compatible filler items. All complex solver internals are hidden behind
 * this clean API surface.
 *
 * ## Generating Combinations
 * ```kotlin
 * val results = IntervalCombinator.generate<String> {
 *     groups = listOf(
 *         listOf(itemA1, itemA2),
 *         listOf(itemB1, itemB2)
 *     )
 *     constraints {
 *         globalStart = LocalTime.of(8, 0)
 *         globalEnd = LocalTime.of(22, 0)
 *     }
 *     algorithms {
 *         optimizeFreeTime = true
 *         maxResults = 50
 *     }
 * }
 * ```
 *
 * ## Finding Compatible Fillers
 * ```kotlin
 * val fillers = IntervalCombinator.findFillers<String> {
 *     existing = currentItems
 *     candidates = allPossibleFillers
 *     constraints {
 *         globalStart = LocalTime.of(8, 0)
 *     }
 * }
 * ```
 */
public object IntervalCombinator {

    /**
     * Generates all valid, non-overlapping timetable combinations.
     *
     * The solver picks exactly one [CombinatorItem] from each group and verifies
     * that no time windows overlap. Items outside the configured global bounds are
     * automatically excluded.
     *
     * @param T The type of the payload objects.
     * @param block A DSL configuration block.
     * @return A list of valid [Timetable]s, potentially ranked by free time.
     * @throws InvalidConfigurationException if no groups are provided.
     */
    public fun <T> generate(block: CombinatorConfig<T>.() -> Unit): List<Timetable<T>> {
        val config = CombinatorConfig<T>().apply(block)

        if (config.groups.isEmpty()) {
            throw InvalidConfigurationException("At least one group of items must be provided.")
        }

        val constraints = config.constraintsConfig
        val algo = config.algorithmConfig

        // Filter items that violate global bounds
        val processedGroups = config.groups.map { group ->
            group.filter { item ->
                item.windows.all { w ->
                    !w.start.isBefore(constraints.globalStart) && !w.end.isAfter(constraints.globalEnd)
                }
            }
        }

        // If any required group has zero remaining candidates, no solution is possible
        if (processedGroups.any { it.isEmpty() }) return emptyList()

        // Sort by group size ascending to prune the search tree early
        val sortedGroups = processedGroups.sortedBy { it.size }

        return BacktrackSolver<T>().solve(
            groups = sortedGroups,
            optimizeFreeTime = algo.optimizeFreeTime,
            limit = algo.maxResults
        )
    }

    /**
     * Finds all filler items that are compatible with an existing timetable.
     *
     * A filler is considered compatible if none of its time windows overlap with
     * any existing item's windows and all its windows fall within the global bounds.
     *
     * @param T The type of the payload objects.
     * @param block A DSL configuration block.
     * @return A list of compatible [CombinatorItem]s.
     */
    public fun <T> findFillers(block: FillerConfig<T>.() -> Unit): List<CombinatorItem<T>> {
        val config = FillerConfig<T>().apply(block)
        val constraints = config.constraintsConfig

        return CompatibilityFilter.filter(
            existingItems = config.existing,
            fillers = config.candidates,
            globalStartBounds = constraints.globalStart,
            globalEndBounds = constraints.globalEnd
        )
    }
}
