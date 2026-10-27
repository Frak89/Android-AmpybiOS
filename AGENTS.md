# AmpybiOS combined firmware integration

Read README.md, docs/COMBINED_PIXEL7PRO_IT.md and docs/FUNZIONI_INTEGRATE_IT.md.
This public repository holds the Android/GrapheneOS integration. The original
Frak89/AmpybiOS app repository is private and is pinned separately in
config/ampysandbox-upstream.json. Never copy its source or generated firmware
containing it into public commits/artifacts as an incidental implementation step.

AmpySandbox is the original io.ampy.bios APK, staged byte-for-byte as a verified
presigned product app. Keep the stable package and signer. AmpyCore and the
Android AmpyLauncher are separate modules. App Python modules are not a new
hardware kernel, and source ZIPs/target-files are not installable ROMs.

Use the correct cheetah device, coherent pinned manifest/kernel/vendor inputs,
and the upstream signing/packaging tools. Never commit keys, tokens or SDK/build
outputs. Existing keys must remain persistent. No build helper should flash,
unlock, erase user data or enable an official GrapheneOS OTA endpoint.

Run configuration validation, Python host tests, shell syntax checks and the
Java PerformancePolicy tests. When preparing the personal combined source ZIP,
verify the private source snapshot against the configured original Git tree.
Tests using mocked APK tools or synthetic signed factory ZIPs are host tests,
not proof of APK compilation, valid boot images, AVB or physical Pixel boot.
