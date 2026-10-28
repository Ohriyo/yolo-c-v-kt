---
version: 1
slug: "app-src-main-java-com-yoloc-app-screens"
primary_target: "app/src/main/java/com/yoloc/app/screens"
related_targets: []
---

# Surface brief: the whole Kotlin app (redesign 2026-10-28)

Scope: every screen of the Kotlin app. Mode: Operate (Guide topics are Read).
Audience and job: see PRODUCT.md. Constraints: native Material controls, 48dp targets, AA
contrast, status by icon + label, reduce motion, offline. RN parity: the new look is a recorded
deliberate difference (docs/PARITY.md); behaviour stays the same except the PRODUCT.md
"Redesign 2026-10-28" features.

## Direction contract

THESIS: A calm field companion. One pale, leaf-tinted ground with crisp white cards; the
category default it refuses is the dark "AI scanner" with neon boxes. The brief (the user's
prototype recording) is pinned and binding.

OWN-WORLD: Ground #F6F8F5, leaf tint #EBF1E6 (hero, active tab pill, selected), white cards
with a hairline #E8ECE6 border and a soft low shadow, forest #375534 for primary actions and
the active state, ink #0F2A1D. Status chips are pale tints with an icon and dark status ink.
Inter for text, Inter Display SemiBold for headlines. Line icons (Feather).

STORY: The farmer opens to a greeting and one clear action, sees how many pods were checked
and early detections this week, finds recent scans, and learns the signs from photo cards.

FIRST VIEWPORT (Home): top bar "Your field companion", Offline ready badge, theme button; date
line; "Good morning, <name>."; the hero card (tint, headline "One pod at a time. Protect your
harvest.", Scan a pod button, pod photo on the right); two stat cards; recent scans card. Five
plain tabs: Home, Scan, History, Guide, Settings, active one on a tint pill.

FORM: brief-pinned (the prototype), no roll. seed: none (unavailable)

Signature move: the photo-led hero card, the tint pill tab and the pale status chips repeated
on every list, so a status reads the same everywhere.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
