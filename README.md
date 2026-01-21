# StockSignal

StockSignal is a local-first Android app that surfaces buy/sell signals for stocks, explains the reasons behind each signal, and keeps a watchlist + market movers feed. Notifications are generated on-device from fetched market data.

## Features
- **Holding Period Customization**: Choose your investment timeframe (Hours, Days, Weeks, Months, Years) to get signals optimized for your strategy.
- **Watchlist** with signal badges, quick actions, and per-stock alerts.
- **Market Movers** (largest increasers/decreasers) across 1D/5D/1M/6M/1Y/5Y ranges.
- **Stock Detail** with chart ranges, signal score (avg/mode), confidence, and reasons.
- **Historical Data Accumulation**: Passively accumulates up to 1 year of 10-minute intraday data.
- **CSV Export**: Export accumulated historical intraday data for analysis.
- **Local Notifications** with schedule windows, quiet hours, and suppression rules.
- **Signals Feed** with history and deep links into stock detail.

## Tech stack
- Jetpack Compose + Material 3
- Hilt, Room, DataStore
- WorkManager + local notifications
- Retrofit + Stooq data parsing
- Vico charts

## Architecture overview
- UI: Compose screens with ViewModels and Compose Navigation.
- Data: Repositories backed by Room caches and DataStore settings.
- Networking: Retrofit Stooq API + HTML/CSV parsers.
- Signals: Domain indicator engine computes scores and reasons.
- Background: WorkManager jobs for scheduled notifications and robots.txt checks.

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
Notifications are generated locally at configured windows (default: market open - 10 minutes, 11:00, 14:00). Quiet hours are respected and only one active notification is shown at a time; suppressed events are queued for the next window. Signals older than 7 days are treated as stale and will not notify.

## Deep links
```
stocksignal://stock/{TICKER}
stocksignal://stock/{TICKER}?eventId={EVENT_ID}
```

## Open TODOs
- Stock detail follow action placeholder in `app/src/main/java/com/example/stocksignal/ui/stockdetail/StockDetailScreen.kt`.
- Intraday API upgrade/backfill note in `app/src/main/java/com/example/stocksignal/data/repository/StockRepository.kt`.

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

Lint:
```sh
./gradlew lintDebug
```

## Using git lfs to upload large files
If simply using it like it's intended doesn't work (because no authentication gets to git lfs and therefore there is the error: " % git push
error: Authentication error: Authentication required: You must have push access to verify locks
error: failed to push some refs to 'https://github.com/lefevre7/StockSignal.git'"), then disable locksverify with "git config lfs.locksverify false" and maybe even remove the Remove the LFS push hook
rm -f .git/hooks/pre-push, and then push again: git push origin main
