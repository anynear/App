package app.anynear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anynear.alerts.AlertPriority
import app.anynear.i18n.texts
import app.anynear.location.MapPack
import app.anynear.location.Outlines
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Someone or something on the map: a point, or with [radiusMeters] an area that is never drawn as a point. */
internal data class MapMarker(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int? = null,
    val own: Boolean = false,
    /** Older than an hour: drawn pale. */
    val stale: Boolean = false,
    /** A place, not a person (meeting point). */
    val place: Boolean = false,
    /** Whose picture the marker carries; the own one for [own]. */
    val image: app.anynear.images.ImageRef? = null,
)

/** A district with an official warning: its key prefix and the highest priority (ADR 0121 §7). */
internal data class MapWarning(val prefix: String, val priority: AlertPriority)

/** Where the map looks: the centre in Mercator units (0…1) and how many pixels the whole world is wide. */
@Stable
/** The map of the app as the person left it (20.11.2026): coming back from another page it is where it was, not at the start. */
internal object MapMemory {
    var camera: MapCamera? = null
}

internal class MapCamera {
    var x by mutableStateOf((Outlines.GERMANY[0] + Outlines.GERMANY[2]) / 2.0)
    var y by mutableStateOf((Outlines.GERMANY[1] + Outlines.GERMANY[3]) / 2.0)
    var scale by mutableStateOf(0.0)

    /** A place (latitude, longitude) to show when the map appears, instead of the whole country. */
    var start: Pair<Double, Double>? = null

    /** Set once the size of the map is known. */
    var width = 0f
    var height = 0f

    /** Metres per pixel at the centre. */
    val metresPerPixel: Double get() = if (scale <= 0) 0.0 else EARTH * cos(Outlines.latitude(y) * PI / 180) / scale

    /** Shows the box (Mercator units) with some room around it. */
    fun fit(minX: Double, minY: Double, maxX: Double, maxY: Double) {
        if (width <= 0f || height <= 0f) return
        x = (minX + maxX) / 2
        y = (minY + maxY) / 2
        val w = max(maxX - minX, MIN_SPAN)
        val h = max(maxY - minY, MIN_SPAN)
        scale = min(width / w, height / h) * 0.8
        clamp()
    }

    fun fitGermany() = fit(Outlines.GERMANY[0].toDouble(), Outlines.GERMANY[1].toDouble(), Outlines.GERMANY[2].toDouble(), Outlines.GERMANY[3].toDouble())

    /** Shows the places (latitude, longitude) with room around them; a single one at the scale of a town. */
    fun fitPlaces(places: List<Pair<Double, Double>>) {
        if (places.isEmpty()) return fitGermany()
        val xs = places.map { Outlines.x(it.second) }
        val ys = places.map { Outlines.y(it.first) }
        fit(xs.min(), ys.min(), xs.max(), ys.max())
    }

    fun zoom(factor: Float, around: Offset? = null) {
        val before = scale
        scale *= factor
        clamp()
        val f = scale / before
        if (around != null && f != 1.0) {
            // Keep the point under the fingers where it is.
            x += (around.x - width / 2) / before * (1 - 1 / f)
            y += (around.y - height / 2) / before * (1 - 1 / f)
        }
    }

    /** Where a place is on the screen. */
    fun project(latitude: Double, longitude: Double): Offset = Offset(
        ((Outlines.x(longitude) - x) * scale + width / 2).toFloat(),
        ((Outlines.y(latitude) - y) * scale + height / 2).toFloat(),
    )

    /** The place (latitude, longitude) at a point of the screen, the reverse of [project]. */
    fun placeAt(at: Offset): Pair<Double, Double> = Outlines.latitude(y + (at.y - height / 2) / scale) to Outlines.longitude(x + (at.x - width / 2) / scale)

    fun pan(by: Offset) {
        if (scale <= 0) return
        x -= by.x / scale
        y -= by.y / scale
    }

    private fun clamp() {
        if (width <= 0f) return
        // From the whole world on the screen down to a few metres per pixel.
        scale = scale.coerceIn(width.toDouble(), MAX_SCALE)
        x = x.coerceIn(0.0, 1.0)
        y = y.coerceIn(0.12, 0.88)
    }

