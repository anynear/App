package app.anynear.attachments

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIRectFill
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class)
actual fun shrinkImage(raw: ByteArray, maxEdge: Int, quality: Int, rotate: Int): ShrunkImage? {
    // UIImage takes the orientation into account; size is already the right way round.
    val image = UIImage.imageWithData(raw.toNSData()) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    val scale = minOf(1.0, maxEdge / max(w, h))
    val drawWidth = (w * scale).roundToInt().coerceAtLeast(1)
    val drawHeight = (h * scale).roundToInt().coerceAtLeast(1)
    val turns = quarterTurns(rotate)
    // With a quarter turn width and height of the result swap.
    val sideways = turns == 90 || turns == 270
    val width = if (sideways) drawHeight else drawWidth
    val height = if (sideways) drawWidth else drawHeight
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
        setScale(1.0)
        setOpaque(true)
    }
    // Redrawn: the result carries no image data (EXIF, GPS) of the original.
    val rendered = UIGraphicsImageRenderer(CGSizeMake(width.toDouble(), height.toDouble()), format).imageWithActions { _ ->
        // Transparency (PNG) onto white, JPEG has none.
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        val context = UIGraphicsGetCurrentContext()
        if (turns != 0 && context != null) {
            // Rotate around the centre; UIKit counts clockwise because y grows downwards.
            CGContextTranslateCTM(context, width / 2.0, height / 2.0)
            CGContextRotateCTM(context, turns * PI / 180.0)
            CGContextTranslateCTM(context, -drawWidth / 2.0, -drawHeight / 2.0)
        }
        image.drawInRect(CGRectMake(0.0, 0.0, drawWidth.toDouble(), drawHeight.toDouble()))
    }
    val jpeg = UIImageJPEGRepresentation(rendered, quality / 100.0) ?: return null
    return ShrunkImage(jpeg.toByteArray(), width, height)
}
