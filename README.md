# ORBIT

ORBIT is an Android-native AI coding workstation built with Kotlin, Jetpack
Compose and Material 3.

This repository is currently a clean Compose scaffold. The next milestones are
the Rikka-style interface, provider setup, persistent chat, the coding agent,
the editor, Git and the terminal.

## Build

GitHub Actions is the build system. Every push to `main` runs unit tests, lint
and a debug APK build.

Local build (requires JDK 17 and the Android SDK):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Structure

| Path | Purpose |
|---|---|
| `app/src/main/java/com/orbit/app/MainActivity.kt` | App entry point |
| `app/src/main/java/com/orbit/app/ui/theme/` | Material 3 theme |
| `app/src/main/res/` | Android resources |
| `.github/workflows/ci.yml` | Tests, lint and APK build |
| `docs/PROGRESS.md` | Current status, commits and next work |

## Commits

Use Conventional Commits. Keep build, UI, tests and CI changes in separate,
reviewable commits. Never force-push or rewrite published history.
