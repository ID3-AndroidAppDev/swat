package com.example.kotobadrop.game

import com.example.kotobadrop.core.model.SpeedDifficulty
import kotlin.math.roundToInt

/**
 * Every game-balance number in one place, per CLAUDE.md §5. Formula variable names
 * (b0, κ, λ, α, s_max, r, g, L, C, Δt) match the spec's notation directly.
 */
object GameTuning {
    const val BASE_VALUE = 10f // b0
    const val KANJI_GRADE_WEIGHT = 0.3f // κ
    const val LENGTH_WEIGHT = 0.15f // λ
    const val COMBO_WEIGHT = 0.10f // α
    const val SPEED_BONUS_MAX = 2.0f // s_max
    const val SPEED_BONUS_DECAY = 0.2f // r

    const val MAX_ACTIVE_WORDS = 5
    const val MISS_FLASH_SECONDS = 1.5f
    const val DEFAULT_LIVES = 3

    // How long the final frame holds after a run ends before navigating away — long enough
    // for the fatal miss's reading flash (MISS_FLASH_SECONDS) or the final clear's petal
    // burst to actually be seen. Without this the "teaching moment" was skipped for exactly
    // the word that ended the run.
    const val END_OF_RUN_HOLD_MILLIS = 1600L

    // Spawn placement: a new word avoids landing within SPAWN_SEPARATION_X (fraction of
    // screen width) of any word still near the top (yFraction < SPAWN_SEPARATION_Y), so two
    // quick spawns don't render overlapping. Best-effort — after a few resamples the
    // overlap is accepted rather than delaying the spawn.
    const val SPAWN_SEPARATION_X = 0.2f
    const val SPAWN_SEPARATION_Y = 0.12f
    const val SPAWN_PLACEMENT_ATTEMPTS = 5

    // Words already shown this run spawn at a fraction of their normal weight, so a word
    // cleared or missed seconds ago doesn't immediately come back — guarantees variety
    // within a run without excluding repeats outright (small pools must stay playable).
    const val REPEAT_DAMP = 0.25f

    // In-run difficulty ramp: ramps spawn interval and fall duration down to this
    // floor fraction of their base value, over the course of a run. Softened from
    // 0.6/0.15 — the old ramp made long Endless runs feel punishing.
    const val RAMP_FLOOR = 0.75f
    const val RAMP_PER_MINUTE = 0.10f

    // Readings at or below this length get exactly the base fall duration; every kana
    // beyond it adds EXTRA_SECONDS_PER_KANA. Without this, a fixed fall duration meant a
    // 2-kana word and a 12+-kana word got the same time to type — long words (a ~100-word
    // tail up to 22 kana) were effectively far harder than short ones for no game-design
    // reason. 4 is the dataset's median reading length, so most words are unaffected.
    const val LENGTH_BASELINE_KANA = 4
    const val EXTRA_SECONDS_PER_KANA = 0.6f

    fun spawnIntervalSeconds(speed: SpeedDifficulty): Float = when (speed) {
        SpeedDifficulty.EASY -> 3.3f
        SpeedDifficulty.NORMAL -> 2.8f
        SpeedDifficulty.HARD -> 2.3f
        SpeedDifficulty.EXPERT -> 1.8f
    }

    /** Base fall duration for `speed`, extended for readings longer than [LENGTH_BASELINE_KANA]. */
    fun fallDurationSeconds(speed: SpeedDifficulty, readingLength: Int): Float {
        val base = when (speed) {
            SpeedDifficulty.EASY -> 11.5f
            SpeedDifficulty.NORMAL -> 9.5f
            SpeedDifficulty.HARD -> 8.0f
            SpeedDifficulty.EXPERT -> 6.5f
        }
        val extraKana = (readingLength - LENGTH_BASELINE_KANA).coerceAtLeast(0)
        return base + extraKana * EXTRA_SECONDS_PER_KANA
    }

    fun rampFactor(elapsedSeconds: Float): Float =
        (1f - RAMP_PER_MINUTE * elapsedSeconds / 60f).coerceAtLeast(RAMP_FLOOR)

    /** b = b0 · (1 + κ·g) · (1 + λ·L). g is 0 for kana-only words. */
    fun wordValue(hardestKanjiGrade: Int?, readingLength: Int): Float {
        val g = hardestKanjiGrade ?: 0
        return BASE_VALUE * (1 + KANJI_GRADE_WEIGHT * g) * (1 + LENGTH_WEIGHT * readingLength)
    }

