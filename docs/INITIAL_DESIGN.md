# 1 — Quick summary of the product & goals
 
An Android app that:
 
* Lets users search stocks and add them to a watchlist.
* Shows a rich single-stock view with time-range tabs (1D, 5D, 1M, 6M, 1Y, 5Y).
* Displays a clear five-level *signal* (Really Good Buy → Really Good Sell) with numeric score and plain-language explanations.
* Sends local notifications for (A) signals related to the user’s watchlist and (B) *market movers / largest increasers and decreasers* even if those stocks are **not** watched.
* Notifications are generated locally by the app (the app fetches data and triggers local notifications).
* Is well designed, mobile-friendly and trustworthy — informational only, not executing trades.
 
Notification frequency options: 3×/day, 1×/day, 1×/week, or only when the user opens the app.
 
Notification dismissal policy: no new notification is posted if a previous notification is still present; once the user explicitly dismisses/swipes the previous notification (or takes its action), new notifications are allowed again (subject to frequency caps).
 
---
 
# 2 — Core user journeys (high-level)
 
1. Launch → Onboarding (tour) + permissions → Home (watchlist).
2. Search stocks → Add to watchlist → Optionally configure per-stock alerts.
3. Browse Market Movers (Largest Increasers / Decreasers) → optionally add to watchlist.
4. Tap a stock or recieve local notification and tap → Stock Detail (chart, signal, explanations).
5. If notification is present and user hasn’t dismissed it, app suppresses additional notifications until dismissal; otherwise future notifications follow frequency caps.
6. Settings: notification frequency (every 30 min / 3×/day / 1×/day / 1×/week / only on open), quiet hours (default is off), signal sensitivity sliders, privacy, disclaimers.
 
---
 
# 3 — Navigation & screen map (updated)
 
Bottom nav:
 
* Watchlist (default/home)
* Market Movers
* Signals (feed of recent signals for watchlist + global movers)
* Notes / Portfolio
* Settings (notification controls live in Settings + per-stock action in watchlist/stock detail.)
 
Key screens:
 
* Onboarding / Permissions (notification & background fetch permission) / Tutorials
* Watchlist (list, card view, sort/filter)
* Market Movers (Largest Increasers / Decreasers)
* Search (instant results)
* Stock Detail (chart & tabs)
* Signals feed (events)
* Alert settings
 
---
 
# 4 — Watchlist screen (Home) — behavior & UI
Purpose: quick digest. Let users act fast.
Components:
Top bar: app logo + quick search icon + profile/avatar
Summary header: “Active signals: 12 | New buys today: 4” (tap to go to Signals feed)
Watchlist cards (one per stock) — card shows:
Ticker + company name + exchange
Current price + % change (1D)
Mini sparkline (1D)
Signal badge (label + color + numeric score)
Quick action buttons: Add note, Set alert, Remove
Tap card => Stock Detail
Watchlist interactions:
Sort modes: By signal (strongest buy first / strongest sell first), Alphabetical, Price change, Custom
Reorder by drag-and-drop
Grouping: Tags/folders (e.g., “Tech”, “Dividend”)
Microcopy example on empty watchlist:
“Your watchlist is empty — search a ticker to get started. We’ll send signals for stocks you add.”
 
* Small indicator on each card: “Last notified” + whether that notification is still active (if applicable). Quick action: “Snooze notifications” or “Mute Market Movers for this ticker.”
*
 
---
 
# 5 — Market Movers
 
Purpose: surface biggest daily/period movers and the ones that meet “really good buy/sell” criteria.
 
Components:
 
* Two tabs: Largest Increasers / Largest Decreasers; rows show % change for the selected range and a signal badge if it meets thresholds. Actions: Add to Watchlist, Set Alert, View Details.
* Each row: ticker, company name, % change (selected range), price, simple sparkline, movement rank, signal badge if it meets a signal level (e.g., Strong Buy / Strong Sell)
 
Behavior:
 
* The Market Movers screen is populated by your API endpoints (e.g., /market-movers)
 
---
 
# 6 — Search / Discover flow (minor update)
 
* Search bar with instant suggestions (symbol, name, exchange)
* Search results show whether a ticker is appearing in Market Movers and whether it’s in your watchlist. Quick add, per-stock notification toggle on add.
* Results show whether a stock is in Market Movers (badge) even if it’s not in watchlist.
* Search suggestions include “Top Increasers” quick filter.
*Quick add: “Add to Watchlist” button on search result
*Show key stats inline: price, % change, market cap (small)
* Show recent searches
 
 
---
 
# 7 — Stock Detail
 
