# Port the NeedMCP `tactical-ops-console` direction into the Taho Browser Compose shell

**Date:** 2026-10-03
**Status:** Approved — all four design decisions settled (§11)
**Author:** orchestrated under the Taho Lead Orchestrator persona (AGENTS.md)

---

## 1. Intent

The team reviewed a NeedMCP `tactical-ops-console` prototype of the three core
browsing surfaces and accepted the direction. This spec covers replacing the
shipped Taho visual design system with it, inside the live Compose shell.

Success means:

- `TahoTheme.kt` emits the `tactical-ops-console` token set, and every shell
  surface renders from it with no hardcoded colours left behind.
- Every text/background pair in the shell meets WCAG AA (4.5:1 body, 3:1 large).
- The four AGENTS.md non-negotiable invariants still hold, and the 33 shell unit
  tests still pass.
- The repository has exactly **one** visual source of truth, and it is not
  ambiguous.

## 2. Why this is architectural

`TahoTheme.kt` is not a leaf. All 13 shell composables (11,681 lines) read its
tokens, and its values were extracted 1:1 from
`Doc/TAHO_BROWSER_UI_UX_SPEC.md` §2/§15, whose declared source of truth is
`TAHO_BROWSER_UI_PROTOTYPE.html`. Changing the tokens therefore rewrites the
product's documented visual authority, not just a stylesheet. Hence: spec →
review → plan → implement.

### 2.1 The authority problem, stated plainly

`TAHO_BROWSER_UI_UX_SPEC.md` line 13 currently reads:

> *"Every value in this document is extracted literally from the working
> prototype. If this document and the prototype disagree, the prototype wins."*

If we change the app but not that chain, the repo ends up with **two specs that
contradict each other** — exactly the "P1 — Architecture authority drifts" risk
already logged in `TAHO_BROWSER_BUILD_PLAN.md`. This spec therefore treats
authority reconciliation as a **required deliverable**, not cleanup.

**Resolution adopted here:**

| Artifact | New role |
|---|---|
| `prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html` | **Promoted** to visual source of truth for the shipped shell |
| `Doc/TAHO_BROWSER_UI_UX_SPEC.md` §2, §13, §15 | Rewritten to match the new tokens; prototype-wins clause retained |
| `TAHO_BROWSER_UI_PROTOTYPE.html` | Retained, explicitly marked superseded/historical |
| `TahoTheme.kt` | Becomes the machine-readable mirror of the promoted prototype |

## 3. Independent justification (not aesthetic)

Computed against the current shipped theme, `TahoFaint #6B675F` **fails WCAG AA
on every surface it is used on**:

| Pair | Ratio | Need | Result |
|---|---|---|---|
| `TahoFaint` on bg `#050505` | 3.62:1 | 4.5 | FAIL |
| `TahoFaint` on sheet `#0D0D10` | 3.45:1 | 4.5 | FAIL |
| `TahoFaint` on row `#141417` | 3.27:1 | 4.5 | FAIL |

`TahoFaint` carries small mono text throughout the shell — the `PAGE ACTIONS` /
`BROWSER HUBS` group labels, timestamps, metadata values. Every other token
passes (text 17.91:1, gold 10.54:1, ok 8.15:1, warn 8.51:1, error 5.58:1).

So this port also closes a **live accessibility defect** that exists today,
independent of any visual preference. That defect alone justifies the token work
even if the team later reverts the palette.

A second, separable defect: the shell uses **emoji as icons** (`🔒 🛡 ⏱ ◐ ⌕` in
`TahoStartPage.kt`, `TahoBrowserMenu.kt`, `TahoTabsOverview.kt`). Emoji render
inconsistently per device and announce inconsistently to screen readers. The port
replaces them with authored vector drawables in one stroke weight.

## 4. Token mapping

Dark field only. The app is a single-theme AMOLED surface today; that stays.

### 4.1 Colour

