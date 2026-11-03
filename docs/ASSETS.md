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

The logo drawables below are VectorDrawables in `res/drawable`, converted from the SVGs in
`design/logo/` (a source folder only; no SVG goes in `res/`).

| File (app/src/main/res/drawable) | Origin | Used on |
| -------------------------------- | ------ | ------- |
| `ic_launcher_foreground.xml` | Owner's logo, recolored to the YOLO-C palette and vectorized with Claude (claude.ai) on 2026-10-31, sources in design/logo/ (`ic_launcher_foreground.svg`) | Launcher icon foreground (`mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`); the splash icon and the launch intro's mark in light mode |
| `ic_launcher_monochrome.xml` | Owner's logo, recolored to the YOLO-C palette and vectorized with Claude (claude.ai) on 2026-10-31, sources in design/logo/ (`ic_launcher_monochrome.svg`) | Launcher themed icon, Android 13+ |
| `ic_splash_dark.xml` | `ic_launcher_foreground.xml` recolored to the logo's dark tile (`yoloc_logo_dark_tile.svg`: Cream pod #E3EED4, Sage leaves #6B9071), 2026-10-31 | The splash icon and the launch intro's mark in dark mode |
| `ic_launcher_background.xml` | Solid Cream (#E3EED4), the ground of the logo's light tile | Launcher icon background |
| `yoloc_mark.xml` | Owner's logo, recolored to the YOLO-C palette and vectorized with Claude (claude.ai) on 2026-10-31, sources in design/logo/ (`yoloc_mark.svg`) | Processing's "Checking your pod" animation (its paths are named pod, ridges and leaves so the leaves can breathe on their own); not the launch intro, which uses the launcher foreground so it starts exactly on the splash icon |
| `yoloc_leaf.xml` | Owner's logo, recolored to the YOLO-C palette and vectorized with Claude (claude.ai) on 2026-10-31, sources in design/logo/ (`leaf_left.svg`) | The launch intro's four leaves |
| `yoloc_logo.xml` | Owner's logo, recolored to the YOLO-C palette and vectorized with Claude (claude.ai) on 2026-10-31, sources in design/logo/ (`yoloc_logo_primary.svg`) | Not used yet |

minSdk is 26, so there are no legacy `mipmap-*dpi` launcher bitmaps; the adaptive icon in
`mipmap-anydpi-v26` serves every device.

This file is the provenance record for every raster above. The design tool's provenance scan
(`impeccable embed-prompt --scan`) cannot embed into WebP and would write a `.json` sidecar beside
each file instead; Android refuses non-image files in `res/`, so the scan lists these files as
missing and this table stands in for the sidecars.
