# Handoff: KotobaDrop Project Presentation Deck

## Overview

A presentation deck introducing **KotobaDrop (言葉ドロップ)** — a native Android Japanese
typing/vocabulary game — to [audience: class / judges / stakeholders — fill in]. The deck
should share the app's own visual identity (palette, typography, background texture) rather
than look like a generic slide template with a logo dropped on top. Real screenshots of the
shipped app are included in the sibling `../screenshots/` folder (see "Screenshots" below) —
treat them as the ground truth for exact colors/type/spacing, the same way a design mockup
would be treated.

**Not included in this pass**: team member names/roles, the actual gameplay video, and final
screenshot crops — see "Fill in before build" at the bottom.

**If you're an AI system building this deck from this handoff**: also read
`PROMPT_FOR_CLAUDE_DESIGN.md` in this same folder — it states the screenshot-pairing
requirement (light + dark theme shown together on every slide) explicitly and is meant to be
pasted in alongside this file.

## Format & layout

- **Aspect ratio**: 16:9 widescreen.
- **Slide 1 (title)**: team introduction + game name/logo. Centered, calm, no media.
- **Every content slide after that**: two-column layout.
  - **Left**: explanatory text — a short headline + a few lines of body copy about one
    feature/mechanic. Not a wall of text; this is a spoken presentation, the slide is a
    prompt, not the script.
  - **Right**: media. Either a gameplay video clip (the presenter is recording these
    separately) or **two** static screenshots of the corresponding app screen — one light
    theme, one dark theme, shown side by side — when video isn't relevant or available for
    that slide. See "Screenshots" below for the dual-theme requirement in detail; leave a
    clearly bordered/labeled placeholder region where video is still pending — actual video
    isn't ready yet, this pass is layout + styling only.
  - **Important constraint**: the game is portrait-locked (phone-shaped), so each screenshot
    is a **tall, narrow rectangle**, never landscape. With two shown side by side, design the
    right column as two same-size portrait rectangles with a small consistent gap between
    them (e.g. a phone-bezel-style frame on each, or simply generous padding around each) —
    don't stretch or letterbox either into a landscape box, and don't shrink them so much
    they stop being legible.

## Design tokens (reused from the app — no new colors or fonts)

Traditional Japanese color names, pastel register. Source: `app/src/main/java/com/example/kotobadrop/core/ui/theme/Color.kt`.

| Token | Hex | Use in the app | Suggested deck use |
|---|---|---|---|
| kinari 生成り | `#FAF6EF` | background (light) | slide background |
| sakura 桜 | `#F4C9D4` | primary accent | headline underlines, primary callouts, title-slide accent |
| matcha 抹茶 | `#C5D8B9` | secondary accent | secondary callouts, "success"-flavored notes |
| sora 空 | `#BBD4E4` | tertiary accent | tertiary callouts, info notes |
| fuji 藤 | `#7A4F97` | readings/furigana, subtle accents | captions under screenshots, small labels |
| akabeni 赤紅 | `#E08A8A` | errors/misses/fail-line | sparingly — only if a slide talks about the miss/lives mechanic |
| sumi 墨 | `#3E3A39` | body text ink (never pure black) | all body text |

Deep registers (for text-sized use of an accent color, since the pastels above are too low-contrast at small sizes):

| Token | Hex |
|---|---|
| matcha deep | `#4A6B39` |
| akabeni deep | `#A83E3E` |

