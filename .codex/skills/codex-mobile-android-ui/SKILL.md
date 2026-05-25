---
name: codex-mobile-android-ui
description: Use when redesigning or polishing the Android UI in this repository, especially Compose screens under `android/app/src/main/java/com/openai/codexmobile/ui/`. Covers theme tuning, spacing, card hierarchy, icon-first secondary actions, and lightweight layout refactors that preserve performance, Chinese copy, test tags, and the bridge-driven product flow.
---

# Codex Mobile Android UI

## Overview

This skill guides UI work for `codex-mobile`, a lightweight Android client for the Windows bridge. Use it when updating the connection, session list, session detail, or settings screens while preserving the current bridge-driven product flow and repository constraints.

## Workflow

1. Read the project `AGENTS.md` and keep these constraints in scope:
   - Android stays a light client.
   - Primary flows are connection, session list, session detail, sending input, and reading replies.
   - User-facing copy is Chinese by default.
   - If `android/` changes, run the project Android validation commands.
2. Read [references/DESIGN.md](references/DESIGN.md) before changing visual style:
   - treat it as the source of truth for color, typography, spacing, shape, elevation, and component styling
   - prefer aligning Compose tokens and component usage to `DESIGN.md` instead of restating visual rules in this skill
3. Inspect the current UI surface before changing code:
   - `ui/CodexMobileApp.kt`
   - `ui/screen/ConnectionScreen.kt`
   - `ui/screen/SessionListScreen.kt`
   - `ui/screen/SessionDetailScreen.kt`
   - `ui/screen/SettingsScreen.kt`
   - `ui/theme/`
   - `ui/TestTags.kt`
4. Decide the scope before editing:
   - Visual pass only: theme, spacing, card hierarchy, iconography, button emphasis.
   - Structural pass: rearrange sections inside a screen, but keep the same data flow and navigation contract.
   - Avoid protocol or repository changes unless the task explicitly calls for them.
5. Start with theme and tokens from `DESIGN.md`, then update screens in this order:
   - Connection
   - Session list
   - Session detail
   - Settings
6. Apply repository-specific UI rules from [references/ui-rules.md](references/ui-rules.md) for action affordances, page priorities, and Compose safety boundaries.
7. After edits, run Android validation and record what changed.

## UI Rules

- Treat the product as a control console, not a social chat app.
- Avoid:
  - heavy blur, glassmorphism, or complex custom drawing
  - large always-on animations
  - new runtime dependencies for cosmetic reasons
  - hiding critical actions behind icon-only controls
- Keep high-risk or high-commitment actions textual:
  - connect
  - send or start session
  - approval decisions
  - destructive deletes unless context is unmistakable
- Preserve `TestTags` unless the change explicitly includes test updates.

## Page Priorities

- Connection screen:
  - emphasize current connection state
  - show one obvious primary action
  - keep endpoint input easy to scan and edit
- Session list:
  - emphasize grouped directories
  - highlight the create-draft action
  - keep per-session cards compact and readable
- Session detail:
  - compress session status into a compact strip
  - maximize readable transcript space
  - keep the input area stable and tool-like
- Settings:
  - separate saved connections, defaults, and diagnostics
  - use chips or grouped controls for finite choices

## Implementation Heuristics

- Prefer Material 3 components already in Compose before inventing custom primitives.
- Theme work should usually happen before per-screen polish.
- If the app bar and the screen body both expose the same action, simplify one of them.
- Use `DESIGN.md` for visual tokens and [references/ui-rules.md](references/ui-rules.md) for project-specific constraints.

## Validation

- For Android UI changes, run:
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
- If the skill itself changes, validate it with:
  - `python "C:\Users\Administrator\.codex\skills\.system\skill-creator\scripts\quick_validate.py" ".\.codex\skills\codex-mobile-android-ui"`

## References

- Read [references/DESIGN.md](references/DESIGN.md) for:
  - color, typography, spacing, shape, elevation, and component styling
  - the intended brand tone and density for the UI
- Read [references/ui-rules.md](references/ui-rules.md) when deciding:
  - which buttons can become icons
  - how to prioritize each screen
  - which implementation moves are safe for this app