Layout (vertical, scrollable):
Sticky top area (ticker row): ticker, name, exchange, watchlist toggle (star), follow button, more (three-dot) menu
Price & signal row: large price, % change, last updated timestamp. Signal badge and numeric signal score displayed prominently (see mapping below).
Chart area (big, interactive) with time-range tabs under it: 1D (intraday), 5D, 1M, 6M, 1Y, 5Y
Chart features: pinch-to-zoom, crosshair with exact price/time, overlay moving averages (selectable), volume bars toggle
use getData() in StooqRepository.kt to get daily data for 6M - 5Y before the current date, and use getIntradayData() to get intraday data for the current day to 1M before the current date. 
Tabs below chart: Overview | Metrics | News | Signals | History
Overview: company summary, market cap, P/E, dividend yield, 52-week high/low
Metrics: technical indicators (RSI, MACD, SMA50, SMA200, ATR) + trend arrows
News: linked headlines fetched from your APIs (optional)
Signals: explanation of the current signal + contributing factors
History: timeline of previous signals with timestamps (tap to view details)
CTA row (sticky): “Set Alert” / “Add Note” / “Share”
Signal badge design:
Icon + one-line summary + confidence %
Tooltip / expandable: “Why this signal? (reasons from API)”
Make the chart and the signal the visual focus — users should know at a glance the app’s read on the stock.
 
 
---
 
# 8 — Signal mapping & UX-friendly scoring
 
Use a normalized `signalScore` value from -100 → +100. Visual mapping:
 
* `>= 60` → **Really good time to buy** — label: Strong Buy — color: deep green
* `30–59` → **Ok time to buy** — label: Buy — color: green
* `-29–29` → **Neutral** — label: Hold/Neutral — color: gray
* `-59–-30` → **Ok time to sell** — label: Sell — color: amber
* `<= -60` → **Really good time to sell** — label: Strong Sell — color: red
 
Show both the label and the numeric score (e.g., “Buy — score 45 / 100 — confidence 78%”). Confidence is a secondary metric derived from the algorithm’s internal reliability metric; show it as a small progress circle.
Explainability:
Show top 3 reasons the algorithm flagged the signal (e.g., “50-day MA crossed above 200-day MA”, “RSI 14 at 28 (oversold)”, “Volume spike +68%”).
Allow the user to tap any reason to read a plain-language explanation.
Visual treatment:
Large, circular badge with the label, small numeric score under it
Color-coded chip next to price
In the detail page, animate the badge when signal changes
 
---
 
# 9 — Notifications: local-generation model, types, and updated rules
 
Notification types:
 
1. **Immediate Signal (Local)** — when a watched stock’s signal crosses user threshold.
2. **Market Movers Alert (Local)** — when the app detects a stock in the Market Movers list that reaches a “Strong Buy / Strong Sell” threshold (even if not watched).
3. **User-threshold / Price Alerts (Local)** — user-defined.
 
Frequency options (user selectable, global + per-stock override):
 
* **Up to 3×/day** — the app will deliver at most three immediate notifications (batches allowed).
* **1×/day**
* **1×/week**
* **Only when user opens the app** — no background notifications; events remain in in-app signals feed.
 
Rules & throttling:
 
* “Up to 3×/day” means immediate events can occur but the app will enforce a cap of 3 notifications/day. If more than 3 interesting events occur, the app groups them into a digest at the next scheduled slot.
* Always respect **quiet hours** (user-configurable) — no immediate notifications during quiet hours; schedule a digest instead.
* **Market Movers**: If the app detects an un-watched stock hitting `>=60` or `<= -60`, it may generate a Market Movers notification subject to frequency/throttling settings. If the user has “Only when open,” the event joins the in-app feed instead of firing a notification.
 
Notification payload (local notification example JSON stored in app for UI/deeplink):
 
```json
{
"type": "market_mover",
"ticker": "TSLA",
"company": "Tesla, Inc.",
"signal": "Strong Buy",
"score": 72,
"confidence": 81,
"price": 325.42,
"percentChange": 14.3,
"time": "2025-12-31T14:05:00Z",
"deep_link": "myapp://stock/TSLA",
"source": "local"
}
```
 
Grouping and UX:
 
* Android notification bundling: group notifications by day and by type (Market Movers vs Watchlist signals).
* Provide action buttons: “View”, “Dissmiss”, “Add to Watchlist” (for market mover notifications).
 
In-app Signals feed:
Reverse-chronological list of signal events for user’s watchlist
Each event: ticker, label, score, small chart thumbnail, timestamp, reason bullets
Tap -> stock detail to view context
---
 
# 10 — Alert settings UX
 
Global settings:
 
