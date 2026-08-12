package com.example.kotobadrop.game.campaign

import com.example.kotobadrop.core.model.SpeedDifficulty

data class CampaignLevel(
    val id: String,
    val sectionTier: Int,
    val indexInSection: Int,
    val speed: SpeedDifficulty,
    val targetClears: Int,
    val lives: Int,
)

data class CampaignSection(
    val tier: Int,
    val levels: List<CampaignLevel>,
)

// 5 levels/section: EASY/NORMAL/HARD/EXPERT, then a repeated EXPERT "master" level as a
// capstone — uses the full SpeedDifficulty range (EXPERT was previously unused anywhere
// in Campaign) rather than just re-padding targets at HARD.
private val LEVEL_SPEEDS = listOf(
    SpeedDifficulty.EASY, SpeedDifficulty.NORMAL, SpeedDifficulty.HARD,
    SpeedDifficulty.EXPERT, SpeedDifficulty.EXPERT,
)
private val LEVEL_TARGETS = listOf(8, 12, 16, 20, 24)

// The master level (index 4) shares level 4's EXPERT speed, so its identity comes from a
// real twist rather than just a bigger target: 2 lives instead of 3.
private val LEVEL_LIVES = listOf(3, 3, 3, 3, 2)

// Index of the "NORMAL" level within a section — completing it unlocks the next section,
// independent of whether this section's harder levels (HARD/EXPERT/master) are done. See
// campaignSectionStates() below.
private const val NORMAL_LEVEL_INDEX = 1

val CAMPAIGN_SECTIONS: List<CampaignSection> = (0..4).map { tier ->
    CampaignSection(
        tier = tier,
        levels = LEVEL_SPEEDS.indices.map { i ->
            CampaignLevel(
                id = "$tier-$i",
                sectionTier = tier,
                indexInSection = i,
                speed = LEVEL_SPEEDS[i],
                targetClears = LEVEL_TARGETS[i],
                lives = LEVEL_LIVES[i],
            )
        },
    )
}

val CAMPAIGN_LEVELS: List<CampaignLevel> = CAMPAIGN_SECTIONS.flatMap { it.levels }

fun campaignLevelById(id: String): CampaignLevel? = CAMPAIGN_LEVELS.firstOrNull { it.id == id }

fun nextCampaignLevel(currentId: String): CampaignLevel? {
    val index = CAMPAIGN_LEVELS.indexOfFirst { it.id == currentId }
    return if (index in CAMPAIGN_LEVELS.indices && index + 1 < CAMPAIGN_LEVELS.size) CAMPAIGN_LEVELS[index + 1] else null
}

/**
 * Star rating for the redesigned Level Result screen (README §4's "add a stars field... if
 * not already present") — derived from data already threaded through the LEVEL_RESULT nav
 * route (cleared, targetClears), no new state needed. 0 stars if the target wasn't reached;
 * otherwise 1 baseline star, +1 at 1.25x the target, +1 more at 1.5x — clearing well past
 * the target (Campaign lets play continue after passing) earns the extra stars.
 */
fun starsForClear(cleared: Int, target: Int): Int {
    if (target <= 0 || cleared < target) return 0
    return when {
        cleared >= target * 1.5 -> 3
        cleared >= target * 1.25 -> 2
        else -> 1
    }
}

enum class LevelStatus { LOCKED, PLAYABLE, COMPLETED }

data class LevelUiState(val level: CampaignLevel, val status: LevelStatus, val bestScore: Int?)

data class SectionUiState(val section: CampaignSection, val levels: List<LevelUiState>)

/**
 * Pure unlock logic, given `completed` (levelId -> best score, presence = completed) from
 * CampaignRepository. Two independent rules, checked separately:
 *  - Within a section: level i (i>0) is playable once level i-1 (same section) is completed.
 *  - Across sections: section N (N>0)'s level 0 is playable once section N-1's NORMAL-speed
 *    level (index 1, not its last level) is completed — a player can move on to the next
 *    word-tier section without first grinding the current section's HARD/EXPERT levels.
 * Section 0's level 0 is always playable. A completed level is always COMPLETED regardless
 * of either rule.
 */
fun campaignSectionStates(completed: Map<String, Int>): List<SectionUiState> {
    val sectionUnlocked = BooleanArray(CAMPAIGN_SECTIONS.size)
    sectionUnlocked[0] = true
    for (i in 1 until CAMPAIGN_SECTIONS.size) {
        val normalLevel = CAMPAIGN_SECTIONS[i - 1].levels[NORMAL_LEVEL_INDEX]
        sectionUnlocked[i] = completed.containsKey(normalLevel.id)
    }

    return CAMPAIGN_SECTIONS.mapIndexed { sectionIndex, section ->
        val levelStates = section.levels.mapIndexed { i, level ->
            val status = when {
                completed.containsKey(level.id) -> LevelStatus.COMPLETED
                i == 0 -> if (sectionUnlocked[sectionIndex]) LevelStatus.PLAYABLE else LevelStatus.LOCKED
                completed.containsKey(section.levels[i - 1].id) -> LevelStatus.PLAYABLE
                else -> LevelStatus.LOCKED
            }
            LevelUiState(level, status, completed[level.id])
        }
        SectionUiState(section, levelStates)
    }
}
