# Codex Mobile Android UI Rules

## Screen Order

Use this order when touching multiple screens:

1. `ConnectionScreen`
2. `SessionListScreen`
3. `SessionDetailScreen`
4. `SettingsScreen`

Reason:

- The first three screens are the main product loop.
- Settings should support that loop, not dominate it.

## Visual Direction

- Background: warm light neutral in light mode, deep slate in dark mode.
- Primary accent: slate blue.
- Secondary accent: muted coral, used sparingly.
- Cards: soft tonal separation instead of heavy shadows.
- Corners: rounded, but not bubbly.
- Typography: compact and readable; no oversized social-style chat headers.

## Safe Icon Replacements

Good icon-only or icon-first candidates:

- open settings
- refresh session
- expand or collapse details
- attach image
- copy message or code
- create inside a known directory when the nearby heading already explains context

Keep text or text-plus-icon for:

- connect to bridge
- send or start session
- approve or reject
- delete saved connection
- disconnect bridge

## Compose Guidance

- Prefer theme and component tuning over custom drawing.
- Prefer `Card`, `Surface`, `OutlinedTextField`, `Button`, `OutlinedButton`, `IconButton`, and tonal variants.
- Keep transcript rendering simple; readability matters more than decorative chrome.
- Avoid nested scrolling changes unless the task explicitly targets behavior.
- Preserve `Modifier.testTag(...)` hooks so existing tests stay meaningful.

## Page-Specific Notes

### Connection

- One dominant action.
- Connection state should be visible before the input field.
- Settings should be available, but visually secondary.

### Session List

- The create-draft action is the main CTA.
- Directory grouping should be immediately legible.
- Session cards should show title, subtitle, and updated time with minimal clutter.

### Session Detail

- Status should be compact and glanceable.
- The transcript should own most of the vertical space.
- The input area should feel like a tool tray, not a plain form row.

### Settings

- Split into saved connections, defaults, and diagnostics.
- Finite options should be presented as grouped controls, not free text.
- Diagnostics actions can be icon-first if labels remain obvious from nearby text.
