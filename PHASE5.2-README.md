# ANB Network Booster — Phase 5.2

## Coverage Intelligence Engine

This patch upgrades `SignalMonitor.kt` while preserving the existing `SimSignalSnapshot`
constructor call site and `MainActivity` UI integration.

### Included
- LTE coverage classification based on RSRP (Excellent / Good / Fair / Weak).
- Separate radio-quality classification using RSRQ and SINR/RSSNR.
- An app-estimated 0–100 coverage score; it is not a carrier or Android official score.
- Contextual, non-invasive recommendations.
- Local per-SIM history: one sample every 30 seconds, up to 20 samples.
- Best and latest observed RSRP summary.

### Limitations
- Does not increase antenna power, change tower coverage, force LTE bands, or control carrier handovers.
- History is time-based and per SIM label; this phase does not store GPS coordinates.
- Android/device/carrier may hide LTE metrics; unavailable values remain unavailable.
- Existing Phone and precise Location permission requirements remain unchanged.

## Apply
Copy the replacement `SignalMonitor.kt` to:
`app/src/main/java/com/anb/networkbooster/network/SignalMonitor.kt`

Run `git diff --check` and review `git diff` before committing.
Do not claim device validation until Phase 5.6 device testing.
