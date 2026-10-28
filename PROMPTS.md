# JK Word — Reviewed & Fixed Prompts

These are J.K's original AI Studio prompts, reviewed and improved.
Use these versions for future bug-fix / feature work on JK Word.

---

## Prompt 1: Bug Report (Performance) — FIXED VERSION

> **Original issue:** The prompt described lag well but didn't require the AI to
> prove each fix, so some "fixes" were incomplete (scrolling still jumped,
> typing still replaced TextFieldValue every keystroke).

**Improved prompt:**

```
BUG: Severe lag and slow performance in the JK Word editor.

SYMPTOMS (reproduce before fixing):
1. Typing a character takes >200ms to appear.
2. Scrolling jumps unexpectedly while typing.
3. Text flickers or turns black briefly.

RULES:
- Inspect the existing codebase FIRST. Do not guess.
- Make targeted, minimal changes. Do not refactor unrelated code.
- For EACH fix: explain the root cause in one line, then the fix.
- After fixing, verify: run the existing unit tests (all must still pass),
  and describe how you confirmed the symptom is gone.
- Do NOT add new dependencies.
- Keep versionCode/versionName unchanged.

ACCEPTANCE:
- Typing latency <50ms per keystroke on a mid-range device.
- No scroll jumps during 60 seconds of continuous typing.
- No text flicker when toggling themes.
```

**What was fixed:** Added "reproduce before fixing", "minimal changes",
"verify each fix", "no new dependencies", measurable acceptance criteria.

---

## Prompt 2: Version Upgrade — FIXED VERSION

> **Original issue:** Asked for "Version 8.0 everywhere" but didn't pin the
> version in exactly which files, leading to inconsistent versioning.

**Improved prompt:**

```
TASK: Upgrade JK Word to Version 8.0 (performance & stability release).

VERSION CHANGES (all of these, no others):
- app/build.gradle.kts: versionCode 80, versionName "8.0"
- About screen: display "Version 8.0"
- metadata.json: version "8.0"

SCOPE: Performance and stability ONLY. No new features.

RULES:
- Inspect the codebase first; fix root causes, not symptoms.
- Run available tests after changes; all must pass.
- List every file you changed and why (one line each).

ACCEPTANCE:
- App builds with zero errors and zero new warnings.
- All existing tests pass.
- Typing stays responsive in a 50-page document.
```

**What was fixed:** Pinned exact files for versioning, banned scope creep,
required a change list.

---

## Prompt 3: Project Requirements (PRD) — NOTES

The 27-section "WORDMATE" PRD is thorough and well-structured. Two improvements
for future use:

1. **Add a "Non-Goals" section** — explicitly list what the app will NOT do
   (cloud sync, AI features, macro execution). This prevents the AI from
   adding placeholder features.
2. **Add "Definition of Done" per section** — e.g. section 12 (Table Editing):
   "Done = insert/delete rows+cols works, survives DOCX round-trip, covered
   by a unit test." Without this, features get marked complete while half-built.

---

## General prompting rules for this project (save these)

1. Always start with: "Inspect the existing codebase first."
2. Always end with: "Run the tests. List every file changed and why."
3. Never allow new dependencies without explicit approval.
4. One task per prompt — don't mix "fix bugs" with "add features".
5. Require measurable acceptance criteria (ms, counts), not vibes.
