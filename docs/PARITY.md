# Parity results: RN vs Kotlin

The numbers from both apps on the same phone (TECNO BG6, Unisoc T606, 4 GB RAM), kept in one
place as the port guide's Step 6 asks. RN values are from the RN repo's
`assets/sample/README.md` ("Device results (2026-10-15)") and the guide's measurement table.

## How to collect the Kotlin numbers

1. Connect the phone over USB (USB debugging on) and run the debug app. It opens on
   **Model test (dev)**.
2. Backend **CPU (default)** → **Run all golden images**. Copy each line into the golden table.
3. Sample **1** → **Benchmark (5 runs)**. Copy the medians into the performance table.
4. Repeat 2–3 with **CPU, 4 threads** and **GPU** for the experiments table.
5. Optional, same check as a test: `./gradlew connectedDebugAndroidTest`. Results are in Logcat
   under the tag `golden`. The app logs every run under the tag `inference`.

## Golden test (CPU, default options: the parity run)

Tolerance: IoU ≥ 0.9, |Δ confidence| ≤ 0.05, no extra boxes. Reference: `expected_tflite.json`.

| Image     | RN (2026-10-15)                                                          | Kotlin | Notes |
| --------- | ------------------------------------------------------------------------ | ------ | ----- |
| golden_01 | PASS, IoU 0.995–0.997, confidence within 0.008                           | PASS, 2/2 matched, worst IoU 0.996, 0 extra |  |
| golden_02 | FAIL, infected pod matches (IoU 0.981); small healthy pod 0.276 vs 0.495 | PASS, 2/2 matched, worst IoU 0.998, 0 extra | RN failed this image; Kotlin passes |
| golden_03 | PASS, IoU 0.998–1.000                                                    | PASS, 2/2 matched, worst IoU 0.999, 0 extra |  |
| golden_04 | PASS, IoU 0.955–0.998, confidence within 0.026                           | PASS, 2/2 matched, worst IoU 0.994, 0 extra |  |
| golden_05 | PASS, IoU 0.992–0.997, confidence within 0.035                           | PASS, 2/2 matched, worst IoU 0.998, 0 extra |  |

Kotlin: debug build, 2026-10-27, CPU with default options. Same result against
`expected_pt.json`. The screen shows the worst IoU only; confidence deltas are within the 0.05
tolerance (all pairs passed) but were not recorded per image.

Expected difference: Kotlin reads the resized bitmap directly instead of re-encoding a JPEG, so
IoU and confidence deltas may differ slightly from RN.

## Performance on golden_01 (CPU, default options, median of 5)

| Measure                          | RN (dev build, 2026-10-15)    | Kotlin (debug build, 2026-10-27) |
| -------------------------------- | ----------------------------- | -------------------------------- |
| Model load                       | 4.1 s (downloaded from Metro) | 261 ms (memory-mapped from the APK) |
| Inference (`run` only)           | 1375 ms                       | **2168 ms** (2171, 2168, 2174, 2168, 2167) |
| Preprocess                       | 2.2–2.7 s                     | 33–40 ms warm (341 ms on the first run after launch) |
| Postprocess                      | about 15 ms                   | about 7.7 ms warm (89 ms on the first run) |
| End to end                       | 3.8 s                         | **2210 ms** median total |

The first run after launch is slower (preprocess 341 ms, postprocess 89 ms, total 2607 ms) while the
debug build's code warms up; later runs settle at the numbers above. Inference across the golden
images: 2166–2190 ms.

Findings so far:
- Kotlin preprocessing is about 2.2 s faster (it reads the bitmap directly; RN re-encodes a JPEG and
  decodes it in JS), so end to end Kotlin is faster: 2.2 s vs 3.8 s.
- Kotlin CPU inference is about 800 ms slower than RN on the same model and phone (2168 vs 1375 ms).
  Setting 4 threads changes nothing (below), so thread count is not the cause. Still open: what
  react-native-fast-tflite configures that the LiteRT `Interpreter` defaults do not (for example the
  XNNPACK delegate or its flags). Both apps were debug builds; release numbers come later.

## Experiments (labelled separately from the parity run)

All on golden_01, median of 5, debug build, 2026-10-27.

| Backend             | Load / compile (ms) | Median inference (ms)                  | Median total (ms) | Golden PASS | Worst IoU per image (01–05)     |
| ------------------- | ------------------- | -------------------------------------- | ----------------- | ----------- | ------------------------------- |
| CPU (default)       | 261                 | 2168 (2171, 2168, 2174, 2168, 2167)    | 2210              | 5/5         | 0.996, 0.998, 0.999, 0.994, 0.998 |
| CPU, 4 threads      | 169                 | 2168 (2167, 2169, 2168, 2168, 2170)    | 2211              | 5/5         | 0.996, 0.998, 0.999, 0.994, 0.998 |
| GPU (CompiledModel) | 3457                | **540** (564, 539, 570, 537, 540)      | 585               | 5/5         | 0.996, 0.989, 0.998, 0.995, 0.997 |

- 4 threads gives the same inference time as the defaults, so the default `Interpreter` options
  already use the cores this phone gives it (or the bottleneck is elsewhere).
- The GPU is about 4× faster than the CPU and still passes all five images against both Python
  references. Its boxes differ slightly from the CPU's (golden_02 worst IoU 0.989 vs 0.998), as
  expected from GPU float arithmetic. Compiling for the GPU costs about 3.5 s once per launch.
- The guide keeps photo scans on the CPU because that path is the verified parity path; the GPU
  results make it a candidate for photos too, as a separate decision.