    /** s(Δt) = max(1, s_max − r·Δt). */
    fun speedBonus(secondsSinceLastClear: Float): Float =
        (SPEED_BONUS_MAX - SPEED_BONUS_DECAY * secondsSinceLastClear).coerceAtLeast(1f)

    /** p = b · (1 + α·C) · s(Δt), rounded to the nearest point. */
    fun payout(hardestKanjiGrade: Int?, readingLength: Int, combo: Int, secondsSinceLastClear: Float): Int {
        val b = wordValue(hardestKanjiGrade, readingLength)
        val p = b * (1 + COMBO_WEIGHT * combo) * speedBonus(secondsSinceLastClear)
        return p.roundToInt()
    }

    // ── Adaptive spawn weighting (§5) — biases spawning toward missed/unmet words, away
    // from reliably-cleared ones. Snapshotted once per run from met-word history, so the
    // effect shows up across runs ("missed several times -> spawns more often next run"),
    // not as live feedback within a single run.
    const val SPAWN_WEIGHT_BASE = 1f
    const val MISS_BONUS = 0.5f // per timesMissed
    const val UNMET_BOOST = 1.5f // flat multiplier for never-cleared words
    const val CLEAR_DAMP = 0.15f // per timesCleared, in the denominator
    const val SPAWN_WEIGHT_MIN = 0.2f
    const val SPAWN_WEIGHT_MAX = 5f

    /** weight = base · (1 + missBonus·timesMissed) · (unmet ? unmetBoost : 1) / (1 + clearDamp·timesCleared), clamped. */
    fun spawnWeight(timesMissed: Int, timesCleared: Int, unmet: Boolean): Float {
        val w = SPAWN_WEIGHT_BASE *
            (1 + MISS_BONUS * timesMissed) *
            (if (unmet) UNMET_BOOST else 1f) /
            (1 + CLEAR_DAMP * timesCleared)
        return w.coerceIn(SPAWN_WEIGHT_MIN, SPAWN_WEIGHT_MAX)
    }

    // ── Frequency-bias spawning (spaced-repetition-style word introduction) — favors
    // common/important words early in a player's career, so a beginner meets とても and
    // これ long before rare tier-appropriate vocabulary; the bias fades out as the player's
    // met-word count grows, so it never becomes a hard gate that locks content away. This
    // stacks multiplicatively with spawnWeight (multiplied into it in GameViewModel, not a
    // replacement) — the two dimensions are independent: spawnWeight reacts to *this
    // player's* per-word history, this reacts to *the vocabulary's* overall frequency rank.
    // Endless-only per user decision — Campaign's fixed section/level structure (tiers 0-4
    // as discrete levels to master) doesn't fit a rolling introduction model.
    const val FREQUENCY_BOOST_MAX = 2f // extra multiplier at rank 1, full bias strength

    // Distinct words met (met_words row count) at which the bias has fully faded to 1x.
    // ~10-15% of a single tier's pool — enough real play that "still mostly unfamiliar
    // with the vocabulary" no longer holds, without requiring a player to exhaust a tier.
    const val FAMILIARITY_HORIZON = 200

    // JMdict's nf frequency tags run 1 (most common ~500 words) through 12 (§6's cutoff);
    // words with no nf tag at all (frequencyRank == null — e.g. common kana-only function
    // words that only carry ichi1/spec1) are treated as one bucket past the least common
    // ranked bucket: still eligible for a small boost, but never prioritized over anything
    // that has a real rank.
    private const val LOWEST_NF_RANK = 12

    /**
     * Multiplier favoring low (common) [frequencyRank] words, strongest when [metWordCount]
     * (distinct words the player has ever encountered) is low and fading linearly to 1x
     * (no bias) by [FAMILIARITY_HORIZON]. Always >= 1 — never penalizes a word, only boosts
     * common ones, so rare/unranked words remain reachable at their normal rate throughout.
     */
    fun frequencyBias(frequencyRank: Int?, metWordCount: Int): Float {
        val rank = frequencyRank ?: (LOWEST_NF_RANK + 1)
        val rankFactor = (LOWEST_NF_RANK + 1 - rank).toFloat() / LOWEST_NF_RANK
        val familiarity = (metWordCount.toFloat() / FAMILIARITY_HORIZON).coerceIn(0f, 1f)
        val biasStrength = 1f - familiarity
        return 1f + FREQUENCY_BOOST_MAX * biasStrength * rankFactor
    }
}
