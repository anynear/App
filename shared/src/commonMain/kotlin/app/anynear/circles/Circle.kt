package app.anynear.circles

import app.anynear.images.ImageRef

/**
 * A circle (e.g. family, friends) as the UI shows it: image of the circle in the ANMP core
 * (`CircleState`, ADR 0004, 0012). Own key and own space at the relays; circles are completely
 * separated from each other. Over radio all circles go over the one channel `anynear` (ADR 0051), there is no
 * channel per circle.
 */
data class Circle(
    /** Stays the same across key changes; is in the invitation link. */
    val id: String,
    val name: String,
    /** Circle secret (32 bytes, Base64url). Secret: only in the protected storage and in the invitation link. */
    val secret: String,
    /** Someone from the circle removed us: history stays readable, writing is no longer possible. */
    val removed: Boolean = false,
    /** Key version (ADR 0012). */
    val epoch: Int = 0,
    /** Key before the last change, for radio packets that still arrive with it. */
    val previousSecret: String? = null,
    val nameTs: Long = 0,
    /** Uploaded circle image, `null` = generated image. */
    val image: ImageRef? = null,
    val imageTs: Long = 0,
    /** When we joined. */
    val joinedAt: Long = 0,
    /** Joined via an invitation, history not completely there yet (ADR 0014). */
    val historyPending: Boolean = false,
) {
    override fun toString(): String = "Circle($id, $name, ***)"
}
