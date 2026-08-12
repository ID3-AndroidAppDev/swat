package com.example.kotobadrop.game

import org.junit.Assert.assertEquals
import org.junit.Test

class FuriganaForTest {

    @Test fun trims_shared_trailing_okurigana() {
        assertEquals("た", furiganaFor("食べる", "たべる"))
    }

    @Test fun trims_longer_okurigana() {
        assertEquals("あたら", furiganaFor("新しい", "あたらしい"))
    }

    @Test fun pureKanji_noOkurigana_keepsWholeReading() {
        assertEquals("とうきょう", furiganaFor("東京", "とうきょう"))
    }

    @Test fun leadingHiragana_notStrippedFromEnd_keepsWholeReading() {
        // お茶/おちゃ: the mismatched hiragana is a *prefix*, not a shared suffix —
        // trailing-suffix trimming can't isolate the kanji-only reading here, so it
        // falls back to showing the whole reading (still correct, just not shortened).
        assertEquals("おちゃ", furiganaFor("お茶", "おちゃ"))
    }

    @Test fun identicalSurfaceAndReading_yieldsEmpty() {
        // Never actually rendered (kana-only words skip furigana entirely), but the
        // function itself should degrade sensibly rather than throw.
        assertEquals("", furiganaFor("これ", "これ"))
    }
}
