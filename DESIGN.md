---
name: YOLO-C
description: Your field companion. Offline, on-device screening of cacao pods for Frosty Pod Rot (Kotlin, Jetpack Compose, Material 3).
colors:
  forest: "#375534"
  forest-ink: "#0F2A1D"
  leaf: "#EBF1E6"
  sage: "#6B9071"
  sage-ink: "#5F8465"
  mist: "#AEC3B0"
  ground: "#F6F8F5"
  card-white: "#FFFFFF"
  press-surface: "#F0F3EE"
  hairline: "#E8ECE6"
  control-edge: "#CAD2C8"
  outline: "#7A7E7C"
  ink-muted: "#4F5E54"
  ink-faint: "#606A64"
  inverse-ink: "#16191A"
  disabled-surface: "#EDEFED"
  disabled-ink: "#5B5F5D"
  status-healthy: "#6B9071"
  status-early: "#D9A441"
  status-infected: "#B5473A"
  amber-ink: "#8A6516"
  brick-light: "#DE7A68"
  sage-chip: "#7D9E80"
  healthy-tint: "#E6F0E1"
  early-tint: "#FAF0DA"
  early-tint-ink: "#7A5810"
  infected-tint: "#FBE7E3"
  infected-tint-ink: "#9C3A2F"
  ground-dark: "#0F1310"
  card-dark: "#171D18"
  press-dark: "#1F2721"
  leaf-dark: "#223125"
  hairline-dark: "#273029"
  ink-dark: "#E8EDE8"
  ink-muted-dark: "#B0BAB2"
  ink-faint-dark: "#9AA59D"
  outline-dark: "#777A79"
  edge-dark: "#2B2D2C"
  inverse-dark: "#ECEDEC"
  healthy-tint-dark: "#1E2E22"
  healthy-tint-ink-dark: "#A9CDAA"
  early-tint-dark: "#352B17"
  early-tint-ink-dark: "#EBC36F"
  infected-tint-dark: "#3B211E"
  infected-tint-ink-dark: "#F29A8E"
  night: "#111312"
  lens: "#191B1A"
  lens-fill: "#222423"
  lens-muted: "#A6A9A7"
typography:
  headline:
    fontFamily: "Inter Display, Inter, sans-serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "34sp"
    letterSpacing: "-0.5sp"
  verdict:
    fontFamily: "Inter Display, Inter, sans-serif"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "30sp"
    letterSpacing: "-0.4sp"
  title:
    fontFamily: "Inter, sans-serif"
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "24sp"
    letterSpacing: "-0.2sp"
  body-strong:
    fontFamily: "Inter, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
    letterSpacing: "-0.1sp"
  body:
    fontFamily: "Inter, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "-0.1sp"
  label:
    fontFamily: "Inter, sans-serif"
    fontSize: "15sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0sp"
  caption:
    fontFamily: "Inter, sans-serif"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "18sp"
    letterSpacing: "0sp"
  data:
    fontFamily: "Inter, sans-serif"
    fontSize: "17sp"
    fontWeight: 500
    lineHeight: "24sp"
    letterSpacing: "0sp"
    fontFeature: "tnum"
  figure:
    fontFamily: "Inter Display, Inter, sans-serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "32sp"
    letterSpacing: "-0.5sp"
    fontFeature: "tnum"
  readout:
    fontFamily: "Inter Display, Inter, sans-serif"
    fontSize: "34sp"
    fontWeight: 600
    lineHeight: "40sp"
    letterSpacing: "-0.8sp"
    fontFeature: "tnum"
rounded:
  sm: "8dp"
  md: "16dp"
  full: "999dp"
spacing:
  none: "0dp"
  xxs: "4dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  screen: "20dp"
  lg: "24dp"
  xl: "32dp"
  xxl: "48dp"
