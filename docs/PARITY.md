# Parity results: RN vs Kotlin

The numbers from both apps on the same phone (TECNO BG6, Unisoc T606, 4 GB RAM), kept in one
place as the port guide's Step 6 asks. RN values are from the RN repo's
`assets/sample/README.md` ("Device results (2026-10-15)") and the guide's measurement table.

## How to collect the Kotlin numbers

1. Connect the phone over USB (USB debugging on) and run the debug app. It opens on
   **Model test (dev)**.
2. Backend **CPU (parity)** → **Run all golden images**. Copy each line into the golden table.
3. Sample **1** → **Benchmark (5 runs)**. Copy the medians into the performance table.
4. Repeat 2–3 with **CPU, XNNPACK (LiteRT defaults)** and **GPU** for the experiments table.
5. Optional, same check as a test: `YoloDetectorGoldenTest`, one method per `am instrument` run on
   the TECNO (the runner closing its activity between tests lets the power manager freeze the app).
   Results are in Logcat under the tag `golden-v3`. The app logs every run under the tag `inference`.

## Model v3 (2026-11-03)

The app now runs `yolo_c_v3_640.tflite`. Every table below this section measured **v2**
(`yolo_c_v2_640_fp32.tflite`) and is kept as the record of the RN comparison. v2 scans keep their
`model_version_id`.

Photo scans use `YoloDetector`: `CompiledModel` on the GPU with CPU fallback. Golden test on the
TECNO BG6: 9 reference images at conf 0.25, IoU 0.45, all PASS on both accelerators.

| | GPU (default) | CPU (fallback) |
| - | ------------- | -------------- |
| Delegate | 355 of 355 nodes, `LITERT_CL` | 353 of 355 nodes, XNNPACK |
| Load, nothing cached | 3912 ms | 2799 ms |
| Load, GPU program cached | about 850 ms | not cached |
| Median inference (10 runs per image, after warm-up) | 539.3 ms | 2175.6 ms |
| Largest confidence difference | 0.0367 | 0.0449 (`IMG_1629.JPG`) |
| Largest box coordinate difference | 0.0015 | 0.0014 |

Live on the GPU, no pod in view: inference p50 575 ms, resize 40 ms, postprocess 7 ms, 1.27
detections/s, camera 5.2 fps. Both rates are below `LivePractical` (2/s, 24 fps).

`IMG_1629.JPG` has a borderline pod: re-saving the photo once as JPEG at quality 100, with no other
change, moves `fully_infected_pod` from 0.459 to 0.328 and `early_stage` from 0.405 to 0.541, which
changes its class at the shipped thresholds. Boxes do not move.

About, Guide and onboarding quote v3's validation metrics (243 images). Test metrics are not
available yet.

The model compiles when the Scan screen opens, not at launch, and is released when the app goes to
the background or memory runs critically low. LiteRT's GPU program cache
(`GpuOptions.serializationDir`, `modelCacheKey`, `serializeProgramCache`) takes a load from 4293 ms
to about 850 ms, of which about 540 ms is the warm-up inference. It holds 23.9 MB in `codeCacheDir`,
which the system clears on an app update. The key includes a CRC of the model because LiteRT's own
fingerprint only covers tensor sizes.

## Golden test (CPU: the parity run)

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

`GoldenDeviceTest` (debug build, 2026-10-31, run alone with `am instrument`) gives the same
5/5 PASS and records the confidence deltas. Worst IoU and largest |Δ confidence| per image:
golden_01 0.996 / 0.004, golden_02 0.998 / 0.034, golden_03 0.999 / 0.003,
golden_04 0.994 / 0.009, golden_05 0.998 / 0.002; no extra boxes. Inference in the test took
2182–2408 ms per image, in line with the 2168 ms median below.

Expected difference: Kotlin reads the resized bitmap directly instead of re-encoding a JPEG, so
IoU and confidence deltas may differ slightly from RN.

## Performance on golden_01 (CPU, median of 5)

Measured on 2026-10-27 with the Interpreter's default options (XNNPACK on), before the CPU
options changed; see "CPU inference gap" for the numbers with the parity options.

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
  The cause is the XNNPACK delegate, not Kotlin: see "CPU inference gap" below. Both apps were
  debug builds; release numbers come later.

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

