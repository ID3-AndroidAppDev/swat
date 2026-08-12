package com.example.kotobadrop.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.app.KotobaDropApplication
import com.example.kotobadrop.core.ui.SectionLabel
import com.example.kotobadrop.core.ui.campaignSectionDisplayName
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sora
import com.example.kotobadrop.core.ui.theme.seigahaPattern

// Design handoff (2026-07-20): "Reset history" is hidden by default now — the reset
// functionality itself stays, just the entry point is gated behind this flag.
private const val SHOW_RESET_HISTORY = false

@Composable
fun DictionaryScreen(onReviewClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val viewModel: DictionaryViewModel = viewModel(
        factory = DictionaryViewModel.factory(app.wordRepository, app.metWordRepository),
    )
    val state by viewModel.uiState.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.dictionary_reset_title)) },
            text = { Text(stringResource(R.string.dictionary_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.danger),
                ) { Text(stringResource(R.string.dictionary_reset_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink),
                ) { Text(stringResource(R.string.dictionary_reset_cancel)) }
            },
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        when {
            state.loaded && !state.hasAnyHistory -> {
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize().seigahaPattern().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.dictionary_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.dictionary_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize().seigahaPattern().padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.dictionary_title), style = MaterialTheme.typography.headlineMedium)
                            if (SHOW_RESET_HISTORY && state.hasAnyHistory) {
                                TextButton(
                                    onClick = { showResetConfirm = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.danger),
                                ) { Text(stringResource(R.string.dictionary_reset_button)) }
                            }
                        }

                        val query = state.query
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(KotobaTheme.palette.ink.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.dictionary_search_placeholder),
                                    fontSize = 14.sp,
                                    color = KotobaTheme.palette.ink.copy(alpha = 0.5f),
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = viewModel::setQuery,
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, color = KotobaTheme.palette.ink),
                                cursorBrush = SolidColor(KotobaTheme.palette.ink),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // A review run with only 1-2 words in the pool plays badly (the
                        // same word over and over) — require a handful before offering it.
                        if (state.missedCount >= 3) {
                            Button(
                                onClick = onReviewClick,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(40.dp),
                            ) {
                                Text(stringResource(R.string.dictionary_review_button), fontSize = 14.sp)
                            }
                        }
                    }

                    if (state.mostMissed.isNotEmpty()) {
                        item {
                            SectionLabel(
                                stringResource(R.string.dictionary_missed_section),
                                color = KotobaTheme.palette.danger,
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                            )
                        }
                        items(state.mostMissed, key = { "missed-${it.word.id}" }) { entry ->
                            DictionaryEntryCard(entry, highlight = true)
                        }
                    }

                    item {
                        SectionLabel(
                            stringResource(R.string.dictionary_all_section),
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                        )
                    }
                    if (state.loaded && state.allEntries.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.dictionary_no_results, state.query),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        items(state.allEntries, key = { it.word.id }) { entry ->
                            DictionaryEntryCard(entry, highlight = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryEntryCard(entry: DictionaryEntry, highlight: Boolean) {
    val borderColor = if (highlight) Sakura else KotobaTheme.palette.ink.copy(alpha = 0.08f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(KotobaTheme.palette.cardBackground, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        val romaji = entry.romaji
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(entry.word.surface, style = MaterialTheme.typography.titleLarge, color = KotobaTheme.palette.ink)
            Text(
                text = if (entry.word.kanaOnly) romaji else "${entry.word.reading} · $romaji",
                style = MaterialTheme.typography.bodyMedium,
                color = KotobaTheme.palette.furigana,
            )
        }
        Text(entry.word.meaning, style = MaterialTheme.typography.bodyMedium, color = KotobaTheme.palette.ink)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.dictionary_stats, entry.timesSeen, entry.timesCleared, entry.timesMissed),
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.timesMissed > 0) KotobaTheme.palette.danger else KotobaTheme.palette.ink.copy(alpha = 0.5f),
            )
            // Difficulty-tier chip (campaign section name doubles as the JLPT-ish label).
            Text(
                campaignSectionDisplayName(entry.word.tier),
                style = MaterialTheme.typography.labelSmall,
                color = KotobaTheme.palette.ink,
                modifier = Modifier
                    .background(Sora.copy(alpha = 0.35f), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
