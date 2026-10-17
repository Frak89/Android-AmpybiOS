# AmpybiOS skin synchronization contract

AmpyCore is the local source of four skin identifiers: `fusion`,
`cyberpunk`, `futuristic`, `minimal`. The in-tree Cam/Connect/Launcher
prototypes each reuse the same JSON color/corner profiles; each app has a
local override.

AmpyCore sends explicit `dev.ampybios.action.SKIN_CHANGED` broadcasts to
participating package IDs, with `dev.ampybios.permission.SKIN_SYNC`
at **signature** protection level. Receivers also require that permission,
and validate incoming skin IDs. Only apps signed with the same certificate
as the AmpyCore permission owner can receive them.

## Canonical AmpyRAW editor

The actual editor is `it.ampy.ampyraw` from
`Frak89/AmpyRAW-Cyberdark`, **not** `dev.ampybios.raw`.
AmpyRAW supports an opt-in, permission-protected bridge:
- Fusion / Cyberpunk → existing AmpyRAW Cyberpunk theme.
- Futuristic → existing AmpyRAW Futuristic theme.
- Minimal → existing AmpyRAW Modern theme.
- AmpyRAW's Vista/Retro themes remain locally selectable.
- Following AmpyCore starts disabled, and manual theme choices disable it.

The original editor keeps its own signing identity for APK updates.
If it is **not signed by the same cert as AmpyCore**, Android refuses
cross-app skin sync. **Never weaken signature checking or silently
re-sign an installed AmpyRAW APK** just to get shared visuals; you can
still choose a theme manually.

These broadcasts are an **optional app-level feature**, NOT global
SystemUI skinning. They do not change RAW color development or export.
