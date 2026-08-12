package com.example.kotobadrop.game

import com.example.kotobadrop.core.model.SpeedDifficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class GameTuningTest {

    // ── Hand-computed cases, per CLAUDE.md §5 formulas ──────────────────────
    // b = b0 · (1 + κ·g) · (1 + λ·L)
    // p = b · (1 + α·C) · s(Δt),  s(Δt) = max(1, s_max − r·Δt)

    @Test fun payout_grade2_length4_combo3_dt1() {
        // b = 10 * 1.6 * 1.6 = 25.6 ; s(1.0) = 1.8 ; p = 25.6 * 1.3 * 1.8 = 59.904 -> 60
        assertEquals(60, GameTuning.payout(hardestKanjiGrade = 2, readingLength = 4, combo = 3, secondsSinceLastClear = 1.0f))
    }

    @Test fun payout_kanaOnly_length2_freshCombo() {
        // b = 10 * 1.0 * 1.3 = 13.0 ; s(0) = 2.0 ; p = 13.0 * 1.0 * 2.0 = 26.0 -> 26
        assertEquals(26, GameTuning.payout(hardestKanjiGrade = null, readingLength = 2, combo = 0, secondsSinceLastClear = 0f))
    }

    @Test fun payout_grade6_length6_combo5_slowClear() {
        // b = 10 * 2.8 * 1.9 = 53.2 ; s(10) floors to 1.0 ; p = 53.2 * 1.5 * 1.0 = 79.8 -> 80
        assertEquals(80, GameTuning.payout(hardestKanjiGrade = 6, readingLength = 6, combo = 5, secondsSinceLastClear = 10f))
    }

    @Test fun wordValue_treatsNullGradeAsZero() {
        assertEquals(GameTuning.wordValue(0, 3), GameTuning.wordValue(null, 3), 0.0001f)
    }

    @Test fun speedBonus_floorsAtOne() {
        assertEquals(1.0f, GameTuning.speedBonus(secondsSinceLastClear = 100f), 0.0001f)
    }

    @Test fun speedBonus_maxAtZeroDelta() {
        assertEquals(2.0f, GameTuning.speedBonus(secondsSinceLastClear = 0f), 0.0001f)
    }

    @Test fun rampFactor_startsAtOne() {
        assertEquals(1.0f, GameTuning.rampFactor(elapsedSeconds = 0f), 0.0001f)
    }

    @Test fun rampFactor_floorsAtRampFloor() {
        assertEquals(GameTuning.RAMP_FLOOR, GameTuning.rampFactor(elapsedSeconds = 600f), 0.0001f)
    }

    // ── fallDurationSeconds: length scaling ─────────────────────────────────

    @Test fun fallDuration_atOrBelowBaseline_isJustBase() {
        assertEquals(11.5f, GameTuning.fallDurationSeconds(SpeedDifficulty.EASY, readingLength = 2), 0.0001f)
        assertEquals(11.5f, GameTuning.fallDurationSeconds(SpeedDifficulty.EASY, readingLength = GameTuning.LENGTH_BASELINE_KANA), 0.0001f)
    }

    @Test fun fallDuration_aboveBaseline_addsPerExtraKana() {
        // 8 kana = 4 over baseline * 0.6s/kana = +2.4s
        assertEquals(13.9f, GameTuning.fallDurationSeconds(SpeedDifficulty.EASY, readingLength = 8), 0.0001f)
        assertEquals(8.9f, GameTuning.fallDurationSeconds(SpeedDifficulty.EXPERT, readingLength = 8), 0.0001f)
    }

    @Test fun fallDuration_longTailWord_getsSubstantialExtension() {
        // 22 kana (the longest reading in words.db) = 18 over baseline * 0.6s/kana = +10.8s
        assertEquals(22.3f, GameTuning.fallDurationSeconds(SpeedDifficulty.EASY, readingLength = 22), 0.0001f)
    }

    // ── spawnWeight: adaptive weighting, per CLAUDE.md §5 ────────────────────
    // weight = base · (1 + missBonus·timesMissed) · (unmet ? unmetBoost : 1) / (1 + clearDamp·timesCleared)

    @Test fun spawnWeight_neverSeenAndNeverMissed_isBaseWeight() {
        assertEquals(1.0f, GameTuning.spawnWeight(timesMissed = 0, timesCleared = 0, unmet = false), 0.0001f)
    }

    @Test fun spawnWeight_missedWordsWeightHigher() {
        // 1 * (1 + 0.5*3) * 1 / 1 = 2.5
        assertEquals(2.5f, GameTuning.spawnWeight(timesMissed = 3, timesCleared = 0, unmet = false), 0.0001f)
    }

    @Test fun spawnWeight_unmetGetsFlatBoost() {
        assertEquals(1.5f, GameTuning.spawnWeight(timesMissed = 0, timesCleared = 0, unmet = true), 0.0001f)
    }

    @Test fun spawnWeight_reliablyClearedWordsWeightLower() {
        // 1 * 1 * 1 / (1 + 0.15*10) = 1 / 2.5 = 0.4
        assertEquals(0.4f, GameTuning.spawnWeight(timesMissed = 0, timesCleared = 10, unmet = false), 0.0001f)
    }

    @Test fun spawnWeight_clampsAtMax() {
        // raw = 1 * (1 + 0.5*100) * 1 / 1 = 51, clamped to SPAWN_WEIGHT_MAX
        assertEquals(GameTuning.SPAWN_WEIGHT_MAX, GameTuning.spawnWeight(timesMissed = 100, timesCleared = 0, unmet = false), 0.0001f)
    }

    @Test fun spawnWeight_clampsAtMin() {
        // raw = 1 / (1 + 0.15*1000) ≈ 0.0066, clamped to SPAWN_WEIGHT_MIN
        assertEquals(GameTuning.SPAWN_WEIGHT_MIN, GameTuning.spawnWeight(timesMissed = 0, timesCleared = 1000, unmet = false), 0.0001f)
    }

    // ── frequencyBias: spaced-repetition-style word introduction (Endless only) ──────

    @Test fun frequencyBias_mostCommonWord_freshPlayer_getsMaxBoost() {
        // rankFactor = (13-1)/12 = 1, biasStrength = 1 (metWordCount=0) -> 1 + 2*1*1 = 3.0
        assertEquals(3.0f, GameTuning.frequencyBias(frequencyRank = 1, metWordCount = 0), 0.0001f)
    }

    @Test fun frequencyBias_unrankedWord_freshPlayer_getsNoBoost() {
        // null rank treated as rank 13 -> rankFactor = (13-13)/12 = 0 -> 1 + 0 = 1.0
        assertEquals(1.0f, GameTuning.frequencyBias(frequencyRank = null, metWordCount = 0), 0.0001f)
    }

    @Test fun frequencyBias_leastCommonRankedWord_freshPlayer_getsSmallBoost() {
        // rankFactor = (13-12)/12 = 1/12 -> 1 + 2*1*(1/12) = 1.16667
        assertEquals(1.16667f, GameTuning.frequencyBias(frequencyRank = 12, metWordCount = 0), 0.0001f)
    }

    @Test fun frequencyBias_fadesLinearlyAsPlayerMeetsMoreWords() {
        // metWordCount=100 is halfway to FAMILIARITY_HORIZON(200) -> biasStrength=0.5
        // rankFactor=1 (rank 1) -> 1 + 2*0.5*1 = 2.0
        assertEquals(2.0f, GameTuning.frequencyBias(frequencyRank = 1, metWordCount = 100), 0.0001f)
    }

    @Test fun frequencyBias_atFamiliarityHorizon_noBoostRegardlessOfRank() {
        assertEquals(1.0f, GameTuning.frequencyBias(frequencyRank = 1, metWordCount = 200), 0.0001f)
    }

    @Test fun frequencyBias_beyondFamiliarityHorizon_staysAtNoBoost() {
        // familiarity clamps at 1 past the horizon, doesn't invert into a penalty
        assertEquals(1.0f, GameTuning.frequencyBias(frequencyRank = 1, metWordCount = 5000), 0.0001f)
    }
}