components:
  button-primary:
    backgroundColor: "{colors.forest}"
    textColor: "{colors.card-white}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 24dp"
    height: "48dp"
  button-primary-pressed:
    backgroundColor: "{colors.forest-ink}"
    textColor: "{colors.card-white}"
  button-primary-lg:
    height: "56dp"
  button-primary-dark:
    backgroundColor: "{colors.sage}"
    textColor: "{colors.night}"
  button-primary-dark-pressed:
    backgroundColor: "{colors.mist}"
    textColor: "{colors.night}"
  button-secondary:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.forest}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 24dp"
    height: "48dp"
  button-secondary-pressed:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.forest-ink}"
  button-ghost:
    textColor: "{colors.forest}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12dp"
    height: "48dp"
  button-ghost-pressed:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.forest-ink}"
  button-on-dark:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.night}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    height: "48dp"
  button-disabled:
    backgroundColor: "{colors.disabled-surface}"
    textColor: "{colors.disabled-ink}"
  card:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.forest-ink}"
    rounded: "{rounded.md}"
    padding: "16dp"
  card-pressed:
    backgroundColor: "{colors.press-surface}"
  card-dark:
    backgroundColor: "{colors.card-dark}"
    textColor: "{colors.ink-dark}"
  hero-card:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.forest-ink}"
    rounded: "{rounded.md}"
    padding: "16dp"
  status-chip-healthy:
    backgroundColor: "{colors.healthy-tint}"
    textColor: "{colors.forest}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12dp"
    height: "32dp"
  status-chip-early:
    backgroundColor: "{colors.early-tint}"
    textColor: "{colors.early-tint-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12dp"
    height: "32dp"
  status-chip-infected:
    backgroundColor: "{colors.infected-tint}"
    textColor: "{colors.infected-tint-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12dp"
    height: "32dp"
  status-chip-sm:
    typography: "{typography.caption}"
    padding: "0 8dp"
    height: "24dp"
  verdict-chip-no-pod:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.forest-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: "0 8dp"
    height: "24dp"
  box-label-healthy:
    backgroundColor: "{colors.sage-chip}"
    textColor: "{colors.forest-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: "2dp 8dp"
  box-label-early:
    backgroundColor: "{colors.status-early}"
    textColor: "{colors.forest-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: "2dp 8dp"
  box-label-infected:
    backgroundColor: "{colors.status-infected}"
    textColor: "{colors.card-white}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: "2dp 8dp"
  filter-chip:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.forest-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 16dp"
    height: "48dp"
  filter-chip-selected:
    backgroundColor: "{colors.forest}"
    textColor: "{colors.card-white}"
  text-field:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.forest-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "0 16dp"
    height: "48dp"
  text-field-pill:
    rounded: "{rounded.full}"
  segmented-selected:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.forest-ink}"
    typography: "{typography.label}"
    height: "48dp"
  top-bar:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
    height: "56dp"
  offline-badge:
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: "0 8dp"
    height: "28dp"
  tab-bar:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.ink-faint}"
    typography: "{typography.caption}"
    height: "64dp"
  tab-active-indicator:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.forest}"
    rounded: "{rounded.sm}"
    width: "48dp"
    height: "32dp"
  tab-scan-pill:
    backgroundColor: "{colors.forest}"
    textColor: "{colors.card-white}"
    rounded: "{rounded.sm}"
    width: "56dp"
    height: "32dp"
  settings-row:
    textColor: "{colors.forest-ink}"
    typography: "{typography.body-strong}"
    padding: "12dp 16dp"
    height: "64dp"
  settings-icon-disc:
    backgroundColor: "{colors.leaf}"
    textColor: "{colors.sage-ink}"
    rounded: "{rounded.full}"
    size: "40dp"
  snackbar:
    backgroundColor: "{colors.inverse-ink}"
    textColor: "{colors.card-white}"
    typography: "{typography.body-strong}"
    rounded: "{rounded.sm}"
    height: "56dp"
  bottom-sheet:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.forest-ink}"
    rounded: "{rounded.md}"
    padding: "16dp 20dp 20dp"
  camera-shutter:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.mist}"
    rounded: "{rounded.full}"
    size: "76dp"
  camera-gallery:
    backgroundColor: "{colors.lens-fill}"
    rounded: "{rounded.sm}"
    size: "52dp"
---

# Design System: YOLO-C

This file records the Kotlin app as built on branch `redesign` (2026-10-28). The source of truth is `app/src/main/java/com/yoloc/app/theme/` (Palette.kt, Themes.kt, Tokens.kt, Type.kt, Contrast.kt) and `components/`. Token keys above are descriptive slugs; each maps to a code name (`colors.forest` is `Palette.forest700`, read through `YoloCTheme.colors.primary.bg` in light). Where this file and the code disagree, the code wins.

