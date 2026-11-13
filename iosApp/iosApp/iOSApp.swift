import MetricKit
import SwiftUI
import UIKit
import UserNotifications
import Shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Crash reports first, so that a crash at start is included too (ADR 0082).
        MainViewControllerKt.installCrashReporting()
        // Before the first access to the shared code: passwords need keychain and Face ID (ADR 0039).
        MainViewControllerKt.setPasswordKeychain(keychain: PasswordBridge())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        // Menu bar on the Mac, shortcuts on the iPad with keyboard (hold ⌘ to see them). Texts from the shared catalog.
        .commands { AppCommands() }
        .onChange(of: scenePhase) { phase in
            // In the background the app builds notifications itself as long as it is still running.
            MainViewControllerKt.setAppVisible(visible: phase == .active)
        }
    }
}

/** Commands of the menu bar; executed by the shared code (AppCommand). */
struct AppCommands: Commands {
    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            item("NewMessage", "n")
        }
        CommandGroup(replacing: .appSettings) {
            item("Settings", ",")
        }
        CommandMenu(MainViewControllerKt.menuText(key: "GoTo")) {
            item("ShowChats", "1")
            item("ShowCircles", "2")
            item("ShowFiles", "3")
            item("ShowAlerts", "4")
            Divider()
            item("Back", "[")
        }
        CommandGroup(replacing: .help) {
            Button(MainViewControllerKt.menuText(key: "Help")) { MainViewControllerKt.menuCommand(name: "Help") }
        }
    }

    private func item(_ name: String, _ key: KeyEquivalent) -> some View {
        Button(MainViewControllerKt.menuText(key: name)) { MainViewControllerKt.menuCommand(name: name) }
            .keyboardShortcut(key, modifiers: .command)
    }
}

/**
 Apple's push service (ADR 0009): pass the token to the shared code, tapped notifications
 open the conversation. The text of the notification is built by the Notification Service Extension.
 */
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Find new documents automatically (ADR 0081): iOS requires the registration before the end of launch.
        MainViewControllerKt.registerBackgroundWork()
        // Crashes outside of Kotlin (signals, Swift) are delivered by MetricKit on the next start (ADR 0082).
        MXMetricManager.shared.add(CrashDiagnostics.shared)
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        #if DEBUG
        let sandbox = true
        #else
        let sandbox = false
        #endif
        MainViewControllerKt.onPushToken(token: token, sandbox: sandbox)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Without a token there are only notifications as long as the app runs. The chat works anyway.
    }

    // App open: no system notification for messages, the number in the overview is enough (ADR 0008). Every alert from
    // „Meldungen“ appears anyway, with its sound (ADR 0062 §5, §6), as in ntfy; silent only if the channel
    // has notifications off (wake-up that the extension cannot swallow).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let content = notification.request.content
        let alert = content.userInfo["alertChannel"] != nil
            || content.interruptionLevel == .timeSensitive
            || content.interruptionLevel == .critical
        // Show reminders (ADR 0085), e.g. to charge the radio, even when the app is open.
        let reminder = content.threadIdentifier == "reminders"
        completionHandler((alert && content.interruptionLevel != .passive) || reminder ? [.banner, .list, .sound] : [])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        if let channel = info["alertChannel"] as? String {
            MainViewControllerKt.onAlertOpened(channel: channel)
        } else if info["finder"] != nil {
            MainViewControllerKt.onFinderOpened()
        } else {
            MainViewControllerKt.onNotificationOpened(conversation: info["conversation"] as? String)
        }
        completionHandler()
    }
}

/**
 Crashes from MetricKit (ADR 0082): every `MXCrashDiagnostic` goes as a report to the shared code, which records it
 like its own. Sending only happens after „Senden“ in the app. The call stack is not symbolicated; for that
 the dSYMs are attached to the release.
 */
final class CrashDiagnostics: NSObject, MXMetricManagerSubscriber {
    static let shared = CrashDiagnostics()

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        for payload in payloads {
            for crash in payload.crashDiagnostics ?? [] {
                let signal = crash.signal?.intValue
                let parts: [String?] = [
                    crash.exceptionType.map { "exception type \($0)" },
                    crash.exceptionCode.map { "code \($0)" },
                    signal.map { "signal \($0)" },
                    crash.terminationReason,
                ]
                let error = "MXCrashDiagnostic: " + parts.compactMap { $0 }.joined(separator: ", ")
                let trace = String(data: crash.callStackTree.jsonRepresentation(), encoding: .utf8) ?? ""
                MainViewControllerKt.recordNativeCrash(
                    error: error,
                    trace: trace,
                    fromMillis: Int64(payload.timeStampBegin.timeIntervalSince1970 * 1000),
                    toMillis: Int64(payload.timeStampEnd.timeIntervalSince1970 * 1000),
                    abort: signal == 6
                )
            }
        }
    }
}
