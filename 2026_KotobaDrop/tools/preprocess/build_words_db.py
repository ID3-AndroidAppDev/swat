#!/usr/bin/env python3
"""Build the prepopulated words.db asset from JMdict + KANJIDIC2.

Usage:
    python3 build_words_db.py --download   # fetch raw dumps into ./data (network)
    python3 build_words_db.py              # parse ./data and emit words.db (offline)

See README.md for details on sourcing, licensing, and the output schema.
"""

import argparse
import gzip
import re
import sqlite3
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA_DIR = HERE / "data"
JMDICT_GZ_URL = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"
KANJIDIC2_GZ_URL = "http://www.edrdg.org/kanjidic/kanjidic2.xml.gz"
JMDICT_XML = DATA_DIR / "JMdict_e.xml"
KANJIDIC2_XML = DATA_DIR / "kanjidic2.xml"
OUTPUT_DB = HERE.parent.parent / "app" / "src" / "main" / "assets" / "words.db"

NF_RE = re.compile(r"^nf(\d\d)$")
KANJI_RE = re.compile(r"[一-鿿㐀-䶿]")

# JMdict stores katakana loanword readings in katakana with the chōonpu long-vowel
# mark (ー), e.g. ボール, not ぼおる — but RomajiConverter.toKana() (Kotlin, input/)
# only ever produces hiragana with vowels spelled out literally, per CLAUDE.md §4.
# Without normalizing readings to that same form here, katakana words can never be
# typed/cleared: the stored reading and the player's converted input never match as
# strings, even when phonetically identical. Every reading gets normalized to pure
# hiragana with ー expanded to its actual vowel, so `reading` is always in the exact
# form toKana() produces — `surface` is untouched, so display still shows real
# orthography (katakana loanwords still render as katakana).
_KATAKANA_TO_HIRAGANA_OFFSET = ord("ぁ") - ord("ァ")

# Vowel each hiragana character ends on — used to expand ー (chōonpu) into the
# vowel it's extending, e.g. ボール -> ぼ + ー(o) + る -> ぼおる. Five-character
# rows are the regular gojūon a-i-u-e-o pattern; the rest are spelled out since
# they don't follow that pattern (や/ゆ/よ skip i/e, わ has no full row).
_GOJUON_VOWEL_ROWS = [
    "あいうえお", "かきくけこ", "がぎぐげご", "さしすせそ", "ざじずぜぞ",
    "たちつてと", "だぢづでど", "なにぬねの", "はひふへほ", "ばびぶべぼ",
    "ぱぴぷぺぽ", "まみむめも", "らりるれろ",
]
_HIRAGANA_VOWEL = {"や": "a", "ゆ": "u", "よ": "o", "わ": "a", "を": "o"}
for _row in _GOJUON_VOWEL_ROWS:
    for _ch, _vowel in zip(_row, "aiueo"):
        _HIRAGANA_VOWEL[_ch] = _vowel
_HIRAGANA_VOWEL.update({
    "ゃ": "a", "ゅ": "u", "ょ": "o", "ぁ": "a", "ぃ": "i", "ぅ": "u", "ぇ": "e", "ぉ": "o",
    "ゔ": "u",  # ヴ (v-sound loanwords, e.g. ヴァイオリン) shifts to this rare hiragana codepoint
})
_VOWEL_TO_HIRAGANA = {"a": "あ", "i": "い", "u": "う", "e": "え", "o": "お"}


def normalize_reading(reading):
    """Katakana -> hiragana, ー (chōonpu) -> the vowel it extends, ・ (nakaguro) dropped.
    Makes `reading` match exactly what RomajiConverter.toKana() produces for typed
    romaji, kana-only or not — see the comment above _KATAKANA_TO_HIRAGANA_OFFSET for
    why this matters. ・ shows up in JMdict readings for spelled-out-letter acronym
    words (e.g. ジー・ユー・アイ for "GUI") purely as a visual separator between
    letters, not a phoneme — it has no romaji mapping and can't be typed, so every
    such word was completely unclearable before this. Dropping it (rather than
    keeping the entry unclearable, or excluding the word) makes it type as one
    unbroken reading, e.g. じいゆうあい, matching how a player would actually type it."""
    result = []
    for ch in reading:
        if ch == "・":
            continue
        code = ord(ch)
        if 0x30A1 <= code <= 0x30F6:  # katakana ァ..ヶ -> hiragana ぁ..ゖ
            ch = chr(code + _KATAKANA_TO_HIRAGANA_OFFSET)
        elif ch == "ー" and result:
            prev_vowel = _HIRAGANA_VOWEL.get(result[-1])
            if prev_vowel:
                ch = _VOWEL_TO_HIRAGANA[prev_vowel]
        result.append(ch)
    return "".join(result)


