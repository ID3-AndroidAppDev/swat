package com.example.kotobadrop.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kotobadrop.core.data.CampaignRepository
import com.example.kotobadrop.core.data.MetWordRepository
import com.example.kotobadrop.core.data.SettingsRepository
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.Settings
import com.example.kotobadrop.core.model.SpeedDifficulty
import com.example.kotobadrop.core.model.ThemePreference
import com.example.kotobadrop.core.model.UiLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val metWordRepository: MetWordRepository,
    private val campaignRepository: CampaignRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setUiLanguage(value: UiLanguage) = viewModelScope.launch { repository.setUiLanguage(value) }

    fun setInputMode(value: InputMode) = viewModelScope.launch { repository.setInputMode(value) }

    fun setFurigana(value: Boolean) = viewModelScope.launch { repository.setFurigana(value) }

    fun setSoundEnabled(value: Boolean) = viewModelScope.launch { repository.setSoundEnabled(value) }

    fun setThemePreference(value: ThemePreference) = viewModelScope.launch { repository.setThemePreference(value) }

    fun setDoNotTouch(value: Boolean) = viewModelScope.launch { repository.setDoNotTouch(value) }

    /** Clears met-word history and campaign completion. Scores (best-score history) are deliberately kept. */
    fun resetProgress() = viewModelScope.launch {
        metWordRepository.resetAll()
        campaignRepository.resetAll()
    }

    fun setSpeedDifficulty(value: SpeedDifficulty) = viewModelScope.launch { repository.setSpeedDifficulty(value) }

    fun setKnowledgeDifficulty(value: Int) = viewModelScope.launch { repository.setKnowledgeDifficulty(value) }

    companion object {
        fun factory(
            repository: SettingsRepository,
            metWordRepository: MetWordRepository,
            campaignRepository: CampaignRepository,
        ) = viewModelFactory {
            initializer { SettingsViewModel(repository, metWordRepository, campaignRepository) }
        }
    }
}
