# CLAUDE.md

Project context and step-by-step build plan for **KotobaDrop (言葉ドロップ)** — a native Android Japanese typing game. This file is the source of truth: if any instruction elsewhere conflicts with it, surface the conflict instead of silently picking one. Work through the build steps (§12) **in order**; each has acceptance criteria, and step 2 ends in a mandatory human checkpoint.

**Pitch:** Japanese words fall from the top of the screen; type their reading before they cross the line. An arcade typing game for Japanese players, a vocabulary/kanji-reading tutor for learners — the same game, played by both.

---

## 1. Scope and audiences

This is a **single-game app**.

Two audiences, served asymmetrically by the same content:

- **Foreign learners of Japanese** (≈ JLPT N5–N2) — the primary learning audience; furigana mode, the met-words dictionary, and adaptive spawning serve them.
- **Japanese players** — reading is trivial for them, so the game is a typing-speed challenge. This is embraced: scoring rewards speed. Do **not** build an English-meaning-recall variant.

### Hard constraints

- **Fully offline at runtime.** No network calls in the shipped app; all data and fonts are bundled. If a task seems to need the network, stop and flag it.
- **Bilingual UI, English default**, Japanese toggle in settings. UI chrome only — game word content is unaffected by UI language.
- **Japanese pastel visual identity** (§10) — not Material defaults.

---

## 2. Tech stack

- **Kotlin + Jetpack Compose + Material 3** with a custom pastel color scheme (§10)
- **Room** — word data, met-words log, high scores; word DB ships prepopulated via `createFromAsset()`
- **DataStore (Preferences)** — settings
- **Compose Navigation** — Home → Game → Results, plus Dictionary, Settings, Credits
- **ViewModel + StateFlow** — game state; loop driven by `withFrameNanos`
- **Min SDK 26**, target latest stable; single Gradle module (multi-module is overkill here)
- **appcompat 1.6+** is needed solely for per-app locales (§9)

`tools/preprocess/` lives at repo level (not in the app): the offline word-data script (§6).

---

## 3. Core game loop

Words fall showing their **kanji surface** (e.g. 食べる). The player types the **reading** (たべる). Input is matched live against **every word on screen** — whichever word the input completes is cleared. No manual targeting.

Rules that make this work:

