package app.anynear.app

import app.anynear.anmp.core.UpdateDemand

/** How this installation gets to a newer version (ADR 0096 §6). */
sealed interface UpdateWay {
    /** None known: debug build, an app store the app cannot ask, or nothing newer found yet. */
    data object None : UpdateWay

    /**
     * The app itself can install the version with build number [build]: Android `direct` with its own updater
     * (ADR 0017), Android `play` with Google Play In-App Updates (ADR 0089).
     */
    data class Install(val build: Long) : UpdateWay

    /**
     * The app cannot ask which version is there, but can open the place where it is (iOS: TestFlight or the page
     * „App laden“ of the website). Blocks without checking (ADR 0096, risks).
     */
    data object OpenPage : UpdateWay
}

/**
 * What the app shows because the network asks for a newer build (ADR 0096 §4, §5) or the relays no longer fully
 * serve it (`clients.min`); one band for both, or the block. The notice about a new version without any rule comes from
 * the store or updater itself ([AppUpdateState.Available], ADR 0096 §7).
 */
sealed interface UpdateNotice {
    /** Nothing. */
    data object None : UpdateNotice

    /** Band „Ab <[from]> geht anynear nur noch mit der neuen Version.“ */
    data class Soon(val from: Long) : UpdateNotice

    /**
     * Band „Bitte aktualisiere anynear, damit deine Nachrichten weiter ankommen.“: the relays no longer fully serve
     * this version (`clients.min`, ADR 0061 decision 13), or `app.min` applies but there is no way to block with.
     */
    data object Needed : UpdateNotice

    /** Full screen over the app until it is updated; [min] is the build it needs at least. */
    data class Block(val min: Long) : UpdateNotice

    val isBanner: Boolean get() = this is Soon || this is Needed
}

/**
 * Combines the rule of the network ([demand], from `app` in the radio list), the way to a new version ([way]) and
 * `clients.min` ([clientsBehind]) into what the app shows. Blocks only with a way (ADR 0096 §6): with the own
 * updater or Google Play only if they offer a build ≥ `min`, on iOS always; never in a [debug] build.
 */
fun updateNotice(demand: UpdateDemand, way: UpdateWay, debug: Boolean, clientsBehind: Boolean = false): UpdateNotice = when (demand) {
    is UpdateDemand.Required -> {
        val canUpdate = when (way) {
            UpdateWay.OpenPage -> true
            is UpdateWay.Install -> way.build >= demand.min
            UpdateWay.None -> false
        }
        if (canUpdate && !debug) UpdateNotice.Block(demand.min) else UpdateNotice.Needed
    }
    is UpdateDemand.Soon -> UpdateNotice.Soon(demand.from)
    UpdateDemand.None -> if (clientsBehind) UpdateNotice.Needed else UpdateNotice.None
}

/**
 * „Später“ on the band (ADR 0096 §4): the band without a date stays hidden until the next start of the app
 * ([laterThisRun]), the one with a date only until the next day ([laterToday]). The block cannot be put off.
 */
fun UpdateNotice.afterLater(laterThisRun: Boolean, laterToday: Boolean): UpdateNotice = when (this) {
    UpdateNotice.Needed -> if (laterThisRun) UpdateNotice.None else this
    is UpdateNotice.Soon -> if (laterToday) UpdateNotice.None else this
    else -> this
}
