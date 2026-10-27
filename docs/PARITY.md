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
| golden_01 | PASS, IoU 0.995–0.997, confidence within 0.008                           |        |       |
| golden_02 | FAIL, infected pod matches (IoU 0.981); small healthy pod 0.276 vs 0.495 |        |       |
| golden_03 | PASS, IoU 0.998–1.000                                                    |        |       |
| golden_04 | PASS, IoU 0.955–0.998, confidence within 0.026                           |        |       |
| golden_05 | PASS, IoU 0.992–0.997, confidence within 0.035                           |        |       |

Expected difference: Kotlin reads the resized bitmap directly instead of re-encoding a JPEG, so
IoU and confidence deltas may differ slightly from RN.

## Performance on golden_01 (CPU, default options, median of 5)

| Measure                          | RN (dev build, 2026-10-15)   | Kotlin |
| -------------------------------- | ---------------------------- | ------ |
| Model load                       | 4.1 s (downloaded from Metro) |        |
| Inference (`run` only)           | 1375 ms                      |        |
| Preprocess                       | 2.2–2.7 s                    |        |
| Postprocess                      | about 15 ms                  |        |
| End to end                       | 3.8 s                        |        |

## Experiments (labelled separately from the parity run)

| Backend         | Load / compile (ms) | Median inference, golden_01 (ms) | Golden PASS count | Notes |
| --------------- | ------------------- | -------------------------------- | ----------------- | ----- |
| CPU (default)   |                     |                                  |                   |       |
| CPU, 4 threads  |                     |                                  |                   |       |
| GPU (CompiledModel) |                 |                                  |                   |       |

RN's live mode uses the `android-gpu` delegate; Kotlin uses LiteRT's `CompiledModel` with
`Accelerator.GPU` (the Interpreter GPU delegate is LiteRT v1 only).

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
