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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.google.android.material.color.utilities.DynamicScheme

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

private val AudioPlayerLightColorScheme = lightColorScheme(
    primary = Color(0xFF006973),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF90F1FF),
    onPrimaryContainer = Color(0xFF001F23),
    inversePrimary = Color(0xFF4ED8E9),
    secondary = Color(0xFF4A6366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE7EB),
    onSecondaryContainer = Color(0xFF051F22),
    tertiary = Color(0xFF515E7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E2FF),
    onTertiaryContainer = Color(0xFF0D1B36),
    background = Color(0xFFFAFDFD),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFAFDFD),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),
    surfaceTint = Color(0xFF006973),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBEC8CA),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFD8DADB),
    surfaceBright = Color(0xFFF8FAFA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F4),
    surfaceContainer = Color(0xFFECEEEE),
    surfaceContainerHigh = Color(0xFFE6E8E9),
    surfaceContainerHighest = Color(0xFFE0E3E3),
    primaryFixed = Color(0xFF90F1FF),
    primaryFixedDim = Color(0xFF4ED8E9),
    onPrimaryFixed = Color(0xFF001F23),
    onPrimaryFixedVariant = Color(0xFF004F57),
    secondaryFixed = Color(0xFFCDE7EB),
    secondaryFixedDim = Color(0xFFB1CBCF),
    onSecondaryFixed = Color(0xFF051F22),
    onSecondaryFixedVariant = Color(0xFF324B4E),
    tertiaryFixed = Color(0xFFD9E2FF),
    tertiaryFixedDim = Color(0xFFB9C6EA),
    onTertiaryFixed = Color(0xFF0D1B36),
    onTertiaryFixedVariant = Color(0xFF3A4664),
)

private val AudioPlayerDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ED8E9),
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF004F57),
    onPrimaryContainer = Color(0xFF90F1FF),
    inversePrimary = Color(0xFF006973),
    secondary = Color(0xFFB1CBCF),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF324B4E),
    onSecondaryContainer = Color(0xFFCDE7EB),
    tertiary = Color(0xFFB9C6EA),
    onTertiary = Color(0xFF23304D),
    tertiaryContainer = Color(0xFF3A4664),
    onTertiaryContainer = Color(0xFFD9E2FF),
    background = Color(0xFF191C1D),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF191C1D),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBEC8CA),
    surfaceTint = Color(0xFF4ED8E9),
    inverseSurface = Color(0xFFE0E3E3),
    inverseOnSurface = Color(0xFF2D3131),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF101414),
    surfaceBright = Color(0xFF363A3A),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF191C1D),
    surfaceContainer = Color(0xFF1D2021),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323536),
    primaryFixed = Color(0xFF90F1FF),
    primaryFixedDim = Color(0xFF4ED8E9),
    onPrimaryFixed = Color(0xFF001F23),
    onPrimaryFixedVariant = Color(0xFF004F57),
    secondaryFixed = Color(0xFFCDE7EB),
    secondaryFixedDim = Color(0xFFB1CBCF),
    onSecondaryFixed = Color(0xFF051F22),
    onSecondaryFixedVariant = Color(0xFF324B4E),
    tertiaryFixed = Color(0xFFD9E2FF),
    tertiaryFixedDim = Color(0xFFB9C6EA),
    onTertiaryFixed = Color(0xFF0D1B36),
    onTertiaryFixedVariant = Color(0xFF3A4664),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> AudioPlayerDarkColorScheme
        else -> AudioPlayerLightColorScheme
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