Dark theme ("yoru" 夜) — **only if the deck itself gets a dark variant** (see "Fill in before
build"); the app's own dark palette:

| Token | Hex | Use |
|---|---|---|
| yoru bg | `#211E1D` | dark background (warm charcoal, never pure black) |
| yoru surface | `#2C2827` | dark cards |
| kinari ink | `#EDE6DA` | dark-mode body text |
| fuji pastel | `#CDBBDD` | dark-mode captions/readings |

Accent pastels (sakura/matcha/sora) stay **identical** in both themes — only the ground
(background/surface/ink) swaps. If the deck does a dark variant, follow the same rule: don't
re-tint the accents.

## Typography

Both fonts are bundled in the app (`app/src/main/res/font/`) as `.ttf` — pull the actual files
if the design tool needs them rather than approximating with a system font.

- **Hachi Maru Pop** — bubbly, playful, single weight. Used in the app for the logo/display
  text, headlines, and titles only (short strings — its glyph widths are wide/uneven, so the
  app deliberately avoids it for anything that needs to lay out predictably). **Deck use**:
  slide titles, the game's name/logo on the title slide, section headers. Not body copy.
- **Yusei Magic** — a different playful/rounded single-weight font, designed for on-screen
  Japanese UI legibility, more consistent glyph widths. Used in the app for everything else
  (body, labels, buttons). **Deck use**: all body copy, bullet points, captions.

## Background

A **seigaiha (青海波, "blue ocean wave")** pattern — rows of overlapping concentric
semicircles — drawn as a large-scale, extremely quiet texture. In the app it's ~20% opacity
in `sora` on the kinari background (light theme), ~3.5% on dark. It should read as a barely-
there texture, not a decoration competing with slide content. See
`app/src/main/java/com/example/kotobadrop/core/ui/theme/SeigahaBackground.kt` for the exact
draw logic if a literal vector recreation is wanted; otherwise a static tiled SVG/PNG
approximating the same wave-row pattern at the same low opacity is fine for slides (it
doesn't need to be redrawn per-slide the way the app draws it in code).

**Nothing else is decorated or animated on the slides** — same "calm chrome, one signature
moment" principle as the app itself (see slide 9 below, the one place a bit of motion/energy
is actually warranted).

## Slide list

Twelve slides suggested below; treat the count/order as adjustable, not fixed — cut or merge
if the presentation has a tighter time limit.

Every slide below that names a screen (not a video) uses the matching numbered pair from
`../screenshots/light/` and `../screenshots/dark/` — e.g. "Home screen" means
`light/01_home.png` shown together with `dark/01_home.png`. See "Screenshots" below for the
full filename list and the dual-theme placement rule.

### 1. Title — Team & Game
- Game name, both scripts together: **言葉ドロップ** (large, Hachi Maru Pop) with
  **KotobaDrop** underneath (smaller, Yusei Magic) — exactly like the app's own Home screen
  logo lockup (`01_home.png` in both theme folders).
- Team member names + roles (placeholder — see below).
- No screenshot pair needed here — this slide is text/logo only, centered, generous
  whitespace. (If the logo lockup is pulled directly from a screenshot instead of redrawn,
  apply the same light+dark pairing rule used everywhere else.)

### 2. What is KotobaDrop — the pitch
- Left: Japanese words fall from the top of the screen, showing their kanji. Type the
  reading before the word crosses the line. One game, two audiences: a fast typing
  challenge for Japanese speakers, and a vocabulary/kanji-reading tutor for learners —
  the same content, the same mechanic, no separate "easy mode" that waters down the game.
- Right: Home screen, both themes (`01_home.png`).

### 3. Core gameplay loop
- Left: describe the loop concretely — words fall showing kanji; type the romaji reading;
  it converts to kana live as you type; whichever on-screen word your input matches
  highlights and clears automatically (no tapping/targeting); miss a word and it costs a
  life, flashing its reading briefly as a last teaching moment before it's gone.
- Right: **video** placeholder (this is the single best slide for a live clip — shows the
  words falling, typing, live kana preview, and a clear all in one take). If a static
  fallback is needed before the video is ready, use the gameplay pair (`03_gameplay.png`,
  both themes).

### 4. Input feedback — designed to read at speed
- Left: three at-a-glance signals while typing: (1) live kana conversion above the input
  field, (2) the word your input is aimed at highlights — a solid colored pill, chosen
  specifically for contrast at typing speed, (3) input that matches nothing on screen turns
  red immediately, so you know to backspace without breaking stride.
- Right: **video** placeholder, or the gameplay pair (`03_gameplay.png`, both themes) as a
  static fallback (shows falling words + furigana + input field together).

### 5. Two difficulty dials, not one slider
- Left: Speed (spawn rate + fall velocity, four steps, and it ramps up within a single run)
  and Word Difficulty (five tiers, roughly kana-only through JLPT N5→N2) are independent —
  a strong reader can play at a relaxed pace, a fast typist can play against easy
  vocabulary. Furigana toggles separately on top of both.
- Right: Endless setup screen, both themes (`02_endless_setup.png`).

### 6. It learns what you don't know
- Left: every word you meet gets logged — seen, cleared, missed. Spawning quietly leans
  toward words you've missed and haven't cleared yet, and toward common vocabulary first
  for new players, fading out as you meet more words. Nothing is ever locked away — it's a
  soft bias, not a gate.
- Right: Stats screen, both themes (`09_stats.png`).

### 7. Two ways to play
- Left: **Endless** — pick a difficulty, play until you're out of lives, chase your high
  score. **Campaign** — five word-tier sections, five levels each, structured targets and
  star ratings, unlocking as you clear NORMAL-speed levels. A run that passes its target
  keeps going — clearing the bar locks in the win, but score-attack continues.
- Right: Campaign screen, both themes (`05_campaign.png`). The Results/Level Result pairs
  (`04_results.png`, `06_level_result.png`) are also available if the slide wants to show
  an end-of-run screen instead of/alongside the level grid.

### 8. Your own study record
- Left: the Dictionary isn't just a word list — it's a browsable history of everything
  you've played, searchable by kanji/reading/meaning/romaji, missed words surfaced on top,
  per-word stats. It's the same underlying data driving the adaptive spawning in slide 6.
- Right: Dictionary screen, both themes (`07_dictionary.png`).

### 9. The one signature moment
- Left: everything on screen stays quiet and calm by design — except clearing a word,
  which bursts into a handful of drifting sakura petals (with a reduced-motion-safe color
  flash instead, for players who need it). One memorable moment, not a screen full of
  particle effects.
- Right: **video** placeholder — this is the second-best slide for a live clip
  specifically (needs motion to land; a screenshot pair can't really show a burst).

### 10. Built for a bilingual audience, offline
- Left: full English/Japanese UI toggle, switchable anytime in Settings. Fully offline —
  every word, every font, every asset ships in the app; nothing needs a network call at
  runtime.
- Right: Settings screen, both themes (`08_settings.png`). Convenient bonus: the reference
  screenshots themselves already demonstrate this — every `light/` capture is in Japanese
  and every `dark/` capture is in English, so this slide's pair shows the language toggle
  *and* the theme toggle at once, for real, without staging anything extra.

### 11. Under the hood (optional — cut if time-constrained)
- Left: Kotlin + Jetpack Compose, a from-scratch romaji→kana converter (handles both
  Hepburn and Kunrei spellings, sokuon, ん, long vowels — exhaustively unit tested), a
  real Japanese dictionary dataset (JMdict + KANJIDIC2, ~6,950 words after filtering),
  Room for local persistence, no backend.
- Right: no screenshots needed — this slide can be text-only, or a simple architecture
  diagram if the team wants one (out of scope for this handoff).

### 12. Closing — thank you / try it
- Mirrors slide 1's layout (logo lockup, centered) for a bookend feel. Team
  contact/repo link if relevant, "questions" prompt.

## Fill in before build

- **Team member names + roles** (not known to this handoff — placeholder text only).
- **Audience / occasion** for this presentation (class project, hackathon, portfolio
  review, etc.) — affects tone and whether slide 11 (tech stack) belongs at all.
- **Time limit** (drives whether all 12 slides survive, or a tighter subset).
- **Actual media** — the gameplay video(s) and any final-crop screenshots. Every
  screenshot-based slide is already fully covered by real captures (see "Screenshots"
  below); only the two video-placeholder slides (3 and 9) are still waiting on the
  presenter's own recordings.

## Screenshots

Real captures from the shipped app (Pixel 6a emulator), not mockups — ground truth for
color/type/spacing/background texture, and the actual images every content slide's right
column uses. Two matched sets, same 11 screens, same filenames, one folder each:

- `../screenshots/light/` — light theme, **Japanese** UI.
- `../screenshots/dark/` — dark theme, **English** UI.

This pairing (light=JA, dark=EN) was a deliberate choice, not two independent random
samples — it lets a single side-by-side image pair demonstrate both the theme toggle and
the language toggle at once (see slide 10). Match files by name across the two folders —
`light/01_home.png` pairs with `dark/01_home.png`, and so on:

| File | Screen |
|---|---|
| `01_home.png` | Home |
| `02_endless_setup.png` | Endless setup |
| `03_gameplay.png` | Live gameplay |
| `04_results.png` | Results (end of an Endless run) |
| `05_campaign.png` | Campaign level grid |
| `06_level_result.png` | Level Result (end of a Campaign level) |
| `07_dictionary.png` | Dictionary |
| `08_settings.png` | Settings |
| `09_stats.png` | Stats |
| `10_tutorial.png` | How to Play (Tutorial) |
| `11_credits.png` | Credits |

Every content slide in the "Slide list" above that names a screen references one of these
pairs by filename. **The dual-theme placement rule (both images from a pair shown together
on the same slide) is the load-bearing requirement of this handoff — see
`PROMPT_FOR_CLAUDE_DESIGN.md` for the explicit instruction.**
