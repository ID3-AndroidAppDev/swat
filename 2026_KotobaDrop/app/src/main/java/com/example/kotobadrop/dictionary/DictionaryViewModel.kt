package com.example.kotobadrop.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kotobadrop.core.data.MetWordRepository
import com.example.kotobadrop.core.data.WordEntity
import com.example.kotobadrop.core.data.WordRepository
import com.example.kotobadrop.input.RomajiConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MOST_MISSED_LIMIT = 10

data class DictionaryEntry(
    val word: WordEntity,
    /** Canonical Hepburn romaji of the reading, precomputed once at load — displayed on the card and searchable. */
    val romaji: String,
    val firstSeen: Long,
    val timesSeen: Int,
    val timesCleared: Int,
    val timesMissed: Int,
)

data class DictionaryUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val hasAnyHistory: Boolean = false,
    /** Unfiltered count of ever-missed words — gates the review-run entry, unaffected by search. */
    val missedCount: Int = 0,
    val mostMissed: List<DictionaryEntry> = emptyList(),
    val allEntries: List<DictionaryEntry> = emptyList(),
)

class DictionaryViewModel(
    private val wordRepository: WordRepository,
    private val metWordRepository: MetWordRepository,
) : ViewModel() {
    private val entries = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    private val loaded = MutableStateFlow(false)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<DictionaryUiState> = combine(entries, loaded, query) { entryList, isLoaded, q ->
        val needle = q.trim()
        val filtered = if (needle.isEmpty()) {
            entryList
        } else {
            // A latin query also matches through the romaji column ("kyoku" finds 局) and
            // as converted kana ("sya" -> しゃ matches readings even though the canonical
            // romaji display is "sha") — a learner who can't type kana can still search.
            val kanaNeedle = RomajiConverter.toKana(needle.lowercase())
            entryList.filter {
                it.word.surface.contains(needle, ignoreCase = true) ||
                    it.word.reading.contains(needle, ignoreCase = true) ||
                    it.word.meaning.contains(needle, ignoreCase = true) ||
                    it.romaji.contains(needle, ignoreCase = true) ||
                    it.word.reading.contains(kanaNeedle)
            }
        }
        DictionaryUiState(
            loaded = isLoaded,
            query = q,
            hasAnyHistory = entryList.isNotEmpty(),
            missedCount = entryList.count { it.timesMissed > 0 },
            mostMissed = filtered.filter { it.timesMissed > 0 }
                .sortedByDescending { it.timesMissed }
                .take(MOST_MISSED_LIMIT),
            allEntries = filtered.sortedByDescending { it.firstSeen },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DictionaryUiState(),
    )

    init {
        viewModelScope.launch {
            val met = metWordRepository.getAll()
            val words = wordRepository.getWordsByIds(met.map { it.wordId }).associateBy { it.id }
            entries.value = met.mapNotNull { m ->
                words[m.wordId]?.let { w ->
                    DictionaryEntry(w, RomajiConverter.toRomaji(w.reading), m.firstSeen, m.timesSeen, m.timesCleared, m.timesMissed)
                }
            }
            loaded.value = true
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun resetHistory() {
        viewModelScope.launch {
            metWordRepository.resetAll()
            entries.value = emptyList()
        }
    }

    companion object {
        fun factory(wordRepository: WordRepository, metWordRepository: MetWordRepository) = viewModelFactory {
            initializer { DictionaryViewModel(wordRepository, metWordRepository) }
        }
    }
}
