# YOLO-C in Kotlin: port guide

A step-by-step plan for rebuilding YOLO-C as a native Android app (Kotlin, Jetpack Compose,
Android Studio) with **the same feature set** as the React Native / Expo version at commit
`1e7aabe` ("Redesign the scan flow: dark camera, photo-first processing"), so the two can be
compared side by side.

How to read it:

- Each step has an **RN →** line that names the file or library in this repo it replaces.
- Code is given in full for the parts that must behave identically (detection math, verdict,
  database rules). Screens and components are given as structure (state, events, wiring), since
  their look is defined by `CLAUDE.md` part 4 and the existing components.
- Library versions marked *(docs, 2026-10-26)* were checked in the official docs on that date.
  Anything else: accept the newest stable version Android Studio suggests.

---

## 0. What you are porting

### 0.1 Feature inventory (current RN app)

Port exactly this list. Nothing more, nothing less.

**Startup and onboarding**
- Opens the database (migrate + seed from `model.config.json`) and reads settings before showing
  anything; on failure shows an error with Retry.
- First launch: 4 slides (scan, offline, photo tips with 4 tips, screening aid with the "6 of 10
  found" recall illustration) + a permission primer that requests nothing. Skip on every slide
  except the last; Android back steps back a slide. Completing it swaps onboarding for the tabs
  (back from Home exits; it never returns to onboarding). Settings can replay it.

**Tabs**: Home · History · **Scan** (center button, not a tab) · Learn · Settings. Back from
any tab goes to Home; back from Home exits.

**Home** (interim layout, as in the code today): headline + full-width "Scan a pod" button;
"Last 30 days" with three tappable count chips (Healthy / Early stage / Infected) that open
History pre-filtered; the 3 most recent scans as rows with "See all"; a photo tip that changes
daily with "Learn more"; skeletons while loading; error card with Retry; empty card; Undo
snackbar.
> The brief's newer Home (latest-scan hero + readout strip) is **not built yet** in RN. Do not
> build it in Kotlin either. `ReadoutStrip` exists as a component but is only used by the dev
> gallery.

**Scan flow**
- **Camera** (`/scan/camera`): dark in both themes, framed 3:4 viewfinder with corner ticks;
  close (X) top-left; Photo | Live switch top-right (only when camera is granted); permission
  states inside the viewfinder (ask → "Allow camera"; denied → rationale + ask again; blocked →
  "Open settings"; camera failed to start → Retry); hint/notice line; bottom bar: gallery
  button (shows the latest saved scan's thumbnail), shutter, flash toggle. Gallery works without
  camera permission. Cancelled gallery pick = stay, no message. Preview only runs while the
  screen is focused and the app is in the foreground. Permission re-checked on return to app.
- **Live** (`/scan/live`): full-screen camera with on-device detection on the newest frame (GPU
  delegate, falls back to CPU with a notice), throttled (≥150 ms between runs), results older than
  1.5 s hidden, boxes + labels drawn over the preview, a live verdict capsule, a "preview only"
  note, and a shutter that captures a full photo into the normal Preview → Processing → Result
  path. Dev builds: benchmark HUD (backend switch, fps, detections/s, p50/p90) and a
  `[live-bench]` log line every 10 s.
- **Preview**: the photo; Retake (camera photo → back to camera) or Choose another (gallery photo
  → picker again, cancel keeps the photo); Use photo. Broken image → error state.
- **Processing**: the photo dimmed + spinner + "Analyzing on your device"; replaces itself with
  Result; failures show a stage-specific error (image / model / memory / inference) with Try
  again / Go back; back while analyzing cancels and a late result is ignored.
- **Result**: annotated photo (tap a box or a row to highlight; tap again to clear), verdict line
  with counts and the low-confidence banner (best detection < 0.40), pod list with confidence %,
  "Correct label" on the selected pod (bottom sheet; picking the model's own class clears the
  correction; verdict is recomputed), advice card from the `classes` table, Save / Discard pinned
  at the bottom. No pods → "No cacao pods found" empty state with 3 tips and Retake (no footer).
  Save opens the **Save sheet** (plot radio list incl. "No plot", add a plot inline, "Manage
  plots", notes, Save). Leaving with unsaved pods asks Stay / Discard / Save. With location
  opted in, the GPS fix starts when Result opens. After saving: History tab, then Scan Detail
  with a "Saved" toast. Discard returns to the camera.

**History**: headline + count ("12 scans" / "3 matching"); search (debounced 250 ms) + Status /
Date / Plot filter pills (each opens a sheet); list grouped by day (Today / Yesterday / "Mon 12
Oct"); rows with thumbnail, verdict chip, "3 pods · North block", time, uncertain flag, first
line of notes; keyset paging 40 at a time; swipe left to delete, long-press → options sheet (Open
/ Delete), accessibility delete action; Undo snackbar (6 s); delete-failed toast; skeletons;
empty state with Scan CTA; no-match state with Clear filters; background thumbnail backfill;
accepts a verdict or plot filter from Home / Plots.

**Scan Detail** (`/history/[id]`): "Saved on …", the same report as Result (corrections write to
the DB immediately and update the verdict), notes editor with explicit Save (shown only when
changed), details card (plot, source, model, analysis time, location if any, threshold), Delete
with confirm → back with Undo; loading / not found / error states; toasts.

**Learn**: 3 topics (Stages, How to photograph pods, How the app works) + draft note. Topic
screens: Stages from the `classes` table (chip, summary, signs, actions); photo tips; how-it-works
steps + "how much to trust it" card linking to About and Settings.

**Settings**: confidence threshold slider (10–90 %, 5 % steps, default marker, reset button,
help text); theme (System / Light / Dark); opt-in location (switch row; permission flow with Try
again / Open settings; "revoked" state); Data: Plots (count), Storage (scans + bytes), Export CSV
(share sheet), Export for retraining (sheet), Clear all data (confirm); App: About, Show
onboarding again; dev rows (Gallery, Seed, Model test) in debug only.

**Export for retraining** sheet: scope Corrected / Early stage / All with per-scope count and
size, free-space check (200 MB margin), writes a YOLO-format zip (images/, labels/, data.yaml,
manifest.json, README.txt) and opens the share sheet; result toast.

**Plots**: add (dedupe, case-insensitive), rename (sheet), delete (confirm; scans keep, lose the
plot), tap → History filtered to the plot, scan count per plot.

**About**: disclaimer first, early-stage recall headline + 10-pod illustration, metric bars, limitations,
model name / id / input size / class chips, privacy note, app version.

**Dev only**: component gallery, fake-scan seeder, model test (tensor shapes, timings, golden
test against `assets/sample/expected_*.json`).

**Cross-cutting**: fully offline (release build has no INTERNET permission), light/dark themes,
reduce-motion respected, 48 dp touch targets, never color alone, all strings in one i18n file.

### 0.2 Stack mapping

| Concern | RN version | Kotlin version |
| --- | --- | --- |
| Language / UI | TypeScript, React Native 0.86, Expo SDK 57 | Kotlin 2.4, Jetpack Compose (Material 3) |
| Navigation | Expo Router (file routes, `Stack.Protected`, Tabs) | Navigation Compose 2.10, type-safe `@Serializable` routes |
| State | Zustand stores (`src/store`) | Singleton state holders with `StateFlow` + one `ViewModel` per screen |
| Database | expo-sqlite + hand-written migrations + repositories | Room 3 (KSP) + DAOs + repositories |
| On-device ML | react-native-fast-tflite (CPU; `android-gpu` delegate in live) | LiteRT: `Interpreter` (CPU, photos) and `CompiledModel` (GPU, live) |
| Camera (photo) | expo-camera `CameraView` | CameraX `Preview` + `ImageCapture`, `CameraXViewfinder` |
| Camera (live) | react-native-vision-camera + GPU resizer + worklets | CameraX `ImageAnalysis` (keep-only-latest) |
| Gallery | expo-image-picker (system photo picker) | `ActivityResultContracts.PickVisualMedia` |
| Image resize / decode | expo-image-manipulator + jpeg-js | `BitmapFactory` + `ExifInterface` + `Bitmap.createScaledBitmap` |
| Files | expo-file-system (`Paths.document`, `Paths.cache`) | `context.filesDir`, `context.cacheDir` |
| Share | expo-sharing | `FileProvider` + `Intent.ACTION_SEND` |
| Zip | fflate | `java.util.zip.ZipOutputStream` |
| Location | expo-location | Play services `FusedLocationProviderClient` |
| Images on screen | RN `Image` | Coil 3 `AsyncImage` |
| Lists | FlashList | `LazyColumn` |
| Icons | Feather via @expo/vector-icons | Feather SVGs imported as vector drawables |
| Fonts | expo-font, `assets/fonts` | `res/font` + `FontFamily` |
| Strings | `src/i18n/en.ts` | `res/values/strings.xml` (+ plurals) |
| Offline release | `plugins/withOfflineRelease.js` | `tools:node="remove"` in the manifest |
| Tests | Jest (Node SQLite for repositories) | JUnit (JVM) + instrumented tests (Room, golden test) |
| Quality gate | `npm run check` (tsc, ESLint, Jest) | `./gradlew lintDebug testDebugUnitTest` (+ ktlint if you add it) |

### 0.3 Suggested build order

The brief's rule still applies: **pipeline first, verified by the golden test, before screens.**

1. Step 1 (setup) → 2. Step 2 (packages) → 3. Step 4.1 (detection pipeline) + the dev Model test
screen + golden test → 4. Step 3 (database) → 5. Step 4.2–4.4 (state, ViewModels) → 6. Step 5
(theme, components, screens) → 7. Step 6 (parity checklist).

---

## Step 1 — Project setup

**RN →** `package.json`, `app.json`, `metro.config.js`, `plugins/withOfflineRelease.js`,
`eslint.config.js`, `tsconfig.json`.

### 1.1 Android Studio and SDK

1. Install the current stable Android Studio. Use its bundled JBR; set the Gradle JDK to 17
   (File → Settings → Build Tools → Gradle → Gradle JDK). Your RN build already needed JDK 17
   for Gradle; the same holds here.
2. SDK Manager: Android SDK Platform **36** (the RN app compiles and targets 36), Build-Tools,
   Platform-Tools. You do not need an emulator image: your AVD is broken and the camera/model
   need a real phone anyway. Use the TECNO BG6 over USB.
3. Keep the project path free of spaces (e.g. `C:\Users\RIO\Desktop\yolo-c-kt`). Your old
   spaced folder broke a native build step once.
4. Disk: a pure-Kotlin build is far smaller than the RN one (no C++ for Reanimated, Screens,
   Worklets), but check you have ~5 GB free for Gradle caches.

### 1.2 New project

New Project → **Empty Activity** (Compose).

| Field | Value | Why |
| --- | --- | --- |
| Name | YOLO-C (Kotlin) | |
| Package name / namespace | `com.yoloc.app` | Same code package as RN (`app.json` → `android.package`) |
| `applicationId` | `com.yoloc.app.kt` | **Different** from RN's `com.yoloc.app`, so both apps install side by side on the same phone |
| Minimum SDK | API 26 | Same as RN (`expo-build-properties` → `minSdkVersion: 26`) |
| Build config language | Kotlin DSL + version catalog | |

Make it a separate Git repo (or a sibling folder), with Git LFS for `*.tflite` as in the RN
repo's `.gitattributes`.

### 1.3 Version catalog (`gradle/libs.versions.toml`)

Keep what the wizard generated (AGP, Kotlin, Compose BOM, core-ktx, activity-compose,
lifecycle) and add:

```toml
[versions]
# Wizard values; bump to the newest stable Studio offers. AGP 9.x has Kotlin built in.
ksp = "2.3.5"                      # must match your Kotlin version line
kotlinxSerialization = "1.11.0"
navigationCompose = "2.10.2"       # (docs, 2026-10-26)
room3 = "3.0.3"                    # (docs, 2026-10-26)
litert = "2.3.0"                   # (docs, 2026-10-26)
camerax = "1.6.1"                  # (docs, 2026-10-26)
coil = "3.6.3"                     # (docs, 2026-10-26)
exifinterface = "1.4.1"
playServicesLocation = "21.3.0"
coroutines = "1.10.2"

[libraries]
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
litert = { module = "com.google.ai.edge.litert:litert", version.ref = "litert" }
litert-gpu = { module = "com.google.ai.edge.litert:litert-gpu", version.ref = "litert" }
camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camera-compose = { module = "androidx.camera:camera-compose", version.ref = "camerax" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
androidx-exifinterface = { module = "androidx.exifinterface:exifinterface", version.ref = "exifinterface" }
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
room3 = { id = "androidx.room3", version.ref = "room3" }
```

> Room 3 needs a SQLite driver. Add `androidx.sqlite:sqlite-framework` (`AndroidSQLiteDriver`,
> uses the phone's SQLite, smaller APK) or `androidx.sqlite:sqlite-bundled`
> (`BundledSQLiteDriver`, ships its own SQLite like expo-sqlite does). Use the androidx.sqlite
> version listed in the Room 3 release notes. For a strict comparison, Bundled is the closer
> match.

### 1.4 `app/build.gradle.kts`

```kotlin
plugins {
    // keep the wizard's android-application + compose plugins
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.yoloc.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yoloc.app.kt"
        minSdk = 26
        targetSdk = 36
        versionName = "0.1.0"          // same as app.json "version"
        // The only native code is LiteRT; one ABI keeps builds and the APK small on your laptop.
        ndk { abiFilters += "arm64-v8a" }
    }
    // openFd() on an asset only works if it is stored uncompressed (RN: Metro assetExts).
    androidResources { noCompress += "tflite" }
    buildFeatures { compose = true; buildConfig = true }
}

room3 { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    // + androidx.sqlite driver (see note above)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    // lifecycle-viewmodel-compose and lifecycle-runtime-compose (wizard catalog, same version)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

### 1.5 `AndroidManifest.xml`

**RN →** `app.json` plugin permissions + `plugins/withOfflineRelease.js`.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <!-- Offline-first: nothing may reach the network. Unlike RN, debug builds do not need
         INTERNET either (there is no Metro), so this applies to every build. -->
    <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" tools:node="remove" />

    <application android:name=".YoloCApp" ...>
        <activity android:name=".MainActivity" android:screenOrientation="portrait"
            android:exported="true" ...>
            <!-- launcher intent filter -->
        </activity>
        <!-- CSV and retraining zip shares (RN: expo-sharing) -->
        <provider android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/share_paths" />
        </provider>
    </application>
</manifest>
```

`res/xml/share_paths.xml`: `<paths><cache-path name="exports" path="." /></paths>`.

After the first build, open the merged manifest (Android Studio: Merged Manifest tab) and check
that INTERNET is gone and CAMERA is present, the same check you do after every RN prebuild.

### 1.6 Assets

| RN path | Kotlin path |
| --- | --- |
| `assets/models/yolo_c_v2_640_fp32.tflite` | `app/src/main/assets/models/yolo_c_v2_640_fp32.tflite` |
| `assets/models/model.config.json` | `app/src/main/assets/models/model.config.json` (same file, unchanged) |
| `assets/sample/golden/*.jpg`, `expected_*.json` | `app/src/debug/assets/sample/...` (debug only) |
| `assets/fonts/Barlow-*.ttf` | `app/src/main/res/font/barlow_regular.ttf`, `barlow_medium.ttf`, `barlow_semibold.ttf`, `barlow_semi_condensed_medium.ttf`, `barlow_semi_condensed_semibold.ttf` (lowercase, underscores) + keep `OFL-Barlow.txt` |
| `assets/android-icon-*.png` | Image Asset Studio → adaptive icon, background `#E3EED4` |
| Feather icons (`@expo/vector-icons`) | Download the Feather SVGs (MIT) and import each with Vector Asset Studio into `res/drawable/ic_<name>.xml` |

Feather icons used by the app: alert-circle, arrow-right, bar-chart-2, book-open, camera,
camera-off, check, chevron-down, chevron-right, clock, cpu, crop, crosshair, database, droplet,
edit-2, edit-3, eye, flag, grid, hard-drive, home, image, info, layers, lock, map, map-pin,
maximize, moon, move, package, pause-circle, play, plus, refresh-cw, rotate-ccw, rotate-cw,
save, search, settings, share-2, shield, slash, sliders, smartphone, sun, tag, trash-2,
umbrella, wifi-off, x (plus the status icons named in `src/components/StatusChip.tsx`).

Swapping the model stays a no-code change: drop the new `.tflite` into `assets/models/`, edit
`file` in `model.config.json`, rebuild. (RN: restart Metro instead of rebuilding.)

### 1.7 Strings

**RN →** `src/i18n/en.ts`.

Move every string into `res/values/strings.xml`, keeping the grouping as name prefixes
(`result_verdict_early`, `history_count`, …). Functions become format strings or plurals:

```xml
<string name="result_verdict_early">Early-stage signs detected in %1$d of %2$d pods</string>
<plurals name="history_count">
    <item quantity="one">%d scan</item>
    <item quantity="other">%d scans</item>
</plurals>
```

Keep the `TODO(verify with agronomist)` placeholders verbatim; they go into `classCatalog`
(Step 4.1) exactly as in RN.

### 1.8 Quality gates

| RN | Kotlin |
| --- | --- |
| `tsc --noEmit` | the Kotlin compiler; set `allWarningsAsErrors = true` in `kotlin { compilerOptions { } }` |
| `eslint` / `prettier` | Android Lint (`./gradlew lintDebug`); optionally Spotless + ktlint (ask yourself whether it is worth the extra plugin) |
| `jest` | `./gradlew testDebugUnitTest` (JVM) and `./gradlew connectedDebugAndroidTest` (phone) |
| `npm run check` | `./gradlew lintDebug testDebugUnitTest` |

---

## Step 2 — Project structure

**RN →** the folder layout in `CLAUDE.md` 2.3. The Kotlin packages mirror it one to one, so a
file in one tree has an obvious twin in the other.

```text
app/src/main/java/com/yoloc/app/
├─ YoloCApp.kt                  Application: builds AppContainer           ← (none; Expo did this)
├─ AppContainer.kt              manual DI: db, repositories, stores, model ← db/client.ts getDb() + store singletons
├─ MainActivity.kt              setContent { YoloCRoot() }                 ← expo-router/entry
├─ screens/                     one package per route                      ← app/
│  ├─ RootScreen.kt             fonts, theme, startup gate, NavHost        ← app/_layout.tsx
│  ├─ navigation/Routes.kt, YoloCNavHost.kt, AppTabBar.kt                 ← app/_layout.tsx, app/(tabs)/_layout.tsx
│  ├─ onboarding/ WelcomeScreen, PermissionsScreen, OnboardingViewModel   ← app/(onboarding)/
│  ├─ home/ HomeScreen, HomeViewModel                                     ← app/(tabs)/index.tsx
│  ├─ history/ HistoryScreen, HistoryViewModel                            ← app/(tabs)/history.tsx
│  ├─ scandetail/ ScanDetailScreen, ScanDetailViewModel                   ← app/history/[id].tsx
│  ├─ learn/ LearnScreen, LearnTopicScreen, LearnTopicViewModel           ← app/(tabs)/learn.tsx, app/learn/[topic].tsx
│  ├─ settings/ SettingsScreen, SettingsViewModel                         ← app/(tabs)/settings.tsx
│  ├─ scan/ camera/, live/, preview/, processing/, result/                ← app/scan/*.tsx
│  ├─ about/ AboutScreen, AboutViewModel                                  ← app/about.tsx
│  ├─ plots/ PlotsScreen, PlotsViewModel                                  ← app/plots.tsx
│  └─ dev/ (debug only) GalleryScreen, SeedScreen, InferenceScreen        ← app/dev/
├─ theme/       Palette.kt, Themes.kt, Tokens.kt, Type.kt, YoloCTheme.kt, Contrast.kt   ← src/theme/
├─ components/  AppText, YButton, Card, StatusChip, VerdictChip, VerdictLine,
│               ReadoutStrip, DetectionListItem, ImageWithBoxes, EmptyState, ErrorState,
│               BottomSheet, Toast, SectionHeader, SegmentedControl, YSlider, TabBar,
│               Icon, RecallPods, ScreenContainer                          ← src/components/
├─ features/
│  ├─ detection/  ModelConfig, ModelSpec, ModelLoader, ClassCatalog, Letterbox,
│  │              ImageDecoder, Preprocess, Decode, Nms, Postprocess,
│  │              RunDetection, Verdict, DetectionErrors, Golden          ← src/features/detection/
│  ├─ scans/      AnalyzePhoto, SaveScan, SaveResult, ScanFileStore, TempImages,
│  │              Thumbnails, ResultEdits, HistoryFilters, VerdictText, CameraAccess,
│  │              ui/ (ScanReport, ScanRow, SaveScanSheet, CorrectLabelSheet,
│  │                   HistoryFilterBar, UndoDeleteSnackbar)              ← src/features/scans/
│  ├─ camera/     Viewfinder, CameraControlBar, ViewfinderMath, rememberCameraAccess ← src/features/camera/
│  ├─ live/       LiveMath, LiveStats, ModeSwitch                         ← src/features/live/
│  ├─ settings/   DataTools, Location, Threshold                          ← src/features/settings/
│  ├─ export/     Retraining, ShareRetrainingZip, RetrainingExportSheet   ← src/features/export/
│  ├─ learn/      LearnTopics                                             ← src/features/learn/
│  ├─ onboarding/ OnboardingFrame, Illustrations, Steps                   ← src/features/onboarding/
│  └─ dev/        FakeScans, SeedFakeScans, GoldenSamples                 ← src/features/dev/
├─ db/
│  ├─ AppDatabase.kt, Entities.kt, Seed.kt                                ← src/db/client.ts, migrations/, seed.ts
│  ├─ dao/ ScanDao, PlotDao, ClassDao, SettingsDao, SeedDao, DevDao        ← SQL inside src/db/repositories/
│  └─ repositories/ ScanRepository, PlotRepository, ClassRepository,
│                   SettingsRepository                                    ← src/db/repositories/
├─ store/       ScanSession, ScanLibrary, SettingsStore                   ← src/store/
└─ util/        Uuid, rememberReduceMotion, rememberAppInForeground, rememberNow ← src/utils/
app/src/test/java/...          JVM unit tests                             ← __tests__/*.test.ts
app/src/androidTest/java/...   Room + golden tests on the phone           ← __tests__/db.*.test.ts, golden
```

**Dependency direction** (same as RN): `screens` → `features` / `store` → `db` →
nothing. Screens never write SQL; `features/detection` never touches Android UI.

**Manual DI instead of Hilt**: `AppContainer` is a plain class built once in
`YoloCApp.onCreate()`. It plays the role of RN's module-level singletons (`getDb()`,
`useScanLibraryStore`, …). Hilt would work too but adds an annotation processor and build time on
a slow laptop, and nothing in the RN app needs scoped injection.

```kotlin
class AppContainer(private val context: Context) {
    val modelConfig: ModelConfig by lazy { ModelConfig.load(context) }
    val database = AppDatabaseHolder(context, modelConfig)        // getDb()
    val files: ScanFileStore = AndroidScanFileStore(context)       // expoScanFileStore
    val tempImages = TempImages(context)                           // tempImage.ts
    val modelLoader = ModelLoader(context, modelConfig)            // modelLoader.ts
    val scanRepository = ScanRepository(database)
    val plotRepository = PlotRepository(database)
    val classRepository = ClassRepository(database)
    val settingsRepository = SettingsRepository(database)
    val settingsStore = SettingsStore(settingsRepository, modelConfig)   // settingsStore.ts
    val scanLibrary = ScanLibrary(scanRepository)                        // scanLibraryStore.ts
    val scanSession = ScanSession(tempImages)                            // scanSessionStore.ts
}
```

---

## Step 3 — Data layer

**RN →** `src/db/`, `src/features/detection/modelConfig.ts`, `classCatalog.ts`,
`src/features/scans/scanFileStore.ts`, `tempImage.ts`, `thumbnails.ts`.

### 3.1 Model config

```kotlin
@Serializable
data class ModelConfig(
    val id: String,
    val displayName: String,
    val file: String,
    val inputSize: Int,
    /** Class keys in model output index order. */
    val classes: List<String>,
    val confThreshold: Double,
    val iouThreshold: Double,
    /** Best detection below this shows the "Uncertain result" banner. */
    val lowConfidenceThreshold: Double,
    val letterboxPadValue: Int,
    val metrics: Metrics,
) {
    @Serializable
    data class Metrics(
        val valMap50: Double? = null,
        val testMap50: Double? = null,
        val earlyStageTestMap50: Double? = null,
        val earlyStageRecall: Double? = null,
    )

    companion object {
        fun load(context: Context): ModelConfig =
            context.assets.open("models/model.config.json").use {
                Json.decodeFromString(serializer(), it.readBytes().decodeToString())
            }
    }
}
```

RN imports the JSON at bundle time; Kotlin reads it from assets at startup. Both treat it as the
single source of truth for size, classes and thresholds.

### 3.2 Entities (the schema)

**RN →** `src/db/migrations/001_initial.ts`. Same tables, columns, names and indexes. Store
verdicts and sources as `String` and booleans as `Int` (0/1), exactly as in SQLite, and map
them to Kotlin types in the repositories (as RN's `toSummary` does).

```kotlin
@Entity(tableName = "model_versions")
data class ModelVersionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "input_size") val inputSize: Int,
    @ColumnInfo(name = "num_classes") val numClasses: Int,
    @ColumnInfo(name = "val_map50") val valMap50: Double?,
    @ColumnInfo(name = "test_map50") val testMap50: Double?,
    @ColumnInfo(name = "early_stage_recall") val earlyStageRecall: Double?,
    @ColumnInfo(name = "is_active", defaultValue = "0") val isActive: Int,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
)

@Entity(tableName = "classes", indices = [Index(value = ["key"], unique = true)])
data class ClassEntity(
    @PrimaryKey val id: Int,                      // model output index
    val key: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val severity: Int,
    @ColumnInfo(name = "color_token") val colorToken: String,
    val summary: String,
    val symptoms: String?,
    @ColumnInfo(name = "recommended_actions") val recommendedActions: String?,  // JSON array
)

@Entity(tableName = "plots")
data class PlotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "scans",
    foreignKeys = [
        ForeignKey(PlotEntity::class, ["id"], ["plot_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ModelVersionEntity::class, ["id"], ["model_version_id"]),
    ],
    indices = [
        Index(value = ["created_at"], name = "idx_scans_created", orders = [Index.Order.DESC]),
        Index(value = ["verdict"], name = "idx_scans_verdict"),
        Index(value = ["plot_id"], name = "idx_scans_plot"),
    ],
)
data class ScanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plot_id") val plotId: String?,
    @ColumnInfo(name = "model_version_id") val modelVersionId: String,
    @ColumnInfo(name = "image_path") val imagePath: String,         // relative to filesDir
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
    @ColumnInfo(name = "image_width") val imageWidth: Int,
    @ColumnInfo(name = "image_height") val imageHeight: Int,
    val source: String,                                             // camera | gallery
    @ColumnInfo(name = "conf_threshold") val confThreshold: Double,
    @ColumnInfo(name = "iou_threshold") val iouThreshold: Double,
    @ColumnInfo(name = "inference_ms") val inferenceMs: Int?,
    @ColumnInfo(name = "total_pods", defaultValue = "0") val totalPods: Int,
    val verdict: String,                                            // healthy | early_stage | infected | no_pod
    @ColumnInfo(name = "low_confidence", defaultValue = "0") val lowConfidence: Int,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "detections",
    foreignKeys = [
        ForeignKey(ScanEntity::class, ["id"], ["scan_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ClassEntity::class, ["id"], ["class_id"]),
        ForeignKey(ClassEntity::class, ["id"], ["corrected_class_id"]),
    ],
    indices = [
        Index(value = ["scan_id"], name = "idx_detections_scan"),
        Index(value = ["class_id"], name = "idx_detections_class"),
    ],
)
data class DetectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "scan_id") val scanId: String,
    @ColumnInfo(name = "class_id") val classId: Int,
    val confidence: Double,
    val x: Double, val y: Double, val w: Double, val h: Double,      // normalized, top-left
    @ColumnInfo(name = "corrected_class_id") val correctedClassId: Int?,
    @ColumnInfo(name = "corrected_at") val correctedAt: Long?,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

**Differences you cannot avoid (write them down for the comparison):**

- Room cannot declare `CHECK (...)` constraints. Enforce them in code instead: `Verdict` and
  `ScanSource` enums in the repository, and `validateNewScan` (below) before every insert.
- RN tracks applied migrations in its own `schema_migrations` table. Room uses SQLite's
  `user_version` plus `Migration(from, to)` objects and an exported JSON schema per version
  (`app/schemas/`). Schema 001 = Room `version = 1`. A future `002_…` becomes
  `Migration(1, 2)` with `connection.executeSQL(...)`.
- RN sets `PRAGMA journal_mode = WAL; foreign_keys = ON` itself. Room uses WAL by default and
  turns on foreign keys for databases whose entities declare them (check the generated
  `AppDatabase_Impl` once to confirm).

### 3.3 Database, defaults and seed

```kotlin
@Database(
    entities = [ModelVersionEntity::class, ClassEntity::class, PlotEntity::class,
        ScanEntity::class, DetectionEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun plotDao(): PlotDao
    abstract fun classDao(): ClassDao
    abstract fun settingsDao(): SettingsDao
    abstract fun seedDao(): SeedDao
    abstract fun devDao(): DevDao
}

/** Defaults that do not depend on the model (RN: the INSERT OR IGNORE at the end of 001). */
private val defaultsCallback = object : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        connection.executeSQL(
            """INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES
               ('onboarding_done', '0', CAST(strftime('%s','now') AS INTEGER) * 1000),
               ('theme', 'system', CAST(strftime('%s','now') AS INTEGER) * 1000),
               ('gps_opt_in', '0', CAST(strftime('%s','now') AS INTEGER) * 1000),
               ('keep_original_images', '1', CAST(strftime('%s','now') AS INTEGER) * 1000)""",
        )
    }
}
```

`AppDatabaseHolder` reproduces RN's `getDb()`: open once, seed once, and **do not cache a
failure**, so the startup Retry button works.

```kotlin
class AppDatabaseHolder(private val context: Context, private val config: ModelConfig) {
    private val mutex = Mutex()
    private var ready: AppDatabase? = null

    suspend fun get(): AppDatabase = mutex.withLock {
        ready ?: run {
            val db = Room.databaseBuilder<AppDatabase>(context, "yolo-c.db")
                .setDriver(AndroidSQLiteDriver())   // or BundledSQLiteDriver()
                .addCallback(defaultsCallback)
                .build()
            // The first query opens the file and creates/migrates the schema.
            Seed.seedFromModelConfig(db.seedDao(), config, classCatalog(context))
            db.also { ready = it }      // only cached once open + seed succeeded
        }
    }
}
```

**Seed** (RN → `src/db/seed.ts`): same rules. Fingerprint the config + class catalog (FNV-1a
over the JSON text), skip if unchanged; fail before writing if a class key has no catalog entry;
in one transaction: deactivate other models, upsert the active model, upsert classes **by id,
never delete**, insert `conf_threshold` / `iou_threshold` only if missing, always overwrite
`active_model_id` and the fingerprint.

```kotlin
@Dao
abstract class SeedDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    abstract suspend fun setting(key: String): String?
    @Query("UPDATE model_versions SET is_active = 0 WHERE id <> :activeId")
    abstract suspend fun deactivateOthers(activeId: String)
    @Upsert abstract suspend fun upsertModel(model: ModelVersionEntity)
    @Upsert abstract suspend fun upsertClasses(classes: List<ClassEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(setting: SettingEntity)
    @Upsert abstract suspend fun upsertSetting(setting: SettingEntity)

    @Transaction
    open suspend fun apply(model: ModelVersionEntity, classes: List<ClassEntity>,
                           config: ModelConfig, fingerprint: String, now: Long) {
        deactivateOthers(model.id)
        upsertModel(model)
        upsertClasses(classes)
        insertIfMissing(SettingEntity("conf_threshold", config.confThreshold.toString(), now))
        insertIfMissing(SettingEntity("iou_threshold", config.iouThreshold.toString(), now))
        upsertSetting(SettingEntity("active_model_id", config.id, now))
        upsertSetting(SettingEntity(FINGERPRINT_KEY, fingerprint, now))
    }
}

/** FNV-1a, same algorithm as seed.ts. Kotlin Ints wrap like Math.imul. */
fun fingerprintOf(text: String): String {
    var hash = 0x811c9dc5.toInt()
    for (ch in text) { hash = hash xor ch.code; hash *= 0x01000193 }
    return hash.toUInt().toString(16)
}
```

One deviation from RN to note: RN's `upsertModel` preserves `installed_at` on conflict (it is not
in the `DO UPDATE SET` list). `@Upsert` replaces the whole row. If you want identical behavior,
write that statement as an `@Query("INSERT ... ON CONFLICT(id) DO UPDATE SET ...")` copied from
`seed.ts`.

**Class catalog** (RN → `classCatalog.ts`): a `Map<String, ClassInfo>` with the same severities
(early 1, infected 2, healthy 0), color tokens and the `TODO(verify with agronomist)` text.
Display names come from `R.string.status_*`, so it is built with a `Context`.

### 3.4 DAOs and repositories

**RN →** `src/db/repositories/*.ts`. Keep the same public functions and names, so the two
codebases read alike. DAOs hold SQL; repositories hold the rules and map rows to domain types.

| RN function | Kotlin | Notes |
| --- | --- | --- |
| `createScan(db, input, {lowConfidenceThreshold})` | `ScanDao.createScan(...)` (`@Transaction`) via `ScanRepository.createScan` | verdict / total_pods / low_confidence derived from detections inside the transaction; corrections count; a "correction" to the model's own class is stored as null |
| `listScans(db, filters)` | `ScanRepository.listScans(filters)` → `@RawQuery` | dynamic WHERE; keyset `after` cursor; `ORDER BY created_at DESC, id` |
| `countScans` | `@RawQuery` returning `CountRow(n)` | same WHERE, no paging |
| `getScan(id)` | `scanRow(id)` + `detections(id)` (`ORDER BY confidence DESC, id`) | `effectiveClassId = corrected ?: classId` |
| `softDeleteScan`, `restoreScan`, `updateScanNotes`, `setScanThumbnail`, `listScansWithoutThumbnail` | `@Query` UPDATE/SELECT | return `changes > 0` → `Int` row count from `@Query` UPDATE |
| `correctDetection` | `@Transaction` | update correction (or clear it), recompute verdict with `COALESCE(corrected_class_id, class_id)` |
| `getStats(since)` | two `@Query` GROUP BY | verdict counts + pods by class key after corrections |
| `exportScansCsv` | repository builds the text | see CSV notes below |
| `clearAllScanData` | `@Transaction` deleting detections, scans, plots | |
| `createPlot`, `listPlots`, `listPlotsWithCounts`, `renamePlot`, `softDeletePlot`, `deletePlot`, `countPlots` | `PlotDao` / `PlotRepository` | `ORDER BY name COLLATE NOCASE`; deletePlot detaches scans then soft-deletes in one transaction |
| `listClasses` | `ClassDao` | parse `recommended_actions` JSON, `[]` on bad JSON |
| `getSettings`, `setSetting` | `SettingsDao` + codecs | same keys, fallbacks and allowed values |
| dev: `countScansWithNotePrefix`, `purgeScansWithNotePrefix`, `purgePlotsWithNamePrefix` | `DevDao` | `LIKE ? ESCAPE '\'` |

The dynamic filter is a straight port of `whereClause`:

```kotlin
data class ScanCursor(val createdAt: Long, val id: String)

data class ScanFilters(
    val verdicts: List<Verdict>? = null,
    val classIds: List<Int>? = null,
    val corrected: Boolean = false,
    /** Unset = any plot; PlotFilter.None = scans without a plot; PlotFilter.Id = one plot. */
    val plot: PlotFilter = PlotFilter.Any,
    val from: Long? = null,
    val to: Long? = null,
    val search: String? = null,
    val after: ScanCursor? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)

private fun whereClause(f: ScanFilters): Pair<MutableList<String>, MutableList<Any>> {
    val where = mutableListOf("s.deleted_at IS NULL")
    val args = mutableListOf<Any>()
    f.verdicts?.takeIf { it.isNotEmpty() }?.let { v ->
        where += "s.verdict IN (${v.joinToString { "?" }})"; args += v.map { it.key }
    }
    f.classIds?.takeIf { it.isNotEmpty() }?.let { ids ->
        where += """EXISTS (SELECT 1 FROM detections d WHERE d.scan_id = s.id
            AND COALESCE(d.corrected_class_id, d.class_id) IN (${ids.joinToString { "?" }}))"""
        args += ids
    }
    if (f.corrected) where += "EXISTS (SELECT 1 FROM detections d WHERE d.scan_id = s.id AND d.corrected_class_id IS NOT NULL)"
    when (val p = f.plot) {
        PlotFilter.Any -> Unit
        PlotFilter.None -> where += "s.plot_id IS NULL"
        is PlotFilter.Id -> { where += "s.plot_id = ?"; args += p.id }
    }
    f.from?.let { where += "s.created_at >= ?"; args += it }
    f.to?.let { where += "s.created_at <= ?"; args += it }
    f.search?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
        where += "(s.notes LIKE ? ESCAPE '\\' OR p.name LIKE ? ESCAPE '\\')"
        val pattern = "%" + s.replace(Regex("""[\\%_]""")) { "\\" + it.value } + "%"
        args += pattern; args += pattern
    }
    return where to args
}

private fun rawQuery(sql: String, args: List<Any>) = RoomRawQuery(sql) { stmt ->
    args.forEachIndexed { i, a ->
        when (a) {
            is String -> stmt.bindText(i + 1, a)
            is Int -> stmt.bindLong(i + 1, a.toLong())
            is Long -> stmt.bindLong(i + 1, a)
            is Double -> stmt.bindDouble(i + 1, a)
            else -> error("Unsupported arg $a")
        }
    }
}
```

`ScanRow` is a plain data class whose `@ColumnInfo` names match `SCAN_COLUMNS` (including
`plot_name` from the `LEFT JOIN plots`), so `@RawQuery abstract suspend fun scanRows(q:
RoomRawQuery): List<ScanRow>` maps it directly.

**`createScan` validation** (RN → `validateNewScan`): image path required, positive size, every
confidence and box value finite and in `[0, 1]`, and `x + w`, `y + h` ≤ 1.0001. Throw before
the transaction.

**CSV details that change silently in Kotlin** (RN → `exportScansCsv`):
- `created_at`: JS `toISOString()` prints `2026-10-16T08:00:00.000Z` (always milliseconds). Use
  `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)`, not
  `Instant.toString()`.
- Keep the formula guard (`'` prefix for strings starting with `= + - @ \t \r`), quoting rule,
  CRLF line endings, trailing CRLF, and the UTF-8 BOM added when the file is written.
- Header: `id, created_at, verdict, total_pods, <class key>_count…, low_confidence, plot, source,
  model_version, inference_ms, latitude, longitude, notes`.

**Settings codecs** (RN → `settingsRepository.ts`): booleans as `"1"`/`"0"`, numbers parsed with
fallback, theme limited to `system | light | dark`. Same keys, same fallbacks.

### 3.5 File storage

**RN →** `scanFileStore.ts`, `tempImage.ts`, `thumbnails.ts`.

```kotlin
interface ScanFileStore {
    suspend fun copyIn(source: File, relativePath: String)
    suspend fun makeThumbnail(relativeSource: String, relativeDest: String, width: Int, height: Int)
    suspend fun remove(relativePath: String)
    suspend fun removeAll()
    suspend fun usedBytes(): Long
    suspend fun readBytes(relativePath: String): ByteArray
    fun sizeOf(relativePath: String): Long
    fun fileFor(relativePath: String): File      // RN: uriFor
}

const val SCANS_DIR = "scans"
const val THUMBNAIL_SHORT_SIDE = 240
fun scanImagePath(id: String) = "$SCANS_DIR/$id.jpg"
fun scanThumbnailPath(id: String) = "$SCANS_DIR/${id}_thumb.jpg"
```

- Root is `context.filesDir` (RN `Paths.document`); the DB stores only the relative path.
- Thumbnail: shorter side 240 px, JPEG quality 70, **EXIF orientation applied** (RN gets this
  from ImageManipulator). Decode with `inSampleSize` first to avoid loading 12 MP for a 240 px
  thumbnail.
- `TempImages.delete(file)` only deletes files inside `context.cacheDir` and never throws (same
  guard as RN, so a gallery original can never be deleted).
- `backfillThumbnails` (max 200, skips failures, concurrent calls share one run): a `Mutex` +
  cached `Deferred` in a singleton.

**Atomic save** (RN → `saveScan.ts`): new id → copy photo to `scans/<id>.jpg` → thumbnail (failure
is logged and stored as null, not fatal) → `createScan` → on any failure delete both files and
rethrow. `saveResult` maps the Result screen's state into that input (trimmed notes → null if
blank, location only if provided).

