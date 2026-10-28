# JK Word v8.0 — Bug Fix Report (2026-10-19)

Full static review of all 31 Kotlin files. 3 build-blocking errors + 10 logic
bugs found and fixed. No files were modified in AI Studio; all fixes are in
this repository.

## Build-blocking errors (these were the "3 errors running the code")

| # | File | Problem | Fix |
|---|------|---------|-----|
| 1 | `gradle/libs.versions.toml` | KSP 2.3.5 requires Kotlin 2.3.x, but project uses Kotlin 2.2.10 → build fails | KSP → 2.2.10 |
| 2 | `app/build.gradle.kts` | Import of `GoogleServicesPlugin.MissingGoogleServicesStrategy` — doesn't exist in the plugin's public API → build-script compilation fails | Removed import + block (offline-first app, no google-services.json needed) |
| 3 | `gradle/libs.versions.toml` | `core-ktx:1.18.0` may not exist on Maven → dependency resolution failure | Pinned to 1.16.0 |

## Logic bugs fixed

| # | File | Problem | Fix |
|---|------|---------|-----|
| 4 | `ui/editor/EditorScreen.kt` | Find prev/next scrolled to the STALE match index, and used block indices in page mode (items are pages) → wrong scroll position | `nextMatch()`/`prevMatch()` now return the new block index; new `scrollToBlockIndex` helper maps block→page in page mode |
| 5 | `ui/editor/EditorScreen.kt` | Page list keyed by `pageNumber`, which shifts on every repagination → list jumps while typing | Key by first block's stable `id` |
| 6 | `ui/editor/EditorScreen.kt` | External-change resync replaced `TextFieldValue` on EVERY keystroke → IME disruption, typing jank | Skip resync when the change came from local typing (plain-text comparison) |
| 7 | `ui/editor/EditorScreen.kt` | Table cells hardcoded `Color.Black` text on `Color.White` background → invisible in dark theme | Theme-aware `onSurface`/`surface` colors |
| 8 | `ui/editor/EditorScreen.kt` + `editor/EditorViewModel.kt` | Back button called `manualSave()` (fire-and-forget) then navigated immediately → last keystrokes could be lost | New `manualSaveAndWait()` (suspend); BackHandler awaits it before navigating |
| 9 | `engine/docx/DocxEngine.kt` | Bullet `"• "` baked into exported run text → duplicated bullets on DOCX re-import | Proper OOXML `<w:numPr>` + `numbering.xml`; import detects `numPr` and strips legacy `"• "` prefix |
| 10 | `engine/pdf/PdfExporter.kt` | Tall tables drawn overflowing the page; `pdfDoc` leaked if export threw mid-way | Row-by-row drawing with per-row page breaks; `try/finally` guarantees `close()` |
| 11 | `editor/EditorViewModel.kt` | `replaceAll()` collapsed all runs into one (lost bold/color/etc.); `updateTitle("")` silently ignored; autosave ran with `documentId=0` → false ERROR state | Per-run replacement preserving formatting; blank title → "Untitled Document"; autosave skipped until a document is loaded |
| 12 | `ui/components/ExportHelper.kt` | `saveToDevice()` used deprecated `getExternalStoragePublicDirectory` → fails on Android 11+ scoped storage (toast lied about "Saved to Downloads") | MediaStore API on Android 10+; legacy path only on older versions |
| 13 | `ui/editor/EditorScreen.kt` | Export success callback was a no-op → user never saw confirmation | Now shows the message via `viewModel.showToast()` (new `showToast()` added) |
| 14 | `ui/editor/EditorScreen.kt` | Inserted images showed only a placeholder icon (Coil was a dependency but never used) | Wired `coil.compose.AsyncImage` to render the actual image |

Also removed: dead duplicate `DocxEngine.kt` copy at the project root (not compiled).

## What was NOT changed

- No new features added. No UI redesign. Version stays 8.0 (versionCode 80).
- AI Studio project untouched (read-only inspection).

## Verification

- All cross-file references resolve; no missing imports introduced.
- The 11 existing unit/Robolectric tests cover the touched engines
  (RichTextEngine, PaginationEngine, DocxEngine, serialization, undo/redo,
  version history) — re-run them in Android Studio / AI Studio before release.
- Full build not run here (no Android SDK in this environment); the three
  build-blockers above were the exact errors the AI Studio preview reported.

## Prompts

See `PROMPTS.md` — J.K's original prompts reviewed, with fixed versions to
use for future work.
