package com.librivox.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.librivox.mobile.colorutilities.Hct
import com.librivox.mobile.colorutilities.MathUtils
import com.librivox.mobile.colorutilities.SchemeContent
import com.librivox.mobile.playback.AppColorPalette
import kotlin.math.min

// The lighter book leaf in the current launcher icon.
internal const val AppBrandPurple: Int = 0xff7761ab.toInt()

internal fun resolveAppColorScheme(
    palette: AppColorPalette,
    darkTheme: Boolean,
    deviceScheme: ColorScheme?,
): ColorScheme {
    if (palette == AppColorPalette.App || deviceScheme == null) {
        return colorSchemeFromDynamicScheme(
            SchemeContent(Hct.fromInt(AppBrandPurple), darkTheme, 0.0),
            darkTheme,
        )
    }
    if (palette == AppColorPalette.Device) return deviceScheme

    val purple = Hct.fromInt(AppBrandPurple)
    val devicePrimary = Hct.fromInt(deviceScheme.primary.toArgb())
    val hue = if (devicePrimary.chroma < 5.0) {
        purple.hue
    } else {
        MathUtils.sanitizeDegreesDouble(
            purple.hue + MathUtils.rotationDirection(purple.hue, devicePrimary.hue) *
                min(MathUtils.differenceDegrees(purple.hue, devicePrimary.hue) * 0.5, 15.0),
        )
    }
    val secondary = SchemeContent(
        Hct.from(hue, purple.chroma, purple.tone),
        darkTheme,
        0.0,
    ).secondaryPalette

    // Keep each device role's tone so device contrast and light/dark choices survive.
    fun recolor(role: Color): Color =
        Color(secondary.getHct(Hct.fromInt(role.toArgb()).tone).toInt())

    return deviceScheme.copy(
        secondary = recolor(deviceScheme.secondary),
        onSecondary = recolor(deviceScheme.onSecondary),
        secondaryContainer = recolor(deviceScheme.secondaryContainer),
        onSecondaryContainer = recolor(deviceScheme.onSecondaryContainer),
        secondaryFixed = recolor(deviceScheme.secondaryFixed),
        secondaryFixedDim = recolor(deviceScheme.secondaryFixedDim),
        onSecondaryFixed = recolor(deviceScheme.onSecondaryFixed),
        onSecondaryFixedVariant = recolor(deviceScheme.onSecondaryFixedVariant),
    )
}