---

## Step 4 — Logic layer

### 4.1 Detection pipeline (build and verify first)

**RN →** `src/features/detection/*`. These are pure functions; port them line by line and keep
them free of Android types (pixels arrive as an `IntArray`), so the JUnit tests run on the JVM.
Use `Double` everywhere JS used `number`, so the golden comparison is like for like.

**Letterbox** (RN → `letterbox.ts`):

```kotlin
data class Letterbox(
    val srcWidth: Int, val srcHeight: Int, val size: Int,
    /** Scale factor original -> input (Ultralytics "gain"). */
    val scale: Double,
    val resizedWidth: Int, val resizedHeight: Int,
    val padLeft: Int, val padTop: Int,
)

data class PixelBox(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

/** Python 3 round(). kotlin.math.round already rounds halves to even, so no helper logic. */
fun pythonRound(value: Double): Int = kotlin.math.round(value).toInt()

fun computeLetterbox(srcWidth: Int, srcHeight: Int, size: Int): Letterbox {
    require(srcWidth > 0 && srcHeight > 0 && size > 0) {
        "Invalid letterbox sizes: ${srcWidth}x$srcHeight -> $size"
    }
    val scale = minOf(size.toDouble() / srcHeight, size.toDouble() / srcWidth)
    val resizedWidth = pythonRound(srcWidth * scale)
    val resizedHeight = pythonRound(srcHeight * scale)
    // LetterBox: top = round(dh - 0.1), left = round(dw - 0.1) where dw, dh are half the padding.
    val padLeft = pythonRound((size - resizedWidth) / 2.0 - 0.1)
    val padTop = pythonRound((size - resizedHeight) / 2.0 - 0.1)
    return Letterbox(srcWidth, srcHeight, size, scale, resizedWidth, resizedHeight, padLeft, padTop)
}

/** Input pixels -> original pixels, clipped. Offset recomputed from the exact gain (scale_boxes). */
fun toOriginalPixels(box: PixelBox, lb: Letterbox): PixelBox {
    val padX = pythonRound((lb.size - lb.srcWidth * lb.scale) / 2 - 0.1)
    val padY = pythonRound((lb.size - lb.srcHeight * lb.scale) / 2 - 0.1)
    fun clip(v: Double, max: Int) = v.coerceIn(0.0, max.toDouble())
    return PixelBox(
        clip((box.x1 - padX) / lb.scale, lb.srcWidth),
        clip((box.y1 - padY) / lb.scale, lb.srcHeight),
        clip((box.x2 - padX) / lb.scale, lb.srcWidth),
        clip((box.y2 - padY) / lb.scale, lb.srcHeight),
    )
}
// toInputPixels: the inverse without clipping, same as letterbox.ts (used by tests).
```

