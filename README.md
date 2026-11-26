# ORBIT

ORBIT is an Android AI client built with Kotlin and Jetpack Compose. The goal is
a clean chat experience that connects to your own model providers, with a coding
agent planned on top.

The app is early but usable: you can add an OpenAI-compatible or Anthropic
provider, pick a model, and chat with streaming responses. Conversations and
keys stay on the device.

## Requirements

- JDK 17
- Android SDK 35

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

To run the full check that CI runs:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Every push to `main` and every pull request builds on GitHub Actions, and the
debug APK is attached to successful runs.

## Layout

```
app/src/main/java/com/orbit/app/
├── MainActivity.kt        app entry point
├── core/                  app container and shared services
├── data/                  database, preferences and repositories
├── provider/              OpenAI and Anthropic API clients
├── chat/                  conversation runtime
└── ui/                    Compose screens and theme
```

`docs/PROGRESS.md` tracks what is done and what comes next.

## Status

- [x] Compose project, theme and CI
- [x] Chat list and chat screen
- [x] Provider setup with encrypted API keys
- [x] Streaming responses with persistence
- [x] Chat search, assistant presets and per-chat prompts
- [x] Image attachments and multiple models per provider
- [ ] Workspace, editor, coding agent, Git and terminal
