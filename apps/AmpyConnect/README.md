# AmpyConnect — read-only Home Assistant viewer (M0)

Standalone Android client that makes a single user-initiated HTTPS GET request
to a Home Assistant server's `/api/states` endpoint. Parses up to 1 MiB of JSON
and displays at most 60 entity IDs and states, with selectable AmpybiOS skins.

- Requires `INTERNET`, **no root, no location, no Bluetooth permissions**.
- Enter a verified HTTPS base URL (no credentials in URL) and a Home Assistant
  long-lived access token manually. **Token is never persisted**, logged,
  sent to any other host by redirect or embedded into a URL.
- Android platform TLS/hostname verification is enforced. Self-signed HTTPS
  requires a properly trusted certificate; **plain HTTP is unsupported**.
- No state-changing API calls, MQTT writes, MODBUS/PLC commands, or background
  device polling in this release.
- Signer-protected theme updates match AmpyCore (same cert required).
- The app has not yet been compiled or tested against a real Home Assistant
  server; it is source-stage only.

Open `apps/AmpyConnect` in Android Studio and build `:app:assembleDebug`.

Future: explicit allowlists for devices, read-only MQTT subscription, Bluetooth
LE telemetry, and separate safety/authorization review for any physical
actuator or PLC write.
