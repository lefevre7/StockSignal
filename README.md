# StockSignal

StockSignal is a local-first Android app that surfaces buy/sell signals for stocks, explains the reasons behind each signal, and keeps a watchlist + market movers feed. Notifications are generated on-device from fetched market data.

## Features
- Watchlist with signal badges, quick actions, and per-stock alerts.
- Market Movers (largest increasers/decreasers) across 1D/5D/1M/6M/1Y/5Y ranges.
- Stock detail with chart ranges, signal score (avg/mode), confidence, and reasons.
- Local notifications with schedule windows, quiet hours, and suppression rules.
- Signals feed with history and deep links into stock detail.

## Tech stack
- Jetpack Compose + Material 3
- Hilt, Room, DataStore
- WorkManager + local notifications
- Retrofit + Stooq data parsing

## Setup
Requirements:
- Android Studio with JDK 11
- Android SDK 24+

Build:
```sh
./gradlew assembleDebug
```

Run:
- Open the project in Android Studio and run the `app` configuration.

Clear Cache:
```sh
adb shell pm clear com.example.stocksignal
```

## Permissions
- `INTERNET` for data fetches.
- `POST_NOTIFICATIONS` for local alerts.
- `RECEIVE_BOOT_COMPLETED` to reconcile notification state on reboot.

## Notifications
Notifications are generated locally at configured windows (default: market open - 10 minutes, 11:00, 14:00). Quiet hours are respected and only one active notification is shown at a time; suppressed events are queued for the next window.

## Deep links
```
stocksignal://stock/{TICKER}
stocksignal://stock/{TICKER}?eventId={EVENT_ID}
```

## Disclaimer and attribution
- Signals are informational only and not investment advice. The app does not execute trades.
- Market data is sourced from Stooq.com and may be delayed, incomplete, or inaccurate. The app does not guarantee the accuracy, completeness, or timeliness of any data.
- Not affiliated with Stooq.
- This app is provided for educational and informational purposes only.

## Tests - probably also ./gradlew clean build test --stacktrace
Unit tests:
```sh
./gradlew testDebugUnitTest
```

Instrumentation tests (device/emulator required):
```sh
./gradlew connectedDebugAndroidTest
```
