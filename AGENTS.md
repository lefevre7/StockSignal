# StockSignal Agent Notes

Purpose: Architecture and workflow reference for contributors.

## Documentation
- **Current Design (comprehensive):** `docs/CURRENT_DESIGN.md` — full architecture, 8 Mermaid diagrams (signal pipeline, scheduling, evaluation engine, AI scoring, data fetch, queuing state machine, user interaction, boot recovery), Room schema, settings reference, class index, test coverage, and delta from initial design.
- **Stooq pacing investigation + fix:** `docs/CURRENT_DESIGN.md` §14.4-14.5 — March 31, 2026 findings on background Stooq blocking, duplicated traffic sources, and the implemented queueing/blocking fix.
- **Initial Design Spec:** `docs/INITIAL_DESIGN.md` — original product requirements and UI/UX specifications.

## Architecture Overview
- UI: Jetpack Compose screens with ViewModels and Compose Navigation (single-activity).
- DI: Hilt modules provide repositories, DAOs, and network clients.
- Data: Room (v7, 9 entities) caches for market data + DataStore for settings + DataStore for diagnostics.
- Networking: Retrofit Stooq API + HTML/CSV/JS parsers + OkHttp interceptor chain (rate limiting, blocking, browser spoofing).
- Signals: 7-metric rule-based scoring engine (IndicatorCalculator → RuleBasedSignalModel → SignalEngine) with optional on-device AI enrichment (Gemma3-1B-IT via LiteRT).
- Charts: Vico (Compose) for sparkline and detail charts.
- Notifications: AlarmManager-scheduled windows → `NotificationAlarmReceiver` (cache-only pre-notify bookkeeping, blocked/weekend skips) → `WindowRunService` / WorkManager workers → `BackgroundStooqExecutionGate` FIFO → signal evaluation → `NotificationQueueProcessor` (caps, quiet hours, active-notification blocking) → `NotificationPublisher` → Android system notifications. Stale signals (>10 min data age) and cooldown duplicates (same ticker+tier within 24h) are suppressed.
- Premarket: 5-sample premarket quote pipeline (bid/ask data, 10-min intervals before market open).
- Compliance: Daily robots.txt check + request blocking (3-5s gaps, 5-timeout threshold on all Stooq endpoints, 24h blocks) plus blocked/weekend background skip policy. See `docs/CURRENT_DESIGN.md` §14.4-14.5 for the regression analysis and implemented fix.
- Diagnostics: 100+ counters tracking scheduling, API calls, AI generation, execution gate metrics.

## Data Flow
- Stooq API -> parsers -> repositories -> Room cache -> ViewModels/Workers -> Compose UI.
- Settings (DataStore) -> ViewModels/Workers.
- Notification windows -> signal evaluation -> suppression/queue -> notification publish.

## Caching Summary
- Price series: intraday TTL 10 minutes, daily TTL 24 hours; intraday history retained 1 year.
- Market movers: TTL 10 minutes.
- Overview/fundamentals: TTL 24 hours.

## Test Coverage Status (as of current session)

- **Unit tests:** 604 tests across 80 test files, **0 failures** — run with `./gradlew testDebugUnitTest`
- **Instrumented tests:** 18 androidTest files covering all navigable screens + Android-bound features
- **Bug fixed:** `SettingsRepository.toAppSettings()` — `selectedChartRange` now correctly defaults to holding-period-appropriate range (was always defaulting to `ONE_DAY`)
- **Key new test files added this session:**
  - `StockRepositoryTest` — cache hit/stale/forceRefresh, intraday vs. daily routing, accumulateIntradayData branches
  - `PremarketQuoteRunnerTest` — all 8+ skip paths + 10 outcome scenarios
  - `NotificationWindowRunnerTest` — 3 early-exit skip paths + market movers + allowAiGeneration flag
  - `NotificationAlarmReceiverTest` (instrumented) — null/unknown type, blank windowId, skip-reason path
  - `NotificationActionReceiverTest` (instrumented) — dismiss, add-to-watchlist (new + duplicate), blank ticker
  - Thin repository tests: `IntradayDataCacheRepositoryTest`, `MarketMoversCacheRepositoryTest`, `StockDetailCacheRepositoryTest`
  - + 11 other new unit test files from previous session (WatchlistRepository, SearchHistoryRepository, etc.)

