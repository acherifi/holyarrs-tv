# HolyArrs TV

Apple TV (tvOS 17+) and Android TV (Android 8+) clients for [HolyArrs](https://github.com/HolyArrs/HolyArrs), built to look and move like the HolyArrs web app. The Apple TV app is at the root of this repo; the Android TV app is in [`android/`](android) (see [Android TV](#android-tv) below).

- **Screens:** Home, Explore, New & Popular, Movies, TV shows, Search, Calendar, Requests, Downloads, title pages, people pages and Settings.
- **Add to library** uses the web's Add dialog: folder, pick rules, seasons and "search for missing". Accounts that can't manage the library get **Request** instead.
- **Open in Jellyfin** opens the title in [Swiftfin](https://apps.apple.com/app/id1604098728) through its deep link, `swiftfin://<server>/<user>/item/<item>`. HolyArrs supplies the item and server IDs. Sign in to Jellyfin once in Settings so the app knows your user.
- **Siri Remote:** each row, the sidebar and each page block is its own focus section, so a swipe on the touch surface lands on the row you aimed at. Cards lift and draw a ring on focus without the system's tilt effect, so a thumb resting on the touch surface doesn't make them wobble. Long text, such as overviews and biographies, sits in focusable blocks, so the touch surface can reach the whole page. On a section's first page, the Menu button moves focus to the sidebar instead of closing the app. The hero carousel stops turning while focus is inside it.

## Run it on your Apple TV (free Apple ID is enough)

1. On a Mac with Xcode 16 or later, install XcodeGen with `brew install xcodegen`.
2. `git clone` this repo, `cd` into it, then run `xcodegen generate` and `open HolyArrsTV.xcodeproj`.
3. In Xcode:
   - Under Signing & Capabilities, pick your Team. A free personal team works; apps signed with it must be re-run from Xcode every 7 days.
   - If Xcode complains, change the bundle id.
4. Pair the Apple TV in Xcode: open Window → Devices and Simulators, then on the Apple TV go to Settings → Remotes and Devices → Remote App and Devices. Pick it as the run destination and press Run.
5. On first launch, enter your HolyArrs address (e.g. `http://192.168.1.10:6262`) and an API key with read and write scopes.

## Simulator / CI

Each push to `main` builds the app on GitHub's macOS runners, which are free for public repos. The build also runs in the Apple TV simulator with bundled demo data (`-demo YES -screen <name>`) and pushes screenshots and logs to the `ci-results` branch. The demo data holds public TMDB metadata only.

Fonts: [Geist](https://github.com/vercel/geist-font) (SIL OFL 1.1).

## Android TV

The same screens and features, written in Kotlin with Jetpack Compose, in [`android/`](android).

- **Open in Jellyfin** opens the title in [Jellyfin for Android TV](https://play.google.com/store/apps/details?id=org.jellyfin.androidtv): the app starts Jellyfin's start screen with the item id, the way Jellyfin's own home-screen rows do. Nothing to set up beyond being signed in to Jellyfin on the TV.
- **Remote:** every row remembers the card you left, so moving up and down never lands on a random card. Back closes the page and returns to the exact card you opened it from; on a section's first page it moves to the sidebar, and from the sidebar it leaves the app. Text fields only start typing when you select them, so the D-pad passes over them. Long text sits in focusable blocks that open the whole text.
- **Appearance:** dark by default, light in Settings.

### Install it

Every push that touches `android/` builds the app on GitHub (free for a public repo) and publishes it at a fixed link:

`https://github.com/acherifi/holyarrs-tv/releases/download/android/holyarrs-tv.apk`

- **With the Downloader app** (on the Play Store, by AFTVnews): allow it to install apps when asked, type the link above, and install. Do the same to update.
- **With adb** from a computer on the same network: turn on developer options and network debugging on the TV, then run `adb connect <tv-ip>` and `adb install -r holyarrs-tv.apk`.

Sideloaded apps don't expire. On first launch, enter your HolyArrs address and an API key (scopes read and write).

The APK is signed with `android/app/holyarrs-tv.keystore` (password `holyarrs`) so that each build installs over the last one. It only identifies this sideloaded app; anyone could sign an APK with it, so only install builds from this repo.

### Build it yourself

`cd android && ./gradlew assembleDebug` with JDK 17 and the Android SDK (platform 35). Launch the demo data with `adb shell am start -n io.holyarrs.tv/.app.MainActivity --ez demo true --es screen explore` (screens: home, explore, popular, movies, shows, search, requests, calendar, downloads, settings, title-movie, title-show, add-sheet, person).