* Notification frequency: every 30 minutes | 3×/day | 1×/day | 1×/week | only when app open
* Quiet hours: start / end
* Types to receive: Watchlist signals | Market Movers | Digests
* Max notifications per day: slider or preset
 
Per-stock override:
 
* Enable/disable immediate signals, per-stock threshold, snooze until time
* “Always notify” and “Ignore market mover notifications for this ticker” toggles
 
Default sensible choices:
 
* Default frequency: 3×/day (balanced)
* Default immediate threshold: `>=60` or `<= -60`
 
---
 
# 11 — Data model (additions for Market Movers & local notifications)
 
WatchlistItem 
json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "exchange": "NASDAQ",
  "addedAt": "2025-12-30T14:00:00Z",
  "alertSettings": {
    "enabled": true,
    "minScoreForNotify": 60,
    "quietHours": {"start": "22:00", "end": "07:00"},
    "snoozedUntil": null
  },
  "lastSignal": {
    "score": 65,
    "label": "Strong Buy",
    "confidence": 78,
    "time": "2025-12-30T14:28:00Z"
  },
  "notes": "Watch before earnings"
}

 
GlobalSignalEvent (local cache)
 
```json
{
"id": "evt_20251231_0001",
"type": "market_mover" | "watchlist_signal" | "digest",
"ticker": "TSLA",
"score": 72,
"label": "Strong Buy",
"confidence": 81,
"percentChange": 14.3,
"price": 325.42,
"generatedAt": "2025-12-31T14:05:00Z",
"notifiedAt": "2025-12-31T14:06:30Z",
"source": "local",
"delivered": true,
"deep_link": "myapp://stock/TSLA"
}
```
 
MarketMoversCache
 
* cached list of top increasers/decreasers per range (with timestamp) to avoid repeated API hits.
 
LocalScheduler state:
 
* lastFetchAt, lastNotifyTimes per ticker, notificationCountsForDay (to enforce caps), queuedEvents (events suppressed while a notification is active)
 
StockDetail (cached):
series data for chart per range,
latest price,
indicators: RSI, SMA, MACD etc (as supplied by APIs),
signal history list (timestamped)
 
---
 
# 12 — UI / Visual design system (catchy & modern)
Goal: confident, minimal, slightly bold. Visual cues emphasize signals.
Palette (example):
Deep Space (background): #0F1724 (very dark navy)
Accent Green (strong buy): #00C853
Mid Green (buy): #46D07D
Neutral Gray: #8A8F98
Amber (sell-ish): #FFB020
Red (strong sell): #FF4D4F
UI Surface (cards): #0B1320
Muted text: #B8C0CC Use semi-transparent layers for depth.
 
Typography:
 
Headline: Inter / Roboto Slab (bold) — friendly but technical
 
Body: Roboto / Inter (regular)
 
Sizes: H1 28sp, H2 20sp, body 14sp, caption 12sp
 
Iconography:
 
Use Material Icons + small custom signal glyphs (arrow up/down, bolt for immediate signal)
 
Rounded cards, soft shadows, 12–16px radius
 
Micro-interactions:
 
When a signal changes, a subtle pulse animation on the badge
 
Chart scrubbing shows a translucent tooltip
 
Toggle switches with haptic feedback (on Android)
 
Dark mode-first design: default to dark theme (trading apps often use dark), but provide light theme option.
 
Logo idea (one-liner):
 
A stylized ticker arrow inside a circle, with a small lightning bolt to imply signals. Keep shapes simple for small icons.
 
Sample homepage hero MV (quick mock words):
 
Big header: “Your Signals” + subline: “Buy/sell signals and explanations — quick, clear, and private.”
 
Add small badges for Market Movers rows: “Top +1” or “Movers” ribbon. Keep strong visual emphasis on signal badges. Add a subtle “Active Alert” chip on Watchlist & Stock Detail that shows when a notification for that ticker is active and awaiting dismissal.
 
---
 
# 13 — Accessibility & localization
WCAG contrast: ensure signal text on badges meets contrast (use white text on colored badges).
Support dynamic type / font scaling.
TalkBack friendly labels for buttons and badges (announce "AAPL: Strong Buy, score 65, confidence 78 percent").
Localize numbers, dates, currency; allow currency preference.
 
---
 
# 14 — Legal / privacy notes
 
*Clear disclaimer in onboarding and settings: “Signals are informational, not investment advice. We do not execute trades.”
* Make sure to show the disclaimer about informational-only signals in onboarding and Settings.
* put "Powered by Stooq.com data" and "Not affiliated with Stooq"

