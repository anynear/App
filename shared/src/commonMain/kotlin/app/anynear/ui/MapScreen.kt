package app.anynear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.anynear.alerts.Alert
import app.anynear.alerts.AlertPriority
import app.anynear.alerts.activeAlerts
import app.anynear.app.AppController
import app.anynear.circles.Circle
import app.anynear.i18n.texts
import app.anynear.location.LocationSharing
import app.anynear.location.Outlines
import app.anynear.location.Place
import app.anynear.location.PlaceSharer
import app.anynear.location.ShareLevel
import app.anynear.location.SharedPlace
import app.anynear.mesh.Conversation
import app.anynear.people.MemberDirectory
import app.anynear.people.Person
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Clock

private const val HOUR = 3_600_000L

@Composable
private fun rememberMinute(): Long {
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    return now
}

/** „vor 5 Min.“: how old a place is, so that nobody takes an old one for a new one. */
internal fun agoText(atMillis: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - atMillis) / 60_000L).coerceAtLeast(0)
    val mt = texts.map
    return when {
        minutes < 2 -> mt.justNow
        minutes < 60 -> mt.minutesAgo(minutes.toInt())
        minutes < 48 * 60 -> mt.hoursAgo((minutes / 60).toInt())
        else -> mt.daysAgo((minutes / (24 * 60)).toInt())
    }
}

internal fun levelName(level: ShareLevel): String = when (level) {
    ShareLevel.Off -> texts.map.levelOff
    ShareLevel.WhenNeeded -> texts.map.levelWhen
    ShareLevel.Area -> texts.map.levelArea
    ShareLevel.Exact -> texts.map.levelExact
}

private fun levelText(level: ShareLevel): String = when (level) {
    ShareLevel.Off -> texts.map.levelOffText
    ShareLevel.WhenNeeded -> texts.map.levelWhenText
    ShareLevel.Area -> texts.map.levelAreaText
    ShareLevel.Exact -> texts.map.levelExactText
}

