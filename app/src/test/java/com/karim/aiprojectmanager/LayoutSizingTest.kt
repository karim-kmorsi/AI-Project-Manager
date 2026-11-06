package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class LayoutSizingTest {
    @Test fun toolbarKeepsActionsInlineWhenActualTextAndButtonsFit() {
        assertFalse(toolbarActionsNeedOwnRow(360, 0, 150, 180, 8))
        assertFalse(toolbarActionsNeedOwnRow(360, 48, 120, 176, 8))
    }

    @Test fun enlargedTextMovesActionsInsteadOfSacrificingTitleWidth() {
        assertTrue(toolbarActionsNeedOwnRow(360, 0, 250, 280, 8))
        assertTrue(toolbarActionsNeedOwnRow(320, 48, 200, 100, 8))
    }

    @Test fun toolbarAccountsForNavigationAndDoesNotCreateAnEmptyActionsRow() {
        assertFalse(toolbarActionsNeedOwnRow(300, 0, 150, 150, 0))
        assertTrue(toolbarActionsNeedOwnRow(300, 48, 150, 150, 0))
        assertFalse(toolbarActionsNeedOwnRow(300, 48, 900, 0, 8))
    }

    @Test fun veryLongTitleCannotOverflowWidthArithmetic() {
        assertTrue(toolbarActionsNeedOwnRow(360, 48, Int.MAX_VALUE, 180, 8))
    }

    @Test fun roomyEditorRetainsTheRemainingViewportHeight() {
        assertEquals(328, documentFieldHeight(500, 160, 12, 120))
    }

    @Test fun keyboardAndTallRecoveryChromeCannotCollapseTheInput() {
        // Former weighted field was only 48dp; a readable minimum now makes the body scroll.
        assertEquals(120, documentFieldHeight(220, 160, 12, 120))
        assertEquals(120, documentFieldHeight(220, 600, 12, 120))
        assertEquals(120, documentFieldHeight(0, 600, 12, 120))
    }

    @Test fun enlargedTypographyMinimumSurvivesACompactViewport() {
        assertEquals(208, documentFieldHeight(150, 240, 12, 208))
    }

    @Test fun editorArithmeticIsSafeForLargeMeasuredContent() {
        assertEquals(120, documentFieldHeight(360, Int.MAX_VALUE, 12, 120))
        assertEquals(Int.MAX_VALUE, documentFieldHeight(Int.MAX_VALUE, 0, 0, 120))
    }
}
