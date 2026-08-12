# Prompt for Claude design

Paste this alongside `README.md` (same folder) when handing this off to build the actual
slide mockups. `README.md` has the full spec — palette, typography, background, slide-by-
slide content. This file states one requirement explicitly, because it's easy to miss if a
design tool defaults to "pick one screenshot per slide."

---

Design a 16:9 presentation deck for **KotobaDrop (言葉ドロップ)**, a native Android Japanese
typing/vocabulary game, following the attached handoff spec (`README.md`) for palette,
typography, background pattern, layout, and slide-by-slide content.

**Screenshot requirement — every content slide must show both themes together.**

For every slide whose right-column media is a screenshot (not a video), show **two**
screenshots side by side: one from `../screenshots/light/`, one from `../screenshots/dark/`,
matched by filename (e.g. `light/03_gameplay.png` next to `dark/03_gameplay.png`). Do not
substitute a single theme, do not put light and dark on separate slides, and do not build a
toggle/interaction to switch between them — both must be simultaneously visible on the same
static slide.

Specifics:
- Frame both screenshots as equal-size tall portrait rectangles (the app is phone-portrait-
  locked, so neither image is ever landscape), placed side by side with a small, consistent
  gap between them — roughly the same combined width a single screenshot would have
  occupied in a one-image layout, so the two-image version doesn't overwhelm the slide.
- A phone-bezel-style frame around each is welcome if it reads as a considered design
  choice rather than decoration for its own sake; plain generous padding around each image
  works just as well.
- A small label under or beside each — "Light" / "Dark", or a sun/moon glyph — is optional
  but helpful for at-a-glance recognition. Keep it quiet: 10–11sp, muted color, not a
  headline.
- The two screenshot sets are in **different UI languages on purpose** — every `light/`
  image is Japanese, every `dark/` image is English. This is intentional (it lets one slide
  demonstrate both the theme toggle and the language toggle at once — see slide 10 in
  `README.md`), not a mismatch to fix. Don't retranslate, relabel, or otherwise edit the
  screenshots themselves.
- Slides 3 and 9 call for a **video** placeholder instead (per `README.md`) — the dual-
  screenshot rule doesn't apply there. Slide 3 has a static fallback available
  (`03_gameplay.png`) if a temporary image is needed before the real video exists — apply
  the same side-by-side dual-theme rule to it if used. Slide 9 (the sakura-petal burst) has
  no static equivalent — a still frame can't show a burst — so leave its placeholder as
  video-only, no screenshot fallback.
- Slides 1 and 12 (title/closing) don't require screenshots — they're a text/logo lockup.
  If the logo lockup is built by cropping it out of a Home screenshot rather than redrawing
  it, apply the same light+dark pairing there too, for consistency.

Everything else — overall layout, color tokens, fonts, background texture, per-slide
copy — follows `README.md` as written. Ask before deviating from it rather than guessing.
