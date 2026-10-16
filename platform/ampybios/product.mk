# AmpybiOS independent device add-on (not a GrapheneOS project).
# Intentionally adds only an unprivileged, in-tree application.
PRODUCT_PACKAGES += AmpyCore

# Non-sensitive version metadata for device debugging.
PRODUCT_COPY_FILES += \
    vendor/ampybios/config/build.json:$(TARGET_COPY_OUT_PRODUCT)/etc/ampybios/build.json
