package com.example.kotobadrop.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kotobadrop.R
import com.example.kotobadrop.app.KotobaDropApplication
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.Settings
import com.example.kotobadrop.core.ui.rememberReducedMotionEnabled
import com.example.kotobadrop.core.ui.theme.Akabeni
import com.example.kotobadrop.core.ui.theme.Fuji
import com.example.kotobadrop.core.ui.theme.IndigoPillFurigana
import com.example.kotobadrop.core.ui.theme.KotobaTheme
import com.example.kotobadrop.core.ui.theme.Matcha
import com.example.kotobadrop.core.ui.theme.Sakura
import com.example.kotobadrop.core.ui.theme.Sumi
import com.example.kotobadrop.core.ui.theme.seigahaPattern
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import com.example.kotobadrop.input.RomajiConverter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val BURST_PARTICLE_COUNT = 7

// Comfortably larger than any real falling word's rendered height, so a not-yet-measured
// word (the single frame between spawn and its first onSizeChanged) is guaranteed to sit
// fully off-screen above the play area rather than at some partially-visible offset.
private const val UNMEASURED_OFFSET_PX = 1000

private data class BurstParticle(val angleDeg: Float, val speedPx: Float, val sizePx: Float)

/** One in-flight burst (or reduced-motion flash) rendered by the Canvas in GameScreen. */
private class BurstVisual(
    val key: Int,
    val xPx: Float,
    val yPx: Float,
    val isFlash: Boolean,
    val particles: List<BurstParticle>,
) {
    val progress = Animatable(0f)
}

/**
 * Furigana should cover only the kanji portion of a word, not okurigana it already
 * shares in plain hiragana with the surface (e.g. 食べる/たべる -> "た", not the whole
 * "たべる") — JMdict has no per-kanji alignment (§10), but surface and reading always
 * agree character-for-character on any trailing hiragana, so trimming the longest
 * common suffix isolates the kanji-covering prefix without needing that alignment.
 */
internal fun furiganaFor(surface: String, reading: String): String {
    var end = 0
    while (end < surface.length && end < reading.length &&
        surface[surface.length - 1 - end] == reading[reading.length - 1 - end]
    ) {
        end++
    }
    return reading.dropLast(end)
}

