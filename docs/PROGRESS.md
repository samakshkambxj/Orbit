# ORBIT progress

## 2026-11-26 — chat, providers and settings

- Added Room storage for providers, models, conversations and messages.
- Added DataStore settings for theme, dynamic color and the default model.
- Added encrypted API key storage backed by the Android Keystore.
- Added streaming OpenAI-compatible and Anthropic clients with SSE parsing,
  cancellation, timeouts and readable error mapping.
- Added a chat runtime that persists partial output, survives rotation and marks
  interrupted generations after a restart.
- Added the Compose UI: chat list, chat screen with Markdown and code blocks,
  model picker, provider management with connection test, and settings.

### Status

- Commits: `b9f1f41`, `782a163`, `0a49756`.
- CI: green. Run `45838642627` passed unit tests, lint and debug APK assembly in
  4m09s.

### Next

1. Search across conversations and message actions (copy, retry, edit).
2. Assistant presets and per-chat system prompts.
3. Attachments and image input.
4. Coding agent with tool calls, approvals, diff review, editor, Git and
   terminal.