**Known testing patterns:**
- Flow property initializers: Repositories that call `dao.observeXxx()` in property initializers require a `repoWith()/repoWithFlowError()` factory helper that creates fresh DAO+repo combos per test.
- Static object mocking: Use `mockkObject(PremarketWindowUtils)` for singleton objects.
- Robolectric + Log: Always `mockkStatic(Log::class)` in `@Before` / unmock in `@After` for tests touching code that calls `Log.*`.
- Log mock completeness: When mocking `Log`, ensure ALL used overloads are mocked (e.g., `Log.w(String, String)` AND `Log.w(String, String, Throwable)` are different overloads). Missing overloads cause silent failures with `mockkStatic`.

**Known issues fixed:**
- Pre-notify notification: Commit `0a0632b` deleted `WindowPreNotifyService` and removed the visible "window scheduled" notification. The `TYPE_PRE_NOTIFY` alarm handler was reduced to diagnostics-only logging. Fixed by posting a notification directly from `NotificationAlarmReceiver` (no foreground service needed) and auto-dismissing it when the `TYPE_WINDOW` alarm fires.
- Premarket retry amplification (commit `d115bfc`): The per-ticker retry logic (3 attempts) for transient network errors caused a 3x request amplification when Stooq was throttling (gzip truncation, EOF errors). Additionally, `UnknownHostException` was incorrectly classified as transient/retryable. Fix: (1) reduced retry count to 2 (1 retry max), (2) moved `UnknownHostException` to terminal failures (DNS failure = stop batch immediately), (3) added `BatchErrorTracker` inner class that stops any batch after 2 consecutive tickers fail with transient errors (indicating Stooq throttling, not random glitches), (4) applied tracker to all three batch methods (`getData`, `getIntradayData`, `getPremarketQuotes`) for consistent protection.
- Stooq throttling signals: gzip truncation (`IOException` with "gzip") and `EOFException` from Stooq are now recognized as server-side throttling when they occur on consecutive tickers. A single occurrence is retried once; a pattern across tickers stops the batch.
- Blocked-request pacing bypass onging issue: `StooqBlockInterceptor.interceptSerialized()` skipped `enforceMinGap()` and `reserveNextGap()` when `blocker.isBlocked()` was true, causing `wait=0ms hold=1ms` in gate diagnostics and no pacing during/after block periods. Fix: moved `enforceMinGap()` before the block check so all requests (blocked or not) sleep 3-5s, added `reserveNextGap()` on the blocked path before throwing `StooqBlockedException`, and removed the `requestAttempted` guard from the `finally` block so gap reservation is unconditional.
  - **WARNING — wrong approaches tried first (DO NOT REPEAT):** When diagnosing `wait=0ms hold=1ms` in gate diagnostics, the following "fixes" are ALL WRONG and must not be attempted:
    1. Do NOT reduce the timeout block duration from 24 hours — it is intentionally 24 hours.
    2. Do NOT reduce `connectTimeout` from 120s — it is intentionally 120s.
    3. Do NOT call `resetTimeoutStreak()` at the start of each notification window or premarket run — the streak must persist across runs.
    4. Do NOT reset `PremarketQuoteRunner` state at the start of each run.
    5. Do NOT add staleness decay to the timeout counter.
    6. Do NOT modify `NotificationWindowRunner`, `PremarketQuoteRunner`, or `StooqModule` for this issue.
  - **The actual root cause** is always in `StooqBlockInterceptor.interceptSerialized()` — the pacing (`enforceMinGap` / `reserveNextGap`) code path. When `wait=0ms hold=1ms`, it means pacing is being skipped on some code path in the interceptor, not that timeouts/blocks/streaks are misconfigured.


## TODO List - Feature Enhancements

