package com.example.kotobadrop.game.campaign

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignLevelsTest {

    private fun statusOf(states: List<SectionUiState>, levelId: String): LevelStatus =
        states.flatMap { it.levels }.first { it.level.id == levelId }.status

    @Test fun fiveLevelsPerSection_withCorrectSpeedsAndTargets() {
        assertEquals(5, CAMPAIGN_SECTIONS.size)
        for (section in CAMPAIGN_SECTIONS) {
            assertEquals(5, section.levels.size)
        }
        val speeds = CAMPAIGN_SECTIONS[0].levels.map { it.speed }
        assertEquals(
            listOf(
                com.example.kotobadrop.core.model.SpeedDifficulty.EASY,
                com.example.kotobadrop.core.model.SpeedDifficulty.NORMAL,
                com.example.kotobadrop.core.model.SpeedDifficulty.HARD,
                com.example.kotobadrop.core.model.SpeedDifficulty.EXPERT,
                com.example.kotobadrop.core.model.SpeedDifficulty.EXPERT,
            ),
            speeds,
        )
        val targets = CAMPAIGN_SECTIONS[0].levels.map { it.targetClears }
        assertEquals(listOf(8, 12, 16, 20, 24), targets)
        // The master level's twist: 2 lives instead of 3.
        val lives = CAMPAIGN_SECTIONS[0].levels.map { it.lives }
        assertEquals(listOf(3, 3, 3, 3, 2), lives)
    }

    @Test fun freshProgress_onlyFirstLevelOfFirstSectionIsPlayable() {
        val states = campaignSectionStates(emptyMap())
        assertEquals(LevelStatus.PLAYABLE, statusOf(states, "0-0"))
        assertEquals(LevelStatus.LOCKED, statusOf(states, "0-1"))
        assertEquals(LevelStatus.LOCKED, statusOf(states, "1-0"))
    }

    @Test fun completingFirstLevel_unlocksSecondLevelOfSameSection() {
        val states = campaignSectionStates(mapOf("0-0" to 10))
        assertEquals(LevelStatus.COMPLETED, statusOf(states, "0-0"))
        assertEquals(LevelStatus.PLAYABLE, statusOf(states, "0-1"))
        assertEquals(LevelStatus.LOCKED, statusOf(states, "0-2"))
        assertEquals(LevelStatus.LOCKED, statusOf(states, "1-0"))
    }

    @Test fun completingNormalLevel_unlocksNextSection_evenThoughHarderLevelsOfCurrentSectionAreNotDone() {
        val states = campaignSectionStates(mapOf("0-0" to 10, "0-1" to 20))
        assertEquals(LevelStatus.PLAYABLE, statusOf(states, "0-2")) // HARD, same section
        assertEquals(LevelStatus.LOCKED, statusOf(states, "0-3")) // EXPERT, still gated sequentially
        assertEquals(LevelStatus.PLAYABLE, statusOf(states, "1-0")) // next section unlocked
        assertEquals(LevelStatus.LOCKED, statusOf(states, "1-1")) // within-section rule still applies
    }

    @Test fun completingOnlyFirstLevel_doesNotUnlockNextSection() {
        val states = campaignSectionStates(mapOf("0-0" to 10))
        assertEquals(LevelStatus.LOCKED, statusOf(states, "1-0"))
    }

    @Test fun lastSection_hasNoOutOfBoundsLookup() {
        // Section 4 (last) completing its NORMAL level must not attempt to unlock a
        // nonexistent section 5 — just verifying this doesn't throw and section 4 itself
        // behaves normally.
        val states = campaignSectionStates(mapOf("4-0" to 5, "4-1" to 5))
        assertEquals(LevelStatus.PLAYABLE, statusOf(states, "4-2"))
    }

    @Test fun completedLevel_isAlwaysCompletedRegardlessOfUnlockRules() {
        val states = campaignSectionStates(mapOf("0-0" to 10, "0-1" to 20, "0-2" to 5))
        assertEquals(LevelStatus.COMPLETED, statusOf(states, "0-2"))
    }

    @Test fun starsForClear_zeroWhenTargetNotReached() {
        assertEquals(0, starsForClear(cleared = 7, target = 8))
    }

    @Test fun starsForClear_oneStarAtExactlyTarget() {
        assertEquals(1, starsForClear(cleared = 8, target = 8))
    }

    @Test fun starsForClear_twoStarsAtOneQuarterOvershoot() {
        assertEquals(2, starsForClear(cleared = 10, target = 8))
    }

    @Test fun starsForClear_threeStarsAtHalfOvershoot() {
        assertEquals(3, starsForClear(cleared = 12, target = 8))
    }

    @Test fun starsForClear_justBelowThreeStarThreshold_isTwoStars() {
        assertEquals(2, starsForClear(cleared = 11, target = 8))
    }
}
