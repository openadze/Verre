package io.ymusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

/** Setting key for the Liquid Glass look. Off = every glass surface paints its plain fallback. */
object GlassSettings {
    val ENABLED = BoolSetting("glass_enabled", true)
}

/**
 * What a glass surface refracts: the content captured by [glassSource]. `null` means glass is off here (setting off,
 * or no source wired): every glass surface then paints its fallback, so a call site never has to check.
 *
 * Provide it ONLY to subtrees that are NOT inside the content [glassSource] captures: a surface that refracts a layer
 * it is itself drawn into would recurse. The app has three layers:
 *  - the ambient layer ([AmbientLayer], behind everything, provided to the whole app) — what buttons, chips, cards
 *    and bars inside screens refract;
 *  - the screens layer (App.kt), refracted only by the docked mini-player that overlays the screens;
 *  - the cover gradient (Now Playing), refracted by everything on that screen.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Marks this content as what the glass surfaces drawn above it refract. Pair it with [LocalGlassBackdrop]. */
fun Modifier.glassSource(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/** How glass looks on the current ground: the veil it carries and the two ends of its rim light. */
@Immutable
data class GlassStyle(val tint: Color, val rimLight: Color, val rimDark: Color) {
    companion object {
        val OnLight = GlassStyle(Color(0x66FFFFFF), Color(0xD9FFFFFF), Color(0x24000000))
        val OnDark = GlassStyle(Color(0x14FFFFFF), Color(0x47FFFFFF), Color(0x0FFFFFFF))

        /** Over the cover gradient of Now Playing. */
        val OnCover = GlassStyle(Color(0x26FFFFFF), Color(0x73FFFFFF), Color(0x14FFFFFF))
    }
}

/** Overrides the theme-derived [GlassStyle] (Now Playing sets it for the cover gradient). */
val LocalGlassStyle = staticCompositionLocalOf<GlassStyle?> { null }

@Composable
fun currentGlassStyle(): GlassStyle =
    LocalGlassStyle.current ?: if (DesignColors.isDark) GlassStyle.OnDark else GlassStyle.OnLight

/** The faint white veil glass buttons carry over a cover gradient, so their glyphs stay readable. */
val GlassButtonTint = GlassStyle.OnCover.tint

/** The rim light: bright on the top-left edge, fading toward the bottom-right, like light catching a glass edge. */
private fun Modifier.glassRim(shape: Shape, style: GlassStyle): Modifier =
    this.border(0.75.dp, Brush.linearGradient(listOf(style.rimLight, style.rimDark)), shape)

/**
 * A full Liquid Glass surface: blur + saturation + lens refraction over [LocalGlassBackdrop], optionally [tint]ed
 * and with a [rim] light. For the few big or primary surfaces (mini-player, play button, pinned headers, buttons on
 * the cover gradient); for anything repeated in a list use [glassPanel], which costs no shader.
 *
 * Without a backdrop it is a plain [fallback]-colored fill in the same [shape]. Where the OS cannot blur or run
 * runtime shaders (Android < 12 / < 13) the library skips just that effect, so the surface degrades to a tinted pane.
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
    rim: Boolean = false,
): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: return this.background(fallback, shape)
    val style = currentGlassStyle()
    val glass = this.drawBackdrop(
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
    return if (rim) glass.glassRim(shape, style) else glass
}

/**
 * The light glass look: the theme's veil (over an optional [base] fill) plus the rim light, no backdrop shader — cheap enough for every chip, card,
 * pill and icon button in a list, and over the ambient layer indistinguishable from a blurred pane. With glass off
 * ([LocalGlassBackdrop] null) it paints [fallback].
 */
@Composable
fun Modifier.glassPanel(shape: Shape, fallback: Color = Color.Transparent, base: Color = Color.Transparent): Modifier {
    if (LocalGlassBackdrop.current == null) return if (fallback == Color.Transparent) this else this.background(fallback, shape)
    val style = currentGlassStyle()
    // [base] is the surface's own fill (a card's gray), kept faintly under the veil so the panel still reads on a light ground.
    val filled = if (base == Color.Transparent) this else this.background(base.copy(alpha = 0.7f), shape)
    return filled.background(style.tint, shape).glassRim(shape, style)
}

/**
 * The ambient ground: the app surface with two soft accent glows, drawn once behind the whole app and captured as
 * the backdrop that glass inside screens refracts. Pass [backdrop] from `rememberLayerBackdrop()`.
 */
@Composable
fun AmbientLayer(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    val surface = DesignColors.Surface
    val accent = DesignColors.Accent
    val dark = DesignColors.isDark
    Box(
        modifier
            // The capture first, the painting inside it: only what is drawn after layerBackdrop is recorded.
            .glassSource(backdrop)
            .drawBehind {
                drawRect(surface)
                val strength = if (dark) 0.16f else 0.11f
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = strength), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.08f),
                        radius = size.maxDimension * 0.55f,
                    ),
                    radius = size.maxDimension * 0.55f,
                    center = Offset(size.width * 0.1f, size.height * 0.08f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = strength * 0.7f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.92f),
                        radius = size.maxDimension * 0.5f,
                    ),
                    radius = size.maxDimension * 0.5f,
                    center = Offset(size.width * 0.95f, size.height * 0.92f),
                )
            },
    )
}