/** The places this phone knows by name: the own ones and those of its circles (ADR 0121 §21). */
@Composable
internal fun rememberKnownPlaces(controller: AppController): List<app.anynear.location.KnownPlace> {
    val own by (controller.savedPlaces?.places ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val circles by (controller.circlePlaces?.all ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    return remember(own, circles) {
        own.map { app.anynear.location.KnownPlace(it.id, it.name, it.latitude, it.longitude, it.radiusMeters) } +
            circles.flatMap { (circle, list) -> list.map { app.anynear.location.KnownPlace(it.id, it.name, it.latitude, it.longitude, it.radius, circle) } }
    }
}

/** What the person shares right now, in a line; a level whose time is over counts as „wenn etwas ist“. */
private fun statusLine(shared: SharedPlace?, outlines: Outlines?, now: Long, known: List<app.anynear.location.KnownPlace> = emptyList()): String {
    val mt = texts.map
    if (shared == null) return mt.sharesNothingYet
    val over = shared.untilMillis in 1..now
    // A place nobody brought up to date for four weeks is not shown any more (ADR 0121 §24).
    val place = shared.place?.takeIf { !over }?.takeIf { !app.anynear.location.PlaceAge.gone(it.atMillis, nextLocalHour(now, 0)) }
    if (shared.place != null && !over && place == null) return mt.tooOld
    if (place == null) {
        return when {
            shared.level == ShareLevel.Off -> mt.sharesOff
            shared.level.always && !over -> mt.waitingForPlace
            else -> mt.sharesWhen
        }
    }
    // At a place this phone knows by name, an own one or one of a circle (ADR 0121 §21): named here, nothing was sent for it.
    if (shared.label == null && !shared.isArea) app.anynear.location.KnownPlace.at(known, place.latitude, place.longitude)?.let { return mt.atSavedArea(it.name) }
    // At a place the person named: its own word for it, with since when for the exact place, never a time for an area.
    shared.label?.let { name ->
        return if (shared.isArea || shared.sinceMillis <= 0) mt.atSavedArea(name) else mt.atSaved(name, formatTime(shared.sinceMillis))
    }
    val town = outlines?.townAt(place.latitude, place.longitude)?.name
    // A level is brought up to date only when something happens (ADR 0121 §24), so the line says from when the place
    // is: for an area only the day, never the time (when someone entered a cell would say more than the cell does);
    // for the exact place the time today and the day before that; while an action runs it is live.
    val midnight = nextLocalHour(now, 0)
    val days = app.anynear.location.PlaceAge.days(place.atMillis, midnight)
    val running = shared.untilMillis > now
    val ago = when {
        running -> mt.live
        days == 0 -> mt.lastAt(formatTime(place.atMillis))
        else -> mt.dayAgo(days)
    }
    return when {
        shared.isArea -> (if (town != null) mt.inArea(town) else mt.areaUnknown) + " · " + mt.dayAgo(days)
        town != null -> mt.atPlace(town, ago)
        else -> mt.exactNoPlace(ago)
    }
}

private fun initials(name: String): String =
    name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

/**
 * The tab „Karte“ (ADR 0121 §1): at the top who sees me, then the map with the own people and the official warnings,
 * below it always the list of the people with what they share, and the check-ins. The map is never empty.
 */
@Composable
internal fun MapScreen(
    controller: AppController,
    onOpenChat: (Conversation) -> Unit,
    onWhoSeesMe: () -> Unit,
    onMyPlaces: () -> Unit,
) {
    val sharing = controller.locationSharing
    val sharer = controller.placeSharer
    val mt = texts.map
    val now = rememberMinute()
    val scope = rememberCoroutineScope()
    val people by controller.people.collectAsState()
    val circles by controller.circles.collectAsState()
    val outlines by produceState<Outlines?>(null) { value = Outlines.load() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(outlines) { if (outlines != null) loaded = true else { delay(4000); loaded = true } }
    val kreisNames by produceState(emptyMap<String, String>()) { value = runCatching { controller.alerts?.kreisNames() }.getOrNull().orEmpty() }
    val places by (sharer?.places ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, SharedPlace>()) }).collectAsState()
    val own by (sharer?.own ?: remember { kotlinx.coroutines.flow.MutableStateFlow<Place?>(null) }).collectAsState()
    val areaAlerts by (controller.alerts?.areaAlerts ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<Alert>()) }).collectAsState()
    val ownName by controller.ownName.collectAsState()
    val ownImage by controller.ownImage.collectAsState()
    val focus by controller.mapFocus.collectAsState()
    // The map opens where the phone is or was last (20.11.2026): the whole country is much more to draw.
    // Coming back from another page the map is where the person left it (20.11.2026).
    val camera = remember { MapMemory.camera ?: MapCamera().apply { start = controller.placeSharer?.lastOwn() }.also { MapMemory.camera = it } }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    // Opened from the map icon of a chat: this circle as the filter.
    val asked by controller.mapCircle.collectAsState()
    LaunchedEffect(asked.second) { if (asked.second != 0L) filter = asked.first }
    var selected by remember { mutableStateOf<String?>(null) }
    // A coloured district tapped (ADR 0121 §7): where, so that the bubble with its warnings moves with the map.
    var warningSpot by remember { mutableStateOf<WarningSpot?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // Open, the sheet can also cover the whole map: scrolling the list up takes it there, back at the top down again.
    var sheetFull by rememberSaveable { mutableStateOf(false) }

    // The own dot: located when the tab opens and every two minutes while it is open, every ten seconds on the way to
    // a destination. The place stays on the phone.
    LaunchedEffect(sharer, focus != null) {
        while (sharer != null) {
            sharer.locate()
            delay(if (focus != null) 10_000 else 120_000)
        }
    }
    // A destination from a chat: show it together with the own place.
    LaunchedEffect(focus, outlines) {
        val f = focus ?: return@LaunchedEffect
        delay(200)
        camera.fitPlaces(listOfNotNull(f.latitude to f.longitude, own?.let { it.latitude to it.longitude }))
    }

    // The search narrows only the list below the map, not the map.
    val search = rememberPeopleSearch(controller, people.filter { it.deviceId != null && (filter == null || filter in it.circleIds) })
    val withApp = search.all
    val shown = search.found
    val active = remember(areaAlerts, now) { activeAlerts(areaAlerts, now) }
    val warnings = remember(active) { active.flatMap { a -> a.area.map { MapWarning(it.take(5), a.priority) } }.distinct() }
    // Everything paused (20.11.2026): the map steps back, the others stand where they were last known.
    val pausedAll by (controller.locationSharing?.paused ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val markers = buildList {
        for (p in withApp) {
            val s = places[p.deviceId]?.takeIf { it.untilMillis !in 1..now } ?: continue
            val place = s.place?.takeIf { !app.anynear.location.PlaceAge.gone(it.atMillis, nextLocalHour(now, 0)) } ?: continue
            add(MapMarker(p.peer, p.name, place.latitude, place.longitude, s.radiusMeters, stale = pausedAll || s.radiusMeters == null && now - place.atMillis > HOUR, image = p.image))
        }
        focus?.let { add(MapMarker(FOCUS_MARKER, it.label, it.latitude, it.longitude, place = true)) }
        own?.let { add(MapMarker(OWN_MARKER, ownName.ifBlank { mt.you }, it.latitude, it.longitude, own = true, stale = now - it.atMillis > HOUR, image = ownImage)) }
    }
    // Once, when the first places are known: show them all.
    // Opened at the own place: stay there, close; showing everyone at once is the button „Alle zeigen“ (20.11.2026).
    var fitted by remember { mutableStateOf(camera.start != null || camera.scale > 0) }
    var locating by remember { mutableStateOf(false) }
    LaunchedEffect(markers.size, outlines) {
        if (!fitted && markers.isNotEmpty() && camera.width > 0f) {
            camera.fitPlaces(markers.map { it.latitude to it.longitude })
            fitted = true
        }
    }

    // Looking closely: the tiles in view are taken from the phone or fetched (ADR 0121 §11).
    val packs by (controller.mapTiles?.loaded ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, app.anynear.location.MapPack>()) }).collectAsState()
    val ground by produceState(emptyList<app.anynear.location.MapPack>()) { value = app.anynear.location.MapPack.bundled() }
    val mapTiles = controller.mapTiles
    if (mapTiles != null) {
        LaunchedEffect(mapTiles) {
            androidx.compose.runtime.snapshotFlow { Triple(camera.x, camera.y, camera.scale) }.collect {
                // Not for every step of a gesture: what is in view once the map rests for a moment.
                delay(250)
                if (camera.scale <= 0) return@collect
                val mpp = camera.metresPerPixel
                val x0 = camera.x - camera.width / 2 / camera.scale
                val x1 = camera.x + camera.width / 2 / camera.scale
                val y0 = camera.y - camera.height / 2 / camera.scale
                val y1 = camera.y + camera.height / 2 / camera.scale
                val keys = buildList {
                    if (mpp < MAP_REGION_BELOW) addAll(app.anynear.location.MapPack.tilesIn(app.anynear.location.MapPack.REGION, x0, y0, x1, y1, limit = 12))
                    if (mpp < MAP_NEAR_BELOW) addAll(app.anynear.location.MapPack.tilesIn(app.anynear.location.MapPack.NEAR, x0, y0, x1, y1, limit = 24))
                }
                mapTiles.want(keys, Clock.System.now().toEpochMilliseconds())
            }
        }
    }
    val lifeSigns = controller.lifeSigns
    // A life sign starts in the „+“ of a chat (ADR 0121 §23); the map menu leads to the own places instead (20.11.2026).
    val menu = listOfNotNull(
        MenuAction(mt.whoSeesMe, AppIcons.Person, onWhoSeesMe),
        if (controller.savedPlaces != null) MenuAction(mt.myPlaces, AppIcons.Karte, onMyPlaces) else null,
    )
    Column(Modifier.fillMaxSize()) {
        AppHeader(mt.title, menu = menu)
        if (sharing != null && sharer != null) VisibleBar(sharing, sharer, circles, people, onWhoSeesMe)
        // The map has the whole page (19.11.2026); the people and what runs are a sheet at its lower edge.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val o = outlines
            val full = maxHeight
            if (o != null) {
                MapView(
                    o, camera, markers, warnings, kreisNames, packs = packs.values, ground = ground,
                    onTap = { id, _, latitude, longitude ->
                        // A person goes before the district below; the own place and a destination open nothing.
                        val person = id?.takeIf { it != OWN_MARKER && it != FOCUS_MARKER }
                        when (val tap = mapTap(person, o.townAt(latitude, longitude)?.kreis, active)) {
                            is MapTap.Person -> { warningSpot = null; selected = tap.id }
                            is MapTap.Warnings -> warningSpot = WarningSpot(latitude, longitude, tap.kreis)
                            MapTap.Nothing -> warningSpot = null
                        }
                    },
                )
                MapMarkers(controller, camera, markers) { id -> if (id != OWN_MARKER && id != FOCUS_MARKER) { warningSpot = null; selected = id } }
                if (pausedAll) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)))
                Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapButton(AppIcons.Plus, mt.zoomIn) { camera.zoom(1.8f) }
                    MapButton(AppIcons.Minus, mt.zoomOut) { camera.zoom(1 / 1.8f) }
                    MapButton(AppIcons.Karte, mt.locateMe) {
                        // At once to where the phone is known to be; the fresh place follows and only moves the map if it is elsewhere.
                        val known = own?.let { it.latitude to it.longitude } ?: sharer?.lastOwn()
                        known?.let { camera.fitPlaces(listOf(it)) }
                        scope.launch {
                            locating = true
                            try {
                                val place = sharer?.locate() ?: own
                                when {
                                    place == null -> if (known == null) controller.locationSharing?.allow()
                                    known == null || app.anynear.location.PlaceSharer.distanceMeters(known.first, known.second, place.latitude, place.longitude) > 150 ->
                                        camera.fitPlaces(listOf(place.latitude to place.longitude))
                                }
                            } finally {
                                locating = false
                            }
                        }
                    }
                    if (markers.size > 1) MapButton(AppIcons.Kreis, mt.showAll) { camera.fitPlaces(markers.map { it.latitude to it.longitude }) }
                }
                // The bubble of a tapped warning area lies above the buttons of the map.
                warningSpot?.let { spot ->
                    val here = warningsAt(active, spot.kreis)
                    // Ended or expired meanwhile: the bubble goes.
                    if (here.isEmpty()) LaunchedEffect(spot) { warningSpot = null }
                    else WarningBubble(
                        camera, spot, here, kreisNames, now, viewWidth = constraints.maxWidth.toFloat(),
                        viewHeight = constraints.maxHeight.toFloat() - with(androidx.compose.ui.platform.LocalDensity.current) { SHEET_CLOSED.toPx() },
                        onOpen = { alert -> warningSpot = null; controller.alerts?.requestOpen(alert.channelId, alert.id) },
                        onClose = { warningSpot = null },
                    )
                }
                // Map pieces are still coming (19.11.2026): a small circle, so that the filling map does not feel jerky.
                // Only after a moment, so that pieces from the phone do not make it flicker.
                val loadingTiles by (mapTiles?.loading ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
                var showLoading by remember { mutableStateOf(false) }
                LaunchedEffect(loadingTiles, locating) {
                    if (loadingTiles && !locating) delay(300)
                    showLoading = loadingTiles || locating
                }
                if (showLoading) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(12.dp).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .semantics { contentDescription = mt.mapLoading },
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
            } else if (loaded) {
                Text(mt.mapMissing, Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Closed it is one line; a tap or a drag opens it over the lower part of the map. Something that waits
            // for an answer or a destination opens it by itself.
            val needsLook = focus != null || (sharer?.asking?.collectAsState()?.value?.isNotEmpty() == true)
            LaunchedEffect(needsLook) { if (needsLook) sheetOpen = true }
            LaunchedEffect(sheetOpen) { if (sheetOpen) warningSpot = null else sheetFull = false }
            val sheetHeight by androidx.compose.animation.core.animateDpAsState(
                when {
                    !sheetOpen -> SHEET_CLOSED
                    sheetFull -> full
                    else -> full * 0.62f
                },
            )
            // The list decides between half and whole (21.11.2026): a scroll up while half open first grows the sheet;
            // a scroll down that the list no longer takes, because it is at its top, makes it half again.
            val sheetScroll = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (sheetOpen && !sheetFull && available.y < 0f && source == NestedScrollSource.UserInput) {
                            sheetFull = true
                            return available
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (sheetFull && available.y > 0f && source == NestedScrollSource.UserInput) {
                            sheetFull = false
                            return available
                        }
                        return Offset.Zero
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(sheetHeight),
            ) {
                Column(Modifier.nestedScroll(sheetScroll)) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable(onClickLabel = if (sheetOpen) mt.sheetClose else mt.sheetOpen, role = Role.Button) { sheetOpen = !sheetOpen }
                            .pointerInput(Unit) {
                                // Up: closed to half, half to whole. Down: whole to half, half to closed. One step per drag.
                                var moved = false
                                detectVerticalDragGestures(onDragStart = { moved = false }) { _, drag ->
                                    if (moved) return@detectVerticalDragGestures
                                    when {
                                        drag < -6 -> { moved = true; if (sheetOpen) sheetFull = true else sheetOpen = true }
                                        drag > 6 -> { moved = true; if (sheetFull) sheetFull = false else sheetOpen = false }
                                    }
                                }
                            }
                            .padding(top = 8.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
                        Text(
                            if (withApp.isEmpty()) mt.people else mt.peopleCount(withApp.size),
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            focus?.let { f ->
                AppSection(mt.guideToPlace(f.label)) {
                    val me = own
                    if (me != null) {
                        val to = Place(f.latitude, f.longitude, 0)
                        val b = bearing(me, to)
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Arrow(b)
                            Text(mt.guideText(formatAccuracy(PlaceSharer.distanceMeters(me.latitude, me.longitude, f.latitude, f.longitude).roundToInt()), direction(b)), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        Text(mt.guideNoOwn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                    AppRow(title = mt.showAll, icon = AppIcons.Karte, onClick = { camera.fitPlaces(listOfNotNull(f.latitude to f.longitude, own?.let { it.latitude to it.longitude })) })
                    AppRow(title = mt.closeFocus, icon = AppIcons.Schliessen, onClick = { controller.showOnMap(null) })
                }
            }
            if (sharer != null && sharing != null) MapRequests(controller, sharer, sharing, people)
            if (sharer != null) Running(controller, sharer, people, now)
            val relevant = circles.filter { c -> !c.removed && people.any { c.id in it.circleIds } }
            if (relevant.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(mt.allCircles) })
                    relevant.forEach { c -> FilterChip(selected = filter == c.id, onClick = { filter = c.id }, label = { Text(c.name) }) }
                }
            }
            AppSection(mt.people, footer = mt.mapNote) {
                AppRow(
                    title = mt.you,
                    subtitle = own?.let { p ->
                        // Outside the map (abroad) there is no name of a place, but the phone knows where it is.
                        outlines?.townAt(p.latitude, p.longitude)?.let { mt.yourPlace(it.name) + " · " + agoText(p.atMillis, now) } ?: mt.exactNoPlace(agoText(p.atMillis, now))
                    } ?: mt.yourPlaceUnknown,
                    icon = AppIcons.Karte,
                    onClick = { own?.let { camera.fitPlaces(listOf(it.latitude to it.longitude)) } },
                )
                PeopleSearchField(search, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                if (withApp.isEmpty()) {
                    Text(mt.noPeople, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
                val known = rememberKnownPlaces(controller)
                shown.forEach { p ->
                    val s = places[p.deviceId]
                    val warned = s?.place?.takeIf { s.untilMillis !in 1..now }?.let { warningAt(outlines, active, it) }
                    AppRow(
                        title = p.name,
                        subtitle = if (warned != null) mt.warningNear(p.name, warned.title ?: warned.text.take(60)) else statusLine(s, outlines, now, known),
                        leading = { PersonAvatar(controller, p.peer, p.image, 44.dp) },
                        danger = warned != null && warned.priority == AlertPriority.Alarm,
                        chevron = true,
                        onClick = {
                            s?.place?.takeIf { s.untilMillis !in 1..now }?.let { camera.fitPlaces(listOf(it.latitude to it.longitude)) }
                            selected = p.peer
                        },
                    )
                }
            }
            if (lifeSigns != null) LifeSignsSection(lifeSigns)
        }
                }
            }
        }
    }

    selected?.let { peer ->
        val person = Person.find(people, peer)
        if (person == null) selected = null
        else PersonPlaceDialog(controller, person, places[person.deviceId], own, outlines, now, onDismiss = { selected = null }, onOpenChat = onOpenChat)
    }
}

/** The sheet below the map when it is closed: the handle and one line. */
private val SHEET_CLOSED = 56.dp

private const val OWN_MARKER = "\u0000own"
private const val FOCUS_MARKER = "\u0000focus"

/** The active official warning for the district the place lies in, the most urgent first. */
private fun warningAt(outlines: Outlines?, active: List<Alert>, place: Place): Alert? =
    warningsAt(active, outlines?.townAt(place.latitude, place.longitude)?.kreis).firstOrNull()

/** Where a coloured district was tapped. */
private data class WarningSpot(val latitude: Double, val longitude: Double, val kreis: String)

/**
 * The warnings of a tapped district (ADR 0121 §7) as a speech bubble like those of the people ([MapMarkers]): its tail
 * points to where the finger was, above it or, without room there, below. Each warning with level, kind, title,
 * source, since when and until when, area, the text with what to do, and the way to the full alert under
 * „Meldungen“; several ones the highest level first. Long ones scroll inside the bubble.
 */
@Composable
private fun WarningBubble(
    camera: MapCamera, spot: WarningSpot, alerts: List<Alert>, kreisNames: Map<String, String>, now: Long,
    viewWidth: Float, viewHeight: Float, onOpen: (Alert) -> Unit, onClose: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val at = texts.alerts
    val tail = with(density) { BUBBLE_TAIL.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val corner = with(density) { WARNING_CORNER.toPx() }
    val width = minOf(viewWidth - 2 * margin, with(density) { WARNING_WIDTH.toPx() })
    val tip = camera.project(spot.latitude, spot.longitude)
    val maxHeight = warningPopupMaxHeight(tip.y, viewHeight, tail, margin)
    var height by remember { mutableStateOf(0f) }
    val place = placeWarningPopup(tip.x, tip.y, width, height, viewWidth, viewHeight, tail, margin, corner)
    val surface = MaterialTheme.colorScheme.surface
    val edge = AppTheme.colors.alarm
    Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)) {
        Canvas(Modifier.fillMaxSize()) {
            if (height <= 0f) return@Canvas
            val base = BUBBLE_TAIL_BASE.toPx() / 2
            val edgeY = if (place.below) place.top else place.top + height
            val bubble = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(place.left, place.top, place.left + width, place.top + height, androidx.compose.ui.geometry.CornerRadius(corner)))
            }
            val tailPath = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(place.tailX - base, edgeY)
                lineTo(place.tailX + base, edgeY)
                close()
            }
            val shape = Path.combine(androidx.compose.ui.graphics.PathOperation.Union, bubble, tailPath)
            translate(0f, 1.5.dp.toPx()) { drawPath(shape, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)) }
            drawPath(shape, surface)
            drawPath(shape, edge, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        Column(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(place.left.roundToInt(), place.top.roundToInt()) }
                .width(with(density) { width.toDp() })
                .heightIn(max = with(density) { maxHeight.toDp() })
                .onSizeChanged { height = it.height.toFloat() }
                .clip(RoundedCornerShape(WARNING_CORNER)),
        ) {
            // The head stays, so that closing is always at hand; only the warnings below scroll.
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(at.mapWarnings(alerts.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onClose) { Icon(AppIcons.Schliessen, contentDescription = texts.common.close) }
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f, fill = false)) {
                Column(
                    Modifier.verticalScroll(scroll).padding(start = 14.dp, end = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    alerts.forEachIndexed { i, alert ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(end = 10.dp))
                        WarningEntry(alert, kreisNames, now) { onOpen(alert) }
                    }
                }
                // There is more (20.11.2026): the text fades out at the edge where it goes on, and a thin bar at the
                // right shows how much there is and where one is, as long as the bubble does not show everything.
                if (scroll.canScrollBackward) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(SCROLL_FADE).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(surface, surface.copy(alpha = 0f)))))
                }
                if (scroll.canScrollForward) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(SCROLL_FADE).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))))
                }
                if (scroll.maxValue > 0) {
                    val thumb = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    Canvas(Modifier.matchParentSize().padding(top = 4.dp, bottom = 10.dp, end = 4.dp)) {
                        val viewport = scroll.viewportSize.toFloat().takeIf { it > 0f } ?: size.height
                        val content = viewport + scroll.maxValue
                        val bar = (size.height * viewport / content).coerceAtLeast(24.dp.toPx())
                        val y = (size.height - bar) * scroll.value / scroll.maxValue
                        val w = 3.dp.toPx()
                        drawRoundRect(thumb, topLeft = Offset(size.width - w, y), size = androidx.compose.ui.geometry.Size(w, bar), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2))
                    }
                }
            }
        }
    }
}

