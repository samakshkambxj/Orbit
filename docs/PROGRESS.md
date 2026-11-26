# ORBIT progress

## 2026-11-26 — clean rebuild

- Replaced the previous codebase with a blank Kotlin/Compose project.
- Added Gradle 8.9 wrapper, AGP 8.7.3, Kotlin 2.0.21 and Compose Material 3.
- Added the app entry point, theme, resources and a smoke test.
- Added GitHub Actions CI for unit tests, lint and debug APK assembly.

### Status

- Initial commits: pending.
- CI: pending first run.

### Next

1. Confirm CI is green and publish the debug APK artifact.
2. Build the Rikka-style application shell: navigation, chat surface, composer,
   model/provider pickers and settings.
3. Add provider configuration and persistent chat.
4. Add the coding agent, approvals, diff review, editor, Git and terminal.
