# Shipping images and their origin

Every photo or drawing the app ships, where it came from, and where it is used. Icons are listed
in their own files' headers (Feather, MIT; Lucide, ISC).

| File (app/src/main/res/drawable-nodpi) | Origin | Used on |
| -------------------------------------- | ------ | ------- |
| `photo_cacao_cluster.webp` | Supplied by the project owner on 2026-10-28 (`cacao photos ui/2.jpg`, 720×404), converted to WebP | Guide card (photo tips), onboarding (scan, primer, aid fallback) |
| `photo_cacao_column.webp` | Supplied by the project owner on 2026-10-28 (`cacao photos ui/4.jpg`, 541×360), converted to WebP | Home hero, Guide cards (stages, how the app works), onboarding (offline, name) |

The photos are decorative and show pods without Frosty Pod Rot, so they are never used as
examples of a disease stage, and screen readers skip them. The owner is responsible for the
right to use them; replace a file here with one of the same name and size class to change it.

The pod outline on Home's guide card is drawn in code (`PodMark` in HomeScreen.kt).

| File | Origin | Used on |
| ---- | ------ | ------- |
| `res/mipmap-*/ic_launcher.webp`, `ic_launcher_round.webp` | Android Studio's new-project template (the default launcher icon), from the project setup in commit 070c670; the adaptive icon is `mipmap-anydpi-v26` | Launcher, below Android 8 |

This file is the provenance record for every raster above. The design tool's provenance scan
(`impeccable embed-prompt --scan`) cannot embed into WebP and would write a `.json` sidecar beside
each file instead; Android refuses non-image files in `res/`, so the scan lists these files as
missing and this table stands in for the sidecars.
