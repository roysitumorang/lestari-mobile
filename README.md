# lestari-mobile

Android app for the **Lestari** back office: every screen of
[lestari-client](https://github.com/roysitumorang/lestari-client) (80 pages: products, stock, customers, sales,
purchases, 30 reports, branches, settings, staff, online orders, PPOB, billing …) talking to
[lestari-backend](https://github.com/roysitumorang/lestari-backend).

| | |
|---|---|
| Language | Java 17 (no Kotlin) |
| Dependencies | **none** — Android platform APIs only (no AndroidX, no third-party libraries) |
| Android | minimum 15 (API 35), target/compile 16 (API 36) |
| Build | Gradle 8.14.3 wrapper + Android Gradle Plugin 8.13.0 |
| UI | built in Java code from platform widgets (no XML layouts); only the launcher icon, theme and network config are resources |

## Build and run

```bash
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                   # on a connected device / running emulator
```

Or open the folder in Android Studio and press Run.

On the sign-in screen, set **Server** to your API address:

| Where the backend runs | Server |
|---|---|
| Your computer, app in the Android emulator | `http://10.0.2.2:8080/api/v1` (the default) |
| Behind nginx / HTTPS (see lestari-client `deploy/nginx`) | `https://backoffice.example.com/api/v1` |
| Your computer, app on a phone on the same Wi-Fi | use HTTPS (e.g. through nginx), or add the computer's address to `res/xml/network_security_config.xml` |

Plain HTTP is only allowed for `10.0.2.2`, `localhost` and `127.0.0.1`
(`app/src/main/res/xml/network_security_config.xml`); everything else must be HTTPS.

Demo account (after `lestari seed` on the backend): `demo@lestari.id` / `demo12345`.

## How it is organised

```
app/src/main/java/id/lestari/mobile/
  LestariApp.java     API client with tokens in SharedPreferences, worker pool, main-thread handler
  core/               plain Java (java.net + org.json) — no Android imports
    Api.java            HttpURLConnection client: bearer token, X-Branch-Id, one refresh on 401,
                        base64 passwords, multipart uploads, file downloads, PATCH
    Field, Action, Page, Registry, Lookup, Opt, Format, Json, Host
  defs/               the 80 page definitions + navigation menu (ported from lestari-client js/modules)
  ui/                 Android screens interpreting the definitions
    MainActivity        single activity: toolbar + screen stack; implements core.Host
    CrudScreen          list / search / tabs / filters / sort / paging / create / edit / delete / bulk delete / import / export
    ReportScreen        period presets + date range + filters → summary cards, chart, rows, totals, details, export
    SettingsScreen      GET → form → PUT (dotted keys sent as nested objects), toolbar actions, extra table below
    FormView/FormScreen every field type: text, number, money, percent, password (base64), date/time pickers,
                        searchable select, multi-select, switch, colour, image upload, file, editable item rows
    Dashboard, Missions, Plugins, ResetData, CardList (integrations, loans) — the hand-written pages
    ChartView, ImageLoader, Dialogs, Theme (emerald / ivory / charcoal), Ui
```

The same idea as the web client: pages are **declared** (fields, columns, filters, row and toolbar actions) and
generic screens render them, so adding a field or an action is a one-line change in `defs/`. Because `core/` and
`defs/` are plain Java, they can be exercised on a desktop JVM against a running backend (every page's list,
details, edit round-trips, exports, reports, settings and actions) without an emulator.

## Behaviour worth knowing

- **Passwords** are sent base64-encoded (UTF-8, standard alphabet), as the backend expects. PINs are sent as digits.
  Base64 is not encryption — use HTTPS.
- **Ids** are UUID strings everywhere.
- **Branches**: owners and administrators can switch branch from the home screen; requests then carry
  `X-Branch-Id`, and lookups are reloaded.
- **Downloads** (receipts, invoices, reports, barcode sheets, exports) are saved to *Downloads* through MediaStore
  (no storage permission) and offered to a viewer app.
- **Uploads**: images use the system photo picker; transfer receipts and imports use the document picker.
- **Plugins**: pages that need an inactive plugin show a lock screen with a link to *Plugins*.
- **Two-step verification**: setup shows the secret key and an "Open in authenticator app" (`otpauth://`) button;
  sign-in asks for the code when the account needs it.
- Edge-to-edge layout with system-bar, cutout and keyboard insets; predictive back.