### CPU inference gap (2026-10-31)

A throwaway on-device benchmark timed one `Interpreter.run` on a golden-sized input (2 warm-up
runs, median of 5) under different options, on LiteRT 2.3.0 (the Kotlin app) and, in a temporary
build, LiteRT 1.4.0 (the version react-native-fast-tflite 3.0.1 bundles). Debug builds.

| `Interpreter.Options`             | LiteRT 2.3.0 | LiteRT 1.4.0 |
| --------------------------------- | ------------ | ------------ |
| Defaults (XNNPACK on)             | 2202 ms      | 2160 ms      |
| XNNPACK on, 1 / 2 / 4 / 8 threads | 2171 / 2178 / 2168 / 2168 ms | 2155 / 2155 / 2150 / 2159 ms |
| XNNPACK off, default threads      | 2485 ms      | **1392 ms**  |
| XNNPACK off, 1 thread             | 2487 ms      | 2677 ms      |
| XNNPACK off, 2 threads            | 1561 ms      | 1743 ms      |
| XNNPACK off, 4 threads            | 1191 ms      | 1393 ms      |
| XNNPACK off, 6 threads            | 1029 ms      | 1214 ms      |
| XNNPACK off, 8 threads            | 960 ms       | 1130 ms      |

- With XNNPACK on, the thread count has no effect on this phone in either version: XNNPACK runs
  about as fast as one plain thread. The Java `Interpreter` turns XNNPACK on by default, which is
  why the Kotlin app sat at 2168 ms and why 4 threads changed nothing.
- On LiteRT 1.4.0 with XNNPACK off, the default thread count behaves like 4 threads (1392 ms),
  which matches RN's 1375 ms. fast-tflite calls the C API with default options
  (`TfLiteInterpreterOptionsCreate`, no thread count, no delegate on the CPU path), so RN most
  likely runs the built-in kernels on about 4 threads, without XNNPACK.
- LiteRT 2.3.0's built-in kernels are faster than 1.4.0's at every thread count, but its default
  thread count is 1 (2485 ms), so turning XNNPACK off alone is not enough there.

Decision (2026-10-31): photo scans and the live CPU fallback use XNNPACK off and 4 threads
(`ModelLoader.PARITY_THREADS`), the setup RN effectively runs. 8 threads would be faster (960 ms)
but also loads the six slow cores and is no longer like-for-like. The Model test keeps the old
setup as **CPU, XNNPACK (LiteRT defaults)**. `GoldenDeviceTest` with the parity options: 5/5 PASS
with the same worst IoU and confidence deltas as before, inference 1180–1225 ms per image warm
(1706 ms on the first image). Kotlin's remaining edge over RN (about 1190 vs 1375 ms) comes from
LiteRT 2.3.0's faster built-in kernels.

RN's live mode uses the `android-gpu` delegate; Kotlin uses LiteRT's `CompiledModel` with
`Accelerator.GPU` (the Interpreter GPU delegate is LiteRT v1 only).

## Deliberate behaviour differences

Places where the Kotlin port does not copy RN on purpose, because RN's behaviour is a bug or an
edge case that would give a wrong result. Everything else follows RN commit `1e7aabe`.

