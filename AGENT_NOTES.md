# Notes for a coding agent working on the Mac

Context gathered from the Linux side. Read this first to avoid rediscovering
the project. Everything here is about this repo only. The repo is public:
never commit server addresses, API keys, user names or library contents.

## Repo layout

```
project.yml              XcodeGen spec (no .xcodeproj in git; run `xcodegen generate`)
HolyArrsTV/
  App/HolyArrsApp.swift  @main, RootView: Shell() if configured, else SettingsView(firstRun: true);
                         also applyStartScreen() (`-screen <name>` launch arg)
  App/AppState.swift     ObservableObject: server, apiKey, jellyfin*, section, paths, toast, loadMe(), reconnect()
  App/Shell.swift        Sidebar (icon rail -> 400pt overlay when focused) + page NavigationStack per Section
  Core/API.swift         HTTP client, demo mode reads Resources/Demo/*.json
  Core/Models.swift      Codable models matching the HolyArrs API
  Core/Theme.swift       Colors, Typo (Geist fonts), spacing, radii
  UI/Components.swift    Cards, shelves, pills (PillButtonStyle), text blocks, etc.
  Screens/*.swift        Home, Explore (+New & Popular), Library, Search, Calendar/Requests/Downloads
                         (ActivityViews), TitlePage, AddSheet, SettingsView
  Resources/Demo/        Fixtures used with `-demo YES` (public TMDB data only)
android/                 Separate Kotlin/Compose Android TV app (not relevant on the Mac)
ci/screenshots.sh        Simulator screenshots used by .github/workflows/build.yml
.github/workflows/       build.yml (tvOS, macOS runner, pushes results to `ci-results` branch),
                         android.yml (APK release)
```

## Build settings (project.yml)

- tvOS deployment target **17.0**, Swift 5.10, `TARGETED_DEVICE_FAMILY: 3`.
- `PRODUCT_BUNDLE_IDENTIFIER: io.holyarrs.tv.app`, `CODE_SIGN_STYLE: Automatic`,
  `DEVELOPMENT_TEAM: ""`. For a free Personal Team, set the team in Xcode and, if
  the ID is taken, change the bundle id locally. Don't commit a team id or a
  personal bundle id. Note: `xcodegen generate` overwrites the Xcode signing
  settings, so set the team again after regenerating, or use a local
  uncommitted override.
- Launch args for testing: `-demo YES -screen settings` (other screens: explore, popular,
  movies, shows, search, requests, calendar, downloads, title-movie, title-show,
  add-sheet, person).

## Signing / install on a real Apple TV (what the user hit)

- Error "Your team has no devices from which to generate a provisioning profile"
  means the Apple TV isn't registered with the Personal Team yet. Fix:
  pair the TV (Devices window, shortcut **⇧⌘2**. In the user's Xcode version it isn't
  in the Window menu. Also reachable via the run-destination dropdown →
  *Manage Run Destinations…*), pick the **Apple TV itself** as run destination
  (not "Any tvOS Device"), keep "Automatically manage signing", click *Try Again*.
- If the TV is greyed out as a destination: wait for "preparing device", enable
  Developer Mode on the TV if prompted, and check that the TV's tvOS version is at
  least the 17.0 deployment target.
- Free team builds expire after 7 days. Re-run from Xcode.

## Fixed: can't reach the address / API key fields with the Siri Remote

Cause: tvOS moves focus geometrically. Each text field sat right of a 260 pt
label, and the "Connect" button was left-aligned under the labels. Nothing
focusable was above Connect, so focus could never leave it.

Fix (`HolyArrsTV/Screens/SettingsView.swift`): each field is a full-width
focusable button row (`FieldRow`) that shows the value, a masked secret or the
placeholder. Selecting it opens `FieldEditor`, a full-screen cover with one
focused text field. Focus starts on the address row (`.defaultFocus`).
Lesson: keep focusables in one column, or make rows full width.

## Sidebar focus (Shell.swift)

Selecting an item, Right, and Menu all close the sidebar. `closeSidebar()` calls
`resetFocus(in: ns)`, and the page is the preferred default focus in `ns`.
Disabling the sidebar items does not move focus out of them.
Menu follows Apple's convention: it closes the open sidebar, goes back on a pushed
page, and leaves the app on a section's first page. It never opens the sidebar.

Entering the sidebar starts on the current section (`onChange(of: sidebarFocus)`),
not on the item level with where focus was in the page.

## Playback: Jellyfin or Infuse

Settings → Playback picks the app (`AppState.player`, default Jellyfin).
- Jellyfin: "Open in Jellyfin" → Swiftfin deep link from HolyArrs' watch links.
- Infuse: "Play in Infuse" → `infuse://movie/<tmdb>?play` or `infuse://series/<tmdb>?play`
  (Infuse 8.4.7+, Apple TV supported). It only plays titles in Infuse's *library*:
  the Jellyfin server must be added in Infuse as a Jellyfin share with "Include in
  Library" on. Otherwise Infuse shows a TMDB placeholder page with a Search button.

## App icon

`HolyArrsTV/Assets.xcassets` holds the layered tvOS icon (Back gradient, Middle
diamond, Front star) and the Top Shelf images, drawn from the `BrandMark` geometry.
XcodeGen sets `ASSETCATALOG_COMPILER_APPICON_NAME` to "App Icon & Top Shelf Image".

## Testing with the remote on the Mac

Xcode 27 has no Simulator.app. To press remote buttons, use a throwaway UI test
target (XCUIRemote) in a scratch copy of the project, and read
`hasFocus == true` from the element tree. Don't add the test target to the repo.

## Conventions

- Match the HolyArrs web app's look (Theme/Typo); cards lift + ring on focus, no
  system tilt (`.buttonStyle` custom styles in UI/Components.swift).
- Each row / page block is its own `.focusSection()`.
- CI builds every push to `main` for free on macOS runners. Don't change the
  workflows or signing for local convenience.
