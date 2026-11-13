package app.anynear.images

/**
 * Generated image of a circle or a person (ADR 0012), computed from an ID. Every device
 * computes the same pattern for the same ID; nothing is transmitted.
 *
 * - Circle: three rings of [SEGMENTS] arc segments each, every ring rotated by [ringOffsets].
 * - Person: mirrored grid of 5 × 5 cells ([cells], row by row).
 *
 * [tone] picks the colour pair from the brand; the uniqueness comes from the pattern.
 */
class AvatarPattern private constructor(
    val tone: Int,
    /** Circle: per ring the set arc segments as bits (inside to outside). */
    val rings: List<Int>,
    /** Circle: rotation per ring in eighths of an arc segment (0..7). */
    val ringOffsets: List<Int>,
    /** Person: 25 cells, row by row; mirrored left and right. */
    val cells: List<Boolean>,
) {
    override fun equals(other: Any?): Boolean =
        other is AvatarPattern && tone == other.tone && rings == other.rings && ringOffsets == other.ringOffsets && cells == other.cells

    override fun hashCode(): Int = ((tone * 31 + rings.hashCode()) * 31 + ringOffsets.hashCode()) * 31 + cells.hashCode()

    companion object {
        const val SEGMENTS = 8
        const val GRID = 5

        fun circle(seed: String, tones: Int): AvatarPattern {
            val bits = Bits(seed, salt = "kreis")
            val tone = bits.take(8) % tones
            val rings = List(3) { ring ->
                // At least three arc segments per ring, otherwise the image looks empty.
                var mask = bits.take(SEGMENTS)
                var extra = 0
                while (mask.countOneBits() < 3) mask = mask or (1 shl ((ring * 3 + extra++) % SEGMENTS))
                mask
            }
            val offsets = List(3) { bits.take(3) }
            return AvatarPattern(tone, rings, offsets, emptyList())
        }

        fun person(seed: String, tones: Int): AvatarPattern {
            val bits = Bits(seed, salt = "person")
            val tone = bits.take(8) % tones
            val half = (GRID + 1) / 2
            // 15 bits for the left half including the middle; at least 6 set so that a figure emerges.
            var mask = bits.take(GRID * half)
            var extra = 0
            while (mask.countOneBits() < 6) mask = mask or (1 shl ((extra++ * 7) % (GRID * half)))
            val cells = List(GRID * GRID) { i ->
                val row = i / GRID
                val col = i % GRID
                val c = if (col < half) col else GRID - 1 - col
                (mask shr (row * half + c)) and 1 == 1
            }
            return AvatarPattern(tone, emptyList(), emptyList(), cells)
        }
    }
}

/** Evenly distributed bits from an ID (FNV-1a, then SplitMix64), the same on all platforms. */
private class Bits(seed: String, salt: String) {
    private var state: Long = run {
        var h = -0x340d631b7bdddcdbL // FNV offset basis
        "$salt:$seed".encodeToByteArray().forEach { b ->
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        h
    }
    private var buffer = 0L
    private var left = 0

    private fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun take(count: Int): Int {
        require(count in 1..31)
        if (left < count) {
            buffer = next()
            left = 64
        }
        val value = (buffer and ((1L shl count) - 1)).toInt()
        buffer = buffer ushr count
        left -= count
        return value
    }
}
