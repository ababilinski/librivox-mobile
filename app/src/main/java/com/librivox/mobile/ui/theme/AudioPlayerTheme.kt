package com.librivox.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.librivox.mobile.colorutilities.DynamicScheme
import com.librivox.mobile.playback.AppColorPalette

/**
 * 2026 Material 3 Expressive shape scale. Adds extraExtraLarge=48dp (new for
 * 2026 hero surfaces) and bumps extraLarge=28dp (was 20dp in stable). Audit
 * screens for hardcoded `20.dp` / `28.dp` corners and replace with
 * [MaterialTheme.shapes].
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 2026 hero typography — oversized title for the player sheet only. Modeled on
 * PixelPlayer's ExpTitleTypography (60sp SemiBold, tightened letter-spacing).
 * The rest of the app keeps the stock M3 type scale.
 */
val ExpHeroTitle: TextStyle = TextStyle(
    fontSize = 56.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.02).em,
    lineHeight = 60.sp,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorPalette: AppColorPalette = AppColorPalette.Default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val deviceScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val colorScheme = remember(colorPalette, darkTheme, deviceScheme) {
        resolveAppColorScheme(colorPalette, darkTheme, deviceScheme)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
    ) {
        CompositionLocalProvider(
            LocalTonalSurfaces provides rememberTonalSurfaces(),
        ) {
            content()
        }
    }
}

internal fun colorSchemeFromDynamicScheme(
    scheme: DynamicScheme,
    darkTheme: Boolean,
): ColorScheme =
    if (darkTheme) {
        darkColorSchemeFromDynamicScheme(scheme)
    } else {
        lightColorSchemeFromDynamicScheme(scheme)
    }

private fun lightColorSchemeFromDynamicScheme(scheme: DynamicScheme): ColorScheme =
    lightColorScheme(
        primary = scheme.getPrimary().toComposeColor(),
        onPrimary = scheme.getOnPrimary().toComposeColor(),
        primaryContainer = scheme.getPrimaryContainer().toComposeColor(),
        onPrimaryContainer = scheme.getOnPrimaryContainer().toComposeColor(),
        inversePrimary = scheme.getInversePrimary().toComposeColor(),
        secondary = scheme.getSecondary().toComposeColor(),
        onSecondary = scheme.getOnSecondary().toComposeColor(),
        secondaryContainer = scheme.getSecondaryContainer().toComposeColor(),
        onSecondaryContainer = scheme.getOnSecondaryContainer().toComposeColor(),
        tertiary = scheme.getTertiary().toComposeColor(),
        onTertiary = scheme.getOnTertiary().toComposeColor(),
        tertiaryContainer = scheme.getTertiaryContainer().toComposeColor(),
        onTertiaryContainer = scheme.getOnTertiaryContainer().toComposeColor(),
        background = scheme.getBackground().toComposeColor(),
        onBackground = scheme.getOnBackground().toComposeColor(),
        surface = scheme.getSurface().toComposeColor(),
        onSurface = scheme.getOnSurface().toComposeColor(),
        surfaceVariant = scheme.getSurfaceVariant().toComposeColor(),
        onSurfaceVariant = scheme.getOnSurfaceVariant().toComposeColor(),
        surfaceTint = scheme.getSurfaceTint().toComposeColor(),
        inverseSurface = scheme.getInverseSurface().toComposeColor(),
        inverseOnSurface = scheme.getInverseOnSurface().toComposeColor(),
        error = scheme.getError().toComposeColor(),
        onError = scheme.getOnError().toComposeColor(),
        errorContainer = scheme.getErrorContainer().toComposeColor(),
        onErrorContainer = scheme.getOnErrorContainer().toComposeColor(),
        outline = scheme.getOutline().toComposeColor(),
        outlineVariant = scheme.getOutlineVariant().toComposeColor(),
        scrim = scheme.getScrim().toComposeColor(),
        surfaceDim = scheme.getSurfaceDim().toComposeColor(),
        surfaceBright = scheme.getSurfaceBright().toComposeColor(),
        surfaceContainerLowest = scheme.getSurfaceContainerLowest().toComposeColor(),
        surfaceContainerLow = scheme.getSurfaceContainerLow().toComposeColor(),
        surfaceContainer = scheme.getSurfaceContainer().toComposeColor(),
        surfaceContainerHigh = scheme.getSurfaceContainerHigh().toComposeColor(),
        surfaceContainerHighest = scheme.getSurfaceContainerHighest().toComposeColor(),
        primaryFixed = scheme.getPrimaryFixed().toComposeColor(),
        primaryFixedDim = scheme.getPrimaryFixedDim().toComposeColor(),
        onPrimaryFixed = scheme.getOnPrimaryFixed().toComposeColor(),
        onPrimaryFixedVariant = scheme.getOnPrimaryFixedVariant().toComposeColor(),
        secondaryFixed = scheme.getSecondaryFixed().toComposeColor(),
        secondaryFixedDim = scheme.getSecondaryFixedDim().toComposeColor(),
        onSecondaryFixed = scheme.getOnSecondaryFixed().toComposeColor(),
        onSecondaryFixedVariant = scheme.getOnSecondaryFixedVariant().toComposeColor(),
        tertiaryFixed = scheme.getTertiaryFixed().toComposeColor(),
        tertiaryFixedDim = scheme.getTertiaryFixedDim().toComposeColor(),
        onTertiaryFixed = scheme.getOnTertiaryFixed().toComposeColor(),
        onTertiaryFixedVariant = scheme.getOnTertiaryFixedVariant().toComposeColor(),
    )

