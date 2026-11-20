# Hardware tests

Results from before the cut-over (ADR 0061) used the app's earlier names and channels; open tests refer to anynear (channel `anynear`, device names `anynear.app | …`).

As of 05.11.2026 (status per test in the tables below). Tested with real radios (Android emulator, radio via Wi-Fi):
W1–W6, E3, E4, E10 (TCP), E11–E14, F1, F3, F4, F6, F8, Ö1, Ö3, Ö3a, Ö4, Ö6, Ö8; partly Ö2 and Ö5. Failed:
U3. T1 and T2 only in the emulator. Nothing has been tested yet on a real phone via Bluetooth or on a real iPhone.
No result here counts as passed before it has been carried out on real hardware.

Test devices: 2 × Heltec V3 / Meshnology N35, T1000-E (CardTracker), Android phone, iPhone (TestFlight).
Note per device: board revision, firmware version, bootloader.

## Preparation per radio

1. Radio profile, role, telemetry, signals, location and name are set up by the app (ADR 0006, tests E1–E9).
   For phase 1 tests run through “Set up radio” once beforehand.
2. The app creates the shared radio channel `anynear` (ADR 0051; separate channels per circle no longer exist). Do **not** activate managed mode.
3. MQTT off for the tests so that radio paths are unambiguous.

## Wi-Fi (emulator, 18.10.2026)

| Nr | Test | Expected | Result |
|---|---|---|---|
| W1 | Heltec V3 with Wi-Fi is found automatically (mDNS) | appears as “Found on Wi-Fi” | passed (Android emulator API 36) |
| W2 | Assign and connect via TCP | “Connected”, device name and power supply read | passed |
| W3 | Missing app channel | setup assistant appears | passed |
| W4 | “Start a new family” | app channel on a free slot (here 2), confirmed by reading back; primary channel and radio profile unchanged | passed, only on the 3rd attempt (see finding) |
| W5 | Test message into the family chat | status “Sent” | passed (reception on a second device not yet tested) |
| W6 | “Invite family” visible | invitation code is offered | passed |
| W7 | Meshtastic app connects to the same Wi-Fi radio while anynear is connected | after three quick drops anynear steps back (“Radio is in use elsewhere”), asks in the app or by notification in the background, and no longer kicks the Meshtastic app out | pending |
| W8 | Then “Connect here” | anynear connects, the Meshtastic app is disconnected; record whether it reconnects by itself | pending |
| W9 | Two phones with anynear on the same Wi-Fi radio, “Connect here” on one | the other one steps back and asks; the one that took over stays connected | pending |

**Finding W4:** Over TCP the confirmation for the first write command after connecting did not arrive;
once the connection dropped afterwards (60 s no data, no restart of the device). After the
automatic reconnection the command was confirmed. The app therefore writes, checks by
reading back and repeats up to five times. Cause (firmware or SDK) still open; test again
via Bluetooth.

## Setting up the radio (ADR 0006, implemented 19.10.2026)

| Nr | Test | Expected | Result |
|---|---|---|---|
| E1 | Pair a brand-new device (factory reset) | notice “Set up radio” without “Not now”, suggested name `anynear.app \| …` | pending |
| E2 | Confirm E1 | device restarts (possibly several times), reconnects, message “Done: Your radio is set up for anynear.”, no dialog afterwards; values in the Meshtastic app as in the profile | pending |
| E3 | Connect the previous test device (hops 3) | question dialog with “Not now”, short text, “Learn more” expands | passed (emulator, Heltec V3 via Wi-Fi, 19.10.2026) |
| E4 | E3 with “Not now” | no dialog again after reconnecting; notice and button in the settings | passed (emulator, 19.10.2026) |
| E5 | Connect a device with role ROUTER | question dialog with warning “router”, button “Set up anyway” | pending |
| E6 | Check after setup | Wi-Fi connection, Bluetooth PIN, display, foreign channels, name and key of the primary channel unchanged | pending |
| E7 | Location | primary channel and channel `anynear` position precision 0, GPS off; no position packet in the network | pending |
| E8 | Telemetry | device telemetry hourly, mains operation (“Plugged in”) visible in the app | pending |
| E9 | Older firmware without `device_telemetry_enabled` | no dialog in a loop after setup, notice in the settings remains | pending |
| E11 | Radio connected over Bluetooth, page of the radio, “Switch to Wi-Fi”, enter Wi-Fi | warning about a wrong password shown; radio restarts, joins the Wi-Fi, app finds it there; “How you use it” says “Stationary on Wi-Fi” | pending |
| E12 | Radio connected over Wi-Fi, “Switch to Bluetooth” | radio restarts, app finds it over Bluetooth, PIN window, connected | pending |
| E13 | As E11 with a wrong password | after 90 s “doesn't show up on your Wi-Fi … USB cable”; plug in, “Switch to Bluetooth” brings it back | pending |
| E10 | Setup via TCP and via Bluetooth | all deviations fixed, notice disappears | TCP passed (emulator, 19.10.2026, see finding E10); Bluetooth pending |
| E11 | Set up a device with different settings | deviations in frequency, hops, telemetry, location, name fixed; name “… Reiher 80” | passed (emulator, 19.10.2026); values not yet cross-checked in the Meshtastic app |
| E12 | Device with fixed transmit power | dialog, afterwards transmit power 0 or the limit of the region | passed (emulator, 19.10.2026): firmware stores the limit 27 dBm instead of 0; only since the check “0 or 27” no more dialog in a loop |
| E13 | “Advanced” → suggestion → save name | name “… Reh 1998”, no restart needed | passed (emulator, 19.10.2026); first command unconfirmed, confirmed after reconnection |
| E14 | “Advanced” → apply the app's settings again | all areas written, one restart, no deviation afterwards | passed (emulator, 19.10.2026) |
| E15 | Device with buzzer, vibration or notification LED (e.g. T-Echo, RAK with buzzer) | dialog because of signals; afterwards no sound, vibration or LED signal on the radio, neither for messages nor for bell characters; Meshtastic app: “Alert when receiving a message/bell” off | pending |
| E16 | “Advanced” → save a new name (24.10.2026) | field shows the new name immediately, locked, “Being applied …”; header “Applying changes…”; device restarts exactly once; afterwards “New name is set.” and everything is free again; no second save possible, no setup dialog in between | pending |

**Finding name (24.10.2026, from the firmware source code, not on hardware):** The firmware restarts after
every name change (`AdminModule::handleSetOwner` → `saveChanges`, after 7 s). E13 was rated as passed under the
assumption “no restart”; the reconnection there was presumably the restart. The
app now waits for the restart and locks until then (E16).

**Finding E10 (TCP, 19.10.2026):** With an edit session (`editSettings`) the setup failed
twice: after the start nothing came from the device for 60 s, the SDK disconnected (`NodeUnreachable`).
Switched to individual write commands: the first command after connecting again remained
unconfirmed and the connection dropped (as in W4). After the reconnection the device confirmed
all commands, restarted after approx. 23 s, and afterwards the app reported no deviation anymore.

**Finding connection (TCP, 19.10.2026):** Without radio traffic the SDK disconnects after 60 s
(“Liveness timeout”) and reconnects after approx. 3 s, i.e. about every minute. The firmware apparently does not
answer the heartbeat signal over TCP. With hourly telemetry this occurs more often in a quiet
network. Clarify the cause (SDK or firmware) and the consequences for reception, compare via
Bluetooth.

## New software for the radio (ADR 0056) – only U3 tested on hardware, failed

Built on Android (nRF52 via legacy DFU, ESP32 from 2.7.18 via MeshtasticOTA), not yet tested on real
hardware. iOS has no path yet. An ESP32 below 2.7.18 is offered nothing (update once via USB).

| Nr | Test | Expected | Result |
|---|---|---|---|
| U1 | Update Heltec V3 over Wi-Fi | page “Keep anynear open”, progress, afterwards new version reported, “Done” | pending |
| U2 | Update Heltec V3 over Bluetooth | as U1 | pending |
| U3 | nRF52 (T1000-E, T-Echo, RAK4631) via DFU, Android (iOS later) | notice “New software …”, page “Keep anynear open”, progress, afterwards new version reported, pairing remains | failed (T1000-E 2.7.15 → 2.7.26, 03.11.2026): “That didn't work”, device remained in the bootloader (LED green, can only be rescued via USB); the screen locked in the meantime. Suspected cause: the app aborted on locking although the device was already in the bootloader. Corrected (no abort after the jump, screen stays on, procedure in the log), renewed test pending |
| U4 | Connection drops during installation | error with “Try again”; on the next attempt it continues from update mode; after two errors instructions via USB | pending |
| U5 | Power lost during installation | as U4, radio can be rescued via USB afterwards | pending |
| U6 | Wrong file (hash does not match) | nothing is offered, nothing installed | in the unit test (`RadioSoftwareTest`), pending on hardware |
| U7 | Jump across several versions and one major version | radio profile, radio channel `anynear`, name and PKI key remain; otherwise notice “Please set it up once more now” | pending |
| U8 | Leave the app or lock the screen during installation | before writing, abort without consequences; afterwards it continues or resumes on return (Android and iOS) | pending |

