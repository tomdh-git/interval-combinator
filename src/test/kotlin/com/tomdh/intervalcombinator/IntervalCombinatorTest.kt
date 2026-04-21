package com.tomdh.intervalcombinator

import com.tomdh.intervalcombinator.dsl.IntervalCombinator
import com.tomdh.intervalcombinator.exception.InvalidConfigurationException
import com.tomdh.intervalcombinator.model.CombinatorItem
import com.tomdh.intervalcombinator.model.TimeWindow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalTime

class TimeWindowTest {

    @Test
    fun `parse creates correct TimeWindow from day char and time strings`() {
        val tw = TimeWindow.parse('M', "1:30pm", "2:50pm")
        assertEquals(DayOfWeek.MONDAY, tw.day)
        assertEquals(LocalTime.of(13, 30), tw.start)
        assertEquals(LocalTime.of(14, 50), tw.end)
    }

    @Test
    fun `parse handles case insensitive time strings`() {
        val tw = TimeWindow.parse('T', "10:00AM", "11:30AM")
        assertEquals(DayOfWeek.TUESDAY, tw.day)
        assertEquals(LocalTime.of(10, 0), tw.start)
        assertEquals(LocalTime.of(11, 30), tw.end)
    }

    @Test
    fun `parse throws on unknown day character`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow.parse('X', "10:00am", "11:00am")
        }
    }

    @Test
    fun `constructor throws when end is before start`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(10, 0))
        }
    }

    @Test
    fun `charToDay maps all supported codes correctly`() {
        assertEquals(DayOfWeek.MONDAY, TimeWindow.charToDay('M'))
        assertEquals(DayOfWeek.TUESDAY, TimeWindow.charToDay('T'))
        assertEquals(DayOfWeek.WEDNESDAY, TimeWindow.charToDay('W'))
        assertEquals(DayOfWeek.THURSDAY, TimeWindow.charToDay('R'))
        assertEquals(DayOfWeek.FRIDAY, TimeWindow.charToDay('F'))
        assertEquals(DayOfWeek.SATURDAY, TimeWindow.charToDay('S'))
        assertEquals(DayOfWeek.SUNDAY, TimeWindow.charToDay('U'))
        assertNull(TimeWindow.charToDay('Z'))
    }
}

class GenerateCombinationsTest {

    @Test
    fun `non-overlapping combinations are correctly generated`() {
        val itemA1 = CombinatorItem("A1", listOf(TimeWindow.parse('M', "10:00am", "11:00am")))
        val itemA2 = CombinatorItem("A2", listOf(TimeWindow.parse('T', "10:00am", "11:00am")))

        val itemB1 = CombinatorItem("B1", listOf(TimeWindow.parse('M', "10:30am", "11:30am"))) // overlaps A1
        val itemB2 = CombinatorItem("B2", listOf(TimeWindow.parse('F', "10:00am", "11:00am")))

        val results = IntervalCombinator.generate<String> {
            groups = listOf(listOf(itemA1, itemA2), listOf(itemB1, itemB2))
        }

        // A1+B1 = conflict. A1+B2, A2+B1, A2+B2 = valid
        assertEquals(3, results.size)

        val combos = results.map { it.items.toSet() }
        assertTrue(combos.contains(setOf("A1", "B2")))
        assertTrue(combos.contains(setOf("A2", "B1")))
        assertTrue(combos.contains(setOf("A2", "B2")))
    }

    @Test
    fun `global bounds correctly filter out-of-range items`() {
        val earlyItem = CombinatorItem("early", listOf(TimeWindow.parse('M', "7:00am", "8:00am")))
        val lateItem = CombinatorItem("late", listOf(TimeWindow.parse('M', "10:00am", "11:00am")))

        val results = IntervalCombinator.generate<String> {
            groups = listOf(listOf(earlyItem, lateItem))
            constraints {
                globalStart = LocalTime.of(9, 0)
                globalEnd = LocalTime.of(17, 0)
            }
        }

        assertEquals(1, results.size)
        assertEquals("late", results[0].items[0])
    }

