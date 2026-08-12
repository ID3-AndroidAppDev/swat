package com.example.kotobadrop.input

/**
 * Converts romaji input to hiragana using longest-match lookup.
 * Supports both Hepburn (shi, chi, tsu, fu, ji) and Kunrei/wāpuro (si, ti, tu, hu, zi) variants.
 *
 * n rules:
 *   - "n" before a consonant (not n/y) → ん
 *   - "n" before "n" → ん (second n left for next syllable)
 *   - "n'" → ん
 *   - "n" at end → ん
 *   - "n" before vowel or y → handled by table (na→な, nya→にゃ, etc.)
 *
 * Sokuon: doubled consonant → っ + remaining (kk→っk, ss→っs, etc.)
 * Trailing partial syllable: passed through as-is (toukyo → とうきょ with trailing o consumed).
 */
object RomajiConverter {

    private val VOWELS = setOf('a', 'i', 'u', 'e', 'o')

    // Vowel each hiragana character ends on, as the hiragana vowel character itself (not
    // the Latin letter) — mirrors tools/preprocess/build_words_db.py's _HIRAGANA_VOWEL +
    // _VOWEL_TO_HIRAGANA combined into one step; used by expandChoonpu() below.
    private val VOWEL_OF_KANA: Map<Char, Char> = buildMap {
        val rows = listOf(
            "あいうえお", "かきくけこ", "がぎぐげご", "さしすせそ", "ざじずぜぞ",
            "たちつてと", "だぢづでど", "なにぬねの", "はひふへほ", "ばびぶべぼ",
            "ぱぴぷぺぽ", "まみむめも", "らりるれろ",
        )
        for (row in rows) {
            for (i in row.indices) put(row[i], "あいうえお"[i])
        }
        put('や', 'あ'); put('ゆ', 'う'); put('よ', 'お'); put('わ', 'あ'); put('を', 'お')
        put('ゃ', 'あ'); put('ゅ', 'う'); put('ょ', 'お')
        put('ぁ', 'あ'); put('ぃ', 'い'); put('ぅ', 'う'); put('ぇ', 'え'); put('ぉ', 'お')
        put('ゔ', 'う')
    }

