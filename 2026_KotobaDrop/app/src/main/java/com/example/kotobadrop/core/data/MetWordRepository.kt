package com.example.kotobadrop.core.data

/**
 * Met-word counters: created on FIRST APPEARANCE, not on clear (CLAUDE.md §7). Backs both
 * the dictionary (§8) and adaptive spawn weighting (§5) — one dataset, two uses.
 */
class MetWordRepository(private val dao: MetWordDao) {
    suspend fun getAll(): List<MetWordEntity> = dao.getAll()

    suspend fun logSeen(wordId: Int) {
        val existing = dao.get(wordId)
        dao.upsert(
            existing?.copy(timesSeen = existing.timesSeen + 1)
                ?: MetWordEntity(wordId, firstSeen = System.currentTimeMillis(), timesSeen = 1, timesCleared = 0, timesMissed = 0)
        )
    }

    suspend fun logCleared(wordId: Int) {
        val existing = dao.get(wordId) ?: return
        dao.upsert(existing.copy(timesCleared = existing.timesCleared + 1))
    }

    suspend fun logMissed(wordId: Int) {
        val existing = dao.get(wordId) ?: return
        dao.upsert(existing.copy(timesMissed = existing.timesMissed + 1))
    }

    suspend fun resetAll() = dao.deleteAll()
}
