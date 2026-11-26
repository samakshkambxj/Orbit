# ORBIT progress

## 2026-11-26 — clean rebuild

- Replaced the previous codebase with a blank Kotlin/Compose project.
- Added Gradle 8.9 wrapper, AGP 8.7.3, Kotlin 2.0.21 and Compose Material 3.
- Added the app entry point, theme, resources and a smoke test.
- Added GitHub Actions CI for unit tests, lint and debug APK assembly.

### Status

- Initial commits: `5144de2`, `5a8ab9e`, `24dc3b3`, `1a39ee9`, `529d731`.
- CI: green. Run `45835564282` passed unit tests, lint and debug APK assembly in
  3m12s. The `orbit-debug-1` artifact was uploaded.

### Next

1. Build the Rikka-style application shell: navigation, chat surface, composer,
   model/provider pickers and settings.
2. Add provider configuration and persistent chat.
3. Add the coding agent, approvals, diff review, editor, Git and terminal.