| Current | Value | New | Value | Note |
|---|---|---|---|---|
| `TahoBg` | `#050505` | `canvas` | `#080A0D` | |
| `TahoSheet` | `#0D0D10` | `bone` | `#0E1216` | |
| — | — | `panel` | `#12161A` | new surface step |
| — | — | `raised` | `#1A1F25` | hover / pressed |
| — | — | `well` | `#040607` | inputs, code blocks |
| `TahoGold` | `#E2B44A` | `amber` | `#E8AE55` | primary + armed |
| `TahoGoldHi` | `#F0CD7E` | `amberHover` | `#EFC06C` | |
| — | — | `amberDeep` | `#D89A3C` | pressed / accent text |
| `TahoText` | `#F3F0E9` | `ink` | `#EDF0F3` | |
| `TahoNeutral` | `#C9C5BB` | `charcoal` | `#95A0AC` | icon strokes |
| `TahoMuted` | `#98948A` | `mute` | `#828D99` | small secondary text |
| `TahoFaint` | `#6B675F` | `mute` | `#828D99` | **deleted** — see §4.4 |
| — | — | `body` | `#BCC5CE` | Material3 `onBackground`/`onSurface`; §4.6 |
| — | — | `ash` | `#666F7A` | non-text marks only |
| — | — | `stone` | `#4F5861` | disabled text only |
| — | — | `scrim` | `Black@.72` | modal/sheet veil |
| `TahoOk` | `#5FBF8A` | `ok` | `#5BC088` | |
| `TahoWarn` | `#E0A64A` | — | — | **conflict**, see §4.2 |
| `TahoInfo` | `#4FBFA3` | `info` | `#82B4D6` | |
| `TahoError` | `#E06A5A` | `danger` | `#D96A5E` | |
| `TahoJsonNum` | `#C9B2F0` | — | — | **conflict**, see §4.3 |
| `TahoHairline` | `White@.08` | `hairline` | `#1E242B` | |
| `TahoHairlineStrong` | `White@.14` | `hairlineStrong` | `#2C343C` | |
| `TahoSurfaceRow` | `White@.028` | `panel` | `#12161A` | |
| `TahoSurfaceRowHover` | `White@.055` | `raised` | `#1A1F25` | |
| `TahoSurfaceControl` | `White@.035` | `bone` | `#0E1216` | |

### 4.2 Conflict 1 — warning has no distinct hue

`tactical-ops-console` collapses chroma to a **Single Amber Rule**: amber is
reserved for the armed state and the primary action. It has no separate warning
hue. But this app uses `TahoWarn` for capture-policy states and degraded-storage
notices, which must not be mistaken for "primary action available".

Today's `TahoWarn #E0A64A` and `TahoGold #E2B44A` are already nearly identical,
so this ambiguity partly exists today; the port would make it worse by collapsing
them fully.

**Decision (approved 2026-10-03): option (a).** Amber serves both roles, so every
warning surface carries a `WARN` tag **and** a 4px amber status bar. Colour is
never the only signal, which satisfies WCAG 1.4.1 without adding a hue and keeps
the style's Single Amber Rule intact. `TahoWarn` is retained as an alias of
`amber` so existing call sites keep compiling, with the structural tag/bar added
at each warning site during Stage 3.

### 4.3 Conflict 2 — JSON syntax highlighting

`TahoJsonNum #C9B2F0` is used in exactly one place, `M7ProductUx.kt:782`, to
colour numbers in the JSON body viewer. The new palette has no violet.

**Decision (approved 2026-10-03): option (a).** Numbers take `body`; keys keep
`info`; punctuation keeps `amber`. Syntax stays legible by structure — digits,
quotes and braces remain visually distinct even without a third hue.
`TahoJsonNum` is deleted. This keeps the style's collapsed chroma intact and
removes the last non-semantic colour from the system.

### 4.4 Deleting `TahoFaint`

