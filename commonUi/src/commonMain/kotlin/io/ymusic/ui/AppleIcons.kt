package io.ymusic.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The tab bar glyphs, drawn as Apple Music draws them (SF Symbols' music.note.list, square.grid.2x2,
 * dot.radiowaves.left.and.right, books.vertical, magnifyingglass) instead of the Material icon set,
 * so the bar reads as Apple's at first glance. All five live in a 24×24 viewport, filled with black
 * (like every Material icon) — the tab bar tints them.
 *
 * The geometry (circles, arcs, rounded corners) is emitted as SVG path data for [addPathNodes] — the
 * same API [TelegramIcon] uses — because PathBuilder's node list is not public API.
 */
internal object AppleIcons {

    /** Listen Now: two eighth notes with a beam — SF Symbols' music.note.list. */
    val ListenNow: ImageVector by lazy {
        icon("ListenNow") {
            path(filled = true) {
                // The beam across both stems, slanted upward left → right.
                moveTo(8.9f, 4.4f); lineTo(20.1f, 6.7f); lineTo(20.1f, 9.3f); lineTo(8.9f, 7.0f); close()
                // Stem of the first note, from the beam's low end straight down.
                moveTo(8.9f, 5.4f); lineTo(10.7f, 5.6f); lineTo(10.7f, 17.0f); lineTo(8.9f, 17.0f); close()
                // Stem of the second note, from the beam's high end straight down.
                moveTo(18.3f, 6.9f); lineTo(20.1f, 7.1f); lineTo(20.1f, 18.6f); lineTo(18.3f, 18.6f); close()
                // The two note heads.
                circle(7.5f, 17.0f, 2.85f)
                circle(16.9f, 18.6f, 2.85f)
            }
        }
    }

    /** Browse: a 2×2 grid of rounded squares — square.grid.2x2. */
    val Browse: ImageVector by lazy {
        icon("Browse") {
            path(filled = true) {
                roundedRect(3.2f, 3.2f, 7.2f, 7.2f, 1.9f)
                roundedRect(13.6f, 3.2f, 7.2f, 7.2f, 1.9f)
                roundedRect(3.2f, 13.6f, 7.2f, 7.2f, 1.9f)
                roundedRect(13.6f, 13.6f, 7.2f, 7.2f, 1.9f)
            }
        }
    }

    /** Radio: a dot with two broadcast arcs — dot.radiowaves.left.and.right, drawn opening upward. */
    val Radio: ImageVector by lazy {
        icon("Radio") {
            // The dot is filled; the arcs around it are stroked in a second path.
            path(filled = true) { circle(12.0f, 16.6f, 2.1f) }
            path(filled = false) {
                arc(12.0f, 16.6f, 5.0f, startDeg = 205f, endDeg = 335f)
                arc(12.0f, 16.6f, 8.2f, startDeg = 205f, endDeg = 335f)
            }
        }
    }

    /** Library: three vertical books — books.vertical. */
    val Library: ImageVector by lazy {
        icon("Library") {
            path(filled = true) {
                roundedRect(4.0f, 5.2f, 3.6f, 13.6f, 1.8f)
                roundedRect(10.2f, 5.2f, 3.6f, 13.6f, 1.8f)
                roundedRect(16.4f, 5.2f, 3.6f, 13.6f, 1.8f)
            }
        }
    }

    /** Search: a magnifying glass — magnifyingglass. */
    val Search: ImageVector by lazy {
        icon("Search") {
            path(filled = false) {
                arc(10.8f, 10.8f, 6.5f, startDeg = 90f, endDeg = 450f)
                moveTo(15.5f, 15.5f); lineTo(20.2f, 20.2f)
            }
        }
    }
}

/** One icon: [build] adds paths into a 24×24 viewport; a path is filled or stroked (round caps). */
private fun icon(name: String, build: IconSpec.() -> Unit): ImageVector {
    val spec = IconSpec().apply(build)
    var builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    for ((data, filled) in spec.paths) {
        builder = builder.addPath(
            pathData = addPathNodes(data),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
        )
    }
    return builder.build()
}

/** Collects the icon's subpaths: each entry is its SVG data and whether it fills or strokes. */
private class IconSpec {
    val paths = mutableListOf<Pair<String, Boolean>>()

    fun path(filled: Boolean, build: SvgPath.() -> Unit) {
        paths += SvgPath().apply(build).toString() to filled
    }
}

/** Emits SVG path commands into a string for [addPathNodes]. */
private class SvgPath {
    private val out = StringBuilder()

    fun moveTo(x: Float, y: Float) = cmd("M", x, y)
    fun lineTo(x: Float, y: Float) = cmd("L", x, y)
    fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        out.append("C").append(f(x1)).append(' ').append(f(y1)).append(' ')
            .append(f(x2)).append(' ').append(f(y2)).append(' ').append(f(x)).append(' ').append(f(y)).append(' ')
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        out.append("Q").append(f(cx)).append(' ').append(f(cy)).append(' ').append(f(x)).append(' ').append(f(y)).append(' ')
    }

    fun close() {
        out.append("Z ")
    }

    private fun cmd(op: String, x: Float, y: Float) {
        out.append(op).append(f(x)).append(' ').append(f(y)).append(' ')
    }

    /** A circle as four cubic segments (the standard κ approximation). */
    fun circle(cx: Float, cy: Float, r: Float) {
        val k = r * 0.5523f
        moveTo(cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        close()
    }

    /**
     * An arc (angles in degrees, y growing downward — 90° points down) as cubic segments of at
     * most 90° each, approximated the way SVG renderers do. Starts with its own moveTo, so a
     * previous subpath is left untouched.
     */
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, endDeg: Float) {
        fun radians(deg: Float) = deg * PI / 180.0
        fun x(a: Float) = (cx + r * cos(radians(a))).toFloat()
        fun y(a: Float) = (cy + r * sin(radians(a))).toFloat()
        var a = startDeg
        moveTo(x(a), y(a))
        while (a < endDeg - 0.01f) {
            val b = minOf(a + 90f, endDeg)
            val k = ((4f / 3f) * tan(radians((b - a) / 2f)) * r).toFloat()
            // The control points sit on the tangents at both ends, k away from the endpoints.
            fun tx(deg: Float) = (-sin(radians(deg)) * k).toFloat()
            fun ty(deg: Float) = (cos(radians(deg)) * k).toFloat()
            curveTo(x(a) + tx(a), y(a) + ty(a), x(b) - tx(b), y(b) - ty(b), x(b), y(b))
            a = b
        }
    }

    /** A filled rounded rectangle as a single subpath (corners are quadratic curves). */
    fun roundedRect(left: Float, top: Float, width: Float, height: Float, radius: Float) {
        val right = left + width
        val bottom = top + height
        moveTo(left + radius, top)
        lineTo(right - radius, top)
        quadTo(right, top, right, top + radius)
        lineTo(right, bottom - radius)
        quadTo(right, bottom, right - radius, bottom)
        lineTo(left + radius, bottom)
        quadTo(left, bottom, left, bottom - radius)
        lineTo(left, top + radius)
        quadTo(left, top, left + radius, top)
        close()
    }

    override fun toString(): String = out.toString().trim()
}

private fun f(v: Float): String = v.toString()
