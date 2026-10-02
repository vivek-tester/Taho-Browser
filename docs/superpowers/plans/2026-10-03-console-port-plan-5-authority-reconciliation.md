# Plan 5 — Authority reconciliation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the repository's visual documentation match what the app now renders, so there is exactly one authority chain and no room for drift.

**Architecture:** `TAHO_BROWSER_UI_UX_SPEC.md` line 13 currently says *"the prototype wins"* on conflict, with `TAHO_BROWSER_UI_PROTOTYPE.html` as its source of truth. After Plans 1–4 that chain is wrong: the code is authoritative, the old prototype is obsolete, and the new NeedMCP prototype covers only 3 of the app's surfaces. This plan rewrites the chain so the *implementation* sits at the top, which is both accurate and self-correcting — a token change in `TahoTheme.kt` is now the thing that defines the truth.

**Tech Stack:** Markdown, HTML.

**Spec:** `docs/superpowers/specs/2026-10-03-tactical-console-port-design.md` — §2.1 authority resolution.

**Sequence position:** 5 of 5. Requires Plans 1–4 complete and green.

## Global Constraints

- **Build must be green before any doc claims anything.** Run `gradle :browser:shell:testDebugUnitTest` and `gradle verifyArchitecture` first. Documentation that describes an unbuilt state is worse than no documentation.
- **Never claim a capability the build does not have.** The repo has twice removed fabricated sync, storage and diagnostic claims. Documentation follows the same rule.
- **Do not delete the old prototype.** It is superseded, not wrong; keeping it preserves the decision history.
- **No ordinal section numbering** in any rewritten section.

## Review Focus

1. **The spec claims coverage the new prototype does not have.** This is the trap: the new prototype renders 3 screens, the spec describes 7, and the app now has ~22 surfaces. Writing "the new prototype is the source of truth" without noting the gap would recreate exactly the drift this plan exists to prevent. → Step 3.
2. **A doc asserts a measurement that was never taken.** No invented byte counts, timings or capability numbers. → Step 3.
3. **The old prototype is deleted rather than superseded.** That destroys the record of why the direction changed. → Step 4.

---

### Task 1: Record the shipped token values in the spec

**Files:**
- Modify: `Doc/TAHO_BROWSER_UI_UX_SPEC.md` — §2 (lines 41–151), the header block (lines 1–25), and §13 (line ~642)

**Interfaces:**
- Consumes: the actual token values in `TahoTheme.kt` after Plan 1.
- Produces: a spec whose §2 matches the code exactly.

- [ ] **Step 1: Read the current tokens from the code, not from this plan**

Never transcribe values from the plan or the prototype. Read them from the source of truth:

```bash
cd "/home/eternal/Taho/Taho Browser"
sed -n '/---------- colour/,/^internal val hairlineStrong/p' \
  browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt
```

If that output disagrees with anything written in this plan or in the spec, the code wins.

- [ ] **Step 2: Rewrite the §2 token tables from those values**

Replace the §2.1 colour, §2.2 typography and §2.3 shape tables with the shipped values. Use this structure so every row is checkable:

```markdown
### 2.1 Colour

Dark field only. Depth is read from luminance steps, never from shadow. The
only chromatic accent is amber, reserved for the armed state and the primary
action; every amber surface that means "warning" must additionally carry a text
tag and a 4dp status bar, because colour alone is not sufficient (WCAG 1.4.1).

| Token | Value | Role |
|---|---|---|
| `canvas` | `#080A0D` | page ground |
| `bone` | `#0E1216` | sheets and grouped fills |
| `panel` | `#12161A` | rows and cards |
| `raised` | `#1A1F25` | hover and pressed |
| `well` | `#040607` | inputs and code blocks |

| Token | Value | Role | Minimum contrast |
|---|---|---|---|
| `ink` | `#EDF0F3` | display and titles | 17.33:1 on canvas |
| `body` | `#BCC5CE` | row titles, prose | 10.40:1 on panel |
| `charcoal` | `#95A0AC` | icon strokes, secondary | 6.84:1 on panel |
| `mute` | `#828D99` | all small secondary text | 5.38:1 on panel |
| `ash` | `#666F7A` | **non-text marks only** | 3.57:1 — fails 4.5:1, never text |
| `stone` | `#4F5861` | **disabled text only** | 2.51:1 — WCAG 1.4.3 exemption |

