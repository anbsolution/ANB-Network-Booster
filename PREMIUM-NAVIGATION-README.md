# ANB Network Booster — Premium Dashboard Navigation

## UI changes
- Adds a persistent bottom navigation bar: Home, Signal, Speed, History, Settings.
- Navigation scrolls to existing sections; it does not create duplicate screens or duplicate data.
- Keeps the Clean Light / Natural Dark appearance switch and persists its selection.
- Retains existing signal monitoring, 3-test speed engine, diagnostic analyzer,
  recommendations, trend dashboard and local test history.
- Native Android Kotlin Views only; no CSS, WebView or third-party UI dependency.

## Navigation behavior
- Home → Network Connection
- Signal → Live SIM Signal
- Speed → Smart Speed Engine
- History → Recent Test History
- Settings → Appearance

## Apply
Replace `app/src/main/java/com/anb/networkbooster/MainActivity.kt`.
Run `git diff --check`, inspect the changed source, and wait for review before committing.
