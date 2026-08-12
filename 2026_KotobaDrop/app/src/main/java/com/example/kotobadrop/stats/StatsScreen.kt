package com.example.kotobadrop.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.app.KotobaDropApplication
import com.example.kotobadrop.core.ui.SectionCard
import com.example.kotobadrop.core.ui.SectionLabel
import com.example.kotobadrop.core.ui.StatCellData
import com.example.kotobadrop.core.ui.StatGrid
import com.example.kotobadrop.core.ui.campaignSectionDisplayName
import com.example.kotobadrop.core.ui.theme.Fuji
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Matcha
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sora
import com.example.kotobadrop.core.ui.theme.seigahaPattern

@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.factory(app.wordRepository, app.metWordRepository, app.scoreRepository),
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .seigahaPattern()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium)

            Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.wordsMet.toString(), style = MaterialTheme.typography.displayMedium, fontSize = 48.sp)
                Text(
                    stringResource(R.string.stats_of_total_met, state.wordsTotal),
                    fontSize = 13.sp,
                    color = KotobaTheme.palette.ink.copy(alpha = 0.6f),
                )
            }

            SectionLabel(stringResource(R.string.section_collection_by_level), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            SectionCard {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    state.tiers.forEachIndexed { index, tier ->
                        if (index != 0) Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(campaignSectionDisplayName(tier.tier), fontSize = 13.sp, color = KotobaTheme.palette.ink)
                            Text(
                                stringResource(R.string.stats_ratio, tier.met, tier.total),
                                fontSize = 13.sp,
                                color = KotobaTheme.palette.furigana,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (tier.total > 0) tier.met.toFloat() / tier.total else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            // Card-aware track color: MaterialTheme.colorScheme.surfaceVariant
                            // (the pre-redesign choice) equals the yoru dark theme's card
                            // background exactly, making the track invisible now that these
                            // rows sit inside a SectionCard instead of directly on the screen
                            // background.
                            trackColor = KotobaTheme.palette.ink.copy(alpha = 0.12f),
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_play_stats), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            StatGrid(
                cells = listOf(
                    StatCellData(state.bestScore.toString(), stringResource(R.string.stat_label_best_score), Matcha.copy(alpha = 0.4f)),
                    StatCellData(
                        state.accuracyPercent?.let { "$it%" } ?: "—",
                        stringResource(R.string.stat_label_accuracy),
                        Fuji.copy(alpha = 0.12f),
                    ),
                    StatCellData(state.runs.toString(), stringResource(R.string.stat_label_runs_played), Sora.copy(alpha = 0.3f)),
                    StatCellData(state.totalCleared.toString(), stringResource(R.string.stat_label_total_cleared), Sakura.copy(alpha = 0.3f)),
                ),
            )
        }
    }
}
