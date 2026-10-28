# Shipping images and their origin

Every photo or drawing the app ships, where it came from, and where it is used. Icons are listed
in their own files' headers (Feather, MIT; Lucide, ISC).

| File (app/src/main/res/drawable-nodpi) | Origin | Used on |
| -------------------------------------- | ------ | ------- |
| `photo_cacao_single.webp` | Supplied by the project owner on 2026-10-28 (`cacao photos ui/1.jpg`, 1280×719), converted to WebP | Home hero |
| `photo_cacao_cluster.webp` | Supplied by the project owner on 2026-10-28 (`cacao photos ui/2.jpg`, 720×404), converted to WebP | Guide card |
| `photo_cacao_opened.webp` | Supplied by the project owner on 2026-10-28 (`cacao photos ui/3.jpg`, 540×360), converted to WebP | Guide card |
| `photo_cacao_column.webp` | Supplied by the project owner on 2026-10-28 (`cacao photos ui/4.jpg`, 541×360), converted to WebP | Guide card |

The photos are decorative and show pods without Frosty Pod Rot, so they are never used as
examples of a disease stage, and screen readers skip them. The owner is responsible for the
right to use them; replace a file here with one of the same name and size class to change it.

The pod outline on Home's guide card is drawn in code (`PodMark` in HomeScreen.kt).