# Reading length beyond which a word is dropped entirely, regardless of tier — past
# this point words stop being reasonable falling-word targets at all (the two entries
# this actually cuts, at time of writing: 朝鮮民主主義人民共和国/22-kana "Democratic
# People's Republic of Korea" and 東京証券取引所/16-kana "Tokyo Stock Exchange" —
# institutional/political proper-noun compounds, not vocabulary worth typing under a
# falling timer). Below this cutoff, length instead only raises the *minimum* tier
# (see LENGTH_TIER_FLOOR) so long-but-legitimate words are pushed to harder
# difficulties rather than removed outright.
MAX_READING_LENGTH = 13

# (max reading length, minimum tier) pairs, checked in order — the first pair whose
# length the reading is at-or-under wins. Keeps long words out of the beginner tiers
# even when their hardest kanji (or lack of kanji, for kana-only loanwords) would
# otherwise put them at tier 0/1 — e.g. ディーブイディープレーヤー ("DVD player",
# 13 kana, kana-only) would otherwise be a tier-0 word a first-time player could hit.
# Thresholds chosen from the actual length histogram: ~96% of words are <=6 kana and
# completely unaffected; the bands above only reclassify the small long-reading tail.
LENGTH_TIER_FLOOR = [(6, 0), (8, 1), (10, 2), (13, 3)]


def length_tier_floor(reading_len):
    for max_len, floor in LENGTH_TIER_FLOOR:
        if reading_len <= max_len:
            return floor
    return 4

# nf tags are frequency-ranked in buckets of 500 (nf01 = most frequent 500, ...).
# Cutting off at nf12 (top ~6,000 by frequency) is what keeps the final word count
# in the "several thousand" range the spec targets — the editorial news/ichi/spec/gai
# tags alone cover ~21,000 entries, an order of magnitude too many. Raise this if the
# game's word pool ends up feeling too small.
COMMON_NF_CUTOFF = 12

# The nf frequency lists are derived from newspaper text, which under-represents
# kana-only function words (pronouns, adverbs like これ/それ/とても/もう) relative to
# kanji nouns — nearly all of them lack an nf tag entirely. Without this, tier 0
# (kana-only, the beginner floor) comes out nearly empty. ichi1/spec1 are small,
# manually-curated "genuinely core vocabulary" tags, so allow those too, but only
# for kana-only entries — allowing them project-wide reintroduces the 21k blowup.
KANA_ONLY_EXTRA_TAGS = {"ichi1", "spec1"}


def is_common(tags, kana_only):
    for t in tags:
        if not t:
            continue
        if kana_only and t in KANA_ONLY_EXTRA_TAGS:
            return True
        m = NF_RE.match(t)
        if m and int(m.group(1)) <= COMMON_NF_CUTOFF:
            return True
    return False

# Lower score = more common / preferred as the canonical display form.
_TAG_SCORE = {"ichi1": 1, "news1": 1, "spec1": 1, "gai1": 2, "ichi2": 3, "news2": 3, "spec2": 3, "gai2": 4}


def download():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    for url, dest in ((JMDICT_GZ_URL, JMDICT_XML), (KANJIDIC2_GZ_URL, KANJIDIC2_XML)):
        print(f"Downloading {url} ...")
        with urllib.request.urlopen(url) as resp:
            raw = resp.read()
        xml_bytes = gzip.decompress(raw)
        dest.write_bytes(xml_bytes)
        print(f"  -> {dest} ({len(xml_bytes):,} bytes)")


def tag_score(tags):
    best = 999
    for t in tags:
        best = min(best, _TAG_SCORE.get(t, 999))
        m = NF_RE.match(t)
        if m:
            best = min(best, int(m.group(1)))
    return best


def min_nf(tags):
    nums = [int(m.group(1)) for t in tags if (m := NF_RE.match(t))]
    return min(nums) if nums else None


def parse_kanjidic2(path):
    """Return {kanji_char: grade_int} for every character that has a <grade>."""
    grades = {}
    root = ET.parse(path).getroot()
    for char_el in root.findall("character"):
        literal = char_el.findtext("literal")
        grade_text = char_el.findtext("misc/grade")
        if literal and grade_text is not None:
            grades[literal] = int(grade_text)
    return grades


def hardness_rank(grade):
    """Higher = harder. `grade` is a raw KANJIDIC2 grade or None (not in KANJIDIC2)."""
    if grade is None:
        return 99
    if grade in (9, 10):
        return 90
    if grade == 8:
        return 80
    return grade


def tier_for_rank(rank):
    if rank is None:
        return 0
    if rank <= 2:
        return 1
    if rank <= 4:
        return 2
    if rank <= 6:
        return 3
    return 4


def hardest_kanji_grade(surface, kanji_grades):
    """Return (raw_grade_or_None, hardness_rank_or_None) for the hardest kanji in surface,
    or (None, None) if surface contains no kanji at all (kana-only)."""
    kanji_chars = KANJI_RE.findall(surface)
    if not kanji_chars:
        return None, None
    best_grade, best_rank = None, -1
    for ch in kanji_chars:
        grade = kanji_grades.get(ch)
        rank = hardness_rank(grade)
        if rank > best_rank:
            best_rank, best_grade = rank, grade
    return best_grade, best_rank