    private val TABLE: Map<String, String> = buildMap {
        // Vowels
        put("a", "あ"); put("i", "い"); put("u", "う"); put("e", "え"); put("o", "お")

        // K
        put("ka", "か"); put("ki", "き"); put("ku", "く"); put("ke", "け"); put("ko", "こ")
        put("kya", "きゃ"); put("kyu", "きゅ"); put("kye", "きぇ"); put("kyo", "きょ")

        // G
        put("ga", "が"); put("gi", "ぎ"); put("gu", "ぐ"); put("ge", "げ"); put("go", "ご")
        put("gya", "ぎゃ"); put("gyu", "ぎゅ"); put("gye", "ぎぇ"); put("gyo", "ぎょ")

        // S — Hepburn: sh*, Kunrei: sy*
        put("sa", "さ"); put("si", "し"); put("shi", "し"); put("su", "す"); put("se", "せ"); put("so", "そ")
        put("sha", "しゃ"); put("shi", "し"); put("shu", "しゅ"); put("she", "しぇ"); put("sho", "しょ")
        put("sya", "しゃ"); put("syu", "しゅ"); put("sye", "しぇ"); put("syo", "しょ")

        // Z — Hepburn: j*, Kunrei: zy*
        put("za", "ざ"); put("zi", "じ"); put("ji", "じ"); put("zu", "ず"); put("ze", "ぜ"); put("zo", "ぞ")
        put("ja", "じゃ"); put("ju", "じゅ"); put("je", "じぇ"); put("jo", "じょ")
        put("jya", "じゃ"); put("jyu", "じゅ"); put("jye", "じぇ"); put("jyo", "じょ")
        put("zya", "じゃ"); put("zyu", "じゅ"); put("zye", "じぇ"); put("zyo", "じょ")

        // T — Hepburn: ch*/ts*, Kunrei: ty*/tu
        put("ta", "た"); put("ti", "ち"); put("chi", "ち"); put("tu", "つ"); put("tsu", "つ"); put("te", "て"); put("to", "と")
        put("cha", "ちゃ"); put("chu", "ちゅ"); put("che", "ちぇ"); put("cho", "ちょ")
        put("tya", "ちゃ"); put("tyu", "ちゅ"); put("tye", "ちぇ"); put("tyo", "ちょ")
        put("tsa", "つぁ"); put("tsi", "つぃ"); put("tse", "つぇ"); put("tso", "つぉ")

        // D
        put("da", "だ"); put("di", "ぢ"); put("du", "づ"); put("de", "で"); put("do", "ど")
        put("dya", "ぢゃ"); put("dyu", "ぢゅ"); put("dye", "ぢぇ"); put("dyo", "ぢょ")

        // N row (n-kana; standalone ん is handled in algorithm)
        put("na", "な"); put("ni", "に"); put("nu", "ぬ"); put("ne", "ね"); put("no", "の")
        put("nya", "にゃ"); put("nyu", "にゅ"); put("nye", "にぇ"); put("nyo", "にょ")

        // H / F — Hepburn: fu, Kunrei: hu
        put("ha", "は"); put("hi", "ひ"); put("fu", "ふ"); put("hu", "ふ"); put("he", "へ"); put("ho", "ほ")
        put("hya", "ひゃ"); put("hyu", "ひゅ"); put("hye", "ひぇ"); put("hyo", "ひょ")
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")

        // B
        put("ba", "ば"); put("bi", "び"); put("bu", "ぶ"); put("be", "べ"); put("bo", "ぼ")
        put("bya", "びゃ"); put("byu", "びゅ"); put("bye", "びぇ"); put("byo", "びょ")

        // P
        put("pa", "ぱ"); put("pi", "ぴ"); put("pu", "ぷ"); put("pe", "ぺ"); put("po", "ぽ")
        put("pya", "ぴゃ"); put("pyu", "ぴゅ"); put("pye", "ぴぇ"); put("pyo", "ぴょ")

        // M
        put("ma", "ま"); put("mi", "み"); put("mu", "む"); put("me", "め"); put("mo", "も")
        put("mya", "みゃ"); put("myu", "みゅ"); put("mye", "みぇ"); put("myo", "みょ")

        // Y
        put("ya", "や"); put("yu", "ゆ"); put("yo", "よ")

        // R
        put("ra", "ら"); put("ri", "り"); put("ru", "る"); put("re", "れ"); put("ro", "ろ")
        put("rya", "りゃ"); put("ryu", "りゅ"); put("rye", "りぇ"); put("ryo", "りょ")

        // W — "wo" is already claimed by を (a distinct kana, not this digraph row), so
        // うぉ needs an alternate spelling; "who" mirrors the thi/dhi/twu/dwu pattern below
        // (an available letter-combo standing in for a taken one), and is the spelling most
        // wāpuro IMEs already use for this exact reason.
        put("wa", "わ"); put("wi", "うぃ"); put("we", "うぇ"); put("wo", "を"); put("who", "うぉ")

        // V — loanword v-sounds (ヴァイオリン etc.); also typeable as "vexi"-style via
        // the small-kana x/l prefix below, but these direct spellings are the ones
        // most typists actually reach for.
        put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")

        // Extended katakana digraphs (loanwords): てぃ/でぃ/とぅ/どぅ can't use "ti"/"di"/"tu"/"du"
        // — those are already Kunrei chi/ji/tsu/zu. "texi"/"teli" (small-kana prefix, below)
        // already decompose to these too; "thi"/"dhi"/"twu"/"dwu" are the more commonly
        // expected direct spellings.
        put("thi", "てぃ"); put("dhi", "でぃ"); put("twu", "とぅ"); put("dwu", "どぅ")

        // Small kana via x/l prefix (wāpuro)
        put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
        put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
        put("xtu", "っ"); put("xtsu", "っ")
        put("la", "ぁ"); put("li", "ぃ"); put("lu", "ぅ"); put("le", "ぇ"); put("lo", "ぉ")
        put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ")
        put("ltu", "っ"); put("ltsu", "っ")

        // Long vowel mark
        put("-", "ー")
    }