    companion object {
        const val EARTH = 40_075_016.686

        /** Down to about 30 cm per pixel (20.11.2026): close enough to read the names of streets. */
        private const val MAX_SCALE = 8.0e7

        /** A single place is shown about 12 km wide. */
        private const val MIN_SPAN = 0.0003
    }
}

/**
 * The map (ADR 0121 §1, §7, §11): municipalities and districts from the app itself, official warnings as coloured
 * districts, the own people as areas or points. Drag moves it, two fingers or a double tap zoom, a tap on a marker
 * calls [onMarker].
 */
@Composable
internal fun MapView(
    outlines: Outlines,
    camera: MapCamera,
    markers: List<MapMarker>,
    warnings: List<MapWarning>,
    kreisNames: Map<String, String>,
    modifier: Modifier = Modifier,
    /** Tiles of the detailed map in view (ADR 0121 §11), region and near ones. */
    packs: Collection<MapPack> = emptyList(),
    /** The maps that ship with the app ([MapPack.bundled]): the world as the ground, Europe and Germany for far out. */
    ground: List<MapPack> = emptyList(),
    /** `false` for a small picture of the map, e.g. in a chat: it does not move and takes no taps. */
    interactive: Boolean = true,
    onMarker: (String) -> Unit = {},
    /**
     * Any tap, with the marker hit (or `null`) and the place under the finger; when set, it is called instead of
     * [onMarker], so that the page can decide between a person and the warning area below (ADR 0121 §7).
     */
    onTap: ((marker: String?, at: Offset, latitude: Double, longitude: Double) -> Unit)? = null,
    onLongPress: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
) {
    val measurer = rememberTextMeasurer()
    val german = texts.language == app.anynear.i18n.Language.GERMAN
    val scheme = MaterialTheme.colorScheme
    val colors = AppTheme.colors
    val description = texts.map.title
    // The gesture lives longer than one composition: always the newest callback.
    val currentTap = androidx.compose.runtime.rememberUpdatedState(onTap)
    val hit = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    // Drawing the whole map is too much for a frame, on a weak tablet it takes seconds (19.11.2026). So it is drawn
    // into a picture in the background, a bit wider than the view; the page stays free meanwhile and only shifts and
    // scales the last picture. A new one is drawn when the map rests or when what it shows changed.
    var picture by androidx.compose.runtime.remember { mutableStateOf<MapPicture?>(null) }
    var drawing by androidx.compose.runtime.remember { mutableStateOf(false) }
    var sized by androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val alarm = colors.alarm
    val content = androidx.compose.runtime.rememberUpdatedState(MapContent(outlines, packs.toList(), ground, markers, warnings, kreisNames, scheme, alarm, german))
    androidx.compose.runtime.LaunchedEffect(camera) {
        androidx.compose.runtime.snapshotFlow { MapWanted(content.value, camera.x, camera.y, camera.scale, sized.width.toFloat(), sized.height.toFloat()) }.collectLatest { want ->
            if (want.scale <= 0 || want.width <= 0f || want.height <= 0f) return@collectLatest
            // Shown from the first moment the picture is out of date, so that a jump of the map never looks like nothing happens.
            drawing = true
            try {
                // Not for every step of a gesture; the very first picture at once.
                if (picture != null) kotlinx.coroutines.delay(MAP_REST_MILLIS)
                val margin = minOf(MAP_MARGIN, (MAX_TEXTURE / maxOf(want.width, want.height) - 1f) / 2f).coerceAtLeast(0f)
                val next = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { renderMap(want, margin, measurer, density, direction) }
                picture = next
            } finally {
                drawing = false
            }
        }
    }
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize().onSizeChanged { now ->
            // Here and not while drawing: the picture is drawn for this size, also after the page was turned.
            val first = camera.scale <= 0
            camera.width = now.width.toFloat()
            camera.height = now.height.toFloat()
            if (first && now.width > 0 && now.height > 0) camera.start?.let { camera.fitPlaces(listOf(it)) } ?: camera.fitGermany()
            sized = now
        },
    ) {
    Canvas(
        Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = description }
            .then(
                if (!interactive) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            camera.zoom(zoom, centroid)
                            camera.pan(pan)
                        }
                    }
                    .pointerInput(markers) {
                        fun markerAt(at: Offset): MapMarker? = markers.minByOrNull { m -> (project(camera, m.latitude, m.longitude) - at).getDistance() }
                            ?.takeIf { m -> (project(camera, m.latitude, m.longitude) - at).getDistance() <= max(hit, (m.radiusMeters ?: 0) / camera.metresPerPixel.toFloat()) }
                        detectTapGestures(
                            onDoubleTap = { camera.zoom(2f, it) },
                            onTap = { at ->
                                val m = markerAt(at)
                                val tap = currentTap.value
                                if (tap != null) camera.placeAt(at).let { (lat, lon) -> tap(m?.id, at, lat, lon) } else m?.let { onMarker(it.id) }
                            },
                            onLongPress = { at -> camera.placeAt(at).let { (lat, lon) -> onLongPress(lat, lon) } },
                        )
                    },
            ),
    ) {
        if (camera.width != size.width || camera.height != size.height) {
            val first = camera.scale <= 0
            camera.width = size.width
            camera.height = size.height
            if (first) camera.start?.let { camera.fitPlaces(listOf(it)) } ?: camera.fitGermany()
        }
        val world = ground.firstOrNull { it.key == MapPack.WORLD }
        // The sea a breath of blue, the land in the light tone of the app; without the world map everything is land.
        val land = androidx.compose.ui.graphics.lerp(scheme.surface, scheme.surfaceVariant, 0.4f)
        drawRect(if (world != null) androidx.compose.ui.graphics.lerp(scheme.surface, BrandPalette.Abendblau, 0.14f) else land)
        if (camera.scale <= 0) return@Canvas
        val shown = picture ?: return@Canvas
        val k = (camera.scale / shown.scale).toFloat()
        val dx = ((shown.x - camera.x) * camera.scale).toFloat()
        val dy = ((shown.y - camera.y) * camera.scale).toFloat()
        // The centre of the picture is the centre of the view it was drawn for.
        val left = (size.width - shown.bitmap.width) / 2f
        val top = (size.height - shown.bitmap.height) / 2f
        drawContext.transform.translate(dx, dy)
        drawContext.transform.scale(k, k, center)
        drawImage(shown.bitmap, topLeft = Offset(left, top))
        drawContext.transform.scale(1 / k, 1 / k, center)
        drawContext.transform.translate(-dx, -dy)
    }
    // The picture is being drawn (20.11.2026): a small circle, so that one sees the map is coming.
    if (drawing && interactive) {
        androidx.compose.foundation.layout.Box(
            Modifier.align(Alignment.TopStart).padding(12.dp).size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(scheme.surface.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
    }
    }
}

