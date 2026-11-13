package app.anynear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.anynear.app.AppController
import app.anynear.i18n.texts
import app.anynear.images.AvatarPattern
import app.anynear.images.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Circles are angular, people round (ADR 0012): distinguishable at first glance. */
private fun avatarShape(isCircle: Boolean, size: Dp): Shape = if (isCircle) RoundedCornerShape(size * 0.28f) else CircleShape

/**
 * Picture of a circle ([isCircle]) or a person: the uploaded [image]; as long as it is not there
 * (or there is none), the one generated from [seed]. The name is always next to it. With [enlargeable]
 * tapping opens an uploaded picture in full screen, like a photo in the chat.
 */
@Composable
internal fun PersonAvatar(
    controller: AppController,
    seed: String,
    isCircle: Boolean,
    image: ImageRef?,
    size: Dp,
    modifier: Modifier = Modifier,
    enlargeable: Boolean = false,
) {
    val shape = avatarShape(isCircle, size)
    val photo = image?.let { rememberAvatarImage(controller, it) }
    var viewing by remember { mutableStateOf(false) }
    val label = if (isCircle) texts.chat.circlePicture else texts.chat.personPicture
    Box(
        modifier.size(size).clip(shape).border(1.dp, AppTheme.colors.bubbleOutline, shape)
            .then(if (enlargeable && photo != null) Modifier.clickable(onClickLabel = if (isCircle) texts.chat.enlargeCirclePicture else texts.chat.enlargePersonPicture) { viewing = true } else Modifier),
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GeneratedAvatar(seed, isCircle)
        }
    }
    if (viewing && photo != null) PhotoViewer(photo, label) { viewing = false }
}

private object AvatarBitmaps {
    private const val MAX = 64
    private val map = LinkedHashMap<String, ImageBitmap>()

    fun get(id: String): ImageBitmap? = map[id]

    fun put(id: String, image: ImageBitmap) {
        map[id] = image
        if (map.size > MAX) map.remove(map.keys.first())
    }
}

@Composable
private fun rememberAvatarImage(controller: AppController, ref: ImageRef): ImageBitmap? {
    // Try again as soon as a picture is added on the phone.
    val version by controller.avatarVersion.collectAsState()
    var bitmap by remember(ref.id) { mutableStateOf(AvatarBitmaps.get(ref.id)) }
    LaunchedEffect(ref.id, version) {
        if (bitmap != null) return@LaunchedEffect
        val bytes = controller.imageBytes(ref) ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            ?.also { AvatarBitmaps.put(ref.id, it) }
    }
    return bitmap
}

@Composable
private fun GeneratedAvatar(seed: String, isCircle: Boolean) {
    val tones = AppTheme.colors.avatarTones
    val pattern = remember(seed, isCircle, tones.size) {
        if (isCircle) AvatarPattern.circle(seed, tones.size) else AvatarPattern.person(seed, tones.size)
    }
    val tone = tones[pattern.tone]
    Canvas(Modifier.fillMaxSize()) {
        drawRect(tone.background)
        val edge = size.minDimension
        if (isCircle) {
            // Three rings of arc segments around a dot, like the emblem.
            val stroke = edge * 0.075f
            val step = 360f / AvatarPattern.SEGMENTS
            pattern.rings.forEachIndexed { ring, mask ->
                val radius = edge * (0.17f + ring * 0.11f)
                val offset = pattern.ringOffsets[ring] * step / 8f
                for (segment in 0 until AvatarPattern.SEGMENTS) {
                    if ((mask shr segment) and 1 == 0) continue
                    drawArc(
                        color = tone.pattern,
                        startAngle = offset + segment * step + step * 0.12f,
                        sweepAngle = step * 0.76f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            drawCircle(tone.pattern, radius = edge * 0.06f, center = center)
        } else {
            // 5 × 5 cells, mirrored, in the inner square of the circle.
            val grid = AvatarPattern.GRID
            val area = edge * 0.6f
            val cell = area / grid
            val origin = Offset(center.x - area / 2, center.y - area / 2)
            pattern.cells.forEachIndexed { i, on ->
                if (!on) return@forEachIndexed
                val x = origin.x + (i % grid) * cell
                val y = origin.y + (i / grid) * cell
                drawRoundRect(
                    color = tone.pattern,
                    topLeft = Offset(x + cell * 0.08f, y + cell * 0.08f),
                    size = Size(cell * 0.84f, cell * 0.84f),
                    cornerRadius = CornerRadius(cell * 0.22f),
                )
            }
        }
    }
}

/** Picture of a circle. Circles of a support request show the picture of support (ADR 0065). */
@Composable
internal fun CircleAvatar(controller: AppController, circleId: String, image: ImageRef?, size: Dp, modifier: Modifier = Modifier, enlargeable: Boolean = false) {
    val support by controller.supportPeer.collectAsState()
    val members by controller.members.collectAsState()
    val supportPeer = support?.takeIf { remember(it, members, circleId) { controller.isSupportCircle(circleId) } }
    if (supportPeer != null) {
        PersonAvatar(controller, supportPeer, isCircle = true, image = controller.findPerson(supportPeer)?.image, size = size, modifier = modifier, enlargeable = enlargeable)
    } else {
        PersonAvatar(controller, circleId, isCircle = true, image = image, size = size, modifier = modifier, enlargeable = enlargeable)
    }
}

/** Picture of a person; [peer] as in [app.anynear.people.Person.peer]. */
@Composable
internal fun PersonAvatar(controller: AppController, peer: String, image: ImageRef?, size: Dp, modifier: Modifier = Modifier, enlargeable: Boolean = false) =
    PersonAvatar(controller, peer, isCircle = false, image = image, size = size, modifier = modifier, enlargeable = enlargeable)

/** Picks a photo from the gallery; [onPicked] receives the content. Needs no permission. */
@Composable
internal fun rememberPhotoPicker(onError: (String) -> Unit, onPicked: (ByteArray) -> Unit): () -> Unit {
    val picker = rememberAttachmentPicker { _, files ->
        val file = files.firstOrNull() ?: return@rememberAttachmentPicker
        val bytes = file.bytes
        when {
            file.tooLarge -> onError(texts.chat.photoTooLarge)
            bytes == null -> onError(texts.chat.photoCantRead)
            else -> onPicked(bytes)
        }
    }
    return { picker.pickImages(1) }
}