**Model spec** (RN → `modelSpec.ts`): port `describeModel` unchanged. Input `[1,3,H,W]` → NCHW,
`[1,H,W,3]` → NHWC, square and equal to `inputSize`; output `[1, 4+C, N]` → features-first,
`[1, N, 4+C]` → anchors-first; both tensors float32; clear error messages otherwise. Your phone
reported input `[1, 3, 640, 640]` and output `[1, 7, 8400]`, so expect NCHW + features-first.

**Preprocess** (RN → `preprocess.ts`):

```kotlin
enum class InputLayout { NCHW, NHWC }

/** 8-bit ARGB pixels (Bitmap.getPixels), row-major. */
class DecodedImage(val width: Int, val height: Int, val argb: IntArray)

fun buildInputTensor(image: DecodedImage, lb: Letterbox, padValue: Int, layout: InputLayout): FloatArray {
    require(image.width == lb.resizedWidth && image.height == lb.resizedHeight) {
        "Resized image is ${image.width}x${image.height}, expected ${lb.resizedWidth}x${lb.resizedHeight}"
    }
    val size = lb.size
    val plane = size * size
    val out = FloatArray(plane * 3) { padValue / 255f }
    for (y in 0 until image.height) {
        val row = (y + lb.padTop) * size + lb.padLeft
        for (x in 0 until image.width) {
            val p = image.argb[y * image.width + x]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val pixel = row + x
            if (layout == InputLayout.NCHW) {
                out[pixel] = r; out[plane + pixel] = g; out[2 * plane + pixel] = b
            } else {
                val o = pixel * 3
                out[o] = r; out[o + 1] = g; out[o + 2] = b
            }
        }
    }
    return out
}
```

