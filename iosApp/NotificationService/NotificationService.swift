import UserNotifications
import Shared

/**
 Wake-up from an anynear relay (ADR 0009, ANDP §10). It carries only a fixed text and the ANDP object of the new
 message (field „o“). The extension fetches the message itself via the ANMP core, decrypts it on the
 iPhone and replaces the text with circle or person and „Anna: …“. If that fails, the fixed text stays. If the
 fetch was complete and there is nothing to show, the wake-up is swallowed (with Apple's filtering entitlement) or
 at least shown quietly (ADR 0117).
 */
final class NotificationService: UNNotificationServiceExtension {
    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var fallback: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        let content = (request.content.mutableCopy() as? UNMutableNotificationContent) ?? UNMutableNotificationContent()
        self.contentHandler = contentHandler
        fallback = content
        content.threadIdentifier = NotificationServiceKt.WAKE_THREAD
        let objectId = request.content.userInfo["o"] as? String
        NotificationServiceKt.handleWake(objectId: objectId) { [weak self] outcome in
            DispatchQueue.main.async {
                guard let self, let handler = self.contentHandler else { return }
                if outcome.found == nil && outcome.nothingToShow && NotificationServiceKt.filterEmptyWakes {
                    // Nothing to show (ADR 0117): with the filtering entitlement an empty content swallows the wake-up.
                    self.contentHandler = nil
                    handler(UNNotificationContent())
                    return
                }
                if let found = outcome.found {
                    content.title = found.title
                    content.body = found.body
                    content.threadIdentifier = found.key
                    // Counter on the app icon, except for silent alerts (channel with notifications off).
                    if found.level != "passive" {
                        content.badge = NSNumber(value: WakeBadgeKt.nextWakeBadge())
                    }
                    if let channel = found.alertChannel {
                        // Alert (ADR 0062): level and sound of the channel, tapping opens the channel.
                        content.userInfo["alertChannel"] = channel
                        NotificationServiceKt.applyWake(content: content, found: found)
                    } else {
                        content.userInfo["conversation"] = found.key
                        // „Antworten“ for circle chats and direct messages, sent by the app in the background.
                        if let category = NotificationServiceKt.replyCategory(key: found.key) {
                            content.categoryIdentifier = category
                        }
                    }
                } else {
                    // Nothing found: fixed text in its own group so that the app can clear it away on reading.
                    content.threadIdentifier = NotificationServiceKt.WAKE_THREAD
                    if outcome.nothingToShow {
                        // Certainly nothing new for this device, but no entitlement to swallow it: at least quiet,
                        // without sound and without lighting up the screen (ADR 0117).
                        content.sound = nil
                        content.interruptionLevel = .passive
                    }
                }
                self.contentHandler = nil
                handler(content)
            }
        }
    }

    // iOS is about to end the extension: show the fixed text.
    override func serviceExtensionTimeWillExpire() {
        if let handler = contentHandler, let fallback {
            contentHandler = nil
            handler(fallback)
        }
    }
}
