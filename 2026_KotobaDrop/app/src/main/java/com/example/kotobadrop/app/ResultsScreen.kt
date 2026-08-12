package com.example.kotobadrop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.example.kotobadrop.core.ui.theme.Fuji
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Matcha
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sora
import com.example.kotobadrop.core.ui.theme.seigahaPattern

@Composable
fun ResultsScreen(
    score: Int,
    maxCombo: Int,
    cleared: Int,
    missed: Int,
    accuracy: Int,
    best: Int,
    newRecord: Boolean,
    missedWords: List<WordEntity>,
    onPlayAgain: () -> Unit,
    onHomeClick: () -> Unit,
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
            if (newRecord) {
                Text(
                    stringResource(R.string.results_new_record).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KotobaTheme.palette.success,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(score.toString(), style = MaterialTheme.typography.displayMedium, fontSize = 56.sp)
            Text(
                stringResource(R.string.stat_points_caption),
                fontSize = 13.sp,
                color = KotobaTheme.palette.ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )

            StatGrid(
                cells = listOf(
                    StatCellData(cleared.toString(), stringResource(R.string.stat_label_cleared), Matcha.copy(alpha = 0.4f)),
                    StatCellData(missed.toString(), stringResource(R.string.stat_label_missed), Sakura.copy(alpha = 0.3f)),
                    StatCellData(maxCombo.toString(), stringResource(R.string.stat_label_max_combo), Sora.copy(alpha = 0.3f)),
                    StatCellData("$accuracy%", stringResource(R.string.stat_label_accuracy), Fuji.copy(alpha = 0.12f)),
                ),
                modifier = Modifier.padding(top = 28.dp),
            )

            if (best >= 0) {
                Text(
                    stringResource(R.string.results_best, best),
                    fontSize = 13.sp,
                    color = KotobaTheme.palette.ink.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }

            MissedWordsList(missedWords, modifier = Modifier.padding(top = 24.dp))

            Button(
                onClick = onPlayAgain,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).height(44.dp),
            ) { Text(stringResource(R.string.results_play_again)) }
            TextButton(
                onClick = onHomeClick,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink.copy(alpha = 0.6f)),
            ) { Text(stringResource(R.string.results_home)) }
        }
    }
}