/** What the map shows, apart from where it looks. */
private data class MapContent(
    val outlines: Outlines, val packs: List<MapPack>, val ground: List<MapPack>, val markers: List<MapMarker>, val warnings: List<MapWarning>,
    val kreisNames: Map<String, String>, val scheme: androidx.compose.material3.ColorScheme, val alarm: Color, val german: Boolean,
)

/** A picture of the map to draw: what and from where. */
private data class MapWanted(val content: MapContent, val x: Double, val y: Double, val scale: Double, val width: Float, val height: Float)

/** A drawn picture of the map and the camera it was drawn for. */
private class MapPicture(val bitmap: androidx.compose.ui.graphics.ImageBitmap, val x: Double, val y: Double, val scale: Double)

/** Draws the map for [want] into a picture, wider than the view by [margin] on every side. Runs off the main thread. */
private fun renderMap(want: MapWanted, margin: Float, measurer: TextMeasurer, density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection): MapPicture {
    val w = (want.width * (1 + 2 * margin)).toInt().coerceAtLeast(1)
    val h = (want.height * (1 + 2 * margin)).toInt().coerceAtLeast(1)
    val bitmap = androidx.compose.ui.graphics.ImageBitmap(w, h)
    // A camera of its own, frozen: the one of the page moves on while this is drawn.
    val camera = MapCamera().apply {
        x = want.x
        y = want.y
        scale = want.scale
        width = w.toFloat()
        height = h.toFloat()
    }
    val c = want.content
    val outlines = c.outlines
    val packs = c.packs
    val ground = c.ground
    val markers = c.markers
    val warnings = c.warnings
    val kreisNames = c.kreisNames
    val scheme = c.scheme
    val german = c.german
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(density, direction, androidx.compose.ui.graphics.Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
        val world = ground.firstOrNull { it.key == MapPack.WORLD }
        val land = androidx.compose.ui.graphics.lerp(scheme.surface, scheme.surfaceVariant, 0.4f)
        val view = Rect(
            (camera.x - size.width / 2 / camera.scale).toFloat(), (camera.y - size.height / 2 / camera.scale).toFloat(),
            (camera.x + size.width / 2 / camera.scale).toFloat(), (camera.y + size.height / 2 / camera.scale).toFloat(),
        )
        val mpp = camera.metresPerPixel
        val inView = packs.filter { it.minX < view.right && it.maxX > view.left && it.minY < view.bottom && it.maxY > view.top }
        val region = inView.filter { it.level == MapPack.REGION && mpp < PACK_BELOW }
        val near = inView.filter { it.level == MapPack.NEAR && mpp < NEAR_BELOW }
        // Far out, and where no region tile is there, the maps of the app carry roads, rivers and cities.
        val far = if (mpp < BASE_BELOW && (mpp >= 60 || region.isEmpty())) ground.filter { it.key != MapPack.WORLD } else emptyList()
        drawTiles(listOfNotNull(world) + far + region + near, camera, view, mpp, scheme.surface, scheme.onSurfaceVariant, land)
        drawWarnings(outlines, camera, view, mpp, warnings, c.alarm, c.alarm)
        if (mpp < GERMAN_BORDERS_BELOW) drawBorders(outlines, camera, view, mpp, scheme.onSurfaceVariant.copy(alpha = if (region.isEmpty()) 0.8f else 0.4f), quiet = far.isNotEmpty())
        // All names of all maps in view compete at once, by rank (MapLabels): a city wins over its quarters, whatever
        // tile either comes from.
        drawLabels(far + region + near + listOfNotNull(world), camera, view, mpp * density.density, measurer, scheme.onSurface, scheme.onSurfaceVariant, scheme.surface, german)
        // The names of districts and municipalities only where nothing better is there.
        if (ground.isEmpty() && region.isEmpty()) drawNames(outlines, camera, view, mpp, kreisNames, measurer, scheme.onSurfaceVariant)
        drawAreas(camera, mpp, markers, measurer, BrandPalette.Signalorange, scheme.onSurface)
    }
    return MapPicture(bitmap, want.x, want.y, want.scale)
}

