# StockSignal Implementation Plan

Purpose: Full-spec build plan with ordered steps and decisions locked in.

## Locked Decisions
- UI: Jetpack Compose, single-activity, Compose Navigation, Material3.
- DI: Hilt (replace Koin).
- Storage: Room for data, DataStore for settings.
- Charts: Vico (Compose).
- Notifications: Scheduled windows only (no immediate posts); immediate posts UI is disabled with "coming soon".
- Market Movers: Stooq market-movers parsing, range tabs 1D/5D/1M/6M/1Y/5Y, follows current watchlist range.
- Search: Stooq cmp endpoint; symbols already include .US and are stored as canonical keys.
- Signals: Multi-model scoring (rule-based + statistical), show average and mode (rounded to nearest 5). If no mode, use average.
- Schedule defaults: 10 minutes before US market open (9:20 ET), 11:00, 14:00 local; editable.
- Suppression: One active notification blocks all new posts; queued events are delivered at the next scheduled window.
- Duplicate cooldown: 24 hours. Stale data threshold: 10 minutes.
- Deep link: stocksignal://stock/{TICKER}.
- Tests: unit + Robolectric + instrumentation.

Make sure the project builds and passes tests after each todo item is done. 

## TODO
[x] 1. Project structure and dependencies
    - Add Compose + Material3 + Navigation Compose.
    - Add Hilt (plugins, annotations, kapt/ksp).
    - Add Room + DataStore + WorkManager + Notification compat.
    - Add Vico chart library.
    - Remove Koin wiring and example activity usage.

[x] 2. App entry + navigation
    - Create Compose MainActivity as launcher.
    - Remove fragment navigation template (FirstFragment/SecondFragment).
    - Add bottom nav with Watchlist, Market Movers, Signals, Notes/Portfolio, Settings.
    - Add onboarding flow for permissions + disclaimers.

[x] 3. Theme + design system
    - Define color tokens (dark-first) and typography.
    - Build reusable components: signal badge, chips, cards, chart frame.
    - Add accessibility labels and dynamic type support.

[x] 4. Data models (domain)
    - Define core models: WatchlistItem, StockDetail, SignalResult, SignalReason.
    - Define signal enums and score mapping (-100..100 to label/color).
    - Define notification event model + payload.

[x] 5. Room schema
    - Entities: WatchlistItem, GlobalSignalEvent, MarketMoversCache, StockDetailCache,
      NotificationState, Notes, SearchHistory.
    - TypeConverters for LocalDate/LocalDateTime and lists.
    - DAO interfaces and database setup.

[x] 6. DataStore settings
    - Notification settings (frequency options, schedule windows, quiet hours).
    - Signal sensitivity sliders and per-stock overrides.
    - Flags for market movers, digests, and "immediate posts (coming soon)".

[x] 7. Stooq networking + parsers
    - Parse cmp endpoint into search results (symbol, name, exchange).
    - Parse market-movers HTML into increasers/decreasers list for all ranges.
    - Cache market movers with timestamp; enforce stale threshold.

[x] 8. Repository layer
    - StockRepository: fetch daily/intraday, cache by range with TTL (intraday 10m, daily 24h).
    - SearchRepository: cmp + recent searches storage.
    - MarketMoversRepository: fetch + cache + parse.
    - SignalsRepository: compute + store signal results, reasons, and history.

[x] 9. Indicators and signal models
    - Implement indicators with defaults:
      - SMA 50/200 (daily); SMA 5/20 (intraday 1D) and 20/50 (intraday 5D/1M).
      - RSI 14.
      - MACD 12/26/9.
      - Bollinger Bands 20, 2.0 std.
      - Volume z-score (20-day window, z >= 2.0).
      - ATR 14 (volatility normalization).
      - Breakout: close > 20-day high with volume confirmation.
      - Rolling return z-score (20-day window).
    - Model A: rule-based score from indicator triggers.
    - Model B: statistical z-score model on returns.
    - Normalize each model to -100..100.
    - Aggregate: average score + mode (scores rounded to nearest 5). If no mode, use average.
    - Reasons: top 3 contributors by absolute impact.

[x] 10. Confidence metric
    - Use agreement ratio across models + magnitude + volatility penalty.
    - Default formula: confidence = clamp( (0.5 * abs(avg)/100) + (0.4 * agreement) + (0.1 * (1 - volNorm)) ) * 100.

[x] 11. Signal generation flow
    - Compute signals on each data fetch and store in Room.
    - Enforce 24h cooldown per ticker per label.
    - Ensure signals use intraday data for 1D/5D/1M; daily data for 6M/1Y/5Y.

[x] 12. UI: Watchlist
    - Watchlist cards: price, change, sparkline, signal badge, last notified indicator.
    - Sort modes + drag-and-drop + tags/folders.
    - Quick actions: note, alert, remove.

[x] 13. UI: Market Movers
    - Tabs for 1D/5D/1M/6M/1Y/5Y (follow current range selection).
    - Rows show price, change, sparkline, rank, signal badge.
    - Actions: add to watchlist, set alert, view details.

[x] 14. UI: Search
    - Instant suggestions from cmp endpoint.
    - Badges for watchlist and market movers.
    - Quick add + per-stock notification toggle.
    - Recent searches list.

[x] 15. UI: Stock Detail
    - Chart with range tabs and overlays (SMA/volume).
    - Signal badge with score, average, mode, confidence.
    - Reasons list with expanders and plain-language explanations.
    - Tabs: Overview, Metrics, News (placeholder), Signals, History.
    - CTA row: Set Alert, Add Note, Share.

[x] 16. UI: Signals feed
    - Reverse-chron list of signal events (watchlist + market movers).
    - Each entry shows ticker, label, avg/mode, timestamp, reasons.

[x] 17. UI: Notes/Portfolio
    - Notes-only MVP; store one note per ticker.

[x] 18. UI: Settings
    - Notification frequency (3x/day, 1x/day, 1x/week, only-when-open).
    - Schedule window editor (default 9:20 ET, 11:00, 14:00 local).
    - Quiet hours toggle (off by default).
    - Types: watchlist, market movers, digests.
    - Signal sensitivity sliders.
    - "Immediate posts (coming soon)" disabled toggle.
    - Disclaimer text + Stooq attribution.

[x] 19. Notification scheduling
    - WorkManager to schedule window-based fetch + notify jobs.
    - No immediate posts; batch all eligible events at each window.
    - Only-when-open disables background work.

[x] 20. Notification suppression + queue
    - Persist NotificationState: lastActiveId, dismissed flag, queued events.
    - deleteIntent receiver updates dismissal and clears active.
    - If active not dismissed, enqueue events for next window.
    - Group notifications by day (single digest per window) and attach action buttons.

[x] 21. Deep links
    - Handle stocksignal://stock/{ticker} in navigation.
    - Highlight the relevant signal event on open.

[x] 22. Edge cases
    - Stale data > 10 min: do not notify.
    - On reboot: reconcile active notifications with system query and TTL.
    - Offline fallback: show cached market movers with freshness label.

[x] 23. Testing
    - Unit tests for indicators, scoring, aggregation, confidence.
    - Unit tests for notification suppression/queue/cooldown.
    - Robolectric tests for notification deleteIntent flow.
    - Instrumentation tests for deep link navigation and WorkManager scheduling.

[x] 24. Cleanup and docs
    - Remove sample fragments and example activity from manifest.
    - Update README with setup and disclaimer.
    - Add in-app attribution: "Powered by Stooq.com data" and "Not affiliated with Stooq".