def pick_best(elements, pri_tag):
    """Pick the element with the best (lowest) priority-tag score; ties keep first-listed."""
    best_el, best_score = None, None
    for el in elements:
        tags = [p.text for p in el.findall(pri_tag)]
        score = tag_score(tags)
        if best_score is None or score < best_score:
            best_el, best_score = el, score
    return best_el


def select_surface_and_reading(entry):
    """Return (surface, reading, kana_only, chosen_reb_pri_tags) or None if entry has no readings."""
    k_eles = entry.findall("k_ele")
    r_eles = entry.findall("r_ele")
    if not r_eles:
        return None

    if k_eles:
        best_k = pick_best(k_eles, "ke_pri")
        surface = best_k.findtext("keb")
        if KANJI_RE.search(surface):
            restr_ok = []
            for r in r_eles:
                if r.find("re_nokanji") is not None:
                    continue
                restrs = [t.text for t in r.findall("re_restr")]
                if not restrs or surface in restrs:
                    restr_ok.append(r)
            candidates = restr_ok or r_eles
            best_r = pick_best(candidates, "re_pri")
            reading = best_r.findtext("reb")
            pri_tags = [p.text for p in best_r.findall("re_pri")] + [p.text for p in best_k.findall("ke_pri")]
            return surface, reading, False, pri_tags
    # Kana-only entry, or the "best" keb turned out to have no actual kanji in it.
    best_r = pick_best(r_eles, "re_pri")
    reading = best_r.findtext("reb")
    pri_tags = [p.text for p in best_r.findall("re_pri")]
    return reading, reading, True, pri_tags


def first_gloss(entry):
    sense = entry.find("sense")
    if sense is None:
        return None
    for gloss in sense.findall("gloss"):
        lang = gloss.get("{http://www.w3.org/XML/1998/namespace}lang")
        if lang is None or lang == "eng":
            return gloss.text
    return None


def all_pri_tags(entry):
    tags = []
    for el in entry.findall("k_ele"):
        tags += [p.text for p in el.findall("ke_pri")]
    for el in entry.findall("r_ele"):
        tags += [p.text for p in el.findall("re_pri")]
    return tags


def parse_jmdict(path, kanji_grades):
    rows = []
    root = ET.parse(path).getroot()
    for entry in root.findall("entry"):
        seq_text = entry.findtext("ent_seq")
        if not seq_text:
            continue
        seq = int(seq_text)

        selection = select_surface_and_reading(entry)
        if selection is None:
            continue
        surface, reading, kana_only, chosen_pri_tags = selection
        reading = normalize_reading(reading)

        if not is_common(all_pri_tags(entry), kana_only):
            continue

        if len(reading) <= 1:
            continue

        if len(reading) > MAX_READING_LENGTH:
            continue

        meaning = first_gloss(entry)
        if not meaning:
            continue

        if kana_only:
            tier, hardest_grade = 0, None
        else:
            hardest_grade, rank = hardest_kanji_grade(surface, kanji_grades)
            if rank is None:
                kana_only, tier = True, 0
            else:
                tier = tier_for_rank(rank)

        tier = max(tier, length_tier_floor(len(reading)))

        freq_rank = min_nf(chosen_pri_tags)

        rows.append((seq, surface, reading, meaning, tier, hardest_grade, freq_rank, int(kana_only)))
    return rows


def write_db(rows, output_path):
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()
    conn = sqlite3.connect(output_path)
    conn.execute(
        """
        CREATE TABLE words (
            id                INTEGER PRIMARY KEY NOT NULL,
            surface           TEXT    NOT NULL,
            reading           TEXT    NOT NULL,
            meaning           TEXT    NOT NULL,
            tier              INTEGER NOT NULL,
            hardestKanjiGrade INTEGER,
            frequencyRank     INTEGER,
            kanaOnly          INTEGER NOT NULL
        )
        """
    )
    conn.executemany("INSERT INTO words VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows)
    conn.commit()
    conn.close()


def print_report(rows):
    print(f"Total words: {len(rows):,}")
    counts = {}
    for row in rows:
        counts[row[4]] = counts.get(row[4], 0) + 1
    for tier in sorted(counts):
        print(f"  tier {tier}: {counts[tier]:,}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--download", action="store_true", help="fetch raw JMdict/KANJIDIC2 dumps (network)")
    args = parser.parse_args()

    if args.download:
        download()
        return

    if not JMDICT_XML.exists() or not KANJIDIC2_XML.exists():
        print("Raw sources missing. Run with --download first.", file=sys.stderr)
        sys.exit(1)

    print("Parsing KANJIDIC2 ...")
    kanji_grades = parse_kanjidic2(KANJIDIC2_XML)
    print(f"  {len(kanji_grades):,} kanji with a grade")

    print("Parsing JMdict ...")
    rows = parse_jmdict(JMDICT_XML, kanji_grades)

    print("Writing DB ...")
    write_db(rows, OUTPUT_DB)
    print(f"Wrote {OUTPUT_DB}")
    print_report(rows)


if __name__ == "__main__":
    main()