/** The map counts as resting after this long without a move; then it is drawn anew. */
private const val MAP_REST_MILLIS = 140L

/** How far beyond the screen the layer is drawn, as a share of the screen; moved further, it is drawn anew. */
private const val MAP_MARGIN = 0.4f

/** No side of the picture longer than this: graphics chips take textures up to 4096 pixels. */
private const val MAX_TEXTURE = 4000f

private fun project(camera: MapCamera, latitude: Double, longitude: Double): Offset = Offset(
    ((Outlines.x(longitude) - camera.x) * camera.scale + camera.width / 2).toFloat(),
    ((Outlines.y(latitude) - camera.y) * camera.scale + camera.height / 2).toFloat(),
)

private fun DrawScope.sx(camera: MapCamera, x: Float): Float = ((x - camera.x) * camera.scale + size.width / 2).toFloat()

private fun DrawScope.sy(camera: MapCamera, y: Float): Float = ((y - camera.y) * camera.scale + size.height / 2).toFloat()

/** How many points of a border piece are skipped when a pixel is [mpp] metres: far out the pieces need no detail. */
private fun strideFor(mpp: Double): Int = when {
    mpp > 2400 -> 8
    mpp > 1200 -> 4
    mpp > 500 -> 2
    else -> 1
}

private fun DrawScope.drawBorders(o: Outlines, camera: MapCamera, view: Rect, mpp: Double, line: Color, quiet: Boolean = false) {
    val towns = mpp < TOWN_BORDERS_BELOW
    val stride = strideFor(mpp)
    // Single segments instead of one path: a path that reaches far beyond the screen is not drawn by every device.
    val kreise = ArrayList<Offset>()
    val small = ArrayList<Offset>()
    // With the map of the country below, the outline of the country stands and the districts step back.
    val outer = ArrayList<Offset>()
    for (i in o.arcs.indices) {
        val kreis = o.isKreisBorder(i)
        if (!kreis && !towns) continue
        if (o.boxes[4 * i] > view.right || o.boxes[4 * i + 2] < view.left || o.boxes[4 * i + 1] > view.bottom || o.boxes[4 * i + 3] < view.top) continue
        val a = o.arcs[i]
        val n = a.size / 2
        val into = if (quiet && o.isOuter(i)) outer else if (kreis) kreise else small
        var last = Offset(sx(camera, a[0]), sy(camera, a[1]))
        var k = stride
        while (k < n - 1) {
            val next = Offset(sx(camera, a[2 * k]), sy(camera, a[2 * k + 1]))
            into += last
            into += next
            last = next
            k += stride
        }
        into += last
        into += Offset(sx(camera, a[2 * n - 2]), sy(camera, a[2 * n - 1]))
    }
    if (towns) drawPoints(small, PointMode.Lines, line.copy(alpha = line.alpha * 0.3f), 1.dp.toPx(), StrokeCap.Butt)
    drawPoints(kreise, PointMode.Lines, line.copy(alpha = line.alpha * (if (quiet) 0.22f else 0.7f)), (if (towns) 1.8f else 1.2f).dp.toPx(), StrokeCap.Butt)
    if (outer.isNotEmpty()) drawPoints(outer, PointMode.Lines, line.copy(alpha = line.alpha * 0.8f), 1.4.dp.toPx(), StrokeCap.Butt)
}

