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

## Open bug: can't reach the address / API key fields with the Siri Remote

The user reports: on the Settings screen they can't move focus to the text fields
to type the HolyArrs URL and API key. This happens on a real Apple TV, and probably
on the first-run screen as well. The cause has not been reproduced or confirmed yet.

Relevant code: `HolyArrsTV/Screens/SettingsView.swift`

- Whole screen is a `ScrollView(.vertical)` → `VStack` of `panel(...)`s.
- `panel()` wraps content in a `VStack` with background/overlay shapes and
  `.focusSection()`.
- `field()` is `HStack { Text(label).frame(width: 260); TextField / SecureField }`
  with `.font(Typo.body)`. No `@FocusState`, no `defaultFocus`, no explicit
  `.focusable`.
- The only other focusables on the first-run screen are the "Connect" button
  (`PillButtonStyle`) and, when configured, the Jellyfin fields/button.
- In `Shell.swift` the page container has `.focusSection()` +
  `.prefersDefaultFocus(true, in: ns)` inside `.focusScope(ns)`. Sidebar items are
  `.disabled(!sidebarReady)` for the first 0.8 s so tvOS starts focus in the page.
  Menu on a section's first page sets `sidebarFocus` (onExitCommand).

Suspicions to check (in the simulator with `-demo YES -screen settings`, and first run
without config):

1. A bare TextField inside an HStack inside a focusSection + ScrollView may get
   no usable focus frame on tvOS 17/18. Check with the focus debugger:
   pause, then in lldb run `po UIFocusDebugger.status()` and
   `po UIFocusDebugger.checkFocusability(for: <view>)`.
2. Focus may land on "Connect", and moving up never reaches the fields because they're
   not focus candidates.

Suggested robust fix, the same approach as the Android app, which had the same
problem: don't put raw text fields in the focus chain. Make each field a
focusable **Button row** that shows the current value, or a placeholder or
masked key. Selecting it opens a sheet or full-screen cover with one
`TextField`/`SecureField` that gets focus on appear (`@FocusState` +
`.onAppear { focused = true }`), so the tvOS keyboard comes up right away.
Save on submit. Also put `.defaultFocus` on the address row on first run so the
user starts there. Keep the look consistent with `PillButtonStyle` and `Theme`.
Then verify with `ci/screenshots.sh`, or by running in the simulator and
moving focus with the arrow keys.

## Conventions

- Match the HolyArrs web app's look (Theme/Typo); cards lift + ring on focus, no
  system tilt (`.buttonStyle` custom styles in UI/Components.swift).
- Each row / page block is its own `.focusSection()`.
- CI builds every push to `main` for free on macOS runners. Don't change the
  workflows or signing for local convenience.