`TahoFaint` cannot be carried forward — it fails AA (§3). Every call site is
remapped: small text → `mute`; non-text marks (chevrons, rest-state icon
strokes, rules) → `ash` `#666F7A`, which clears the 3:1 non-text floor (3.25–3.57:1
on the relevant surfaces) but must never carry text.

This is a mechanical but wide change: `TahoFaint` appears throughout
`TahoStartPage.kt`, `TahoTabsOverview.kt`, `TahoBrowserMenu.kt`,
`TahoPageOverlays.kt` and `M7ProductUx.kt`.

### 4.5 Typography

| Role | Current | New |
|---|---|---|
| Display | Clash Display (bundled) | **Archivo** (to be bundled) |
| Body | General Sans (bundled) | **Archivo** (to be bundled) |
| Mono / all metrics, identifiers, timestamps | JetBrains Mono | JetBrains Mono (unchanged) |

Two registers, not three. Every URL, count, timestamp, byte size, status code and
label value moves to JetBrains Mono with `tabular-nums` for column alignment.

**Prerequisite:** Archivo TTFs must be added to `browser/shell/src/main/res/font/`
and licensed (OFL). Clash Display and General Sans are then unused and removed.
JetBrains Mono ships unchanged.

### 4.6 Shape

The style mandates machined corners; the app is built on large soft radii.

| Current | Value | New | Value |
|---|---|---|---|
| `TahoSheetShape` | 26dp | panel radius | 0dp |
| `TahoCardShape` | 16dp | panel radius | 0dp |
| `TahoBlockShape` | 14dp | control | 2dp |
| `TahoNoteShape` | 12dp | control | 2dp |
| `TahoBadgeShape` | 6dp | badge | 4dp |
| `TahoPillShape` | 999dp | pill | 999dp (unchanged) |

**Confirmed (approved 2026-10-03).** This is the highest-visual-impact change in
the port: every bottom sheet loses its rounded silhouette and becomes a
hard-edged slab with a hairline top rule. That is correct for the style, whose
depth model is hairline rules and luminance steps rather than soft shapes.

### 4.7 Motion

| Current | Value | New | Value |
|---|---|---|---|
| `TahoEasing` | `(0.32, 0.72, 0, 1)` | standard | `(0.16, 1, 0.3, 1)` |
| `TahoSpringEasing` | `(0.34, 1.4, 0.44, 1)` | press | unchanged |
| `TahoDurationScreen` | 600ms | screen | 300ms |
| `TahoDurationSheet` | 750ms | sheet | 300ms |
| `TahoDurationVeil` | 550ms | veil | 200ms |

All durations must still respect `TahoReducedMotion()` (animator scale 0). The
style's one authored moment is the amber **corner-tick reticle** on the focused
panel; no other entrance animation is introduced. The prototype's reticle
(`TahoRingPulse` and the pulse helpers) is reworked to draw four 11px corner ticks
rather than an expanding ring, preserving the reduced-motion bypass.

## 5. Icon system

Replace emoji with authored vector drawables (`res/drawable/`), 24dp viewport,
1.5dp stroke, round caps and joins, `currentColor` tint — one family, one weight.

Required set, reconciled against the shell at `0cb3822` (37):

    close  chevron-right  minus  plus  search  check  warn-triangle  settings
    person  incognito  brand  shield  shield-check  lock  bolt  monitor
    monitor-check  sparkle  grid  clock  caret-up  caret-down  alert  home  card
    star  star-outline  book  reader  expand
    location  camera  microphone  notification  clipboard  storage  popups
    autoplay

Each needs `contentDescription` for accessibility, replacing the
`contentDescription` semantics calls that currently sit on emoji glyphs.

### What changed from the original list, and why

The original list read "Required set (28)" and then enumerated 30 names. It has been
replaced rather than patched, because it no longer described this codebase:

