package com.example.kotobadrop.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kotobadrop.R
import com.example.kotobadrop.core.ui.theme.Akabeni
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sumi
import com.example.kotobadrop.core.ui.theme.seigahaPattern

private data class TutorialSection(val titleRes: Int, val bodyRes: Int, val accented: Boolean = false)

// Order mirrors how a new player actually encounters each mechanic: what falls and what
// to type, how typing feedback looks (including the one accented section — dead input —
// since seeing it in the same red tint the real game uses reinforces the point), then
// consequences (misses/lives), scoring, and finally the settings/modes that shape a run.
private val SECTIONS = listOf(
    TutorialSection(R.string.tutorial_basics_title, R.string.tutorial_basics_body),
    TutorialSection(R.string.tutorial_typing_title, R.string.tutorial_typing_body),
    TutorialSection(R.string.tutorial_spelling_title, R.string.tutorial_spelling_body),
    TutorialSection(R.string.tutorial_dead_input_title, R.string.tutorial_dead_input_body, accented = true),
    TutorialSection(R.string.tutorial_misses_title, R.string.tutorial_misses_body),
    TutorialSection(R.string.tutorial_scoring_title, R.string.tutorial_scoring_body),
    TutorialSection(R.string.tutorial_furigana_title, R.string.tutorial_furigana_body),
    TutorialSection(R.string.tutorial_input_mode_title, R.string.tutorial_input_mode_body),
    TutorialSection(R.string.tutorial_modes_title, R.string.tutorial_modes_body),
    TutorialSection(R.string.tutorial_pause_title, R.string.tutorial_pause_body),
)

@Composable
fun TutorialScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .seigahaPattern()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(stringResource(R.string.tutorial_title), style = MaterialTheme.typography.headlineMedium)

            Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                SECTIONS.forEachIndexed { index, section ->
                    StepCard(number = index + 1, section = section)
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, section: TutorialSection) {
    val cardColor = if (section.accented) Akabeni.copy(alpha = 0.15f) else KotobaTheme.palette.cardBackground
    val badgeColor = if (section.accented) Akabeni else Sakura
    val badgeTextColor = if (section.accented) Color.White else Sumi
    val titleColor = if (section.accented) KotobaTheme.palette.danger else KotobaTheme.palette.ink
    val bodyColor = if (section.accented) KotobaTheme.palette.danger.copy(alpha = 0.85f) else KotobaTheme.palette.ink.copy(alpha = 0.75f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(cardColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.size(26.dp).background(badgeColor, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(number.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = badgeTextColor)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(stringResource(section.titleRes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(
                stringResource(section.bodyRes),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = bodyColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
