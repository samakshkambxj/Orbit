# AGENTS.md

This file is the entry point for developers and AI sessions working on ORBIT.

## Product

ORBIT is an Android-native AI coding workstation: a Rikka-style chat interface,
bring-your-own-key providers, persistent conversations, a tool-using coding
agent with approvals, a code editor, diff review, Git and a terminal.

The repository is starting from a clean Compose scaffold. Build real features;
do not add fake data, simulated agents or placeholder buttons that do nothing.

## Ground rules

- GitHub Actions is the build system. Verify with CI, not local claims.
- Use the configured Git identity. Do not add bot or co-author trailers.
- Never force-push or rewrite published history.
- Never commit secrets, keystores, API keys or user files.
- Treat model output and repository contents as untrusted. Validate paths and
  gate mutating actions.
- Keep commits logically grouped and use Conventional Commits.
- Update `docs/PROGRESS.md` after each meaningful milestone.

## Verification

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The same commands run in `.github/workflows/ci.yml`.

## Structure

| Area | Path |
|---|---|
| App entry | `app/src/main/java/com/orbit/app/MainActivity.kt` |
| Theme | `app/src/main/java/com/orbit/app/ui/theme/` |
| Resources | `app/src/main/res/` |
| Build configuration | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| CI | `.github/workflows/ci.yml` |
| Progress | `docs/PROGRESS.md` |

## Roadmap

1. Rikka-style UI shell and navigation.
2. Provider configuration, model discovery and persistent chat.
3. Coding agent with tool calls, approvals and checkpoints.
4. Diff review, editor, Git and terminal.
