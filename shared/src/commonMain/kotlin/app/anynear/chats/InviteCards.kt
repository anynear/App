package app.anynear.chats

import app.anynear.i18n.AlertTextsDe
import app.anynear.i18n.AlertTextsEn
import app.anynear.i18n.StatusTextsDe
import app.anynear.i18n.StatusTextsEn
import app.anynear.i18n.texts

/** Kind of an invitation link: channel (`/a#`), contact (`/c#`), circle (`/k#`) or new own device (`/p#`, ADR 0042 §5). */
enum class InviteKind { Channel, Contact, Circle, Device }

/** Invitation link in the message text: [range] in the text, [url] as sent. */
data class InviteLink(val range: IntRange, val url: String, val kind: InviteKind)

/** Our links, also those with broken content; the network is only checked by [app.anynear.app.AppController.inviteCard]. */
private val INVITE_LINK = Regex("""(?i)^https://anynear\.(?:app|net)/([ackp])/?#""")

/** Invitation links in [text], in the order in which they appear. Other web addresses stay normal links. */
fun findInviteLinks(text: String): List<InviteLink> = findLinks(text).mapNotNull { link ->
    val kind = when (INVITE_LINK.find(link.url)?.groupValues?.get(1)?.lowercase()) {
        "a" -> InviteKind.Channel
        "c" -> InviteKind.Contact
        "k" -> InviteKind.Circle
        "p" -> InviteKind.Device
        else -> return@mapNotNull null
    }
    InviteLink(link.range, link.url, kind)
}

/**
 * What stays visible of [text] without the invitation links: the card replaces the link (ADR 0007, no link to be seen).
 * Empty if the message consists only of links. The stored text stays unchanged, copying gives the link.
 */
fun withoutInviteLinks(text: String, links: List<InviteLink> = findInviteLinks(text)): String {
    if (links.isEmpty()) return text
    val rest = buildString {
        var last = 0
        links.sortedBy { it.range.first }.forEach { link ->
            append(text, last, link.range.first)
            last = link.range.last + 1
        }
        append(text, last, text.length)
    }
    return rest.lines()
        .map { it.replace(SPACES, " ").trimEnd() }
        .joinToString("\n")
        .replace(BLANK_LINES, "\n\n")
        .trim()
        // „Komm in meinen Kreis bei anynear: <Link>“ otherwise ends with a colon leading nowhere.
        .trimEnd(':', ' ', '-', '–')
        // Forwarded sentence from „Teilen“: the card says the same, otherwise it would be there twice.
        .let { rest -> if (SHARE_SENTENCES.any { it.matches(rest) }) "" else rest }
}

private val SPACES = Regex("""[ \t]{2,}""")
private val BLANK_LINES = Regex("""\n{3,}""")

/**
 * The sentences that „Teilen“ puts before an invitation link (both languages), as patterns without the link: names of
 * person, channel and circle may be arbitrary.
 */
private val SHARE_SENTENCES: List<Regex> by lazy {
    val name = "\u0001"
    val link = "\u0002"
    listOf(
        AlertTextsDe.shareChannel(name, name, link), AlertTextsEn.shareChannel(name, name, link),
        StatusTextsDe.shareInvite(name, link), StatusTextsEn.shareInvite(name, link),
        StatusTextsDe.reinviteMessage(name, link), StatusTextsEn.reinviteMessage(name, link),
        StatusTextsDe.shareOwnContact(name, link), StatusTextsEn.shareOwnContact(name, link),
    ).map { template ->
        val sentence = template.substringBefore(link).trimEnd(':', ' ', '-', '–')
        Regex(sentence.split(name).joinToString(".+") { Regex.escape(it) })
    }
}

/** What an invitation link shows in the chat; determined by [app.anynear.app.AppController.inviteCard]. */
sealed interface InviteCard {
    /** Channel (ADR 0062); [curated]: official warnings. [subscribed]: already subscribed (or own channel). */
    data class Channel(val name: String, val curated: Boolean, val subscribed: Boolean) : InviteCard

    /** Contact; [known]: already in the own contacts, [self]: the own contact. */
    data class Contact(val name: String, val known: Boolean, val self: Boolean) : InviteCard

    /** Circle; [member]: already in it. */
    data class Circle(val name: String, val member: Boolean) : InviteCard

    /** Code of a new device ([name]); [added]: already one of the own devices, [self]: the code of this device. */
    data class Device(val name: String, val added: Boolean, val self: Boolean) : InviteCard

    /** Broken, incomplete or from another network: cannot be opened. */
    data object Broken : InviteCard
}

/**
 * Content of the card: one sentence, below it possibly a state ([status], e.g. „Abonniert“) and the one action
 * ([action]); without [action] the card cannot be tapped.
 */
data class InviteCardView(val sentence: String, val status: String?, val action: String?)

/**
 * Sentence and button of a card. [sender] is the name shown in the speech bubble; [own]: own message, then
 * „Du hast …“. Names do not reveal a gender, hence without „sein/ihr“.
 */
fun inviteCardView(card: InviteCard, sender: String, own: Boolean): InviteCardView {
    val t = texts.chat
    return when (card) {
        is InviteCard.Channel -> InviteCardView(
            sentence = when {
                card.curated && own -> t.cardOfficialOwn
                card.curated -> t.cardOfficial(sender)
                own -> t.cardChannelOwn(card.name)
                else -> t.cardChannel(sender, card.name)
            },
            status = t.cardSubscribed.takeIf { card.subscribed },
            action = if (card.subscribed) t.cardOpen else t.cardSubscribe,
        )
        is InviteCard.Contact -> InviteCardView(
            sentence = when {
                own && card.self -> t.cardContactSelfOwn
                own -> t.cardContactOwn(card.name)
                // „Alex teilt einen Kontakt: Alex“ reads as a duplicate.
                card.name == sender -> t.cardContactSelf(sender)
                else -> t.cardContact(sender, card.name)
            },
            status = when {
                card.self -> t.cardThisIsYou
                card.known -> t.cardInContacts
                else -> null
            },
            action = when {
                card.self -> null
                card.known -> t.cardOpen
                else -> t.cardAdd
            },
        )
        is InviteCard.Circle -> InviteCardView(
            sentence = if (own) t.cardCircleOwn(card.name) else t.cardCircle(sender, card.name),
            status = t.cardInCircle.takeIf { card.member },
            action = if (card.member) t.cardOpen else t.cardJoin,
        )
        is InviteCard.Device -> InviteCardView(
            sentence = when {
                own -> t.cardDeviceOwn(card.name)
                else -> t.cardDevice(sender, card.name)
            },
            status = when {
                card.self -> t.cardThisDevice
                card.added -> t.cardDeviceAdded
                else -> null
            },
            // Only useful for the own person; adding asks for the device lock and checks the code again.
            action = t.cardAddDevice.takeIf { !card.added && !card.self },
        )
        InviteCard.Broken -> InviteCardView(t.cardBroken, status = null, action = null)
    }
}