## Rescue via USB cable (ADR 0056) – none of it tested

Radio → Advanced → “Rescue via USB cable”, Android only (USB OTG).

| Nr | Test | Expected | Result |
|---|---|---|---|
| R1 | T1000-E with broken pairing via USB cable on the phone, “Search”, “Reset to factory settings” | permission question for USB, “Found: anynear … , software …”, afterwards “Reset”, button to the Bluetooth settings; after removing and pairing again “Set up radio” | pending |
| R2 | Running nRF52, “Install software again” | device appears as a USB drive, file picker, choose drive, “Done”, device starts with the approved version | pending |
| R3 | nRF52 stuck in the bootloader (LED permanently on), “Radio is stuck” | model of the last connected device or selection, choose drive, device starts again | pending |
| R4 | T1000-E with bootloader 0.9.1 (Seeed), “Renew start program” | choose drive, “Done”, device restarts, INFO_UF2.TXT states 0.9.2; afterwards U3 works (update over Bluetooth) | pending |
| R5 | As R4, bootloader already 0.9.2 | software is installed again, device starts with the approved version | pending |

## Radio slots (ADR 0038) – dropped

ADR 0038 is replaced by ADR 0051: there are no channels per circle anymore, but one shared channel `anynear`.
The tests K1–K5 are dropped; the numbers are not reassigned.

## Bluetooth permission with a stored radio (23.10.2026)

