# AmpybiOS safety model

**Do not flash M0. It is scaffolding, not a signed ROM.**

1. Keep GrapheneOS SELinux enforcing, sandboxing, permission controls, hardware-backed keystore and verified boot features intact in the production profile.
2. Root-by-default for every third-party app is deliberately not implemented. Root developer builds, if eventually added, must have explicit authorization prompts and a prominent warning that they are not equivalent to secure GrapheneOS.
3. Never commit release keys, root keys, tokens, signing key passwords, private APKs or vendor binary payloads.
4. AmpyOTA needs a separate HTTPS origin, own signing keys, verified releases and a tested future update path. Do **not** configure the official GrapheneOS update server for derivatives.
5. Never automatically reapply incompatible SystemUI/framework patches on an upstream update; fail closed, run tests, then publish.
6. `Boot Studio` can customize Android's later boot animation. The Pixel proprietary bootloader graphic and Verified Boot warning are not user-substitutable Android assets.
7. Third-party APKs are not redistributed or bundled until signatures, licenses, update responsibility, Google-free operation, push notification limitations and provenance are reviewed.
8. AmpyCam/AmpyRaw camera and CR3 parsing should run with least privilege and isolate native decoders handling untrusted media files.
9. AmpyRadar visual borders are UI indicators and do not add isolation beyond Android's actual sandboxing.

10. A bootanimation ZIP is admitted to the checkout only after bounded PNG chunk
    CRC/dimension and ZIP name/integrity checks, before device file modification.
    It is still untrusted developer artwork until tested in the OS.
