# Skin contract

AmpyCore and AmpyCam now consume the **same** theme files from
`config/themes/{fusion,cyberpunk,futuristic,minimal}.json`. This means the
palette and per-corner radii are identical across both apps.

The prototypes save each app's choice locally because Android isolates app
storage. They **do not** have permission to read each other's private
preferences or to modify arbitrary third-party apps. A future AmpyCore ROM
service can implement global theme updates with signature-level permissions,
a sanitized schema, and explicit opt-in for each app.

## Prototype app-to-app synchronization

AmpyCore now emits `dev.ampybios.action.SKIN_CHANGED` explicitly to
`dev.ampybios.cam` using a **signature-level permission**
`dev.ampybios.permission.SKIN_SYNC`. AmpyCam receives the theme only if it
opts to `Segui tema AmpyCore` and validates the incoming ID against the
four locally bundled profiles. Manual theme selection in AmpyCam disables
automatic following until re-enabled.

**Both apps must be signed by the same Android signing certificate** to use
this path; Android Studio debug keys from different computers usually differ.
A signature mismatch fails closed without granting external apps access.
This is a local IPC prototype, **not global Android/SystemUI skinning**.
