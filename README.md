# HolyArrs TV

An Apple TV (tvOS 17+) client for [HolyArrs](https://github.com/HolyArrs/HolyArrs), built to look and move like the HolyArrs web app.

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
