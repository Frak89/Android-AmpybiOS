# AmpybiOS product overlay
# Adds AmpyCore to PRODUCT_PACKAGES and copies bootanimation and optional APK prebuilts

PRODUCT_PACKAGES += \
    AmpyCore

PRODUCT_COPY_FILES += \
    vendor/ampybi/bootanimation.zip:system/media/bootanimation.zip \
    vendor/ampybi/prebuilts/AmpyCore.apk:system/priv-app/AmpyCore/AmpyCore.apk
