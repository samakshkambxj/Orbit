# ORBIT progress

## 2026-11-26 — workspace and coding agent

- Added a Workspace tab backed by the Storage Access Framework: pick a folder,
  browse it with breadcrumbs, and create, rename, delete and edit text files.
- Added a coding agent that can list, read and search workspace files, and
  write, create, rename or delete them after explicit approval.
- Added streaming tool calls for OpenAI-compatible and Anthropic providers,
  including tool result messages and a bounded multi-step loop.
- Added approval cards with line-level diff previews before edits are applied.
- Added provider model discovery, so model IDs can be imported instead of typed.
- Added message editing: edit a sent message to regenerate the conversation
  from that point.
- Added reasoning display for models that stream thinking content.
- Added chat pinning, with pinned chats kept at the top of the list.

### Status

- Feature commits: `f3e4994`, `8e31b59`, `66edd99`, `ae78bd7`, `635ab75`.
- CI: green on every push.

### Next

1. Git status, diff and commit support for the workspace.
2. A terminal runner with explicit approval for commands.
3. Voice input and richer export options.

## 2026-11-26 — images and model management

- Added image attachments: picker, downscaling into app storage, multimodal
  requests for OpenAI-compatible and Anthropic models, and thumbnails in chat.
- Added multiple models per provider with add, update and remove controls.
- Attachments are cleaned up when a chat is deleted or all chats are cleared.

### Status

- Feature commits: `cf50a41`, `78d5173`, `26dc759`.
- CI: green. Run `45844416273` passed unit tests, lint and debug APK assembly.

### Next

1. Workspace folder, file browser and text editor.
2. Coding agent with tool calls, approvals and diff review.
3. Git and terminal support.
4. Voice input and richer export options.

## 2026-11-26 — assistants and UI polish

- Added assistant presets (name, icon, system prompt, model) with a Discover tab.
- Added per-chat system prompts, response retry and share-chat export.
- Added chat search and date-grouped history.
- Added provider presets for OpenAI, Anthropic, OpenRouter, Groq, Ollama and
  LM Studio.
- Added an app icon.

### Status

- Feature commits: `fe47b03`, `975b08b`, `8bd6bb0`.
- CI: green on every push. Run `45842177987` passed unit tests, lint and debug
  APK assembly.

### Next

1. Attachments and image input.
2. Multiple models per provider with model management.
3. Coding agent with tool calls, approvals, diff review, editor, Git and
   terminal.
4. Voice input and richer export options.

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
