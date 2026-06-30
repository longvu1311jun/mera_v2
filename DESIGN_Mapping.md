---
name: ProData Mapping
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#3f4850'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#707881'
  outline-variant: '#bfc7d2'
  surface-tint: '#006398'
  primary: '#006194'
  on-primary: '#ffffff'
  primary-container: '#007bb9'
  on-primary-container: '#fdfcff'
  inverse-primary: '#93ccff'
  secondary: '#505f76'
  on-secondary: '#ffffff'
  secondary-container: '#d0e1fb'
  on-secondary-container: '#54647a'
  tertiary: '#006947'
  on-tertiary: '#ffffff'
  tertiary-container: '#00855b'
  on-tertiary-container: '#f5fff6'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#cce5ff'
  primary-fixed-dim: '#93ccff'
  on-primary-fixed: '#001d31'
  on-primary-fixed-variant: '#004b73'
  secondary-fixed: '#d3e4fe'
  secondary-fixed-dim: '#b7c8e1'
  on-secondary-fixed: '#0b1c30'
  on-secondary-fixed-variant: '#38485d'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  title-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  container-max: 1440px
  gutter: 20px
---

## Brand & Style
The design system is engineered for high-density data mapping and enterprise administration. The brand personality is authoritative, precise, and systematic, prioritizing functional clarity over decorative flair. It aims to evoke a sense of absolute reliability and control, ensuring users feel confident while managing complex data relationships.

The aesthetic follows a **Corporate / Modern** style with a focus on systematic utilitarianism. It employs a "white-label" professional feel: generous white space to reduce cognitive load, razor-sharp alignment, and a sophisticated monochromatic foundation punctuated by high-intent functional colors.

## Colors
The palette is rooted in professional stability. 

*   **Primary (Corporate Blue):** Used for primary actions, active states, and progress indicators. It signifies intelligence and trust.
*   **Secondary (Slate):** Handles the bulk of the UI structure, including borders, secondary text, and iconography. It provides a grounded, neutral framework.
*   **Tertiary (Emerald):** Reserved strictly for success states, completed mappings, and "Go" actions.
*   **Neutrals:** A range of cool grays (from #F8FAFC to #0F172A) defines the surface hierarchy. 

Backgrounds utilize subtle off-whites to reduce eye strain during long working sessions, while text maintains high contrast for accessibility.

## Typography
This design system uses **Inter** for its exceptional legibility in data-heavy environments. The typographic scale is optimized for information density. 

**Vietnamese Language Support:** Special attention is paid to line-heights (leading) to accommodate diacritics in Vietnamese without clipping or crowding. 

*   **Headlines:** Use tighter letter spacing and bolder weights to create clear section entry points.
*   **Body:** Uses `body-md` (14px) as the default for data tables and property panels to maximize visible information.
*   **Labels:** All-caps labels are used sparingly for table headers and category descriptors to differentiate them from interactive content.

## Layout & Spacing
The layout employs a **12-column fluid grid** for the main content area, anchored by a fixed left-hand navigation sidebar (240px). 

*   **Rhythm:** A 4px baseline grid ensures consistent vertical rhythm.
*   **Density:** For the mapping interface, "Compact" spacing (8px) is preferred between related data fields, while "Comfortable" spacing (24px) is used to separate distinct functional modules.
*   **Tables:** Data tables should occupy 100% of their container width, with horizontal scrolling enabled only for overflow columns.

## Elevation & Depth
Depth is conveyed through **Tonal Layers** and **Low-Contrast Outlines** rather than heavy shadows, maintaining a clean, "flat" professional look.

*   **Level 0 (Background):** #F8FAFC - The canvas.
*   **Level 1 (Cards/Tables):** White (#FFFFFF) with a 1px border in Slate-200. No shadow.
*   **Level 2 (Dropdowns/Popovers):** White with a soft, 4px blur, 10% opacity neutral shadow to provide separation from the base layer.
*   **Active States:** High-contrast primary borders indicate focus, rather than lift.

## Shapes
The shape language is **Soft (0.25rem)**. This subtle rounding takes the edge off the industrial nature of the data without appearing overly consumer-focused or "playful."

*   **Standard (4px):** Buttons, Input fields, Checkboxes.
*   **Large (8px):** Data cards, Modals.
*   **Pill:** Used exclusively for Status Badges (Trạng thái) to differentiate them from interactive buttons.

## Components
*   **Data Tables (Bảng dữ liệu):** High-density rows (40px height). Header cells use `label-md` with a subtle gray background. Hover states on rows use a very light blue tint.
*   **Search Inputs (Tìm kiếm):** Rectangular with a 1px border. Includes a leading search icon. Placeholder text should be in Slate-400.
*   **Buttons (Nút):**
    *   *Primary:* Solid Blue (#0284C7) with white text.
    *   *Secondary:* White background with Slate-300 border and Slate-700 text.
    *   *Success:* Solid Emerald (#10B981) for final "Lưu" (Save) or "Hoàn tất" (Complete) actions.
*   **Status Badges (Nhãn trạng thái):** Use a "Ghost" style (light background tint with dark text).
    *   *Success:* Light Emerald background / Dark Emerald text.
    *   *Pending:* Light Amber background / Dark Amber text.
    *   *Error:* Light Red background / Dark Red text.
*   **Mapping Connectors:** Use 2px solid Slate-300 lines with circular nodes to represent data relationships between columns. Active connections transition to Primary Blue.