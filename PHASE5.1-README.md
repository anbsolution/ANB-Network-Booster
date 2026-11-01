# ANB Network Booster — Phase 5.1 Smart Speed Engine

## Changes
- Replaces the single-test button with a 3-test smart speed check.
- Runs three independent existing cellular tests sequentially.
- Waits 1.2 seconds between tests so the prior worker can clear its running flag.
- Saves every successful individual result to existing local history.
- Displays mean download, upload, TCP latency and jitter after the batch.
- Stops cleanly and reports the number completed if a test fails.
- Keeps the existing package ID, permissions, signal monitoring and Phase 4 dashboard.

## Apply
Replace `app/src/main/java/com/anb/networkbooster/MainActivity.kt`.

## Data use
Each existing measurement uses about 1 MB download + 256 KB upload. Three tests use approximately 3.75 MB, excluding protocol overhead. The user starts the batch manually. It does not run in the background.

## Limits
This measures the current default cellular network. It does not increase radio power, tower capacity, carrier bandwidth, or coverage. Results can vary with radio conditions, congestion and server path.
