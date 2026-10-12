package com.librivox.mobile.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import com.librivox.mobile.playback.AnimationSpeed
import kotlin.math.roundToInt

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalActiveBookSharedTransitionKey = compositionLocalOf<BookSharedTransitionKey?> { null }
val LocalAnimationSpeed = compositionLocalOf { AnimationSpeed.Default }

data class BookSharedElementSource(
    val route: String,
    val slot: String,
)

enum class BookSharedElementType {
    Container,
}

data class BookSharedTransitionKey(
    val bookId: String,
    val source: BookSharedElementSource,
) {
    fun element(type: BookSharedElementType): BookSharedElementKey =
        BookSharedElementKey(bookId = bookId, source = source, type = type)
}

data class BookSharedElementKey(
    val bookId: String,
    val source: BookSharedElementSource,
    val type: BookSharedElementType,
) {
    val transitionKey: BookSharedTransitionKey
        get() = BookSharedTransitionKey(bookId = bookId, source = source)
}

private const val ContentExitFadeDurationMillis = 120
private const val ContentEnterFadeDurationMillis = 120
private const val ContentEnterFadeDelayMillis = 0

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.bookSharedBounds(
    sharedElementKey: BookSharedElementKey?,
    shape: Shape,
    contentFade: Boolean = false,
    resizeMode: SharedTransitionScope.ResizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val activeSharedElementKey = sharedElementKey ?: return this
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this

    val animationSpeed = LocalAnimationSpeed.current
    val boundsSpec = remember(animationSpeed) {
        spring<Rect>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = bookCoverBoundsStiffness(animationSpeed),
        )
    }
    val exitDurationMs = scaledMotionMillis(ContentExitFadeDurationMillis, animationSpeed)
    val enterDurationMs = scaledMotionMillis(ContentEnterFadeDurationMillis, animationSpeed)
    val enterDelayMs = scaledMotionMillis(ContentEnterFadeDelayMillis, animationSpeed)
    val exitFadeSpec = remember(exitDurationMs) {
        tween<Float>(durationMillis = exitDurationMs)
    }
    val enterFadeSpec = remember(enterDurationMs, enterDelayMs) {
        tween<Float>(durationMillis = enterDurationMs, delayMillis = enterDelayMs)
    }
    val boundsTransform = remember(boundsSpec) {
        BoundsTransform { _: Rect, _: Rect -> boundsSpec }
    }
    return with(sharedTransitionScope) {
        this@bookSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(activeSharedElementKey),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = if (contentFade) fadeIn(animationSpec = enterFadeSpec) else EnterTransition.None,
            exit = if (contentFade) fadeOut(animationSpec = exitFadeSpec) else ExitTransition.None,
            boundsTransform = boundsTransform,
            resizeMode = resizeMode,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

private fun scaledMotionMillis(baseMillis: Int, animationSpeed: AnimationSpeed): Int =
    (baseMillis * animationSpeed.motionScale).roundToInt().coerceAtLeast(0)

private fun bookCoverBoundsStiffness(animationSpeed: AnimationSpeed): Float {
    val scale = animationSpeed.motionScale.coerceAtLeast(0.25f)
    return (Spring.StiffnessHigh / (scale * scale))
        .coerceIn(Spring.StiffnessMediumLow, Spring.StiffnessHigh * 2f)
}