**Instructions for AI Agent:**
- Search the codebase thoroughly before starting each item.
- Ask follow-up questions if implementation details are unclear.
- Run unit tests (`./gradlew testDebugUnitTest`) after completing each item.
- Ensure the project builds successfully (`./gradlew assembleDebug`) after each item.
- Each item should be atomic and completable in one session.

---

### - [x] 1. Add Loading Indicators for AI Score Generation
**Goal:** Display visual feedback when AI scores are being computed or queued across all relevant screens.

**Details:**
- Show different visual indicators: pulsing dot for queued state, circular progress spinner for active generation
- Implement in StockDetailScreen, WatchlistScreen item cards, and SignalsFeedScreen items
- Add loading states to relevant ViewModels (StockDetailViewModel, WatchlistViewModel, SignalsFeedViewModel)
- Update UI state data classes to include AI generation status (idle, queued, generating, complete, error)
- Use Material3 CircularProgressIndicator for spinning animation
- Position indicators next to AI score/confidence labels

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/stockdetail/StockDetailScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/stockdetail/StockDetailViewModel.kt`
- `app/src/main/java/com/example/stocksignal/ui/watchlist/WatchlistScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/watchlist/WatchlistViewModel.kt`
- `app/src/main/java/com/example/stocksignal/ui/signals/SignalsFeedScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/signals/SignalsFeedViewModel.kt`

**Unit Tests:**
- Test ViewModel state transitions: idle → queued → generating → complete
- Test ViewModel state transitions: idle → queued → generating → error
- Verify UI displays correct indicator based on state

**UI Tests:**
- Verify spinner appears when AI score is generating
- Verify pulsing indicator appears when queued
- Verify indicators disappear when complete

---

### - [x] 2. Implement LLM Download Onboarding Flow
**Goal:** Guide users through downloading Gemma3-1B-IT model on first launch.

**Details:**
- Create onboarding screen that explains LLM benefits (offline AI scoring, privacy)
- Show download size (584MB) and storage requirements
- Implement progress tracking during download using existing model download infrastructure
- Add download state to OnboardingViewModel (not_started, downloading, complete, error)
- Store onboarding completion flag in DataStore (settings.onboardingCompleted)
- Download target: gemma3-1b-it-int4.litertlm from appropriate source
- Handle download failures with retry option
- Skip onboarding if model already exists

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/onboarding/OnboardingRoute.kt`
- `app/src/main/java/com/example/stocksignal/ui/onboarding/OnboardingViewModel.kt`
- `app/src/main/java/com/example/stocksignal/data/settings/SettingsRepository.kt`
- `app/src/main/java/com/example/stocksignal/data/translation/NewsTranslationService.kt` (if needed for model download)

**Unit Tests:**
- Test onboarding completion flag persistence
- Test download progress tracking
- Test error handling and retry logic
- Verify onboarding skipped if model exists

**UI Tests:**
- Verify onboarding shows on first launch
- Verify download progress updates correctly
- Verify navigation proceeds after successful download

---

### - [x] 3. Add LLM Re-download/Update Setting
**Goal:** Allow users to re-download or update the current LLM model from settings.

**Details:**
- Add "Re-download LLM Model" option in SettingsScreen
- Show current model name and file size
- Display confirmation dialog before re-download
- Reuse download progress UI from onboarding
- Show success/failure toast messages
- Update model file info after successful download
- Handle case where download fails mid-way

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/settings/SettingsViewModel.kt`

**Unit Tests:**
- Test model file verification
- Test re-download triggers correctly
- Test failure handling

**UI Tests:**
- Verify confirmation dialog appears
- Verify progress updates during re-download
- Verify success toast appears

---

### - [x] 4. Add Notification Permission Setting
**Goal:** Add setting to request/manage notification permissions.

**Details:**
- Add "Enable Notifications" toggle in SettingsScreen under Notifications section
- Check current notification permission status using ActivityCompat.checkSelfPermission
- When toggled ON: request notification permission using ActivityResultContracts.RequestPermission
- When toggled OFF: direct user to app settings to disable (show dialog with instructions)
- Store permission request state in settings to avoid repeated prompts
- Update toggle state based on actual permission status on screen resume
- Handle Android 13+ (TIRAMISU) POST_NOTIFICATIONS permission specifically
- Show explanatory text about why notifications are useful

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/example/stocksignal/data/settings/AppSettings.kt` (add notificationPermissionRequested field)
- `app/src/main/java/com/example/stocksignal/data/settings/SettingsRepository.kt`

