# App (anynear)

**anynear** ([anynear.app](https://anynear.app); app ID and Kotlin package `app.anynear`) is
a messenger for families, friends, neighbors and close acquaintances who want to stay in touch in an emergency. It
keeps a circle of people connected: messages in the shared chat (“My circle”, German “Mein Kreis”) and directly,
storage, passwords and alerts, end-to-end encrypted, without an account and without a phone number. Messages travel
over the internet and, with a suitable radio, additionally as short texts without internet. A radio is not required
(see ADR 0003). This repository holds the Android and iOS app (Kotlin Multiplatform, Compose Multiplatform,
GPL-3.0-or-later).

This README describes the structure, the build and the state of the app. ADR numbers in this README and in code
comments (“ADR 0061”) are internal decision numbers; the decisions themselves are not in this repository. Methods
and known limits are described at <https://anynear.app/de/technik/> (German).

How to get the app (Android as an APK, iOS via TestFlight) is described at <https://anynear.app/en/get-the-app/>.
What has been tested on real radios and phones and what has not is listed in
[docs/hardware-tests.md](docs/hardware-tests.md).

Please report vulnerabilities as described in [SECURITY.md](SECURITY.md); for contributing see
[CONTRIBUTING.md](CONTRIBUTING.md).

## Structure

```
shared/      Kotlin Multiplatform: logic + Compose UI (Android & iOS)
  mesh/      MeshLink (boundary to the SDK), SdkMeshLink, BLE search, state mapping
  contacts/  Radio contacts (Meshtastic SharedContact) with key check
  data/      Device assignment, message history and settings (encrypted on the device), protected storage,
             My files (PersonalFiles)
  passwords/ Passwords: file `vault` under its own key, screen lock per platform, matching to websites
  circles/   Circles as a mirror of the ANMP core, own name and picture
  images/    Pictures of circles and people: generated pattern, reference to uploads, storage
  attachments/ Attachments: model, encrypted storage on the phone, shrinking photos
  core/      Connection to the ANMP core (ADR 0036): storage for the core, network (network list, development
             network), ANMP over radio, fresh start, key of the data on the device, text of the wake-up call (iOS)
  people/    Members and contacts (mirror of the core) and radio contacts as one person
  alerts/    Alerts and official warnings (district from the approximate location)
  lifesigns/ Check-ins (“Lebenszeichen”)
  app/       AppController (UI-independent app logic)
  chats/     Overview “Messages”: all conversations with the number of new messages
  i18n/      Text catalogs in German and English
  ui/        Messages, chat, contacts, settings, setup, theme and brand (Brand.kt)
androidApp/  Android shell: permissions, foreground service (receiving messages, BLE), notifications,
             autofill (autofill/)
iosApp/      Xcode project (SwiftUI shell around the Compose UI, push token, Notification Service Extension,
             password extension CredentialProvider/, keychain for passwords Passwords/)
docs/        Hardware test plan
scripts/     Generating the municipality table (regions.py) and release notes
tools/       Reproducing the text recognition models (ocr-models/)
```

Technology: Kotlin 2.4.10, Compose Multiplatform 1.12.1, [meshtastic-sdk](https://github.com/meshtastic/meshtastic-sdk) 0.2.0,
Kable 0.45.0.

## Building

Requirements:

- JDK 21 and Android SDK (platform 37); for iOS also Xcode.
- The neighboring repositories [`anynear/ANMP`](https://github.com/anynear/ANMP) and
  [`anynear/ANDP`](https://github.com/anynear/ANDP), checked out as `../ANMP` and `../ANDP` next to this folder. The
  app includes them via composite build (`settings.gradle.kts`); without them the Gradle configuration already fails
  with “ANMP nicht gefunden” (ANMP not found). Without both, the app cannot be built.

```bash
git clone https://github.com/anynear/App.git
git clone https://github.com/anynear/ANMP.git
git clone https://github.com/anynear/ANDP.git
cd App
```

```bash
./gradlew :shared:testAndroidHostTest      # unit tests
RELAY_E2E=1 ./gradlew :shared:testAndroidHostTest   # additionally against local relays (see below)
./gradlew :androidApp:assembleDirectDebug  # APK under androidApp/build/outputs/apk/direct/debug/
```

Two Android flavors with the same app ID and key: `direct` is the APK from the GitHub releases, which updates itself
(`AppUpdater`); `play` is the app bundle for Google Play (`./gradlew :androidApp:bundlePlayRelease`) without the
permissions Play does not allow there (installing packages). Flavor-only entries are in
`androidApp/src/direct/AndroidManifest.xml`.

**Internet path: ANMP core on ANDP (ADR 0034–0036, 0061).** Protocol and logic without UI live in the repos
`ANMP` (format and core, specification under `spec/`) and `ANDP` (relay network, `spec/`), both next to this one. The
app includes `ANMP/kotlin` via composite build (`settings.gradle.kts`), ANMP includes `ANDP/kotlin` itself. The build
looks for `../ANMP` or `../../ANMP` (for a git worktree one level deeper); otherwise use
`ANMP_DIR=<path to ANMP/kotlin>` or `-Panmp.dir=…`. ANMP finds ANDP next to itself or via `ANDP_DIR`. In CI
the workflows check out ANMP and ANDP (`main` each) as neighboring folders.

The core holds circles, contacts, the personal mailbox with grants, sending and fetching via the relays,
confirmations, history, key rotation, `want`/`have`, watch (iOS) and passing on at a shared
radio. The app implements its platform interfaces with its own stores (`core/CoreStores.kt`: secrets
`core.device.sign`, `core.device.x`, `core.inbox.owner`, `core.core.state` in protected storage, read positions and
network list in the encrypted key-value store, waiting objects in the folder `core`) and maps its
events to messages, notifications, system lines and states (`app/AppController.kt`). The storage
carries a version (`data/StorageVersion.kt`); every change to it comes as a migration with a test.

Network: network key and ID of the anynear network come from `AnmpCoreConfig.default`. Release builds embed the
signed network list as `net.json` (from `https://anynear.net/andp/v1/net`); without the file the app fetches it on
first start from the entry point `https://anynear.net`.

**Local network for development and tests:** The E2E suites expect five local relays speaking ANDP v1 on
`127.0.0.1:9081–9085` (operators a, a, b, c, d, open test key). The relay software is not part of the public
repositories; the suites are skipped without `RELAY_E2E=1`. The E2E suites (`ReadReceiptAppTest`,
`CircleAppEndToEndTest`) sign a network list themselves and offer it to the relays (`LocalNet.kt`):

```bash
RELAY_E2E=1 ./gradlew :shared:testAndroidHostTest --rerun
```

After that the relays know the list, and a debug app with `app.devNet=true` in `local.properties` uses
this network instead of the anynear network: entry point `https://10.0.2.2:9081` in the emulator or `127.0.0.1` in
the simulator (`app.devRelayHost` for a real phone); the internal addresses of the list go through the ports
on the computer (`core/AppNet.kt`).

**Testing in the emulator:** The emulator has no Bluetooth, but it finds a radio with
Wi-Fi switched on in the same network. On macOS the launching program (Terminal or Android Studio) must be
allowed under Privacy & Security → Local Network.

iOS: open `iosApp/iosApp.xcodeproj` in Xcode, set your own `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`
(for a fork also your own `BUNDLE_ID`, see [TRADEMARKS.md](TRADEMARKS.md)) and run. The Xcode build calls `:shared:embedAndSignAppleFrameworkForXcode`.
For notifications the app ID (and that of the extension `….notifications`) needs “Push Notifications” in the
Apple developer account, the app group `group.app.anynear`, Associated Domains
(`applinks:anynear.app`, `applinks:anynear.net`) and
Keychain Sharing; the entitlements are in `iosApp/iosApp/iosApp.entitlements` and
`iosApp/NotificationService/NotificationService.entitlements`. Without them the app runs, only without
notifications while the app is closed.

Mac (ADR 0047): The iOS app runs on Macs with Apple Silicon as an iPad app (“Designed for iPad”, the same
TestFlight build). There it has a menu bar (⌘N, ⌘,, ⌘1–⌘4, ⌘[; SwiftUI commands in `iOSApp.swift`, executed
via `AppCommand`), from a window width of 860 dp a sidebar instead of the tabs, Escape for back and Enter to send.
In Xcode choose the destination “My Mac (Designed for iPad)”.

## Preparing the radio

Not necessary: the app sets up the radio itself (ADR 0006). After every connection it compares
the device with the uniform device profile (`mesh/RadioProfile.kt`):

1. EU_868, SHORT_SLOW, 869.525 MHz (Hessenmesh), **hops 5**, transmit on, transmit power 0
   (the highest allowed for the region, limited by the hardware); `config_ok_to_mqtt` on, `ignore_mqtt` off
   (radio bridge, ADR 0054; in a separate step after region and preset).
2. Role CLIENT.
3. Device telemetry hourly on; environment, air quality, power and health sensors off.
4. Location off: GPS off, no fixed or smart position, position precision 0 on the
   primary channel and the radio channel `anynear`.
5. Device name `anynear.app | <word> <last 4 digits of the node ID>`, e.g. “anynear.app | Reh 1998”. Short name
   `A` + three characters, unique from the last four digits of the node ID (“A472”). The word comes from the
   node number; names already taken within radio range are skipped. The part after `anynear.app | ` can be changed.
   The app rewrites names from earlier versions (e.g. `anynear …`) during setup (ADR 0061).

Under “Settings → Radio → Advanced” the name can be changed (with a suggestion) and the
device profile can be applied again, even if the device already matches.

If something differs, “Set up radio” appears (new device: notice, otherwise a question, for
router devices with a warning). The app writes only what differs, checks against the freshly read state
and repeats until everything matches. Wi-Fi, Bluetooth, display
and foreign channels remain untouched. No managed mode. For all circles and contacts there is one
radio channel `anynear` with a public key from ANMP (ADR 0051, 0061); the app removes the channels of earlier
versions.

Over radio, circle and direct messages go as ANMP packets on `PRIVATE_APP` to everyone on channel 0 when it is open
(default key, no own name), otherwise in the channel `anynear`; only the ANMP content is confidential (ADR 0051, 0054,
0057). Receiving works on both. With `ok_to_mqtt` in the radio profile, community gateways upload these packets to
their brokers. The switch `AppConfig.RADIO_CHANNEL0` is meant to come from the relays later (ADR 0054).

## What the app can do

- Usable without a radio: start a circle or join by invitation, enter a name, chat. The
  circle key is created on the phone and is protected in the Android Keystore or the keychain.
- One chat, two paths: every message (including direct messages) goes over the internet to several
  independent mailboxes and, with a radio, additionally over radio. It appears only once at the recipient.
- One message format for all paths: ANMP (anynear Message Protocol, ADR 0021, 0034, specification and code in the
  repo `ANMP`). CBOR starting with `AN\x01`; the sender is the
  app installation, not the radio, so that several people can share one radio.
  Circle messages are encrypted with the circle key, direct ones only for the recipient's installation;
  over the internet always signed, over radio by default (can be switched off under Radio > Advanced).
  Over radio about 94 characters fit into a signed circle message (160 without signature,
  152 direct). Radios without anynear continue to get normal Meshtastic text.
- Shared radios (ADR 0022): the radio holds only a channel key derived from the circle secret.
  If a direct message arrives over radio at the phone of someone else on the shared device,
  that person's app hands it unread to the ANMP core, which puts it into the recipient's personal mailbox.
- Several separate circles (e.g. family, friends), each with its own key, space at the relays and
  radio channel. Invite via the share button (WhatsApp etc.) with a link `https://anynear.app/k#…` that opens the
  app directly; contacts via `https://anynear.app/c#…` (ADR 0036, 0061; links on anynear.net remain valid).
  Every member can invite, rename, change the circle picture, remove members and invalidate old links
  (new key); everything appears as a line in the circle chat (ADR 0012).
- Circles and people always have a picture: generated from the ID (circles angular with rings, people
  round with a box pattern) or uploaded as a photo. Pictures go over the internet only.
- Circle members are contacts automatically; people outside the circles via contact link.
  Direct messages go through the recipient's personal mailbox, end-to-end encrypted.
- Radio check (ADR 0053): “Check over radio” on every person with a known radio, “Who answers over radio?” in the
  circle and on the Radio page. A small packet with `want_ack` on the real path (channel `anynear`, hop limit of the
  radio profile); only the answer of the target radio counts. One sentence with name, signal and relays under
  “Learn more”. At most every 10 minutes per radio, group check once per hour, 10 s between packets.
- Optional device assignment under “Settings”: searches automatically via Bluetooth and on Wi-Fi (mDNS `_meshtastic._tcp`),
  then reconnects automatically. Address entry only collapsed under “Advanced” and only accepted after
  a successful connection check.
- Set up “My circle” directly in the app: “Start a new circle” (new random key) or
  join via invitation link. A radio paired later gets the circle channel
  automatically; if the device differs from the device profile, the app offers “Set up radio”.
- Overview “Messages” like in WhatsApp: all circles and direct messages one below the other, newest on top,
  new items bold with a yellow number (also on the tab at the bottom).
- Notifications without Google (Android): the app receives by itself in the foreground service, also after
  a restart of the phone; the notification is created on the phone, one per conversation, tapping opens the
  conversation. No FCM, no UnifiedPush needed. If battery optimization is on, a notice appears with a button
  leading straight to the setting. Android does not report the manufacturers' own power-saving features (Samsung
  “Sleeping apps”, Xiaomi “Autostart”); from Android 11 the app recognizes on opening, from the reasons of the last
  process exits, that it was stopped despite the foreground service, and shows the same notice with a way to the app info. Because Android pauses timers while the phone sleeps,
  an alarm (`WakeAlarm`, also in deep sleep, there at most about every 9 minutes) wakes the phone briefly every 15 minutes and
  restarts stalled fetches (`AnmpCore.wakeUp`); on start the log states how the previous process ended.
  In the background the app keeps a connection to a home relay (stream, ADR 0072): the relay reports new items, the
  app then fetches the affected mailbox once; on connecting and on the alarm it catches up on everything. Without a
  stream (relays with `stream.clients` off) it asks only one relay per mailbox and, after an empty answer, only again
  after 60 seconds. Under “Advanced → Log” the line `Stream: …` at the top shows home relay, disconnects by
  reason, switches and catch-up finds. Some things pause in the background: the search for radios
  on Wi-Fi runs only while the app is open, and a paired radio out of range is, after five
  failed attempts, searched for only every 5 minutes until the app is open again. iOS: the relays wake the iPhone via Apple's push service (watch, ANDP §10) with
  a fixed text and the identifier of the object; the Notification Service Extension fetches the message itself via the
  ANMP core (`wakeFetch`) and builds the text on the iPhone (not tested on a real iPhone, `docs/hardware-tests.md`).
- Messages with line breaks, emojis and clickable web addresses. Up to five photos or PDFs
  per message (at most 10 MB each): photos are shrunk and sent without location, everything
  is stored encrypted at the relays and loaded automatically at the recipient, with a preview
  in the chat. Over radio only the text goes with “📎 …”; the rest arrives as soon as there is internet. Whatever
  has not yet gone out over the internet waits, even across a restart (ADR 0011).
- Sharing from other apps: anynear appears in the system share menu (links, text, photos, files).
  The app asks “Who should get this?” and puts the shared item into the conversation as a draft; it is sent
  only with the send button. iOS: the Share Extension (`iosApp/Share`, `app.anynear.share`) puts the
  shared item into the app group and opens anynear via `anynear://share`. For TestFlight the identifier
  `app.anynear.share` with app group `group.app.anynear` must exist in Apple Developer.
- Voice messages (ADR 0030, 0099): tap the microphone, speak, “Done”, send; up to 5 minutes (longer if the plan
  allows, at most 30 minutes),
  AAC mono, as an encrypted attachment like photos. The phone recognizes the beginning locally (Android from 13,
  iOS; never via a service on the internet) and sends it along as “Text preview”, over radio as
  “🎤 Text…”, shortened with the same limit as the input field. Your own text replaces the preview;
  without recognition “🎤 Voice message 0:42”. The recording arrives as soon as there is internet. Playback only
  in the app.
- Details for every message (sent, delivered, read, reactions) via the small “i” next to it.
- “Troubleshooting” in the ⋮ menu: report a problem and the log for troubleshooting, the last 1000 lines of the app
  (read receipt, radio connection), without message texts, in memory only, pass on via “Share”.
- Support as a chat (ADR 0065): “Report a problem” (under “Troubleshooting”, optionally with the log) and “Ask us”
  (under “Help”) create a separate circle “Support: …” with the support identity for each request and invite it
  directly; the answer arrives in this circle. The support identity's contact link is loaded from
  `AppConfig.SUPPORT_LINK_URL`, with `AppConfig.SUPPORT_CONTACT_LINK` as fallback; without a link, sending is off.
  If the person owns a channel `anynear-support` (`AppConfig.SUPPORT_ALERT_CHANNEL`, in practice only the
  support identity), the app joins every new request itself right away and additionally sends it as an alert
  there, so that the support team notices it on their own phones.
  The support identity is not listed among the contacts and not as a direct conversation in “Messages”; whoever opens
  it directly anyway ends up at the form. Circles of a request show the support picture.
- Crash report (ADR 0082): a crash is recorded immediately as a report on the device (`CrashRecorder`,
  `files/crashes.json`), without network: Android via the `UncaughtExceptionHandler`, plus on start native crashes and
  ANR from `ApplicationExitInfo` (from Android 11; for native ones only the reason, we do not read the tombstone), iOS via
  `setUnhandledExceptionHook` and MetricKit (`MXCrashDiagnostic`). Content: kind, build, platform, model,
  call stack, last 200 log lines, cleaned (nothing after `#` in addresses, no long key sequences,
  no paths). At most 5, identical ones combined, 14 days. On the next start a card at the top of “Messages”
  (“Review and send”, “Don't send”); sending goes via “Report a problem” with `absturz.txt` and the log, without a
  support identity via “Share”. Under “Troubleshooting” the line “Crash reports”. `release.yml` attaches dSYMs
  to the release as `ios-dsyms.zip`.
- Alerts under “Active now” can be hidden with “Got it”, only for the person, on all their devices
  (setting `alerts_acked` in the profile, ADR 0050); a replacing alert appears again (ADR 0065).
- Reactions with emojis: long-press a message, choose 👍 ❤️ 😂 😮 😢 🙏; one per
  person, another emoji replaces it, tapping your own again takes it back. Over the internet
  and over radio as ANMP; reactions from radios without anynear are shown (ADR 0018, 0021).
- Read receipt, always on: whoever looks at a conversation confirms reading. Two yellow check marks mean
  “read”, in a circle only once everyone has read (before that “Read by 1 of 3” in the details).
  On both paths as ANMP to the sender's installation, with the message IDs; over radio
  one per sender and conversation (ADR 0019, 0021).
- Chat in the circle and direct messages to contacts. When the connection drops, messages wait
  invisibly in a queue and go out automatically, without duplicates.
- Exchange contacts via contact link (with radio contact if a radio is paired); a changed
  key is not adopted silently. Whoever has the link may write to the personal mailbox
  (grant); the core no longer lets a removed contact write (ADR 0035 §2).
- Stored data carries a version (`data/StorageVersion.kt`); changes come as a migration with a test, never as a
  fresh start (ADR 0061, decision 13). The app ID `app.anynear` starts empty; nothing moves over from an earlier app ID.
- Honest message states: “delivery confirmed” only on a confirmation from the recipient's device.
- Only what the radio has not yet accepted is repeated automatically.
- No backup file (ADR 0105, replaces ADR 0027): a new phone comes back with another own device or the emergency
  card (ADR 0042, 0093); everything else is on the relays. A restore staged by an older version is discarded at start.
- Settings in the network (ADR 0106, `app/SettingsSync.kt`): every stored setting is a person setting (`PERSON`, in
  the profile space with prefix `kv:`, the same on all own devices; received ones take effect at the next start), a
  device setting (`DEVICE`, in the device space of ANMP under this device's ID) or stays on the device. The store is an
  `ObservedKeyValueStore`, so changes go out as they happen. After the emergency card or adding a phone, the app asks
  once “Does this phone replace one of your devices?”; the chosen device's settings come over, then the app restarts.
  Drafts are not in the network yet.
- Passwords (ADR 0039): under “Files”, between personal storage and circles. Opening only with the screen lock (Android: RSA key in the
  Keystore, 30 seconds after confirmation by `BiometricPrompt` or the screen lock prompt; iOS:
  keychain with “user presence”). Create, search, view, copy (marked confidential),
  edit and delete logins; leaving the area locks immediately, background after 180 seconds. The same on all your own
  devices (ADR 0048): the state lies with the key in the password space on the relays, devices without a key
  adopt it, states of two devices are merged (deletions as markers in the file). Autofill via an
  `AutofillService` (`androidApp/.../autofill/`) and the iOS extension `CredentialProvider` (bundle ID
  `app.anynear.credentials`). One-time codes and passkeys (ADR 0052): TOTP link (`otpauth://totp`) via camera or
  typed into the login, code large with remaining time, filled into the code field on Android, otherwise in the
  clipboard after the password; passkeys (ES256, attestation “none”) via the Credential Manager from Android 14
  (`PasskeyProviderService`, `PasskeyActivity`, list of privileged browsers in `res/raw`) and the iOS extension from
  iOS 17, codes there from iOS 18; new passkeys reach the logins via the file `incoming`. `vault` is version 2
  as soon as a login has a code or passkey. The logins come back with the emergency card (ADR 0048). For TestFlight the identifier `app.anynear.credentials` with app group
  and “AutoFill Credential Provider” must exist in Apple Developer and the capability must also be enabled on `app.anynear`.
- Profile and settings (ADR 0050): name, own picture, light/dark and “sign over radio” are the same on all
  your own devices and come back with the emergency card. They lie in the person's profile space on the relays;
  per item the newest change wins. Which setting travels along is defined in `ProfileRepository.personalSettings`;
  notices, radio and setup stay on the device.
- My files (German “Meine Ablage”; ADR 0040, 0041): photos and any files (50 MB each) only for you, encrypted on the phone
  (`data/PersonalFiles.kt`) and permanently on the relays in a separate space without expiry date
  (`app/PersonalFilesSync.kt`, ANMP core `storePut`/`storeList`); open, rename, delete, save on the phone,
  send into a chat; attachments from the chat via “Save to my files”.
- Storage per circle (ADR 0049): the same pages and the same sync as My files (`app/CircleFiles.kt`), online
  in a separate space derived from the circle secret (ANMP core `circleStorePut`/`circleStoreList`). Everyone in the
  circle sees, changes and deletes the same files; after a key rotation the core moves them to the new space. Whoever
  leaves the circle loses the storage from the phone. Still without “Important” and without a line in the chat.
- Recognizing and searching in both storages (ADR 0067): search field and stack chips instead of folders (`ui/PersonalFilesUi.kt`,
  search `DocIndex` in the ANMP core). Text is read only by the device (`attachments/DocumentText.kt`): Android via PP-OCRv5 with
  LiteRT (`attachments/TextOcr.android.kt`, models under `androidApp/src/main/assets/ocr/`, reproducible from
  `tools/ocr-models/convert.sh`, ADR 0078) and `PdfRenderer` from Android 15, iOS via Vision and PDFKit; a better
  reader re-reads existing files (`readerVersion`); the text read is shown on the file's page under “Text read”; stored encrypted in `file-texts`, only on the
  device (`data/DocTexts.kt`). Kind and details are recognized by `DocRecognizer` in the core (`app/DocReading.kt`); they go to the relays as
  field `doc` with the entry, changes without a new upload; the kind can be chosen by hand.
- Gallery and photo topics (ADR 0070): switch “Documents/Photos”, photos as a grid with topic chips. Topics are recognized
  only by the device: Android MobileNet v1 with LiteRT (`:androidApp:visionModel` downloads the model at build time with a fixed
  SHA-256) and `FaceDetector` only for “People”, iOS Vision; mapping `PhotoTopics` in the core, as `topics` in the field
  `doc`.
- Searching the phone for documents (ADR 0071): card when My files is empty, entry in the ⋮ menu of the storage
  (`ui/DocFinderUi.kt`). Photos only after “Search” (Android `READ_MEDIA_IMAGES`/`READ_MEDIA_VISUAL_USER_SELECTED`, up to
  12 `READ_EXTERNAL_STORAGE`, from 13 returned after the run; iOS PhotoKit), without access via the picker;
  folders (also Dropbox, Synology Drive) via the system folder picker, only on Wi-Fi. Search run `app/DocFinder.kt`:
  pre-check on a small image (`PhotoTopics`, text recognition), then `DocRecognizer`; from 20 % battery; a run once started
  finishes in the background (ADR 0071 item 9: Android `DocFinderService` as foreground service `dataSync` with
  progress and “Stop”, iOS from 26 `BGContinuedProcessingTask`, otherwise pause);
  suggestions with check marks, adopting like “Add photo” or “Add file” with `doc`. What has been seen is remembered only by the
  phone (`doc-finder`).
- Finding new documents automatically (ADR 0081): off until you say “Yes” after the first search run; switch on
  “Find documents”. Then once a day on the charging cable `DocFinder.checkNew()` over the remembered sources (photos, whole
  phone, folders), only unchecked items, cloud folders only on Wi-Fi; Android `DocFinderWorker` (WorkManager), iOS
  `BGProcessingTask` `app.anynear.docfinder.new`. Notification at most once a day; access remains as long as it is on.
- Attachments (ADR 0011, 0041): photos (shrunk), PDF with preview and any other file unchanged with its name,
  10 MB each; opened with the matching app of the system.
- New software for the radio (ADR 0056, `app/RadioSoftware.kt`, `mesh/FirmwareFlasher*.kt`, `ui/RadioSoftwareUi.kt`):
  signed list from the radio software space on the relays (ANMP core `radioSoftwareLists`; the relays
  enter every stable Meshtastic release themselves), file via the link at `meshtastic.github.io`, selection only of
  newer versions, download and check (size, SHA-256) in the background, conditions (directly connected, battery of
  radio/phone, no waiting messages), question every time with “Later” until the next start, page “Keep
  anynear open” with the screen on, check afterwards, after two failed attempts instructions via USB; under “Advanced”
  firmware version and “Check for new software” with result. Installing only on **Android**, procedures as in
  Meshtastic-Android: nRF52 via legacy DFU with the Nordic DFU library (`FirmwareDfuService`, jump into the
  bootloader via its service), ESP32 from firmware 2.7.18 via `ota_request` and MeshtasticOTA loader over Wi-Fi
  (TCP 3232) or Bluetooth. Not yet tested on real hardware (U1–U8). Rescue via USB cable under “Advanced”
  (`app/UsbRescueFlow.kt`, `mesh/UsbRescue*.kt`, Android only): reset the radio via USB OTG through meshtastic-sdk (serial)
  (also deletes pairings) or send an nRF52 into the bootloader and write the `.uf2` from the list to its
  drive (Android file picker); also for a device that is already stuck in the bootloader (R1–R3).
- Check-ins (German “Lebenszeichen”, ADR 0083; `lifesigns/`): for times alone (working alone, out and about, alone at
  home, daily, custom), started in a circle of your choice. The phone asks “All good?” at intervals; if nobody
  answers, the people in that circle first get a hint and then an alarm. It does not replace the emergency number.
- Trash (ADR 0086): files deleted from My files or a circle's storage stay in the trash for 7 days and can be brought
  back (in a circle by everyone in it); until then they still count toward the storage quota.
- Replies (ADR 0092): “Reply” on a message quotes it above your own; tapping the quote jumps to the original.
- Titles and sorting (ADR 0091): after reading a file, the device gives it a title (one set by hand stays); the
  storages can be sorted by newest, title or file name, chosen per device.
- “Keep on this phone” (ADR 0080): on by default for every file in the storages; switched off, the file stays only
  in the network with a preview on the phone, and opening it needs internet.
- Adding your own devices (ADR 0093): the new device shows a QR code and a comparison code; an existing device
  scans it, compares the code and, after the device lock, hands over identity, name and logins.
- Setting up a radio over USB (ADR 0094, Android only): plugging in a radio without Meshtastic installs it (ESP32 via
  the ROM loader, nRF52 via its bootloader drive); afterwards you choose how to use it: on the move (Bluetooth),
  stationary on Wi-Fi or over the USB cable. On the iPhone a note leads to the setup in the web app on a computer.
- Recommended radios (ADR 0095): the list of recommended models with picture and use comes from
  `https://anynear.app/v1/radios.json` (last good list or built-in copy as fallback); when setting up over USB the
  recognized model is preselected, others are behind “Other Meshtastic-compatible radio”.
- New versions and required updates (ADR 0096): notices about a new version come from the own updater (`direct`),
  Google Play or the App Store; TestFlight reports by itself. A rule in the signed radio list (`app`: `min`, `from`)
  announces a required update with a band and the date, and after that date covers the app with a full screen
  as long as a newer version can be reached. Debug builds are never blocked.

## Deliberately not included yet

- The maintenance service (a separate product, see below); installing new software on the radio on iOS (ADR 0056).
- Automatic switching between Wi-Fi and Bluetooth on the same device (ESP32: Wi-Fi switches BLE off).

## License

The code is licensed under GPL-3.0-or-later because meshtastic-sdk is under this license. See [LICENSE](LICENSE).
Origin and licenses of sounds, models, font and data in the repository: [NOTICE](NOTICE). Name and logos are
excluded: [TRADEMARKS.md](TRADEMARKS.md).

anynear is an independent project and is not affiliated with Meshtastic LLC; it is neither
supported nor endorsed by it. Meshtastic® is a registered trademark of Meshtastic LLC. The app works with
radios that run the free software Meshtastic and uses the library meshtastic-sdk (GPL-3.0) for this.
The procedures for installing radio software and for the serial connection follow those of Meshtastic-Android
(GPL-3.0).

For official warnings the app determines the district from the approximate location on the phone, with a table of
municipality center points under `shared/src/commonMain/composeResources/files/regions/` (generated with `scripts/regions.py`).
Source: Gemeindeverzeichnis-Informationssystem GV-ISys, “Alle politisch selbständigen Gemeinden mit ausgewählten
Merkmalen am 31.12.2025 (4. Quartal)” (file `AuszugGV4QAktuell.xlsx`, published January 2026, downloaded on 04.11.2026 from
[destatis.de](https://www.destatis.de/DE/Themen/Laender-Regionen/Regionales/Gemeindeverzeichnis/Administrativ/Archiv/GVAuszugQ/AuszugGV4QAktuell.html)).
© Statistisches Bundesamt (Destatis), 2026, on behalf of the Herausgebergemeinschaft Statistische Ämter des Bundes und der
Länder; geographic center coordinates: Bundesamt für Kartographie und Geodäsie (BKG). Reproduction and
distribution, also in part, permitted with attribution. The attribution is also shown in the app under “License”.

The name “anynear” and the logos are excluded from this. Forks must use a different
name, see [TRADEMARKS.md](TRADEMARKS.md). The maintenance service is a separate product
and not part of this repository.