**Image decoding** (RN → `imageDecoder.ts`; the only Android-specific step):

```kotlin
/** Full-resolution bitmap with EXIF rotation/flip applied (RN: ImageManipulator.renderAsync). */
fun decodeOriented(file: File): Bitmap {
    val raw = BitmapFactory.decodeFile(file.path) ?: error("Could not decode ${file.name}")
    val exif = ExifInterface(file)
    val matrix = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    return if (matrix.isIdentity) raw
    else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
}

fun resizedPixels(source: Bitmap, width: Int, height: Int): DecodedImage {
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)   // bilinear
    val argb = IntArray(width * height)
    scaled.getPixels(argb, 0, width, 0, 0, width, height)
    if (scaled !== source) scaled.recycle()
    return DecodedImage(width, height, argb)
}
```

Note for the comparison: RN resizes natively, **re-encodes to JPEG at quality 1.0 and decodes
with jpeg-js** (because JS cannot read a native bitmap). Kotlin reads the pixels directly, so it
skips one lossy step and one slow step. Expect slightly different golden deltas and a much
shorter preprocess time; record both.

**Decode, NMS, postprocess** (RN → `decode.ts`, `nms.ts`, `postprocess.ts`): port verbatim.

```kotlin
enum class OutputLayout { FeaturesFirst, AnchorsFirst }
enum class BoxUnits { Normalized, Pixels }
data class Candidate(val box: PixelBox, val classId: Int, val score: Double)

fun decodeOutput(data: FloatArray, spec: ModelSpec, confThreshold: Double): Pair<List<Candidate>, BoxUnits> {
    val anchors = spec.numAnchors
    val features = 4 + spec.numClasses
    require(data.size == anchors * features) { "Output has ${data.size} values, expected ${anchors * features}" }
    val at: (Int, Int) -> Double =
        if (spec.outputLayout == OutputLayout.FeaturesFirst) { f, i -> data[f * anchors + i].toDouble() }
        else { f, i -> data[i * features + f].toDouble() }

    var maxCenter = 0.0
    for (i in 0 until anchors) maxCenter = maxOf(maxCenter, at(0, i), at(1, i))
    val units = if (maxCenter <= 2) BoxUnits.Normalized else BoxUnits.Pixels
    val k = if (units == BoxUnits.Normalized) spec.inputSize.toDouble() else 1.0

    val out = ArrayList<Candidate>()
    for (i in 0 until anchors) {
        var best = 0.0; var classId = -1
        for (c in 0 until spec.numClasses) {
            val s = at(4 + c, i)
            if (s > best) { best = s; classId = c }
        }
        if (classId < 0 || best <= confThreshold) continue
        val cx = at(0, i) * k; val cy = at(1, i) * k; val w = at(2, i) * k; val h = at(3, i) * k
        out += Candidate(PixelBox(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), classId, best)
    }
    return out to units
}

fun iou(a: PixelBox, b: PixelBox): Double {
    val iw = minOf(a.x2, b.x2) - maxOf(a.x1, b.x1)
    val ih = minOf(a.y2, b.y2) - maxOf(a.y1, b.y1)
    if (iw <= 0 || ih <= 0) return 0.0
    val inter = iw * ih
    val union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter
    return if (union > 0) inter / union else 0.0
}

/** Class-aware NMS, best first, at most maxDetections (Ultralytics agnostic=False). */
fun nonMaxSuppression(candidates: List<Candidate>, iouThreshold: Double, maxDetections: Int = 300): List<Candidate> {
    val kept = ArrayList<Candidate>()
    for (c in candidates.sortedByDescending { it.score }) {   // stable sort, like JS
        if (kept.size >= maxDetections) break
        if (kept.none { it.classId == c.classId && iou(it.box, c.box) > iouThreshold }) kept += c
    }
    return kept
}

/** Final detection: normalized 0–1, top-left origin, relative to the original image. */
data class Detection(val classId: Int, val confidence: Double, val x: Double, val y: Double, val w: Double, val h: Double)
// postprocess(): decode -> NMS -> toOriginalPixels -> drop empty boxes -> normalize. Same as postprocess.ts.
```

