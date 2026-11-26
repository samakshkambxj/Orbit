# ORBIT

ORBIT is an Android AI client built with Kotlin and Jetpack Compose. It connects
to your own model providers, keeps conversations and keys on the device, and
includes a coding agent that can work inside a folder you choose.

## Features

- **Providers** — OpenAI-compatible and Anthropic APIs, multiple models per
  provider, encrypted API keys, connection tests and model discovery.
- **Chat** — streaming replies, Markdown and code blocks, image attachments,
  reasoning display, message editing, retry, search, pinning and sharing.
- **Assistants** — reusable system prompts with their own model and icon.
- **Workspace** — pick a folder with the system picker, then browse, create,
  rename, delete and edit text files.
- **Agent** — list, read and search workspace files automatically; write, edit,
  create, rename and delete files only after you approve a diff.

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
- [x] Provider model discovery and message editing
- [x] Reasoning display for thinking models
- [x] Pinned chats
- [x] Workspace browser and text editor
- [x] Coding agent with workspace tools, approvals and diff previews
- [x] Exact text edits and line-range reads for the agent
- [ ] Git and terminal support