| Token | Value | Role |
|---|---|---|
| `amber` | `#E8AE55` | primary action, armed state |
| `amberHover` | `#EFC06C` | primary hover |
| `amberDeep` | `#D89A3C` | pressed, accent text |
| `ok` | `#5BC088` | success state |
| `info` | `#82B4D6` | informational state |
| `danger` | `#D96A5E` | error state |
| `hairline` | `#1E242B` | 1dp seams |
| `hairlineStrong` | `#2C343C` | focused and emphasised seams |

Every ratio in the tables above is computed, not estimated, and is asserted by
`TahoFeaturesTest.everyTextTierClearsWcagAaOnEverySurfaceItSitsOn`. A change that
breaks a ratio fails the build.
```

- [ ] **Step 3: Update the header block**

Replace lines 5 and 13's prototype-wins clause. The new chain, in priority order:

```markdown
**Source of truth:** `browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt`.
The code defines the tokens. This document describes them in prose; where the two
disagree, `TahoTheme.kt` wins and this document is a bug.

**Visual reference:** `prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html`
shows the approved `tactical-ops-console` direction. It is a **partial** reference
— see §16 — and is normative for visual intent only, never for behaviour or data.
```

- [ ] **Step 4: Rewrite §13 (explicit don'ts)**

The existing don'ts referenced rounded sheets and the previous palette. Replace with the current prohibitions:

```markdown
- Do not reintroduce rounded sheets or cards. Panels hold at 0dp; controls at 2dp.
- Do not add a colour outside the token table. `ash` and `stone` are non-text tiers.
- Do not signal state with colour alone. Pair every semantic colour with a tag,
  a 4dp status bar, or a text label.
- Do not render unavailable evidence as an empty cell, a zero, or a default.
- Do not use emoji as icons. Use `TahoIcon` with a `contentDescription`.
- Do not animate anything on arrival except the corner-tick reticle.
- Do not add a second authored entrance animation to a surface.
- Do not bypass `TahoReducedMotion()`.
- Do not reorder `Doc/` so this document outranks the implementation.
```

- [ ] **Step 5: Commit**

```bash
git add Doc/TAHO_BROWSER_UI_UX_SPEC.md
git commit -m "docs: make TahoTheme.kt the source of truth for visual tokens

The prototype-wins clause is replaced. The code now defines the tokens,
so a token change is self-documenting, and the spec is a prose mirror
that fails review when it drifts. Records the computed contrast floors
and the non-text restriction on ash and stone."
```

### Task 2: Rewrite §15 (motion) and the motion references

**Files:**
- Modify: `Doc/TAHO_BROWSER_UI_UX_SPEC.md` — §15, plus any §4/§5 references to the removed durations

**Interfaces:**
- Consumes: the durations and easings in `TahoTheme.kt` after Plan 1 Task 2.
- Produces: a motion section matching the code.

- [ ] **Step 1: Read the actual motion values**

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -nE 'TahoEasing|TahoSpringEasing|TahoDuration' \
  browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt | head -8
```

- [ ] **Step 2: Rewrite §15**

```markdown
## 15. Mini-animation library

One authored moment per surface, and only one moment is authored at all: the
corner-tick reticle on the focused panel. Nothing else animates on arrival.

| Token | Value | Use |
|---|---|---|
| `TahoEasing` | `cubic-bezier(0.16, 1, 0.3, 1)` | standard |
| `TahoSpringEasing` | `cubic-bezier(0.34, 1.4, 0.44, 1)` | press release |
| `TahoDurationScreen` | 300ms | screen change |
| `TahoDurationSheet` | 300ms | sheet |
| `TahoDurationVeil` | 200ms | scrim |

All motion is gated by `TahoReducedMotion()`, which reads
`Settings.Global.ANIMATOR_DURATION_SCALE`. When the user has disabled animations
the reticle snaps to its armed state with no transition. Do not add a duration
check that bypasses this function.

The press affordance is scale-based (`tahoPressScale`, .96 primary / .97
controls) with a springy release, so press feedback does not depend on a
background lift.
```