## Overview

**Creative North Star: "The Calm Field Companion"**

A pale, leaf-tinted ground, crisp white cards, and one forest green that means "do this". The app is used outdoors, one-handed, in sun, often by people who read as little as possible, so every screen leads with a single large action and states its result in plain words with an icon. It refuses the category default of a dark "AI scanner" with neon boxes. The camera is the one dark place, because a viewfinder should be.

Density is relaxed: a 20dp screen gutter, 24dp between blocks, rows at least 48dp tall and settings rows at 64dp. Depth is quiet: a hairline on every card, a 6% forest shadow under it in light, none in dark. Colour is mostly neutral. Forest marks actions and the active state. The three status colours appear only where a pod's condition is being stated, and always with their own icon shape.

The visual direction is the owner's prototype recording, which is binding (PRODUCT.md). Type is Inter for everything, with Inter Display SemiBold for headlines and large numbers. Icons are Feather line icons at a 1.75 stroke, plus Lucide's scan-line for Scan.

**Key Characteristics:**
- One tinted ground (`ground`), white cards (`card-white`) with a hairline and a soft low shadow.
- Forest (`forest`) for the primary action, links and the active tab. Sage (`sage`) takes that role in dark.
- Pale status tint chips with icon plus label. Solid status fills are used only for box labels on photos.
- Three radii only: 8dp for controls, 16dp for cards and photos, full for round things.
- Text scales with the system font up to 1.6×. Layouts reflow instead of truncating.
- A full-screen dark camera with floating controls on dark scrims and a dashed oval guide.

## Colors

A green-tinted neutral world with one forest accent and three status hues that are reserved for meaning.

### Primary
- **Field Forest** (`forest`, `Palette.forest700`): primary button fill, filter chips when selected, the Scan tab pill, the active tab icon and label, links, text on the healthy tint, and the focus colour in light.
- **Deep Forest Ink** (`forest-ink`, `Palette.forest900`): all body and heading text in light, the pressed fill of the primary button, and the text on healthy and early box labels. The 6% card shadow is tinted with it.
- **Leaf Tint** (`leaf`, `Palette.leaf`): the Home hero card, the active tab pill, selected rows and segments, the settings icon discs, and the pressed fill of secondary and ghost buttons.
- **Sage** (`sage`, `Palette.sage500`): in dark it is the primary button fill, the Scan pill and the icon colour. In both themes it is the healthy box stroke.
- **Sage Ink** (`sage-ink`, `Palette.sageInk`): the default icon colour in light, and the healthy status icon.
- **Mist** (`mist`, `Palette.mist300`): the camera shutter ring, and in dark the link, focus, active tab and ghost/secondary text colour.

### Secondary (status)
These colours are for pod condition only. `StatusColors` gives each status a `stroke`, `ink`, `fill`/`onFill` and `tint`/`onTint`.
- **Healthy Sage** (`status-healthy`): box stroke. Chip: `healthy-tint` with `forest` text.
- **Early Amber** (`status-early`): box stroke and box label fill. On light grounds its icon uses `amber-ink`, because amber on white is 2.25:1. Chip: `early-tint` with `early-tint-ink`.
- **Infected Brick** (`status-infected`): box stroke, box label fill, and the light-theme infected icon. In dark the icon uses `brick-light`, because brick on near-black is 3.49:1. Chip: `infected-tint` with `infected-tint-ink`.
- **Sage Chip** (`sage-chip`): the healthy box label fill. `forest-ink` on `sage` is only 4.27:1, so the fill is a step lighter.