**Unit Tests:**
- Test permission state tracking
- Test settings persistence

**UI Tests:**
- Verify permission request dialog appears
- Verify toggle reflects actual permission state
- Verify instructions dialog for disabling

---

### - [x] 5. Add Holding Period to AI Scoring Prompt
**Goal:** Include user's holding period preference in AI signal scoring prompt for better context.

**Details:**
- Modify buildPrompt function in AiSignalScorer to include holding period
- Add prompt section explaining user's investment timeframe (HOURS, DAYS, WEEKS, MONTHS, YEARS)
- Format: "User's holding period: MONTHS (typically holding positions for several months)"
- Include context about how this affects signal interpretation
- Holding period already available via settingsRepository.settingsFlow

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/data/ai/AiSignalScorer.kt` (buildPrompt function)

**Unit Tests:**
- Test prompt includes holding period for each enum value
- Test prompt formatting is correct
- Verify existing AI scoring tests still pass

---

### - [x] 6. Enhance Search Button Visibility
**Goal:** Make the search button more prominent and discoverable.

**Details:**
- Increase search button/icon size in SearchBar composable
- Apply accent color (MaterialTheme.colorScheme.primary) to search icon
- Add subtle elevation or shadow effect
- Ensure button meets minimum touch target size (48dp)
- Consider adding a "Search" text label alongside icon
- Maintain accessibility (content description)

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/search/SearchScreen.kt`

**Unit Tests:**
- Test accessibility properties are maintained

**UI Tests:**
- Verify button is easily discoverable
- Verify touch target size meets Android guidelines
- Test search functionality still works

---

### - [x] 7. Add Info Pop-ups for Technical Metrics
**Goal:** Provide educational tooltips explaining each technical indicator/metric.

**Details:**
- Create InfoIconButton composable that shows dialog on click
- Add info icons next to each metric label in MetricsTab (StockDetailScreen)
- Create dialog content explaining: RSI 14, MACD, Signal, Histogram, SMA 50, SMA 200, ATR 14, Volume Z-Score, Return Z-Score, Bollinger Bands
- Include: what the metric measures, typical ranges, how to interpret values, trading implications
- Use Material3 AlertDialog with scrollable content
- Store explanations in strings.xml or separate constants file for easy maintenance

**Metrics to explain:**
- RSI 14: Relative Strength Index (0-100, >70 overbought, <30 oversold)
- MACD: Moving Average Convergence Divergence (trend and momentum)
- MACD Signal: Signal line for MACD
- MACD Histogram: Difference between MACD and Signal
- SMA 50: 50-period Simple Moving Average (medium-term trend)
- SMA 200: 200-period Simple Moving Average (long-term trend)
- ATR 14: Average True Range (volatility measure)
- Volume Z-Score: How unusual current volume is
- Return Z-Score: How unusual current returns are
- Bollinger Bands: Volatility bands around price

