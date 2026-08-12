package com.example.kotobadrop.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kotobadrop.R
import com.example.kotobadrop.core.ui.SectionCard
import com.example.kotobadrop.core.ui.SectionLabel
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.seigahaPattern

@Composable
fun CreditsScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().seigahaPattern().padding(24.dp),
        ) {
            Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.headlineMedium)

            SectionLabel(stringResource(R.string.section_word_data), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            SectionCard {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(stringResource(R.string.credits_word_data_body), fontSize = 14.sp, lineHeight = 21.sp, color = KotobaTheme.palette.ink)
                    Text(
                        stringResource(R.string.credits_word_data_license),
                        fontSize = 12.sp,
                        color = KotobaTheme.palette.ink.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_typefaces), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            SectionCard {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(stringResource(R.string.credits_fonts_body), fontSize = 14.sp, lineHeight = 21.sp, color = KotobaTheme.palette.ink)
                    Text(
                        stringResource(R.string.credits_fonts_license),
                        fontSize = 12.sp,
                        color = KotobaTheme.palette.ink.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