    // Kana → romaji, for display only (dictionary screen). Reading strings in words.db are
    // always pure hiragana — build_words_db.py's normalize_reading converts katakana to
    // hiragana and expands ー before insertion — so this only needs hiragana input, unlike
    // toKana()'s job of accepting many spellings for the same kana, this always emits one
    // canonical Hepburn spelling (the one most learners expect to read).
    private val KANA_TO_ROMAJI: Map<String, String> = buildMap {
        put("あ", "a"); put("い", "i"); put("う", "u"); put("え", "e"); put("お", "o")
        put("か", "ka"); put("き", "ki"); put("く", "ku"); put("け", "ke"); put("こ", "ko")
        put("さ", "sa"); put("し", "shi"); put("す", "su"); put("せ", "se"); put("そ", "so")
        put("た", "ta"); put("ち", "chi"); put("つ", "tsu"); put("て", "te"); put("と", "to")
        put("な", "na"); put("に", "ni"); put("ぬ", "nu"); put("ね", "ne"); put("の", "no")
        put("は", "ha"); put("ひ", "hi"); put("ふ", "fu"); put("へ", "he"); put("ほ", "ho")
        put("ま", "ma"); put("み", "mi"); put("む", "mu"); put("め", "me"); put("も", "mo")
        put("や", "ya"); put("ゆ", "yu"); put("よ", "yo")
        put("ら", "ra"); put("り", "ri"); put("る", "ru"); put("れ", "re"); put("ろ", "ro")
        put("わ", "wa"); put("を", "o")

        put("が", "ga"); put("ぎ", "gi"); put("ぐ", "gu"); put("げ", "ge"); put("ご", "go")
        put("ざ", "za"); put("じ", "ji"); put("ず", "zu"); put("ぜ", "ze"); put("ぞ", "zo")
        put("だ", "da"); put("ぢ", "ji"); put("づ", "zu"); put("で", "de"); put("ど", "do")
        put("ば", "ba"); put("び", "bi"); put("ぶ", "bu"); put("べ", "be"); put("ぼ", "bo")
        put("ぱ", "pa"); put("ぴ", "pi"); put("ぷ", "pu"); put("ぺ", "pe"); put("ぽ", "po")

        put("きゃ", "kya"); put("きゅ", "kyu"); put("きょ", "kyo")
        put("ぎゃ", "gya"); put("ぎゅ", "gyu"); put("ぎょ", "gyo")
        put("しゃ", "sha"); put("しゅ", "shu"); put("しょ", "sho")
        put("じゃ", "ja"); put("じゅ", "ju"); put("じょ", "jo")
        put("ちゃ", "cha"); put("ちゅ", "chu"); put("ちょ", "cho")
        put("ぢゃ", "ja"); put("ぢゅ", "ju"); put("ぢょ", "jo")
        put("にゃ", "nya"); put("にゅ", "nyu"); put("にょ", "nyo")
        put("ひゃ", "hya"); put("ひゅ", "hyu"); put("ひょ", "hyo")
        put("びゃ", "bya"); put("びゅ", "byu"); put("びょ", "byo")
        put("ぴゃ", "pya"); put("ぴゅ", "pyu"); put("ぴょ", "pyo")
        put("みゃ", "mya"); put("みゅ", "myu"); put("みょ", "myo")
        put("りゃ", "rya"); put("りゅ", "ryu"); put("りょ", "ryo")

        // Extended loanword digraphs — post-normalization these are hiragana too (e.g. ふぁ).
        put("うぃ", "wi"); put("うぇ", "we"); put("うぉ", "who")
        put("ゔぁ", "va"); put("ゔぃ", "vi"); put("ゔ", "vu"); put("ゔぇ", "ve"); put("ゔぉ", "vo")
        put("しぇ", "she"); put("じぇ", "je"); put("ちぇ", "che")
        put("つぁ", "tsa"); put("つぃ", "tsi"); put("つぇ", "tse"); put("つぉ", "tso")
        put("てぃ", "ti"); put("でぃ", "di"); put("とぅ", "tu"); put("どぅ", "du")
        put("ふぁ", "fa"); put("ふぃ", "fi"); put("ふぇ", "fe"); put("ふぉ", "fo")
    }

