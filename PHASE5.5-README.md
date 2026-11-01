# ANB Network Booster — Phase 5.5

## Advanced Dashboard & Charts

This patch extends the existing MainActivity with compact local trend visualizations.

### Included
- Recent up to five saved speed-test records, oldest to newest.
- Compact sparkline-style trend for download, upload, TCP latency and jitter.
- Latest value shown for each metric.
- Updates after each completed speed test and signal refresh.
- Uses existing local SharedPreferences history; no network call or chart library added.
- Preserves the Phase 4 dashboard and Phase 5.1–5.4 features.

### Notes
- Trends are relative to the displayed history for each metric; scales are not comparable across metrics.
- Missing values are omitted rather than fabricated.
- The dashboard only displays the last five records from the existing 10-record local history.
- No location coordinates are collected.

## Apply
Replace:
`app/src/main/java/com/anb/networkbooster/MainActivity.kt`

Keep the Phase 5.2 `SignalMonitor.kt` file. Run `git diff --check`, inspect the diff, and do not commit until source review is complete.
Device testing remains scheduled for Phase 5.6.
