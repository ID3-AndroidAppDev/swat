package com.example.kotobadrop.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kotobadrop.core.data.MetWordRepository
import com.example.kotobadrop.core.data.ScoreRepository
import com.example.kotobadrop.core.data.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TierProgress(val tier: Int, val met: Int, val total: Int)

data class StatsUiState(
    val loaded: Boolean = false,
    val wordsMet: Int = 0,
    val wordsTotal: Int = 0,
    val tiers: List<TierProgress> = emptyList(),
    val runs: Int = 0,
    val bestScore: Int = 0,
    val totalCleared: Int = 0,
    val totalMissed: Int = 0,
) {
    /** Lifetime accuracy over saved (STANDARD) runs; null when there's nothing to compute. */
    val accuracyPercent: Int?
        get() = if (totalCleared + totalMissed > 0) totalCleared * 100 / (totalCleared + totalMissed) else null
}

/**
 * Collection progress (met words vs the full pool, per tier) + lifetime play stats from
 * the scores table. Loaded once per screen entry — both data sets only change through
 * playing, which can't happen while this screen is visible.
 */
class StatsViewModel(
    private val wordRepository: WordRepository,
    private val metWordRepository: MetWordRepository,
    private val scoreRepository: ScoreRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val met = metWordRepository.getAll()
            val metWords = wordRepository.getWordsByIds(met.map { it.wordId })
            val metByTier = metWords.groupingBy { it.tier }.eachCount()
            val totals = wordRepository.tierCounts().sortedBy { it.tier }
            val aggregates = scoreRepository.aggregates()
            _uiState.value = StatsUiState(
                loaded = true,
                wordsMet = metWords.size,
                wordsTotal = totals.sumOf { it.count },
                tiers = totals.map { TierProgress(it.tier, metByTier[it.tier] ?: 0, it.count) },
                runs = aggregates.runs,
                bestScore = aggregates.bestScore,
                totalCleared = aggregates.totalCleared,
                totalMissed = aggregates.totalMissed,
            )
        }
    }

    companion object {
        fun factory(
            wordRepository: WordRepository,
            metWordRepository: MetWordRepository,
            scoreRepository: ScoreRepository,
        ) = viewModelFactory {
            initializer { StatsViewModel(wordRepository, metWordRepository, scoreRepository) }
        }
    }
}