### Neutral
- **Leaf-Tinted Ground** (`ground`): every screen background in light.
- **Card White** (`card-white`): cards, the top bar, the tab bar, sheets, text fields, unselected filter chips and secondary buttons.
- **Pressed Surface** (`press-surface`, `YoloCColors.surface`): the pressed state of cards, rows and tabs, skeletons, and grouped areas.
- **Hairline** (`hairline`, `YoloCColors.border`): card borders, dividers, the top and tab bar rules. It groups things visually. It is not a control boundary.
- **Control Edge** (`control-edge`): the border of secondary buttons.
- **Control Outline** (`outline`, `Palette.light400`): the boundaries of controls that must pass 3:1, such as segmented controls, the no-pod chip, the low-confidence notice and the sheet handle.
- **Muted Ink** (`ink-muted`): secondary text, subtitles, captions under titles and the top-bar tagline.
- **Faint Ink** (`ink-faint`): meta text such as times, readout labels and inactive tab labels. It was darkened from #66706A so it passes 4.5:1 on the leaf tint.
- **Inverse Ink** (`inverse-ink`): the snackbar surface, the swipe-to-delete reveal, and the disc of destructive settings rows.
- **Disabled** (`disabled-surface`, `disabled-ink`): filled buttons when they are disabled. These are exempt from contrast checks.
- **Dark theme** (`*-dark` keys): `ground-dark` ground, `card-dark` cards and bars, `press-dark` pressed, `leaf-dark` selected, `hairline-dark` borders, `ink-dark`, `ink-muted-dark` and `ink-faint-dark` text, `outline-dark` control edges, `edge-dark` secondary button border, and `inverse-dark` for the snackbar. Status tints have `-dark` counterparts.
- **Camera Night** (`night`, `lens`, `lens-fill`, `lens-muted`): `CameraColors`, which are the same in both themes. `night` is the camera screen, and also the text on dark-theme primary buttons and on the white `onDark` button.

### Named Rules
**The Status Is Meaning Rule.** Amber, brick and the status sage appear only where a pod's condition is being stated: chips, box strokes, box labels, verdict icons and the early-detections stat. They never decorate.

**The Never Colour Alone Rule.** Each status has its own icon shape: check-circle for healthy, alert-triangle for early and alert-octagon for infected. Each also has a text label. "No pods" is neutral, an outlined chip with a search icon, so it never reads as healthy.

**The Tint Not Fill Rule.** On the app's own surfaces a status chip is a pale tint with dark status ink. The solid status fill appears only on box labels drawn over a photo, where it must hold against any picture.

**The Checked Pair Rule.** Every text and icon pair that a component uses is listed in `contrastPairs()` (Contrast.kt) and checked by ContrastTest: 4.5:1 for text, 3:1 for icons, outlines and focus. A new pairing goes into that list before it ships.

## Typography

**Display Font:** Inter Display SemiBold (`InterDisplay`, res/font/inter_display_semibold.ttf)
**Body Font:** Inter Regular, Medium and SemiBold (`Inter`, res/font/inter_regular, inter_medium, inter_semibold)

**Character:** One family in two optical cuts. Inter Display tightens the large lines (greeting, verdict, counts) so they read as calm statements, and Inter keeps everything else even and legible in sun.

All sizes are in sp, so they follow the system font size. `AppText` caps the growth at 1.6× (`capFontScale`). Line heights sit on the 4dp grid. Line boxes are centred with no trim, as in RN. Headline and Title are announced as headings.

### Hierarchy
- **Headline** (Inter Display 600, 28/34sp, −0.5sp): the screen title and the Home greeting.
- **Verdict** (Inter Display 600, 24/30sp, −0.4sp): the verdict line on Result and Scan Detail, the Home hero line, and the Home guide card title.
- **Title** (Inter 600, 18/24sp, −0.2sp): section, card and sheet titles, and the camera title.
- **Body Strong** (Inter 500, 16/24sp): row labels, snackbar text, the camera hint and emphasised lines.
- **Body** (Inter 400, 16/24sp): running text. This is the minimum size for running text.
- **Label** (Inter 600, 15/20sp): buttons, chips, segments and arrow links.
- **Caption** (Inter 500, 13/18sp): meta lines, tab labels, the top-bar tagline, small chips and box labels.
- **Data** (Inter 500, 17/24sp, tabular): numbers in running rows, such as confidence, counts and times.
- **Figure** (Inter Display 600, 28/32sp, tabular): readout strip values.
- **Readout** (Inter Display 600, 34/40sp, −0.8sp, tabular): Home's stat counts.

`TypeVariant.Overline` (Inter 600, 12/16sp, +1.1sp) remains in `YoloCType` only because the type scale was ported from RN. Nothing in the redesign uses it.

### Named Rules
**The No Kicker Rule.** There are no small all-caps labels above titles: no kickers, no eyebrows, no "Field guide · 0N" numbering. The owner removed them and the Overline component is deleted. The Home date is a plain muted Caption line.