**Verdict** (RN → `verdict.ts`):

```kotlin
enum class Verdict(val key: String) { Healthy("healthy"), EarlyStage("early_stage"), Infected("infected"), NoPod("no_pod") }

data class VerdictSummary(val verdict: Verdict, val totalPods: Int, val lowConfidence: Boolean)

/** Most severe class present; no detections is NoPod, never Healthy. */
fun summarizeDetections(detections: List<Pair<Int, Double>>, lowConfidenceThreshold: Double): VerdictSummary {
    if (detections.isEmpty()) return VerdictSummary(Verdict.NoPod, 0, false)
    val verdict = when (val s = detections.maxOf { it.first }) {
        0 -> Verdict.Healthy; 1 -> Verdict.EarlyStage; 2 -> Verdict.Infected
        else -> error("Unknown severity $s")
    }
    return VerdictSummary(verdict, detections.size, detections.maxOf { it.second } < lowConfidenceThreshold)
}
```

(`Pair(severity, confidence)`; a small data class reads better if you prefer.)

**Errors** (RN → `errors.ts`): one error type tagged by stage.

```kotlin
enum class DetectionErrorKind { Image, Model, Inference, Memory }

class DetectionError(kind: DetectionErrorKind, cause: Throwable) : Exception(cause.message, cause) {
    val kind: DetectionErrorKind =
        if (cause is OutOfMemoryError || isOutOfMemory(cause.message)) DetectionErrorKind.Memory else kind
}

private fun isOutOfMemory(message: String?) =
    message != null && Regex("""out ?of ?memory|OutOfMemoryError|\bOOM\b|allocation failed|bad_alloc""",
        RegexOption.IGNORE_CASE).containsMatchIn(message)

suspend fun <T> inStage(kind: DetectionErrorKind, task: suspend () -> T): T =
    try { task() }
    catch (e: CancellationException) { throw e }          // never wrap coroutine cancellation
    catch (e: DetectionError) { throw e }
    catch (e: Throwable) { throw DetectionError(kind, e) } // Throwable: OutOfMemoryError is an Error

// describeDetectionError(e): kind -> (R.string.processing_<kind>_error_title, ..._body)
```

**Model loader** (RN → `modelLoader.ts`). Photos use the LiteRT `Interpreter` on CPU: it is the
same classic TFLite runtime react-native-fast-tflite wraps, it exposes tensor shapes for
`describeModel`, and it is the path the golden test verifies. Load once, do not cache failures.

```kotlin
class LoadedModel(private val interpreter: Interpreter, val spec: ModelSpec, val loadMs: Long) {
    private val outputSize = spec.outputShape.fold(1) { a, b -> a * b }

    /** Interpreter is not thread-safe; one run at a time. */
    fun run(input: FloatArray): FloatArray = synchronized(interpreter) {
        val inBuf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
        inBuf.asFloatBuffer().put(input)
        val outBuf = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
        interpreter.run(inBuf, outBuf)
        outBuf.rewind()
        FloatArray(outputSize).also { outBuf.asFloatBuffer().get(it) }
    }
}

class ModelLoader(private val context: Context, private val config: ModelConfig) {
    private val mutex = Mutex()
    private var cpu: LoadedModel? = null

    suspend fun loadCpu(): LoadedModel = mutex.withLock {
        cpu ?: inStage(DetectionErrorKind.Model) {
            withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                val interpreter = Interpreter(mapAsset("models/${config.file}"))
                val inputs = (0 until interpreter.inputTensorCount).map { interpreter.getInputTensor(it).info() }
                val outputs = (0 until interpreter.outputTensorCount).map { interpreter.getOutputTensor(it).info() }
                LoadedModel(interpreter, describeModel(inputs, outputs, config), SystemClock.elapsedRealtime() - start)
            }
        }.also { cpu = it }
    }

    private fun mapAsset(path: String): MappedByteBuffer =
        context.assets.openFd(path).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
}
// Tensor.info() = TensorInfo(name(), dataType().name.lowercase(), shape().toList())
```

Leave `Interpreter.Options` at defaults for the parity run (fast-tflite uses defaults); try
`setNumThreads(4)` afterwards as a separate, labelled experiment.

**Live backend** (RN → `loadDetectionModel(config, 'gpu')`): LiteRT 2.x only offers GPU through
the `CompiledModel` API (the `Interpreter` GPU delegate is v1 only). Mirror RN's fallback: try GPU,
on failure show the notice and use the CPU `LoadedModel`.

```kotlin
interface LiveRunner { fun run(input: FloatArray): FloatArray; val backend: String }

fun gpuRunner(context: Context, config: ModelConfig): LiveRunner {
    val model = CompiledModel.create(context.assets, "models/${config.file}", CompiledModel.Options(Accelerator.GPU))
    val inputs = model.createInputBuffers()
    return object : LiveRunner {
        override val backend = "gpu"
        override fun run(input: FloatArray): FloatArray {
            inputs[0].writeFloat(input)
            return model.run(inputs)[0].readFloat()
        }
    }
}
```

The tensor layout for live comes from the CPU spec (`describeModel` once), since both backends
run the same file.

**Run detection** (RN → `runDetection.ts`):

```kotlin
data class DetectionTimings(val preprocessMs: Double, val inferenceMs: Double, val postprocessMs: Double, val totalMs: Double)
data class DetectionRun(val detections: List<Detection>, val boxUnits: BoxUnits, val candidateCount: Int,
                        val imageWidth: Int, val imageHeight: Int, val timings: DetectionTimings)

suspend fun runDetection(loaded: LoadedModel, image: File, padValue: Int, thresholds: Thresholds): DetectionRun =
    withContext(Dispatchers.Default) {
        val t0 = System.nanoTime()
        val (size, lb, input) = inStage(DetectionErrorKind.Image) {
            val bitmap = decodeOriented(image)
            val box = computeLetterbox(bitmap.width, bitmap.height, loaded.spec.inputSize)
            val pixels = resizedPixels(bitmap, box.resizedWidth, box.resizedHeight)
            val size = bitmap.width to bitmap.height
            bitmap.recycle()
            Triple(size, box, buildInputTensor(pixels, box, padValue, loaded.spec.inputLayout))
        }
        val t1 = System.nanoTime()
        val raw = inStage(DetectionErrorKind.Inference) { loaded.run(input) }
        val t2 = System.nanoTime()
        val result = inStage(DetectionErrorKind.Inference) { postprocess(raw, loaded.spec, lb, thresholds) }
        val t3 = System.nanoTime()
        fun ms(a: Long, b: Long) = (b - a) / 1e6
        DetectionRun(result.detections, result.boxUnits, result.candidateCount, size.first, size.second,
            DetectionTimings(ms(t0, t1), ms(t1, t2), ms(t2, t3), ms(t0, t3)))
    }
```

**Golden test** (RN → `golden.ts` + Settings → Model test): port `compareToReference` (greedy
same-class match by IoU, tolerance IoU ≥ 0.9, |Δconf| ≤ 0.05, no extras) to `features/detection`,
then:
- a JVM test for the comparison logic itself, and
- an instrumented test (`androidTest`) plus the dev **Model test** screen that runs the pipeline on
  each `sample/golden/*.jpg` and prints tensor shapes, timings and PASS/FAIL per image.
  Compare its table with the "Device results (2026-10-15)" table in `assets/sample/README.md`.

### 4.2 Feature logic (non-UI)

Port each file as a plain Kotlin function or small class:

| RN file | Kotlin | What to keep identical |
| --- | --- | --- |
| `features/scans/analyzePhoto.ts` | `AnalyzePhoto(container).invoke(photo): ScanResult` | thresholds from **settings table**, severities from **classes table**, low-confidence from `modelConfig`, `modelVersionId = activeModelId.ifEmpty { config.id }`, writes nothing, timings rounded |
| `resultEdits.ts` | `correctResultDetection(result, index, classId, severities)` | picking the model's class clears; verdict recomputed; lowConfidence unchanged |
| `saveScan.ts`, `saveResult.ts` | `saveScan(...)`, `saveResult(...)` | atomic save as in 3.5 |
| `historyFilters.ts` | `HistoryFilter`, `rangeStart`, `toScanFilters`, `activeFilterCount`, `groupByDay`, `dayLabel`, `formatShortWhen`, `formatDateTime` | ranges: today = start of today, week = start of day 6 days ago, month = 29 days ago (local time). Use `java.time` (`LocalDate`, `ZoneId.systemDefault()`; available from API 26) and `DateFormat.getBestDateTimePattern(locale, "EEEdMMM")` for "Mon 12 Oct" |
| `verdictText.ts` | `verdictTitle(verdict, total, atVerdict)` | the 4 sentences from strings |
| `cameraAccess.ts` | `cameraAccessFrom(granted, everAsked, showRationale)` | see 5.4 Camera |
| `settings/threshold.ts` | `THRESHOLD_MIN 0.1, MAX 0.9, STEP 0.05`, `snapThreshold` | snap without float noise: `"%.2f".format(Locale.ROOT, …).toDouble()` |
| `settings/dataTools.ts` | `getStorageSummary`, `formatBytes`, `csvFileName`, `shareScansCsv`, `clearAllData` | `formatBytes` uses KB below 1 MB, one decimal; file `yolo-c-scans-YYYY-MM-DD.csv` in local time; returns false when there are no scans |
| `settings/location.ts` | `LocationAccess`, `captureScanLocation()` | permission check first; last known fix ≤ 5 min old and ≤ 100 m accuracy, else a fresh balanced-accuracy fix within 8 s (`withTimeoutOrNull`), null on any failure |
| `export/retraining.ts` | `writeRetrainingZip(...)`, `yoloLabels`, `dataYaml`, `readme`, `summarizeExport` | same layout and text; scopes: corrected / early (`severity == 1`, not a hardcoded key) / all; one photo in memory at a time; skipped count. Format numbers with `Locale.ROOT` (`"%.6f"`) or labels break on comma-decimal phones |
| `export/shareRetrainingZip.ts` | `shareRetrainingZip(...)` | file `yolo-c-retraining-<scope>-YYYY-MM-DD.zip` in `cacheDir`, replaced on re-export; `ZipOutputStream` with level 0 for photos and 6 for text |
| `live/liveMath.ts` | `frameLetterbox`, `liveDetections` (max 20), `previewTransform`, `toViewBox`, `LiveStats`, `LIVE_PRACTICAL` | same math; 10 s window; percentiles |
| `camera/viewfinderMath.ts` | `VIEWFINDER_ASPECT = 3f/4f`, `fitAspect` | |
| `learn/topics.ts`, `onboarding/steps.ts` | enums / constants | |
| `utils/uuid.ts` | `UUID.randomUUID().toString()` | |
| `dev/fakeScans.ts`, `seedFakeScans.ts` | same, mulberry32 PRNG ported | fake scans go through the real save path |

