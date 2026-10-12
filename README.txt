VJ Prescription – Android app (WebView wrapper)

OPTION A – Android Studio (easiest on a PC)
 1. Install Android Studio, File > Open > this folder, let Gradle sync.
 2. Build > Build Bundle(s)/APK(s) > Build APK(s).
 3. APK: app/build/outputs/apk/debug/app-debug.apk  -> copy to phone and install
    (allow "Install unknown apps").

OPTION B – No install, cloud build (GitHub)
 1. Create a free GitHub repo, upload all files in this folder (keep .github folder).
 2. Actions tab > "Build APK" > Run workflow.
 3. Download "VJ-Prescription-APK" from the finished run, unzip, install the .apk.

To update the app later: replace app/src/main/assets/index.html and rebuild.
Needs internet when making a PDF (jsPDF loads from cdnjs).