**The Wrap Not Cut Rule.** Button labels may wrap to two lines and are centred. A status name in a chip is never cut short. Where a single line will not fit, the layout reflows.

## Layout

The layout is a single column on a 4dp grid. `ScreenContainer` pads the screen 20dp (`spacing.screen`) and spaces its blocks 24dp apart (`spacing.lg`). Tab screens start 24dp below the top bar. Home leaves 96dp at the bottom so the Undo snackbar never covers the footer. Inside cards the default padding is 16dp and the gap between children is 8dp. Lists such as recent scans, day groups and settings groups sit edge to edge inside one card and pad their own rows at 12dp × 16dp. Hairline dividers in a group are inset to the text column.

Every tappable target is at least 48dp (`TouchTarget`). The large button is 56dp, settings rows are 64dp and scan rows are 80dp.

**Chrome.** Tab screens have the top bar (56dp, white, hairline below) and the bottom tab bar (white, hairline above). The tabs are, in order, Home, History, Scan, Guide and Settings. Scan sits in the centre. The camera is full screen with no top bar or tab bar.

**Reflow, not truncation.** There are no width breakpoints. Layout responds to font scale and string length:
- Above 1.15× font scale the top-bar tagline hides, and the Offline badge stays.
- Above 1.2× the segmented control drops its icons. The selected check stays.
- The Home hero measures its button label. If the label will not fit beside the 92dp photo, the photo moves above the text as an 88dp banner.
- History's title and New scan button sit in a `FlowRow`, so the button drops under the title when it does not fit.
- The camera title may take two lines beside the Photo/Live switch.

**Languages.** The app ships in English and Filipino (`values-fil`). The Filipino is a draft that a native speaker still has to check. Filipino strings run longer, which is one reason for the wrap and reflow rules above.

## Elevation & Depth

Depth comes from hairlines and the tinted ground, plus two shadows. Material's tonal elevation is switched off (`surfaceTint = Transparent`, `tonalElevation = 0`), so no tint shows through.

### Shadow Vocabulary
- **Card shadow** (`Modifier.cardShadow`: y 2dp, blur 12dp, `forest-ink` at 6%): used under cards, the hero and settings groups, in light only. In dark `cardShadow` is transparent and the hairline carries the edge.
- **Floating shadow** (`Modifier.floatingShadow`: y 6dp, blur 20dp, `night` at 18% in light and black at 50% in dark): used for things that float over content, such as the snackbar and the sheet panel (the modal sheet itself sits on the scrim with no tonal elevation).
- **Camera scrims** (`CameraColors.scrim`, black at 60%; `Modifier.scrim` in CameraScreen.kt): full strength behind the top row and the controls, fading to clear over the last 24dp toward the picture. Contrast.kt checks every camera control against a white scene seen through it, the worst case. Text over the live picture also gets a soft 4dp black halo at 80%.

### Named Rules
**The Quiet Lift Rule.** Cards lift only by their hairline and a 6% shadow. Nothing else casts a shadow unless it floats over other content.

## Shapes

There are three radii and no others (`Radii`):
- **8dp** (`rounded.sm`): buttons, inputs, chips, the tab pills, box labels, thumbnails, the gallery button and the flash toggle.
- **16dp** (`rounded.md`): cards, the hero, photos (`ImageWithBoxes`, Guide covers use 8dp inside a 16dp card), settings groups, sheets (top corners) and the History range options.
- **full** (`rounded.full`): the shutter, round icon buttons such as the theme toggle, status dots, settings icon discs, the sheet handle and pill text fields.

Borders are 1dp. Settings group dividers and thumbnail edges use a true device hairline. Bounding boxes use a 3dp status stroke inside a 1dp white halo, and 5dp when selected (`BoxStroke`). Dashed lines appear twice: the camera's oval guide (1.5dp, 6/5dp dashes, white at 85%) and the Guide's draft-note frame (1dp, 3/3 dashes, hairline).

Photos are cropped to fill (16:9 on Guide cards). The pod outline on Home's guide card is drawn in code (`PodMark`: a 76dp leaf-tint disc with a 1.75dp sage-ink outline).

## Components

Every press is a colour change with no ripple (`rememberPress`, `indication = null`).

