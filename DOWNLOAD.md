# Building the APK

The Android source code is in this repo.

I added a manual GitHub Actions workflow at `.github/workflows/apk.yml` that runs:

```bash
gradle assembleDebug --no-daemon
```

Because the artifact-upload workflow was blocked while creating it, this repo currently builds the APK but does not automatically attach the APK as a downloadable artifact.

## Manual build option

Open the repo in Android Studio, let Gradle sync, then use:

**Build → Build Bundle(s) / APK(s) → Build APK(s)**

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## App status

This is a visual placeholder app. It has landscape controller UI, profiles, edit mode, draggable controls, buttons, joysticks, sliders, and saved layouts. It does not connect to a PC or game yet.