    /**
     * Converts a romaji string to hiragana best-effort.
     * Fully-matched syllables become kana; a trailing partial syllable (e.g. the 'k' in "touk")
     * is appended as-is so the UI can show the in-progress input.
     */
    fun toKana(input: String): String {
        val s = input.lowercase()
        val result = StringBuilder()
        var i = 0

        while (i < s.length) {
            val c = s[i]

            // Try longest match (4..2 chars) via table
            val maxLen = minOf(4, s.length - i)
            var matched = false
            for (len in maxLen downTo 2) {
                val kana = TABLE[s.substring(i, i + len)]
                if (kana != null) {
                    result.append(kana)
                    i += len
                    matched = true
                    break
                }
            }
            if (matched) continue

            // Special ん logic (only fires when no 2+ char match found)
            if (c == 'n') {
                val next = s.getOrNull(i + 1)
                when {
                    // explicit n' → ん
                    next == '\'' -> { result.append('ん'); i += 2; continue }
                    // n before another n → ん (leave second n to combine with following vowel)
                    next == 'n' -> { result.append('ん'); i += 1; continue }
                    // n at end or before consonant (not y, not vowel) → ん
                    next == null || (next !in VOWELS && next != 'y') -> {
                        result.append('ん'); i += 1; continue
                    }
                    // n before vowel or y: fall through — single-char lookup or partial
                }
            }

            // Single-char table lookup (vowels)
            val kana = TABLE[c.toString()]
            if (kana != null) {
                result.append(kana)
                i++
                continue
            }

            // Sokuon: doubled consonant → っ (consume first, leave second for next iter)
            if (i + 1 < s.length && s[i + 1] == c && c !in VOWELS && c != 'n') {
                result.append('っ')
                i++
                continue
            }

            // Trailing partial: pass through as-is
            result.append(c)
            i++
        }

        return result.toString()
    }

    /**
     * Returns true if the kana converted from [buffer] is a prefix of [reading].
     * Trailing partial romaji (not yet a full syllable) is ignored for prefix comparison.
     *
     * Matching is dual-path: alongside the kana comparison, the raw buffer is also
     * accepted when it string-matches the reading's canonical Hepburn romaji
     * ([toRomaji]). The kana path alone rejected spellings the app itself displays in
     * the dictionary — てぃ words like ぱあてぃい show as "paatii", but typed "ti" is
     * Kunrei ち, so the canonical spelling could never match ("thi" was required). The
     * romaji path also admits standard Hepburn "matcha" (っ before ch = t) and "sampo"
     * (ん = m before b/m/p). Long-vowel strictness survives: canonical romaji spells
     * vowels out ("toukyou"), so "tokyo" still fails per §4.
     */
    fun isPrefixOfReading(buffer: String, reading: String): Boolean {
        if (buffer.isEmpty()) return true
        val kana = expandChoonpu(toKana(buffer))
        val kanaOnly = kanaPrefix(kana)
        // Empty kana prefix (only partial input typed) — allow as potential prefix
        return reading.startsWith(kanaOnly) || toRomaji(reading).startsWith(buffer.lowercase())
    }

    /**
     * Looser than [isPrefixOfReading]: true if [buffer]'s kana and [reading] merely
     * share a non-empty leading run, even once the buffer has diverged into dead input
     * (a typo partway through a word). Used to decide whether a miss should sweep away
     * the player's buffer — "close enough that the miss was probably this word," not
     * "this buffer could still go on to clear this word."
     *
     * Kana-only, deliberately not dual-path like [isPrefixOfReading]: comparing raw
     * romaji letter-by-letter is a poor similarity signal on its own (many kana share a
     * first Latin letter — "shi" and toRomaji("する") = "suru" both start with "s" despite
     * し and す being unrelated sounds), so it would produce false positives that the kana
     * comparison alone doesn't have.
     */
    fun sharesPrefixWithReading(buffer: String, reading: String): Boolean {
        if (buffer.isEmpty()) return false
        val kanaOnly = kanaPrefix(expandChoonpu(toKana(buffer)))
        return kanaOnly.commonPrefixWith(reading).isNotEmpty()
    }