    @Test
    fun `optimizeFreeTime ranks results by descending free time`() {
        val itemA1 = CombinatorItem("A1", listOf(TimeWindow.parse('M', "10:00am", "11:00am")))
        val itemA2 = CombinatorItem("A2", listOf(TimeWindow.parse('M', "8:00am", "10:00am"))) // 2 hours

        val itemB1 = CombinatorItem("B1", listOf(TimeWindow.parse('M', "11:30am", "12:30pm")))

        val results = IntervalCombinator.generate<String> {
            groups = listOf(listOf(itemA1, itemA2), listOf(itemB1))
            algorithms {
                optimizeFreeTime = true
            }
        }

        assertEquals(2, results.size)
        // A1 (1hr) + B1 (1hr) = 2hr classes → more free time
        // A2 (2hr) + B1 (1hr) = 3hr classes → less free time
        assertEquals(setOf("A1", "B1"), results[0].items.toSet())
        assertTrue(results[0].freeTimeMinutes > results[1].freeTimeMinutes)
    }

    @Test
    fun `empty groups throws InvalidConfigurationException`() {
        assertThrows<InvalidConfigurationException> {
            IntervalCombinator.generate<String> {
                groups = emptyList()
            }
        }
    }

    @Test
    fun `returns empty list when a group has no viable candidates after filtering`() {
        val item = CombinatorItem("A", listOf(TimeWindow.parse('M', "7:00am", "8:00am")))

        val results = IntervalCombinator.generate<String> {
            groups = listOf(listOf(item))
            constraints {
                globalStart = LocalTime.of(9, 0)
            }
        }

        assertTrue(results.isEmpty())
    }

    @Test
    fun `items with no windows are always included`() {
        val noWindows = CombinatorItem("async-course", emptyList<TimeWindow>())
        val normal = CombinatorItem("normal", listOf(TimeWindow.parse('M', "10:00am", "11:00am")))

        val results = IntervalCombinator.generate<String> {
            groups = listOf(listOf(noWindows), listOf(normal))
        }

        assertEquals(1, results.size)
        assertEquals(setOf("async-course", "normal"), results[0].items.toSet())
    }

    @Test
    fun `maxResults limits output count`() {
        // Create many non-overlapping items so many combos are possible
        val groupA = (1..5).map { i ->
            CombinatorItem("A$i", listOf(TimeWindow.parse('M', "${7 + i}:00am", "${7 + i}:50am")))
        }
        val groupB = (1..5).map { i ->
            CombinatorItem("B$i", listOf(TimeWindow.parse('T', "${7 + i}:00am", "${7 + i}:50am")))
        }

        val results = IntervalCombinator.generate<String> {
            groups = listOf(groupA, groupB)
            algorithms {
                maxResults = 3
            }
        }

        assertEquals(3, results.size)
    }
}

class FindFillersTest {

    @Test
    fun `finds only non-conflicting fillers`() {
        val existing = CombinatorItem("A", listOf(TimeWindow.parse('M', "10:00am", "11:00am")))
        val fillerBad = CombinatorItem("F-Bad", listOf(TimeWindow.parse('M', "10:30am", "11:30am")))
        val fillerGood = CombinatorItem("F-Good", listOf(TimeWindow.parse('T', "10:00am", "11:00am")))

        val result = IntervalCombinator.findFillers<String> {
            this.existing = listOf(existing)
            candidates = listOf(fillerBad, fillerGood)
        }

        assertEquals(1, result.size)
        assertEquals("F-Good", result[0].payload)
    }

    @Test
    fun `respects global bounds when filtering fillers`() {
        val existing = CombinatorItem("A", listOf(TimeWindow.parse('M', "10:00am", "11:00am")))
        val earlyFiller = CombinatorItem("early", listOf(TimeWindow.parse('T', "6:00am", "7:00am")))
        val validFiller = CombinatorItem("valid", listOf(TimeWindow.parse('T', "10:00am", "11:00am")))

        val result = IntervalCombinator.findFillers<String> {
            this.existing = listOf(existing)
            candidates = listOf(earlyFiller, validFiller)
            constraints {
                globalStart = LocalTime.of(8, 0)
            }
        }

        assertEquals(1, result.size)
        assertEquals("valid", result[0].payload)
    }
}