- [ ] **Step 3: Fix stale cross-references**

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -nE '600ms|750ms|550ms|ring pulse|RingPulse|Clash Display|General Sans|26dp|16dp' Doc/TAHO_BROWSER_UI_UX_SPEC.md
```

Every hit refers to the previous direction. Update each. Do not leave a reference
to a duration or radius that no longer exists — that is precisely the drift this
plan removes.

- [ ] **Step 4: Commit**

```bash
git add Doc/TAHO_BROWSER_UI_UX_SPEC.md
git commit -m "docs: rewrite the motion section for the console direction

Documents the reticle as the single authored moment, the shortened
durations, and the reduced-motion gate. Clears stale references to the
old durations, radii, ring pulse and typefaces."
```

### Task 3: Document the prototype's partial coverage

**Files:**
- Modify: `Doc/TAHO_BROWSER_UI_UX_SPEC.md` — add §16
- Modify: `prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html` — header comment and on-page banner

**Interfaces:**
- Consumes: the actual screen count in the prototype file.
- Produces: an honest, checkable statement of what the reference covers.

- [ ] **Step 1: Count what the prototype actually renders**

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -c '<section class="screen"' prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html
grep -oE 'id="s-[a-z]+"' prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html | sort -u
```

Record the real number. At the time of writing this is 3 — start page, tabs
overview, browser menu — against roughly 22 surfaces in the shipped app.

- [ ] **Step 2: Add §16 stating the gap plainly**

```markdown
## 16. Visual reference coverage

`prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html` is the approved
visual reference for this direction. It is a **partial** reference.

**Covered:** start page, tabs overview, browser menu.

**Not covered:** capture summary, request inspector, technical workspace, send
confirmation, transfer progress, site info, permissions, share QR, find-in-page,
omnibox and banners, reader mode, translation bar, tab switcher, and the settings
hub.

For uncovered surfaces, `TahoTheme.kt` is normative. The prototype is normative
for visual intent only where it does cover a surface.

Closing this gap is outstanding work: either extend the reference to the full
surface set, or retire it in favour of screenshots of the running app. Do not
promote it to normative status while the gap exists.
```

- [ ] **Step 3: Correct the prototype's own header banner**

The file currently opens with a large "EXPLORATORY · NOT A SOURCE OF TRUTH" claim. That claim is now half wrong in the other direction: the file **is** the approved visual reference, but it is still not the token authority. Rewrite both the HTML comment and the on-page tag so they say exactly that:

```html
<!--
  VISUAL REFERENCE for the approved tactical-ops-console direction.
  Normative for visual intent on the three surfaces it covers; NOT normative
  for tokens, behaviour or data — TahoTheme.kt is. Partial coverage is
  documented in Doc/TAHO_BROWSER_UI_UX_SPEC.md §16.
-->
```

and on-page:

```html
<span class="review-tag">Visual reference &middot; partial &middot; not the token authority</span>
```

Replace the existing "This file changes nothing" disclaimer with one that points
at the spec rather than claiming the file is throwaway:

```html
<p class="disclaimer">
  <b>Scope.</b> This reference covers three of roughly twenty-two surfaces —
  see <b>Doc/TAHO_BROWSER_UI_UX_SPEC.md §16</b> for the gap. Tokens are defined
  by <b>TahoTheme.kt</b>, not by this file.
</p>
```

- [ ] **Step 4: Commit**

```bash
git add Doc/TAHO_BROWSER_UI_UX_SPEC.md prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html
git commit -m "docs: state the visual reference's partial coverage

The reference covers three surfaces against roughly twenty-two in the
app. Recording the gap in the spec prevents the reference being read as
complete coverage, and the file's own banner now says what it is for
instead of claiming to be throwaway."
```

