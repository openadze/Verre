package io.ymusic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Two colors taken from a cover, already tuned to sit under white text: [top] is the vivid one, [bottom] a darker
 * companion. The Now Playing lyrics screen paints a vertical gradient between them (Apple Music's look).
 */
@Immutable
data class CoverPalette(val top: Color, val bottom: Color) {
    companion object {
        /** No cover yet (loading, offline, no art): a calm dark gray. */
        val Neutral = CoverPalette(Color(0xFF45464D), Color(0xFF17171B))
    }
}

private const val HUE_BINS = 12
private const val MAX_SAMPLES = 4096

/**
 * The palette of a cover given as ARGB pixels. Pure (no Compose state) so it is unit-tested:
 * the dominant saturated hue wins (weighted by saturation and brightness, so a big dull area does not beat a small
 * vivid one); near-black, near-white and transparent pixels are ignored; a grayscale cover gives a gray palette.
 */
fun coverPaletteFromPixels(pixels: IntArray): CoverPalette {
    if (pixels.isEmpty()) return CoverPalette.Neutral
    val step = max(1, pixels.size / MAX_SAMPLES)
    val weight = DoubleArray(HUE_BINS)
    val sumR = DoubleArray(HUE_BINS)
    val sumG = DoubleArray(HUE_BINS)
    val sumB = DoubleArray(HUE_BINS)
    var grayLuma = 0.0
    var grayCount = 0
    var i = 0
    while (i < pixels.size) {
        val p = pixels[i]
        i += step
        if ((p ushr 24) < 128) continue
        val r = ((p shr 16) and 0xFF) / 255.0
        val g = ((p shr 8) and 0xFF) / 255.0
        val b = (p and 0xFF) / 255.0
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val v = hi
        val s = if (hi == 0.0) 0.0 else (hi - lo) / hi
        grayLuma += 0.2126 * r + 0.7152 * g + 0.0722 * b
        grayCount++
        if (v < 0.12 || s < 0.14) continue
        val hue = hueOf(r, g, b, hi, lo)
        val bin = ((hue / 360.0) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
        val w = s * (0.35 + v)
        weight[bin] += w
        sumR[bin] += r * w
        sumG[bin] += g * w
        sumB[bin] += b * w
    }
    if (grayCount == 0) return CoverPalette.Neutral
    val total = weight.sum()
    if (total < grayCount * 0.02) {
        // Grayscale (or nearly) cover: a gray gradient from its average brightness.
        val v = (grayLuma / grayCount).toFloat().coerceIn(0.28f, 0.5f)
        return CoverPalette(Color(v, v, v * 1.04f), Color(v * 0.38f, v * 0.38f, v * 0.4f))
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: 0
    val second = weight.indices
        .filter { circularDistance(it, best) >= 2 && weight[it] >= weight[best] * 0.3 }
        .maxByOrNull { weight[it] }
    val main = averageColor(sumR[best], sumG[best], sumB[best], weight[best])
    val accent = second?.let { averageColor(sumR[it], sumG[it], sumB[it], weight[it]) } ?: main
    return CoverPalette(top = tuned(main, 0.55f), bottom = tuned(accent, 0.2f))
}

private fun hueOf(r: Double, g: Double, b: Double, hi: Double, lo: Double): Double {
    val d = hi - lo
    if (d == 0.0) return 0.0
    val h = when (hi) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } * 60.0
    return if (h < 0) h + 360.0 else h
}

private fun circularDistance(a: Int, b: Int): Int = min(abs(a - b), HUE_BINS - abs(a - b))

private fun averageColor(r: Double, g: Double, b: Double, w: Double) =
    Color((r / w).toFloat(), (g / w).toFloat(), (b / w).toFloat())

/** [c] kept at its hue but saturated enough to look alive and no brighter than [value], so white text stays readable. */
private fun tuned(c: Color, value: Float): Color {
    val hi = max(c.red, max(c.green, c.blue)).toDouble()
    val lo = min(c.red, min(c.green, c.blue)).toDouble()
    val hue = hueOf(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble(), hi, lo).toFloat()
    val sat = (if (hi == 0.0) 0.0 else (hi - lo) / hi).toFloat().coerceIn(0.4f, 0.9f)
    return Color.hsv(hue, sat, value)
}

/** The palette of the cover at [url], kept while the next one loads; [CoverPalette.Neutral] without a cover or a loader. */
@Composable
fun rememberCoverPalette(graph: AppGraph, url: String?): CoverPalette {
    val loader = graph.imageLoader
    val palette by produceState(CoverPalette.Neutral, loader, url) {
        if (loader == null || url == null) {
            value = CoverPalette.Neutral
            return@produceState
        }
        val bitmap = loader.loadOrNull(url, 64, 64) ?: return@produceState
        value = withContext(Dispatchers.Default) { bitmap.palette() }
    }
    return palette
}

private fun ImageBitmap.palette(): CoverPalette {
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return CoverPalette.Neutral
    val buffer = IntArray(w * h)
    readPixels(buffer, 0, 0, w, h, 0, w)
    return coverPaletteFromPixels(buffer)
}

/** The cover gradient, cross-fading when the track (and so the palette) changes. */
@Composable
fun CoverGradient(palette: CoverPalette, modifier: Modifier = Modifier) {
    val top by animateColorAsState(palette.top, tween(700))
    val bottom by animateColorAsState(palette.bottom, tween(700))
    Box(modifier.background(Brush.verticalGradient(listOf(top, bottom))))
}

/** True below [CoverTheme]: the surface is the cover gradient, so screens must not paint their own opaque ground. */
val LocalCoverLook = staticCompositionLocalOf { false }

/** The app palette outside any [CoverTheme] — what a dialog or sheet opened from Now Playing must go back to. */
val LocalAppPalette = staticCompositionLocalOf<DesignPalette?> { null }

/** [base] turned into the palette of ink over a cover gradient: white text and strokes, white as the accent. */
fun coverDesignPalette(base: DesignPalette): DesignPalette = DarkDesignColors.copy(
    Accent = Color.White,
    OnAccent = Color(0xFF141414),
    InkStrong = Color.White,
    Ink = Color.White.copy(alpha = 0.95f),
    InkMuted = Color.White.copy(alpha = 0.8f),
    InkSoft = Color.White.copy(alpha = 0.65f),
    InkSecondary = Color.White.copy(alpha = 0.6f),
    InkFaint = Color.White.copy(alpha = 0.45f),
    IconDots = Color.White.copy(alpha = 0.6f),
    Outline = Color.White.copy(alpha = 0.3f),
    OutlineSoft = Color.White.copy(alpha = 0.2f),
    Divider = Color.White.copy(alpha = 0.14f),
    DividerMini = Color.White.copy(alpha = 0.14f),
)

/**
 * Now Playing's look: ink is white, glass is tuned for the gradient, and screens skip their own ground.
 * The Material scheme stays the opaque dark one, so menus and dialogs opened from here keep a readable container.
 */
@Composable
fun CoverTheme(content: @Composable () -> Unit) {
    val appPalette = LocalAppPalette.current ?: DesignColors
    val cover = remember(appPalette) { coverDesignPalette(appPalette) }
    val scheme = remember(cover) { schemeFor(DarkDesignColors) }
    CompositionLocalProvider(
        LocalAppPalette provides appPalette,
        LocalDesignColors provides cover,
        LocalCoverLook provides true,
        LocalGlassStyle provides GlassStyle.OnCover,
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
        }
    }
}

/** Puts the app's own palette back (a dialog is its own window: it must not inherit the cover ink or glass). */
@Composable
fun AppThemeReset(content: @Composable () -> Unit) {
    val app = LocalAppPalette.current
    if (app == null) {
        content()
        return
    }
    val scheme = remember(app) { schemeFor(app) }
    CompositionLocalProvider(
        LocalDesignColors provides app,
        LocalCoverLook provides false,
        LocalGlassStyle provides null,
        LocalGlassBackdrop provides null,
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides app.Ink, content = content)
        }
    }
}

/** A solid ground for a screen's own surface — skipped over the cover gradient, which is the ground there. */
@Composable
fun Modifier.surfaceBackground(color: Color = DesignColors.Surface): Modifier =
    if (LocalCoverLook.current) this else this.background(color)