**Files to create:**
- `app/src/main/java/com/example/stocksignal/ui/components/InfoIconButton.kt`
- `app/src/main/java/com/example/stocksignal/ui/components/MetricExplanations.kt`

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/stockdetail/StockDetailScreen.kt` (add info icons to each metric)

**Unit Tests:**
- Test all metric explanations are non-empty
- Test dialog shows correct explanation for each metric

**UI Tests:**
- Verify info icon appears next to each metric
- Verify clicking icon shows explanation dialog
- Verify dialog is scrollable if content is long

---

### - [x] 8. Add Info Pop-ups for Signal Tiers and Scores
**Goal:** Explain what each signal tier means and how scores are calculated.

**Details:**
- Add info icons next to signal tier labels (Strong Buy, Buy, Hold, Sell, Strong Sell)
- Add info icon next to overall score display
- Create explanations for:
  - Strong Buy (60-100): High confidence buy signal, multiple positive indicators
  - Buy (30-59): Moderate buy signal, some positive indicators
  - Hold (-29-29): Neutral signal, mixed indicators or low conviction
  - Sell (-59--30): Moderate sell signal, some negative indicators
  - Strong Sell (-100--60): High confidence sell signal, multiple negative indicators
  - Score calculation: Aggregate of technical indicators weighted by confidence
  - Confidence: Measure of indicator agreement and volatility
- Include info about AI vs rule-based scoring
- Show in StockDetailScreen and SignalsFeedScreen

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/components/SignalChip.kt` (add info icon)
- `app/src/main/java/com/example/stocksignal/ui/components/SignalScoreRow.kt` (add info icon)
- `app/src/main/java/com/example/stocksignal/ui/components/MetricExplanations.kt` (add tier explanations)

**Unit Tests:**
- Test explanation content is comprehensive
- Test tier ranges are correctly documented

**UI Tests:**
- Verify info icons appear next to tier chips
- Verify clicking shows correct explanation
- Verify score explanation is clear

---

### - [x] 9. Fix Light Mode Tile Overlay Text Contrast
**Goal:** Improve text readability on tile overlays in light mode by adding semi-transparent dark backgrounds.

**Details:**
- Identify all tile overlay text (ticker symbols, market names, other text overlaid on the tiles in top-left of item cards)
- Add semi-transparent dark background (Color.Black.copy(alpha = 0.5f)) behind overlay text
- Apply to WatchlistScreen item cards, MarketMoversScreen cards, and SearchScreen results
- Use Box with background modifier or Surface with appropriate color
- Ensure padding around text for better visual appearance
- Keep text color white for all themes (as requested)
- Test in both light and dark modes to ensure contrast is good in both

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/watchlist/WatchlistScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/marketmovers/MarketMoversScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/search/SearchScreen.kt`

**Unit Tests:**
- Test text color remains white in both themes
- Test background alpha is correct

**UI Tests:**
- Verify text is readable in light mode
- Verify text is readable in dark mode
- Verify semi-transparent background doesn't obscure too much content

---

### - [x] 10. Remove Campaign ID from Search API Calls if failure case
**Goal:** Simplify search API calls by removing campaign ID parameter.

**Details:**
- Modify StooqSearchRepository to call getCmp with empty campaign ID if failing to get the id
- Update error handling to remove campaign ID failure cases

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/data/stooq/repository/StooqSearchRepository.kt`

**UI Tests:**
- Verify search functionality works end-to-end
- Verify search results are displayed correctly

---

### - [x] 11. Implement Swipe-to-Dismiss for Signals
**Goal:** Allow users to dismiss signals with swipe gesture and undo option.

