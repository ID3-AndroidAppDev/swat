package com.example.kotobadrop.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiConverterTest {

    private fun kana(romaji: String) = RomajiConverter.toKana(romaji)
    private fun prefix(buf: String, reading: String) = RomajiConverter.isPrefixOfReading(buf, reading)
    private fun equal(buf: String, reading: String) = RomajiConverter.isEqualToReading(buf, reading)
    private fun sharesPrefix(buf: String, reading: String) = RomajiConverter.sharesPrefixWithReading(buf, reading)

    // ── Gojūon (base syllables) ────────────────────────────────────────────

    @Test fun gojuon_a_row() {
        assertEquals("あいうえお", kana("aiueo"))
    }

    @Test fun gojuon_ka_row() {
        assertEquals("かきくけこ", kana("kakikukeko"))
    }

    @Test fun gojuon_sa_row() {
        assertEquals("さすせそ", kana("sasuseso"))
        assertEquals("し", kana("shi"))
    }

    @Test fun gojuon_ta_row() {
        // ta-chi-tsu-te-to (Hepburn)
        assertEquals("た", kana("ta"))
        assertEquals("ち", kana("chi"))
        assertEquals("つ", kana("tsu"))
        assertEquals("て", kana("te"))
        assertEquals("と", kana("to"))
    }

    @Test fun gojuon_na_row() {
        assertEquals("なにぬねの", kana("naninuneno"))
    }

    @Test fun gojuon_ha_row() {
        assertEquals("はひへほ", kana("hahiheho"))
        assertEquals("ふ", kana("fu"))
    }

    @Test fun gojuon_ba_row() {
        assertEquals("ばびぶべぼ", kana("babibubebo"))
    }

    @Test fun gojuon_pa_row() {
        assertEquals("ぱぴぷぺぽ", kana("papipupepo"))
    }

    @Test fun gojuon_ma_row() {
        assertEquals("まみむめも", kana("mamimumemo"))
    }

    @Test fun gojuon_ya_row() {
        assertEquals("やゆよ", kana("yayuyo"))
    }

    @Test fun gojuon_ra_row() {
        assertEquals("らりるれろ", kana("rarirurero"))
    }

    @Test fun gojuon_wa_row() {
        assertEquals("わを", kana("wawo"))
    }

    // ── Loanword extensions (katakana digraphs) ──────────────────────────

    @Test fun loanword_v_row() {
        assertEquals("ゔぁゔぃゔゔぇゔぉ", kana("vavivuvevo"))
    }

    @Test fun loanword_wi_we() {
        assertEquals("うぃうぇ", kana("wiwe"))
    }

    @Test fun loanword_who_for_uo() {
        // "wo" is already を — うぉ needs the alternate "who" spelling (see RomajiConverter).
        assertEquals("うぉっちんぐ", kana("whocchingu"))
    }

    @Test fun loanword_ti_di_tu_du_direct_spelling() {
        assertEquals("てぃでぃとぅどぅ", kana("thidhitwudwu"))
    }

    @Test fun loanword_ti_di_via_small_kana_prefix() {
        // "ti"/"di" alone are the Kunrei spellings of chi/ji — てぃ/でぃ need the small-kana
        // x/l prefix convention instead (te + xi), which already works via decomposition.
        assertEquals("てぃでぃ", kana("texideli"))
    }

    @Test fun loanword_full_word_violinist() {
        assertEquals("ゔぁいおりにすと", kana("vaiorinisuto"))
    }

    // ── Chōonpu (ー) expansion for matching — every stored reading is normalized to
    // spelled-out vowels (tools/preprocess/build_words_db.py), so "-" dash input must
    // converge onto the same form as double-vowel spelling to match it. ──────────

    @Test fun choonpu_dash_matches_double_vowel_reading() {
        // カード's stored reading is かあど (normalized from katakana's かーど at DB build).
        assertTrue(equal("ka-do", "かあど"))
        assertTrue(equal("kaado", "かあど"))
    }

    @Test fun choonpu_dash_preserves_live_display_as_literal() {
        // The live kana preview (raw toKana, no expansion) should still show ー as typed —
        // only the matching predicates expand it.
        assertEquals("かーど", kana("ka-do"))
    }

    @Test fun choonpu_dash_prefix_matches() {
        assertTrue(prefix("ka-", "かあど"))
    }

    @Test fun choonpu_dash_after_i_row_expands_to_i() {
        // ビール (beer): び + ー(i) + る -> びいる
        assertTrue(equal("bi-ru", "びいる"))
    }

    @Test fun gojuon_ga_row() {
        assertEquals("がぎぐげご", kana("gagigugego"))
    }

    @Test fun gojuon_za_row() {
        assertEquals("ざじずぜぞ", kana("zajizuzezo"))
    }

    @Test fun gojuon_da_row() {
        assertEquals("だでど", kana("dadedo"))
    }

    // ── Yōon digraphs ─────────────────────────────────────────────────────

    @Test fun yoon_kya_series() {
        assertEquals("きゃ", kana("kya"))
        assertEquals("きゅ", kana("kyu"))
        assertEquals("きょ", kana("kyo"))
    }

    @Test fun yoon_sha_series() {
        assertEquals("しゃ", kana("sha"))
        assertEquals("しゅ", kana("shu"))
        assertEquals("しょ", kana("sho"))
    }

    @Test fun yoon_cha_series() {
        assertEquals("ちゃ", kana("cha"))
        assertEquals("ちゅ", kana("chu"))
        assertEquals("ちょ", kana("cho"))
    }

    @Test fun yoon_ja_series() {
        assertEquals("じゃ", kana("ja"))
        assertEquals("じゅ", kana("ju"))
        assertEquals("じょ", kana("jo"))
    }

    @Test fun yoon_nya_series() {
        assertEquals("にゃ", kana("nya"))
        assertEquals("にゅ", kana("nyu"))
        assertEquals("にょ", kana("nyo"))
    }

    @Test fun yoon_hya_series() {
        assertEquals("ひゃ", kana("hya"))
        assertEquals("ひゅ", kana("hyu"))
        assertEquals("ひょ", kana("hyo"))
    }

    @Test fun yoon_mya_series() {
        assertEquals("みゃ", kana("mya"))
        assertEquals("みゅ", kana("myu"))
        assertEquals("みょ", kana("myo"))
    }

    @Test fun yoon_rya_series() {
        assertEquals("りゃ", kana("rya"))
        assertEquals("りゅ", kana("ryu"))
        assertEquals("りょ", kana("ryo"))
    }

    @Test fun yoon_gya_series() {
        assertEquals("ぎゃ", kana("gya"))
        assertEquals("ぎゅ", kana("gyu"))
        assertEquals("ぎょ", kana("gyo"))
    }

    @Test fun yoon_bya_series() {
        assertEquals("びゃ", kana("bya"))
        assertEquals("びゅ", kana("byu"))
        assertEquals("びょ", kana("byo"))
    }

    @Test fun yoon_pya_series() {
        assertEquals("ぴゃ", kana("pya"))
        assertEquals("ぴゅ", kana("pyu"))
        assertEquals("ぴょ", kana("pyo"))
    }

    // ── Kunrei / wāpuro variants ──────────────────────────────────────────

    @Test fun kunrei_si_equals_shi() {
        assertEquals("し", kana("si"))
        assertEquals(kana("shi"), kana("si"))
    }

    @Test fun kunrei_ti_equals_chi() {
        assertEquals("ち", kana("ti"))
        assertEquals(kana("chi"), kana("ti"))
    }

    @Test fun kunrei_tu_equals_tsu() {
        assertEquals("つ", kana("tu"))
        assertEquals(kana("tsu"), kana("tu"))
    }

    @Test fun kunrei_hu_equals_fu() {
        assertEquals("ふ", kana("hu"))
        assertEquals(kana("fu"), kana("hu"))
    }

    @Test fun kunrei_zi_equals_ji() {
        assertEquals("じ", kana("zi"))
        assertEquals(kana("ji"), kana("zi"))
    }

    @Test fun kunrei_sya_equals_sha() {
        assertEquals("しゃ", kana("sya"))
        assertEquals(kana("sha"), kana("sya"))
    }

    @Test fun kunrei_syu_equals_shu() {
        assertEquals(kana("shu"), kana("syu"))
    }

    @Test fun kunrei_syo_equals_sho() {
        assertEquals(kana("sho"), kana("syo"))
    }

    @Test fun kunrei_tya_equals_cha() {
        assertEquals("ちゃ", kana("tya"))
        assertEquals(kana("cha"), kana("tya"))
    }

    @Test fun kunrei_tyu_equals_chu() {
        assertEquals(kana("chu"), kana("tyu"))
    }

    @Test fun kunrei_tyo_equals_cho() {
        assertEquals(kana("cho"), kana("tyo"))
    }

    @Test fun kunrei_jya_equals_ja() {
        assertEquals("じゃ", kana("jya"))
        assertEquals(kana("ja"), kana("jya"))
    }

    @Test fun kunrei_zya_equals_ja() {
        assertEquals("じゃ", kana("zya"))
        assertEquals(kana("ja"), kana("zya"))
    }

    @Test fun kunrei_jyu_equals_ju() {
        assertEquals(kana("ju"), kana("jyu"))
    }

    @Test fun kunrei_zyu_equals_ju() {
        assertEquals(kana("ju"), kana("zyu"))
    }

    @Test fun kunrei_jyo_equals_jo() {
        assertEquals(kana("jo"), kana("jyo"))
    }

    @Test fun kunrei_zyo_equals_jo() {
        assertEquals(kana("jo"), kana("zyo"))
    }

    // ── Sokuon (っ) ───────────────────────────────────────────────────────

    @Test fun sokuon_kka() {
        assertEquals("っか", kana("kka"))
    }

    @Test fun sokuon_tte() {
        assertEquals("って", kana("tte"))
    }

    @Test fun sokuon_sshi() {
        // doubled s before sh → っし
        assertEquals("っし", kana("sshi"))
    }

    @Test fun sokuon_ssu() {
        assertEquals("っす", kana("ssu"))
    }

    @Test fun sokuon_ppa() {
        assertEquals("っぱ", kana("ppa"))
    }

    @Test fun sokuon_cchi() {
        // cc + hi → っち (doubled c, then chi)
        assertEquals("っち", kana("cchi"))
    }

    @Test fun sokuon_mma() {
        assertEquals("っま", kana("mma"))
    }

    @Test fun sokuon_kitte() {
        assertEquals("きって", kana("kitte"))
    }

    @Test fun sokuon_gakkou() {
        assertEquals("がっこう", kana("gakkou"))
    }

    @Test fun sokuon_zasshi() {
        assertEquals("ざっし", kana("zasshi"))
    }

    @Test fun sokuon_triple_consonant() {
        // kkka → っ + っ + か
        assertEquals("っっか", kana("kkka"))
    }

    // ── ん handling ───────────────────────────────────────────────────────

    @Test fun n_before_consonant() {
        assertEquals("さんぽ", kana("sanpo"))
        assertEquals("でんき", kana("denki"))
    }

    @Test fun n_before_n_advances_one_char() {
        // "nna" → n(before n → ん, advance 1) + na(→ な) = んな
        assertEquals("んな", kana("nna"))
    }

    @Test fun n_before_n_in_word() {
        // anna → あ + n(before n → ん) + na(→ な) = あんな
        assertEquals("あんな", kana("anna"))
    }

    @Test fun n_at_end() {
        assertEquals("さん", kana("san"))
        assertEquals("にほん", kana("nihon"))
    }

    @Test fun n_apostrophe_before_vowel() {
        // man'ichi → まんいち (apostrophe forces ん, then i + chi = いち)
        assertEquals("まんいち", kana("man'ichi"))
    }

    @Test fun n_apostrophe_before_y() {
        // hon'ya → ほんや (not ほにゃ)
        assertEquals("ほんや", kana("hon'ya"))
    }

    @Test fun n_before_vowel_forms_na_row() {
        assertEquals("なに", kana("nani"))
        assertEquals("ねこ", kana("neko"))
        assertEquals("のり", kana("nori"))
    }

    @Test fun n_before_y_forms_yoon() {
        assertEquals("にゃ", kana("nya"))
        assertEquals("にゅ", kana("nyu"))
        assertEquals("にょ", kana("nyo"))
    }

    @Test fun n_triple() {
        // "nnn": n(before n→ん) + n(before n→ん) + n(at end→ん) = んんん
        assertEquals("んんん", kana("nnn"))
    }

    @Test fun n_before_shinbun() {
        // しんぶん via n-before-consonant rule
        assertEquals("しんぶん", kana("shinbun"))
    }

    // ── Long vowels ───────────────────────────────────────────────────────

    @Test fun long_vowel_ou_stays_literal() {
        // spec: long vowels stay literal; "ou" → おう not a contracted sound
        assertEquals("おう", kana("ou"))
        assertEquals("とうきょう", kana("toukyou"))
    }

    @Test fun long_vowel_oo_stays_literal() {
        assertEquals("おお", kana("oo"))
        assertEquals("おおきい", kana("ookii"))
    }

    @Test fun long_vowel_uu_stays_literal() {
        assertEquals("うう", kana("uu"))
        assertEquals("くうき", kana("kuuki"))
    }

    @Test fun long_vowel_dash_to_chouon_mark() {
        assertEquals("らーめん", kana("ra-men"))
    }

    // ── Trailing partial syllable ─────────────────────────────────────────

    @Test fun partial_single_consonant() {
        assertEquals("k", kana("k"))
        assertEquals("s", kana("s"))
    }

    @Test fun partial_trailing_consonant_after_kana() {
        assertEquals("とうk", kana("touk"))
    }

    @Test fun partial_two_char_partial() {
        // "ky" needs a vowel to complete
        assertEquals("とうky", kana("touky"))
    }

    @Test fun partial_sh_incomplete() {
        assertEquals("sh", kana("sh"))
        assertEquals("さsh", kana("sash"))
    }

    @Test fun partial_ny_incomplete() {
        assertEquals("ny", kana("ny"))
    }

    @Test fun empty_input() {
        assertEquals("", kana(""))
    }

    // ── Full word round-trips ─────────────────────────────────────────────

    @Test fun word_taberu() {
        assertEquals("たべる", kana("taberu"))
    }

    @Test fun word_toukyou() {
        assertEquals("とうきょう", kana("toukyou"))
    }

    @Test fun word_chotto() {
        assertEquals("ちょっと", kana("chotto"))
    }

    @Test fun word_setsumei_hepburn() {
        assertEquals("せつめい", kana("setsumei"))
    }

    @Test fun word_setsumei_kunrei() {
        assertEquals("せつめい", kana("setumei"))
    }

    @Test fun word_kikkake() {
        assertEquals("きっかけ", kana("kikkake"))
    }

    @Test fun word_jinja() {
        assertEquals("じんじゃ", kana("jinja"))
    }

    @Test fun word_ryokou() {
        assertEquals("りょこう", kana("ryokou"))
    }

    @Test fun word_shashin() {
        assertEquals("しゃしん", kana("shashin"))
    }

    // ── isPrefixOfReading ─────────────────────────────────────────────────

    @Test fun prefix_empty_buffer_is_always_true() {
        assertTrue(prefix("", "たべる"))
        assertTrue(prefix("", ""))
    }

    @Test fun prefix_kana_prefix_matches() {
        assertTrue(prefix("ta", "たべる"))
        assertTrue(prefix("tabe", "たべる"))
        assertTrue(prefix("taberu", "たべる"))
    }

    @Test fun prefix_wrong_first_kana() {
        assertFalse(prefix("ki", "たべる"))
    }

    @Test fun prefix_shared_start() {
        // Both かき and かぶ start with か
        assertTrue(prefix("ka", "かき"))
        assertTrue(prefix("ka", "かぶ"))
        // き is not a prefix of かぶ
        assertFalse(prefix("ki", "かぶ"))
    }

    @Test fun prefix_trailing_partial_uses_only_completed_kana() {
        // "tab" → た + partial b; kana prefix = た; たべる starts with た
        assertTrue(prefix("tab", "たべる"))
    }

    @Test fun prefix_only_partial_consonant_matches_any_reading() {
        // No kana committed yet; kana prefix is ""; everything is a match
        assertTrue(prefix("t", "たべる"))
        assertTrue(prefix("t", "てくる"))
        assertTrue(prefix("k", "かき"))
    }

    @Test fun prefix_buffer_longer_than_reading() {
        assertFalse(prefix("taberuyo", "たべる"))
    }

    @Test fun prefix_sokuon_word() {
        assertTrue(prefix("ki", "きって"))
        assertTrue(prefix("kit", "きって"))
        assertTrue(prefix("kitte", "きって"))
    }

    @Test fun prefix_n_word() {
        assertTrue(prefix("ni", "にほん"))
        assertTrue(prefix("niho", "にほん"))
        assertTrue(prefix("nihon", "にほん"))
    }

    @Test fun prefix_wrong_kana_after_correct_start() {
        // し is not a prefix of する
        assertFalse(prefix("shi", "する"))
        assertTrue(prefix("su", "する"))
    }

    // ── sharesPrefixWithReading ──────────────────────────────────────────

    @Test fun sharesPrefix_empty_buffer_is_always_false() {
        // Unlike isPrefixOfReading, an empty buffer shares nothing — clearing it on a
        // miss would be a no-op anyway, so this just needs to not crash.
        assertFalse(sharesPrefix("", "たべる"))
    }

    @Test fun sharesPrefix_true_whenever_isPrefixOfReading_is_true() {
        // A strict prefix match is always also a "shares a prefix" match.
        assertTrue(sharesPrefix("tabe", "たべる"))
        assertTrue(sharesPrefix("ki", "きって"))
    }

    @Test fun sharesPrefix_survives_a_typo_that_derails_the_buffer() {
        // "taberi" fully converts to valid kana たべり — a real typo (り for る), not
        // trailing garbage, so isPrefixOfReading correctly rejects it. But the player
        // was clearly typing this word: they share the leading たべ.
        assertFalse(prefix("taberi", "たべる"))
        assertTrue(sharesPrefix("taberi", "たべる"))
    }

    @Test fun sharesPrefix_false_for_a_completely_different_word() {
        assertFalse(sharesPrefix("ki", "たべる"))
    }

    @Test fun sharesPrefix_false_once_the_typed_kana_actually_diverges() {
        // "shi" fully converts to し — it doesn't share even a first character with する.
        assertFalse(sharesPrefix("shi", "する"))
    }

    @Test fun sharesPrefix_dual_path_romaji_typo() {
        // "paachi" (real kana divergence, ち for てぃ partway through) is rejected by
        // isPrefixOfReading (existing coverage: canonicalRomaji tests below) but still
        // shares "ぱあ" / "paa" with ぱあてぃい on both the kana and romaji paths.
        assertFalse(prefix("paachi", "ぱあてぃい"))
        assertTrue(sharesPrefix("paachi", "ぱあてぃい"))
    }

    // ── isEqualToReading ──────────────────────────────────────────────────

    @Test fun equal_taberu() {
        assertTrue(equal("taberu", "たべる"))
    }

    @Test fun equal_nihon() {
        assertTrue(equal("nihon", "にほん"))
    }

    @Test fun equal_chotto() {
        assertTrue(equal("chotto", "ちょっと"))
    }

    @Test fun equal_toukyou() {
        assertTrue(equal("toukyou", "とうきょう"))
    }

    @Test fun equal_kunrei_setumei() {
        assertTrue(equal("setumei", "せつめい"))
        assertTrue(equal("setsumei", "せつめい"))
    }

    @Test fun equal_san_n_at_end() {
        assertTrue(equal("san", "さん"))
    }

    @Test fun equal_gakkou_sokuon() {
        assertTrue(equal("gakkou", "がっこう"))
    }

    @Test fun equal_jinja() {
        assertTrue(equal("jinja", "じんじゃ"))
    }

    @Test fun equal_ryokou() {
        assertTrue(equal("ryokou", "りょこう"))
    }

    @Test fun equal_rejects_partial_buffer() {
        assertFalse(equal("tab", "たべる"))
        assertFalse(equal("tabe", "たべる"))
    }

    @Test fun equal_rejects_trailing_romaji() {
        assertFalse(equal("taberuk", "たべる"))
    }

    @Test fun equal_rejects_wrong_word() {
        assertFalse(equal("taberu", "たべ"))
        assertFalse(equal("neko", "いぬ"))
    }

    @Test fun equal_empty_both_empty() {
        assertTrue(equal("", ""))
    }

    @Test fun equal_empty_buffer_nonempty_reading() {
        assertFalse(equal("", "あ"))
    }

    // ── toRomaji (kana → romaji, display only) ─────────────────────────────

    private fun romaji(reading: String) = RomajiConverter.toRomaji(reading)

    @Test fun romaji_gojuon() {
        assertEquals("aiueo", romaji("あいうえお"))
        assertEquals("taberu", romaji("たべる"))
    }

    @Test fun romaji_yoon() {
        assertEquals("kyoku", romaji("きょく"))
        assertEquals("ryokou", romaji("りょこう"))
    }

    @Test fun romaji_sokuon_doubles_consonant() {
        assertEquals("gakkou", romaji("がっこう"))
    }

    @Test fun romaji_sokuon_before_ch_is_tch() {
        assertEquals("matcha", romaji("まっちゃ"))
    }

    @Test fun romaji_n_before_consonant() {
        assertEquals("sanzen", romaji("さんぜん"))
        assertEquals("kaban", romaji("かばん"))
    }

    @Test fun romaji_n_before_bmp_is_m() {
        assertEquals("sampo", romaji("さんぽ"))
        assertEquals("shimbun", romaji("しんぶん"))
    }

    @Test fun romaji_n_before_vowel_or_y_gets_apostrophe() {
        assertEquals("ken'i", romaji("けんい"))
        assertEquals("kon'ya", romaji("こんや"))
    }

    @Test fun romaji_loanword_digraphs() {
        assertEquals("weito", romaji("うぇいと"))
        assertEquals("sukejuuru", romaji("すけじゅうる"))
    }

    @Test fun romaji_uo_is_who() {
        // っ before ch-row is "t" (matcha, not maccha) per the existing sokuon rule above.
        assertEquals("whotchingu", romaji("うぉっちんぐ"))
    }

    // ── Dual-path matching: raw romaji vs canonical Hepburn (パーティー fix) ─────────

    @Test fun canonicalRomaji_paatii_matchesTeiWord() {
        // The dictionary displays ぱあてぃい as "paatii" (てぃ -> "ti"), but typed "ti" is
        // Kunrei ち — the kana path alone could never match the app's own displayed
        // spelling. The romaji path accepts it directly.
        assertTrue(RomajiConverter.isEqualToReading("paatii", "ぱあてぃい"))
        assertTrue(RomajiConverter.isPrefixOfReading("paati", "ぱあてぃい"))
        assertTrue(RomajiConverter.isPrefixOfReading("paatii", "ぱあてぃい"))
    }

    @Test fun canonicalRomaji_thiSpellingStillWorks() {
        assertTrue(RomajiConverter.isEqualToReading("paathii", "ぱあてぃい"))
    }

    @Test fun canonicalRomaji_matchaAndSampo() {
        // Standard Hepburn irregulars now match: っ before ch = "t", ん = "m" before b/m/p.
        assertTrue(RomajiConverter.isEqualToReading("matcha", "まっちゃ"))
        assertTrue(RomajiConverter.isEqualToReading("sampo", "さんぽ"))
        // The wāpuro spellings keep working through the kana path.
        assertTrue(RomajiConverter.isEqualToReading("maccha", "まっちゃ"))
        assertTrue(RomajiConverter.isEqualToReading("sanpo", "さんぽ"))
    }

    @Test fun canonicalRomaji_longVowelStrictnessSurvives() {
        // §4: とうきょう requires "toukyou" — canonical romaji spells vowels out, so the
        // romaji path must not open a "tokyo" loophole.
        assertFalse(RomajiConverter.isEqualToReading("tokyo", "とうきょう"))
        // ("toky" IS still a potential prefix — と matches and "ky" is a pending partial
        // syllable, pre-existing lenient behavior; the completed "tokyo" must be dead.)
        assertFalse(RomajiConverter.isPrefixOfReading("tokyo", "とうきょう"))
        assertTrue(RomajiConverter.isEqualToReading("toukyou", "とうきょう"))
    }

    @Test fun canonicalRomaji_nonMatchStaysDead() {
        assertFalse(RomajiConverter.isPrefixOfReading("paachi", "ぱあてぃい"))
    }
}
