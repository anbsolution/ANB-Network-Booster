# Phase 2 – Signal Intelligence

Adds modem-reported signal monitoring for active SIM subscriptions.

## Permissions
- `READ_PHONE_STATE`: subscribe to signal-strength updates and enumerate active subscriptions.
- `ACCESS_FINE_LOCATION`: Android may require this permission (and device Location enabled) before exposing cell information.

The app does not collect or upload location. It does not increase radio power, force tower handovers, or change carrier settings.

## Metrics
- Generic signal dBm and Android signal level.
- LTE RSRP, RSRQ, RSSNR/SINR and RSSI when Android/device exposes them.
- Dual-SIM listener labels where the Android build exposes active subscription information.

Unavailable metrics are displayed as `Unavailable`; this is not interpreted as zero.