### Buttons
Buttons are solid and plain: one clear tap target per intent.
- **Shape:** gently rounded (8dp). The height is 48dp, or 56dp for `ButtonSize.Lg`. Horizontal padding is 24dp (12dp for Ghost). An optional 20dp icon sits 8dp before the label.
- **Primary:** `forest` fill with white Label text, pressing to `forest-ink`. In dark it is a `sage` fill with `night` text, pressing to `mist`.
- **Secondary:** white with `forest` text and a `control-edge` border, pressing to `leaf`.
- **Ghost:** a transparent text button in `forest`, pressing to `leaf`.
- **OnDark:** white with `night` text, for the camera's lens panels.
- **Disabled / loading:** filled variants go to `disabled-surface` and `disabled-ink`. Loading swaps the icon for a 20dp spinner.
- **Labels:** wrap to at most two lines, centred.

### Chips
- **StatusChip:** `status.tint` background, a 16dp status icon and a Label in `status.onTint`. The `Sm` size is 24dp tall with a 14dp icon and Caption text. Md is 32dp. An optional confidence is shown as "Name 91%".
- **VerdictChip:** a saved scan's verdict. Pod verdicts reuse StatusChip. "No pods" is white with an `outline` border, a search icon and `forest-ink` text.
- **Filter chips (History):** "All" and one chip per result, combinable, 48dp tall. Off is white with a hairline. On is a solid `forest` fill with white text. The Date and Plot pills follow the same pattern with a chevron.
- **Box labels:** the solid `status.fill` with `onFill` Caption text, 8dp corners and a 1dp white halo, placed above the box on the photo.

### Cards / Containers
- **Corner Style:** softly rounded (16dp).
- **Background:** `card-white` (`card-dark` in dark). A pressable card goes to `press-surface` while pressed.
- **Shadow Strategy:** the card shadow in light. See Elevation & Depth.
- **Border:** a 1dp hairline, always.
- **Internal Padding:** 16dp by default, and 0 for edge-to-edge row lists.
- **Hero card:** a `leaf` card with no border, Verdict text, a Caption body and a primary button. The 92dp photo is on the right, or above at large text.
- **Stat cards:** two equal cards with an 18dp icon, a Caption label, a Readout number and "last 7 days" in faint Caption. The early-detections card uses the early ink icon.

### Inputs / Fields
- **Style:** a white field with a 1dp hairline, 8dp corners (or fully round for the `pill` variant), 48dp minimum height, 16dp side padding, Body text and a muted placeholder.
- **Focus:** the cursor is drawn in `focus` (`forest`, or `mist` in dark). There is no glow and no border change.
- **Switches:** Material `Switch` coloured from tokens. On is a `forest` track. Off is a `hairline` track with an `outline` edge. The row is the control.
- **Segmented control:** a 1dp `outline` frame with outline dividers. The selected segment is `leaf` with a check icon in place of its own icon, so the change is in shape as well as colour.

### Navigation
- **Top bar:** white, 56dp, with a hairline below. It holds the Caption tagline "Your field companion" in muted ink, the Offline ready badge (a 28dp hairline-bordered pill with a 7dp healthy dot and muted Caption text), and a 48dp round sun/moon theme switch.
- **Tab bar:** white with a hairline above. Each tab is 64dp tall with a 22dp icon over a Caption label. The active tab gets a 48 × 32dp `leaf` pill, the `forest` colour and a SemiBold label, so it never relies on colour alone. Inactive tabs are `ink-faint`.
- **Scan tab:** the main action. It is a solid 56 × 32dp `forest` pill with a white 24dp scan-line icon in the centre slot. It is announced as a button, never as "selected", and is always filled.
- **Arrow links:** Label text in `link` with a trailing 16dp arrow. They dim to 60% while pressed.

### Lists and Settings
- **Scan row:** a 56dp thumbnail with 8dp corners and a hairline edge. Beside it, what was found (Body Strong) and the time (muted Caption) on one line, then the VerdictChip, then plot and notes in muted Caption, then a faint chevron.
- **Settings group:** one white card. Each row has a 40dp `leaf` icon disc with a 20dp `sage-ink` icon, a Body Strong label, a muted Caption detail and a chevron or switch. A destructive row swaps the disc for `inverse-ink` with white icon, and never uses a status colour.