| Where (Kotlin) | RN | Kotlin | Why |
| -------------- | -- | ------ | --- |
| `DetectionSensitivity` (v3) | A confidence threshold slider, 10% to 90% | Strict, Standard, Sensitive. Standard is the config's per-class thresholds; the others shift every class by `sensitivity.shift`, none below `sensitivity.minConfThreshold` | v3 ships per-class thresholds, and a level means the same for any model |
| `scans.conf_thresholds` (v3, schema 2) | `conf_threshold`, one number | JSON of the threshold each class ran with. Old scans' single threshold was copied to every class, which is what ran | A scan records what actually filtered it |
| `summarizeDetections` (v3) | Uncertain when the best detection in the photo is low | Uncertain when the detection that decides the verdict is below `uncertainBelow`: the strongest of the most severe class. Recomputed when a label is corrected | A weak infected pod must not read as certain because a healthy pod beside it scores high |
| `postprocessByClass` (v3) | NMS in input pixels, then maps back with recomputed padding | Maps to the original image first, subtracting the padding actually applied, then NMS | The v3 brief's order. IoU is unchanged by the mapping except for clamped boxes, and the padding differs by at most a pixel |
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
| `YoloCNavHost` (Step 5) | Stack screens use the native stack's Android default animation; the header back arrow is the platform's | A 200 ms fade (`Motion.BASE`) for stack screens and the camera, none between tabs; Feather's arrow-left | Compose Navigation has no native-stack animation to copy; fade is RN's own choice for the camera and stays within the motion tokens |
| `NavActions.ifShown` (Step 5) | A fast double tap on Skip, a Learn topic, a scan row or Scan can open the next screen twice | Forward taps count only while the screen is fully shown | Back from the first copy landed on a second copy of the same screen |
| Home count chips (Step 5) | While the counts load or after they fail, a chip shows "–" but is read out as "0 scans" | Read out as the result name only until the count is known | A screen-reader user was told there were no infected scans |
| `ScanRow` in History (Step 5) | Screen readers hear the hint "Long-press for options. Swipe left to delete." | TalkBack offers the row's long-press ("Scan options") and its "Delete" action itself | Android announces long-press and custom actions natively; a swipe hint is wrong for TalkBack users, who cannot swipe a row |
| `CameraScreen` shutter flash (Step 5) | expo-camera's `animateShutter` flashes the preview dark on every capture | Same dark flash, skipped when the system's reduce-motion setting is on | The app's motion rule: animations respect reduce motion. The shutter's spinner still shows the capture |
| `SaveScanSheet` plot choice (Step 5) | The chosen plot stays chosen after it is deleted in Manage plots (Save then fails), and a double tap on Add plot adds it twice | A deleted plot falls back to "No plot", and one add runs at a time | Bugs in RN's sheet (a foreign-key failure reported as "free space", a duplicate plot); the fix changes nothing else |
| `PlotsScreen` opened from Result's save sheet (Step 5) | Tapping a plot is stopped by Result's `usePreventRemove`, and Stay / Discard / Save is asked over Plots | Result is shown again and asks there (`returnToUnsavedResult`) | Navigation Compose has no prevent-remove; without this the unsaved result would be dropped without asking |
| `SettingsRow` (Step 5) | The detail line is the button's accessibility hint | It is read after the label, in the same announcement | Compose has no hint separate from the label; TalkBack reads both either way |
| `SeedScreen` failure message (Step 5, dev only) | "See the Metro log" | "See the Logcat log" | The Kotlin app logs to Logcat; there is no Metro |

## Redesign (2026-10-28, Kotlin only)

The Kotlin app follows the user's UI prototype (the screen recording `YOLO-C-UI-GUIDE.mp4`)
instead of RN's "Field instrument" look; the RN app may get the same design later. Product
truth and the analysis are unchanged. Compare the two apps' UI with this in mind.