**Details:**
- Wrap signal items in SwipeToDismiss composable (Material3)
- Add "dismissed" boolean field to GlobalSignalEventEntity
- Update SignalEventDao with dismissSignal and undoDismissSignal functions
- Filter out dismissed signals in SignalsRepository.observeEvents query
- Show Snackbar with "Undo" action after swipe dismiss (5 second duration)
- If undo not clicked, permanently mark as dismissed in database
- Dismissed signals kept in DB but hidden from UI (as requested)
- Add background reveal during swipe showing delete icon
- Test swipe gesture on different screen sizes

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/ui/signals/SignalsFeedScreen.kt`
- `app/src/main/java/com/example/stocksignal/ui/signals/SignalsFeedViewModel.kt`
- `app/src/main/java/com/example/stocksignal/data/local/entity/GlobalSignalEventEntity.kt`
- `app/src/main/java/com/example/stocksignal/data/local/dao/SignalEventDao.kt`
- `app/src/main/java/com/example/stocksignal/data/repository/SignalsRepository.kt`
- `app/src/main/java/com/example/stocksignal/data/local/db/StockSignalDatabase.kt` (migration)

**Unit Tests:**
- Test signal dismissal persists to database
- Test undo restores signal visibility
- Test dismissed signals are filtered from query
- Test database migration for new dismissed field

**UI Tests:**
- Verify swipe gesture dismisses signal
- Verify undo action restores signal
- Verify dismissed signal disappears after timeout
- Test swipe in both directions

---

### - [x] 12. Rename and Enhance Rolling Return Z-Score
**Goal:** Rename `returnZScore` to `rollingReturnZScore`, adapt to holding period, and improve documentation.

**Details:**
- Rename function from `returnZScore` to `rollingReturnZScore` in IndicatorCalculator
- Update window parameter to adapt based on holding period (like other indicators):
  - HOURS: window = 10
  - DAYS: window = 15  
  - WEEKS: window = 20
  - MONTHS: window = 30
  - YEARS: window = 50
- Update all call sites to use new function name
- Add comprehensive KDoc explaining rolling window calculation
- Update IndicatorMetric enum: RETURN_ZSCORE_20 → ROLLING_RETURN_ZSCORE
- Update all references in SignalModels, IndicatorAlertEvaluator, and AiSignalScorer
- Add holding period parameter to indicator config and pass through

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/domain/signal/IndicatorCalculator.kt`
- `app/src/main/java/com/example/stocksignal/domain/signal/IndicatorConfig.kt` (add rollingReturnZScoreWindow)
- `app/src/main/java/com/example/stocksignal/domain/signal/SignalModels.kt`
- `app/src/main/java/com/example/stocksignal/domain/signal/IndicatorAlertEvaluator.kt`
- `app/src/main/java/com/example/stocksignal/data/ai/AiSignalScorer.kt`
- `app/src/main/java/com/example/stocksignal/domain/model/IndicatorAlert.kt`

**Unit Tests:**
- Test function rename doesn't break existing tests
- Test rolling window adapts correctly to each holding period
- Update all existing returnZScore tests to use new name
- Add tests for each holding period window size
- Verify calculation correctness for different windows

---

### - [x] 13. Display Price and Percent Change in Search Results
**Goal:** Show current price and percent change next to each stock in search results.

**Details:**
- Parse price (432.7500) and percent change (0.43%) from search API response
- Update SearchResult data class to include price and percentChange fields
- Modify CmpParser to extract these values from API response
- Display in SearchScreen results list: "TSLA.US - $432.75 (+0.43%)"
- Use green color for positive changes, red for negative
- Handle cases where price/percent data is missing (show ticker only)
- Format price with appropriate decimal places based on value
- Format percent with + or - sign

**API Response Example:**
```
TSLA.US~Tesla Inc~XNAS~432.7500~0.43%~4
```
Fields: ticker~name~exchange~price~percentChange~other

**Files to modify:**
- `app/src/main/java/com/example/stocksignal/data/stooq/model/SearchResult.kt`
- `app/src/main/java/com/example/stocksignal/data/stooq/parser/CmpParser.kt`
- `app/src/main/java/com/example/stocksignal/ui/search/SearchScreen.kt`

**Unit Tests:**
- Test CmpParser extracts price correctly
- Test CmpParser extracts percent change correctly
- Test CmpParser handles missing data gracefully
- Test price formatting for different magnitudes
- Test percent formatting with sign

**UI Tests:**
- Verify price displays in search results
- Verify percent change shows with correct color
- Verify formatting is consistent

---

### - [x] 14. Update App Icon with SS.jpeg
**Goal:** Replace current app icon with SS.jpeg using black background.