RN's live mode uses the `android-gpu` delegate; Kotlin uses LiteRT's `CompiledModel` with
`Accelerator.GPU` (the Interpreter GPU delegate is LiteRT v1 only).

## Deliberate behaviour differences

Places where the Kotlin port does not copy RN on purpose, because RN's behaviour is a bug or an
edge case that would give a wrong result. Everything else follows RN commit `1e7aabe`.

| Where (Kotlin) | RN | Kotlin | Why |
| -------------- | -- | ------ | --- |
| `AnalyzePhoto`, `correctResultDetection` (Step 4.2) | A class missing from the `classes` table counts as severity 0 (healthy) | Fails with an error | Only a mismatched model and database can cause it; calling an unknown pod healthy would hide a possible infection |
| `rangeStart` for 7 / 30 days (Step 4.2) | Start of today minus 6 or 29 × 24 h | Start of today minus 6 or 29 calendar days | Right after a daylight-saving change the 24-hour version is off by a day |
| `SettingsStore.hydrate` (Step 4.3) | A load overwrites every cached setting with what it read | A setting changed while the load ran keeps the newer value | Otherwise the screen shows the old value until the next launch; at startup the UI waits for the load, so only a retry can race |
| `ScanSession.setResult` (Step 4.3) | Stores any result | Ignores a result for a photo the session no longer holds | A slow analysis finishing after Back or "Choose another" would show the wrong photo's pods |
| `ScanLibrary.deleteScan` (Step 4.3) | Offers Undo even when no row was deleted | A scan that is already gone only refreshes the lists | A double tap would replace the real Undo with an empty one |
| `HistoryViewModel` (Step 4.4) | Rows hidden by a delete reappear whenever the row list changes ("load more", or a reload started by another change) | A row stays hidden while its delete is still writing; the first reload after that shows the database | Otherwise the deleted row briefly comes back |
| `HistoryViewModel` (Step 4.4) | A Home count chip opens History with that result over all time | Also sets "Last 30 days" | The chip counts the last 30 days; RN's own comment says the chip and the list should agree |
| `HistoryViewModel.loadMore` (Step 4.4) | Appends the page even if a reload replaced the list meanwhile | Drops the page when the list no longer ends at its cursor | A scan saved during the load would otherwise make one row disappear from the list |
| `HistoryViewModel` (Step 4.4) | "Clear filters" or a request from Home / Plots within 250 ms of typing re-applies the typed search | Clears the pending search | The new filter should be exactly what was asked for |
| `ScanDetailViewModel` (Step 4.4) | Classes load once; a failure stays until the screen reopens | Retried on each reload (Retry) | Otherwise the labels and the Correct label sheet never fill in |
| `LiveViewModel.analyze` (Step 4.4) | The GPU resizer pads live frames with black | Pads with `letterboxPadValue` (gray 114), like photos | Same input as the verified photo pipeline and the model's training; RN used black only because its resizer offers nothing else. Speed is unaffected |
| `ResultViewModel` (Step 4.4) | Back during a save still offers Discard; the save finishes anyway and opens the scan | Back and Discard do nothing while a save is writing | A scan the user discarded would end up in History |
| `CameraViewModel` (Step 4.4) | A capture and a gallery pick can run at once | One at a time | The second photo would replace (and delete) the first |
| `SettingsViewModel.askClear` (Step 4.4) | If the storage counts are missing (still loading, or failed), Clear all data says "nothing to clear" | Failed counts still open the confirm dialog (without counts); while loading, nothing happens | After a failed count the user could never clear their data |
| `PlotsViewModel.add` (Step 4.4) | A double tap, or Add before the list loads, can insert the same name twice | One add at a time, and only once the list (the duplicate check) has loaded | The table has no unique index; the screen is the only duplicate check |
| `RetrainingExportViewModel` (Step 4.4) | A failed count leaves the sheet counting forever | Says the count failed (`sizingFailed`) | The user could not tell a failure from a slow count |
| `refreshedNow` (RN `useNow`) (Step 4.4) | Ignores changes up to a minute | Also refreshes when the local day changed or the clock went back | "Today" labels after midnight |
| `BottomSheet` (Step 5) | Closes with back, a scrim tap or the close button | Also closes when dragged down | It is Material 3's `ModalBottomSheet` (guide 5.2); the drag is the platform's own gesture |
| `SwipeToDelete` (Step 5) | Deletes past 40 % of the width, or on a flick faster than 0.9 px/ms after 40 px | Deletes past 40 % of the width, or on Material 3's own flick speed | It is Material 3's `SwipeToDismissBox` (guide 5.2), which sets its own flick threshold |

## Step 6 measurements (filled in as the port progresses)

| Measure                                                  | RN                                                    | Kotlin |
| -------------------------------------------------------- | ----------------------------------------------------- | ------ |
| Release APK size                                         |                                                       |        |
| Cold start to Home (ms)                                  |                                                       |        |
| Model load (ms)                                          |                                                       |        |
| Preprocess / inference / postprocess per photo (median of 5, golden_01) | 2.2–2.7 s / 1375 ms / 15 ms (dev build, 2026-10-15) | |
| Live: detections/s and camera fps, GPU and CPU           |                                                       |        |
| Peak memory during analysis (Android Studio profiler)    |                                                       |        |
| Clean build time / incremental build time                |                                                       |        |
| Lines of code (app code, tests)                          |                                                       |        |
| Permissions in the release manifest                      | CAMERA, location, ACCESS_NETWORK_STATE                |        |
