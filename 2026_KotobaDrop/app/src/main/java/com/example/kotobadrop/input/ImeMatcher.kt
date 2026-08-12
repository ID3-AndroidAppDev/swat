package com.example.kotobadrop.input

/**
 * Matching for the IME fallback input path (§4/§12 step 10): the system Japanese keyboard
 * commits hiragana/katakana text directly, so there is no romaji-to-kana conversion step
 * here — deliberately isolated from [RomajiConverter.toKana], not reused as a passthrough,
 * because its sokuon detection (`s[i+1] == s[i]`) misfires on any two identical consecutive
 * kana characters that aren't actually a doubled consonant (e.g. `toKana("ぱぱ")` produces
 * "っぱ", not "ぱぱ") — safe for romaji input (where that pattern only occurs for real
 * doubled consonants like "kk"), wrong for already-kana input.
 */
object ImeMatcher {

    /** Returns true if [buffer] (already hiragana/katakana, as committed by the IME) is a prefix of [reading]. */
    fun isPrefixOfReading(buffer: String, reading: String): Boolean {
        if (buffer.isEmpty()) return true
        return reading.startsWith(normalize(buffer))
    }

    /** Returns true if [buffer] converts to exactly [reading]. */
    fun isEqualToReading(buffer: String, reading: String): Boolean {
        if (buffer.isEmpty()) return reading.isEmpty()
        return normalize(buffer) == reading
    }

    /**
     * Looser than [isPrefixOfReading]: true if [buffer] and [reading] merely share a
     * non-empty leading run of characters, even once the buffer has diverged into dead
     * input. See [RomajiConverter.sharesPrefixWithReading]'s doc for why this exists.
     */
    fun sharesPrefixWithReading(buffer: String, reading: String): Boolean {
        if (buffer.isEmpty()) return false
        return normalize(buffer).commonPrefixWith(reading).isNotEmpty()
    }

    // Readings in words.db are always hiragana with long vowels spelled out (build_words_db.py's
    // normalize_reading) — the IME may commit katakana (for loanwords) or a literal ー
    // chōonpu mark, so both need folding before comparison.
    private fun normalize(text: String): String = RomajiConverter.expandChoonpu(katakanaToHiragana(text))

    private fun katakanaToHiragana(text: String): String =
        text.map { c -> if (c in 'ァ'..'ヶ') c - 0x60 else c }.joinToString("")
}
