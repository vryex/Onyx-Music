package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class OnyxThemeTest {
    @Test
    fun defaultPaletteKeepsTextReadableOnEverySurface() {
        val scheme = onyxColorScheme(darkTheme = true)
        listOf(scheme.background, scheme.surface, scheme.surfaceContainer).forEach { surface ->
            assertTrue("Primary text must meet WCAG AA", contrast(scheme.onSurface, surface) >= 4.5f)
            assertTrue("Secondary text must meet WCAG AA", contrast(scheme.onSurfaceVariant, surface) >= 4.5f)
        }
        assertTrue("Accent button labels must remain readable", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
    }

    @Test
    fun explicitLightModeRetainsReadableControls() {
        val scheme = onyxColorScheme(darkTheme = false)
        assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5f)
        assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