### Task 4: Mark the old prototype superseded

**Files:**
- Modify: `TAHO_BROWSER_UI_PROTOTYPE.html` — header comment and on-page banner
- Modify: `README.md` — line 7
- Modify: `Doc/TAHO_BROWSER_BUILD_PLAN.md` — line 174 and the P1 row at line 64

**Interfaces:**
- Consumes: nothing.
- Produces: a repository where no reader can mistake the old prototype for current.

- [ ] **Step 1: Mark the old prototype, do not delete it**

At the very top of `TAHO_BROWSER_UI_PROTOTYPE.html`, insert:

```html
<!--
  ============================================================================
  SUPERSEDED — retained for decision history only. Do not implement from this.
  ============================================================================
  This prototype defined the previous visual direction (Ethereal Glass fused
  with Taho AMOLED/gold, Asymmetrical Bento + Editorial Split). That direction
  was replaced by the approved NeedMCP `tactical-ops-console` direction on
  2026-10-03.

  Current authority: browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt
  Current spec:       Doc/TAHO_BROWSER_UI_UX_SPEC.md
  Visual reference:   prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html

  Kept because it records why the direction changed. Its tokens, radii,
  fonts and durations are all obsolete.
  ============================================================================
-->
```

Also add a visible banner as the first element inside `<body>` so nobody reaches
the artefact without seeing it:

```html
<div style="position:sticky;top:0;z-index:9999;background:#1a0f0f;color:#e06a5a;
            font:600 12px/1.5 ui-monospace,monospace;padding:10px 14px;
            border-bottom:1px solid #e06a5a">
  SUPERSEDED 2026-10-03 — replaced by the tactical-ops-console direction.
  Current tokens live in TahoTheme.kt. See Doc/TAHO_BROWSER_UI_UX_SPEC.md.
</div>
```

- [ ] **Step 2: Update the README**

Line 7 currently reads:

> `UI/UX follows TAHO_BROWSER_UI_UX_SPEC.md and its prototype visual intent, while runtime facts come from measured capabilities rather than prototype fixtures.`

Replace it:

```markdown
UI/UX follows `TahoTheme.kt` for tokens, described in `TAHO_BROWSER_UI_UX_SPEC.md`;
runtime facts come from measured capabilities rather than prototype fixtures.
`TAHO_BROWSER_UI_PROTOTYPE.html` is superseded and retained for history only.
```

- [ ] **Step 3: Close the P1 authority-drift item in the build plan**

The build plan logs this risk and proposes a remedy. Record that it is now
addressed. Amend the P1 row at line 64 and line 174:

```markdown
| P1 — Architecture authority drifts | **Resolved 2026-10-03.** The visual
  authority chain is now `TahoTheme.kt` → `TAHO_BROWSER_UI_UX_SPEC.md` →
  partial visual reference. Module-graph and gate-registry items remain open. |
```

```markdown
- Superseded visual intent: `TAHO_BROWSER_UI_PROTOTYPE.html` (retained for
  history; replaced 2026-10-03).
- Current visual intent: `prototype-assets/review/TAHO_BROWSER_NEEDMCP_DIRECTION.html`
  and the tokens in `TahoTheme.kt`.
```

Leave the rest of the P1 row's findings intact. The module-graph and
gate-registry drift is a separate, still-open problem and this plan does not
claim to have solved it.

- [ ] **Step 4: Verify no stale reference survives**

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -rn 'TAHO_BROWSER_UI_PROTOTYPE' README.md Doc/ docs/ 2>/dev/null \
  | grep -v 'superseded\|Superseded\|history\|SUPERSEDED'
```

Expected: no output. Any remaining unqualified reference to the old prototype as
current authority is a bug in this plan.

- [ ] **Step 5: Commit**

```bash
git add TAHO_BROWSER_UI_PROTOTYPE.html README.md Doc/TAHO_BROWSER_BUILD_PLAN.md
git commit -m "docs: mark the previous prototype superseded and close the P1 drift item

