package com.example.kotobadrop.core.data

class ScoreRepository(private val dao: ScoreDao) {
    suspend fun save(
        score: Int,
        maxCombo: Int,
        wordsCleared: Int,
        wordsMissed: Int,
        speedDifficulty: String,
        knowledgeDifficulty: Int,
    ) {
        dao.insert(
            ScoreEntity(
                score = score,
                playedAt = System.currentTimeMillis(),
                maxCombo = maxCombo,
                wordsCleared = wordsCleared,
                wordsMissed = wordsMissed,
                speedDifficulty = speedDifficulty,
                knowledgeDifficulty = knowledgeDifficulty,
            )
        )
    }

    suspend fun getHighScores(limit: Int = 10): List<ScoreEntity> = dao.getHighScores(limit)
    suspend fun aggregates(): ScoreAggregates = dao.aggregates()
}