private fun DrawScope.drawWarnings(o: Outlines, camera: MapCamera, view: Rect, mpp: Double, warnings: List<MapWarning>, alarm: Color, warning: Color) {
    if (warnings.isEmpty()) return
    val stride = strideFor(mpp)
    val byKreis = HashMap<String, AlertPriority>()
    for ((kreis, _) in o.kreisBoxes) {
        val worst = warnings.filter { kreis.startsWith(it.prefix) || it.prefix.startsWith(kreis) }.maxOfOrNull { it.priority } ?: continue
        byKreis[kreis] = worst
    }
    for ((kreis, priority) in byKreis) {
        val box = o.kreisBoxes[kreis] ?: continue
        if (box[0] > view.right || box[2] < view.left || box[1] > view.bottom || box[3] < view.top) continue
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        for (town in o.byKreis[kreis].orEmpty()) {
            for (ring in town.rings) {
                var first = true
                o.ring(ring, stride) { x, y ->
                    if (first) path.moveTo(sx(camera, x), sy(camera, y)) else path.lineTo(sx(camera, x), sy(camera, y))
                    first = false
                }
                path.close()
            }
        }
        drawPath(path, (if (priority == AlertPriority.Alarm) alarm else warning).copy(alpha = if (priority == AlertPriority.Info) 0.12f else 0.28f))
    }
}

private fun DrawScope.drawNames(o: Outlines, camera: MapCamera, view: Rect, mpp: Double, kreisNames: Map<String, String>, measurer: TextMeasurer, color: Color) {
    val taken = ArrayList<Rect>()
    fun put(text: String, x: Float, y: Float, style: TextStyle) {
        val layout = measurer.measure(text, style, maxLines = 1)
        val at = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f)
        val box = Rect(at, Size(layout.size.width.toFloat(), layout.size.height.toFloat())).inflate(4.dp.toPx())
        if (box.left < 0 || box.top < 0 || box.right > size.width || box.bottom > size.height || taken.any { it.overlaps(box) }) return
        taken += box
        drawText(layout, color, at)
    }
    if (mpp in TOWN_NAMES_BELOW..KREIS_NAMES_BELOW) {
        val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
        for ((kreis, box) in o.kreisBoxes) {
            if (box[0] > view.right || box[2] < view.left || box[1] > view.bottom || box[3] < view.top) continue
            put(kreisNames[kreis]?.removePrefix("Landkreis ") ?: continue, sx(camera, (box[0] + box[2]) / 2), sy(camera, (box[1] + box[3]) / 2), style)
        }
    } else if (mpp < TOWN_NAMES_BELOW) {
        val style = TextStyle(fontSize = 12.sp)
        // Larger municipalities first, so that their names win the room.
        o.towns.asSequence()
            .filter { it.centerX >= view.left && it.centerX <= view.right && it.centerY >= view.top && it.centerY <= view.bottom }
            .sortedByDescending { (it.maxX - it.minX) * (it.maxY - it.minY) }
            .forEach { put(it.name, sx(camera, it.centerX), sy(camera, it.centerY), style) }
    }
}

