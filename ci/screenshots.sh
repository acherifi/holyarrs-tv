#!/bin/bash
# Boots an Apple TV simulator, runs the app against bundled demo data
# and screenshots every main screen.
set -euo pipefail
APP=$(ls -d build/Build/Products/Debug-appletvsimulator/*.app | head -1)
DEV=$(xcrun simctl list devices available | grep -E 'Apple TV 4K' | head -1 | grep -oE '[0-9A-F-]{36}')
xcrun simctl boot "$DEV" || true
xcrun simctl bootstatus "$DEV" -b
# A fresh simulator restarts its UI once after first boot: let it settle.
sleep 45
xcrun simctl install "$DEV" "$APP"
BID=io.holyarrs.tv.app
mkdir -p shots
xcrun simctl launch "$DEV" $BID -demo YES >/dev/null; sleep 15
for screen in home explore popular movies shows search title-movie title-show requests calendar downloads person add-sheet settings; do
  xcrun simctl terminate "$DEV" $BID 2>/dev/null || true
  xcrun simctl launch "$DEV" $BID -demo YES -screen "$screen" >/dev/null
  sleep 9
  xcrun simctl io "$DEV" screenshot "shots/$screen.png" >/dev/null
  echo "shot $screen"
done
# dark appearance pass for two screens
xcrun simctl ui "$DEV" appearance dark || true
for screen in home title-show; do
  xcrun simctl terminate "$DEV" $BID 2>/dev/null || true
  xcrun simctl launch "$DEV" $BID -demo YES -screen "$screen" >/dev/null
  sleep 7
  xcrun simctl io "$DEV" screenshot "shots/dark-$screen.png" >/dev/null
done
# keep crash reports, if any
mkdir -p shots/crashes
cp ~/Library/Logs/DiagnosticReports/HolyArrs* shots/crashes/ 2>/dev/null || true
