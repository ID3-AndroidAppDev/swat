package com.example.kotobadrop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.app.KotobaDropApplication
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.ThemePreference
import com.example.kotobadrop.core.model.UiLanguage
import com.example.kotobadrop.core.ui.SectionCard
import com.example.kotobadrop.core.ui.SectionLabel
import com.example.kotobadrop.core.ui.SelectableChip
import com.example.kotobadrop.core.ui.inputModeLabel
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.seigahaPattern
import com.example.kotobadrop.core.ui.themePreferenceLabel

@Composable
fun SettingsScreen(onCreditsClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app.settingsRepository, app.metWordRepository, app.campaignRepository))
    val settings by viewModel.settings.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetProgress()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.danger),
                ) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink),
                ) { Text(stringResource(R.string.dictionary_reset_cancel)) }
            },
        )
    }

    val divider = @Composable { HorizontalDivider(color = KotobaTheme.palette.ink.copy(alpha = 0.06f), thickness = 1.dp) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().seigahaPattern().padding(24.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

            SectionLabel(stringResource(R.string.section_general), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_language_label), fontSize = 15.sp)
                    TextButton(
                        onClick = {
                            val next = if (settings.uiLanguage == UiLanguage.EN) UiLanguage.JA else UiLanguage.EN
                            viewModel.setUiLanguage(next)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink.copy(alpha = 0.6f)),
                    ) {
                        // Language names are shown in their own script regardless of the
                        // current UI language, like any language picker — not translated.
                        Text(if (settings.uiLanguage == UiLanguage.EN) "English" else "日本語", fontSize = 13.sp)
                    }
                }
                divider()
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.settings_theme_label), fontSize = 15.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            SelectableChip(
                                selected = settings.themePreference == pref,
                                onClick = { viewModel.setThemePreference(pref) },
                                label = themePreferenceLabel(pref),
                            )
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_gameplay), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            SectionCard {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.settings_input_mode_label), fontSize = 15.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InputMode.entries.forEach { mode ->
                            SelectableChip(
                                selected = settings.inputMode == mode,
                                onClick = { viewModel.setInputMode(mode) },
                                label = inputModeLabel(mode),
                            )
                        }
                    }
                }
                divider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.label_furigana), fontSize = 15.sp)
                    Switch(checked = settings.furigana, onCheckedChange = viewModel::setFurigana)
                }
            }

            SectionLabel(stringResource(R.string.section_sound_extras), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_sound_label), fontSize = 15.sp)
                    Switch(checked = settings.soundEnabled, onCheckedChange = viewModel::setSoundEnabled)
                }
                divider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_do_not_touch_label), fontSize = 15.sp)
                    Switch(checked = settings.doNotTouch, onCheckedChange = viewModel::setDoNotTouch)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onCreditsClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.ink.copy(alpha = 0.6f)),
                ) { Text(stringResource(R.string.settings_credits), fontSize = 13.sp) }
                TextButton(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = KotobaTheme.palette.danger),
                ) { Text(stringResource(R.string.settings_reset_button), fontSize = 13.sp) }
            }
        }
    }
}
