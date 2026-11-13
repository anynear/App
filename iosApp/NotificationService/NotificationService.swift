import UserNotifications
import Shared

/**
 Wake-up from an anynear relay (ADR 0009, ANDP §10). It carries only a fixed text and the ANDP object of the new
 message (field „o“). The extension fetches the message itself via the ANMP core, decrypts it on the
 iPhone and replaces the text with circle or person and „Anna: …“. If that fails, the fixed text stays.
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
        NotificationServiceKt.handleWake(objectId: objectId) { [weak self] found in
            DispatchQueue.main.async {
                guard let self, let handler = self.contentHandler else { return }
                if let found {
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
                    }
                } else {
                    // Nothing found: fixed text in its own group so that the app can clear it away on reading.
                    content.threadIdentifier = NotificationServiceKt.WAKE_THREAD
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