### 4.3 Global state (Zustand stores → state holders)

**RN →** `src/store/*.ts`. Zustand stores are app-wide singletons that any screen can read. In
Kotlin the equivalent is a singleton class in `AppContainer` exposing `StateFlow`. ViewModels
read and call them; Composables never touch them directly.

```kotlin
/** RN: scanSessionStore. The unsaved photo and result; nothing is in the DB until Save. */
class ScanSession(private val temp: TempImages) {
    data class State(val photo: PendingPhoto? = null, val result: ScanResult? = null)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** New photo for the flow; replaces (and deletes) any previous unsaved photo. */
    fun setPhoto(photo: PendingPhoto) {
        _state.value.photo?.let { if (it.file != photo.file) temp.delete(it.file) }
        _state.value = State(photo = photo)
    }
    fun setResult(result: ScanResult) = _state.update { it.copy(result = result) }
    fun clear(keepPhoto: Boolean = false) {
        if (!keepPhoto) temp.delete(_state.value.photo?.file)
        _state.value = State()
    }
}

/** RN: scanLibraryStore. `revision` is bumped after any change to saved scans. */
class ScanLibrary(private val scans: ScanRepository) {
    data class PendingUndo(val scanId: String, val token: Int)
    private var nextToken = 1
    val revision = MutableStateFlow(0)
    val pendingUndo = MutableStateFlow<PendingUndo?>(null)

    fun bump() = revision.update { it + 1 }
    suspend fun deleteScan(id: String) {
        scans.softDeleteScan(id)
        pendingUndo.value = PendingUndo(id, nextToken++)
        bump()
    }
    suspend fun undoDelete() {
        val pending = pendingUndo.value ?: return
        pendingUndo.value = null
        scans.restoreScan(pending.scanId)
        bump()
    }
    fun dismissUndo(token: Int) { if (pendingUndo.value?.token == token) pendingUndo.value = null }
    companion object { const val UNDO_WINDOW_MS = 6000L }
}
```

`SettingsStore` (RN → `settingsStore.ts`): `StateFlow<SettingsState>` with `status (Idle /
Loading / Ready / Error)`, `onboardingDone`, `theme`, `confThreshold`, `gpsOptIn`; `hydrate()`
opens the DB (migrate + seed) and reads settings, sharing one in-flight load; each setter updates
the flow first, then writes, logging (not throwing) a failed write. Same "optimistic then persist"
rule as RN.

> Room can emit a `Flow` that re-queries whenever a table changes, which would make `revision`
> unnecessary. Keep `revision` for the first port so both apps refresh at the same moments; switch
> to Room flows later as a separate, measured change if you want.

### 4.4 ViewModels

Each RN screen keeps its own `useState` / `useEffect` logic. In Kotlin that logic moves into one
`ViewModel` per screen that exposes a single `StateFlow<UiState>` and plain functions for events.
Composables collect with `collectAsStateWithLifecycle()` and stay stateless apart from purely
visual state (scroll position, which row is pressed).

Create them through a factory that reads the container (no Hilt):

```kotlin
private val CreationExtras.container get() = (this[APPLICATION_KEY] as YoloCApp).container

class HomeViewModel(private val scans: ScanRepository, library: ScanLibrary) : ViewModel() {
    sealed interface Summary {
        data object Loading : Summary
        data object Error : Summary
        data class Ready(val recent: List<ScanSummary>, val stats: ScanStats) : Summary
    }
    private val attempt = MutableStateFlow(0)

    /** RN useHomeSummary: reload on revision or Retry; a failed refresh keeps what is shown. */
    val summary: StateFlow<Summary> = combine(library.revision, attempt) { _, _ -> }
        .mapLatest {
            runCatching {
                Summary.Ready(scans.listScans(ScanFilters(limit = 3)),
                    scans.getStats(rangeStart(DateRange.Month, System.currentTimeMillis())!!))
            }.onFailure { Log.w(TAG, "Could not load Home summary", it) }.getOrNull()
        }
        .runningFold(Summary.Loading as Summary) { prev, next -> next ?: if (prev is Summary.Ready) prev else Summary.Error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Summary.Loading)

    fun reload() = attempt.update { it + 1 }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(container.scanRepository, container.scanLibrary) }
        }
    }
}
```

Screens with route arguments read them from `SavedStateHandle`:

```kotlin
initializer {
    val route = createSavedStateHandle().toRoute<ScanDetail>()
    ScanDetailViewModel(route.id, route.saved, container.scanRepository, container.scanLibrary, ...)
}
```

The full list (what each replaces and owns):

| ViewModel | Replaces (RN) | State | Events |
| --- | --- | --- | --- |
| `RootViewModel` | `app/_layout.tsx` | settings status, theme, onboardingDone | `hydrate()`, retry |
| `OnboardingViewModel` | `welcome.tsx`, `permissions.tsx` | slide index | next, back, skip, `completeOnboarding()` |
| `HomeViewModel` | `index.tsx`, `useHomeSummary`, `useNow` | summary, today's tip | reload, open count → History request |
| `HistoryViewModel` | `history.tsx`, `useScanList`, `HistoryFilterBar` state | filter, plotName, rows (grouped), total, status, loadingMore, hidden ids, optionsFor, deleteError | setFilter, search (debounced 250 ms), loadMore (keyset, 40), delete (optimistic hide), retry, consume incoming filter request, backfill thumbnails on start |
| `ScanDetailViewModel` | `history/[id].tsx` | load state, toast, deleting, notes draft/saved/saving/failed | correct, saveNotes (≤ 500 chars), delete |
| `CameraViewModel` | `camera.tsx`, `useCameraAccess`, `useLatestScanImage` | access, flash, capturing, picking, notice, latest image, mountError | onPermissionResult, recheck on resume, capture result, gallery result |
| `LiveViewModel` | `live.tsx` | backend, loaded, loadError, notice, latest result, stats snapshot, capturing | frame analyzed, switch backend (dev), capture |
| `PreviewViewModel` | `preview.tsx` | photo, loadFailed | retake, choose another, use photo |
| `ProcessingViewModel` | `processing.tsx` | Running / Failed(errorView) | start (cancels on leave; a late result is ignored), retry |
| `ResultViewModel` | `result.tsx`, `SaveScanSheet` state | result, classes, saveOpen, saving, saveError, exit, location `Deferred` | correct, openSave, save(plotId, notes), discard, back-pressed → confirm |
| `LearnTopicViewModel` | `learn/[topic].tsx` | classes (sorted by severity) | — |
| `SettingsViewModel` | `settings.tsx` | threshold draft, theme, gps + location access + refused, storage summary, exporting, clearing, retrainOpen, toast | slider change/end, reset, theme, toggle gps, try again, export CSV, clear all (confirm), retrain done/error |
| `RetrainingExportViewModel` | `RetrainingExportSheet.tsx` | per-scope summaries, scope, free space, exporting | choose scope, export |
| `PlotsViewModel` | `plots.tsx` | load, name input, error, renaming | add (dedupe), rename, delete (confirm), retry |
| `AboutViewModel` | `about.tsx` | classes | — |
| `DevInferenceViewModel`, `DevSeedViewModel` | `dev/inference.tsx`, `dev/seed.tsx` | run reports / counts | run golden, seed N, remove fakes |

