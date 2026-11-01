# ANB Network Booster — Dual Theme Upgrade

## Themes
- Clean Light: white surfaces, royal-blue controls, muted slate text.
- Natural Dark: dark navy surfaces, subtle blue accents and soft contrast.
- Appearance switch is shown below the app header.
- Selection is persisted in SharedPreferences and restored on next launch.

## Safety of existing data
The patch does not clear preferences. Existing test history and settings are retained.
The theme switch recreates the activity to apply the selected palette. It does not change
network monitoring, permissions, cellular radio settings or test endpoints.

## Apply
Replace `app/src/main/java/com/anb/networkbooster/MainActivity.kt`.
Run `git diff --check` and inspect the diff before committing.
