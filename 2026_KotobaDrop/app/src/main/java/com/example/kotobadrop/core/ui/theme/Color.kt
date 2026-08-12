package com.example.kotobadrop.core.ui.theme

import androidx.compose.ui.graphics.Color

// Traditional Japanese colors, pastel register — see CLAUDE.md §10.
val Kinari = Color(0xFFFAF6EF) // 生成り, undyed cloth — background
val Sakura = Color(0xFFF4C9D4) // 桜 — primary; combo highlights, petal burst
val Matcha = Color(0xFFC5D8B9) // 抹茶 — secondary; success, cleared-word flash
val Sora = Color(0xFFBBD4E4) // 空 — tertiary; info, prefix-match highlight
val Fuji = Color(0xFF7A4F97) // 藤 (deep register) — furigana text, subtle accents; darkened from CDBBDD for legible contrast on kinari
val Akabeni = Color(0xFFE08A8A) // 赤紅 (softened) — fail line, lives, misses, dead-input tint
val Sumi = Color(0xFF3E3A39) // 墨 — text, never pure black

// Deep registers for readable text — the pastels above are ~1.4-2.4:1 contrast on kinari
// (fine for large filled shapes like the lives hearts, unreadable for prose-sized text).
val MatchaDeep = Color(0xFF4A6B39) // 抹茶 deep — success/won text
val AkabeniDeep = Color(0xFFA83E3E) // 赤紅 deep — fail/lost text

// Dark-theme ("yoru" 夜, night) registers. The identity stays the light kinari theme (§10);
// dark mode swaps the ground, not the accents: pastel containers keep Sumi ink on them in
// both themes, while text on the background flips to a kinari-register ink. Matcha/Akabeni
// double as the dark theme's readable success/danger text — the same pastels that are too
// faint *on* kinari are comfortably legible on charcoal.
val YoruBg = Color(0xFF211E1D) // warm sumi-adjacent charcoal — never pure black
val YoruSurface = Color(0xFF2C2827) // elevated surfaces (cards) on YoruBg
val YoruField = Color(0xFF353029) // input-field fill on YoruBg
val YoruAkabeniContainer = Color(0xFF4A2C2C) // errorContainer register on YoruBg
val KinariInk = Color(0xFFEDE6DA) // text on dark — kinari repurposed as ink
val FujiPastel = Color(0xFFCDBBDD) // 藤 original pastel — furigana on dark (the pre-darkening value; Fuji was deepened only for contrast on kinari)

// Redesign pass (2026-07-20): furigana/reading text sitting on the solid ai/indigo (Fuji)
// game-highlight pill — a literal on-pastel content color like Sumi, identical in both
// themes since the pill itself never changes with theme.
val IndigoPillFurigana = Color(0xFFEFE2F5)