    /**
     * Returns true if [buffer] fully converts to exactly [reading] with no trailing
     * partial — or string-equals the reading's canonical Hepburn romaji (see
     * [isPrefixOfReading] for why matching is dual-path).
     */
    fun isEqualToReading(buffer: String, reading: String): Boolean {
        if (buffer.isEmpty()) return reading.isEmpty()
        val kana = expandChoonpu(toKana(buffer))
        return (kana == reading && kana.all { isKana(it) }) || buffer.lowercase() == toRomaji(reading)
    }

    /**
     * Converts a hiragana [reading] to Hepburn romaji, for display (e.g. the dictionary
     * screen) — not the inverse of toKana(), which accepts many spellings per kana; this
     * always emits one canonical spelling. Handles っ (doubles the next consonant, "tch"
     * before ち-row per standard Hepburn) and ん (→ "m" before b/m/p, apostrophe before a
     * vowel or y to avoid misreading as the な-row, e.g. けんい → "ken'i").
     */
    fun toRomaji(reading: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < reading.length) {
            val c = reading[i]

            if (c == 'っ') {
                val nextRomaji = romajiOfMoraAt(reading, i + 1)
                if (nextRomaji != null) {
                    result.append(if (nextRomaji.startsWith("ch")) "t" else nextRomaji.first().toString())
                    i++
                    continue
                }
            }

            if (c == 'ん') {
                val nextFirst = romajiOfMoraAt(reading, i + 1)?.firstOrNull()
                result.append(if (nextFirst in setOf('b', 'm', 'p')) "m" else "n")
                if (nextFirst != null && (nextFirst in VOWELS || nextFirst == 'y')) result.append('\'')
                i++
                continue
            }

            if (i + 1 < reading.length) {
                val romaji = KANA_TO_ROMAJI[reading.substring(i, i + 2)]
                if (romaji != null) {
                    result.append(romaji)
                    i += 2
                    continue
                }
            }

            val romaji = KANA_TO_ROMAJI[c.toString()]
            if (romaji != null) {
                result.append(romaji)
                i++
                continue
            }

            // Unmapped char (shouldn't happen for a real word reading) — pass through as-is.
            result.append(c)
            i++
        }
        return result.toString()
    }

    /** Romaji of the mora starting at [index] (2-char digraph checked first), or null. */
    private fun romajiOfMoraAt(reading: String, index: Int): String? {
        if (index >= reading.length) return null
        if (index + 1 < reading.length) {
            KANA_TO_ROMAJI[reading.substring(index, index + 2)]?.let { return it }
        }
        return KANA_TO_ROMAJI[reading[index].toString()]
    }

    /**
     * Expands ー (chōonpu) into the vowel it extends — "-" is a valid literal mapping in
     * TABLE (a wāpuro long-vowel shortcut, e.g. "ka-do" -> かーど), but every stored word
     * reading is normalized to spelled-out vowels at DB-build time (tools/preprocess/
     * build_words_db.py's normalize_reading, since JMdict katakana readings use ー too),
     * so "ka-do" and "kaado" must converge to the same かあど to both match. Only affects
     * matching — the live kana preview in the UI still shows literal ー as typed, which is
     * the more natural thing to see.
     *
     * internal, not private: pure kana-normalization (not romaji-specific), also reused by
     * ImeMatcher (step 10) for the same reason — a hiragana IME can commit literal ー too.
     */
    internal fun expandChoonpu(kana: String): String {
        if ('ー' !in kana) return kana
        val sb = StringBuilder(kana.length)
        for (c in kana) {
            if (c == 'ー' && sb.isNotEmpty()) {
                val vowel = VOWEL_OF_KANA[sb.last()]
                if (vowel != null) {
                    sb.append(vowel)
                    continue
                }
            }
            sb.append(c)
        }
        return sb.toString()
    }

    /** Extracts the leading kana characters before any trailing ASCII partial syllable. */
    private fun kanaPrefix(converted: String): String {
        val sb = StringBuilder()
        for (c in converted) {
            if (isKana(c)) sb.append(c) else break
        }
        return sb.toString()
    }

    private fun isKana(c: Char): Boolean =
        c.code in 0x3040..0x30FF || c == 'ー'
}
