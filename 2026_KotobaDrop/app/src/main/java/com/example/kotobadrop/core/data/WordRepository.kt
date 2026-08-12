package com.example.kotobadrop.core.data

class WordRepository(private val wordDao: WordDao) {
    suspend fun wordCount(): Int = wordDao.count()
    suspend fun getWords(): List<WordEntity> = wordDao.getAll()
    suspend fun getWordsByIds(ids: List<Int>): List<WordEntity> = wordDao.getByIds(ids)
    suspend fun tierCounts(): List<TierCount> = wordDao.countByTier()
}