- **Prefix-collision rule.** Never have two words on screen where one reading is a prefix of the other (か vs かい) — enforce at spawn time by skipping/resampling the candidate. Backstop: if an exact match somehow hits multiple words, clear the one closest to the fail line.
- **Concurrency cap.** At most ~5 words on screen; a spawn tick with a full screen is skipped. Keeps matching readable and bounds the collision check.
- **Input buffer lifecycle.** Auto-clear on a successful match. On a miss, clear only if the buffer was a prefix of the missed word (don't punish the player for typing a different word). When the converted buffer prefixes *no* on-screen word, tint it `akabeni` as a "dead input" signal — one glance tells the player to backspace.
- **Miss = teaching moment.** A word crossing the fail line costs a life and briefly flashes its **reading** (kanji words only — a kana-only word's reading is already its surface, so nothing extra shows) in `akabeni` before disappearing.
- **Lives.** 3; a miss costs one; zero ends the run → Results (score, max combo, cleared, missed, accuracy, play again).
- **Pause.** A pause button, and auto-pause when the app loses focus mid-run (lifecycle stop). Never let a backgrounded run keep losing lives.

## 4. Input system — the riskiest piece, built and proven first (§12 steps 1–2)

**Primary: romaji the app converts to kana itself.** Latin keyboard only — no Japanese IME dependency, no cross-device IME surprises, the app owns the whole pipeline. Matching is **kana-accurate**: とうきょう requires `toukyou`, not `tokyo` (correct for reading practice; show a one-time hint about long vowels).

**Fallback (settings toggle): system Japanese IME**, typing hiragana directly — for Japanese players and advanced learners. Keep it isolated and off the critical path; if composing-region behavior is fiddly across keyboards, match on committed text only.

### Romaji → kana converter (`input/`, pure Kotlin)

Longest-match lookup table plus a few rules. Must handle:

- Gojūon + voiced/semi-voiced + yōon digraphs (kya, sha, cho…)
- **Both Hepburn and Kunrei/wāpuro variants** — `shi/si`, `chi/ti`, `tsu/tu`, `fu/hu`, `ji/zi`, `sha/sya`, `cha/tya`, `ja/jya/zya`, etc. Learners type Hepburn; Japanese typists often type Kunrei. Costs only table rows.
- Sokuon: doubled consonant → small っ (`kk`, `tt`, `ssh`…)
- ん: `n` before a consonant; `nn` or `n'` before vowels/y or at end
- Long vowels stay literal: `ou` → おう, `oo` → おお — whatever the stored reading says

API: `toKana(buffer): String` (best-effort; a trailing partial syllable is shown as-is), plus prefix-of-reading and equals-reading predicates. **Pure logic — unit-test it exhaustively; it's the cheapest correctness win in the project.**

**Matching is dual-path (2026-07-19):** the predicates accept the buffer if its kana conversion matches the reading **or** if the raw buffer string-matches the reading's canonical Hepburn romaji (`toRomaji`). Rationale: the dictionary displays てぃ words like パーティー as "paatii", but typed `ti` is Kunrei ち — the kana path alone rejected the app's own displayed spelling. The romaji path also admits standard-Hepburn `matcha` (っ→"t" before ch) and `sampo` (ん→"m" before b/m/p). Long-vowel strictness is preserved (canonical romaji spells vowels out, so `tokyo` still fails). The kana preview remains conversion-based — display and matching are deliberately decoupled.

### Live input feedback (core, not polish)

1. Render the buffer **as kana while the player types** (`toukyo` → とうきょ) — teaches the mapping and confirms input.
2. **Highlight** on-screen words whose reading the current input prefixes — with several words falling, the player must see what they're aimed at, or fast typing feels random.

### Keyboard handling (known Android friction — handle deliberately)

- A visually minimal `TextField` with **autocorrect and suggestions disabled** and an ASCII keyboard type, so the keyboard never mangles romaji. (The exact `KeyboardOptions` parameter names vary by Compose version — the requirement is the behavior.)
- Focus pinned via `FocusRequester` for the whole run; re-request if the keyboard is dismissed.
- Portrait-locked game screen; the fail line and all falling words must stay visible **above the soft keyboard** (`imePadding()`, plan the play area assuming the keyboard occupies the lower ~40%).

## 5. Difficulty, scoring, adaptivity

### Two independent dials — never collapse into one slider

- **Speed** (Easy / Normal / Hard / Expert): spawn rate + fall velocity. Both also **ramp gently upward within a run**, so an endless run has an arc.
- **Knowledge**: word-pool tier ceiling (0–4, §6) + furigana.
  - Furigana **ON** = reading shown above the word → recall-free study/beginner mode.
  - Furigana **OFF** = recall is the challenge.
  - Render the **whole reading above the whole word** — JMdict has no per-kanji alignment; per-character ruby is out of scope.

### Scoring

```
b = b0 · (1 + κ·g) · (1 + λ·L)      // word value: g = hardest-kanji grade (0 if kana-only), L = kana length
p = b · (1 + α·C) · s(Δt)           // payout: C = combo (resets on miss), Δt = secs since last clear
s(Δt) = max(1, s_max − r·Δt)
```

Longer/harder words pay proportionally more — which also offsets their longer typing time inflating Δt. All tunables live in one `GameTuning` object; starting values (tune by playtesting): `b0=10, κ=0.3, λ=0.15, α=0.10, s_max=2.0, r=0.2`.

### Adaptive spawn weighting — the feature that makes it a tutor

Bias spawning toward missed and unmet words, away from reliably cleared ones, using counters already collected (§7):

```
weight = base · (1 + missBonus·timesMissed) · (unmet ? unmetBoost : 1) / (1 + clearDamp·timesCleared)
```

Clamp weights so nothing dominates or starves; sample only from tiers the knowledge dial allows; apply the prefix-collision rule after sampling.

### Out of scope — do not build

Power-ups, multiplayer, TTS pronunciation (online-leaning), English-meaning mode, per-kanji ruby, leaderboards beyond local high scores.

---

## 6. Word data pipeline (offline preprocessing — never at runtime)

Sources (both EDRDG, **CC BY-SA, attribution required**, §11): **JMdict** (surface, reading, glosses) and **KANJIDIC2** (per-kanji grade/JLPT).

A standalone Python script in `tools/preprocess/` (with a README on how to run it):

1. Parse JMdict; keep **common entries only** (priority/frequency tags).
2. Grade each word by its **hardest constituent kanji** (KANJIDIC2).
3. Bucket into tiers: **0 = kana-only floor, 1–4 ≈ JLPT N5→N2**.
4. Pick one concise English gloss per word.
5. **Exclude readings ≤ 1 kana** (un-typeable at speed; worst prefix-collision offenders).
6. **Use the JMdict entry sequence number as the word's primary key**, so player history (met-words) survives future DB regenerations.
7. Emit `words.db` (SQLite matching the Room schema) → `assets/`, loaded via `createFromAsset()`.

Target several thousand words. The app never parses JMdict at runtime.

## 7. Data model

```
WordEntity:    id (JMdict seq), surface, reading (hiragana), meaning,
               tier (0..4), hardestKanjiGrade, frequencyRank, kanaOnly

MetWordEntity: wordId (FK), firstSeen, timesSeen, timesCleared, timesMissed
               // created on FIRST APPEARANCE, not on clear

ScoreEntity:   id, score, playedAt, maxCombo, wordsCleared, wordsMissed,
               speedDifficulty, knowledgeDifficulty
```

DataStore: `uiLanguage` (EN default | JA), `inputMode` (ROMAJI default | IME), `furigana`, `speedDifficulty`, `knowledgeDifficulty`, `soundEnabled`.

The met-words counters serve **two** purposes: the dictionary (§8) and adaptive weighting (§5). One dataset, two uses.

## 8. Dictionary of met words

Logged on first appearance; encounters/clears/misses increment counters. Browsable screen: surface, reading, meaning, per-word stats; searchable; a "words you keep missing" section on top. This is the player's study record — treat it as a first-class screen, not an afterthought.

## 9. Bilingual UI

All user-facing strings via string resources (`values/` = EN default, `values-ja/`). Never hardcode display text. The settings toggle writes `uiLanguage` and applies via per-app locales (`AppCompatDelegate.setApplicationLocales`, hence the appcompat dependency).

---

## 10. Visual identity — Japanese pastel

The brief: a **Japanese-themed pastel look** — soft and friendly, not Material defaults, and not cherry-blossom clipart. Calm chrome, one memorable signature element.

### Palette (traditional Japanese colors, pastel register)

| Token | Hex | Use |
|---|---|---|
| `kinari` (生成り, undyed cloth) | `#FAF6EF` | background |
| `sakura` (桜) | `#F4C9D4` | primary; combo highlights, petal burst |
| `matcha` (抹茶) | `#C5D8B9` | secondary; success, cleared-word flash |
| `sora` (空, sky) | `#BBD4E4` | tertiary; info, prefix-match highlight |
| `fuji` (藤, wisteria, deep register) | `#7A4F97` | furigana text, subtle accents — darkened from the original `#CDBBDD` pastel, which measured ~1.7:1 contrast on `kinari` and was unreadable |
| `akabeni` (赤紅, softened) | `#E08A8A` | fail line, lives, misses, dead-input tint |
| `sumi` (墨, ink) | `#3E3A39` | text — never pure black |

Map into a custom Material 3 **light** `ColorScheme` (light theme is the identity). Containers are the pastels; on-colors are `sumi`. Contrast stays accessible: pastel backgrounds with `sumi` text, never pastel-on-pastel for anything that must be read.

**Dark theme ("yoru" 夜, built 2026-07-18, follows system setting):** swaps the ground, not the accents — background `#211E1D` (warm sumi-adjacent charcoal, never pure black), text ink `#EDE6DA` (kinari repurposed as ink), pastel containers unchanged and keeping `sumi` on-colors in both themes. Accent text on the dark background reuses the pastels themselves (`matcha`/`akabeni` as success/danger — the same colors too faint *on* kinari are legible on charcoal); furigana uses fuji's original pastel `#CDBBDD` (the pre-darkening value). Implementation: `KotobaPalette` semantic colors (`ink`/`furigana`/`success`/`danger`/`fieldBackground`) via a CompositionLocal in `core/ui/theme/Theme.kt` — screens must use `KotobaTheme.palette` for any color sitting on the background/surface, and literal `Sumi` only for on-pastel content.

### Typography (bundled font files — offline, no downloadable-fonts provider)

- **Display / headings / title: Hachi Maru Pop** — bubbly, playful comic-style lettering for a deliberately child-friendly feel. Single weight (Regular only). Went app-wide briefly (2026-07-17), then scoped back down: its glyph widths are wide and uneven enough to cause real layout bugs on text that has to lay out predictably — segmented-control chip labels wrapping mid-word, falling words drifting past the screen's right edge. Fine on short, static display text where that risk doesn't apply.
- **Body / labels / falling words / buttons / dictionary: Yusei Magic** — a different single-weight playful font (rounded marker style), chosen specifically because Google's Japanese type team designed it for on-screen UI legibility, with more consistent character widths than Hachi Maru Pop. Keeps the child-friendly feel without the layout risk. Verified against `words.db` (all 1,796 unique characters across all 6,949 words have glyphs, zero fallback) before adopting.
- `res/font/noto_sans_jp.ttf` (the original body font, superseded first by app-wide Hachi Maru Pop, then by this split) has been deleted — fully unused, no code or credits-screen reference.

Falling words render large and crisp; furigana above them, smaller, in `fuji`.

### Signature element — spend the boldness here, keep everything else quiet

**Cleared words burst into drifting sakura petals**: a small particle burst at the word's position, petals in `sakura` fading as they fall. The fail line is a thin `akabeni` line — a quiet nod to torii red. Background: `kinari` with an *extremely* subtle large-scale seigaiha wave pattern at ~3–4% opacity, drawn in code rather than a bitmap. Nothing else is animated or decorated — buttons, cards, and screens stay calm so the burst is the one memorable thing. **Respect reduced-motion settings**: replace the burst with a color flash.

### Writing in the UI

Plain verbs, sentence case, no filler ("Start run", not "Let's go!!"). Errors say what happened and what to do next. The empty dictionary invites action ("Play a round to start collecting words").

---

## 11. Licensing

JMdict and KANJIDIC2 are CC BY-SA (EDRDG); bundled fonts are SIL OFL. Ship a real **Credits screen** acknowledging EDRDG, both data licenses, and the fonts. It's a build step (§12 step 8), not an afterthought.

---

## 12. Build steps — follow in order

Don't start a step until the previous step's acceptance criteria pass. Step 1 is first on purpose: it's the highest-risk component and everything else builds around it.

### Step 1 — Input core (pure logic, no UI)
Romaji→kana converter and matching predicates in `input/`, pure Kotlin.
**Accept when:** unit tests pass covering gojūon, voicing, yōon, sokuon, ん (`nn` / `n'` / n-before-consonant), Hepburn **and** Kunrei variants, long vowels (`ou` vs `oo`), trailing partial syllables, and the prefix/equality predicates against sample readings.

### Step 2 — Input prototype screen
Throwaway Compose screen: 10–15 hardcoded readings falling as plain text; pinned `TextField` (autocorrect/suggestions off, ASCII); live kana rendering; prefix highlighting; dead-input tint; clear-on-match; buffer lifecycle per §3. Keyboard stays open; play area sits above it.
**Accept when:** on a real device or emulator, typing romaji clears falling words reliably and the loop *feels* responsive. **Human checkpoint — stop and get the developer's confirmation of feel before proceeding.**

### Step 3 — Word data pipeline
`tools/preprocess/` per §6, README included.
**Accept when:** the script runs offline on the raw downloads and emits a DB with several thousand rows, a sensible tier distribution (spot-check ~20 words per tier), no readings ≤ 1 kana, and JMdict seq numbers as IDs.

### Step 4 — App skeleton + theme
Navigation graph (Home, Game, Results, Dictionary, Settings, Credits — placeholders fine), Room via `createFromAsset("words.db")`, DataStore repo, and the **full §10 theme**: palette as a Material 3 scheme, bundled fonts, kinari background with subtle seigaiha.
**Accept when:** the app launches into a themed Home screen, DB queries return words, and settings persist across restarts.

### Step 5 — Full game loop
Spawning from the real pool (tier filter, prefix-collision rule, concurrency cap), fall physics via `withFrameNanos`, lives, fail line, miss teaching-moment, pause + lifecycle auto-pause, scoring per §5 (combo, speed multiplier, in-run ramp), furigana rendering, Results screen with play-again.
**Accept when:** a full run plays start → game-over → results with correct scoring (verify formulas against a couple of hand-computed cases), furigana follows settings, and backgrounding mid-run pauses the game.

### Step 6 — Persistence + adaptivity
Met-word logging (create on first appearance; increment seen/cleared/missed), score saving, adaptive spawn weighting with clamped weights.
**Accept when:** DB counters match a played run's events, high scores list correctly, and a word deliberately missed several times observably spawns more often next run.

### Step 7 — Dictionary screen
Per §8: search, per-word stats, "words you keep missing" on top, themed empty state.
**Accept when:** it reflects play history accurately and search works.

### Step 8 — Settings, bilingual, credits
Settings (language, input mode, furigana, both difficulty dials, sound); complete EN + JA string resources; per-app locale switching; Credits screen per §11.
**Accept when:** every visible string switches with the language toggle, all settings round-trip through DataStore, and credits are complete.

### Step 9 — Signature polish
Sakura petal burst (with reduced-motion fallback), seigaiha refinement, transitions, app icon in the palette; optionally a few soft bundled SFX (clear / miss / game-over) behind `soundEnabled`, default off.
**Accept when:** the burst holds full frame rate on a mid-range device and motion is disabled when the system requests reduced motion.

### Step 10 — IME fallback input (last; drop if time runs out)
Hiragana-direct input behind the settings toggle, isolated from the romaji path; committed-text matching if composing regions misbehave.
**Accept when:** a run is playable with a Japanese IME and the romaji path is untouched.

---

## 13. Working rules

- The two difficulty dials stay orthogonal.
- The converter is the cheapest correctness win — when in doubt, add a test.
- The romaji path is primary; IME quirks must never block the main loop.
- Everything bundled; no runtime network.
- Visual identity per §10 — no drifting back to Material defaults, no decoration beyond the signature element.
- When in doubt about scope, finish the core loop well rather than adding features.
