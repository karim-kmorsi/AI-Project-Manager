package com.karim.aiprojectmanager

import java.text.Bidi
import org.junit.Assert.*
import org.junit.Test

class TaskCountDisplayTest {
    /** Host Unicode bidi ordering only; Android glyph positions are checked by UI regressions. */
    private fun visualOrder(text: String, direction: Int): String {
        val bidi = Bidi(text, direction)
        val characters = Array<Any>(text.length) { text[it] }
        val levels = ByteArray(text.length) { bidi.getLevelAt(it).toByte() }
        Bidi.reorderVisually(levels, 0, characters, 0, characters.size)
        return characters.joinToString("").filter { Character.getType(it) != Character.FORMAT.toInt() }
    }

    @Test fun zeroOfOneKeepsNumeratorBeforeDenominatorInBothContexts() {
        for (prefix in listOf("المهام المتحققة: ", "Verified tasks: ")) {
            for (direction in listOf(Bidi.DIRECTION_LEFT_TO_RIGHT, Bidi.DIRECTION_RIGHT_TO_LEFT)) {
                val result = visualOrder(prefix + taskCountExpression(0, 1), direction)
                assertTrue(result, result.contains("0 / 1"))
                assertFalse(result, result.contains("1 / 0"))
            }
        }
    }

    @Test fun multipleDigitsAndEmptyCountsKeepValuesAndOrder() {
        for ((verified, total) in listOf(12 to 345, 0 to 0, 1 to 1)) {
            val expected = "$verified / $total"
            for (direction in listOf(Bidi.DIRECTION_LEFT_TO_RIGHT, Bidi.DIRECTION_RIGHT_TO_LEFT)) {
                val result = visualOrder("المهام المتحققة: " + taskCountExpression(verified, total), direction)
                assertTrue(result, result.contains(expected))
            }
        }
    }

    @Test fun isolationIsBalancedAndLimitedToTheDisplayExpression() {
        val display = taskCountExpression(0, 1)
        assertEquals('\u2066', display.first())
        assertEquals('\u2069', display.last())
        assertEquals("0 / 1", display.substring(1, display.lastIndex))
    }
}