| Where (Kotlin) | RN | Kotlin | Part |
| -------------- | -- | ------ | ---- |
| Theme tokens | White ground, neutral surfaces, Barlow and Barlow Semi Condensed, corners 4 / 10 | Leaf-tinted ground `#F6F8F5`, white cards with a hairline and a soft shadow, Inter and Inter Display, corners 8 / 16 | 1 |
| Status chips | Solid status fill | Pale status tint with the status ink (same icon and label); box labels on photos keep the solid fill | 1 |
| Tab bar | Home, History, a filled Scan action, Learn, Settings | Home, History, Scan, Guide, Settings: Scan in the middle as a filled primary pill (the user's choice over the prototype's plain Scan tab, 2026-10-28), the active tab on a tint pill | 1 |
| Top bar on tab screens | None | "Your field companion", an "Offline ready" badge and a dark mode switch | 1 |
| Icons | Feather at stroke 2 | Feather at stroke 1.75, plus Lucide's scan-line for the Scan tab | 1 |
| Home | A heading, the Scan button, three 30-day count chips, recent scans, a photo tip | Date line and a greeting by part of the day (with the name), a hero card with a photo and Scan a pod, two stat cards (Total scans, Early detections) over the last 7 days, recent scans in one card, a card into the guide's stages topic, the photo tip with a link to the photos topic, and a footer naming the model | 2 |
| Name (onboarding primer, `user_name` setting) | None | An optional first name on the permission primer, saved in the key/value settings table (no schema change); Home greets with it | 2 |
| `HistoryRequest.ByVerdict` | Home opens History by result over 30 days | Home's stat cards open History over the last 7 days, for every result or early stage only | 2 |
| History | "History" with a count; one row of Result, Date and Plot pills, results picked in a sheet | "Scan history" with its own New scan button, folding away while the list scrolls; All and one chip per result (still combinable) in one row with the Date and Plot pills; each day's scans in one card | 3 |
| Guide (RN: Learn) | Three topic rows, the first filled in the primary color | "A field guide to Frosty Pod Rot": one photo card per topic, numbered in reading order; the draft note stays | 3 |
| Settings: theme | System / Light / Dark | One Dark mode switch (and the top bar's); "follow the phone" stays the first-run default until it is used | 3 |
| Settings: new rows | None | Your name (a sheet to change or remove it) and On-device model (opens About), in a "Profile and display" group; "Show onboarding again" reads "View introduction" | 3 |
| Camera (photo) | A 3:4 framed viewfinder with corner ticks on black; gallery, shutter and flash on a dark bar | The preview fills the screen; the top row and the controls sit on a dark scrim that fades out toward the picture; a dashed oval guide; "Scan a pod" beside the close button; the white shutter with a sage ring between gallery and flash; a line saying the photo is checked on the phone. The 4:3 photo holds a little more than the screen shows | 4 |
| Buttons | Labels on one line | Labels may wrap to a second line (Inter is wider than Barlow, and Filipino labels are longer); Preview's two buttons are equal halves | 4 |
| Result and Scan Detail | Pod rows on the page | Pod rows in one white card, like the advice | 4 |
| Language | English only | English and Filipino. Filipino (values-fil) is a draft by Claude, marked as such beside the choice, and needs a native speaker's check; the agronomic placeholders stay TODO in both. Settings > Language switches in the app (Android 13+: the per-app language, also in the phone's settings; older: saved in the app). Without a choice the app follows the phone. Class names stored in the database follow the language; scans keep their class ids | 5 |
| Camera title | One line | Up to two lines, for longer languages (part 5); since Snap tips (2026-10-31) its own line under the buttons, never cut short | 5 |
| App bundle | Language splits on | Off, so an install from a bundle has both languages | 5 |
| Onboarding panels | Drawn capsules and a disc around an icon | The owner's pod photos (finish review); the drawn viewfinder and recall chart stay. The optional name is its own sixth step after the primer (RN: five steps, the primer ends onboarding) | finish |
| Small all-caps labels | None | None either: the prototype's date line, kickers and "Field guide · 0N" numbers were built, then removed at the owner's choice in the finish review. The Home date is a plain muted line. The Home hero shows red pods on the tree (`photo_cacao_column`) | finish |
| Scan rows | Pods and plot on one line | What was found and the time on the first line, the chip, then plot and notes on one line (finish review) | finish |
| Home hero, History title, top bar | Fixed layout | With large text or a longer language: the hero photo moves above the text, the History New scan button moves under the title, and the top-bar tagline gives way to the Offline badge (finish review) | finish |
| Launch (2026-10-31, owner-approved, inspired by a Plantum recording; our own assets) | None: the window ground, then the first screen | A system splash (core-splashscreen: the launcher foreground on the window ground, in the logo's dark-tile colors in dark mode), kept only while the startup settings load. Then, on a cold start from the launcher only, an intro of about 1.4 s: the splash icon settles into place with a spring, four faint leaves drift in from the corners and sway, "YOLO-C" and the tagline rise, then a crossfade to onboarding or Home (not a route; Back never returns to it). A tap skips it; with reduce motion everything appears in place for 400 ms. No intro after a language change, rotation, process restore, a warm start, or a link or share | launch |
| Processing (2026-10-31, UI only) | The photo dimmed above "Analyzing on your device" with a spinner, under the screen header | The photo fills the screen, blurred (Modifier.blur on Android 12+; before 12 a softened ~24 px copy stretched with filtering) under a ground scrim (Deep Forest Ink in dark). After 150 ms (a fast result never flashes it) a card shows the mark with a scan line sweeping over the pod and the leaves breathing (1.6 s per cycle), "Checking your pod…" and "This runs on your phone. No internet needed.", which becomes "The first check after opening the app takes a little longer." after 3 s. The back arrow stays top left, floating over the photo as a round secondary button. Reduce motion: a still mark and a progress bar. The ViewModel, cancel on back, the error states and `ScanSession.setResult` are unchanged | processing |
| Snap tips (2026-10-31, inspired by the ⓘ tips sheet in a Plantum recording; our own content) | None | An ⓘ button (48dp, "Snap tips") in the camera's top row in Photo and Live mode opens a bottom sheet: four photo tips, each with a pictogram drawn from the mark and Feather icons (one pod in the oval, shade, close and steady, clean lens), worded to match the Guide's photo tips; "More photo tips" opens the Guide's photo topic and "Got it" closes it. It opens by itself once, the first time the camera shows after install (either mode), tracked by a `snap_tips_seen` row in the key/value settings table (no schema change; "View introduction" leaves it set). The camera keeps running under the sheet; over Live, detection pauses while it is open and the last result is dropped. To make room for the ⓘ, the Photo camera title moved to its own line under the buttons | snap tips |
| Onboarding panels, richer (2026-10-31, Plantum-like warmth, our own visual language; the pod photos stay) | Drawn panels on the plain ground, a row of capsules for progress | Every first-run step sits on a soft gradient (leaf tint to ground; Deep Forest Ink to the dark ground) with one organic blob behind the photo that morphs between steps, faint corner leaves that sway, and a faint dot grid beside the photo. The welcome slides show their picture as a framed "pebble" print (32/32/96/32dp corners, 4dp border, card shadow). Scan: viewfinder brackets and a slow scan line over the photo, chips "On-device" and "One pod at a time". Offline: chips "Works offline" and "Stays on your phone". Aid: the three status chips as a legend under the text ("The app sorts each pod into one of these."), never on a photo. Up to three short feature rows per slide (the aid slide leads with the measured miss rate; the tips slide keeps its four photo tips as rows). Progress is a segmented bar, the current step wider. The slides also swipe. When space runs out the decor, then the third row, then the print size go; the middle scrolls. Reduce motion: all in place, no sway, sweep or morph. The permission primer and the name step keep their plain panel and content, and share only the ground and the progress bar | onboarding panels |

## Step 6 measurements (filled in as the port progresses)

Cold start: `adb shell am force-stop`, then `am start -W` with the launcher intent, 7 runs. "First
frame" is `am start -W` TotalTime. "Fully drawn" is the Logcat `Fully drawn` line, which
YoloCRoot reports (`ReportDrawn`) once onboarding or Home is composed, after the intro if it
plays. In the debug build the first screen's first frame takes about 0.67 s of main thread, which
holds the intro's last frame before the crossfade.

| Measure                                                  | RN                                                    | Kotlin |
| -------------------------------------------------------- | ----------------------------------------------------- | ------ |
| Release APK size                                         |                                                       |        |
| Cold start to Home (ms)                                  |                                                       |        |
| Cold start to the first interactive screen, before the launch intro (ms) |                                       | 2583 (Fully drawn; first frame 1705). Debug build, TECNO BG6, Home, median of 7, 2026-10-31 |
| Cold start to the first interactive screen, with the launch intro (ms)   |                                       | 3944 (Fully drawn, +1361 for the intro; first frame 1736). Same build type, phone and method |
| Model load (ms)                                          |                                                       |        |
| Preprocess / inference / postprocess per photo (median of 5, golden_01) | 2.2–2.7 s / 1375 ms / 15 ms (dev build, 2026-10-15) | |
| Live: detections/s and camera fps, GPU and CPU           |                                                       |        |
| Peak memory during analysis (Android Studio profiler)    |                                                       |        |
| Clean build time / incremental build time                |                                                       |        |
| Lines of code (app code, tests)                          |                                                       |        |
| Permissions in the release manifest                      | CAMERA, location, ACCESS_NETWORK_STATE                |        |