# 15 — Implementation notes for Android
Tech stack: Jetpack Compose, MVVM, Kotlin, Coroutines, Flow, Room, WorkManager, Retrofit.
Key implementation details for local notifications + dismissal policy:
• Polling scheduling
• Use WorkManager for periodic background fetches. For frequency options:
• 3×/day and 1×/day/1×/week — schedule periodic or one-off workers aligned to preferred delivery windows (use WorkManager + AlarmManager if you need exact times; minimize exact alarms unless necessary).
• “Only when open” — don’t schedule background work; evaluate only when the app foregrounds.
• Ensure fetch cadence and battery tradeoffs are explained to users.
• Local evaluation & suppression logic
• After each fetch, the app evaluates candidate events (watchlist signals + market movers) and filters them by user thresholds and quiet hours.
• Before posting a notification, the app must check whether any active notification (that the app previously posted and that remains not dismissed) exists. Implement this with two complementary mechanisms:
• Local state tracking: store lastNotificationIdActive and notificationDismissed boolean(s) in local DB. When you post a notification, write its id and notificationDismissed=false. When the user dismisses it (handled by deleteIntent below), mark notificationDismissed=true and clear lastNotificationIdActive.
• System query where available: optionally query active notifications from the NotificationManager (when feasible) to double-check (note: behavior and APIs vary by Android version). Use system query as a secondary check, not primary logic.
• If lastNotificationIdActive indicates an active (not dismissed) notification, do not post any further new notification. Instead, store the events in queuedEvents or add to the in-app Signals feed; they can be grouped into the next digest.
• Catch dismissal & user actions
• When you create the notification, attach:
• A contentIntent for taps (open deep link).
• A deleteIntent (PendingIntent to a BroadcastReceiver) that fires when the user dismisses the notification. In that BroadcastReceiver, mark notificationDismissed=true, clear lastNotificationIdActive, and optionally post the next queued notification immediately if frequency rules permit.
• Action buttons (Snooze, Add to Watchlist): each action has its own PendingIntent handled by the app.
• This deleteIntent mechanism is the canonical way on Android to detect dismissals and implement your “allow next only after dismiss” rule.
• Throttling & caps
• Maintain notificationCountsForDay to enforce the 3×/day cap, increment on each posted notification, reset at midnight local time.
• If cap reached, add further events to queuedEvents for digesting.
• Queued events & digest
• When a notification is suppressed (due to an active notification or cap), store the event in queuedEvents. When the active notification is dismissed (deleteIntent) or when the next digest slot occurs, process queuedEvents and post a grouped notification/digest.
• Edge cases & failure handling
• If the app or device restarts, persist lastNotificationIdActive and queuedEvents to Room. If a posted notification was cleared by the system (not via user swipe) you may not receive deleteIntent — handle inconsistencies by periodically cross-checking the system’s active notifications and clearing stale lastNotificationIdActive entries older than a configured TTL (e.g., 24 hours) to avoid permanent suppression.
• Permission & UX
• Explain in onboarding why background fetch is used; let users pick “only when open” easily.
• Provide a clear setting to reset notification state (if user wants to clear queued events).
 
# 16 — Acceptance criteria & test cases (updated)
Acceptance criteria (selected):
• User chooses frequency 3×/day, app posts up to three notifications/day (unless user dismisses earlier ones which clears active status to allow more per cap).
• If a notification is active and not dismissed, the app does not post additional notifications (they appear in the in-app feed/queuedEvents instead).
• When the user dismisses the active notification (swipe), the app receives the deleteIntent, marks the notification dismissed, and then the next queued eligible notification may post (respecting caps/quiet hours).
• If user selects “Only when app open,” no background notifications are posted; events appear in Signals feed when the app is opened.
Test cases:
• Post a notification, ensure while it’s present no new notifications appear, and queued events are stored. Swipe to dismiss — verify app posts next eligible notification immediately (if frequency cap allows).
• Reach the 3×/day cap — verify extra events are grouped into digest.
• Select “Only when open” — verify no background posts occur.
• Simulate device restart and ensure persisted lastNotificationIdActive does not permanently block posting (stale entries are cleared by TTL or by checking system state).
 
Edge cases:
• App offline/stale data: show cached market movers and mark freshness; do not create notifications based on stale data older than a configurable threshold (e.g., 10 minutes).
• Duplicate events: if a symbol triggers the same label within a configured cooldown window (e.g., 60 minutes), do not re-notify.
Test cases (examples):
• Simulate multiple market mover events across the day and verify notification cap enforcement and grouping.
• Simulate quiet hours and verify notifications suppress until quiet hours end.
• Validate deep-link opens correct stock detail and highlights the event explanation.