The old prototype is retained with an unmissable banner rather than
deleted, so the decision history survives. Records that the visual
authority chain now starts at TahoTheme.kt. Closes only the visual half
of the P1 authority-drift finding; module-graph and gate-registry drift
remain open."
```

### Task 5: Final end-to-end verification

**Files:**
- Verify only

**Interfaces:**
- Consumes: everything.
- Produces: the evidence that the port is complete and the docs are true.

- [ ] **Step 1: Full build, test and gate**

```bash
cd "/home/eternal/Taho/Taho Browser"
gradle :browser:shell:testDebugUnitTest
gradle verifyArchitecture
```

Expected: PASS. Report the test count from the XML rather than assuming 33:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=0
for p in glob.glob('browser/shell/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0))
print(f"tests={t} failures={f} errors={e}")
PY
```

- [ ] **Step 2: Verify every documented token matches the code**

```bash
cd "/home/eternal/Taho/Taho Browser"
for hex in 080A0D 0E1216 12161A 1A1F25 040607 EDF0F3 BCC5CE 95A0AC 828D99 666F7A 4F5861 E8AE55 EFC06C D89A3C 5BC088 82B4D6 D96A5E 1E242B 2C343C; do
  grep -q "$hex" browser/shell/src/main/java/app/taho/browser/shell/TahoTheme.kt || echo "MISSING IN CODE: $hex"
  grep -q "$hex" Doc/TAHO_BROWSER_UI_UX_SPEC.md || echo "MISSING IN SPEC: $hex"
done
echo "token cross-check done"
```

Expected: no `MISSING` lines. Any hex present in one and absent from the other is
exactly the drift this plan exists to prevent.

- [ ] **Step 3: Verify the obsolete tokens are gone from both code and docs**

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -rn 'TahoGold\|TahoFaint\|TahoJsonNum\|TahoBg\|TahoSheet\b' \
  browser/shell/src/main/ Doc/TAHO_BROWSER_UI_UX_SPEC.md \
  | grep -v 'SUPERSEDED\|superseded'
```

Expected: no output.

- [ ] **Step 4: Verify no emoji remain as icons**

```bash
cd "/home/eternal/Taho/Taho Browser"
grep -rnP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]' browser/shell/src/main/java/ || echo "no emoji in shell sources"
```

Expected: `no emoji in shell sources`.

- [ ] **Step 5: Record the outcome**

Note the final test count, whether the architecture gate passed, and the token
cross-check result in the PR description. If any check failed, say so explicitly
rather than reporting the port as complete.

---

## Exit criteria for Plan 5

- [ ] `gradle :browser:shell:testDebugUnitTest` — PASS, count reported from the XML
- [ ] `gradle verifyArchitecture` — PASS
- [ ] Every token hex appears in **both** `TahoTheme.kt` and the spec
- [ ] No obsolete token name appears in either
- [ ] No unqualified reference to the old prototype remains as current authority
- [ ] Spec §16 states the prototype's coverage gap, and the gap matches reality
- [ ] The old prototype is retained with a visible superseded banner
- [ ] README and the build plan's P1 row reflect the new chain

**Rollback:** `git revert` the five commits. Documentation-only, so reverting
leaves the app exactly as it was.

---

## Port completion checklist

All five plans are done when every line below holds.

- [ ] `gradle :browser:shell:testDebugUnitTest` green, count reported
- [ ] `gradle verifyArchitecture` green
- [ ] No colour literal in production shell code
- [ ] Every text tier clears 4.5:1; `ash` and `stone` carry no text
- [ ] Warning state signalled by colour **and** tag **and** 4dp bar
- [ ] Unavailable evidence rendered as unavailable, never as blank or zero
- [ ] Credential masking unchanged and pinned by a test
- [ ] The 64 KiB display cap distinguished from capture truncation
- [ ] No emoji as icons; every icon has a `contentDescription`
- [ ] One authored entrance motion only, gated by `TahoReducedMotion()`
- [ ] All four AGENTS.md invariants intact
- [ ] Documented tokens match code exactly
- [ ] Visual authority chain is `TahoTheme.kt` → spec → partial reference