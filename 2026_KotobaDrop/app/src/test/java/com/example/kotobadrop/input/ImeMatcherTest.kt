package com.example.kotobadrop.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeMatcherTest {

    @Test
    fun emptyBufferIsAlwaysAPrefix() {
        assertTrue(ImeMatcher.isPrefixOfReading("", "えちけっと"))
    }

    @Test
    fun emptyBufferEqualsOnlyEmptyReading() {
        assertTrue(ImeMatcher.isEqualToReading("", ""))
        assertFalse(ImeMatcher.isEqualToReading("", "あ"))
    }

    @Test
    fun hiraganaPrefixMatches() {
        assertTrue(ImeMatcher.isPrefixOfReading("えち", "えちけっと"))
    }

    @Test
    fun hiraganaExactMatch() {
        assertTrue(ImeMatcher.isEqualToReading("えちけっと", "えちけっと"))
        assertFalse(ImeMatcher.isEqualToReading("えち", "えちけっと"))
    }

    @Test
    fun nonMatchingPrefixFails() {
        assertFalse(ImeMatcher.isPrefixOfReading("かい", "えちけっと"))
    }

    @Test
    fun katakanaInputMatchesHiraganaReading() {
        // Readings are always stored normalized to hiragana (build_words_db.py), but a
        // Japanese IME commonly commits katakana for loanwords.
        assertTrue(ImeMatcher.isEqualToReading("エチケット", "えちけっと"))
        assertTrue(ImeMatcher.isPrefixOfReading("エチ", "えちけっと"))
    }

    @Test
    fun literalChoonpuMatchesExpandedReading() {
        // ボール normalizes to ぼおる at DB-build time; the IME may commit either the
        // katakana surface itself or a hiragana spelling with a literal ー.
        assertTrue(ImeMatcher.isEqualToReading("ボール", "ぼおる"))
        assertTrue(ImeMatcher.isEqualToReading("ぼーる", "ぼおる"))
    }

    @Test
    fun repeatedIdenticalKanaIsNotMisreadAsSokuon() {
        // Regression guard: RomajiConverter.toKana("ぱぱ") would wrongly produce "っぱ"
        // (its sokuon check fires on any two identical consecutive characters) — this is
        // exactly why ImeMatcher does not reuse toKana() as a passthrough.
        assertTrue(ImeMatcher.isEqualToReading("ぱぱ", "ぱぱ"))
        assertEquals("っぱ", RomajiConverter.toKana("ぱぱ"))
    }

    @Test
    fun emptySharesPrefixIsAlwaysFalse() {
        // Unlike isPrefixOfReading, empty shares nothing — clearing an already-empty
        // buffer on a miss is a harmless no-op either way.
        assertFalse(ImeMatcher.sharesPrefixWithReading("", "えちけっと"))
    }

    @Test
    fun sharesPrefixSurvivesATypoThatDerailsTheBuffer() {
        // "えけ" is a real divergence (け for ち) rejected by isPrefixOfReading, but
        // still shares the leading え with えちけっと.
        assertFalse(ImeMatcher.isPrefixOfReading("えけ", "えちけっと"))
        assertTrue(ImeMatcher.sharesPrefixWithReading("えけ", "えちけっと"))
    }

    @Test
    fun sharesPrefixFalseForACompletelyDifferentWord() {
        assertFalse(ImeMatcher.sharesPrefixWithReading("かい", "えちけっと"))
    }

    @Test
    fun sharesPrefixNormalizesKatakanaBeforeComparing() {
        assertTrue(ImeMatcher.sharesPrefixWithReading("エケ", "えちけっと"))
    }
}
