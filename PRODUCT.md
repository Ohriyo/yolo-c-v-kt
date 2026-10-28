# Product

<!-- impeccable:product-schema 1 -->

Derived from the RN app's PRODUCT.md (C:\Users\RIO\Desktop\yolo-c\PRODUCT.md, 2026-10-15) and
the UI redesign brief of 2026-10-28 (the prototype recording YOLO-C-UI-GUIDE.mp4). This is the
Kotlin port; the redesign is Kotlin first, the RN app may follow later. Product truth below is
unchanged from RN except where marked "Redesign 2026-10-28".

## Platform

android

## Users

Cacao farmers, farm technicians and agriculture students. They use the app in the field, often
with weak or no signal, in bright sun, sometimes with dirty or gloved hands. Their job is to
photograph cacao pods and quickly learn whether any show Frosty Pod Rot, especially early-stage
signs, so they can act before the disease spreads. Reading is kept to a minimum.

## Product Purpose

YOLO-C (thesis: "A Mobile-Based Application for Early-Stage Detection of Frosty Pod Rot Using
YOLOv11") runs a YOLO11s detector on the phone to mark each pod in a photo as healthy, early
stage or fully infected. Scans are saved locally so users can review what they found over time,
per plot. Success means a farmer can scan, read the verdict, and find past scans without help
and without internet.

The Kotlin app is also one half of the thesis comparison with the RN app: behaviour, results
and performance are compared; deliberate differences are recorded in docs/PARITY.md.

## Positioning

Fully offline, on-device screening built for one disease and one crop. Nothing leaves the phone.
It is honest about its limits: it is a screening aid, not a diagnosis.

## Operating Context

- Outdoors, sunlit screens, one-handed use, intermittent attention.
- Offline is the normal state; the app never shows "no internet" errors. (Redesign 2026-10-28:
  an "Offline ready" badge states this positively in the top bar.)
- Scans can be grouped by farm plot. History is reviewed later, often on the same day or across
  a season (hundreds of scans per device).

## Capabilities and Constraints

- Camera or gallery photo, local LiteRT inference, boxes per pod with confidence, verdict =
  most severe class present. Users can correct a pod's label (kept for retraining).
- Local Room history with notes, plots, soft delete, CSV export, retraining zip export.
- Model is swappable via model.config.json; class names, colors and advice come from the
  classes table, never hardcoded in screens.
- Redesign 2026-10-28, new features:
  - The user's first name, optional, stored only on the phone: asked (skippable) in
    onboarding, changeable in Settings. Home greets by time of day ("Good morning, Juan.");
    without a name it says "Good morning."
  - Languages: English and Filipino. Filipino strings are drafted by Claude and must be checked
    by a native speaker before release (mark them so). Chosen in Settings.
  - Theme: one Dark mode switch in Settings (and a theme button in the top bar) replaces the
    System / Light / Dark choice.
  - Home stats: "Total scans" and "Early detections" over the last 7 days (rolling), replacing
    the three 30-day result counts.
  - Navigation: five tabs, Home, History, Scan, Guide, Settings. Learn is renamed Guide. Scan
    sits in the middle, highlighted as the main action (the user's choice over the prototype's
    plain Scan tab).
- Out of scope: accounts, cloud sync, iOS, languages beyond English and Filipino.

## Brand Commitments

- Name: YOLO-C. Voice: plain, calm, practical; short sentences; no jargon. Tagline in the
  brief: "Your field companion".
- Redesign 2026-10-28: the visual direction is the prototype recording
  (C:\Users\RIO\Videos\Screen Recordings\YOLO-C-UI-GUIDE.mp4), which replaces the RN app's
  "Field instrument" direction (Barlow) for the Kotlin app. The prototype is binding.
- Disclaimer, verbatim: "This app is a screening aid and does not replace an expert inspection."

## Evidence on Hand

- Model metrics (show honestly): test mAP50 0.727; early-stage test mAP50 0.614, recall about
  0.60 (the model misses roughly 4 in 10 early-stage pods).
- Sample photos with reference detections in app/src/debug/assets/sample (debug builds only).
- Cacao photos for Home's hero and the Guide cards: the user will supply them; until then
  clearly marked placeholders. Do not use photos the user has not supplied.
- No agronomic advice has been verified yet: disease and treatment text is placeholder. Do not
  invent advice.

## Product Principles

1. Screening aid, never a diagnosis: always show confidence and the uncertainty.
2. Task first: the next useful action is always the biggest thing on screen.
3. Offline and private by default.
4. Field-ready over pretty: legible in sun, large targets, forgiving of mistakes (undo).
5. Collect corrections: user labels are valuable future training data.

## Accessibility & Inclusion

Touch targets at least 48dp, WCAG AA text contrast, status never shown by color alone (always an
icon and a label), respect the system reduce-motion setting, body text at least 16.
