# ANB Network Booster — UI Polish Patch

The supplied screenshot shows the app uses native Android Kotlin views, not a WebView.
Therefore CSS does not control this screen. This patch improves the native view styling.

## UI changes
- Dark navy background with royal-blue header and action buttons.
- Larger, consistent typography and density-aware dp spacing.
- Rounded cards with subtle borders for status, metrics, analyzer and history.
- Better section hierarchy and vertical rhythm.
- Full-height scrolling layout with readable content spacing.
- Preserves Phase 5.1–5.5 network logic, local history, permissions and test flow.

## Apply
Replace `app/src/main/java/com/anb/networkbooster/MainActivity.kt`.
Do not change `SignalMonitor.kt` or other files for this visual-only patch.
Run `git diff --check` and inspect the source before committing.