**Details:**
- Locate SS.jpeg in Downloads folder
- Generate all required mipmap density icons (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Create adaptive icon with:
  - Foreground: SS.jpeg content
  - Background: Solid black (#000000)
- Generate both regular (ic_launcher) and round (ic_launcher_round) variants
- Update mipmap resources in appropriate density folders
- Maintain vector drawable format where possible for scalability
- Ensure icon meets Android design guidelines (safe zones, size)
- Update monochrome variant for themed icons (Android 13+)

**Files to modify/create:**
- `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.png` (and other densities)
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml` (update background color)
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` (update background color)
- `app/src/main/res/drawable/ic_launcher_background.xml` (set to black)

**Process:**
1. Load SS.jpeg and validate format
2. Use Android Asset Studio or similar tool to generate icon set
3. Set background to #000000 (black)
4. Ensure foreground safe zone compliance
5. Generate all density variants
6. Test icon appearance on different launchers

**UI Tests:**
- Verify icon displays correctly on home screen
- Verify adaptive icon works on Android 8+
- Verify icon appears in app switcher
- Test on both light and dark launcher themes

---

### - [x] 15. Run Full Test Suite and Fix Any Failures
**Goal:** Ensure all changes integrate correctly and existing functionality is preserved.

**Details:**
- Run complete unit test suite: `./gradlew testDebugUnitTest`
- Run instrumentation tests: `./gradlew connectedDebugAndroidTest`
- Fix any test failures introduced by changes
- Update test mocks/stubs as needed for new functionality
- Ensure code coverage doesn't decrease significantly
- Run lint: `./gradlew lintDebug` and fix any critical issues
- Build release variant: `./gradlew assembleRelease`
- Manually test critical user flows on device/emulator

**Test Areas:**
- AI score generation with new holding period context
- Signal dismissal and undo
- Rolling return z-score calculation
- LLM download flow
- Notification permission flow
- All info dialogs display correctly
- Light/dark mode contrast on tiles

---

## Original Open TODOs
- Stock detail follow action placeholder in `app/src/main/java/com/example/stocksignal/ui/stockdetail/StockDetailScreen.kt`.
- Intraday API upgrade/backfill note in `app/src/main/java/com/example/stocksignal/data/repository/StockRepository.kt`.

## Key Class Reference (Quick Lookup)

### Notification Pipeline
- `NotificationScheduler` — AlarmManager alarm orchestration based on settings/frequency.
- `NotificationAlarmReceiver` → `WindowRunService` → `NotificationWindowRunner` — alarm → receiver-side skip/pre-notify handling → foreground service → signal evaluation.
- `BackgroundStooqExecutionGate` / `BackgroundStooqRunPolicy` — FIFO serialization and skip policy for background Stooq work.
- `NotificationQueueProcessor` — caps, quiet hours, active-notification blocking, queue management.
- `NotificationPublisher` — renders and posts Android system notifications.
- `NotificationActionReceiver` — handles dismiss + add-to-watchlist user actions.
- `NotificationBootReceiver` / `NotificationBootstrapWorker` / `NotificationReconcileWorker` — boot recovery + periodic state cleanup.

### Signal Evaluation
- `IndicatorCalculator` — RSI, MACD, SMA, Bollinger, ATR, Z-scores.
- `IndicatorConfig` — adaptive indicator windows per HoldingPeriod.
- `RuleBasedSignalModel` (SignalModels.kt) — 7-metric composite scoring.
- `SignalEngine` — orchestrates scoring + confidence calculation.
- `IndicatorAlertEvaluator` — 8-metric threshold crossover detection.
- `AiSignalScorer` — Gemma3-1B-IT prompt building, caching, response parsing.

### Data
- `StockRepository` — data fetch, caching, intraday accumulation, premarket candles.
- `SignalsRepository` — signal computation, AI integration, cooldown, event management.
- `StooqRepository` / `StooqSearchRepository` / `MarketMoversRepository` — Stooq API data fetching.
- `StooqBlockInterceptor` / `StooqRequestBlocker` — rate limiting and 24h blocking.
- `StooqExecutionGate` — mutex serializing Stooq HTTP.
- `ExternalExecutionGate` — mutex serializing on-device LLM inference.
- `SettingsRepository` — DataStore preferences (holdingPeriod, frequency, sensitivity, etc.).

### Room Database (v7)
- 9 entities: WatchlistItem, GlobalSignalEvent, IntradayDataCache, StockDetailCache, StockOverviewCache, MarketMoversCache, NotificationState, Note, SearchHistory.
- Migrations 1→7 documented in `docs/CURRENT_DESIGN.md` §12.2.

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