/** One warning in the bubble of the map. */
@Composable
private fun WarningEntry(alert: Alert, kreisNames: Map<String, String>, now: Long, onOpen: () -> Unit) {
    val at = texts.alerts
    Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriorityChip(alert, curated = true)
            warningKind(alert.source)?.let { kind ->
                Text(
                    when (kind) {
                        WarningKind.Weather -> at.kindWeather
                        WarningKind.Flood -> at.kindFlood
                        WarningKind.Police -> at.kindPolice
                        WarningKind.CivilProtection -> at.kindCivilProtection
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        alert.title?.ifBlank { null }?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        val names = areaNames(alert.area, kreisNames, at.states)
        val details = listOfNotNull(
            alert.source?.let { at.fromSource(it) },
            at.validSince(formatDayAndTime(alert.timestampMillis, now)),
            at.validUntil(formatFutureDayAndTime(alert.untilMillis, now)),
            names.takeIf { it.isNotEmpty() }?.let { n ->
                at.area(if (n.size <= AREA_NAMES) n.joinToString(", ") else n.take(AREA_NAMES).joinToString(", ") + " " + at.moreAreas(n.size - AREA_NAMES))
            },
        )
        details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // Description and what to do, as the source wrote them (the relay sends both as one text).
        if (alert.text.isNotBlank() && alert.text != alert.title) Text(alert.text, style = MaterialTheme.typography.bodyMedium)
        AppButton(at.openInAlerts, onOpen, kind = ButtonKind.Secondary, icon = AppIcons.Glocke)
    }
}

private val WARNING_WIDTH = 340.dp
private val WARNING_CORNER = 16.dp

/** How far the text fades out where the bubble goes on. */
private val SCROLL_FADE = 28.dp

/** At most this many names of the area, then „und n weitere“. */
private const val AREA_NAMES = 4

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
    }
}

