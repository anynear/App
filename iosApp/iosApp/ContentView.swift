import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/**
 On the Mac the window can be resized freely; it does not get smaller than a phone, otherwise the pages no longer fit.
 On iPhone and iPad this limit does not exist (sizeRestrictions is `nil` there).
 */
private func limitWindowSize() {
    for case let scene as UIWindowScene in UIApplication.shared.connectedScenes {
        scene.sizeRestrictions?.minimumSize = CGSize(width: 400, height: 640)
    }
}

struct ContentView: View {
    var body: some View {
        // All the way to the edge: the shared code handles status bar, home indicator and keyboard itself
        // (WindowInsets in App.kt). Otherwise iOS shows the white window background there.
        ComposeView()
            .ignoresSafeArea()
            .onAppear(perform: limitWindowSize)
            // Invitation, contact, device and channel links from anynear.app and anynear.net (Universal Links, ADR 0036, ADR 0061).
            // anynear://share: the Share Extension has stored something (iosApp/Share).
            .onOpenURL { url in
                if url.scheme == "anynear" {
                    MainViewControllerKt.receiveShared()
                } else {
                    MainViewControllerKt.handleIncomingUrl(url: url.absoluteString)
                }
            }
    }
}