/** Areas and meeting points; the people themselves are pictures above the map ([MapMarkers]). */
private fun DrawScope.drawAreas(camera: MapCamera, mpp: Double, markers: List<MapMarker>, measurer: TextMeasurer, own: Color, other: Color) {
    val dot = 15.dp.toPx()
    for (m in markers) {
        val at = project(camera, m.latitude, m.longitude)
        if (at.x < -400 || at.y < -400 || at.x > size.width + 400 || at.y > size.height + 400) continue
        val fill = (if (m.own || m.place) own else other).copy(alpha = if (m.stale) 0.45f else 1f)
        if (m.radiusMeters != null) {
            // An area: the honeycomb cell the person is somewhere in, never a pin (ADR 0121 §3, §13). A dashed edge:
            // somewhere in here (21.11.2026).
            val path = Path()
            app.anynear.location.PlaceSharer.areaCorners(m.latitude, m.longitude, m.radiusMeters).forEachIndexed { i, (lat, lon) ->
                val c = project(camera, lat, lon)
                if (i == 0) path.moveTo(c.x, c.y) else path.lineTo(c.x, c.y)
            }
            path.close()
            drawPath(path, fill.copy(alpha = fill.alpha * 0.14f))
            drawPath(path, fill.copy(alpha = fill.alpha * 0.65f), style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        } else if (m.place) {
            drawCircle(fill.copy(alpha = 0.25f), dot * 1.5f, at)
            drawCircle(fill, dot * 0.55f, at)
            label(measurer, m.label, Offset(at.x, at.y + dot * 1.9f), other, bold = true)
        }
    }
}

/** Road classes: from how many metres per pixel on they show, and how wide in dp. */
private fun roadFrom(kind: Int): Double = when (kind) {
    MapPack.MOTORWAY -> PACK_BELOW
    MapPack.MAIN -> 60.0
    MapPack.RAIL -> 25.0
    MapPack.SIDE -> 12.0
    MapPack.STREAM -> 15.0
    MapPack.STREET -> 6.0
    else -> 1.8
}

private fun tone0(ground: Color, edge: Color, share: Float) = androidx.compose.ui.graphics.lerp(ground, edge, share)

private fun roadWidth(kind: Int, mpp: Double): Float {
    val close = if (mpp < 0.7) 4.2f else if (mpp < 1.4) 2.8f else if (mpp < 3) 1.6f else if (mpp < 9) 1.2f else if (mpp < 18) 0.7f else if (mpp < 40) 0.55f else 0.45f
    return close * when (kind) {
        MapPack.MOTORWAY -> 4f
        MapPack.MAIN -> 3.2f
        MapPack.SIDE -> 2.6f
        MapPack.STREET -> 2f
        MapPack.RAIL -> 1.2f
        MapPack.STREAM -> 1.6f
        else -> 1f
    }
}

/**
 * The detailed map in the colours of the brand: built-up areas in Stein, green and woods in the green of „sicher“,
 * water in Abendblau, roads light with a slate edge, motorways and main roads in Bernstein.
 */
private fun DrawScope.drawTiles(packs: List<MapPack>, camera: MapCamera, view: Rect, mpp: Double, ground: Color, edge: Color, land: Color) {
    if (packs.isEmpty()) return
    val minPixels = 3f / camera.scale.toFloat()
    val lines = HashMap<Int, ArrayList<Offset>>()
    // Areas of all tiles first, then the lines of all tiles by class: a pond of a near tile never covers a main road.
    for (p in packs) {
        for (i in 0 until p.size) {
            val b = 4 * i
            if (p.boxes[b] > view.right || p.boxes[b + 2] < view.left || p.boxes[b + 1] > view.bottom || p.boxes[b + 3] < view.top) continue
            val kind = p.kinds[i].toInt()
            val a = p.shapes[i]
            val n = a.size / 2
            if (MapPack.isArea(kind)) {
                if (p.boxes[b + 2] - p.boxes[b] < minPixels && p.boxes[b + 3] - p.boxes[b + 1] < minPixels) continue
                val path = Path()
                path.moveTo(sx(camera, a[0]), sy(camera, a[1]))
                for (k in 1 until n) path.lineTo(sx(camera, a[2 * k]), sy(camera, a[2 * k + 1]))
                path.close()
                drawPath(
                    path,
                    when (kind) {
                        MapPack.LAND -> land
                        MapPack.WATER -> BrandPalette.Abendblau.copy(alpha = 0.24f)
                        MapPack.WOOD -> BrandPalette.Sicher.copy(alpha = 0.16f)
                        else -> BrandPalette.Sicher.copy(alpha = 0.08f)
                    },
                )
                // Coasts and borders of countries: a fine line around the land.
                if (kind == MapPack.LAND) drawPath(path, androidx.compose.ui.graphics.lerp(land, edge, 0.3f), style = Stroke(1.dp.toPx()))
            } else if (kind < MapPack.PLACE && n > 1 && (p.level == 0 || mpp < roadFrom(kind))) {
                val into = lines.getOrPut(kind) { ArrayList() }
                var last = Offset(sx(camera, a[0]), sy(camera, a[1]))
                for (k in 1 until n) {
                    val next = Offset(sx(camera, a[2 * k]), sy(camera, a[2 * k + 1]))
                    into += last
                    into += next
                    last = next
                }
            }
        }
    }
    fun stroke(kind: Int, color: Color, extra: Float = 0f) {
        val pts = lines[kind] ?: return
        // Round ends only where one sees them: for fine lines they cost the graphics chip a lot and show nothing.
        val width = (roadWidth(kind, mpp) + extra).dp.toPx()
        drawPoints(pts, PointMode.Lines, color, width, if (width >= 4f) StrokeCap.Round else StrokeCap.Butt)
    }
    // Calm colours (ADR 0121 §13): roads light with a fine slate edge, orange stays for the person and the destination.
    stroke(MapPack.STREAM, BrandPalette.Abendblau.copy(alpha = if (mpp > PACK_BELOW) 0.5f else 0.35f))
    stroke(MapPack.PATH, tone0(ground, edge, 0.2f))
    stroke(MapPack.RAIL, tone0(ground, edge, 0.3f))
    // Opaque mixes instead of see-through strokes: where segments meet, see-through ones would add up to dark knots.
    fun tone(share: Float) = androidx.compose.ui.graphics.lerp(ground, edge, share)
    val cased = mpp < 9
    if (cased) {
        // Edges first, then the roads themselves, small before large, so that crossings look joined.
        for (kind in intArrayOf(MapPack.STREET, MapPack.SIDE, MapPack.MAIN, MapPack.MOTORWAY)) stroke(kind, tone(if (kind <= MapPack.MAIN) 0.5f else 0.35f), extra = 1.2f)
        for (kind in intArrayOf(MapPack.STREET, MapPack.SIDE, MapPack.MAIN, MapPack.MOTORWAY)) stroke(kind, ground)
    } else {
        stroke(MapPack.STREET, tone(0.18f))
        stroke(MapPack.SIDE, tone(0.24f))
        stroke(MapPack.MAIN, tone(0.34f))
        stroke(MapPack.MOTORWAY, tone(0.5f))
    }
}

/** The text style of a place name: the larger the place, the larger and bolder. */
private fun placeStyle(rank: PlaceRank): TextStyle = when (rank.type) {
    PlaceType.COUNTRY -> TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    PlaceType.CITY -> TextStyle(fontSize = if (rank.size >= 12) 16.sp else 15.sp, fontWeight = FontWeight.Bold)
    PlaceType.TOWN -> TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    PlaceType.VILLAGE -> TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    PlaceType.HAMLET -> TextStyle(fontSize = 11.sp)
    PlaceType.SUBURB -> TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
    PlaceType.QUARTER -> TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp)
}

