# ANB Network Booster Ultra

Phase 1 foundation for a non-root Android network diagnostics app.

## Current Phase 1 scope
- Android project and Kotlin build configuration
- Network state monitoring using `ConnectivityManager`
- GitHub Actions debug APK workflow
- Minimal dashboard shell

## Important limitations
This app cannot increase radio transmit power, change tower coverage, force a carrier handover, or guarantee better signal. It reports network state and will add device-supported diagnostics in later phases.

Cell information and LTE signal metrics are permission-, OS-, carrier-, and device-dependent. Phase 2 will request only the required runtime permissions and display unavailable metrics honestly.

## Build
GitHub Actions builds the debug APK on pushes to `main` and on manual workflow dispatch.
