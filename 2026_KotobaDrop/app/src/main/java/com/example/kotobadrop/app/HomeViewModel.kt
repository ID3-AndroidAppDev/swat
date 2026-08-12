package com.example.kotobadrop.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kotobadrop.core.data.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(wordRepository: WordRepository) : ViewModel() {
    private val _wordCount = MutableStateFlow<Int?>(null)
    val wordCount: StateFlow<Int?> = _wordCount

    init {
        viewModelScope.launch {
            _wordCount.value = wordRepository.wordCount()
        }
    }

    companion object {
        fun factory(wordRepository: WordRepository) = viewModelFactory {
            initializer { HomeViewModel(wordRepository) }
        }
    }
}