### Feedback
- **Snackbar (Toast):** an `inverse-ink` surface with 8dp corners, 56dp tall, with the floating shadow. It holds a tone icon (info, check or alert-circle) in `mist`, Body Strong text, and an optional underlined Label action such as Undo. It fades in while sliding down a third of its height over 200ms, and fades out over 150ms; with reduce motion it appears and leaves at once.
- **Bottom sheet:** opens fully, never half way. `card-white` (raised surface) with 16dp top corners, a 32 × 4dp `outline` handle, a Title header with a 48dp close button, and content that scrolls inside the sheet.
- **Low-confidence notice:** a `press-surface` panel with an `outline` border, a sun icon and Body Strong text, announced politely.

### Camera (signature)
The camera is the one dark surface. The preview fills the screen on `night` and has no tab bar.
- **Top row:** over the top scrim, a 48dp close X, the Title "Scan a pod" (up to two lines, with a text halo) and the Photo/Live mode switch (a white selected segment on a `lens` track).
- **Guide:** a dashed oval, 62% of the width with a 0.62 aspect ratio, in white at 85%. It is decorative, and the hint below says the same in words.
- **Controls:** over the bottom scrim, a Body Strong hint, then a 52dp gallery thumbnail (8dp corners, `lens-fill`, a white 32% edge), the shutter and a 48dp flash toggle (8dp corners, white 10% fill when off, solid white with a `lens` icon when on, and zap or zap-off by shape). Under them is a Caption line saying the photo is checked privately on the phone.
- **Shutter:** 76dp, a 4dp `mist` ring, a 4dp gap and a white core. The core scales to 0.86 in 100ms on press, and snaps instead of animating under reduce motion. A black flash fades over 200ms when the photo is taken, and is skipped under reduce motion.

### Imagery
Only the owner's two clean "pods on the tree" photos ship: `photo_cacao_cluster` and `photo_cacao_column` (docs/ASSETS.md). Both are decorative and skipped by screen readers. They appear on the Home hero, the Guide covers and the onboarding panels.

### Motion
Motion is purposeful only, 100 to 250ms (`Motion.PRESS` 100, `FAST` 150, `BASE` 200, `SLOW` 250). Uses: screen fades (200ms, none between tabs), the History header folding while the list scrolls (200ms), onboarding step fades (250ms), the shutter and the snackbar. The shutter, the History header, onboarding and the snackbar check `rememberReduceMotion()` and drop the animation.

## Do's and Don'ts

### Do:
- **Do** read every colour through `YoloCTheme.colors` and every size through `Spacing`, `Radii`, `TouchTarget` and `YoloCType`. Never use a raw hex or dp in a screen.
- **Do** show status as icon plus label on a pale tint chip (`StatusChip`). Keep the solid `status.fill` for box labels on photos.
- **Do** give every new text or icon pairing an entry in `contrastPairs()` so ContrastTest checks it at 4.5:1 for text and 3:1 for icons.
- **Do** make the next useful action the biggest thing on the screen: one primary `forest` button, with secondary and ghost buttons for the rest.
- **Do** keep touch targets at 48dp or more, and let labels wrap to two lines rather than cut.
- **Do** reflow when text grows or a language runs long: move the photo above, drop the button under the title, or hide the tagline.
- **Do** put list rows edge to edge inside one white card, with hairline dividers inset to the text.
- **Do** gate every animation on `rememberReduceMotion()` and keep durations within the `Motion` tokens.

### Don't:
- **Don't** add small all-caps kicker, eyebrow or overline labels above titles. `TypeVariant.Overline` has no use in the redesign.
- **Don't** use the pod photos as examples of a disease stage. They show healthy pods and are decorative.
- **Don't** use amber, brick or the status sage for decoration, errors or destructive actions. Destructive settings rows use the inverse surface.
- **Don't** show a status by colour alone, or let "No pods" look healthy.
- **Don't** add a ripple, tonal elevation or a shadow to anything that does not float. Cards get the hairline and the 6% card shadow only.
- **Don't** introduce a fourth corner radius. Use 8dp for controls, 16dp for cards and photos, and full for round things.
- **Don't** put the tab bar or top bar on the camera, or turn the camera into a light screen.
- **Don't** truncate a status name, a verdict or a button label with an ellipsis.
