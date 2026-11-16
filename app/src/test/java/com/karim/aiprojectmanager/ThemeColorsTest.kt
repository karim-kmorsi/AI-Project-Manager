package com.karim.aiprojectmanager

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ThemeColorsTest {
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double = value.toDouble().let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
        return .2126 * channel(color.red) + .7152 * channel(color.green) + .0722 * channel(color.blue)
    }
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    private fun readable(foreground: Color, background: Color, minimum: Double = 4.5) {
        val ratio = contrast(foreground, background)
        assertTrue("$foreground on $background contrast $ratio < $minimum", ratio >= minimum)
    }

    @Test fun approvedBaseColorsAndAllContainerRolesAreExplicit() {
        for (dark in listOf(false, true)) {
            val c = appearanceColors(dark)
            val card = Color(if (dark) 0xFF172131 else 0xFFFFFFFF)
            assertEquals(Color(if (dark) 0xFF0B101D else 0xFFF6F8FB), c.background)
            assertEquals(c.background, c.surface)
            assertEquals(Color(if (dark) 0xFFFFFFFF else 0xFF17263D), c.onSurface)
            assertEquals(Color(if (dark) 0xFFC4CCD6 else 0xFF536276), c.onSurfaceVariant)
            assertEquals(Color(if (dark) 0xFF00C2FF else 0xFF0B5ED7), c.primary)
            listOf(c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest, c.surfaceVariant).forEach { assertEquals(card, it) }
            assertEquals(Color.Transparent, c.surfaceTint)
            assertEquals(Color(0xFF0D6EFD), c.primaryContainer)
            assertEquals(Color(0xFF0D6EFD), FilledActionBlue)
        }
    }

    @Test fun textErrorContainerAndInversePairsMeetNormalTextContrast() {
        for (dark in listOf(false, true)) {
            val c = appearanceColors(dark)
            for (surface in listOf(c.background, c.surfaceContainerLow, c.surfaceContainerHigh)) {
                listOf(c.onSurface, c.onSurfaceVariant, c.primary, c.error).forEach { readable(it, surface) }
                readable(c.outline, surface, 3.0)
            }
            listOf(c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondary to c.secondary, c.onSecondaryContainer to c.secondaryContainer,
                c.onTertiary to c.tertiary, c.onTertiaryContainer to c.tertiaryContainer,
                c.onError to c.error, c.onErrorContainer to c.errorContainer,
                c.inverseOnSurface to c.inverseSurface, c.inversePrimary to c.inverseSurface
            ).forEach { (fg, bg) -> readable(fg, bg) }
        }
    }

    @Test fun filledActionBlackInteractionLayersPreserveWhiteTextContrast() {
        for (alpha in listOf(0f, .08f, .1f, .12f, .16f)) {
            readable(Color.White, Color.Black.copy(alpha = alpha).compositeOver(FilledActionBlue))
        }
    }

    @Test fun textActionInteractionLayersRetainContrastOnUsedSurfaces() {
        for (dark in listOf(false, true)) {
            val c = appearanceColors(dark)
            for (surface in listOf(c.background, c.surfaceContainerHigh)) {
                for (alpha in listOf(0f, .08f, .1f, .12f)) readable(c.primary, c.primary.copy(alpha = alpha).compositeOver(surface))
            }
        }
    }
}
