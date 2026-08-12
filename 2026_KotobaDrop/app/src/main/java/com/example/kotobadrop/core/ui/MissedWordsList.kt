package com.example.kotobadrop.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kotobadrop.R
import com.example.kotobadrop.core.data.WordEntity
import com.example.kotobadrop.core.ui.theme.KotobaTheme

/**
 * The run's missed words on a results screen — the durable version of the in-game 1.5s
 * miss flash, so a learner can actually study what beat them. At most `lives` entries
 * (2–3), so no scrolling/limiting concerns. Renders nothing when the run had no misses.
 * Kana-only words skip the separate reading, same convention as the game and dictionary.
 * Left-aligned rows (word + reading vs. meaning right-aligned), hairline-divided.
 */
@Composable
fun MissedWordsList(words: List<WordEntity>, modifier: Modifier = Modifier) {
    if (words.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.run_missed_words_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = KotobaTheme.palette.danger,
        )
        words.forEachIndexed { index, word ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 10.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(word.surface, fontSize = 16.sp, color = KotobaTheme.palette.ink)
                    if (!word.kanaOnly) {
                        Text(
                            word.reading,
                            fontSize = 13.sp,
                            color = KotobaTheme.palette.furigana,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Text(word.meaning, fontSize = 13.sp, color = KotobaTheme.palette.ink.copy(alpha = 0.55f))
            }
            if (index != words.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = KotobaTheme.palette.ink.copy(alpha = 0.08f),
                    thickness = 1.dp,
                )
            }
        }
    }
}