/**
 * The people on the map (ADR 0121 §1), above the drawn map: each one a speech bubble with the picture (or the initials)
 * and the name, whose tip ends in a dot exactly at the place; the own one with the name from the own profile and an
 * orange edge. An area has no place to point to: its bubble has no tip and sits on the edge of the cell drawn below,
 * several in one cell spread along it (21.11.2026), so that „ungefähr“ never looks like „genau“. Where bubbles would
 * cover each other, they are fanned out around the place ([placeBubbles], 20.11.2026).
 */
@Composable
private fun MapMarkers(controller: AppController, camera: MapCamera, markers: List<MapMarker>, onMarker: (String) -> Unit) {
    val myDevice by controller.myDeviceId.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    // The own one last, so that it lies on top.
    val people = markers.filter { !it.place }.sortedBy { it.own }
    // The size each bubble really has once laid out; until then a guess from the length of the name.
    val measured = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.unit.IntSize>() }
    val guess = with(density) { (BUBBLE_AVATAR + BUBBLE_PAD * 4).toPx() } to with(density) { (BUBBLE_AVATAR + BUBBLE_PAD * 2).toPx() }
    fun sizeOf(m: MapMarker) = measured[m.id]?.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
        ?: androidx.compose.ui.geometry.Size(guess.first + with(density) { (m.label.length * 8).dp.toPx() }, guess.second)
    val tail = with(density) { BUBBLE_TAIL.toPx() }
    val gap = with(density) { 4.dp.toPx() }
    // Only the distances count, so moving the map does not change the layout, zooming may.
    val spots by remember(people) {
        androidx.compose.runtime.derivedStateOf {
            placeBubbles(people.map { m -> val size = sizeOf(m); camera.project(m.latitude, m.longitude).let { Bubble(m.id, it.x, it.y, size.width, size.height, ringOf(camera, m)) } }, tail, gap)
        }
    }
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val orange = BrandPalette.Signalorange
    Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)) {
        // The bubbles with their tails, drawn in one piece so that the edge runs around both.
        Canvas(Modifier.fillMaxSize()) {
            val corner = (BUBBLE_AVATAR / 2 + BUBBLE_PAD).toPx()
            val base = BUBBLE_TAIL_BASE.toPx() / 2
            people.forEachIndexed { i, m ->
                val tip = camera.project(m.latitude, m.longitude)
                val spot = spots.getOrNull(i) ?: return@forEachIndexed
                val size = sizeOf(m)
                val centre = Offset(tip.x + spot.dx, tip.y + spot.dy)
                val bubble = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(centre.x - size.width / 2, centre.y - size.height / 2, centre.x + size.width / 2, centre.y + size.height / 2, androidx.compose.ui.geometry.CornerRadius(corner)))
                }
                val area = m.radiusMeters != null
                val toTip = tip - centre
                val length = toTip.getDistance()
                // An area: the bubble alone, without a tip (there is no point to show).
                val shape = if (area || length <= 0f) bubble else {
                    val across = Offset(-toTip.y / length * base, toTip.x / length * base)
                    // The base of the tail just inside the edge of the bubble, not in its middle.
                    val inside = (bubbleEdge(size.width, size.height, toTip.x, toTip.y) - BUBBLE_TAIL_INSET.toPx()).coerceIn(0f, length)
                    val root = centre + toTip * (inside / length)
                    val tailPath = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(root.x + across.x, root.y + across.y)
                        lineTo(root.x - across.x, root.y - across.y)
                        close()
                    }
                    Path.combine(androidx.compose.ui.graphics.PathOperation.Union, bubble, tailPath)
                }
                val alpha = if (m.stale) 0.6f else 1f
                // A soft shadow below, so that the bubble stands out from the light map.
                translate(0f, 1.5.dp.toPx()) { drawPath(shape, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f * alpha)) }
                drawPath(shape, surface.copy(alpha = alpha))
                val edge = if (m.own) orange.copy(alpha = alpha) else outline.copy(alpha = outline.alpha * alpha)
                drawPath(shape, edge, style = androidx.compose.ui.graphics.drawscope.Stroke((if (m.own) 2.dp else 1.5.dp).toPx()))
                // The exact place: a dot at the end of the tip, ringed so it shows on any map.
                if (!area) {
                    drawCircle(surface.copy(alpha = alpha), BUBBLE_DOT.toPx() + 1.5.dp.toPx(), tip)
                    drawCircle(if (m.own) orange.copy(alpha = alpha) else onSurface.copy(alpha = alpha), BUBBLE_DOT.toPx(), tip)
                }
            }
        }
        people.forEachIndexed { i, m ->
            Row(
                Modifier
                    .offset {
                        val tip = camera.project(m.latitude, m.longitude)
                        val spot = spots.getOrNull(i) ?: BubbleSpot(0f, 0f)
                        val size = sizeOf(m)
                        androidx.compose.ui.unit.IntOffset((tip.x + spot.dx - size.width / 2).roundToInt(), (tip.y + spot.dy - size.height / 2).roundToInt())
                    }
                    .onSizeChanged { measured[m.id] = it }
                    .clip(RoundedCornerShape(BUBBLE_AVATAR / 2 + BUBBLE_PAD))
                    .clickable(onClickLabel = m.label, role = Role.Button) { onMarker(m.id) }
                    .alpha(if (m.stale) 0.6f else 1f)
                    .padding(BUBBLE_PAD),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BUBBLE_PAD),
            ) {
                val seed = if (m.own) app.anynear.mesh.PeerId.device(myDevice.orEmpty()) else m.id
                if (m.image != null) {
                    PersonAvatar(controller, seed, m.image, BUBBLE_AVATAR)
                } else {
                    // No picture: the initials, in the colour the generated picture of the person would have.
                    val tones = AppTheme.colors.avatarTones
                    val tone = tones[remember(seed, tones.size) { app.anynear.images.AvatarPattern.person(seed, tones.size).tone }]
                    Box(Modifier.size(BUBBLE_AVATAR).clip(CircleShape).background(if (m.own) orange else tone.background), contentAlignment = Alignment.Center) {
                        Text(initials(m.label), style = MaterialTheme.typography.labelMedium, color = if (m.own) androidx.compose.ui.graphics.Color.White else tone.pattern, maxLines = 1)
                    }
                }
                Text(m.label, style = style, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = BUBBLE_TEXT_MAX).padding(end = BUBBLE_PAD))
            }
        }
    }
}

private val BUBBLE_AVATAR = 30.dp
private val BUBBLE_PAD = 4.dp
private val BUBBLE_TEXT_MAX = 120.dp
private val BUBBLE_TAIL = 12.dp
private val BUBBLE_TAIL_BASE = 14.dp
private val BUBBLE_DOT = 3.dp

/** How far inside the edge of the bubble the tail begins, so that the two join without a seam. */
private val BUBBLE_TAIL_INSET = 4.dp

/** For an area: pixels from the centre of its cell to the top corner, as drawn ([PlaceSharer.areaCorners]); else 0. */
private fun ringOf(camera: MapCamera, m: MapMarker): Float {
    val radius = m.radiusMeters ?: return 0f
    val (lat, lon) = app.anynear.location.PlaceSharer.areaCorners(m.latitude, m.longitude, radius)[1]
    return (camera.project(lat, lon) - camera.project(m.latitude, m.longitude)).getDistance().coerceAtLeast(1f)
}

