# Contributing

Issues and pull requests are welcome. Please do not report vulnerabilities as an issue, but as described in
[SECURITY.md](SECURITY.md).

## Building and testing

Requirements: JDK 21, Android SDK (platform 37), Xcode for iOS, plus the neighbouring repositories `anynear/ANMP` and
`anynear/ANDP` as `../ANMP` and `../ANDP` (composite build, see `settings.gradle.kts` and [README.md](README.md)).

```bash
./gradlew :shared:testAndroidHostTest      # unit tests
./gradlew :androidApp:assembleDebug        # debug APK
```

If the core lacks something (format, protocol, logic without UI), the change belongs in ANMP or ANDP, not in the
app.

## Pull requests

- Pull requests need green CI (`.github/workflows/ci.yml`: unit tests and debug APK). This repository is published
  from the project's working repository; nobody pushes to `main` here directly. An accepted pull request is applied
  there with you as co-author (`Co-authored-by`), arrives here with the next update of `main`, and is then closed with
  a link to that commit.
- Every text that people see is in German and English in the catalogues under
  `shared/src/commonMain/kotlin/app/anynear/i18n/`.
- Changes to stored values come with a migration and a test (`data/StorageVersion.kt`).
- No secrets (keys, tokens, certificates, passwords) and no personal data of real people in code,
  tests, examples or log excerpts.
- Do not present anything as tested on hardware that has not run on real hardware
  ([docs/hardware-tests.md](docs/hardware-tests.md)).

## Licence and Contributor License Agreement

Contributions need your agreement to the Contributor License Agreement in [CLA.md](CLA.md): you keep the copyright and
give the project a licence that also allows it to license your contribution under other terms. A bot asks for your
agreement in your first pull request; you agree by commenting the sentence it shows. Accepted contributions stay
available under GPL-3.0-or-later, like the rest of the repository. The name “anynear” and the logos are excluded; what applies to
forks is described in [TRADEMARKS.md](TRADEMARKS.md).
