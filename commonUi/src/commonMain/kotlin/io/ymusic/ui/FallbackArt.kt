package io.ymusic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Deep enough that the white note reads on each, in light and dark themes alike. */
private val FALLBACK_ART_PALETTE = longArrayOf(
    0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF4527A0, 0xFF283593, 0xFF1565C0, 0xFF0277BD, 0xFF00838F,
    0xFF00695C, 0xFF2E7D32, 0xFF558B2F, 0xFF827717, 0xFFE65100, 0xFFD84315, 0xFF4E342E, 0xFF37474F,
)

/**
 * The art of a local item that has none, or whose every art source failed: [key]'s flat colour
 * under a music note, drawn at [width]×[height]. The `art:` fetcher returns it; [key] is the art url.
 */
fun fallbackArtBitmap(key: String, width: Int, height: Int): ImageBitmap {
    val color = Color(FALLBACK_ART_PALETTE[key.hashCode().mod(FALLBACK_ART_PALETTE.size)])
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawRect(color)
        val side = size.minDimension * 0.6f
        translate(left = (size.width - side) / 2f, top = (size.height - side) / 2f) {
            scale(side / VerreIcons.MusicNote.viewportWidth, pivot = Offset.Zero) {
                drawPath(musicNotePath(), Color.White.copy(alpha = 0.85f))
            }
        }
    }
    return bitmap
}

/** A fresh path each call: fetches draw on several worker threads at once. */
private fun musicNotePath(): Path {
    val path = Path()
    fun add(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorPath -> path.addPath(node.pathData.toPath())
                is VectorGroup -> add(node)
            }
        }
    }
    add(VerreIcons.MusicNote.root)
    return path
}
