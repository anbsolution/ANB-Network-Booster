# ANB Network Booster — Phase 5.3

## Network Diagnostic Analyzer

This patch updates `MainActivity.kt` to add an explainable, rule-based diagnostic panel.

### Included
- Combines latest Airtel LTE signal classification/radio quality with the latest speed-test record.
- Evaluates download/upload throughput, TCP latency, jitter and failed TCP probes.
- Shows a confidence indicator based on which measurement groups are available.
- Refreshes when a new signal sample arrives and after speed-test records are saved.
- Preserves the Phase 4 intelligence dashboard, Phase 5.1 three-test speed flow and history.

### Important limitations
- This is deterministic local rule-based analysis, not a remote/LLM AI model.
- Low throughput alone cannot prove tower congestion. It may reflect coverage, radio quality, cell load, server path, or other factors.
- TCP probe failures are not ICMP packet loss.
- No radio settings, bands, transmit power or carrier handover are modified.

## Apply
Replace:
`app/src/main/java/com/anb/networkbooster/MainActivity.kt`

Keep the Phase 5.2 `SignalMonitor.kt` changes in place. Run `git diff --check` and inspect the diff.
Do not commit until the Phase 5.2 and Phase 5.3 source changes are both reviewed.
Device testing remains scheduled for Phase 5.6.