/**
 * The names of places, roads and waters of [packs] at [metresPerDp]: which ones and which win the room decides
 * [MapLabels]; here they are measured and drawn on a light halo. Parts of a city in a quieter tone than places.
 */
private fun DrawScope.drawLabels(packs: List<MapPack>, camera: MapCamera, view: Rect, metresPerDp: Double, measurer: TextMeasurer, color: Color, quiet: Color, halo: Color, german: Boolean) {
    val pad = 3.dp.toPx()
    val roadStyle = TextStyle(fontSize = 11.sp)
    val candidates = ArrayList<LabelCandidate>()
    fun add(text: String, x: Float, y: Float, style: TextStyle, tint: Color, priority: Int, room: Float, once: Boolean, tie: Int) {
        val layout = measurer.measure(text, style, maxLines = 1)
        val w = layout.size.width / 2f + pad
        val h = layout.size.height / 2f + pad
        candidates += LabelCandidate(text, priority, x - w, y - h, x + w, y + h, room.dp.toPx(), once, tie, layout to tint)
    }
    for (p in packs) {
        for (i in 0 until p.size) {
            val kind = p.kinds[i].toInt()
            val rank = MapLabels.placeOf(kind, p.level, p.key)
            if (rank != null) {
                if (!MapLabels.placeShown(rank, metresPerDp)) continue
                val x = p.shapes[i][0]
                val y = p.shapes[i][1]
                if (x < view.left || x > view.right || y < view.top || y > view.bottom) continue
                val name = p.name(i)?.let { MapPack.localName(it, german) } ?: continue
                val part = rank.type == PlaceType.SUBURB || rank.type == PlaceType.QUARTER
                // Within a file the places stand by inhabitants, small before large: the later one wins a tie.
                add(name, sx(camera, x), sy(camera, y), placeStyle(rank), if (part) quiet else color, MapLabels.placePriority(rank), if (part) 8f else 14f, once = false, tie = i)
                continue
            }
            // Names of roads and waters only from the tiles, and only once one is close (MapLabels.lineNameBelow).
            if (p.level == 0 || p.nameOf[i] == 0 || !MapLabels.lineNameShown(kind, metresPerDp)) continue
            val b = 4 * i
            if (p.boxes[b] > view.right || p.boxes[b + 2] < view.left || p.boxes[b + 1] > view.bottom || p.boxes[b + 3] < view.top) continue
            val a = p.shapes[i]
            val mid = (a.size / 4) * 2
            val x = a[mid]
            val y = a[mid + 1]
            if (x < view.left || x > view.right || y < view.top || y > view.bottom) continue
            // The longer piece of a street carries its name: it has the room for it.
            val length = ((p.boxes[b + 2] - p.boxes[b]) + (p.boxes[b + 3] - p.boxes[b + 1])) * camera.scale.toFloat()
            add(MapPack.localName(p.names[p.nameOf[i] - 1], german), sx(camera, x), sy(camera, y), roadStyle, color, MapLabels.linePriority(kind), 4f, once = true, tie = length.toInt())
        }
    }
    for (c in MapLabels.choose(candidates, size.width, size.height)) {
        @Suppress("UNCHECKED_CAST")
        val (layout, tint) = c.payload as Pair<androidx.compose.ui.text.TextLayoutResult, Color>
        drawRoundRect(halo.copy(alpha = 0.6f), Offset(c.left, c.top), Size(c.right - c.left, c.bottom - c.top), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        drawText(layout, tint, Offset(c.left + pad, c.top + pad))
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, color: Color, bold: Boolean) {
    val layout = measurer.measure(text, TextStyle(fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
    drawText(layout, color, Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** Borders of municipalities appear when a pixel is less than this many metres. */
private const val TOWN_BORDERS_BELOW = 260.0

/** A detailed package is drawn when a pixel is less than this many metres. */
private const val PACK_BELOW = 160.0

/** Near tiles (streets, paths) are drawn when a pixel is less than this many metres. */
private const val NEAR_BELOW = 22.0

/** The map of the whole country shows when a pixel is less than this many metres. */
private const val BASE_BELOW = 2600.0

/** The borders of German districts show when a pixel is less than this many metres; further out the countries do. */
private const val GERMAN_BORDERS_BELOW = 1400.0

/** For the caller: from when on which tiles are worth asking for. */
internal const val MAP_REGION_BELOW = PACK_BELOW
internal const val MAP_NEAR_BELOW = NEAR_BELOW
private const val TOWN_NAMES_BELOW = 90.0
private const val KREIS_NAMES_BELOW = 900.0
