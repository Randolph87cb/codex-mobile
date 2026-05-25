---
name: Proton Codex
colors:
  surface: '#f8f9fa'
  surface-dim: '#d9dadb'
  surface-bright: '#f8f9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f5'
  surface-container: '#edeeef'
  surface-container-high: '#e7e8e9'
  surface-container-highest: '#e1e3e4'
  on-surface: '#191c1d'
  on-surface-variant: '#43474e'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f2'
  outline: '#74777f'
  outline-variant: '#c3c6cf'
  surface-tint: '#426089'
  primary: '#13345b'
  on-primary: '#ffffff'
  primary-container: '#2d4b73'
  on-primary-container: '#9ebbea'
  inverse-primary: '#abc8f7'
  secondary: '#466082'
  on-secondary: '#ffffff'
  secondary-container: '#bcd6fe'
  on-secondary-container: '#435d7f'
  tertiary: '#442f00'
  on-tertiary: '#ffffff'
  tertiary-container: '#604505'
  on-tertiary-container: '#dab46c'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d4e3ff'
  primary-fixed-dim: '#abc8f7'
  on-primary-fixed: '#001c3a'
  on-primary-fixed-variant: '#294870'
  secondary-fixed: '#d3e4ff'
  secondary-fixed-dim: '#aec8ef'
  on-secondary-fixed: '#001c38'
  on-secondary-fixed-variant: '#2e4869'
  tertiary-fixed: '#ffdea5'
  tertiary-fixed-dim: '#e8c178'
  on-tertiary-fixed: '#261900'
  on-tertiary-fixed-variant: '#5d4202'
  background: '#f8f9fa'
  on-background: '#191c1d'
  surface-variant: '#e1e3e4'
typography:
  display-title:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  metadata-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  margin-mobile: 16px
  gutter-list: 1px
---

## Brand & Style

The design system is built for technical professional utility, specifically targeting developers and system administrators. The brand personality is **precise, reliable, and functional**, prioritizing clarity over decorative elements.

The visual style is **Corporate / Modern** with a focus on systematic organization. It leverages a clean, structured layout with subtle depth through tonal layering and micro-shadows. The interface aims to evoke a sense of calm control in high-stakes environments, using a high-density information display that remains legible and uncrowded.

## Colors

The palette is anchored by a deep professional blue, which provides a strong sense of stability.

- **Primary Blue (#2D4B73):** Used for key action buttons, header cards, and primary branding elements.
- **Surface Neutrals:** A range of very light greys (#F8F9FA) are used to distinguish between different content containers, such as list groupings versus the main background.
- **Status Indicators:** Clear semantic colors (Green for Online, Yellow for Idle, Grey for Offline) are used to communicate system state at a glance.
- **Text & Contrast:** Secondary text uses a mid-tone grey to establish hierarchy without sacrificing legibility against white backgrounds.

## Typography

The typography system uses **Hanken Grotesk** for its sharp, contemporary feel and excellent legibility in high-density layouts.

- **Headlines:** Use semi-bold weights to clearly define section starts.
- **Body:** Standardized on 14px for optimal information density on mobile devices.
- **Metadata:** Smaller 12px type is used for secondary information like IDs, timestamps, and status labels.
- **Monospaced Accents:** **JetBrains Mono** is utilized for technical strings (IP addresses, session IDs, and code snippets) to differentiate system data from human-readable labels.

## Layout & Spacing

This design system uses a **Fluid Grid** for mobile, centered around a 4px baseline.

- **Margins:** Standard 16px horizontal margins for all screen content.
- **Grouping:** Related items (like session list items) are separated by a 1px gutter or thin divider, while distinct logic sections (like different categories) are separated by 24px of whitespace.
- **Safe Areas:** Interactive elements maintain a minimum 48px touch target height, even when visually appearing smaller.

## Elevation & Depth

Hierarchy is established through **Tonal Layers** and extremely soft, ambient shadows.

- **Level 0 (Background):** The primary background uses #F8F9FA.
- **Level 1 (Cards/Containers):** White (#FFFFFF) surfaces are used for interactive cards and list groups. They feature a subtle 1px border or a soft shadow (0px 2px 4px rgba(0,0,0,0.05)) to separate them from the background.
- **Floating Actions:** Primary actions, such as the "Add" button, use higher elevation (shadows with 8px-12px blur) to appear above the content scroll.

## Shapes

The shape language is **Rounded**, favoring a professional yet accessible aesthetic.

- **Primary Containers:** 1rem (16px) corner radius for main feature cards and large input groups.
- **Small Elements:** 0.5rem (8px) corner radius for list items, chips, and small action icons.
- **Inputs:** 1.5rem (24px) or full-pill shapes are used for search bars and message inputs to provide a distinct tactile contrast from the rectangular cards.

## Components

### Buttons

- **Primary:** Filled with #2D4B73, white text/icons. Full-width buttons in cards use 8px rounded corners.
- **FAB:** Circular buttons with primary blue fill and centered white icons for the most common app action.

### Cards & Lists

- **Session Cards:** Grouped list items with a leading icon (in a grey box), title, sub-text, and trailing status label.
- **Header Cards:** Large blue blocks used at the top of views to summarize status (e.g., "Connected to Bridge").

### Input Fields

- **Monospaced Inputs:** For technical addresses, using JetBrains Mono text and a leading "link" or "terminal" icon.
- **Chat/Command Input:** Pill-shaped text area with an attachment icon on the left and a circular send button on the right.

### Status Chips

- Small text labels with a circular dot prefix. The dot color indicates the status (Green/Yellow/Grey).

### Navigation

- Top app bar with a back button (left) and contextual actions (right, e.g., search, filter, settings).
