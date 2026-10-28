# JK Word 📝

**Professional native Android word processor** — offline-first, with genuine
DOCX editing, rich formatting, tables, images, and PDF export. No Microsoft
trademark; no cloud required.

- **Version:** 8.0 (versionCode 80)
- **Stack:** Kotlin, Jetpack Compose, MVVM + Repository, Room, DataStore,
  coroutines, Storage Access Framework
- **Original project:** Built in Google AI Studio
  ([open in AI Studio](https://ai.studio/apps/f495e7fd-ec73-4092-877a-b7ace18f7e95))

## Features

- Real document editing: bold/italic/underline/strikethrough, fonts, sizes,
  colors, highlight, alignment, bullets & numbered lists, indent
- Tables (insert, edit, resize, styling), images (picker/camera, resize),
  page & section breaks, headers/footers, page numbers
- **DOCX** open/edit/save with round-trip fidelity (proper OOXML numbering,
  styles, tables, images, hyperlinks)
- **PDF** export from the real layout engine + printing
- Find & replace, undo/redo, document version history with revert
- Autosave with crash recovery, dark/light themes, Urdu/Arabic RTL support
- 100% offline — no login, no cloud, no uploads

## Run it

1. Open this directory in **Android Studio**
2. Let it sync Gradle (Kotlin 2.2.10, KSP 2.2.10, AGP per `libs.versions.toml`)
3. Run on an emulator or device (minSdk 24)

## Docs

- [`BUGFIX_REPORT.md`](BUGFIX_REPORT.md) — full audit: 3 build errors + 11
  logic bugs found and fixed (2026-10-19)
- [`PROMPTS.md`](PROMPTS.md) — the AI Studio prompts behind this app,
  reviewed and improved for future work

## Tests

`app/src/test` — unit + Robolectric tests covering RichTextEngine,
PaginationEngine, DocxEngine import/export, serialization, undo/redo, and
version history. Run with `./gradlew test`.
