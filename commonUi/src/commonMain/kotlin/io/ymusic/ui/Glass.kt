package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import io.ymusic.settings.BoolSetting

/** Setting key for the Liquid Glass look. Off = every glass surface paints its plain translucent fallback. */
object GlassSettings {
    val ENABLED = BoolSetting("glass_enabled", true)
}

/**
 * What a glass surface refracts: the content captured by [glassSource]. It is `null` (the default) wherever no
 * source is wired, on a platform or screen that cannot capture it, or when [GlassSettings.ENABLED] is off.
 * Every glass surface then paints its fallback instead, so a call site never has to check.
 *
 * Provide it ONLY to the overlay subtree, never to the content that [glassSource] captures: a surface that
 * refracts a layer it is itself drawn into would recurse.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Marks this content as what the glass surfaces drawn above it refract. Pair it with [LocalGlassBackdrop]. */
fun Modifier.glassSource(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/**
 * A Liquid Glass surface: blur + saturation + lens refraction over [LocalGlassBackdrop], optionally [tint]ed.
 *
 * Without a backdrop it is a plain [fallback]-colored fill in the same [shape] (the "plain translucent
 * fallback": pass the translucent color the surface had before glass). Where the OS cannot blur or run runtime
 * shaders (Android < 12 / < 13) the library skips just that effect, so the surface degrades to a tinted pane.
 *
 * [refract] needs a corner-based [shape] (rounded rectangle, circle, capsule); pass `false` for a plain rectangle
 * such as an edge-to-edge bar.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    fallback: Color,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 6.dp,
    refract: Boolean = true,
): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: return this.background(fallback, shape)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (refract) lens(refractionHeight = 10.dp.toPx(), refractionAmount = 20.dp.toPx())
        },
        onDrawSurface = {
            if (tint.isSpecified) drawRect(tint)
        },
    )
}
