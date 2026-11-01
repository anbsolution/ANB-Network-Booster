# ANB Network Booster — Phase 4 Patch

## Features
- Airtel-focused network intelligence dashboard.
- Last 3 structured test average for download and upload.
- Estimated 0–100 stability score based on speed, TCP latency, jitter and TCP probe success.
- Warnings for low Airtel RSRP/SINR/RSRQ, low upload, high latency and high jitter.
- Keeps existing plain-text test history and stores a separate structured history for the new dashboard.
- Retains existing SIM signal monitor and internet tester.

## Apply
Replace:
`app/src/main/java/com/anb/networkbooster/MainActivity.kt`

No Gradle dependency or manifest changes are required.

## Important limitations
- The score is an app-defined diagnostic estimate, not an official Airtel score.
- Android apps cannot increase modem transmit power, tower capacity or carrier coverage.
- The app measures the current default cellular network. Keep Airtel selected as the default mobile-data SIM.
- Existing old history remains visible. Structured averages begin accumulating from the first Phase 4 test.