private fun darkColorSchemeFromDynamicScheme(scheme: DynamicScheme): ColorScheme =
    darkColorScheme(
        primary = scheme.getPrimary().toComposeColor(),
        onPrimary = scheme.getOnPrimary().toComposeColor(),
        primaryContainer = scheme.getPrimaryContainer().toComposeColor(),
        onPrimaryContainer = scheme.getOnPrimaryContainer().toComposeColor(),
        inversePrimary = scheme.getInversePrimary().toComposeColor(),
        secondary = scheme.getSecondary().toComposeColor(),
        onSecondary = scheme.getOnSecondary().toComposeColor(),
        secondaryContainer = scheme.getSecondaryContainer().toComposeColor(),
        onSecondaryContainer = scheme.getOnSecondaryContainer().toComposeColor(),
        tertiary = scheme.getTertiary().toComposeColor(),
        onTertiary = scheme.getOnTertiary().toComposeColor(),
        tertiaryContainer = scheme.getTertiaryContainer().toComposeColor(),
        onTertiaryContainer = scheme.getOnTertiaryContainer().toComposeColor(),
        background = scheme.getBackground().toComposeColor(),
        onBackground = scheme.getOnBackground().toComposeColor(),
        surface = scheme.getSurface().toComposeColor(),
        onSurface = scheme.getOnSurface().toComposeColor(),
        surfaceVariant = scheme.getSurfaceVariant().toComposeColor(),
        onSurfaceVariant = scheme.getOnSurfaceVariant().toComposeColor(),
        surfaceTint = scheme.getSurfaceTint().toComposeColor(),
        inverseSurface = scheme.getInverseSurface().toComposeColor(),
        inverseOnSurface = scheme.getInverseOnSurface().toComposeColor(),
        error = scheme.getError().toComposeColor(),
        onError = scheme.getOnError().toComposeColor(),
        errorContainer = scheme.getErrorContainer().toComposeColor(),
        onErrorContainer = scheme.getOnErrorContainer().toComposeColor(),
        outline = scheme.getOutline().toComposeColor(),
        outlineVariant = scheme.getOutlineVariant().toComposeColor(),
        scrim = scheme.getScrim().toComposeColor(),
        surfaceDim = scheme.getSurfaceDim().toComposeColor(),
        surfaceBright = scheme.getSurfaceBright().toComposeColor(),
        surfaceContainerLowest = scheme.getSurfaceContainerLowest().toComposeColor(),
        surfaceContainerLow = scheme.getSurfaceContainerLow().toComposeColor(),
        surfaceContainer = scheme.getSurfaceContainer().toComposeColor(),
        surfaceContainerHigh = scheme.getSurfaceContainerHigh().toComposeColor(),
        surfaceContainerHighest = scheme.getSurfaceContainerHighest().toComposeColor(),
        primaryFixed = scheme.getPrimaryFixed().toComposeColor(),
        primaryFixedDim = scheme.getPrimaryFixedDim().toComposeColor(),
        onPrimaryFixed = scheme.getOnPrimaryFixed().toComposeColor(),
        onPrimaryFixedVariant = scheme.getOnPrimaryFixedVariant().toComposeColor(),
        secondaryFixed = scheme.getSecondaryFixed().toComposeColor(),
        secondaryFixedDim = scheme.getSecondaryFixedDim().toComposeColor(),
        onSecondaryFixed = scheme.getOnSecondaryFixed().toComposeColor(),
        onSecondaryFixedVariant = scheme.getOnSecondaryFixedVariant().toComposeColor(),
        tertiaryFixed = scheme.getTertiaryFixed().toComposeColor(),
        tertiaryFixedDim = scheme.getTertiaryFixedDim().toComposeColor(),
        onTertiaryFixed = scheme.getOnTertiaryFixed().toComposeColor(),
        onTertiaryFixedVariant = scheme.getOnTertiaryFixedVariant().toComposeColor(),
    )

private fun Int.toComposeColor(): Color = Color(this)

/**
 * Scoped expressive theme for the player sheet — wraps a child composition
 * in a nested MaterialExpressiveTheme whose ColorScheme is derived from the
 * current album art. The rest of the app stays on Dynamic Color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSheetTheme(
    scheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
    ) {
        CompositionLocalProvider(
            LocalTonalSurfaces provides rememberTonalSurfaces(),
        ) {
            content()
        }
    }
}