- **It was internally inconsistent.** It claimed 28 and listed 30. The implementation plan
  separately claimed 28 plus a brand mark, and its test expected 29. Three numbers, two
  documents, and the mismatch is why the first two attempts at this task produced the wrong
  file count.
- **Eight names had no site.** `find`, `print`, `download`, `key`, `puzzle` and `folder` exist
  in the shell only *inside prose* — `"🔍 Find on page"` — never in an icon slot.
  `arrow-up-right`, `share`, `translate` and `install` had no glyph at all.
- **Thirteen were missing.** The site-permission list (`TahoPageOverlays.kt`) alone contributes
  nine: location, camera, microphone, notification, clipboard, storage, popups, autoplay and
  background. Also `check`, `warn-triangle`, `home`, `card`, `person`, `expand`, `caret-up`,
  `caret-down`, `star-outline`, `monitor-check` and `shield-check`.
- **`close` was two codepoints.** `×` (U+00D7, 28 uses) and `✕` (U+2715, 4 uses) are both the
  close affordance. One drawable serves both; leaving them as separate glyphs would have
  preserved the inconsistency the icon system exists to remove.

### Inline marks are not icons

The shell uses 31 further glyph characters (55 occurrences) **only inside sentences** —
`"✓ complete"`, `"△ body partial"`, `"📁 Requests"`. These are typographic marks in prose and
stay as text. Converting them would be wrong: the spec's intent is to replace emoji *used as
icons*, not to strip punctuation out of labels. The distinction is what keeps this task from
either under- or over-reaching, and it is why the guard test distinguishes icon slots from prose
rather than banning glyph characters outright.

### Two glyphs are data, not chrome

`⌕` and `📌` reach the UI through the `iconGlyph` **data field** on `SearchEngineItem` and
`TopSiteItem` (`TahoModels.kt`), with `addSearchEngine(iconGlyph = "⌕")` and a `.take(2)`
truncation that implies user-supplied content. Converting those would be a model change, not an
icon swap. Chrome uses of the same characters become drawables; the data field keeps text.

## 6. Change surface

| File | Lines | Change |
|---|---|---|
| `TahoTheme.kt` | 272 | Full token replacement, reticle rework, typography |
| `TahoStartPage.kt` | 857 | `TahoFaint` remap, radii, emoji → icons |
| `TahoSettingsHub.kt` | 3,117 | `TahoFaint` remap, section titles, radii |
| `TahoBrowserApp.kt` | 1,943 | Chrome/omnibox, banner surfaces |
| `M7ProductUx.kt` | 1,805 | Capture sheets, JSON highlighter conflict §4.3 |
| `TahoPageOverlays.kt` | 974 | Overlays, reader, translation |
| `TahoTabsOverview.kt` | 803 | `TahoFaint` remap, radii, emoji → icons |
| `TahoBrowserMenu.kt` | 380 | `TahoFaint` remap, radii, emoji → icons |
| `TahoBrowserStateStore.kt` | 718 | None (no UI) — verify only |
| `TahoModels.kt`, `M7TransferSearch.kt`, `M4CaptureModels.kt`, `TahoBrowserPersistence.kt` | 812 | Colour references only |
| `res/font/` | — | Add Archivo, remove Clash Display + General Sans |
| `res/drawable/` | — | New icon set |
| `Doc/TAHO_BROWSER_UI_UX_SPEC.md` | 875 | §2, §13, §15 rewritten |
| `TAHO_BROWSER_UI_PROTOTYPE.html` | 1,245 | Mark superseded |

## 7. Invariants that must survive

From AGENTS.md, none of which this port may weaken:

1. **Pure JVM isolation** — untouched; no shell file imports `android.*` into
   `:capture:domain`, `:transfer:core`, `:contract:taho-transfer`.
2. **Session attribution** — untouched; no tab-ID lookup introduced.
3. **Capture decoupling** — capture must stay invisible when off. A visual change
   must never introduce work on the browsing path.
