# Skin contract

AmpyCore and AmpyCam now consume the **same** theme files from
`config/themes/{fusion,cyberpunk,futuristic,minimal}.json`. This means the
palette and per-corner radii are identical across both apps.

The prototypes save each app's choice locally because Android isolates app
storage. They **do not** have permission to read each other's private
preferences or to modify arbitrary third-party apps. A future AmpyCore ROM
service can implement global theme updates with signature-level permissions,
a sanitized schema, and explicit opt-in for each app.