/** Always on the map: for whom the own place is visible; a tap opens „Wer sieht mich?“ (ADR 0121 §1, §4.5). */
@Composable
private fun VisibleBar(sharing: LocationSharing, sharer: PlaceSharer, circles: List<Circle>, people: List<Person>, onClick: () -> Unit) {
    val paused by sharing.paused.collectAsState()
    val levels by sharing.circles.collectAsState()
    val persons by sharing.persons.collectAsState()
    val default by sharing.default.collectAsState()
    val approved by sharing.approved.collectAsState()
    val mt = texts.map
    val text = remember(paused, levels, persons, default, approved, circles, people) {
        val always = circles.filter { !it.removed && sharing.level(it.id).always }.map { it.name } +
            people.filter { p -> p.deviceId?.let { persons[it]?.always == true && sharing.levelFor(it, emptyList()).always } == true }.map { it.name }
        when {
            paused -> mt.visiblePaused
            always.isNotEmpty() -> mt.visibleTo(always.joinToString(", "))
            sharer.visibleTo().isNotEmpty() || default == ShareLevel.WhenNeeded -> mt.visibleWhen
            else -> mt.visibleNobody
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().clickable(onClickLabel = mt.whoSeesMe, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.Karte, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Chevron()
        }
    }
}

/** Requests that wait for an answer: someone asks to see the place, or someone is new in a circle shared all the time. */
@Composable
private fun MapRequests(controller: AppController, sharer: PlaceSharer, sharing: LocationSharing, people: List<Person>) {
    val asking by sharer.asking.collectAsState()
    val approved by sharing.approved.collectAsState()
    val levels by sharing.circles.collectAsState()
    val persons by sharing.persons.collectAsState()
    val mt = texts.map
    fun nameOf(id: String) = people.firstOrNull { it.deviceId == id }?.name
    asking.forEach { id ->
        val name = nameOf(id) ?: return@forEach
        AppSection(mt.askedTitle(name), footer = mt.askedText) {
            AppRow(title = mt.showOnce, icon = AppIcons.Karte, onClick = { sharer.answer(id, 0) })
            AppRow(title = mt.showOneHour, icon = AppIcons.Uhr, onClick = { sharer.answer(id, 60) })
            AppRow(title = texts.location.no, icon = AppIcons.Schliessen, onClick = { sharer.answer(id, null) })
        }
    }
    controller.savedPlaces?.let { saved ->
        val proposal by saved.proposal.collectAsState()
        proposal?.let { p ->
            val name = if (p.kind == app.anynear.location.SavedPlaces.Kind.Home) mt.placeHome else mt.placeWork
            AppSection(if (p.kind == app.anynear.location.SavedPlaces.Kind.Home) mt.proposeHome else mt.proposeWork, footer = mt.proposeText) {
                AppRow(title = mt.proposeYes, icon = AppIcons.Haken, onClick = {
                    saved.save(app.anynear.location.SavedPlace(p.cell, name, p.latitude, p.longitude))
                })
                AppRow(title = mt.proposeNo, icon = AppIcons.Schliessen, onClick = { saved.dismiss(p) })
            }
        }
    }
    val newcomers = remember(approved, levels, persons, people) { sharer.newcomers() }
    newcomers.take(3).forEach { t ->
        AppSection(mt.newcomerTitle(t.name), footer = mt.newcomerText(t.name)) {
            AppRow(title = mt.newcomerYes, icon = AppIcons.Haken, onClick = { sharing.approve(listOf(t.personId)) })
            AppRow(title = mt.newcomerNo, icon = AppIcons.Schliessen, onClick = { sharing.approve(listOf(t.personId), yes = false) })
        }
    }
}

/** What runs right now: „Begleite mich“ and „Alle da?“. */
@Composable
private fun Running(controller: AppController, sharer: PlaceSharer, people: List<Person>, now: Long) {
    val escort by sharer.escort.collectAsState()
    val circles by controller.circles.collectAsState()
    val scope = rememberCoroutineScope()
    val mt = texts.map
    escort?.takeIf { it.untilMillis > now }?.let { e ->
        val name = e.circleId?.let { id -> circles.firstOrNull { it.id == id }?.name } ?: people.firstOrNull { it.deviceId == e.personId }?.name ?: "?"
        AppSection(mt.shareMine) {
            AppRow(
                title = mt.escortRunning(name, formatTime(e.untilMillis)),
                subtitle = e.destLabel?.takeIf { e.hasDestination }?.let { mt.shareMineEndsAt(it) }, icon = AppIcons.Karte,
            )
            AppRow(title = mt.shareMineStop, icon = AppIcons.Haken, onClick = {
                scope.launch {
                    e.planId?.let { controller.lifeSigns?.end(it) }
                    sharer.setEscort(null)
                    controller.showOnMap(null)
                }
            })
        }
    }
}

/** A person on the map: what it shares, the way to it, and what it sees of me. */
@Composable
private fun PersonPlaceDialog(
    controller: AppController, person: Person, shared: SharedPlace?, own: Place?, outlines: Outlines?, now: Long,
    onDismiss: () -> Unit, onOpenChat: (Conversation) -> Unit,
) {
    val mt = texts.map
    val scope = rememberCoroutineScope()
    val sharer = controller.placeSharer
    val sharing = controller.locationSharing
    var note by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val place = shared?.place?.takeIf { shared.untilMillis !in 1..now }
    ActionsDialog(person.name, onDismiss = onDismiss) {
        Text(statusLine(shared, outlines, now, rememberKnownPlaces(controller)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        if (place != null) {
            if (own != null) {
                val meters = PlaceSharer.distanceMeters(own.latitude, own.longitude, place.latitude, place.longitude)
                val bearing = bearing(own, place)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Arrow(bearing)
                    Column {
                        Text(mt.guideTo(person.name), style = MaterialTheme.typography.titleSmall)
                        Text(mt.guideText(formatAccuracy(meters.roundToInt()), direction(bearing)), style = MaterialTheme.typography.bodyMedium)
                        if (shared.isArea) Text(mt.guideArea, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(mt.guideNoOwn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        AppRow(title = mt.writeMessage, icon = AppIcons.Nachrichten, onClick = { onDismiss(); onOpenChat(Conversation.Direct(person.peer)) })
        val id = person.deviceId
        if (sharer != null && id != null && place == null) {
            AppRow(title = mt.askPlace, icon = AppIcons.Karte, onClick = {
                scope.launch { note = if (sharer.request(id)) mt.askSent(person.name) else mt.askFailed }
            })
        }
        if (sharing != null && id != null) {
            val level = sharing.levelFor(id, person.circleIds.filter { it != MemberDirectory.DIRECT_CONTACTS })
            AppRow(title = mt.whatSees(person.name), subtitle = levelName(level), icon = AppIcons.Person, chevron = true, onClick = { choosing = true })
        }
    }
    if (choosing && sharing != null) {
        val id = person.deviceId
        if (id != null) LevelDialog(
            title = mt.chooseLevel(person.name), current = sharing.persons.collectAsState().value[id], followLabel = mt.followsCircles,
            onDismiss = { choosing = false },
        ) { level, until ->
            if (level != null && level.always) sharing.approve(listOf(id))
            sharing.setPersonLevel(id, level, until)
            choosing = false
        }
    }
}

/** Degrees clockwise from north, from [from] to [to]. */
private fun bearing(from: Place, to: Place): Double {
    val dy = to.latitude - from.latitude
    val dx = (to.longitude - from.longitude) * cos((from.latitude + to.latitude) / 2 * PI / 180)
    return (atan2(dx, dy) * 180 / PI + 360) % 360
}

private fun direction(bearing: Double): String {
    val mt = texts.map
    return listOf(mt.north, mt.northEast, mt.east, mt.southEast, mt.south, mt.southWest, mt.west, mt.northWest)[((bearing + 22.5) / 45).toInt() % 8]
}

/** The arrow of „Zu Anna führen“ (ADR 0121 §8): north is up; it needs no map picture and no network. */
@Composable
private fun Arrow(bearing: Double) {
    val color = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(56.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        drawCircle(ring, r - 1, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        val a = bearing * PI / 180
        fun at(angle: Double, len: Float) = Offset(c.x + (sin(angle) * len).toFloat(), c.y - (cos(angle) * len).toFloat())
        val path = Path().apply {
            val tip = at(a, r * 0.8f)
            val left = at(a + 2.5, r * 0.55f)
            val right = at(a - 2.5, r * 0.55f)
            moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(c.x, c.y); lineTo(right.x, right.y); close()
        }
        drawPath(path, color)
    }
}

/**
 * The four levels as a choice (ADR 0121 §3), then for how long. [followLabel]: the entry that hands the choice back
 * (to the default or to the circles), `null` without one.
 */
@Composable
internal fun LevelDialog(
    title: String, current: ShareLevel?, followLabel: String?, onDismiss: () -> Unit, levels: List<ShareLevel> = ShareLevel.entries,
    onChoose: (level: ShareLevel?, untilMillis: Long) -> Unit,
) {
    val mt = texts.map
    var picked by remember { mutableStateOf<ShareLevel?>(null) }
    val chosen = picked
    if (chosen == null) {
        ActionsDialog(title, onDismiss = onDismiss) {
            levels.forEach { level ->
                AppRow(
                    title = levelName(level), subtitle = levelText(level), trailing = { if (current == level) Icon(AppIcons.Haken, contentDescription = null) },
                    // A level has no end (ADR 0121 §24): sharing for a time is the action „Standort teilen“.
                    onClick = { onChoose(level, 0) },
                )
            }
            if (followLabel != null) AppRow(title = followLabel, trailing = { if (current == null) Icon(AppIcons.Haken, contentDescription = null) }, onClick = { onChoose(null, 0) })
        }
    } else {
        val now = Clock.System.now().toEpochMilliseconds()
        ActionsDialog(mt.forHowLong, onDismiss = onDismiss) {
            AppRow(title = mt.untilChanged, onClick = { onChoose(chosen, 0) })
            AppRow(title = mt.oneHour, onClick = { onChoose(chosen, now + HOUR) })
            AppRow(title = mt.untilTonight, onClick = { onChoose(chosen, now + 8 * HOUR) })
            Text(mt.openOnly, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

/**
 * The phone's permission follows the level (ADR 0121 §24): as soon as a level needs more than the phone allows, the
 * app says in a sentence what for and leads to the system's question. „Nur bei SOS“ needs the exact location while the
 * app is used; „Ungefähr“ and „Genau“ need „Immer“, because they are brought up to date when the app is woken.
 * Asked once per step; who says „Später“ finds the same under „Dein Handy“.
 */
@Composable
internal fun PhonePermissionPrompt(sharing: LocationSharing) {
    val mt = texts.map
    val default by sharing.default.collectAsState()
    val circles by sharing.circles.collectAsState()
    val persons by sharing.persons.collectAsState()
    val allowed by sharing.allowed.collectAsState()
    val background by sharing.background.collectAsState()
    val all = listOfNotNull(default) + circles.values + persons.values
    val wantsAlways = all.any { it.always }
    val wantsAny = all.any { it != ShareLevel.Off }
    val phone = remember(allowed, background, wantsAlways, wantsAny) { sharing.phone() }
    val missing = when {
        wantsAlways && phone != LocationSharing.Phone.Always -> 2
        wantsAny && (phone == LocationSharing.Phone.NotAllowed || phone == LocationSharing.Phone.SwitchedOff) -> 1
        else -> 0
    }
    // What was asked already, for this visit of the page: a higher step asks again, the same one does not.
    var asked by remember { mutableStateOf(0) }
    if (missing > asked) {
        AppDialog(
            onDismissRequest = { asked = missing },
            title = { Text(if (missing == 2) mt.needAlwaysTitle else mt.needLocationTitle) },
            text = { Text(if (missing == 2) mt.needAlwaysText else mt.needLocationText) },
            confirmButton = {
                AppButton(mt.needAllow, {
                    asked = missing
                    sharing.fixPhone()
                })
            },
            dismissButton = { AppButton(mt.needLater, { asked = missing }, kind = ButtonKind.Quiet) },
        )
    }
}

/** Where a sharing shall end or a meeting point shall be: a place with a name. */
internal class PickedPlace(val latitude: Double, val longitude: Double, val label: String)

/**
 * „Meinen Standort teilen“ (ADR 0121 §13), started in a conversation: the circle or the person sees the exact place
 * for a time. With a destination the sharing ends by itself near it; in a circle a check-in can watch over it and
 * warn the helpers if the person does not arrive.
 */
@Composable
internal fun ShareMyPlaceDialog(controller: AppController, conversation: Conversation, onDismiss: () -> Unit) {
    val mt = texts.map
    val sharer = controller.placeSharer ?: return onDismiss()
    val lifeSigns = controller.lifeSigns
    val people by controller.people.collectAsState()
    val messages by controller.messages.collectAsState()
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableStateOf(60) }
    var warn by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<PickedPlace?>(null) }
    var picking by remember { mutableStateOf(false) }
    val circleId = (conversation as? Conversation.Circle)?.circleId
    // Meeting points of this conversation are destinations at hand, the newest first.
    val points = remember(messages, conversation) {
        messages.asReversed().asSequence().filter { it.conversation == conversation }.mapNotNull { m ->
            val s = m.spot?.takeIf { it.kind == app.anynear.mesh.ChatSpot.MEET } ?: return@mapNotNull null
            PickedPlace(s.latitude ?: return@mapNotNull null, s.longitude ?: return@mapNotNull null, s.label ?: mt.spotMeet)
        }.distinctBy { it.label }.take(3).toList()
    }
    val savedPlaces by (controller.savedPlaces?.places ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<app.anynear.location.SavedPlace>()) }).collectAsState()
    // The places the person named are destinations at hand, too („bis ich zu Hause bin“).
    val named = savedPlaces.map { PickedPlace(it.latitude, it.longitude, it.name) }
    if (picking) {
        PlacePickerDialog(controller, mt.destination, onDismiss = { picking = false }) { lat, lon ->
            destination = PickedPlace(lat, lon, mt.destinationOnMap)
            picking = false
        }
        return
    }
    ActionsDialog(mt.shareMine, onDismiss = onDismiss) {
        Text(mt.shareMineText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text(mt.forHowLong, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 120, 240, 480).forEach { m ->
                FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text(if (m < 120) mt.minutes(m) else mt.hours(m / 60)) })
            }
        }
        Text(mt.destination, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = destination == null, onClick = { destination = null }, label = { Text(mt.destinationNone) })
            (named + points).distinctBy { it.label }.forEach { p -> FilterChip(selected = destination?.label == p.label, onClick = { destination = p }, label = { Text(p.label) }) }
            FilterChip(selected = destination?.label == mt.destinationOnMap, onClick = { picking = true }, label = { Text(mt.destinationPick) })
        }
        if (destination != null) Text(mt.destinationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        if (circleId != null && lifeSigns != null) {
            AppRow(
                title = if (destination != null) mt.shareMineWarnArrive else mt.shareMineWarn, subtitle = mt.shareMineWarnText, icon = AppIcons.Uhr,
                trailing = { Switch(checked = warn, onCheckedChange = { warn = it }) }, onClick = { warn = !warn },
            )
        }
        Text(mt.openOnly, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        AppButton(
            mt.shareMineStart,
            {
                scope.launch {
                    val until = Clock.System.now().toEpochMilliseconds() + minutes * 60_000L
                    val to = destination
                    val personId = (conversation as? Conversation.Direct)?.let { Person.find(people, it.peer)?.deviceId }
                    if (circleId != null || personId != null) {
                        val plan = if (warn && circleId != null) lifeSigns?.startTimed(circleId, mt.shareMine, until, quietMillis = 20 * 60_000L) else null
                        sharer.setEscort(
                            PlaceSharer.Escort(circleId, plan, until, personId, to?.latitude, to?.longitude, to?.label),
                            if (circleId != null) people.filter { circleId in it.circleIds }.mapNotNull { it.deviceId } else emptyList(),
                        )
                        // The own map leads to the destination.
                        to?.let { controller.showOnMap(AppController.MapFocus(it.latitude, it.longitude, it.label)) }
                        sharer.sync()
                    }
                    onDismiss()
                }
            },
            Modifier.fillMaxWidth().padding(16.dp), kind = ButtonKind.Main,
        )
    }
}

/**
 * Chooses a place on the map (ADR 0121 §13): the person moves the map under the cross and takes what lies there, for
 * a destination or a meeting point. Starts at the own place if the phone knows it.
 */
@Composable
internal fun PlacePickerDialog(controller: AppController, title: String, onDismiss: () -> Unit, onPick: (latitude: Double, longitude: Double) -> Unit) {
    val mt = texts.map
    val outlines by produceState<Outlines?>(null) { value = Outlines.shared() }
    val ground by produceState(emptyList<app.anynear.location.MapPack>()) { value = app.anynear.location.MapPack.bundled() }
    val packs by (controller.mapTiles?.loaded ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, app.anynear.location.MapPack>()) }).collectAsState()
    val own by (controller.placeSharer?.own ?: remember { kotlinx.coroutines.flow.MutableStateFlow<Place?>(null) }).collectAsState()
    val camera = remember { MapCamera().apply { start = own?.let { it.latitude to it.longitude } } }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Triple(camera.x, camera.y, camera.scale) }.collect {
            delay(250)
            if (camera.scale <= 0) return@collect
            val mpp = camera.metresPerPixel
            val x0 = camera.x - camera.width / 2 / camera.scale
            val x1 = camera.x + camera.width / 2 / camera.scale
            val y0 = camera.y - camera.height / 2 / camera.scale
            val y1 = camera.y + camera.height / 2 / camera.scale
            controller.mapTiles?.want(
                buildList {
                    if (mpp < MAP_REGION_BELOW) addAll(app.anynear.location.MapPack.tilesIn(app.anynear.location.MapPack.REGION, x0, y0, x1, y1, limit = 12))
                    if (mpp < MAP_NEAR_BELOW) addAll(app.anynear.location.MapPack.tilesIn(app.anynear.location.MapPack.NEAR, x0, y0, x1, y1, limit = 24))
                },
                Clock.System.now().toEpochMilliseconds(),
            )
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = AppFullScreenDialogProperties) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    AppButton(texts.common.cancel, onDismiss, kind = ButtonKind.Quiet)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    outlines?.let { o ->
                        MapView(o, camera, own?.let { listOf(MapMarker(OWN_MARKER, mt.you, it.latitude, it.longitude, place = true)) }.orEmpty(), emptyList(), emptyMap(), packs = packs.values, ground = ground)
                    }
                    // The cross in the middle marks what is taken.
                    val cross = BrandPalette.Signalorange
                    Canvas(Modifier.align(Alignment.Center).size(44.dp)) {
                        drawCircle(cross, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                        drawCircle(cross, radius = 5f)
                    }
                    Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapButton(AppIcons.Plus, mt.zoomIn) { camera.zoom(1.8f) }
                        MapButton(AppIcons.Minus, mt.zoomOut) { camera.zoom(1 / 1.8f) }
                    }
                }
                Text(mt.pickHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                AppButton(
                    mt.pickTake, { onPick(Outlines.latitude(camera.y), Outlines.longitude(camera.x)) },
                    Modifier.fillMaxWidth().padding(16.dp), kind = ButtonKind.Main, enabled = camera.scale > 0,
                )
            }
        }
    }
}

/** „Meine Orte“ as its own page, opened from the ⋮ menu of the map (20.11.2026; before it was part of „Wer sieht mich?“). */
@Composable
internal fun MyPlacesScreen(controller: AppController, onBack: () -> Unit) {
    val saved = controller.savedPlaces ?: return onBack()
    Column(Modifier.fillMaxSize()) {
        AppHeader(texts.map.myPlaces, leading = { HeaderBackButton(onBack) })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MyPlacesSection(controller, saved)
        }
    }
}

/** „Meine Orte“ (ADR 0121 §13): the places the person named, where it adds, changes and removes them. */
@Composable
private fun MyPlacesSection(controller: AppController, saved: app.anynear.location.SavedPlaces) {
    val mt = texts.map
    val places by saved.places.collectAsState()
    val presence by saved.presence.collectAsState()
    val own by (controller.placeSharer?.own ?: remember { kotlinx.coroutines.flow.MutableStateFlow<Place?>(null) }).collectAsState()
    var adding by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    // A place for a circle (ADR 0121 §21): set up once, everyone in the circle knows it.
    val circlePlaces = controller.circlePlaces
    val ofCircles by (circlePlaces?.all ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val circles by controller.circles.collectAsState()
    val usable = circles.filter { !it.removed }
    var forCircle by remember { mutableStateOf<String?>(null) }
    var editingCircle by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    // „Hier“ needs to know where the phone is; after a start of the app nobody may have asked yet.
    LaunchedEffect(adding) { if (adding && own == null) controller.placeSharer?.locate() }
    fun add(latitude: Double, longitude: Double) {
        val label = name.trim().ifEmpty { mt.placeHome }
        val circle = forCircle
        if (circle != null && circlePlaces != null) {
            scope.launch { circlePlaces.add(circle, label, latitude, longitude) }
        } else {
            saved.save(app.anynear.location.SavedPlace("p" + Clock.System.now().toEpochMilliseconds(), label, latitude, longitude))
            own?.let(saved::onPlace)
        }
        forCircle = null
        adding = false
        picking = false
        name = ""
    }
    AppSection(mt.myPlaces, footer = mt.myPlacesText) {
        places.forEach { p ->
            AppRow(
                title = p.name, subtitle = if (presence?.placeId == p.id) mt.placeHereNow else null, icon = AppIcons.Karte, chevron = true,
                onClick = { editing = p.id },
            )
        }
        ofCircles.forEach { (circleId, list) ->
            val circleName = circles.firstOrNull { it.id == circleId }?.name.orEmpty()
            list.forEach { p ->
                AppRow(title = p.name, subtitle = mt.placeOfCircle(circleName), icon = AppIcons.Kreis, chevron = true, onClick = { editingCircle = circleId to p.id })
            }
        }
        AppRow(title = mt.placeAdd, icon = AppIcons.Plus, onClick = { adding = true })
    }
    editingCircle?.let { (circleId, id) ->
        val p = ofCircles[circleId]?.firstOrNull { it.id == id }
        if (p == null) editingCircle = null
        else ActionsDialog(p.name, onDismiss = { editingCircle = null }) {
            Text(mt.placeCircleText(circles.firstOrNull { it.id == circleId }?.name.orEmpty()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            AppRow(title = mt.placeRemove, icon = AppIcons.Papierkorb, danger = true, onClick = {
                scope.launch { circlePlaces?.remove(circleId, id) }
                editingCircle = null
            })
        }
    }
    if (picking) {
        PlacePickerDialog(controller, name.trim().ifEmpty { mt.placeAdd }, onDismiss = { picking = false }) { lat, lon -> add(lat, lon) }
    } else if (adding) {
        ActionsDialog(mt.placeAdd, onDismiss = { adding = false }) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(mt.placeHome, mt.placeWork, mt.placeSchool).forEach { n -> FilterChip(selected = name == n, onClick = { name = n }, label = { Text(n) }) }
            }
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text(mt.placeName) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // For whom: only me, or everyone in one of my circles.
            if (circlePlaces != null && usable.isNotEmpty()) {
                Text(mt.placeFor, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = forCircle == null, onClick = { forCircle = null }, label = { Text(mt.placeOnlyMe) })
                    usable.forEach { c -> FilterChip(selected = forCircle == c.id, onClick = { forCircle = c.id }, label = { Text(c.name) }) }
                }
                if (forCircle != null) Text(mt.placeForCircleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val me = own
            if (me != null) AppRow(title = mt.placeHere, icon = AppIcons.Karte, onClick = { add(me.latitude, me.longitude) })
            AppRow(title = mt.placeOnMap, icon = AppIcons.Karte, chevron = true, onClick = { picking = true })
        }
    }
    editing?.let { id ->
        val p = places.firstOrNull { it.id == id }
        if (p == null) editing = null
        else ActionsDialog(p.name, onDismiss = { editing = null }) {
            AppRow(
                title = mt.placeWithArea, subtitle = mt.placeWithAreaText,
                trailing = { Switch(checked = p.withArea, onCheckedChange = { saved.save(p.copy(withArea = it)) }) }, onClick = { saved.save(p.copy(withArea = !p.withArea)) },
            )
            AppRow(title = mt.placeRemove, icon = AppIcons.Papierkorb, danger = true, onClick = {
                saved.remove(p.id)
                editing = null
            })
        }
    }
}

/**
 * The line „Standort“ on the page of a circle or a person (ADR 0121 §13): what this counterpart sees of me, and the
 * choice of the level right there. [circleId] for a circle, otherwise [personId] with the circles it shares with me.
 */
@Composable
internal fun LocationLevelRow(controller: AppController, name: String, circleId: String? = null, personId: String? = null, personCircles: List<String> = emptyList()) {
    val sharing = controller.locationSharing ?: return
    val mt = texts.map
    val default by sharing.default.collectAsState()
    val levels by sharing.circles.collectAsState()
    val persons by sharing.persons.collectAsState()
    val until by sharing.until.collectAsState()
    val paused by sharing.paused.collectAsState()
    val approved by sharing.approved.collectAsState()
    val people by controller.people.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    // Read, so that the line follows every change.
    approved.size; until.size
    val level = when {
        paused -> ShareLevel.Off
        circleId != null -> sharing.level(circleId)
        personId != null -> sharing.levelFor(personId, personCircles)
        else -> return
    }
    AppSection {
        AppRow(
            title = mt.rowTitle, subtitle = if (paused) mt.pauseAllOn else levelName(level),
            icon = AppIcons.Karte, chevron = true, onClick = { choosing = true },
        )
    }
    if (choosing) LevelDialog(
        title = mt.chooseLevel(name),
        current = if (circleId != null) levels[circleId] ?: default else persons[personId],
        followLabel = if (circleId != null) null else mt.followsCircles,
        onDismiss = { choosing = false },
        // For a person only less than its circles give (ADR 0123 §8); more is „Standort freigeben“.
        levels = if (personId == null) ShareLevel.entries else sharing.circleLevelFor(personId, personCircles).let { cap -> ShareLevel.entries.filter { it.wire <= cap.wire } },
    ) { chosen, untilMillis ->
        if (circleId != null) {
            sharing.setLevel(circleId, chosen, untilMillis, members = people.filter { circleId in it.circleIds }.mapNotNull { it.deviceId })
        } else if (personId != null) {
            if (chosen != null && chosen.always) sharing.approve(listOf(personId))
            sharing.setPersonLevel(personId, chosen, untilMillis)
        }
        choosing = false
    }
}

/** „Dein Handy“ (ADR 0121 §24): what the phone allows right now and what follows from it; a tap leads to the way to change it. */
@Composable
internal fun PhoneLocationRow(sharing: LocationSharing) {
    val mt = texts.map
    val allowed by sharing.allowed.collectAsState()
    val background by sharing.background.collectAsState()
    val now = rememberMinute()
    val phone = remember(allowed, background, now) { sharing.phone() }
    val (title, text) = when (phone) {
        LocationSharing.Phone.Always -> mt.phoneAlways to mt.phoneAlwaysText
        LocationSharing.Phone.WhileOpen -> mt.phoneWhileOpen to mt.phoneWhileOpenText
        LocationSharing.Phone.NotAllowed -> mt.phoneNotAllowed to mt.phoneNotAllowedText
        LocationSharing.Phone.SwitchedOff -> mt.phoneOff to mt.phoneOffText
    }
    val ok = phone == LocationSharing.Phone.Always
    AppRow(title = title, subtitle = text, icon = if (ok) AppIcons.Haken else AppIcons.Achtung, chevron = !ok, onClick = if (ok) null else ({ sharing.fixPhone() }))
}

/**
 * „Wer sieht mich?“ (ADR 0121 §4.5): everything at a glance, one line per circle and person, „Alles pausieren“ and
 * the device the location comes from.
 */
@Composable
internal fun WhoSeesMeScreen(controller: AppController, onBack: () -> Unit) {
    val sharing = controller.locationSharing ?: return onBack()
    val mt = texts.map
    val default by sharing.default.collectAsState()
    val levels by sharing.circles.collectAsState()
    val persons by sharing.persons.collectAsState()
    val until by sharing.until.collectAsState()
    val paused by sharing.paused.collectAsState()
    val source by sharing.source.collectAsState()
    val approved by sharing.approved.collectAsState()
    val allowed by sharing.allowed.collectAsState()
    val circles by controller.circles.collectAsState()
    val people by controller.people.collectAsState()
    val myDevice by controller.myDeviceId.collectAsState()
    val sharedAll by (controller.sharings?.all ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, app.anynear.anmp.core.PlaceSharing>()) }).collectAsState()
    val sharedLevels = sharedAll.values.filter { it.active }.associate { it.person to ShareLevel.ofWire(it.level) }
    val now = rememberMinute()
    var editing by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var editingDefault by remember { mutableStateOf(false) }
    fun untilText(key: String): String = until[key]?.takeIf { it > now }?.let { " · " + mt.until(formatTime(it)) }.orEmpty()
    val shownCircles = controller.lifeSigns?.choices(circles) ?: circles.filter { !it.removed }
    Column(Modifier.fillMaxSize()) {
        AppHeader(mt.whoSeesMe, leading = { HeaderBackButton(onBack) })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(mt.whoSeesMeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PhonePermissionPrompt(sharing)
            AppSection {
                AppRow(
                    title = mt.pauseAll, subtitle = if (paused) mt.pauseAllOn else mt.pauseAllOff, icon = AppIcons.Anhalten,
                    trailing = { Switch(checked = paused, onCheckedChange = sharing::setPaused) }, onClick = { sharing.setPaused(!paused) },
                )
            }
            // Three steps, each one going before the one above (20.11.2026): everyone, then a circle, then a person.
            val base = levelName(default ?: ShareLevel.Off)
            AppSection(mt.stepAll, footer = mt.stepAllFooter) {
                AppRow(title = base, icon = AppIcons.Karte, chevron = true, onClick = { editingDefault = true })
            }
            if (shownCircles.isNotEmpty()) AppSection(mt.stepCircles, footer = mt.stepCirclesFooter) {
                shownCircles.forEach { c ->
                    val own = levels[c.id]
                    AppRow(
                        title = c.name, subtitle = if (own != null) levelName(own) + untilText("c:${c.id}") else mt.likeAll(base),
                        leading = { CircleAvatar(controller, c.id, c.image, 40.dp) }, chevron = true, onClick = { editing = c.id to true },
                    )
                }
            }
            val search = rememberPeopleSearch(controller, people.filter { it.deviceId != null })
            if (search.all.isNotEmpty()) AppSection(mt.stepPersons, footer = mt.stepPersonsFooter) {
                PeopleSearchField(search, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                search.found.forEach { p ->
                    val id = p.deviceId!!
                    val own = persons[id]
                    val inCircles = p.circleIds.filter { it != MemberDirectory.DIRECT_CONTACTS }
                    val level = levelName(sharing.levelFor(id, inCircles))
                    AppRow(
                        title = p.name,
                        subtitle = when {
                            // A sharing both agreed on goes before the rest (ADR 0123 §8).
                            sharedLevels[id] != null -> mt.sharingRow(levelName(sharedLevels.getValue(id)))
                            own != null && own.wire > sharing.circleLevelFor(id, inCircles).wire -> levelName(own) + untilText("p:$id") + " · " + mt.onlyFromYou
                            own != null -> levelName(own) + untilText("p:$id")
                            inCircles.any { levels[it] != null } -> mt.likeCircles(level)
                            else -> mt.likeAll(level)
                        },
                        leading = { PersonAvatar(controller, p.peer, p.image, 40.dp) }, chevron = true, onClick = { editing = id to false },
                    )
                }
            }
            AppSection(mt.sourceTitle, footer = mt.openOnly) {
                val here = source == null || source == myDevice
                AppRow(title = if (here) mt.sourceThis else mt.sourceOther, icon = AppIcons.Karte)
                if (!here) AppRow(title = mt.sourceUseThis, icon = AppIcons.Haken, onClick = { sharing.setSource(myDevice) })
            }
        }
    }
    // Reading [approved] keeps the lines current after an answer about someone new.
    approved.size
    if (editingDefault) LevelDialog(
        title = mt.defaultRow, current = default, followLabel = null, onDismiss = { editingDefault = false },
        levels = listOf(ShareLevel.Off, ShareLevel.WhenNeeded),
    ) { level, _ ->
        level?.let(sharing::answer)
        editingDefault = false
    }
    editing?.let { (id, isCircle) ->
        val name = if (isCircle) circles.firstOrNull { it.id == id }?.name.orEmpty() else people.firstOrNull { it.deviceId == id }?.name.orEmpty()
        // For a person only less than its circles give (ADR 0123 §8): more is „Standort freigeben“, which both agree to.
        val personCircles = if (isCircle) emptyList() else people.firstOrNull { it.deviceId == id }?.circleIds.orEmpty().filter { it != MemberDirectory.DIRECT_CONTACTS }
        val cap = if (isCircle) null else sharing.circleLevelFor(id, personCircles)
        LevelDialog(
            title = mt.chooseLevel(name), current = if (isCircle) levels[id] ?: default else persons[id],
            followLabel = if (isCircle) null else mt.followsCircles, onDismiss = { editing = null },
            levels = if (cap == null) ShareLevel.entries else ShareLevel.entries.filter { it.wire <= cap.wire },
        ) { level, untilMillis ->
            if (isCircle) {
                sharing.setLevel(id, level, untilMillis, members = people.filter { id in it.circleIds }.mapNotNull { it.deviceId })
            } else {
                if (level != null && level.always) sharing.approve(listOf(id))
                sharing.setPersonLevel(id, level, untilMillis)
            }
            editing = null
        }
    }
}