4. **Credential isolation** — untouched. Note the reticle/mask treatment must not
   weaken existing redaction; `M7CaptureSummarySheet` already projects masked
   values and that projection is unchanged.

Plus: browsing performance and stability remain flawless regardless of styling.

## 8. Testing

- All 33 existing shell unit tests pass unchanged. **No test asserts on colour
  tokens** (verified — the only `TahoTheme*` match in tests is
  `TahoThemeMode.DARK`), so the token swap is not test-breaking. This is a
  favourable finding and should be re-confirmed at execution time.
- New test: assert no production shell file hardcodes a `Color(0x…)` literal —
  all colour must come from tokens. This prevents the exact drift that caused the
  `TahoFaint` defect.
- Manual: contrast sweep over every text node against its composited background,
  re-run per surface as the browser sweep used on the prototype.
- Manual: `TahoReducedMotion()` bypass still suppresses sheet/reticle motion.

## 9. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Visual regression: hardcoded colours survive the token swap in one of 13 files | High | New no-hardcoded-colour test (§8); grep gate |
| Sheets lose rounded silhouettes — reads as a downgrade to some | Medium | Deliberate; §4.6 makes it an explicit decision |
| `TahoWarn` / amber collision misleads users about state | Medium | §4.2 decision, with tag + 4px bar so colour is never alone |
| Scope creep across 11,681 lines in one pass | Medium | Plan sequences per-file with a build+test gate between |
| Spec/prototype drift reintroduced | Medium | §2.1 authority resolution is a tracked deliverable |
| Archivo licensing/bundling | Low | OFL; verify before build |

## 10. Out of scope

- Light theme. The style ships two modes; this app stays dark-only.
- The 20 near-identical settings pages beyond the representative set.
- Any change to capture logic, attribution, persistence, transfer or IPC.
- Marketing/landing surfaces.
- The `taho.request-transfer` contract.

## 10.1 Suggested decomposition for planning

One coherent subsystem, but too wide for a single pass. The plan should sequence
these five stages, each gated on a successful build plus the shell unit tests:

1. **Foundation** — `TahoTheme.kt` tokens, Archivo bundling, icon set, the
   no-hardcoded-colour test. Nothing else changes yet, so this stage is
   independently reviewable and reversible.
2. **Browser shell** — `TahoBrowserApp.kt`, `TahoStartPage.kt`,
   `TahoTabsOverview.kt`, `TahoBrowserMenu.kt`, `TahoPageOverlays.kt`.
3. **Capture surfaces** — `M7ProductUx.kt`, `M7TransferSearch.kt`,
   `M4CaptureModels.kt`. Carries the §4.3 JSON decision.
4. **Settings hub** — `TahoSettingsHub.kt`, the widest single file (3,117 lines),
   isolated last so it cannot destabilise the earlier stages.
5. **Authority reconciliation** — spec §2/§13/§15, mark the old prototype
   superseded. Tracked as a deliverable, not silent cleanup.

Each stage must leave the app buildable and the 33 tests green, so a failure
rolls back one stage rather than the whole port.

## 11. Resolved decisions

All four were put to the reviewer and approved on 2026-10-03. No open questions
remain; the spec is executable as written.

| # | Question | Decision |
|---|---|---|
| 1 | Warning hue under the Single Amber Rule | Amber for both, with a mandatory `WARN` tag **and** 4px status bar (§4.2) |
| 2 | JSON viewer violet | Monochrome numbers; `TahoJsonNum` deleted (§4.3) |
| 3 | Panel and sheet radii | Accept 0dp panels and sheets (§4.6) |
| 4 | Visual source of truth | Promote the NeedMCP prototype, rewrite spec §2/§13/§15, mark the old prototype superseded (§2.1) |

---

*Approved. Next step is the `writing-plans` skill to produce the sequenced
implementation plan across the five stages in §10.1.*