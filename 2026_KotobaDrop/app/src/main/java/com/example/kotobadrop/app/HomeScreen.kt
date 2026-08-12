package com.example.kotobadrop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.core.ui.campaignSectionDisplayName
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sora
import com.example.kotobadrop.core.ui.theme.Sumi
import com.example.kotobadrop.core.ui.theme.seigahaPattern
import com.example.kotobadrop.game.campaign.CampaignLevel
import com.example.kotobadrop.game.campaign.LevelStatus
import com.example.kotobadrop.game.campaign.campaignSectionStates
import kotlinx.coroutines.flow.first

@Composable
fun HomeScreen(
    onEndlessClick: () -> Unit,
    onCampaignClick: () -> Unit,
    onDictionaryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTutorialClick: () -> Unit,
    onStatsClick: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(app.wordRepository))
    val wordCount by viewModel.wordCount.collectAsState()
    val bestScore by produceState<Int?>(initialValue = null) {
        value = app.scoreRepository.getHighScores(1).firstOrNull()?.score
    }
    val currentCampaignLevel by produceState<CampaignLevel?>(initialValue = null) {
        val completed = app.campaignRepository.progressFlow.first()
        value = campaignSectionStates(completed)
            .flatMap { it.levels }
            .firstOrNull { it.status == LevelStatus.PLAYABLE }
            ?.level
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .seigahaPattern()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Bilingual brand mark — shown together regardless of the UI language toggle,
            // like a logo, so not string-resourced.
            Text("言葉ドロップ", style = MaterialTheme.typography.displayMedium, fontSize = 44.sp)
            Text("KotobaDrop", style = MaterialTheme.typography.headlineSmall, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = wordCount?.let { stringResource(R.string.home_word_count, it) } ?: stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = KotobaTheme.palette.ink.copy(alpha = 0.7f),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ModeCard(
                    title = stringResource(R.string.home_endless),
                    status = bestScore?.let { stringResource(R.string.home_endless_best, it) },
                    color = Sakura,
                    onClick = onEndlessClick,
                )
                ModeCard(
                    title = stringResource(R.string.home_campaign),
                    status = currentCampaignLevel?.let { level ->
                        stringResource(
                            R.string.home_campaign_status,
                            campaignSectionDisplayName(level.sectionTier),
                            level.indexInSection + 1,
                        )
                    },
                    color = Sora,
                    onClick = onCampaignClick,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDictionaryClick,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KotobaTheme.palette.ink),
                ) { Text(stringResource(R.string.home_dictionary), fontSize = 14.sp) }
                OutlinedButton(
                    onClick = onStatsClick,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KotobaTheme.palette.ink),
                ) { Text(stringResource(R.string.home_stats), fontSize = 14.sp) }
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(
                    onClick = onSettingsClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink.copy(alpha = 0.6f)),
                ) { Text(stringResource(R.string.home_settings), fontSize = 13.sp) }
                TextButton(
                    onClick = onTutorialClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink.copy(alpha = 0.6f)),
                ) { Text(stringResource(R.string.home_tutorial), fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    status: String?,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = color.copy(alpha = 0.5f),
                spotColor = color.copy(alpha = 0.5f),
            )
            .background(color, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontSize = 22.sp, color = Sumi)
        status?.let {
            Text(it, fontSize = 13.sp, color = Sumi.copy(alpha = 0.75f))
        }
    }
}
