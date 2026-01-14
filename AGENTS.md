# StockSignal Agent Notes

Purpose: Architecture and workflow reference for contributors.

## Architecture Overview
- UI: Jetpack Compose screens with ViewModels and Compose Navigation (single-activity).
- DI: Hilt modules provide repositories, DAOs, and network clients.
- Data: Room caches for market data + DataStore for settings.
- Networking: Retrofit Stooq API + HTML/CSV parsers.
- Signals: Indicator engine in the domain layer computes scores and reasons.
- Charts: Vico (Compose) for sparkline and detail charts.
- Notifications: WorkManager scheduled windows + NotificationManager publishing; stale signals (>7 days) are suppressed.

## Data Flow
- Stooq API -> parsers -> repositories -> Room cache -> ViewModels/Workers -> Compose UI.
- Settings (DataStore) -> ViewModels/Workers.
- Notification windows -> signal evaluation -> suppression/queue -> notification publish.

## Caching Summary
- Price series: intraday TTL 10 minutes, daily TTL 24 hours; intraday history retained 1 year.
- Market movers: TTL 10 minutes.
- Overview/fundamentals: TTL 24 hours.

## Open TODOs
- Stock detail follow action placeholder in `app/src/main/java/com/example/stocksignal/ui/stockdetail/StockDetailScreen.kt`.
- Intraday API upgrade/backfill note in `app/src/main/java/com/example/stocksignal/data/repository/StockRepository.kt`.

## Workflow
- Make sure the project builds and passes tests after each todo item is done.

## Commands
Build:
- `./gradlew assembleDebug`

Unit tests:
- `./gradlew testDebugUnitTest`

Instrumentation tests:
- `./gradlew connectedDebugAndroidTest`

Lint:
- `./gradlew lintDebug`

Run:
- Open Android Studio and run the `app` configuration.
