package app.anynear.alerts

import app.anynear.resources.Res
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Area for official warnings: the district the phone is roughly in. [kreis] is the first 5 digits of the
 * Amtlicher Regionalschlüssel (e.g. `06412` Frankfurt am Main), [name] is only there for display. Stays on the device.
 */
@Serializable
data class AlertRegion(val kreis: String, val name: String)

/** Approximate location in degrees (WGS 84). Does not leave the device. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * District of the location without a service on the internet: the district of the municipality with the nearest
 * centre. The table comes from the municipality register of the Statistisches Bundesamt (`scripts/regions.py`, source
 * in the README under licence). At a district border this can be off by one municipality; the approximate location
 * is only accurate to a few hundred metres up to a few kilometres anyway.
 */
class RegionTable private constructor(
    private val names: Map<String, String>,
    private val keys: Array<String>,
    private val latitudes: IntArray,
    private val longitudes: IntArray,
) {
    val size: Int get() = keys.size

    /** District for [point]; `null` outside Germany (nearest municipality centre further than [MAX_DISTANCE_KM]). */
    fun nearest(point: GeoPoint): AlertRegion? {
        // At these distances the flat approximation suffices: degrees of longitude shrink with the cosine of latitude.
        val shrink = cos(point.latitude * PI / 180)
        val lat = point.latitude * MICRO
        val lon = point.longitude * MICRO
        var best = -1
        var bestDistance = Double.MAX_VALUE
        for (i in keys.indices) {
            val dy = latitudes[i] - lat
            val dx = (longitudes[i] - lon) * shrink
            val d = dy * dy + dx * dx
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        if (best < 0 || sqrt(bestDistance) / MICRO * KM_PER_DEGREE > MAX_DISTANCE_KM) return null
        val kreis = keys[best]
        return AlertRegion(kreis, names[kreis] ?: kreis)
    }

    companion object {
        /** In Germany no place lies further away from the nearest municipality centre. */
        const val MAX_DISTANCE_KM = 30.0

        private const val MICRO = 1_000_000.0
        private const val KM_PER_DEGREE = 111.2

        /** From `kreise.csv` (`key;name`) and `gemeinden.csv` (`key;latitude;longitude` in millionths of a degree). */
        fun parse(kreise: String, gemeinden: String): RegionTable {
            val names = kreise.lineSequence().filter { it.isNotBlank() }.associate { line ->
                line.substringBefore(';') to line.substringAfter(';')
            }
            val rows = gemeinden.lineSequence().filter { it.isNotBlank() }.map { it.split(';') }.toList()
            return RegionTable(
                names,
                Array(rows.size) { rows[it][0] },
                IntArray(rows.size) { rows[it][1].toInt() },
                IntArray(rows.size) { rows[it][2].toInt() },
            )
        }

        /** Table from the app's resources. */
        @OptIn(ExperimentalResourceApi::class)
        suspend fun load(): RegionTable = parse(
            Res.readBytes("files/regions/kreise.csv").decodeToString(),
            Res.readBytes("files/regions/gemeinden.csv").decodeToString(),
        )
    }
}
