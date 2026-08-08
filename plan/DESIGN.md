---
name: Premium Dark Stream
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c9c4d8'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#928ea1'
  outline-variant: '#484555'
  surface-tint: '#c9bfff'
  primary: '#c9bfff'
  on-primary: '#2e009c'
  primary-container: '#917eff'
  on-primary-container: '#28008a'
  inverse-primary: '#5d3fe0'
  secondary: '#53e076'
  on-secondary: '#003914'
  secondary-container: '#02b04c'
  on-secondary-container: '#003a14'
  tertiary: '#c6c6c7'
  on-tertiary: '#2f3131'
  tertiary-container: '#909191'
  on-tertiary-container: '#282a2a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e5deff'
  primary-fixed-dim: '#c9bfff'
  on-primary-fixed: '#1a0063'
  on-primary-fixed-variant: '#441cc8'
  secondary-fixed: '#72fe8f'
  secondary-fixed-dim: '#53e076'
  on-secondary-fixed: '#002108'
  on-secondary-fixed-variant: '#005320'
  tertiary-fixed: '#e2e2e2'
  tertiary-fixed-dim: '#c6c6c7'
  on-tertiary-fixed: '#1a1c1c'
  on-tertiary-fixed-variant: '#454747'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '500'
    lineHeight: '1.6'
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1.4'
    letterSpacing: 0.01em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  gutter: 24px
  margin: 32px
  section-gap: 64px
  container-max: 1440px
---

## Brand & Style

This design system is built on a foundation of immersive digital experiences, prioritizing high-impact content delivery within a sophisticated, low-light environment. The brand personality is energetic yet premium, designed to recede into the background so that media and imagery can take center stage.

The aesthetic direction is **Modern-Minimalist with High-Contrast accents**. It utilizes the deep blacks of OLED-friendly interfaces to create a sense of infinite depth. The user experience is focused on rhythm and flow, using generous whitespace (or "blackspace") to reduce cognitive load and create a cinematic atmosphere.

## Colors

The palette is anchored by a "Pure Black" foundation, optimized for contrast and premium hardware displays. 

- **Primary (#7B61FF):** A vibrant purple used for primary actions, active states, and brand-heavy highlights.
- **Secondary (#1DB954):** A nod to the heritage of the inspiration, used sparingly for success states or specific "play" interactions to maintain familiarity.
- **Neutral (#121212):** The base canvas. Variation in depth is achieved through slight lightening of this hex rather than adding greys.
- **Typography:** Headlines utilize pure white (#FFFFFF) for maximum legibility, while secondary text uses #EEEEEE to reduce eye strain in dark environments.

## Typography

This design system uses **Plus Jakarta Sans** to emulate the geometric, friendly, and bold characteristics of iconic streaming interfaces. 

The typographic hierarchy is intentionally dramatic. Display and Headline styles use extra-bold weights and tight letter-spacing to create a "wall of text" effect for editorial sections. Body text maintains a medium weight to ensure it doesn't disappear against the pure black background. All labels and metadata should be rendered in semi-bold to maintain clarity at smaller scales.

## Layout & Spacing

The layout follows a **Fixed-Fluid Hybrid Grid**. Content is housed within a 12-column system with a 1440px max-width, while the background remains fluid to fill the viewport.

A strict 8px linear scale governs all spacing. Generous section gaps (64px+) are required to separate distinct content buckets, such as "Recently Played" or "Recommended." Internal padding within cards and containers should never drop below 16px to ensure touch targets are accessible and the interface feels airy.

## Elevation & Depth

In a pure black environment, traditional drop shadows are often invisible. This design system communicates depth through **Tonal Layering** and **Subtle Elevation Shifts**:

1.  **Level 0 (Base):** #121212 — The global background.
2.  **Level 1 (Surface):** #1E1E1E — Used for cards and secondary navigation bars.
3.  **Level 2 (Float):** #2A2A2A — Used for hover states and tooltips.

Interaction depth is reinforced with high-contrast outlines (1px solid #FFFFFF at 10% opacity) to define edges without breaking the dark aesthetic. Glassmorphism is reserved strictly for global navigation headers to provide context of the content scrolling beneath.

## Shapes

The shape language is defined by a consistent **Rounded** philosophy. Large containers and cards utilize a 1rem (16px) radius to soften the high-contrast visuals. 

Buttons and interactive chips transition to a "Pill" shape (fully rounded) to differentiate them from content-driven containers. This distinction helps users instantly identify clickable actions versus informational cards. Imagery must always follow the corner radius of its parent container.

## Components

### Buttons
Primary buttons are pill-shaped, using the #7B61FF purple with white text. On hover, buttons should scale slightly (1.05x) rather than changing color, maintaining the premium feel.

### Cards
Cards are the primary vehicle for content. They feature a #1E1E1E background with a 16px corner radius. High-contrast imagery should occupy the top 70% of the card, with a subtle gradient overlay at the bottom to ensure white text remains legible over varying image backgrounds.

### Inputs
Search and text fields use a subtle #2A2A2A fill with a "ghost" border. Upon focus, the border transitions to the primary purple. 

### Chips & Tags
Used for filtering, these are small, low-profile pill shapes. Active states use the primary purple, while inactive states use a semi-transparent white (white at 10% opacity) to recede into the background.

### Imagery
All imagery should have a slight desaturation applied in its default state, returning to full saturation on hover or focus to create a "lighting up" effect as the user interacts with the grid.