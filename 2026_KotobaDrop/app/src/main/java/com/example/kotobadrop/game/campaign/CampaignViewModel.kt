package com.example.kotobadrop.game.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kotobadrop.core.data.CampaignRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CampaignViewModel(repository: CampaignRepository) : ViewModel() {
    val sections: StateFlow<List<SectionUiState>> = repository.progressFlow.map { completed ->
        campaignSectionStates(completed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    companion object {
        fun factory(repository: CampaignRepository) = viewModelFactory {
            initializer { CampaignViewModel(repository) }
        }
    }
}
