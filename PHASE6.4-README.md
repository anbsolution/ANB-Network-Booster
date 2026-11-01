# ANB Network Booster — Phase 6.4

## Signal Trend & Coverage Analytics

This patch reuses the existing per-SIM local coverage history stored by `SignalMonitor`.

### Included
- Signal history summary for the latest detected Airtel SIM label
- RSRP average, best and weakest recorded value
- RSRQ and SINR averages
- First/last sample timestamps and retained sample count
- Manual refresh and Android share-sheet export as plain text
- No new dependency, permission, network request, or background polling
- Existing Home, Signal, Speed, History, Settings and Guide navigation remains intact

### Data limits
- The existing monitor stores at most 20 samples per SIM.
- It stores no more than one sample per SIM every 30 seconds.
- History is local to the device and may be incomplete if Android stops monitoring.
- Signal readings do not prove tower distance, congestion, or guaranteed coverage.

## Apply
Run the included `apply_phase6_4.py` from the repository root. Then run `git diff --check`, inspect `git diff`, and build only through GitHub Actions.