Two RN hooks become small Compose utilities instead of ViewModels:
- `useReduceMotion` → `rememberReduceMotion()`: true when
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`; skip or shorten animations when true.
- `useAppInForeground` → `LocalLifecycleOwner` + `LifecycleEventEffect(ON_RESUME / ON_PAUSE)`.

---

## Step 5 — UI

### 5.1 Theme and tokens

**RN →** `src/theme/palette.ts`, `themes.ts`, `tokens.ts`, `contrast.ts`, `ThemeProvider.tsx`.

- `Palette.kt`: the same hex values (`forest900 0xFF0F2A1D`, … `brickLight 0xFFDE7A68`,
  `sageChip 0xFF7D9E80`, light and dark neutrals).
- `Themes.kt`: a `YoloCColors` data class with the same fields as RN's `Theme.colors`
  (background, surface, surfaceRaised, selected, text, textMuted, textFaint, icon, border,
  outline, focus, link, scrim, boxHalo, inverse, primary/secondary/ghost/onDark button colors,
  disabled, tabBar, camera) and `status: Map<StatusKey, StatusColors>` (stroke, ink, fill,
  onFill). Build `lightColors` and `darkColors` with the same values as `themes.ts`.
- `Tokens.kt`: `Spacing` (4, 8, 12, 16, 20 screen, 24, 32, 48 dp), `Radii` (4, 10, full),
  `TouchTarget = 48.dp`, `Motion` (150 / 200 / 250 ms, press 100 ms), `BoxStroke` (3 dp + 1 dp
  halo), `CameraBarOpacity = 0.94f`.
- `Type.kt`: `Barlow` and `BarlowSemiCondensed` `FontFamily`s from `res/font`, and a
  `YoloCType` object with the same variants and sizes: headline 28/32, verdict 22/28, title
  18/24, bodyStrong and body 16/24, label 16/20, caption 13/16, data 18/24, figure 28/32, readout
  44/48. Tabular numerals: `fontFeatureSettings = "tnum"` on data, figure and readout.
- `YoloCTheme(preference)`: picks light/dark (`isSystemInDarkTheme()` for `system`), provides
  `LocalYoloCColors` and `LocalStatusColors` through `CompositionLocalProvider`, and wraps
  `MaterialTheme` with a `ColorScheme` mapped from the same tokens so M3 widgets (Slider, Switch,
  ModalBottomSheet, AlertDialog) match.
- System bars: `enableEdgeToEdge()` in `MainActivity`; set light/dark bar icons from the theme
  with `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars`; camera
  screens force light icons while focused (RN: `<StatusBar style="light" />` on the camera).
  Set the window background to `colors.background` so dark mode never flashes white (RN:
  `SystemUI.setBackgroundColorAsync`).
- `Contrast.kt` + `ContrastTest.kt`: port `contrastRatio`, `AA` and the `contrastPairs(theme)`
  list; the JUnit test fails on any pair below AA, exactly like `__tests__/contrast.test.ts`.

### 5.2 Components

**RN →** `src/components/*`. Same names, same props, so screens translate almost word for word.

| RN component | Compose | Notes |
| --- | --- | --- |
| `ScreenContainer` (scroll, edges, footer, contentStyle) | `ScreenContainer(scroll, footer, content)` | `Column` + `verticalScroll`, 20 dp padding, footer pinned above the nav bar with `WindowInsets.navigationBars` |
| `AppText` (variant, tone, color) | `AppText(text, variant, tone, color)` | `Text` with `YoloCType` |
| `Button` (primary / secondary / ghost / onDark, size lg, icon, loading, fullWidth, disabled) | `YButton(...)` | custom `Surface` + `Row`, min 48 dp, radius 4 dp, spinner when loading |
| `Card` | `Card` | bordered panel, radius 10 dp, no shadow |
| `StatusChip`, `VerdictChip`, `statusIcon`, `verdictStatus` | same | icon + label + fill, never color alone |
| `VerdictLine` (verdict, title, detail, lowConfidence) | same | uncertain banner above the verdict |
| `ReadoutStrip` | same | only used by the dev gallery today |
| `DetectionListItem` | same | `#n`, status icon, label, "corrected from …", right-aligned tabular %, selected fill + eye icon |
| `ImageWithBoxes` | `ImageWithBoxes` | Coil `AsyncImage` + `Canvas` overlay: 3 dp stroke in status color with 1 dp white halo, label chip "class + %", tap hit-test on boxes, selected box emphasized |
| `EmptyState`, `ErrorState` | same | icon, title, body, tips, action / Retry + Back |
| `BottomSheet` | `ModalBottomSheet` wrapper | back and scrim close it |
| `Toast`, `UndoDeleteSnackbar` | custom composables | Undo snackbar reads `ScanLibrary.pendingUndo`, auto-dismiss after 6 s |
| `SectionHeader` (title, subtitle, action) | same | |
| `SegmentedControl` | custom row or M3 `SingleChoiceSegmentedButtonRow` | icons + labels |
| `Slider` (min, max, step, marker, onChange, onChangeEnd) | M3 `Slider` (`steps`, `onValueChangeFinished`) + drawn default marker | |
| `TabBar` (center Scan pill) | `AppTabBar` | 4 tabs + emphasized (not raised) center Scan button |
| `Icon` | `Icon(painterResource(R.drawable.ic_x))` wrapper with `IconName` | |
| `RecallPods` | same | 10 pods, `found` filled |
| `features/scans/SwipeToDelete` | M3 `SwipeToDismissBox` (end-to-start only, 40 % threshold) | plus long-press and a `CustomAccessibilityAction("Delete")` |

### 5.3 Navigation

**RN →** Expo Router file routes, `app/_layout.tsx`, `app/(tabs)/_layout.tsx`.

```kotlin
@Serializable data object OnboardingGraph
@Serializable data object Welcome
@Serializable data object Permissions

@Serializable data object MainGraph
@Serializable data object Home
@Serializable data object History
@Serializable data object Learn
@Serializable data object Settings

@Serializable data object Camera
@Serializable data object Live
@Serializable data object Preview
@Serializable data object Processing
@Serializable data object Result
@Serializable data class ScanDetail(val id: String, val saved: Boolean = false)
@Serializable data class LearnTopic(val topic: String)
@Serializable data object About
@Serializable data object Plots
@Serializable data object DevGallery
@Serializable data object DevSeed
@Serializable data object DevInference
```

| RN route | Kotlin route | Top bar | Bottom bar |
| --- | --- | --- | --- |
| `/(onboarding)/welcome`, `/permissions` | `Welcome`, `Permissions` in `OnboardingGraph` | none | no |
| `/(tabs)/index`, `history`, `learn`, `settings` | `Home`, `History`, `Learn`, `Settings` in `MainGraph` | none (own headline) | yes |
| `/scan/camera`, `/scan/live` | `Camera`, `Live` | none, dark, fade transition | no |
| `/scan/preview`, `processing`, `result` | `Preview`, `Processing`, `Result` | back arrow + title | no |
| `/history/[id]` | `ScanDetail(id, saved)` | back arrow + "Scan" | no |
| `/learn/[topic]` | `LearnTopic(topic)` | back arrow + topic title | no |
| `/about`, `/plots` | `About`, `Plots` | back arrow + title | no |
| `/dev/*` | `Dev*` (only added when `BuildConfig.DEBUG`) | | no |

**Root and onboarding gate** (RN → `Stack.Protected guard={onboardingDone}`):

```kotlin
@Composable
fun YoloCRoot(vm: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    YoloCTheme(preference = s.theme) {
        when (s.status) {
            Status.Error -> ErrorState(message = stringResource(R.string.startup_db_error_body), onRetry = vm::hydrate)
            Status.Ready -> YoloCNavHost(onboardingDone = s.onboardingDone)
            else -> Box(Modifier.fillMaxSize().background(LocalYoloCColors.current.background))
        }
    }
}

@Composable
fun YoloCNavHost(onboardingDone: Boolean) {
    val nav = rememberNavController()
    // Chosen once: changing a NavHost's startDestination later would rebuild the graph.
    val start: Any = remember { if (onboardingDone) MainGraph else OnboardingGraph }
    // Flipping the flag swaps graphs and drops the other graph's history, so back from Home
    // exits instead of returning to onboarding (and "Show onboarding again" works the other way).
    LaunchedEffect(onboardingDone) {
        val inMain = nav.currentDestination?.hierarchy?.any { it.hasRoute<MainGraph>() } == true
        if (onboardingDone && !inMain) nav.navigate(MainGraph) { popUpTo<OnboardingGraph> { inclusive = true } }
        if (!onboardingDone && inMain) nav.navigate(OnboardingGraph) { popUpTo<MainGraph> { inclusive = true } }
    }
    val entry by nav.currentBackStackEntryAsState()
    val isTab = entry?.destination?.let { d -> listOf(Home::class, History::class, Learn::class, Settings::class).any { d.hasRoute(it) } } == true

    Scaffold(bottomBar = { if (isTab) AppTabBar(nav, entry) }) { padding ->
        NavHost(nav, startDestination = start, Modifier.padding(padding)) {
            navigation<OnboardingGraph>(startDestination = Welcome) {
                composable<Welcome> { WelcomeScreen(onDone = { nav.navigate(Permissions) }) }
                composable<Permissions> { PermissionsScreen() }   // calls completeOnboarding()
            }
            navigation<MainGraph>(startDestination = Home) {
                composable<Home> { HomeScreen(nav) }
                composable<History> { HistoryScreen(nav) }
                composable<Learn> { LearnScreen(nav) }
                composable<Settings> { SettingsScreen(nav) }
                composable<Camera>(enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) { CameraScreen(nav) }
                composable<Live> { LiveScreen(nav) }
                composable<Preview> { PreviewScreen(nav) }
                composable<Processing> { ProcessingScreen(nav) }
                composable<Result> { ResultScreen(nav) }
                composable<ScanDetail> { ScanDetailScreen(nav) }
                composable<LearnTopic> { LearnTopicScreen(nav) }
                composable<About> { AboutScreen(nav) }
                composable<Plots> { PlotsScreen(nav) }
                if (BuildConfig.DEBUG) { /* Dev* routes */ }
            }
        }
    }
}
```

**Tab behaviour** (RN → `backBehavior="firstRoute"` and the custom `AppTabBar`): selecting a tab
navigates with `popUpTo<Home> { saveState = true }; launchSingleTop = true; restoreState = true`,
so back from any tab lands on Home and back from Home exits. The center **Scan** item is not a
tab: it calls `nav.navigate(Camera)`.

**Navigation actions, RN → Kotlin:**

| RN | Kotlin |
| --- | --- |
| `router.push('/scan/camera')` | `nav.navigate(Camera)` |
| Camera close: `canGoBack() ? back() : navigate('/')` | `if (!nav.popBackStack()) nav.navigate(Home)` |
| ModeSwitch: `router.replace('/scan/live')` and back | `nav.navigate(Live) { popUpTo<Camera> { inclusive = true } }` (and the reverse) |
| Live capture: `router.replace('/scan/preview')` | `nav.navigate(Preview) { popUpTo<Live> { inclusive = true } }` |
| Processing → Result: `router.replace('/scan/result')` | `nav.navigate(Result) { popUpTo<Processing> { inclusive = true } }` |
| `router.dismissTo('/scan/camera')` (Retake, Discard, missing photo) | `if (!nav.popBackStack<Camera>(inclusive = false)) nav.navigate(Camera) { popUpTo<Home>() }` |
| Saved: `dismissTo('/history')` + push `/history/[id]?saved=1` | `nav.navigate(History) { popUpTo<Home>(); launchSingleTop = true }` then `nav.navigate(ScanDetail(id, saved = true))` |
| Home chip / Plots row: `navigate('/history', {verdict})` / `{plotId, plotName}` | `historyRequests.send(HistoryRequest.Verdict(v))` / `.Plot(id, name)`, then select the History tab. `HistoryViewModel` consumes and clears the request (RN: applies the params, then `setParams(undefined)`) |
| `usePreventRemove` on Result | `BackHandler(enabled = guarded)` + the top bar's back arrow both open the Stay / Discard / Save dialog |
| `Alert.alert(...)` | `AlertDialog` driven by ViewModel state |
| `Linking.openSettings()` | `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))` |

`historyRequests` is a tiny singleton (`MutableStateFlow<HistoryRequest?>`) in `AppContainer`.

### 5.4 Screens

For each screen: what it shows, its ViewModel, and how the two connect. The layout and copy are
those of the RN screen named in the heading; open both side by side while building.

#### Welcome + Permissions (`app/(onboarding)/welcome.tsx`, `permissions.tsx`)
- `OnboardingFrame(step, total = slides + 1, title, body, illustration, primary, onSkip)` with the
  capsule progress row. Slides from strings: scan, offline, tips (4 tips from `tipPicks`),
  aid (`RecallPods`, "about 4 in 10 missed" from `modelConfig.metrics.earlyStageRecall`).
- `BackHandler(enabled = index > 0) { vm.back() }` (RN: `hardwareBackPress` returning true).
- Permissions requests nothing. **Continue** → `settingsStore.completeOnboarding()`; the
  `LaunchedEffect(onboardingDone)` in the NavHost swaps graphs.

#### Home (`app/(tabs)/index.tsx`) — `HomeViewModel`
- `summary` drives: 3 `CountChip`s (`stats.scansByVerdict[v]`, "–" while loading), recent list
  (`RecentSkeleton` / error `Card` + Retry → `vm.reload()` / empty `Card` / up to 3 `ScanRow`s with
  `formatShortWhen`), tip of the day (`tips[(startOfDay(now) / DAY_MS) % tips.size]`).
- Events: Scan CTA → `Camera`; chip → History request + tab; row → `ScanDetail(id)`; See all →
  History tab; Learn more → Learn tab. `UndoDeleteSnackbar` at the bottom.

#### History (`app/(tabs)/history.tsx`) — `HistoryViewModel`
- Header: headline + count (`plurals`), `HistoryFilterBar` (search field with 250 ms debounce in
  the ViewModel, Status / Date / Plot pills, each opening a `BottomSheet`; Plot sheet lists plots
  + "No plot").
- Body by state: error + Retry; skeleton rows; empty state with Scan CTA; `LazyColumn` of
  `groupByDay` items (`key = item.key`, `contentType = item.type`), day headers, `ScanRow`s inside
  swipe-to-delete; load more when the last visible index is near the end (RN
  `onEndReachedThreshold 0.6`); no-match state with Clear filters; footer spinner.
- Delete: hide the row at once (`hidden` set), call `scanLibrary.deleteScan`; on failure unhide
  and show the 4 s error toast; the hidden set is cleared on the next reload.
- On first composition: start `backfillThumbnails`; bump the revision if any were written.
- Long-press → options sheet (Open / Delete).
- `ScanRow`: 72 dp thumbnail via Coil (thumbnail or full photo decoded at view size; missing file
  → icon), `VerdictChip`, time, "3 pods · North block", uncertain flag, first line of notes, one
  merged content description.

#### Scan Detail (`app/history/[id].tsx`) — `ScanDetailViewModel`
- Loads `getScan(id)` on start and whenever `revision` changes; a failed refresh keeps the scan.
- `ScanReport(...)` shared with Result (see below), with `onCorrect` → `correctDetection` then
  `bump()`.
- `NotesEditor`: text field (max 500), outline highlights when dirty, Save button only when dirty.
- Details `Card`, Delete → confirm dialog → `scanLibrary.deleteScan` → `popBackStack()` (Undo is
  shown on the screen you return to). `saved = true` shows the "Saved" toast for 3 s.

#### Learn + Learn topic (`app/(tabs)/learn.tsx`, `app/learn/[topic].tsx`)
- Learn is static: three topic rows (the first emphasized) + draft note; tap → `LearnTopic(topic)`.
- `LearnTopicScreen` sets the top bar title from the topic; unknown topic → `ErrorState`.
  Stages uses `LearnTopicViewModel.classes` (from the `classes` table, mildest first). How it works
  links to About and Settings.

#### Settings (`app/(tabs)/settings.tsx`) — `SettingsViewModel` + `RetrainingExportViewModel`
- Detection: big percent readout, `YSlider` (draft value while dragging, `snapThreshold` on
  release → `settingsStore.setConfThreshold`), default marker, "More pods / Fewer pods" ends, help,
  "applies to the next scan", Reset button only when not default.
- Appearance: `SegmentedControl` → `settingsStore.setTheme`; the root theme follows immediately.
- Location: switch row. Turning on → `rememberLauncherForActivityResult(RequestMultiplePermissions)`
  for coarse + fine; granted → `setGpsOptIn(true)`; refused → message + Try again, or Open
  settings when Android no longer shows the prompt. Re-check permission on `ON_RESUME`; show the
  "revoked" message if it was on and permission is gone.
- Data: Plots row (count) → `Plots`; Storage row (scans + `formatBytes`); Export CSV (busy while
  running, "nothing to export" toast, error toast) → `shareScansCsv` → share chooser; Export for
  retraining → sheet; Clear all data → dialog with counts → `clearAllData` → clear `pendingUndo`,
  `bump()`, toast. Storage numbers reload on `revision` and each time the screen resumes.
- App: About, Show onboarding again (`resetOnboarding()`), dev rows when `BuildConfig.DEBUG`.

#### Camera (`app/scan/camera.tsx`) — `CameraViewModel`
- Permission state (RN `cameraAccessFrom`): Android has no `canAskAgain`, so derive it:
  granted → `Granted`; never requested (a flag you save in `SharedPreferences` on first request)
  → `Ask`; requested and `shouldShowRequestPermissionRationale` → `Denied`; requested and no
  rationale → `Blocked`. Re-check on `ON_RESUME` (RN: `AppState` "active").
- `Viewfinder { … }`: 3:4 frame (`fitAspect`), outline, corner ticks, and a ring in the screen
  color over the corners (camera surfaces ignore rounded clipping on Android, same reason as RN).
  Inside: `LensPanel` for Ask / Denied / Blocked / mount error, else the preview.
- Preview: bind `Preview` + `ImageCapture` (`setJpegQuality(90)`, flash mode from state) with
  `ProcessCameraProvider.bindToLifecycle(lifecycleOwner, DEFAULT_BACK_CAMERA, …)`, show it with
  `CameraXViewfinder(surfaceRequest)`. Bind only while the screen is resumed (CameraX unbinds
  with the lifecycle, which covers RN's "focused and in foreground" rule). Report readiness when
  the first surface request arrives; a binding exception → mount error.
- Capture: `takePicture(OutputFileOptions(File(cacheDir, "capture-<uuid>.jpg")), …)`, read the
  oriented size with `ExifInterface`, `scanSession.setPhoto(PendingPhoto(file, w, h, Camera))`,
  `nav.navigate(Preview)`. Failure → notice text.
- Gallery: `rememberLauncherForActivityResult(PickVisualMedia())` with `ImageOnly`; null =
  cancelled, stay silent; otherwise copy the `Uri` into `cacheDir` (RN's picker also hands back a
  cache copy, which is what keeps `TempImages.delete` safe), then set the photo and navigate.
- `CameraControlBar`: gallery thumbnail (latest scan, from `ScanRepository.listScans(limit = 1)`
  reloaded on `revision`), shutter, flash; `ModeSwitch` top right when granted.

#### Live (`app/scan/live.tsx`) — `LiveViewModel`
- Use cases: `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`,
  `OUTPUT_IMAGE_FORMAT_RGBA_8888`, 4:3) + `ImageCapture` for the shutter.
- Analyzer, on a single background executor: skip if less than 150 ms since the last run ended;
  `imageProxy.toBitmap()`, rotate by `imageInfo.rotationDegrees`, draw into a 640×640 bitmap
  scaled to fit and centered on **black** (RN's GPU resizer pads black, not 114, and
  `frameLetterbox` assumes the same geometry), build the tensor, run the `LiveRunner`, post the raw
  output + frame size + timings to the ViewModel, close the `ImageProxy`. Count every frame for
  camera fps.
- ViewModel: `liveDetections(...)` with the current threshold, `LiveStats`, a 1 s ticker for the
  snapshot and staleness (1.5 s), the dev log line every 10 s, GPU → CPU fallback with the notice.
- Overlay: `previewTransform` assumes the preview **fills** the view (crop), so use the
  viewfinder's fill-center scale type; draw boxes with `toViewBox`. Live verdict capsule uses
  `summarizeDetections` + `verdictTitle`.
- Capture: `ImageCapture` to a cache file → `setPhoto` → `nav.navigate(Preview) { popUpTo<Live>
  { inclusive = true } }`.
- The RN resize runs on the GPU; this one runs on the CPU. Record live numbers per backend and
  note this difference when you compare.

#### Preview (`app/scan/preview.tsx`) — `PreviewViewModel`
- Missing photo → `ErrorState` → back to Camera. Image with `ContentScale.Fit`, aspect from the
  photo size, Coil decoding at view size (RN `resizeMethod="resize"`). Coil error → error state,
  Use photo disabled.
- Footer: Retake / Choose another (2 : 3 width ratio) and Use photo → `Processing`.

#### Processing (`app/scan/processing.tsx`) — `ProcessingViewModel`
- `init` (and Retry) launches `AnalyzePhoto(photo)` in `viewModelScope`. Leaving the screen
  clears the ViewModel and cancels the job, so a late result is never applied (RN: the `active`
  flag).
- Success → `scanSession.setResult(result)` → navigate to `Result`, replacing Processing.
- Failure → `describeDetectionError` → `ErrorState(title, message, Try again, Go back)`.
- UI: the photo at 60 % opacity, max 70 % height, spinner + "Analyzing on your device" +
  detail line.

#### Result (`app/scan/result.tsx`) — `ResultViewModel`
- `ScanReport` (`features/scans/ui`): `ImageWithBoxes` with `selectedId` toggle; when pods exist:
  `VerdictLine` (`verdictTitle(verdict, total, countAtVerdictSeverity)`, screening note,
  low-confidence banner), section header "N pods found", `DetectionListItem`s (Correct label
  button under the selected one), "tap a pod" hint when nothing is selected, advice `Card` from
  the class at the verdict's severity; otherwise the `noPods` slot. `CorrectLabelSheet` lists all
  classes with icon + color + name and marks the model's answer.
- Footer only with pods: Save scan (opens `SaveScanSheet`) and Discard.
- `SaveScanSheet`: loads plots when opened, radio list with "No plot", add plot inline, Manage
  plots (closes the sheet, opens `Plots`), notes (max 500), Save (disabled while saving; sheet
  cannot be dismissed while saving), error line.
- Save: `saveResult(db, files, result, config, plotId, notes, location = locationDeferred.await())`
  → `bump()` → exit to History + ScanDetail(saved = true) → `scanSession.clear()`.
- Guard: `guarded = result != null && pods > 0 && exit == null`; `BackHandler(guarded)` shows the
  Stay / Discard / Save dialog. Exit navigation happens after the guard is off (RN does this with
  an `exit` state + effect; do the same with a one-shot event from the ViewModel).
- Location: if `gpsOptIn`, `viewModelScope.async { captureScanLocation() }` in `init`.

#### Plots (`app/plots.tsx`) — `PlotsViewModel`
- Intro, add row (text field max 60, Add disabled when blank; duplicate check case-insensitive
  with `Collator` strength PRIMARY, RN uses `localeCompare sensitivity: 'base'`), error line,
  list of plot rows (tap → History request + tab; rename → sheet with duplicate check and
  disabled-when-unchanged Save; delete → dialog with the scan count). Every change `bump()`s.

#### About (`app/about.tsx`) — `AboutViewModel`
- All from `modelConfig` + classes: disclaimer panel on `inverse` colors, recall headline +
  `RecallPods` + body, metric bars (test mAP50, early-stage mAP50, val mAP50 as `%.1f%%`),
  limitations list, model card (name, id, input size, class chips), privacy, app version
  (`BuildConfig.VERSION_NAME`).

#### Dev screens (`app/dev/*`)
- Gallery: every component in every state, light and dark (port `GalleryScreen.tsx`).
- Seed: add N fake scans through the real save path, remove them (`[dev seed]` notes prefix,
  `[dev]` plot prefix).
- Model test: load time, tensor shapes, box units, per-image timings and golden PASS/FAIL.

### 5.5 Tests (port of `__tests__/`)

| Jest file | Kotlin test | Where |
| --- | --- | --- |
| `detection.math.test.ts` | `LetterboxTest`, `DecodeTest`, `NmsTest`, `PostprocessTest`, `ModelSpecTest` | `src/test` (JVM) |
| `verdict.test.ts` | `VerdictTest` | JVM |
| `modelConfig.test.ts` | `ModelConfigTest` (parses the real asset file) | JVM (read the file from `src/main/assets`) |
| `golden.references.test.ts` | `GoldenCompareTest` (logic) + `GoldenDeviceTest` (pipeline) | JVM + `androidTest` |
| `liveMath.test.ts` | `LiveMathTest` | JVM |
| `historyFilters.test.ts` | `HistoryFiltersTest` (fixed `ZoneId` and `Locale`) | JVM |
| `scan.logic.test.ts`, `camera.logic.test.ts`, `settings.logic.test.ts` | `ResultEditsTest`, `CameraAccessTest`, `ThresholdTest`, `DataToolsTest` | JVM |
| `retrainingExport.test.ts` | `RetrainingExportTest` (zip into a `ByteArrayOutputStream`, fake file store) | JVM |
| `contrast.test.ts` | `ContrastTest` | JVM |
| `db.migrations.test.ts`, `db.scans.test.ts` | `MigrationTest` (Room `MigrationTestHelper`), `ScanRepositoryTest` (in-memory Room) | `androidTest` |
| `navigation.test.tsx`, `history.test.tsx`, `settings.screens.test.tsx`, `gallery.test.tsx` | Compose UI tests (`createAndroidComposeRule`) | `androidTest` (optional; RN renders these in Node, Kotlin needs a device) |

Plus `docs/TESTING.md`'s manual checklist, which applies to both apps unchanged.

---

## Step 6 — Parity checklist and what to compare

Run each item on the TECNO BG6 for both apps and keep the numbers in one table.

**Behaviour parity** (should be identical):
- [ ] Golden test: same PASS/FAIL per image; IoU and confidence deltas recorded (expect small
      differences from the removed JPEG round trip).
- [ ] Same photo, same threshold → same boxes, classes, verdict, low-confidence flag.
- [ ] CSV export of the same data opens identically in a spreadsheet.
- [ ] Retraining zip: same files and label lines for the same scans.
- [ ] Every item in `docs/TESTING.md` passes.

**Measurements** (where the two will differ):

| Measure | RN | Kotlin |
| --- | --- | --- |
| Release APK size | | |
| Cold start to Home (ms) | | |
| Model load (ms) | | |
| Preprocess / inference / postprocess per photo (median of 5, golden_01) | 2.2–2.7 s / 1375 ms / 15 ms (dev build, 2026-10-15) | |
| Live: detections/s and camera fps, GPU and CPU | | |
| Peak memory during analysis (Android Studio profiler) | | |
| Clean build time / incremental build time | | |
| Lines of code (app code, tests) | | |
| Permissions in the release manifest | CAMERA, location, ACCESS_NETWORK_STATE | |

**Known, intentional differences** (state them in the thesis, not as bugs):
- No `CHECK` constraints and no `schema_migrations` table (Room's versioning instead).
- Preprocessing reads pixels directly instead of re-encoding a JPEG.
- Live resize runs on the CPU instead of a GPU resizer; live GPU inference uses LiteRT's
  `CompiledModel`.
- Separate `applicationId` (`com.yoloc.app.kt`) so both apps can be installed together.
- Debug builds of the Kotlin app also have no INTERNET permission (there is no Metro).
- LiteRT 2.3.0 adds permissions to the merged manifest: FOREGROUND_SERVICE and
  FOREGROUND_SERVICE_DATA_SYNC (`litert-api`), plus WAKE_LOCK, ACCESS_NETWORK_STATE and
  RECEIVE_BOOT_COMPLETED (`androidx.work:work-runtime`, pulled in by LiteRT). None grants network
  access; kept for now (2026-10-26). Removing them with `tools:node="remove"` needs a test on the
  phone first.
