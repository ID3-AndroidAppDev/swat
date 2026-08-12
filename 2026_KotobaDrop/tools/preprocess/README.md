# Word data pipeline

Turns the raw JMdict + KANJIDIC2 dictionary dumps into `app/src/main/assets/words.db`,
the prepopulated Room database the app loads via `createFromAsset()`. Runs offline,
outside the app, never at runtime. Python 3 stdlib only — no dependencies to install.

## Usage

```bash
# One-time: fetch the raw dumps into ./data (the only step that touches the network)
python3 build_words_db.py --download

# Parse ./data and (re)generate app/src/main/assets/words.db — fully offline
python3 build_words_db.py
```

Re-run the second command any time the tiering/filtering logic changes; re-run
`--download` only to pick up a newer JMdict/KANJIDIC2 release.

## Sources

- [JMdict (English edition)](http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz) — surface forms, readings, glosses
- [KANJIDIC2](http://www.edrdg.org/kanjidic/kanjidic2.xml.gz) — per-kanji school grade, used to tier words

Both are © the Electronic Dictionary Research and Development Group (EDRDG),
licensed **CC BY-SA 4.0**. Any release of this app must carry the EDRDG attribution
notice and a link to the licenses — see the Credits screen (build step 8).

## What the script does

1. Keeps only "common" entries: top-6,000-by-frequency (`nf01`–`nf12`) for
   kanji-containing words, plus `ichi1`/`spec1`-tagged kana-only words (the
   newspaper-frequency `nf` tags under-represent kana-only function words like
   これ/それ/とても, so kana-only entries get a small additional editorial-tag
   allowance to keep tier 0 from coming out nearly empty).
2. Picks one surface + one reading per entry (the highest-priority `keb`/`reb` pair,
   respecting `re_restr`/`re_nokanji`) — one row per JMdict entry, keyed by `ent_seq`.
3. Takes the first gloss of the first sense as the English meaning.
4. Grades the word by its hardest constituent kanji (KANJIDIC2 `grade`), bucketing
   into tiers 0 (kana-only) through 4 (≈N2 and up — including non-jōyō/rare kanji).
5. Drops readings of 1 kana or less (untypeable at speed, worst prefix-collision case),
   and readings over `MAX_READING_LENGTH` (13 kana) — past that point a word is an
   unwieldy falling-word target regardless of tier (institutional/political proper-noun
   compounds, mostly).
6. Strips `・` (nakaguro) from readings — JMdict spells some acronym/loanword entries
   letter-by-letter with `・` as a visual separator (e.g. ジー・ユー・アイ for "GUI");
   it isn't a phoneme and has no romaji mapping, so leaving it in made the word
   permanently unclearable. Dropping it makes it type as one unbroken reading.
7. Raises a word's tier to at least `length_tier_floor(len(reading))` — long readings
   (>6 kana) get pushed to progressively higher minimum tiers so they can't spawn for
   beginners even when their (lack of) kanji would otherwise put them at tier 0/1.
8. Emits `words.db` and prints a total + per-tier row count for a sanity check.

## Output schema

```sql
CREATE TABLE words (
  id                INTEGER PRIMARY KEY NOT NULL,  -- JMdict ent_seq
  surface           TEXT    NOT NULL,
  reading           TEXT    NOT NULL,
  meaning           TEXT    NOT NULL,
  tier              INTEGER NOT NULL,               -- 0..4
  hardestKanjiGrade INTEGER,                         -- nullable: null when kanaOnly
  frequencyRank     INTEGER,                         -- nullable: null if no nf-tag
  kanaOnly          INTEGER NOT NULL                 -- 0/1
);
```

**Important for step 4:** Room's `createFromAsset()` validates the copied database's
actual table/column structure against the `@Entity` classes on first open — there's
no separate metadata bake-in step. The `WordEntity` Room entity must declare a table
named `words` with columns matching this schema exactly (same names, so no
`@ColumnInfo` overrides are needed) or the app will crash on first launch.