@Composable
fun GameScreen(
    runConfig: RunConfig,
    onGameEnd: (score: Int, maxCombo: Int, cleared: Int, missed: Int, accuracy: Int, won: Boolean) -> Unit,
    onQuit: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    val vm: GameViewModel = viewModel(factory = GameViewModel.factory(app.wordRepository, app.metWordRepository, runConfig))
    val state by vm.state.collectAsState()
    // Per-frame word positions live outside GameState (see GameViewModel.FallingWord's doc):
    // this map is only ever read inside offset placement lambdas, so word movement re-runs
    // placement without recomposing anything.
    val yPositions by vm.yPositions.collectAsState()
    val focusRequester = remember { FocusRequester() }
    // rememberUpdatedState, not a plain val: the burst collector below lives in a
    // LaunchedEffect(Unit) that never restarts, so a plain captured Boolean would freeze
    // at whatever reduced-motion state was true when the run started. This keeps every
    // burst checking the live value, so toggling the setting mid-run takes effect immediately.
    val reducedMotion by rememberUpdatedState(rememberReducedMotionEnabled())
    val density = LocalDensity.current
    val bursts = remember { mutableStateListOf<BurstVisual>() }
    val palette = KotobaTheme.palette

    // SFX (§12 step 9): gated on the live soundEnabled setting — same rememberUpdatedState
    // reasoning as reducedMotion above, since the playback triggers live in long-lived
    // collectors/effects.
    val sounds = rememberGameSounds()
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = Settings())
    val soundEnabled by rememberUpdatedState(settings.soundEnabled)
    // Do-not-touch mode (settings toggle): each clear has a 10% chance to both swap the
    // clear SFX for the skebob sound and bump the skebob image's opacity by 10% while it
    // continuously fades back out — the other 90% of clears play the default sound with no
    // image change. Same rememberUpdatedState reasoning as soundEnabled — read inside the
    // burst collector.
    val doNotTouch by rememberUpdatedState(settings.doNotTouch)
    val skebobAlpha = remember { Animatable(0f) }

    // ── Frame-driven game loop, per CLAUDE.md §2/§12 ──────────────────────
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                    vm.tick(dt.coerceAtMost(0.1f))
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    // End of run: hold the final frame briefly so the fatal miss's reading flash (or the
    // final clear's burst) is actually seen before navigating — previously the "teaching
    // moment" was skipped for exactly the word that ended the run. tick() freezes itself
    // via isOver, so this is a clean freeze-frame, not continued play.
    LaunchedEffect(state.isOver) {
        if (state.isOver) {
            app.lastRunMissedWords = state.missedWords
            // A passed run already played the WIN chime at the moment the target was hit;
            // the descending game-over sound is only for runs that end without passing.
            if (soundEnabled && !state.targetReached) sounds.play(GameSound.GAME_OVER)
            delay(GameTuning.END_OF_RUN_HOLD_MILLIS)
            onGameEnd(state.score, state.maxCombo, state.cleared, state.missed, vm.accuracyPercent(), state.targetReached)
        }
    }

    // Campaign: the moment the target is met — the pass is locked in and play continues,
    // so the celebratory feedback belongs here, not at run end.
    LaunchedEffect(state.targetReached) {
        if (state.targetReached && soundEnabled) sounds.play(GameSound.WIN)
    }

    // Miss SFX: state.missed only ever increments, so each change is exactly one miss.
    LaunchedEffect(state.missed) {
        if (state.missed > 0 && soundEnabled) sounds.play(GameSound.MISS)
    }

    // Disabling the BasicTextField while paused (below) drops its focus and dismisses the
    // keyboard; simply re-enabling it on resume doesn't bring the keyboard back on its own,
    // so re-request focus explicitly whenever a run becomes active — both at start and on
    // every resume — or typed input after unpausing silently goes nowhere.
    LaunchedEffect(state.paused, state.isOver) {
        if (!state.paused && !state.isOver) {
            focusRequester.requestFocus()
        }
    }

    // ── Lifecycle auto-pause (§3): never let a backgrounded run keep losing lives.
    // ON_PAUSE, not ON_STOP: in multi-window/split-screen the activity loses focus without
    // ever stopping, and the run must pause there too.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Back press mid-run: pause and confirm instead of silently abandoning the run —
    // an accidental edge-swipe while typing fast shouldn't throw away a good run. While
    // the end-of-run hold is playing, back is swallowed (navigation is already queued).
    var showQuitConfirm by remember { mutableStateOf(false) }
    BackHandler {
        if (!state.isOver) {
            vm.pause()
            showQuitConfirm = true
        }
    }
    if (showQuitConfirm) {
        // Once a campaign target is met, backing out shouldn't read as "abandon" — the
        // pass is locked in, so the dialog offers finishing (score saved) instead.
        val finishing = state.targetReached
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text(stringResource(if (finishing) R.string.game_finish_title else R.string.game_quit_title)) },
            text = { Text(stringResource(if (finishing) R.string.game_finish_body else R.string.game_quit_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQuitConfirm = false
                        if (finishing) vm.finishLevel() else onQuit()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (finishing) palette.success else palette.danger,
                    ),
                ) { Text(stringResource(if (finishing) R.string.game_finish_confirm else R.string.game_quit_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showQuitConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.ink),
                ) { Text(stringResource(R.string.game_quit_cancel)) }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .seigahaPattern()
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!vm.mode.hasLives) {
                // Zen-ruleset runs (ZEN/REVIEW) have no lives — keep a placeholder so
                // SpaceBetween doesn't shift the HUD.
                Spacer(Modifier.size(1.dp))
            } else {
                val livesDescription = stringResource(R.string.game_lives_desc, state.lives)
                // Icon shapes, not ♥/♡ text: Android renders ♥ as a wide emoji (ignoring the
                // tint) but ♡ as a narrow text glyph, so losing a life used to change both the
                // row's width and its color. Fixed-size icons keep the row stable — a lost
                // life only swaps the fill for an outline.
                Row(modifier = Modifier.semantics { contentDescription = livesDescription }) {
                    repeat(vm.totalLives) { i ->
                        Icon(
                            imageVector = if (i < state.lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = Akabeni,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            val scoreText = stringResource(R.string.game_score_pt, state.score)
            val clearedText = runConfig.targetClears?.let { stringResource(R.string.game_cleared_target, state.cleared, it) }
                ?: stringResource(R.string.game_cleared_endless, state.cleared)
            val comboText = if (state.combo > 1) "  " + stringResource(R.string.game_combo, state.combo) else ""
            Text(
                text = "$scoreText  $clearedText$comboText",
                // Matcha once a campaign target is met — the quiet, persistent "you've
                // passed, everything from here is bonus" signal.
                color = if (state.targetReached) palette.success else palette.ink,
                fontSize = 16.sp,
            )
            TextButton(onClick = { vm.togglePause() }) {
                Text(stringResource(if (state.paused) R.string.game_resume else R.string.game_pause), color = palette.ink)
            }
        }

        HorizontalDivider(color = Akabeni.copy(alpha = 0.25f), thickness = 1.dp)

        // Measured render size per falling word (by id). Width clamps x so the word stays
        // fully on-screen; height anchors rendering to the word's BOTTOM edge — yFraction
        // is interpreted as the bottom, so the miss check (y >= 1) fires exactly when the
        // word touches the fail line instead of after the whole word has slid past it
        // (kanji words with furigana are tall enough that top-anchored rendering let them
        // visibly sink into the input area before registering as missed).
        val wordSizes = remember { mutableStateMapOf<Int, IntSize>() }

        // clipToBounds is the hard guarantee that nothing in the play area ever paints
        // outside it: Compose doesn't clip children to their parent by default, so a falling
        // word (or its pill shadow, or a petal burst) positioned above y=0 used to draw
        // straight over the HUD divider and the lives/score row. Offset arithmetic alone
        // can't be trusted to prevent that — a word is unmeasured on its first frame, and
        // a measured height can go stale when furigana appears or a match swaps the
        // layout — so the clip, not the math, is what enforces the boundary.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight

            // Sakura-petal burst on clear (§10, signature element), with a reduced-motion
            // fallback per the same section: a color flash instead of drifting particles.
            // Collected here (not GameState) — a burst is a one-shot event to consume once,
            // not persistent state to diff on every recomposition.
            LaunchedEffect(Unit) {
                var seq = 0
                vm.burstEvents.collect { event ->
                    val skebobTriggered = doNotTouch && Random.nextFloat() < 0.1f
                    if (soundEnabled) {
                        sounds.play(if (skebobTriggered) GameSound.SKEBOB else GameSound.CLEAR)
                    }
                    if (skebobTriggered) {
                        launch {
                            // +10% per clear (Animatable's mutex cancels the running fade,
                            // so rapid clears stack from the current, partially-faded value).
                            skebobAlpha.snapTo((skebobAlpha.value + 0.1f).coerceAtMost(1f))
                            skebobAlpha.animateTo(0f, tween(durationMillis = 3000, easing = LinearEasing))
                        }
                    }
                    val measuredSize = wordSizes[event.wordId]
                    val measuredWidth = measuredSize?.width ?: 0
                    // Mirror the falling-word render clamp exactly, so the burst appears
                    // where the word actually was — not where its unclamped fraction says.
                    val clampedLeft = (event.xFraction * wPx)
                        .coerceIn(0f, (wPx - measuredWidth).coerceAtLeast(0).toFloat())
                    val x = clampedLeft + measuredWidth / 2f
                    // yFraction is the word's bottom edge; center the burst on the word.
                    val y = event.yFraction * hPx - (measuredSize?.height ?: 0) / 2f
                    val pxPerDp = density.density
                    val visual = BurstVisual(
                        key = seq++,
                        xPx = x.coerceIn(0f, wPx.toFloat()),
                        yPx = y,
                        isFlash = reducedMotion,
                        particles = if (reducedMotion) {
                            emptyList()
                        } else {
                            List(BURST_PARTICLE_COUNT) {
                                BurstParticle(
                                    angleDeg = Random.nextFloat() * 360f,
                                    speedPx = (34f + Random.nextFloat() * 40f) * pxPerDp,
                                    sizePx = (7f + Random.nextFloat() * 5f) * pxPerDp,
                                )
                            }
                        },
                    )
                    bursts.add(visual)
                    launch {
                        visual.progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = if (reducedMotion) 260 else 550),
                        )
                        bursts.remove(visual)
                    }
                }
            }

            if (doNotTouch) {
                Image(
                    painter = painterResource(R.drawable.skebob),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.75f)
                        // Alpha read in the draw phase only — the fade never recomposes.
                        .graphicsLayer { alpha = skebobAlpha.value },
                )
            }

            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color = Akabeni,
                    start = Offset(0f, size.height - 2f),
                    end = Offset(size.width, size.height - 2f),
                    strokeWidth = 3f,
                )
                val gravityPx = 190f * density.density
                bursts.forEach { burst ->
                    val p = burst.progress.value
                    val alpha = (1f - p).coerceIn(0f, 1f)
                    if (burst.isFlash) {
                        drawCircle(
                            color = Matcha.copy(alpha = alpha * 0.55f),
                            radius = 30.dp.toPx(),
                            center = Offset(burst.xPx, burst.yPx),
                        )
                    } else {
                        burst.particles.forEach { particle ->
                            val rad = Math.toRadians(particle.angleDeg.toDouble())
                            val dx = (cos(rad) * particle.speedPx * p).toFloat()
                            val dy = (sin(rad) * particle.speedPx * p).toFloat() + gravityPx * p * p
                            val cx = burst.xPx + dx
                            val cy = burst.yPx + dy
                            rotate(degrees = particle.angleDeg, pivot = Offset(cx, cy)) {
                                drawOval(
                                    color = Sakura.copy(alpha = alpha),
                                    topLeft = Offset(cx - particle.sizePx / 2f, cy - particle.sizePx * 0.35f),
                                    size = Size(particle.sizePx, particle.sizePx * 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            state.words.forEach { falling ->
                val word = falling.word

                // Matched word (the one the current input completes) gets a solid indigo
                // pill instead of a tinted-on-tinted highlight — the previous sora-on-sora
                // treatment was too low contrast to read at typing speed.
                val isMatched = falling.isHighlighted && !falling.isFlashing
                val textColor = when {
                    falling.isFlashing -> Akabeni
                    isMatched -> Color.White
                    else -> palette.ink
                }
                val furiganaColor = if (isMatched) IndigoPillFurigana else palette.furigana
                val pillShape = RoundedCornerShape(6.dp)

                Column(
                    modifier = Modifier
                        // Positions (and measured widths) are read inside the placement
                        // lambda: per-frame movement invalidates placement only, never
                        // composition — the actual perf win of the yPositions split.
                        .offset {
                            val size = wordSizes[falling.id]
                            val x = (falling.xFraction * wPx).roundToInt()
                                .coerceIn(0, (wPx - (size?.width ?: 0)).coerceAtLeast(0))
                            // Bottom-anchored: subtract the word's height so yFraction = 1
                            // puts the bottom on the fail line. A word isn't measured yet on
                            // the very first frame after spawning (onSizeChanged hasn't fired)
                            // — falling back to a height of 0 in that gap used to render it
                            // flush against the top boundary/HUD divider for that frame
                            // instead of hidden above it. Push it safely off-screen above
                            // until a real height is known.
                            // A negative y is fine (and wanted — the word slides in from above
                            // the line rather than popping in flush against it): the play area
                            // clips its children, so the part above the top edge is simply not
                            // painted instead of spilling into the HUD.
                            val y = if (size == null) {
                                -UNMEASURED_OFFSET_PX
                            } else {
                                ((yPositions[falling.id] ?: 0f) * hPx).roundToInt() - size.height
                            }
                            IntOffset(x, y)
                        }
                        .onSizeChanged { size ->
                            wordSizes[falling.id] = size
                            // Prune measurements for long-gone words once the map grows —
                            // not on every removal, so a just-cleared word's size is still
                            // there when its burst event is consumed.
                            if (wordSizes.size > 30) {
                                val liveIds = state.words.mapTo(HashSet()) { it.id }
                                liveIds.add(falling.id)
                                wordSizes.keys.retainAll(liveIds)
                            }
                        }
                        .then(
                            if (isMatched) {
                                Modifier
                                    .shadow(elevation = 8.dp, shape = pillShape, ambientColor = Fuji.copy(alpha = 0.4f), spotColor = Fuji.copy(alpha = 0.4f))
                                    .background(Fuji, pillShape)
                            } else {
                                Modifier.background(Color.Transparent, shape = MaterialTheme.shapes.extraSmall)
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (!word.kanaOnly) {
                        if (falling.isFlashing) {
                            Text(text = word.reading, color = Akabeni, fontSize = 14.sp)
                        } else if (vm.furigana) {
                            Text(text = furiganaFor(word.surface, word.reading), color = furiganaColor, fontSize = 16.sp)
                        }
                    }
                    Text(
                        text = word.surface,
                        color = textColor,
                        fontSize = 26.sp,
                        fontWeight = if (falling.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            if (state.paused) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xCC1C1A1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.game_paused), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { vm.togglePause() },
                            colors = ButtonDefaults.buttonColors(containerColor = Matcha),
                        ) {
                            Text(stringResource(R.string.game_resume), color = Sumi, fontWeight = FontWeight.SemiBold)
                        }
                        if (state.targetReached || !vm.mode.hasLives) {
                            Button(
                                onClick = { if (state.targetReached) vm.finishLevel() else vm.finishRun() },
                                colors = ButtonDefaults.buttonColors(containerColor = Sakura),
                            ) {
                                Text(
                                    stringResource(if (state.targetReached) R.string.game_finish_button else R.string.game_finish_run),
                                    color = Sumi,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.ink.copy(alpha = 0.08f), thickness = 1.dp)

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val kanaDisplay = when {
                state.buffer.isEmpty() -> "—"
                vm.inputMode == InputMode.IME -> state.buffer
                else -> RomajiConverter.toKana(state.buffer)
            }
            Text(
                text = kanaDisplay,
                color = when {
                    state.deadInput -> Akabeni
                    state.buffer.isNotEmpty() -> palette.furigana
                    else -> palette.ink.copy(alpha = 0.25f)
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )

            val fieldModifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .background(
                    color = if (state.deadInput) Akabeni.copy(alpha = 0.12f) else palette.fieldBackground,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
            val fieldTextStyle = TextStyle(fontSize = 18.sp, color = if (state.deadInput) Akabeni else palette.ink)

            if (vm.inputMode == InputMode.IME) {
                // IME fallback path (§4/§12 step 10): a TextFieldValue field (not the plain
                // String overload above) so the composing region is visible via `.composition`
                // — matching only fires once it's null, i.e. the IME has actually committed
                // the text, since composing-region behavior is inconsistent across keyboards.
                // Local state, not state.buffer directly: only reset to empty when the
                // ViewModel clears the buffer (a match or a miss-driven clear) — never
                // overwritten mid-composition.
                var imeFieldValue by remember { mutableStateOf(TextFieldValue()) }
                LaunchedEffect(state.buffer) {
                    if (state.buffer.isEmpty() && imeFieldValue.text.isNotEmpty()) {
                        imeFieldValue = TextFieldValue()
                    }
                }
                BasicTextField(
                    value = imeFieldValue,
                    onValueChange = { new ->
                        imeFieldValue = new
                        if (new.composition == null) vm.onImeInput(new.text)
                    },
                    enabled = !state.isOver && !state.paused,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.None),
                    textStyle = fieldTextStyle,
                    modifier = fieldModifier,
                )
            } else {
                BasicTextField(
                    value = state.buffer,
                    onValueChange = { vm.onInput(it) },
                    enabled = !state.isOver && !state.paused,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrectEnabled = false,
                    ),
                    textStyle = fieldTextStyle,
                    modifier = fieldModifier,
                )
            }
        }
    }
}