If a radio is stored via Bluetooth but Bluetooth is not allowed (new installation,
return with the emergency card), the app asks for it: card “Allow Bluetooth?” in
Messages, line “Allow Bluetooth” on the Radio page, button in the banner without internet. If
the system question no longer appears (“Don't ask again”), the button opens the app settings. Tested in the
emulator: decline twice, afterwards the button opens the app info; after allowing, the
request disappears. The logic with a stored device with unit tests (`BluetoothHintAppTest`); card and line
not tested with a real Bluetooth radio (the emulator has no Bluetooth).

| Nr | Test | Expected | Result |
|---|---|---|---|
| B1 | Phone with a stored Bluetooth radio: reinstall the app, come back with the emergency card | card “Allow Bluetooth?”, after “Allow” the radio connects | pending |
| B2 | Revoke Bluetooth again in the Android settings of the app | Radio page shows “Allow Bluetooth”, tapping leads to the permission | pending |

## Phase 1: vertical prototype

| Nr | Test | Expected | Result |
|---|---|---|---|
| H1 | Search for and assign a device | device appears, pairing (PIN) works, status “Connected” | pending |
| H2 | Channel `anynear` recognized | no notice “not set up” | pending |
| H3 | Circle chat A → B | arrives at B, A shows “Sent” | pending |
| H4 | Circle chat B → A | arrives at A | pending |
| H5 | Public Hessenmesh traffic | does **not** appear in the app | pending |
| H6 | Import contact code of A into B | contact appears, `addContact` successful | pending |
| H7 | Direct message A → B | arrives, A shows “Delivery confirmed” | pending |
| H8 | Direct message to a switched-off device | A shows “no confirmation”, never “Delivery confirmed” | pending |
| H9 | Direct message via a relay | “Delivery confirmed” only with the ACK from the target, not from the relay | pending |
| H10 | Radio off and on again | “Looking for your radio”, afterwards automatically “Connected” | pending |

## Phase 2: everyday use (not yet implemented or open)

- Bluetooth on the phone off/on, deny permission and grant it later.
- Quit the app, lock the screen, reception in the background (Android foreground service, iOS `bluetooth-central`).
- Phone restart, process killed by the system.
- Two devices with the same name in range.
- Message history after restart and app update (is stored, not yet tested on a real device).
- Notifications (ADR 0008) on real phones: does a message arrive reliably with the screen locked
  over hours? After a restart of the phone? With manufacturers with strict battery optimization
  (Xiaomi, Samsung)? Measure battery consumption over a day. Tested in the emulator (Android 16): notification in
  the background, tapping opens the conversation, battery notice and system dialog “Allow”; restart and
  battery not tested. Check on real devices whether “Open setting” hits the right page on Samsung/Xiaomi.

## ANMP over radio and shared radios (ADR 0021) – none of it tested

Unit tests and the end-to-end test against the local mailboxes run (19.10.2026); the format has not been
tested over a real radio link. Whether the firmware delivers `PRIVATE_APP` packets in the channel `anynear` unchanged
is open; directly (with PKI) a direct message arrived on 02.11.2026 (see findings radio check). Since
ADR 0051 there are no channels per circle anymore; P5 and P9 are therefore dropped.

| Nr | Test | Expected | Result |
|---|---|---|---|
| P1 | Without internet: A writes into the circle (signature on), B receives | text arrives, sender A, no Meshtastic text message in the channel `anynear` | pending |
| P2 | As P1, text with 94 characters; then signature off and 160 characters | both arrive; the count while writing matches | pending |
| P3 | Without internet: direct message A → B | arrives; C in the same circle sees nothing | pending |
| P4 | Tom and Anna use one radio (connected alternately), B writes to both | messages appear with the right sender; “last reachable” separate | pending |
| P5 | Ben (another circle) is on the shared radio, B writes into Tom's circle; Ben reads out the channels with the official Meshtastic app | Ben's app shows nothing; the channel key read out is not the circle secret (ADR 0022) | dropped (ADR 0051, no circle channels anymore) |
| P6 | With internet and radio | every message once, path shows both | pending |
| P7 | Official Meshtastic app in the channel `anynear` | does not see anynear messages; anynear shows its own texts and reactions under the name of the radio | pending |
| P8 | Anna's phone on the shared radio, Bob writes to Tom directly over radio, Anna gets internet later | Tom gets the message over the internet, Bob sees “delivered”; Anna sees nothing (ADR 0022) | pending |
| P9 | Update from a version before ADR 0022, radio connected | app renews the circle channels with the new key, radio then works between updated apps | dropped (ADR 0051, no circle channels anymore) |

## Reactions over radio (ADR 0018, as ANMP per ADR 0021) – none of it tested

| Nr | Test | Expected | Result |
|---|---|---|---|
| R1 | Without internet: A reacts with 👍 to a circle message from B | B sees 👍 under the message, no new message “👍” | pending |
| R2 | Without internet: reaction to a direct message | arrives at the conversation partner | pending |
| R3 | Official Meshtastic app reacts in the channel `anynear` | anynear shows the reaction under the message | pending |
| R4 | anynear reacts to a message from a radio without anynear | visible there as a Meshtastic reaction | pending |
| R5 | With internet and radio | reaction counts once, not twice | pending |
| R6 | Replace a reaction without internet by another emoji | recipient sees only the new one | pending |
| R7 | Tap your own reaction again, without internet, then with | reaction disappears at the recipient and does not come back | pending |

## Read receipt over radio (ADR 0019, as ANMP per ADR 0021) – none of it tested

| Nr | Test | Expected | Result |
|---|---|---|---|
| L1 | Without internet: B opens the circle chat with a message from A | yellow check marks at A as soon as everyone has read; before that “Read by 1 of 2” | pending |
| L2 | Without internet: direct message A → B, B opens the conversation | two yellow check marks at A, “Read: … over radio” | pending |
| L3 | Official Meshtastic app in the channel `anynear`, B reads in anynear | Meshtastic app shows no new message | pending |
| L4 | B reads ten unread messages from A at once | exactly one radio packet to A, all ten read at A | pending |
| L5 | With internet and radio | “read” once, details name both paths | pending |

## iOS notifications via APNs (ADR 0009) – none of it tested

The app is now built, signed and uploaded to TestFlight on a dedicated Mac runner with every release build
(`release.yml`, job `ios`). The wake-up calls themselves have been tested **on no real iPhone**. Originally only
the relay side (with a mock APNs server over HTTP/2), the
shared Kotlin code (`PushTest`) and that the iOS Kotlin code compiles were tested. Swift parts
(`iosApp/iosApp/iOSApp.swift`, `iosApp/NotificationService/`) and the Xcode project are unbuilt.

Requirements (Apple account of the project): team ID in `Config.xcconfig`; app ID with “Push Notifications”,
“App Groups” (`group.app.anynear`) and Keychain Sharing, likewise for the extension
(`….notifications`); APNs key (`.p8`, key ID) on the relays.

| Nr. | Test | Expected | Status |
|---|---|---|---|
| I1 | Xcode build with team ID, app and extension | builds, signs, extension is in `anynear.app/PlugIns` | pending |
| I2 | First start with a circle, “Allow notifications” | mailbox log “Weckrufe an (1 Geräte)” (wake-up calls on, 1 device) after restart or entry in `push.json` | pending |
| I3 | App swiped away, circle message from Android | banner “Family” / “Anna: …” within seconds | pending |
| I4 | As I3, iPhone locked (preview “When Unlocked”) | only “anynear · New message”, text after unlocking | pending |
| I5 | Direct message with the app closed | title = name of the person, text of the message | pending |
| I6 | Tap the notification | app opens exactly this conversation, number of new messages is correct | pending |
| I7 | Own message from the same iPhone | no wake-up call (`skip`) | pending |
| I8 | Delivery confirmation / sign-in to the circle | no notification (no `wake`) | pending |
| I9 | iPhone restarted, not yet unlocked | only fixed text “New message” | pending |
| I10 | Memory of the extension (Instruments) | stays below the limit (approx. 24 MB) with the whole Kotlin framework | pending |
| I11 | Leave the circle | no more wake-up calls from this circle after the next sync | pending |
| I12 | Update from a version before ADR 0009 | circles, keys and read positions remain (move to app group and keychain group) | pending |
| I13 | Two mailboxes with key | one notification (same `apns-collapse-id`), same text | pending |

## Attachments over radio (ADR 0011)

Tested in the emulator (19.10.2026, two emulators, local Docker mailboxes): line break,
emoji, link, two photos in the circle, resumption after restart, PDF with preview. Not tested:
tapping the PDF (system viewer), everything on iOS and everything with a real radio link.

| Nr | Test | Expected | Result |
|---|---|---|---|
| A1 | A without internet with a radio sends a photo with text into the circle, B has only radio | B immediately sees “📎 Picture: Text” and “Arrives in full once there's internet” | pending |
| A2 | A gets internet hours later, B is online | A stores photo and message, B shows **one** message with photo (matched via the message ID), loads automatically | pending |
| A3 | Quit and restart A's app between A1 and A2 | message stays “waiting” and goes out after the restart | pending |

## Voice messages (ADR 0030)

Tested in the emulator (23.10.2026, Android 16, two installations in the same circle, local
Docker mailboxes, microphone of the computer): microphone question, recording with time, “Done” stores a
draft, draft survives a restart, play and pause with progress, sending with and without
text, delivery confirmation. Speech recognition available on the device, recording is decoded to PCM,
recognition runs (silent recording: “no match”). E2E tests with mock radio and real
mailboxes: preview shortened over radio, recording over the internet, one message, labeling,
recipient at first only with radio. Not tested: recognition with real speech, playback on a
second device with a new app, 5-minute limit, everything on iOS, real radio link.

| Nr | Test | Expected | Result |
|---|---|---|---|
| S1 | A without internet with a radio records and sends, B has only radio | B immediately sees “🎤 Voice message 0:12” or “🎤 Text” and “Arrives in full once there's internet” | pending |
| S2 | A gets internet later | B shows **one** message, loads the recording, playback works | pending |
| S3 | Quit and restart A's app between S1 and S2 | recording stays “waiting” and goes out after the restart | pending |
| S4 | iPhone records, Android plays and vice versa | playable in both directions, loudspeaker instead of earpiece | pending |
| S5 | Android 13+ (e.g. Pixel) says “Bin gut angekommen, melde mich heute Abend” | under the draft “Text preview” with the text, at the recipient “Text preview” above the text | pending |
| S6 | Speak for 40 seconds without a break, B has only radio | B reads “🎤 …” with the beginning and “…”, later “Text preview · shortened” | pending |
| S7 | iPhone, first recording | question about speech recognition; declining does not block the recording | pending |

## Passwords and My files (ADR 0039, 0040)

Tested in the emulator (24.10.2026, Pixel 9, Android 16, test PIN): notice without screen lock, afterwards
“Set up” by itself; setting up and unlocking via the system PIN prompt; create and show a login; lock after
switching to the background; provider in the Android settings, display “… fills in logins”;
Chrome 133 (“Autofill using another service”): “Fill in with …”, PIN, selection,
fields filled in; PDF in My files with preview; backup with PIN prompt, restore after deleted
app data: login and file are back. iOS: app and extension build, app starts in the simulator; nothing else tested.

| Nr | Test | Expected | Result |
|---|---|---|---|
| PW1 | Android with fingerprint: open passwords | system prompt with fingerprint or PIN, then list | pending |
| PW2 | Android 8–10: open passwords | screen lock prompt (PIN/pattern), then list | pending |
| PW3 | Switch the screen lock off and on again | “Passwords can no longer be read”, “Start over” or the emergency card | pending |
| PW4 | iPhone: set up and open passwords | Face ID or device passcode, then list | pending |
| PW5 | iPhone: choose anynear under “AutoFill & Passwords”, sign in in Safari | key symbol → anynear, Face ID, matching logins on top, fields filled in | pending |
| PW6 | Come back with the emergency card from Android on iPhone and vice versa | logins and My files are back, passwords readable after Face ID or PIN | pending |
| PW7 | Firefox, Samsung Internet, a native app with sign-in | entry “Fill in with anynear”, filling in after PIN | pending |
| PW8 | Large font, TalkBack and VoiceOver in the password area and when filling in | everything reachable and read aloud, password only after “Show” | pending |

My files on the relays (ADR 0041), tested in the emulator against the real relays (24.10.2026): .txt file
added, “Saved online”; backup without the file restored, file came back from the relays;
deleted. Opening a .txt shows the system chooser.

| Nr | Test | Expected | Result |
|---|---|---|---|
| AB1 | iPhone: send and open a Word, Excel and image file as an attachment | preview of iOS (QuickLook) | pending |
| AB2 | Android ↔ iPhone: file in My files, come back with the emergency card on the other phone | file comes from the relays | pending |
| AB3 | Add a file without internet, internet later | “Not saved online yet”, afterwards “Saved online” by itself | pending |
| AB4 | Recognizing (ADR 0067): photo of a letter, PDF with text layer, scanned PDF in My files | kind and details in the list, searching for a word from the text finds the file | Android emulator (API 36) passed 07.11.2026 (invoice, contract, insurance; chips filter); search by typing not tested there (input via adb did not arrive); iPhone pending |
| AB5 | Change the kind by hand, second device or circle member | new kind appears there after the sync without the file being uploaded again | pending |
| AB6 | Search with many files (over 200, long PDFs) on an older phone | typing stays smooth, battery and heat unremarkable, reading proceeds bit by bit | pending |
| AB7 | Photos in the storage (ADR 0067 §7): photo with capture location (4000 × 3000) via “Add file”, then “Save on phone”; afterwards send into a chat | saved with 3200 × 2400, without image metadata and capture location; in the chat 1600 pixels | Android emulator (API 36) 07.11.2026: storage passed (3200 × 2400, no EXIF data); sending into a chat untested; iPhone pending |
| AB8 | Gallery (ADR 0070): photos and a PDF in My files | switch “Documents/Photos”, photos as a grid, topic chips, “dog” finds animal photos, full-text search finds the PDF | Android emulator (API 36) passed 07.11.2026 with macOS sample photos (zebra, parrot → Animals, sunflower → Nature, basketball → Sport); People (faces) and iPhone pending |
| AB9 | Search the phone (ADR 0071): empty My files, card “Search”, access “Allow all”, made-up invoice, shopping list and landscape picture in the photos | invoice checked with kind, list under “More photos with text”, landscape not suggested; adopting stores the invoice with kind; second run suggests nothing again; after quitting the app the access is returned | Android emulator (API 36) passed 07.11.2026; iPhone pending |
| AB10 | Search the phone without access: “Don't allow”, then “Choose photos myself” | picker with multiple selection, search in the chosen photos | Android emulator (API 36): picker opens, search in it untested; iPhone pending |
| AB11 | Search folders: folder “Pictures” of the phone, then folders in Dropbox and Synology Drive (Android and iPhone), with and without Wi-Fi | pictures and PDFs from the folder as suggestions; without Wi-Fi the question “Search anyway?”; pictures already adopted do not come again | Android emulator (API 36): folder “Pictures” passed 07.11.2026; Dropbox, Synology Drive, cellular data and iPhone pending |
| AB12 | Search the phone with many thousands of photos on an older Android phone | progress runs, pauses in the background and below 20 % battery, resuming on the next run; note duration and battery | pending |
| AB13 | Search the phone in the background (ADR 0071 item 9): start a search run, leave the app | notification with progress and “Stop” (Android) or system progress (iOS 26), run finishes, message “X important documents found” | Android emulator (API 36) 07.11.2026 passed: 124 pictures, left the app, notification counted up, after 3.5 min “124 important documents found”; “Stop” only in the unit test; iPhone pending |
| AB14 | Only important documents (ADR 0074): store a photo with some text (sign), birth certificate, invoice and animal photos | sign and animal photos in the gallery with topic chips, certificate under “Certificate”, invoice under “Other”; phone search suggests only important ones | Android emulator (API 36) 07.11.2026 passed with made-up pictures (gallery 3 photos, chips “Animals”, “Nature”); phone search only in the unit test; iPhone and real everyday photos pending |
| AB15 | Better text recognition and search (ADR 0078): store an ID photographed at an angle, vaccination record, medical letter, sick note, a photo rotated by 90°; search for “Krankmeldung” (sick note); open “Text read” | all recognized as important documents, search finds the certificate of incapacity for work, text read is complete; after the update Android re-reads old files | Android emulator (Pixel 9, API 36) 08.11.2026 passed with made-up pictures (ID with pattern and reflection, 8–22° at an angle, rotated 90°: 1× ID, 4× health; “Krankmeldung” finds the certificate of incapacity for work; text complete); re-reading only in the unit test; real photos, handwriting, iPhone pending |
| AB16 | Finding new documents automatically (ADR 0081): first run, “Yes, check regularly”, then a new important document in the photos, phone on the charging cable overnight | question after the first run; the next morning “1 new document found”, tapping shows only the new one; without charging cable nothing; switching off removes the task | Android emulator (API 36) 08.11.2026: question, scheduled task with condition “charging”, message after simulated charging, only the new vaccination record suggested, switching off removes the task; a real night on the charging cable, manufacturers with strict battery management and iPhone (`BGProcessingTask`) pending |

## Bridge and MQTT (ADR 0010, replaced by ADR 0051 and ADR 0054)

ADR 0010 is replaced: the bridge over radio (stage 1) is implemented as radio sponsorship per ADR 0051, tested only with
unit tests and local relays. MQTT (stage 2) follows ADR 0054; there the bridge over MQTT is not yet
implemented. The expectations in B1–B5 come from ADR 0010 and must be reconciled with ADR 0051 before the test.

Stage 1, app bridge. Setup: A without internet (flight mode, Bluetooth on) with a radio, B with
a radio and internet, C only with internet, out of radio range (or without a radio).

| Nr | Test | Expected | Result |
|---|---|---|---|
| B1 | A writes into the circle | C receives the message via B after the waiting time, detail line names B | pending |
| B2 | A and B both online, A writes | no bridge copy in the mailbox, C shows the message once | pending |
| B3 | Two bridges (B and D) hear A | one deposit in the mailbox, C shows the message once | pending |
| B4 | A online again, original is delivered later | C still shows one message, now confirms; A sees two check marks only afterwards | pending |
| B5 | Direct message A → C over radio | is not passed on | pending |

Stage 2, MQTT. Separate test device on Wi-Fi and a local broker. Do not mix with the radio tests
above (MQTT stays off there).

| Nr | Test | Expected | Result |
|---|---|---|---|
| M1 | Receiving device on Wi-Fi, uplink only on the channel `anynear` | packets of the channel `anynear` from another device appear in the broker | pending |
| M2 | `encryption_enabled` on | broker sees only ciphertext, no text | pending |
| M3 | Sender without `ok_to_mqtt` | record whether the gateway uploads anyway | pending |
| M4 | Uplink on the primary channel off | no foreign Hessenmesh traffic in the broker | pending |
| M5 | `tls_enabled` with a wrong certificate on the broker | record whether the firmware rejects the connection | pending |
| M6 | Wi-Fi and Bluetooth at the same time on Heltec V3 | record whether both work | pending |

### Gateway on Wi-Fi (ADR 0054 §5b, H5 of the bridge plan, 15.11.2026) – none of it tested on hardware

Every radio on Wi-Fi is a gateway; the app writes the values from the `gateway` block of the signed radio list
(`mqtt.anynear.net`, port 1883, root `msh/EU_868`) silently on every connection: uplink on channel 0 and `anynear`
(downlink off), `ok_to_mqtt` on and `ignore_mqtt` off after region and preset, the MQTT module last. Emulator with
meshtasticd (`--sim`, Docker) on 15.11.2026: all four writes confirmed, read back with the Meshtastic CLI, the
firmware connected with these values (G1, G2 in simulation only). With the simulated firmware, admin writes after a
restart did not reach it (also the profile's own name write), so there was one unconfirmed extra write.

Before the test: Relay ≥ v0.25.0 with `MQTT_GATEWAY_LOGIN` live, Network release with the `gateway` block, an app build
with ANMP-Dev PR 138 and this change. Radio G: ESP32 with Wi-Fi (e.g. Heltec V3), set up for anynear. Radio S: a
second radio with the radio profile, phone S without internet (flight mode, Bluetooth on), within radio range of G.

| Nr | Test | Expected | Result |
|---|---|---|---|
| G1 | Switch G to “Stationary on Wi-Fi” (cable or Bluetooth), app connects over Wi-Fi | log `Brücke: Kanal 0`, `Kanal n`, `MQTT an` with `Success`; no question, no new text | pending |
| G2 | Read G with the Meshtastic app or CLI | MQTT on, `mqtt.anynear.net`, root `msh/EU_868`, encryption on, JSON and map reports off; channel 0 and `anynear` uplink on, downlink off; `ignore_mqtt` off | pending |
| G3 | Relay logs / metrics after a minute | one MQTT connection with client ID `!<node of G>` | pending |
| G4 = H5 | Phone S writes into a circle (S asks with `ask` after the waiting time) | the `ask` is in `radio heard` with the node of G, the packet of S is in the `radio inbox` | pending |
| G5 | Change the region of G with the Meshtastic app, reconnect the anynear app | `ignore_mqtt` is off again (profile), bridge values unchanged | pending |
| G6 | Switch G back to “On the move” (Bluetooth) | MQTT off, uplink off on channel 0 and `anynear`; no MQTT connection at the relays | pending |
| G7 | Radio with a foreign MQTT server, on Bluetooth | the app leaves it as it is | pending |

## Sharing from other apps and battery in the background (25.10.2026)

| Nr | Test | Expected | Result |
|---|---|---|---|
| T1 | Android: share a link from another app | anynear in the share menu, selection “Who should get this?” with preview, afterwards text as a draft in the conversation | passed (Android emulator API 36) |
| T2 | Android: share a photo from Google Photos | photo as an attachment in the draft | passed (Android emulator API 36) |
| T3 | iOS: share a link from Safari | anynear in the share menu, app opens and picks up the shared item | passed up to the pick-up (iOS simulator 27.1, setup not completed, selection not seen) |
| T4 | iOS: share a photo and a PDF, on a real iPhone (TestFlight) | selection, afterwards attachments in the draft | pending |
| T5 | Android: radio paired, out of range, app in the background | after five failed attempts only one connection attempt every 5 minutes; immediately on opening | pending |
| T6 | Battery consumption over a day with and without a radio | noticeably lower than before | pending |

## Manufacturers stop the app in the background (01.11.2026)

Reason: for one tester no notifications arrived anymore. The app checked only Android's battery optimization,
not the manufacturers' own power-saving features. New: from Android 11 it evaluates on opening the reasons of the
last process exits (`BackgroundStops`) and shows the notice “Your phone closed anynear in the background”.

| Nr | Test | Expected | Result |
|---|---|---|---|
| H1 | Samsung: anynear under “Sleeping apps”, leave the app in the background, open later | notice “closed in the background”, button opens the app info | pending |
| H2 | Xiaomi: “Autostart” off, swipe the app from the recent apps, open later | notice as H1 | pending |
| H3 | After “Open setting” and release wait a day | no new notice, messages arrive | pending |
| H4 | Stock Android: app update, “Force stop”, crash | no notice | pending |
| H5 | Which reasons (`reason`, `description`) do Samsung and Xiaomi really report? | `REASON_OTHER` or `REASON_SIGNALED`, otherwise adjust the filter | pending |
| H6 | Phone locked overnight, switch Wi-Fi/cellular data, send a message in the morning | notification after about 15 minutes at the latest, without opening the app | pending |
| H7 | Log after opening | line “Start: Letztes Ende …” (last exit) with reason; “wake: fetching again” about every 15 minutes | pending |

## Radio check (ADR 0053, 02.11.2026)

New: “Check over radio” on the person, “Who answers over radio?” in the circle and on the Radio page. One byte on
`PRIVATE_APP` with `want_ack` directly to the radio, on the channel `anynear` (before the cut-over the app's earlier channel) with the hop limit
of the radio profile; only the answer of the target radio counts (`isCheckReplyFrom`), also an error from there. Tested on 02.11.2026
with real radios (Android emulator, radio via Wi-Fi); F2, F5 and F7 are pending.

| Nr | Test | Expected | Result |
|---|---|---|---|
| F1 | Target directly in range | “… answers. Directly.”, signal under “Learn more” | passed (02.11.2026, Android emulator via Wi-Fi radio, target CardTracker next to it): answer after 1 s, direct, 13.5 dB, −44 dBm |
| F2 | Target via one and via two relays | “Via 1 / 2 other radios” | pending |
| F3 | Target radio without a connected phone | answers anyway (firmware confirms) | passed (02.11.2026): phone disconnected from the CardTracker, answer after 1 s, direct |
| F4 | Contact without a shared circle, key unknown to the radio | answer or error from the target; record which (`PKI_UNKNOWN_PUBKEY`?) | passed (02.11.2026): only contact link, no circle; normal answer from the target, no error |
| F5 | Shared radio | sentence names “the radio of A and B” | pending |
| F6 | Target switched off | after 60 s “did not answer”; waiting time sufficient with three repetitions by the firmware? | passed (02.11.2026): after 60 s “did not answer”; the own radio already reported after 11.5 s via `ROUTING_APP` that it gives up (correctly does not count as an answer). 60 s are enough; idea: abort earlier on this message |
| F7 | One byte versus an empty packet | record whether the firmware also sends and confirms an empty packet | pending |
| F8 | anynear app open at the target | no entry in the chat, no passing on | passed (02.11.2026): nothing in the chat at the target, no notification |

### Radio area (ADR 0054 §1b, 15.11.2026) – none of it tested on hardware

The radio uses region, preset and frequency of the area of the signed radio list at the phone's approximate location
(2 km hysteresis at borders), or of an area chosen under Radio → Advanced → Radio area. A radio of the app on the
settings of another area (or the default) switches without a question; the app says so once. Emulator with
meshtasticd (`--sim`, Docker) on 15.11.2026: setup in Frankfurt wrote the area of the live list (EU_868, ShortSlow,
default slot); a fixed 869.525 MHz set with the CLI was switched back silently, read back with the CLI. The live list
had only Hessen, so a change of preset or region was tested in unit tests only.

Before the test: Network release with `lora` and `frequency` per area (Hessen, Europe, North America), an app build with
ANMP-Dev and App-Dev PRs `feat/radio-areas`. Radio A set up for anynear; phone with location allowed.

| Nr | Test | Expected | Result |
|---|---|---|---|
| R1 | Phone in Hessen (e.g. Frankfurt), radio set up before this change | no question, no restart; row shows “Automatic: Hessen · EU_868, ShortSlow” | pending |
| R2 | Choose “Europa” by hand | radio restarts once on LongFast, message “now uses the settings of the radios in Europa”; circle messages to a radio still on ShortSlow do not arrive over radio (expected) | pending |
| R3 | Back to “Automatic by location” in Hessen | ShortSlow again, one restart | pending |
| R4 | Location 1 km outside Hessen (emulated or real), then 5 km | no change at 1 km, Europa at 5 km | pending |
| R5 | Location not allowed | row says “Automatic, area not known yet”, radio keeps its settings; the dialog offers “Allow location” | pending |
| R6 | Radio on Wi-Fi (gateway) in Europa | gateway values stay (root `msh/EU_868`); after “Nordamerika” by hand: region US, gateway off | pending |
| R7 | Radio with a preset changed by hand (e.g. MediumFast) | the question “Set up radio” as before, no silent change | pending |

### Findings from the first hardware test (02.11.2026)

- **iOS: no radio connection usable.** In the iOS simulator with a radio on Wi-Fi the channel list stays empty (`rc.channels`
  always `null`); on every connect meshtastic-sdk 0.2.0 throws, when creating its device storage
  (`SqlDelightStorageProvider` → `applyStoragePragmas`), `SQLiteException: Queries can be performed using SQLiteDatabase query
  or rawQuery methods only`. Consequence: the app's channel invisible, no radio check, no radio messages. Whether a real iPhone
  via Bluetooth is affected in the same way is **untested** (same storage code, therefore likely). Bug in the SDK,
  not in the app; report to meshtastic-sdk open.
- **Wi-Fi: a second client displaces anynear.** On Wi-Fi the firmware serves only one connection. If another app connects
  (here the Meshtastic app on the phone, automatically), the radio closes the connection of anynear. meshtastic-sdk
  0.2.0 notices this only after 60 s (liveness), reconnects and displaces the other app; so both alternate every minute,
  and packets in that time are lost. Without the other app the connection remained stable. The same happens with a
  second phone with anynear on the same radio. Since 02.11.2026 the app recognizes the pattern (at least 3 drops in
  5 minutes, each after less than 2 minutes of connection, only on Wi-Fi) and shows “The connection
  keeps dropping” on the Radio page; tested in the emulator with a second connection from the development computer, the notice appeared. Open: the late
  detection belongs in the SDK as a bug.
- **Radio of the contact forgotten** (fixed in the ANMP core): packets without a radio number set it to 0.
- **Contacts, circles and people gone after every restart** (fixed in the ANMP core): `setSettings` on start ran before the
  core state was loaded and wrote the empty initial state over it. After the fix the contact survived a restart.
- **Direct messages over radio never arrived**: direct message and reaction over radio were addressed to the person ID (ADR 0042)
  instead of to a device; the recipient discarded them (“route tag unbekannt”, route tag unknown). Fixed: to the device on the
  person's radio, otherwise the one last seen. Tested 02.11.2026: message from the emulator arrived at the phone “over radio and
  internet”.
- **Sender never saw “over radio”**: the confirmation over radio only went out if the radio copy came before the internet copy;
  fixed. In addition the radio rule blocked the confirmation over radio entirely (fixed). Confirmation over radio back to the
  sender still **untested** on hardware (the other side had neither fix).
- **Texts**: “Connect your radio first” despite a connection (channel was missing), error when setting up the channel without a circle
  invisible, “added by you” also for contacts who added you themselves, “set up again” after
  reinstallation on iOS (keychain survives deletion) – fixed.
- **iOS simulator**: app quit once without a crash report; cause unknown.

## Public radio (ADR 0055, 02.11.2026)

New: chats “Everyone nearby” (slot 0 with default key) and “Mesh Hessen” (channel of the community, which the app creates only
after “Yes” to the message from ADR 0054 §1a, without location) in the section “Public radio” below the circles. Sending as ordinary
Meshtastic text, at most 200 bytes, one message per 30 s and channel. Tested on 02.11.2026 with real radios
(results below). The key for “Mesh Hessen” is in `AppConfig.MeshHessen.psk`. Reactions in public
chats can meanwhile also be sent by anynear (ADR 0055 §3).

| Nr | Test | Expected | Result |
|---|---|---|---|
| Ö1 | Message from the official Meshtastic app on the primary channel | appears in “Everyone nearby” with the name of the radio | passed (emulator, Heltec V3 via Wi-Fi, 02.11.2026): messages from other radios in “Everyone nearby” with the radio's name; see finding Ö1 |
| Ö2 | Message from anynear in “Everyone nearby” | appears in the official app on the primary channel | partly (emulator, Heltec V3 via Wi-Fi, 02.11.2026): “Kurzer Funktest” from “Everyone nearby” arrives on the tester's second radio, seen there in anynear; display in the official Meshtastic app pending |
| Ö3 | Connect a radio without the channel “Mesh Hessen”, answer the message with “Yes” | message names the free slots correctly; channel on a free slot, position precision 0, no restart | passed (emulator, Heltec V3 via Wi-Fi, 02.11.2026): message “one of 5 free channels” (7 minus the app's channel and a foreign channel), channel on slot 2, reading back confirmed, position precision 0 (checked with the Meshtastic command line), no restart; uplink and downlink off, see finding Ö3 |
| Ö3a | Answer the message with “No thanks”, later switch on and off again on the Radio page | no channel, no new question; on creates it, off removes it | passed (emulator, Heltec V3 via Wi-Fi, 02.11.2026): after “No thanks” no channel, no more question, also after restarting the app; switch on creates it, off removes it (checked on the device) |
| Ö4 | Channel “Mesh Hessen” already there (from the Meshtastic app, location 16) | no second channel; dialog “Set up radio” because of location | passed (emulator, Heltec V3 via Wi-Fi, 02.11.2026): location set to 16 from outside; dialog “Update radio” with deviation [POSITION], afterwards location 0, no second channel; after pairing again with the channel present no question, chat appears directly |
| Ö5 | Send and receive a message in “Mesh Hessen”, also via the MQTT bridge of Meshhessen | visible in both directions | partly (emulator, Heltec V3 via Wi-Fi, 02.11.2026): sending “Kurzer Funktest” in “Mesh Hessen” passed, state “Passed on”, without a second confirmation question, seen in anynear on the tester's second radio; reception from others in “Mesh Hessen” and path via the MQTT bridge pending |
| Ö6 | Sent message is repeated by another radio | state “Passed on”, never “Delivered” | passed (emulator, Heltec V3 via Wi-Fi, 02.11.2026): “Kurzer Funktest” in “Everyone nearby” and in “Mesh Hessen” shows “Passed on” after a few seconds, never “Delivered”; confirmation question “Visible to everyone” only the first time |
| Ö7 | Reaction from the official app to a public message | is not shown as a separate message | pending: needs the official Meshtastic app |
| Ö9 | anynear reacts to a public message | visible in the official app as a reaction, not as a separate message | pending |
| Ö8 | Channel of another app on slot 1–7 | stays invisible and unchanged | passed (emulator, Heltec V3 via Wi-Fi, 02.11.2026): foreign channel on slot 1 (location 32) neither visible as a chat nor changed, also not when updating the radio |

Still pending: Ö7, Ö9 and the cross-check in the official app for Ö2 (need the official Meshtastic app on a second radio; the CardTracker could not be reached via Bluetooth from the development
computer; “Passed on” only shows that a radio repeated the packet, not that an app displays it)
and the reception part of Ö5.

**Finding emulator:** `adb shell input text` with `%s` for the space confuses Gboard's input; afterwards
no key arrived in the field anymore until the emulator was restarted. Typing word by word with `keyevent 62` in between works. Screenshots
with `screencap` are white in this emulator; the UI can be read out with `uiautomator dump`.

**Finding Ö1:** For messages from radios whose name the own radio does not yet know at reception, the app shows
“Unknown radio”. The app stores the name at reception and does not fetch it later when it becomes known.

**Finding Ö3:** The app creates “Mesh Hessen” with uplink and downlink off, unlike the QR code of the community. Uncritical
as long as anynear radios themselves do not forward anything via MQTT.

**Finding command line:** Also with the Meshtastic command line over TCP the first write command was lost and only worked
on the second attempt (as in finding W4).

## Official warnings by approximate location (ADR 0062, 04.11.2026) – none of it tested

The district comes from the approximate location and the municipality table on the phone (`scripts/regions.py`). So far only
the unit tests (`RegionTableTest`, `AlertsTest`) with fixed coordinates have been run, no device and no emulator.

| Nr. | Step | Expected | Result |
|---|---|---|---|
| R1 | Start the freshly installed app | no question about the location | pending |
| R2 | Switch on “Official warnings” (Android, iOS) | system question “approximate location” or “While Using the App”; after allowing, district name under “Official warnings” | pending |
| R3 | Decline location, button “Allow location” (also after “Don't ask again”) | first the system question, afterwards the settings of the app; without a district no official warning in the list, no notification | pending |
| R4 | Location services off on the phone | sentence “Your location isn't available right now …” with “Try again” | pending |
| R5 | Android without Google services (e.g. GrapheneOS), only Wi-Fi/cellular data | district is found | pending |
| R6 | Travel somewhere else, open the app | district changes on opening, not in the background | pending |
| R7 | iOS: warning for your own district with the app closed (wake-up call) | notification arrives; none for another district | pending |

## Reminder to charge the radio (ADR 0085, 09.11.2026) – none of it tested

So far only the unit tests (`ChargeReminderTest`) with fixed battery levels and times have been run.

| Nr. | Step | Expected | Result |
|---|---|---|---|
| L1 | Radio connected, battery drops to 20 % or less (Android, iOS) | once “Radio almost empty” with the level, also with the app open | pending |
| L2 | further below 20 %, values fluctuate around the threshold | no further notification | pending |
| L3 | Charge the radio (on mains or above 50 %), afterwards below 20 % again | “Radio almost empty” again | pending |
| L4 | Do not connect the radio for 14 days (to test, set the phone's clock forward) | “Radio not seen for a while” with the last known level, also with the app quit | pending |
| L5 | Android: restart the phone after connecting, wait for the period, open the app | reminder arrives on opening at the latest, only once | pending |
| L6 | Switch off “Remind me to charge” under Radio → Advanced | no more reminder, the scheduled one is deleted | pending |

## Direct messages from radios without anynear (11.11.2026) – none of it tested

A direct message from the Meshtastic app arrives as a direct conversation. Replies go back as plain Meshtastic text on
the channel the last message came on (not on `anynear`, which the other side does not know). So far only the unit tests
(`SdkMeshLinkRulesTest`, `MessageStoreTest`) have been run.

| Nr. | Step | Expected | Result |
|---|---|---|---|
| M1 | Meshtastic app on a second radio sends a direct message to the own radio (channel 0) | appears as a direct conversation; strip “Radio without anynear …” at the top; contact details say “Doesn't use anynear …” | pending |
| M2 | Reply from anynear | arrives readable in the Meshtastic app | pending |
| M3 | As M2, but the second radio does not know the own radio's key yet (fresh node database) | reply still readable, because it goes on channel 0 | pending |
| M4 | React to the received message | reaction shows in the Meshtastic app | pending |
| M5 | Restart the app, reply again | still goes on the channel of the last message | pending |

## Page “Radios” and first pairing (12.11.2026) – none of it tested

The page “Radios” now shows “My radios” at the top (the assigned one first, grayed out with “Not connected” while it
is not connected) and “Pair a new radio” below with the radios found and a “Search” button. “Set up another radio” is
gone: pairing a new one replaces the assigned one, which stays under “My radios”. The first pairing still asks for the
PIN from the radio's display (Meshtastic random PIN). On Android the app runs a one-second Bluetooth search right before
pairing so that the PIN request comes up as a window in front instead of only as a notification, and while it waits it
shows “Enter the number” with a direct way to the Bluetooth settings. So far only the unit tests have run.

| Nr. | Step | Expected | Result |
|---|---|---|---|
| R1 | Fresh Heltec V3, open “Radios”, tap “Pair” | phone asks for the number, it is on the display; afterwards connected and at the top of “My radios” | pending |
| R2 | Switch the assigned radio off | its row turns gray with its name and “Not connected”, no endless “Connecting …” | pending |
| R3 | “Disconnect” on its page, back to “Radios” | row gray, “Disconnected”, “Connect” button reconnects | pending |
| R4 | Second radio nearby, “Search”, “Pair” it | switches to it, the first one stays under “My radios” as “Nearby”/“Not connected” | pending |
| R5 | Android, fresh Heltec V3, guided setup, “Connect” | PIN window comes up in front (not only in the notifications); meanwhile “Enter the number” with “Open Bluetooth settings” | pending |
| R6 | As R5, dismiss the PIN window, tap “Open Bluetooth settings” | Bluetooth settings open, tapping the radio there asks for the number again, afterwards connected | pending |
| R7 | Android, Heltec V3 with Meshtastic and Wi-Fi on, plug it in, choose anynear, “Set up a new radio” | says Bluetooth is off; “Turn on Bluetooth” restarts it, the app finds exactly this radio over Bluetooth, pairs it, asks for the number; then “… is connected” | pending |
| R8 | Android, Heltec V3 with factory software, plug it in | dialog says the Meshtastic software is missing, “Open instructions” opens flasher.meshtastic.org | pending |
| R9 | “Connect with a USB cable” under “Pair a new radio” and in the guided setup | opens the dialog without the choice and searches right away | pending |
| R10 | Plug in with a radio already assigned | choice comes up, “Rescue the radio” is the main button and leads to the known rescue | pending |
| R11 | As R7, but the radio is connected to the Meshtastic app on another phone | after 90 s “doesn't show up over Bluetooth”, “Try again” works once disconnected there | pending |

## Set up over USB: install Meshtastic, way of use, use over the cable (ADR 0094, 12.11.2026) – none of it tested

Android only. The ROM loader protocol is tested against a simulated ROM (`Esp32LoaderTest`), the flow with fakes
(`UsbRescueFlowTest`); nothing of it has run on a real radio. Needs a signed radio software list with
`usb_installs` from the relays.

| Nr. | Step | Expected | Result |
|---|---|---|---|
| E1 | Heltec V3 with factory software, plug in, anynear, choice | “Set up a new radio” is the main button, “Use with a USB cable” grayed out with its reason | pending |
| E2 | E1, “Set up”, pick “Heltec V3” | chip recognized as ESP32-S3, download, progress up to 100 %, radio restarts with Meshtastic (display) | pending |
| E3 | after E2, “On the move” | found over Bluetooth by its name, PIN window, connected | pending |
| E4 | after E2, “Stationary on Wi-Fi”, enter Wi-Fi | radio joins the Wi-Fi, app finds it there and connects; row says “on Wi-Fi” | pending |
| E5 | radio from E4, unplug router power, plug radio into the phone | choice offers “Use with a USB cable” and “Switch to Bluetooth” | pending |
| E6 | E5, “Use with a USB cable” | connected, row says “with USB cable”; unplug: “Not connected”, plug in again: connects by itself | pending |
| E7 | E5, “Switch to Bluetooth” | Wi-Fi off, found over Bluetooth, PIN, connected | pending |
| E7a | Set-up radio on Bluetooth, plug in, “Switch to Wi-Fi”, enter Wi-Fi | note about power cuts shown; radio joins the Wi-Fi, app finds it there and connects; row says “on Wi-Fi” | pending |
| E8 | set-up radio, plug in, “Rescue”, “Install Meshtastic again” | writes without asking the model, then asks the way of use | pending |
| E9 | E2, pull the cable while writing, plug in again | the next attempt writes everything again and works | pending |
| E10 | Board with native USB (ESP32-S3 without USB-serial chip, e.g. Heltec V4) | since the fix of 12.11.2026 expected to work (esptool’s reset order), see L1–L2 | pending |

## Radio without Meshtastic on the USB cable (fix 12.11.2026) – none of it tested

A new radio, an empty one or one with other software was not recognized when plugged in. Changes: the USB filter
lists Espressif and Nordic entirely and more CH34x chips; the check whether Meshtastic answers gives up after 12 s
without a frame (before 30 s); an ESP32 on Espressif's own USB is reset into the ROM loader in esptool's order, and a
chip already waiting in the loader (BOOT held) is used without a reset; a silent ESP32 leads to “hold BOOT, plug in”;
an nRF52/RP2040 without Meshtastic is asked into its bootloader (1200 baud touch) and gets the `.uf2` onto its drive
instead of going to the ESP32 loader; a device that already shows a drive (UF2) goes straight there. Tested with fakes
only (`UsbRescueFlowTest`, `Esp32LoaderTest`).

| Nr. | Step | Expected | Result |
|---|---|---|---|
| L1 | Empty or factory ESP32-S3 with native USB (Heltec V4, T-Beam Supreme), plug in | Android offers anynear; within about 15 s the dialog says Meshtastic is still missing, “Set up” leads to the model choice | pending |
| L2 | L1, “Set up” | chip recognized as ESP32-S3 over USB-Serial/JTAG (debug log `ROM-Lader: esp32s3`); the USB connection survives the reset into the loader; writing works | pending |
| L3 | ESP32 board whose loader does not answer, “Set up” | hint “hold BOOT, plug in again, Search”; done that way, the models are offered | pending |
| L4 | Board with CH340K (0x1A86:0x7522) or CH343, factory software, plug in | Android offers anynear; recognized as without Meshtastic | pending |
| L5 | T1000-E / RAK4631 / T-Echo with factory software, plug in, “Set up” | asked into the bootloader, drive appears; pick model and drive, `.uf2` written, Meshtastic starts | pending |
| L6 | nRF52 whose software ignores the touch | hint to press reset twice is shown; after the double tap the drive appears and writing works | pending |
| L7 | nRF52 already in the bootloader (drive visible), plug in | goes straight to model/drive, no wait for Meshtastic | pending |
| L8 | Radio with Meshtastic, plug in (regression) | still recognized with name and software within a few seconds, no early “no Meshtastic” | pending |
| L9 | Heltec V3 with a charging-only cable (or OTG off), “Connect with a USB cable” | “Searching …” with progress bar visible for about a second, then “Your phone doesn't see any device on the USB cable …” with the hints on cable, power and OTG; with a data cable, “Search” finds it | pending |
| L10 | Heltec V3 with factory software, data cable, “Set up” | since ADR 0095 the choice of radio (A1); debug log `ROM-Lader: esp32s3, Flash im Chip 8 MB, PSRAM 0 MB` and `Modell erkannt: HELTEC_V3 (USB 10c4:ea60, esp32s3)`; “Other Meshtastic-compatible radio” shows the full list | pending |
| L11 | Other empty ESP32 boards (Heltec V4, T-Deck, T-Beam Supreme, Station G2 …) | no wrong suggestion; note the `ROM-Lader` and `USB` values from the debug log so the board can be added to `UsbModelHints` | pending |

## Choice of radio when setting up over USB (ADR 0095, 13.11.2026) – none of it tested

After plugging in a radio without Meshtastic (and with “Set up a new radio”) the dialog shows the recommended radios
from `https://anynear.app/v1/radios.json` with picture, name and what they are good for; the recognized one is
preselected, the main button is “Install Meshtastic”, every other model sits behind the small “Other
Meshtastic-compatible radio”. Recommended radios on another chip are grayed out with “Doesn't match the plugged-in
device”. ESP32 (Meshnology N35) is written over the ROM loader, nRF52 (T1000-E, Solar Node) is asked into its bootloader
only after the choice and gets the `.uf2` onto its drive. Until the list is live the app uses the last good list, else
its own copy. On the iPhone, where Android offers the cable, a note leads to the webapp on a computer. Tested with fakes
only (`UsbRescueFlowTest`).

| Nr. | Step | Expected | Result |
|---|---|---|---|
| A1 | Meshnology N35 (Heltec V3) with factory software, plug in, “Set up” | N35 with picture and “On the go or at home” preselected; T1000-E and Solar Node grayed out with the reason; “Install Meshtastic” writes it, then the way of use | pending |
| A2 | SenseCAP T1000-E with factory software, plug in, “Set up” | T1000-E preselected (if its USB IDs are among those in `radios.json`), N35 grayed out; only after “Install Meshtastic” the drive appears; pick the drive, `.uf2` written, Meshtastic starts | pending |
| A3 | SenseCAP Solar Node with factory software, plug in | Solar Node offered and preselected once the software list has a `.uf2` for `SEEED_SOLAR_NODE`; otherwise not offered (no file); note its USB IDs from the debug log | pending |
| A4 | A1 in flight mode (no internet) | pictures show the radio icon instead, nothing breaks; the list comes from the last good one or the app's copy | pending |
| A5 | Board not in `radios.json` (e.g. RAK4631), plug in | nothing preselected, “Install Meshtastic” grayed until a radio is chosen; “Other Meshtastic-compatible radio” shows the full list | pending |
| A6 | Rescue “Stuck in the bootloader” without a known last model | same choice, only radios that go onto a drive | pending |
| A7 | iPhone: “Radios” page and first setup | note “Set up with a USB cable on a computer”, “Open web app” opens the webapp setup page | pending |

## Stationary on Wi-Fi in the web app (ADR 0095 §5, 15.11.2026) – none of it tested on hardware

After installing Meshtastic over the ROM loader, `web.anynear.app/radio` opens the same cable at 115200 baud, waits for
Meshtastic and, for a radio with Wi-Fi, asks “How will you use the radio?”. “Stationary on Wi-Fi” writes radio profile,
name, Wi-Fi and the gateway values from the radio list in passes (core `RadioWifiSetup`, the same values as the app),
then asks the radio whether it got into the Wi-Fi. Tested against `meshtasticd --sim` over TCP only (`MeshtasticdTest`
in ANMP, and the JS facade from Node): profile, Wi-Fi, gateway, third pass without deviation.

| Nr. | Step | Expected | Result |
|---|---|---|---|
| WW1 | Chrome on macOS, N35 (CP2102) with factory software, install, “Stationary on Wi-Fi”, right password | “Radio is restarting …”, then the question; after “Connect” “The radio is on your Wi-Fi”; the iPhone app finds it under “Connect a radio” on the same Wi-Fi | pending |
| WW2 | WW1 with a wrong password | “Your radio can't get onto the Wi-Fi …” and the form again; with the right password it works without installing again | pending |
| WW3 | WW1, then Meshtastic CLI or the app: MQTT and channels | MQTT on with address and login of the radio list, uplink on channel 0 (and `anynear` once the app added it), downlink off, `ignore_mqtt` off, region EU_868, Hessenmesh preset | pending |
| WW4 | ESP32-S3 with its own USB (no USB-serial chip), install, “Stationary on Wi-Fi” | the port comes back after each restart, or a clear message; note what Chrome does | pending |
| WW5 | WW1 on Windows with Edge (CH340 board) | as WW1 | pending |
| WW6 | “On the move” instead | nothing written; the phone pairs over Bluetooth as before | pending |
| WW7 | T1000-E (no Wi-Fi) | no question, the done page as before | pending |

## New versions and required update (ADR 0096, 13.11.2026) – none of it tested

Notices about a new version come from the store or the own updater, without any list: `direct` from the GitHub
releases, `play` from Google Play (In-App Updates), iPhone from the App Store (Apple's public lookup, `X.Y.Z`),
TestFlight reports by itself. The signed radio list only enforces (`app`: `min`, `from`): before `from` a band
“From <date> …” with “Update” and “Later”, after `from` a full screen over the app if there is a way to the new
version. Tested with unit tests only (`UpdateNoticeTest`). The rule needs a signed radio list with `app` from the
anynear network (or a test network).

| Nr. | Step | Expected | Result |
|---|---|---|---|
| U1 | `play` from the internal test track, newer build on the track, open the app | dialog “New version”; “Update now” starts Play's flexible flow, app restarts after the download | pending |
| U2 | iPhone from the App Store, newer version in the store, open the app | dialog “New version”; “Update now” opens anynear in the App Store | pending |
| U3 | iPhone via TestFlight, newer build | no dialog from the app (TestFlight reports it) | pending |
| U4 | `direct`, `app.min` above the build, `from` in a few days | band with the date; “Later” hides it until the next day | pending |
| U5 | `direct`, `from` passed, updater knows a build ≥ `min` | full screen “Please update anynear”; Back does not close it; “Update now” downloads and installs; messages and notifications keep arriving meanwhile | pending |
| U6 | `direct`, `from` passed, newest release below `min` | only the band, no block | pending |
| U7 | Block with a running check-in | check-in card with “All good” and “I'm done” works on the block screen | pending |
| U8 | dropped: the backup file is gone (ADR 0105) | – | – |
| U9 | `play`, `from` passed, Play offers a build ≥ `min` | block; “Update now” opens Play's full-screen update | pending |
| U10 | iPhone, `from` passed | block; “Update now” opens the App Store page (TestFlight: the app TestFlight; not in the store yet: anynear.app/de/app-laden/) | pending |
| U11 | Debug build, `from` passed | never blocked, only the band | pending |

## Settings in the network and replacing a phone (ADR 0106, 14.11.2026) – none of it tested

Unit tests only (`SettingsSyncTest`, ANMP `replacingPhoneTakesOverTheDeviceSettings`).

| Nr | Test | Expected | Result |
|---|---|---|---|
| G1 | Phone with a paired radio and an alert region; new phone, emergency card, choose the old phone under “Does this phone replace one of your devices?” | after the restart the radio and region are set; the old phone is gone from “My devices” without a further question | pending |
| G2 | Same, choose “No, it is an additional one” | no restart, device settings stay empty, the question does not come again | pending |
| G3 | Add a tablet with the QR code, answer “No, it is an additional one” | the phone stays under “My devices”, the tablet keeps its own settings | pending |
| G4 | Change life sign circle on the phone, restart the tablet | tablet uses the same circle | pending |
| G5 | Without internet right after recovering | question comes once the network answers (up to 2 minutes), otherwise not | pending |

## Direct messages to the person and loading notice (ADR 0107, 14.11.2026) – none of it tested

Every packet to a person goes once to its mailbox recipient; a phone back from the emergency card reads all direct
conversations. After the update the core moves old entries and the app hands over its local direct history once.

| Nr | Test | Expected | Result |
|---|---|---|---|
| DP1 | Anna (one phone) and Ben write each other, then Anna comes back with the emergency card on a second phone | “Loading your messages …” over the list, then both directions of the conversation are there, Anna's own messages included | Android emulator (two Pixel 9, API 36, real relays) passed 15.11.2026: both directions back, new messages both ways after the restore, replaced phone gone from “My devices” without a question; the loading notice was too short to see there; phones pending |
| DP2 | As DP1, but the conversation was written before the update; update Anna's phone first, then the card | old messages of both sides come back once, no duplicates | pending |
| DP3 | Anna with phone and tablet, Ben writes once | both get it; Ben's message is one entry, not one per device | pending |
| DP4 | Ben without internet sends Anna a direct message over radio; Anna's phone at the radio, tablet online | tablet shows the message about 10 minutes later | pending |
| DP5 | Two devices of Anna at the radio, Ben sends over radio | both take it, Ben sends it once | pending |
| DP6 | New circle member or added device opens a circle and a direct chat while loading | “Loading earlier messages …” at the top until the messages are there | pending |
| DP7 | Anna reads all circles and chats on her phone, then Ben writes one more message in a circle; Anna comes back with the emergency card on a second phone | while loading the circle rows say “Loading …”, not “No messages yet”; afterwards only Ben's last message is unread, no notifications for the rest | pending |
| DP8 | As DP7, but the phone ran a version without “read until” (no mark yet) | everything sent before the restore is read, a message sent during loading is unread | pending |
| DP9 | Phone and web app, read a circle on the phone | the circle is read in the web app a few seconds later, also after reloading | pending |


## Reply from the notification and on watches (17.11.2026) – watches and iPhone not tested

Message notifications of circle chats and direct messages have “Reply” with ready-made short replies (“Yes”, “No”,
“Okay”, “On my way”, “Will call you soon”) and free text. The reply goes the same way as from the chat, without opening
the app. Public chats and alerts have no reply.

| Nr | Test | Expected | Result |
|---|---|---|---|
| NR1 | Android: Ben writes Anna, app in the background, Anna taps a ready-made reply in the notification | reply arrives at Ben, Anna's notification shows it and the app does not open; Ben sees “Read” | Android emulator (two Medium Phone, API 36, real relays) passed 17.11.2026 with “Okay”: arrived at Ben, read receipt; Android keeps the notification with “Me: Okay” until it is swiped away; phones pending |
| NR2 | Garmin watch paired with an Android phone (Garmin Connect), message arrives | the watch offers “Reply” with text responses; a chosen one arrives at the sender. Note: Garmin may show its own list of text responses from Garmin Connect instead of the app's | pending |
| NR3 | Wear OS watch, message arrives | “Reply” with the app's ready-made replies (and generated ones if the watch offers them); a chosen one arrives | pending |
| NR4 | iPhone, app closed, push wakes the extension, Anna long-presses the notification and replies | text field with “Send”; iOS starts the app in the background, the reply arrives at Ben, the notification disappears | pending (the iOS simulator only built and started the app, no reply sent) |
| NR5 | Apple Watch paired with the iPhone, as NR4 | “Reply” on the watch with the watch's own suggestions, dictation or scribble; reply arrives | pending |
| NR6 | Garmin watch with an iPhone | iOS only passes simple actions to Garmin; no text reply expected on the watch, the notification still arrives | pending |
| NR7 | Reply without a path (e.g. contact without internet and no radio) | notification comes back without sound, “Not sent: …” with the reason | unit test only |

## Location when nobody confirms a check-in (ADR 0121 §10, 19.11.2026) – none of it tested on hardware

Unit tests (`LocationSharingTest`, `LifeSignsTest`, ANMP `thePlaceLeavesThePhoneOnlyWhenNobodyConfirmed`) and one
Android emulator (Android 16, 19.11.2026): the step in the setup with Android's question for precise or approximate, the
section in the settings, the own card naming the place with its time, and the dialog after “Later” and four days (clock
set forward), which does not come back after “No”. Not tried: any phone, iOS, a second device (S5–S7, S9) and radio.

| Nr | Test | Expected | Result |
|---|---|---|---|
| S1 | New setup on Android, step “Your location”, tap “Yes, when something is wrong” | Android asks for precise and approximate location; after the answer the setup goes on; Settings → Location shows the switch on | pending |
| S2 | Same step, tap “No, only for warnings and radio” | Android asks only for the approximate location; switch off; no question after the setup | pending |
| S3 | Same step, “Later” | setup goes on; the question comes as a dialog after three days at the earliest, not right after the setup | pending |
| S4 | Phone that had anynear before the update, with a circle | about 20 seconds after the start one dialog “Your location when something is wrong” with “Applies to: …”; both buttons decide; it does not come again | pending |
| S5 | Same person on a second device after answering on the first | no dialog there; under Settings → Location only “Allow location on this phone” if the permission is missing | pending |
| S6 | Start a check-in with the switch on, do not answer the question | the helper's warning says “The location from … is included”; the card shows time and accuracy; “Show on map” opens the maps app at the right place | pending |
| S7 | As S6, then tap “All good” | the place disappears from the helper's card | pending |
| S8 | As S6 with the app in the background since the start (screen off, phone in the pocket) | the place is the one from the start or the last “All good”, with that time; note how old it is | pending |
| S9 | Circle switched off under Settings → Location, check-in in that circle, no answer | warning without place | pending |
| S10 | iPhone with “Precise Location” off | place a few kilometres wide, the card says so (“accurate to about 3 km”) | pending |
| S11 | iPhone, new setup and after the update (S1–S4) | the same questions; iOS asks once for the location | pending |
| S12 | Both phones with a radio, no internet, no answer to the question | place arrives over radio, or the warning arrives without it when the packet does not fit | pending |

