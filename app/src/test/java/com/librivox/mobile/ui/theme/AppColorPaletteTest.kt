package com.librivox.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.librivox.mobile.colorutilities.Contrast
import com.librivox.mobile.colorutilities.Hct
import com.librivox.mobile.colorutilities.MathUtils
import com.librivox.mobile.colorutilities.SchemeContent
import com.librivox.mobile.playback.AppColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorPaletteTest {
    @Test
    fun deviceChoicePreservesSchemeAndHybridOnlyChangesSecondaryFamily() {
        for (seed in Seeds) for (dark in listOf(false, true)) {
            val device = scheme(seed, dark)
            assertSame(device, resolveAppColorScheme(AppColorPalette.Device, dark, device))
            val mixed = resolveAppColorScheme(AppColorPalette.DeviceAndApp, dark, device)
            assertEquals(otherRoles(device), otherRoles(mixed))
            secondaryRoles(device).zip(secondaryRoles(mixed)).forEach { (before, after) ->
                assertEquals(tone(before), tone(after), 0.3)
            }
            for ((foreground, background) in listOf(1 to 0, 3 to 2, 6 to 4, 7 to 5)) {
                val original = secondaryRoles(device)
                val changed = secondaryRoles(mixed)
                val beforeRatio = Contrast.ratioOfTones(tone(original[foreground]), tone(original[background]))
                val afterRatio = Contrast.ratioOfTones(tone(changed[foreground]), tone(changed[background]))
                assertTrue("Secondary text must stay readable", afterRatio >= 4.5)
                assertEquals(beforeRatio, afterRatio, 0.12)
            }
        }
    }

    @Test
    fun hybridPurpleStaysNearBrandHueIncludingGrayDevicePalettes() {
        val purpleHue = Hct.fromInt(AppBrandPurple).hue
        for (seed in Seeds) {
            val mixed = resolveAppColorScheme(AppColorPalette.DeviceAndApp, false, scheme(seed, false))
            val hue = Hct.fromInt(mixed.secondary.toArgb()).hue
            assertTrue(MathUtils.differenceDegrees(purpleHue, hue) <= 17.0)
        }
    }

    @Test
    fun appChoiceAndNoDeviceFallbackUseCurrentBrandIndependentlyOfDevice() {
        for (dark in listOf(false, true)) {
            val expected = scheme(AppBrandPurple, dark)
            for (choice in AppColorPalette.entries) {
                val fallback = resolveAppColorScheme(choice, dark, null)
                assertEquals(otherRoles(expected), otherRoles(fallback))
                assertEquals(secondaryRoles(expected), secondaryRoles(fallback))
            }
            val selected = resolveAppColorScheme(AppColorPalette.App, dark, scheme(Seeds[1], dark))
            assertEquals(otherRoles(expected), otherRoles(selected))
            assertEquals(secondaryRoles(expected), secondaryRoles(selected))
        }
    }

    private fun scheme(seed: Int, dark: Boolean) =
        colorSchemeFromDynamicScheme(SchemeContent(Hct.fromInt(seed), dark, 0.0), dark)

    private fun tone(color: Color) = Hct.fromInt(color.toArgb()).tone

    private fun secondaryRoles(c: ColorScheme) = listOf(
        c.secondary, c.onSecondary, c.secondaryContainer, c.onSecondaryContainer,
        c.secondaryFixed, c.secondaryFixedDim, c.onSecondaryFixed, c.onSecondaryFixedVariant,
    )

    private fun otherRoles(c: ColorScheme) = listOf(
        c.primary, c.onPrimary, c.primaryContainer, c.onPrimaryContainer, c.inversePrimary,
        c.tertiary, c.onTertiary, c.tertiaryContainer, c.onTertiaryContainer,
        c.background, c.onBackground, c.surface, c.onSurface, c.surfaceVariant, c.onSurfaceVariant,
        c.surfaceTint, c.inverseSurface, c.inverseOnSurface, c.error, c.onError,
        c.errorContainer, c.onErrorContainer, c.outline, c.outlineVariant, c.scrim,
        c.surfaceDim, c.surfaceBright, c.surfaceContainerLowest, c.surfaceContainerLow,
        c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest,
        c.primaryFixed, c.primaryFixedDim, c.onPrimaryFixed, c.onPrimaryFixedVariant,
        c.tertiaryFixed, c.tertiaryFixedDim, c.onTertiaryFixed, c.onTertiaryFixedVariant,
    )

    private companion object {
        val Seeds = listOf(
            AppBrandPurple, 0xffdb6c10.toInt(), 0xff277b3f.toInt(),
            0xff2466be.toInt(), 0xff808080.toInt(),
        )
    }
}
