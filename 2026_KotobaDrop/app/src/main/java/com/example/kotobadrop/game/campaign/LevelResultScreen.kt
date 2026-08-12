package com.example.kotobadrop.game.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kotobadrop.R
import com.example.kotobadrop.core.data.WordEntity
import com.example.kotobadrop.core.ui.MissedWordsList
import com.example.kotobadrop.core.ui.StatCellData
import com.example.kotobadrop.core.ui.StatGrid
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Matcha
import com.example.kotobadrop.core.ui.theme.Sora
import com.example.kotobadrop.core.ui.theme.seigahaPattern

@Composable
fun LevelResultScreen(
    won: Boolean,
    score: Int,
    cleared: Int,
    targetClears: Int,
    hasNextLevel: Boolean,
    missedWords: List<WordEntity>,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onLevelSelect: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .seigahaPattern()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(if (won) R.string.level_result_clear else R.string.level_result_failed).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (won) KotobaTheme.palette.success else KotobaTheme.palette.danger,
            )
            Text(
                score.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontSize = 56.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.stat_points_caption),
                fontSize = 13.sp,
                color = KotobaTheme.palette.ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )

            if (won) {
                val stars = starsForClear(cleared, targetClears)
                StatGrid(
                    cells = listOf(
                        StatCellData(
                            stringResource(R.string.stats_ratio, cleared, targetClears),
                            stringResource(R.string.stat_label_cleared),
                            Matcha.copy(alpha = 0.4f),
                        ),
                        StatCellData("★".repeat(stars), stringResource(R.string.stat_label_stars), Sora.copy(alpha = 0.3f)),
                    ),
                    modifier = Modifier.padding(top = 28.dp),
                )
            }

            MissedWordsList(missedWords, modifier = Modifier.padding(top = 24.dp))

            if (won && hasNextLevel) {
                Button(
                    onClick = onNextLevel,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).height(44.dp),
                ) { Text(stringResource(R.string.level_result_next)) }
            }
            if (!won) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp).height(44.dp),
                ) { Text(stringResource(R.string.level_result_retry)) }
            }
            TextButton(
                onClick = onLevelSelect,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink.copy(alpha = 0.6f)),
            ) { Text(stringResource(R.string.level_result_level_select)) }
        }
    }
}
