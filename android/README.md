# FitPlan Android

Android wrapper for the FitPlan PWA.

The app loads https://fitplan-plani-im.netlify.app/ in a native WebView, keeps JavaScript/DOM storage enabled, supports file upload for the personal hero photo, Back navigation, and exposes a small native bridge for Android BLE availability.

Build debug APK with GitHub Actions. The generated artifact is named FitPlan-debug-apk.

Important: direct automatic weight import from the Senssun iF1031D requires its BLE GATT service/characteristic protocol to be identified from the physical scale. This project does not invent that protocol.
