package app.anynear

/**
 * Fixed product defaults of anynear.
 *
 * Deliberately contains no secret keys: circle keys are created on the phone and are
 * only passed on by invitation link.
 */
object AppConfig {
    /** Installs Meshtastic onto a radio from the browser (Chrome on a computer), for radios without it (e.g. a new Heltec). */
    const val MESHTASTIC_FLASHER_URL = "https://flasher.meshtastic.org"

    /**
     * Setting up a radio with a USB cable in the webapp (ADR 0095 §5), for the iPhone, which has no USB access: on a
     * computer in Chrome or Edge.
     */
    const val WEB_RADIO_SETUP_URL = "https://web.anynear.app/radio"

    /** Name of the app in all texts (ADR 0025). Identifiers in the code are still called anynear, the protocol ANMP and ANDP (ADR 0034). */
    const val APP_NAME = "anynear"

    /** Page „App laden“ (Google Play, iPhone invitation), also for reinstalling and the move to a new app ID (ADR 0025,
     *  ADR 0089). The website forwards to the page in the language of the browser. */
    const val APP_PAGE_URL = "https://anynear.app/app-laden/"

    /** Page „App laden“ by language, the way to the App Store on iOS (Docs ADR 0096 §6) until there is a store address. */
    const val APP_PAGE_URL_DE = "https://anynear.app/de/app-laden/"
    const val APP_PAGE_URL_EN = "https://anynear.app/en/get-the-app/"

    /** Opens the app TestFlight on iOS (Docs ADR 0096 §6). */
    const val TESTFLIGHT_URL = "itms-beta://"

    /** Subscription plans with expected prices (ADR 0076), from the website; prices are never in the app. */
    const val ABO_PREVIEW_URL = "https://anynear.app/v1/abo.json"

    /**
     * Name of the channel into which the app of the support identity sends every new request as an alert (ADR 0065).
     * Only applies to a channel whose owner is the person; whoever has no such channel notices nothing of it.
     */
    const val SUPPORT_ALERT_CHANNEL = "anynear-support"

    /** Here the website delivers the current support link (ADR 0084 §7); [SUPPORT_CONTACT_LINK] is only the fallback. */
    const val SUPPORT_LINK_URL = "https://anynear.app/v1/support.json"

    /** Name of the support identity; a loaded link with a different name is not accepted. */
    const val SUPPORT_NAME = "anynear Support"

    /**
     * Contact link of the support identity (support as chat, ADR 0065): every request from “Fehlerbehebung” or
     * “Hilfe” becomes its own small circle with it. Public like every contact link. `null`: not set up yet,
     * then sending is off and the form says that support will be reachable soon. Identity “anynear Support”,
     * link from the identity's web app (09.11.2026) with mailbox key (field 8, ADR 0084) so that each of its devices
     * opens requests. Link for publishing, write permission until 09.11.2027 (ADR 0084 §6); loaded
     * first is [SUPPORT_LINK_URL], this one here is the fallback.
     */
    val SUPPORT_CONTACT_LINK: String? = "https://anynear.app/c#qAABAVggro9NwRU2zyAA-5xRZy0U48TOfL5kGHDZABeekQ1utVkCb2FueW5lYXIgU3VwcG9ydANYIMaQxOzgmTT_-VwDRC6yHLdYOya-uEp6TQTpodJKQ_q-BFggvcvp_7PbSPpvn7NZgwhMyP-xL9EQj1I4ME2zwQSYnXgFWCBcjZlP2w-YrAKcMHAyOoMOhXX-xldLtLkuesGNXF_1qAZYd4JYMqQBWCBcjZlP2w-YrAKcMHAyOoMOhXX-xldLtLkuesGNXF_1qAIEBRps0twtBhpq8ahxWECm6kvVaxdW8yYOgIVdDpEfBdGRTezljVWrNWbtpLYekP0Fzn01VUbkZC2--GJFmT05pgoG4BA__4O1Sd9m3uoDCFggQFwlvXXLrQTmBmyg9vtLWOhXUZrZv1UQwyVt24GS8wE"

