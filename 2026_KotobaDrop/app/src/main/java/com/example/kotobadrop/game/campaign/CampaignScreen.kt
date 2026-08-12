package com.example.kotobadrop.game.campaign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.app.KotobaDropApplication
import com.example.kotobadrop.core.ui.PadlockIcon
import com.example.kotobadrop.core.ui.campaignSectionDisplayName
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Matcha
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sumi
import com.example.kotobadrop.core.ui.theme.seigahaPattern

@Composable
fun CampaignScreen(onLevelSelected: (levelId: String) -> Unit) {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val viewModel: CampaignViewModel = viewModel(factory = CampaignViewModel.factory(app.campaignRepository))
    val sections by viewModel.sections.collectAsState()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().seigahaPattern().padding(horizontal = 24.dp),
        ) {
            item { Text(stringResource(R.string.campaign_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp)) }

            items(sections) { sectionState ->
                Column(modifier = Modifier.padding(bottom = 22.dp)) {
                    Text(
                        campaignSectionDisplayName(sectionState.section.tier),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KotobaTheme.palette.ink,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sectionState.levels.forEach { levelState ->
                            LevelCell(levelState, onClick = { onLevelSelected(levelState.level.id) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelCell(levelState: LevelUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val enabled = levelState.status != LevelStatus.LOCKED
    val a11yLabel = stringResource(
        R.string.campaign_level_a11y,
        levelState.level.indexInSection + 1,
        levelState.level.targetClears,
    )
    val shape = RoundedCornerShape(12.dp)
    val cellModifier = modifier
        .aspectRatio(1f)
        .let { m ->
            if (levelState.status == LevelStatus.PLAYABLE) {
                m.shadow(elevation = 6.dp, shape = shape, ambientColor = Sakura.copy(alpha = 0.6f), spotColor = Sakura.copy(alpha = 0.6f))
            } else {
                m
            }
        }
        .background(
            when (levelState.status) {
                LevelStatus.COMPLETED -> Matcha
                LevelStatus.PLAYABLE -> Sakura
                LevelStatus.LOCKED -> KotobaTheme.palette.ink.copy(alpha = 0.06f)
            },
            shape,
        )
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .semantics { contentDescription = a11yLabel }

    Column(
        modifier = cellModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (levelState.status) {
            LevelStatus.LOCKED -> {
                PadlockIcon(tint = KotobaTheme.palette.ink.copy(alpha = 0.3f))
            }
            LevelStatus.COMPLETED -> {
                Text("${levelState.level.indexInSection + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Sumi)
                levelState.bestScore?.let { best ->
                    Text(stringResource(R.string.campaign_level_best, best), fontSize = 9.sp, color = Sumi)
                }
            }
            LevelStatus.PLAYABLE -> {
                Text("${levelState.level.indexInSection + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Sumi)
                Text(stringResource(R.string.campaign_level_target, levelState.level.targetClears), fontSize = 9.sp, color = Sumi)
            }
        }
    }
}
