package com.example.kotobadrop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.core.model.SpeedDifficulty
import com.example.kotobadrop.core.ui.SectionCard
import com.example.kotobadrop.core.ui.SectionLabel
import com.example.kotobadrop.core.ui.SelectableChip
import com.example.kotobadrop.core.ui.campaignSectionDisplayName
import com.example.kotobadrop.core.ui.speedDifficultyLabel
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.seigahaPattern
import com.example.kotobadrop.game.GameTuning
import com.example.kotobadrop.game.RunConfig
import com.example.kotobadrop.game.RunMode
import com.example.kotobadrop.game.campaign.CAMPAIGN_SECTIONS
import com.example.kotobadrop.settings.SettingsViewModel

@Composable
fun EndlessSetupScreen(onStart: (RunConfig) -> Unit) {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app.settingsRepository, app.metWordRepository, app.campaignRepository))
    val settings by viewModel.settings.collectAsState()
    val bestScore by produceState<Int?>(initialValue = null) {
        value = app.scoreRepository.getHighScores(1).firstOrNull()?.score
    }
    // Per-run choice, deliberately not persisted — zen is a "right now I want to warm up"
    // mode, not a standing preference like the difficulty dials.
    var zenMode by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().seigahaPattern().padding(24.dp)) {
            Text(stringResource(R.string.home_endless), style = MaterialTheme.typography.headlineMedium)
            bestScore?.let { best ->
                Text(
                    stringResource(R.string.results_best, best),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KotobaTheme.palette.ink.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            SectionLabel(stringResource(R.string.section_speed), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            SectionCard {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpeedDifficulty.entries.forEach { speed ->
                        SelectableChip(
                            selected = settings.speedDifficulty == speed,
                            onClick = { viewModel.setSpeedDifficulty(speed) },
                            label = speedDifficultyLabel(speed),
                        )
                    }
                }
            }

            SectionLabel(
                stringResource(R.string.section_word_difficulty_up_to, campaignSectionDisplayName(settings.knowledgeDifficulty)),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            SectionCard {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CAMPAIGN_SECTIONS.forEach { section ->
                        SelectableChip(
                            selected = settings.knowledgeDifficulty == section.tier,
                            onClick = { viewModel.setKnowledgeDifficulty(section.tier) },
                            label = section.tier.toString(),
                            horizontalPadding = 18.dp,
                            verticalPadding = 9.dp,
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_rules), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.label_furigana), fontSize = 14.sp)
                    Switch(checked = settings.furigana, onCheckedChange = viewModel::setFurigana)
                }
                HorizontalDivider(color = KotobaTheme.palette.ink.copy(alpha = 0.06f), thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.endless_zen_label), fontSize = 14.sp)
                    Switch(checked = zenMode, onCheckedChange = { zenMode = it })
                }
            }

            Button(
                onClick = {
                    onStart(
                        RunConfig(
                            speed = settings.speedDifficulty,
                            tierMin = 0,
                            tierMax = settings.knowledgeDifficulty,
                            furigana = settings.furigana,
                            targetClears = null,
                            inputMode = settings.inputMode,
                            lives = GameTuning.DEFAULT_LIVES,
                            mode = if (zenMode) RunMode.ZEN else RunMode.STANDARD,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            ) {
                Text(stringResource(R.string.action_start))
            }
        }
    }
}
