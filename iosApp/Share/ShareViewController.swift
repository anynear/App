import UIKit
import UniformTypeIdentifiers

/**
 Sharing from other apps (links, text, photos, files). The extension knows neither circles nor contacts
 and sends nothing: it puts the shared item into the app group (`Shared/<id>/item.json` and the files)
 and opens anynear. The app asks „An wen soll das gehen?“ and puts it as a draft into the conversation
 (`SharedInbox.ios.kt`). If the app cannot be opened, it picks it up the next time it is opened.
 */
final class ShareViewController: UIViewController {
    private static let appGroup = "group.app.anynear"
    // One file more than a message carries: the app then shows the notice „höchstens …“.
    private static let maxFiles = 6
    private var started = false

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        Task { @MainActor in
            if await store() {
                if await openApp() { finish() } else { tell("shared") }
            } else {
                tell("failed")
            }
        }
    }

    /** Everything shared into its own folder of the app group. `false` if nothing was taken over. */
    private func store() async -> Bool {
        guard let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroup) else { return false }
        let dir = base.appendingPathComponent("Shared", isDirectory: true).appendingPathComponent(UUID().uuidString, isDirectory: true)
        let partial = dir.appendingPathExtension("part")
        do {
            try FileManager.default.createDirectory(at: partial, withIntermediateDirectories: true)
        } catch {
            return false
        }
        var texts: [String] = []
        var files: [[String: Any]] = []
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) || isFile(provider) {
                guard files.count < Self.maxFiles else { continue }
                let image = provider.hasItemConformingToTypeIdentifier(UTType.image.identifier)
                if let file = await save(provider, image: image, index: files.count, into: partial) {
                    files.append(file)
                }
            } else if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                if let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                    texts.append(url.absoluteString)
                }
            } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                if let text = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
                    texts.append(text)
                }
            }
        }
        // Some apps send the link in the text as well: not twice.
        let text = texts.reduce(into: [String]()) { all, t in
            if !all.contains(where: { $0.contains(t) }) { all.append(t) }
        }.joined(separator: "\n")
        guard !text.isEmpty || !files.isEmpty,
              let json = try? JSONSerialization.data(withJSONObject: ["text": text, "files": files]),
              (try? json.write(to: partial.appendingPathComponent("item.json"))) != nil,
              // Rename only when done: the app only reads complete folders.
              (try? FileManager.default.moveItem(at: partial, to: dir)) != nil
        else {
            try? FileManager.default.removeItem(at: partial)
            return false
        }
        return true
    }

    /** A file without image and without web address, e.g. a PDF from „Dateien“. */
    private func isFile(_ provider: NSItemProvider) -> Bool {
        provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier)
            || (!provider.hasItemConformingToTypeIdentifier(UTType.url.identifier)
                && !provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier)
                && provider.hasItemConformingToTypeIdentifier(UTType.data.identifier))
    }

    private func save(_ provider: NSItemProvider, image: Bool, index: Int, into dir: URL) async -> [String: Any]? {
        let type = image ? UTType.image.identifier
            : provider.registeredTypeIdentifiers.first { $0 != UTType.fileURL.identifier && $0 != UTType.url.identifier } ?? UTType.data.identifier
        let copied: (String, String)? = await withCheckedContinuation { done in
            // The file is only available during the call, so copy right away.
            provider.loadFileRepresentation(forTypeIdentifier: type) { url, _ in
                guard let url else { done.resume(returning: nil); return }
                let stored = "\(index)-\(url.lastPathComponent)"
                let ok = (try? FileManager.default.copyItem(at: url, to: dir.appendingPathComponent(stored))) != nil
                done.resume(returning: ok ? (stored, url.lastPathComponent) : nil)
            }
        }
        if let copied {
            let (stored, name) = copied
            return ["file": stored, "name": provider.suggestedName.map { withExtension($0, from: name) } ?? name, "image": image]
        }
        // Screenshots and edited images often come only as UIImage.
        guard image, let item = try? await provider.loadItem(forTypeIdentifier: UTType.image.identifier) else { return nil }
        let data = (item as? UIImage)?.jpegData(compressionQuality: 0.9) ?? (item as? Data)
        let stored = "\(index)-image.jpg"
        guard let data, (try? data.write(to: dir.appendingPathComponent(stored))) != nil else { return nil }
        return ["file": stored, "name": "", "image": true]
    }

    /** „Rechnung“ and „x.pdf“ gives „Rechnung.pdf“: name without extension from the app, extension from the file. */
    private func withExtension(_ name: String, from file: String) -> String {
        let ext = (file as NSString).pathExtension
        return ext.isEmpty || !(name as NSString).pathExtension.isEmpty ? name : "\(name).\(ext)"
    }

    /**
     Open anynear. Extensions may not call `UIApplication.open` directly; via the responder
     chain one reaches the app anyway. If that fails, the extension says how to continue.
     */
    private func openApp() async -> Bool {
        guard let url = URL(string: "anynear://share") else { return false }
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                let selector = NSSelectorFromString("openURL:options:completionHandler:")
                guard let method = application.method(for: selector) else { return false }
                typealias Open = @convention(c) (AnyObject, Selector, URL, NSDictionary, ((Bool) -> Void)?) -> Void
                let open = unsafeBitCast(method, to: Open.self)
                return await withCheckedContinuation { done in
                    open(application, selector, url, NSDictionary()) { done.resume(returning: $0) }
                }
            }
            responder = current.next
        }
        return false
    }

    private func tell(_ key: String) {
        let alert = UIAlertController(
            title: NSLocalizedString("\(key).title", comment: ""),
            message: NSLocalizedString("\(key).message", comment: ""),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: NSLocalizedString("ok", comment: ""), style: .default) { [weak self] _ in self?.finish() })
        present(alert, animated: true)
    }

    private func finish() {
        extensionContext?.completeRequest(returningItems: nil)
    }
}
