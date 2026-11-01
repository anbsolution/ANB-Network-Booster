# ANB Network Booster — UI Readability & Spacing Upgrade

This is a native Android Kotlin View styling update, not a CSS patch.

## Visual refinements
- Main content typography increased to 16sp.
- SIM signal cards use a slightly smaller 15.5sp size to retain hierarchy.
- Section headings increased to 14sp.
- Captions increased to 13sp.
- Buttons increased to 16sp with a 56dp minimum height.
- More consistent card padding, line spacing and section rhythm.
- Keeps the dark navy / royal blue palette.

## Functionality preservation
This patch changes only view styling values and spacing in `MainActivity.kt`.
It retains the existing signal monitor, network monitor, speed test, history, analyzer,
recommendations and dashboard code. No permissions or network behavior are changed.

## Apply
Replace `app/src/main/java/com/anb/networkbooster/MainActivity.kt`.
Run `git diff --check` and inspect the diff. Do not commit until source review.