    /**
     * Invitation link of the curated channel with official warnings from NINA (ADR 0062 §7, implementation plan §5).
     * The read key and read grant in it are public. Created with `relay nina-channel` (relay); the owner
     * key lives only on the relays from `NINA_RELAYS`.
     * Goes to the core as `curatedChannels` (AppNet); only channels listed there count as official.
     * Newly created on 05.11.2026 for the anynear network (ADR 0061, procedure A2).
     */
    val OFFICIAL_ALERTS_LINK: String? = "https://anynear.app/a#pwABAVggro9NwRU2zyAA-5xRZy0U48TOfL5kGHDZABeekQ1utVkCeCtudDdNdV9rQmJfTFE4YU5VeTd1b1ZRbEM4NmRhcThfQXlaT0c0S051WUpzA25XYXJudW5nZW4gTklOQQRYIP1won4MxYXtP4Qd71ghHpSv6Aitf81MsalUYcok2EHqBXifZ2xneXBBRllJSjdlekx2NUFXX3kwUEdqVk11N3FGVUpRdk9uV3F2UHdNbVRodUNqYm1DYkFnTUZHdlNHVndBR0dtcnNLUjFZUUdUc2l2d1lWcVMtRUFCUUViclZlaEx1TU9ZQU5jSjNiME1VZlhnZnJVVzZfWU4wM2tDNWROWTNLMWlkZG1zc2JyV2pldDdIUmlvUkJJZ2RDMmxwaXdFBvU"

    /**
     * iOS: alarms as “Critical Alert”, which override mute switch and Focus (ADR 0062 §6). Needs the entitlement
     * `com.apple.developer.usernotifications.critical-alerts`, which may only go into the project after approval by
     * Apple; until then off, alarms come as “Time Sensitive”.
     */
    const val CRITICAL_ALERTS = false

    /** Former app ID (PYRSA, until the cut-over to anynear). As long as it is installed, the app offers to delete it (ADR 0061). */
    const val OLD_APP_ID = "com.pyrsa.pyrsa"

    /**
     * Send ANMP on channel 0 (ADR 0054 §1, implementation plan radio bridge phase 1), always on `PRIVATE_APP`: when
     * channel 0 is open (default key, no own name), packets go there, otherwise in the radio channel `anynear`;
     * receiving works on both. On since 11.11.2026, before the store release, so that community gateways can uplink
     * our packets. The switch is meant to come from the relays later (`channel0` in the `radio list`, phase 6) so
     * that it can be flipped without an app update.
     */
    const val RADIO_CHANNEL0 = true

    /**
     * Channels of earlier apps that the app clears away when connecting: the radio channel `PYRSA` (ADR 0051, replaced
     * by `anynear`, ADR 0061 decision 6), circle channels `PYRSA-xxxxx` (before ADR 0051) and the channels of PINA
     * before ANMP 1 (`PINA`, `PINA-xxxxxx`, ADR 0034 §4). The primary channel is never among them.
     */
    fun isOldChannelName(name: String?): Boolean =
        name == app.anynear.anmp.AnmpInfo.LEGACY_RADIO_CHANNEL_NAME || app.anynear.anmp.AnmpKeys.isLegacyChannelName(name) ||
            name == "PINA" || (name != null && name.startsWith("PINA-") && name.length <= 11)

    /**
     * Hessenmesh radio profile according to meshhessen.de (checked on 18.10.2026), part of the device profile
     * ([app.anynear.mesh.RadioProfile], ADR 0006). It is only written on factory-new
     * devices or after consent in the dialog “Funkgerät einrichten”.
     */
    object Hessenmesh {
        const val FREQUENCY_MHZ = 869.525f

        /** Deliberately 5 instead of 3: with 3 hops messages did not arrive in the field test (ADR 0006). */
        const val HOP_LIMIT = 5
    }

    /**
     * Public channel of the Meshhessen community (ADR 0055). Name and key are published as a channel link on
     * meshhessen.de; the key is an identifier of the channel, not a secret. Only name and
     * key are adopted, all radio values come from the app's radio profile.
     */
    object MeshHessen {
        const val NAME = "Mesh Hessen"

        /**
         * Key (32 bytes) from the channel link on meshhessen.de. The app offers the
         * channel according to ADR 0054 §1a.
         */
        val psk: ByteArray = byteArrayOf(
            -6, -28, -52, 17, -93, -111, -18, 25, 42, 105, 123, -2, 13, 19, -114, 17,
            -34, 65, -122, -16, 8, 65, -113, -62, 103, -15, -21, -30, -54, 25, 112, -27,
        )

        val isKnown: Boolean get() = psk.size == 32
    }
}
