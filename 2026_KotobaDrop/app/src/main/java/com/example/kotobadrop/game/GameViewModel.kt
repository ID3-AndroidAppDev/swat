package com.example.kotobadrop.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.kotobadrop.core.data.MetWordEntity
import com.example.kotobadrop.core.data.MetWordRepository
import com.example.kotobadrop.core.data.WordEntity
import com.example.kotobadrop.core.data.WordRepository
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.SpeedDifficulty
import com.example.kotobadrop.input.ImeMatcher
import com.example.kotobadrop.input.RomajiConverter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * STANDARD: the normal pool/lives rules (Endless and every Campaign level).
 * REVIEW: pool restricted to the player's missed words (tier filter ignored) — the
 *   Dictionary "practice missed words" run. Zen by default: a drill shouldn't punish
 *   the misses it exists to fix.
 * ZEN: the no-pressure ruleset — no lives, no game over, run ends only via the
 *   pause-overlay Finish action.
 * REVIEW/ZEN runs are never saved to the scores table (they'd pollute best-score history).
 */
enum class RunMode {
    STANDARD, REVIEW, ZEN;

    /** Lives (and therefore game over) only exist in STANDARD runs. */
    val hasLives: Boolean get() = this == STANDARD
}

/**
 * A run's parameters, supplied by whoever starts the game (Endless setup, a Campaign
 * level, or the Dictionary review entry) — GameViewModel itself has no notion of where
 * these came from. `targetClears == null` means Endless-style: play until game over, no
 * win condition. Non-null: reaching it marks the run passed ([GameState.targetReached])
 * but play CONTINUES — the run ends at game over like Endless, or early via
 * [finishLevel], and the pass sticks either way.
 */
data class RunConfig(
    val speed: SpeedDifficulty,
    val tierMin: Int,
    val tierMax: Int,
    val furigana: Boolean,
    val targetClears: Int?,
    val inputMode: InputMode,
    val lives: Int,
    val mode: RunMode,
)

class GameViewModel(
    private val wordRepository: WordRepository,
    private val metWordRepository: MetWordRepository,
    private val runConfig: RunConfig,
) : ViewModel() {

    /**
     * Static per-word render data. The word's y-position and flash countdown deliberately
     * do NOT live here (or anywhere in GameState): they change every frame, and keeping
     * them in the composed state would recompose the whole screen at frame rate. Positions
     * are published through [yPositions] instead — the screen reads them inside its
     * placement lambda, so per-frame movement costs placement only, no recomposition.
     * GameState.words therefore only changes on real events (spawn/clear/miss/highlight).
     */
    data class FallingWord(
        val id: Int,
        val word: WordEntity,
        val xFraction: Float,
        val isHighlighted: Boolean = false,
        val isFlashing: Boolean = false,
    ) {
        val isActive: Boolean get() = !isFlashing
    }

    data class GameState(
        val words: List<FallingWord> = emptyList(),
        val buffer: String = "",
        val lives: Int = GameTuning.DEFAULT_LIVES,
        val score: Int = 0,
        val combo: Int = 0,
        val maxCombo: Int = 0,
        val cleared: Int = 0,
        val missed: Int = 0,
        val missedWords: List<WordEntity> = emptyList(),
        val deadInput: Boolean = false,
        val paused: Boolean = false,
        val gameOver: Boolean = false,
        /** Campaign: target met — the pass is locked in, but the run keeps going. Sticky. */
        val targetReached: Boolean = false,
        /** Campaign: the player chose to end a passed run early via [finishLevel]. */
        val levelComplete: Boolean = false,
    ) {
        val isOver: Boolean get() = gameOver || levelComplete
    }

    val furigana: Boolean get() = runConfig.furigana
    val inputMode: InputMode get() = runConfig.inputMode
    val totalLives: Int get() = runConfig.lives
    val mode: RunMode get() = runConfig.mode

    /** A cleared word's last position, for the screen to spawn a sakura-petal burst at (§10, step 9). */
    data class BurstEvent(val wordId: Int, val xFraction: Float, val yFraction: Float)

    private val _state = MutableStateFlow(GameState(lives = runConfig.lives))
    val state: StateFlow<GameState> = _state.asStateFlow()

    // Channel, not part of GameState: a burst is a one-shot event, not persistent state to
    // diff against on every recomposition — the screen consumes each one exactly once.
    private val _burstEvents = Channel<BurstEvent>(Channel.BUFFERED)
    val burstEvents = _burstEvents.receiveAsFlow()

    /** id -> yFraction, republished every tick — see [FallingWord]'s doc for why it's separate. */
    private val _yPositions = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val yPositions: StateFlow<Map<Int, Float>> = _yPositions.asStateFlow()

    private var pool: List<WordEntity> = emptyList()
    private var nextId = 0
    private var spawnTimer = 0f
    private var elapsedSeconds = 0f
    private var lastClearElapsedSeconds = 0f

    // Frame-rate word state, mutated in place by tick() and mirrored into _yPositions.
    private val ys = HashMap<Int, Float>()
    private val flashTimers = HashMap<Int, Float>()

    // Word ids that already spawned this run — their spawn weight is damped so a word
    // cleared or missed seconds ago doesn't immediately come back (GameTuning.REPEAT_DAMP).
    private val seenThisRun = HashSet<Int>()

    // Snapshotted once at run start — adaptive weighting is meant to shift spawning across
    // runs ("missed several times -> spawns more often next run"), not live within one.
    private var metWordSnapshot: Map<Int, MetWordEntity> = emptyMap()

    init {
        viewModelScope.launch {
            metWordSnapshot = metWordRepository.getAll().associateBy { it.wordId }
            pool = if (runConfig.mode == RunMode.REVIEW) {
                // The whole point of a review run: only words the player has missed,
                // regardless of tier. Adaptive weighting still applies within the pool.
                val missedIds = metWordSnapshot.values.filter { it.timesMissed > 0 }.map { it.wordId }
                wordRepository.getWordsByIds(missedIds)
            } else {
                wordRepository.getWords().filter { it.tier in runConfig.tierMin..runConfig.tierMax }
            }
        }
    }

    /**
     * Which matching predicate to use — chosen once per tick from [runConfig.inputMode], not
     * per call, but written as a method reference so every prefix check below (miss-buffer
     * clearing, dead-input, highlighting) shares one branch point rather than three. For
     * ROMAJI this is exactly [RomajiConverter.isPrefixOfReading] — behaviorally identical to
     * before step 10 (§13: "the romaji path is primary; IME quirks must never block the
     * main loop").
     */
    private fun isPrefixOfReading(buffer: String, reading: String): Boolean =
        if (runConfig.inputMode == InputMode.IME) ImeMatcher.isPrefixOfReading(buffer, reading)
        else RomajiConverter.isPrefixOfReading(buffer, reading)

    /**
     * Looser than [isPrefixOfReading] — shares a non-empty leading run rather than being
     * a full prefix. Used only to decide whether a miss should clear the buffer: a typo
     * that derailed partway through the missed word ("dead" input by the time it fell)
     * still counts as "this buffer was for this word," per user request.
     */
    private fun sharesPrefixWithReading(buffer: String, reading: String): Boolean =
        if (runConfig.inputMode == InputMode.IME) ImeMatcher.sharesPrefixWithReading(buffer, reading)
        else RomajiConverter.sharesPrefixWithReading(buffer, reading)

    private fun isEqualToReading(buffer: String, reading: String): Boolean =
        if (runConfig.inputMode == InputMode.IME) ImeMatcher.isEqualToReading(buffer, reading)
        else RomajiConverter.isEqualToReading(buffer, reading)

    // ROMAJI: a buffer only counts as "has kana content" once toKana() actually produced
    // some (gates dead-input so a bare latin partial like "k" isn't flagged red yet). IME:
    // committed text is already kana/katakana whenever non-empty, so no conversion needed.
    private fun hasKanaContent(buffer: String): Boolean =
        if (runConfig.inputMode == InputMode.IME) buffer.isNotEmpty()
        else RomajiConverter.toKana(buffer).any { it.code in 0x3040..0x30FF }

    /** Called every frame from the withFrameNanos loop in the screen composable. */
    fun tick(dt: Float) {
        val s = _state.value
        if (s.paused || s.isOver || pool.isEmpty()) return

        elapsedSeconds += dt
        val ramp = GameTuning.rampFactor(elapsedSeconds)
        val spawnInterval = GameTuning.spawnIntervalSeconds(runConfig.speed) * ramp

        spawnTimer -= dt
        var lives = s.lives
        var missed = s.missed
        var combo = s.combo
        var missedWords = s.missedWords
        var bufferShouldClear = false
        var structuralChange = false
        var words = s.words

        // Expire finished miss flashes.
        val expired = HashSet<Int>()
        val flashIter = flashTimers.entries.iterator()
        while (flashIter.hasNext()) {
            val entry = flashIter.next()
            val t = entry.value - dt
            if (t <= 0f) {
                expired.add(entry.key)
                flashIter.remove()
            } else {
                entry.setValue(t)
            }
        }
        if (expired.isNotEmpty()) {
            expired.forEach { ys.remove(it) }
            words = words.filterNot { it.id in expired }
            structuralChange = true
        }

        // Advance falling words; collect any that crossed the fail line this frame.
        val newlyMissed = ArrayList<FallingWord>(0)
        for (word in words) {
            if (!word.isActive) continue
            val fallSeconds = GameTuning.fallDurationSeconds(runConfig.speed, word.word.reading.length) * ramp
            val y = (ys[word.id] ?: 0f) + dt / fallSeconds
            if (y >= 1f) newlyMissed.add(word) else ys[word.id] = y
        }
        if (newlyMissed.isNotEmpty()) {
            structuralChange = true
            for (word in newlyMissed) {
                if (runConfig.mode.hasLives) lives--
                missed++
                combo = 0
                missedWords = missedWords + word.word
                if (sharesPrefixWithReading(s.buffer, word.word.reading)) {
                    bufferShouldClear = true
                }
                ys[word.id] = 1f
                flashTimers[word.id] = GameTuning.MISS_FLASH_SECONDS
                val missedWordId = word.word.id
                viewModelScope.launch { metWordRepository.logMissed(missedWordId) }
            }
            val missedIds = newlyMissed.mapTo(HashSet()) { it.id }
            words = words.map { if (it.id in missedIds) it.copy(isFlashing = true) else it }
        }

        val activeWords = words.filter { it.isActive }
        if (spawnTimer <= 0f && activeWords.size < GameTuning.MAX_ACTIVE_WORDS) {
            spawnTimer = spawnInterval
            val candidate = pickCandidate(activeWords)
            if (candidate != null) {
                val spawned = FallingWord(
                    id = nextId++,
                    word = candidate,
                    xFraction = pickSpawnX(activeWords),
                )
                ys[spawned.id] = 0f
                seenThisRun.add(candidate.id)
                words = words + spawned
                structuralChange = true
                viewModelScope.launch { metWordRepository.logSeen(candidate.id) }
            }
        }

        _yPositions.value = HashMap(ys)

        // GameState only changes on events — highlights/dead-input depend on the buffer and
        // the word set, both of which are stable between events, so quiet frames skip the
        // state emission (and with it, recomposition) entirely.
        if (!structuralChange && !bufferShouldClear) return

        val newBuffer = if (bufferShouldClear) "" else s.buffer
        val nowActive = words.filter { it.isActive }

        val deadInput = newBuffer.isNotEmpty() &&
            hasKanaContent(newBuffer) &&
            nowActive.none { isPrefixOfReading(newBuffer, it.word.reading) }

        val withHighlights = words.map { word ->
            if (word.isActive) {
                word.copy(
                    isHighlighted = newBuffer.isNotEmpty() &&
                        isPrefixOfReading(newBuffer, word.word.reading)
                )
            } else word
        }

        val gameOver = lives <= 0
        _state.value = s.copy(
            words = withHighlights,
            buffer = newBuffer,
            lives = lives.coerceAtLeast(0),
            missed = missed,
            missedWords = missedWords,
            combo = combo,
            deadInput = deadInput,
            gameOver = gameOver,
        )
    }

    /** Called whenever the TextField value changes. */
    fun onInput(newRomaji: String) {
        val s = _state.value
        if (s.isOver || s.paused) return

        val activeWords = s.words.filter { it.isActive }

        // Exact match → clear the word. Backstop (§3): if multiple match, clear the
        // one closest to the fail line.
        val matched = activeWords
            .filter { RomajiConverter.isEqualToReading(newRomaji, it.word.reading) }
            .maxByOrNull { ys[it.id] ?: 0f }

        if (matched != null) {
            clearWord(s, matched)
            return
        }

        val kana = RomajiConverter.toKana(newRomaji)
        val deadInput = newRomaji.isNotEmpty() &&
            kana.any { it.code in 0x3040..0x30FF } &&
            activeWords.none { RomajiConverter.isPrefixOfReading(newRomaji, it.word.reading) }

        val updatedWords = s.words.map { word ->
            if (word.isActive) {
                word.copy(
                    isHighlighted = newRomaji.isNotEmpty() &&
                        RomajiConverter.isPrefixOfReading(newRomaji, word.word.reading)
                )
            } else word
        }

        _state.value = s.copy(
            words = updatedWords,
            buffer = newRomaji,
            deadInput = deadInput,
        )
    }

    /**
     * IME fallback path (§4/§12 step 10): called with already-committed hiragana/katakana
     * text only (the screen withholds calls while the IME has an active composing region —
     * "match on committed text only," since composing-region behavior is inconsistent
     * across keyboards). Structurally mirrors [onInput] but is a separate function, not a
     * shared code path, so romaji-path behavior can never be affected by IME quirks (§13).
     */
    fun onImeInput(committedText: String) {
        val s = _state.value
        if (s.isOver || s.paused) return

        val activeWords = s.words.filter { it.isActive }

        val matched = activeWords
            .filter { ImeMatcher.isEqualToReading(committedText, it.word.reading) }
            .maxByOrNull { ys[it.id] ?: 0f }

        if (matched != null) {
            clearWord(s, matched)
            return
        }

        val deadInput = committedText.isNotEmpty() &&
            activeWords.none { ImeMatcher.isPrefixOfReading(committedText, it.word.reading) }

        val updatedWords = s.words.map { word ->
            if (word.isActive) {
                word.copy(
                    isHighlighted = committedText.isNotEmpty() &&
                        ImeMatcher.isPrefixOfReading(committedText, word.word.reading)
                )
            } else word
        }

        _state.value = s.copy(
            words = updatedWords,
            buffer = committedText,
            deadInput = deadInput,
        )
    }

    /** Shared clear bookkeeping for both input paths — scoring/combo/burst are mode-agnostic. */
    private fun clearWord(s: GameState, matched: FallingWord) {
        val payout = GameTuning.payout(
            hardestKanjiGrade = matched.word.hardestKanjiGrade,
            readingLength = matched.word.reading.length,
            combo = s.combo,
            secondsSinceLastClear = elapsedSeconds - lastClearElapsedSeconds,
        )
        lastClearElapsedSeconds = elapsedSeconds
        viewModelScope.launch { metWordRepository.logCleared(matched.word.id) }
        _burstEvents.trySend(BurstEvent(matched.id, matched.xFraction, ys[matched.id] ?: 0f))
        ys.remove(matched.id)
        val newCombo = s.combo + 1
        val newCleared = s.cleared + 1
        val targetReached = s.targetReached ||
            (runConfig.targetClears != null && newCleared >= runConfig.targetClears)
        _state.value = s.copy(
            words = s.words.filterNot { it.id == matched.id },
            buffer = "",
            deadInput = false,
            score = s.score + payout,
            combo = newCombo,
            maxCombo = maxOf(s.maxCombo, newCombo),
            cleared = newCleared,
            targetReached = targetReached,
        )
    }

    /**
     * Ends a passed campaign run early (the pause-overlay "Finish level" action) — the
     * alternative to playing on until game over. No-op unless the target is actually met.
     */
    fun finishLevel() {
        val s = _state.value
        if (s.targetReached && !s.isOver) _state.value = s.copy(levelComplete = true)
    }

    /** Ends a zen-ruleset run (ZEN/REVIEW) — no game over exists there, so the pause-overlay Finish action is the only exit. */
    fun finishRun() {
        val s = _state.value
        if (!runConfig.mode.hasLives && !s.isOver) _state.value = s.copy(gameOver = true)
    }

    fun pause() {
        if (!_state.value.isOver) _state.value = _state.value.copy(paused = true)
    }

    fun togglePause() {
        if (!_state.value.isOver) _state.value = _state.value.copy(paused = !_state.value.paused)
    }

    /**
     * cleared + missed is > 0 at every terminal state under current rules (gameOver needs
     * misses, levelComplete needs clears); the coercion only guards against a future
     * targetClears = 0 level turning this into a division by zero.
     */
    fun accuracyPercent(): Int {
        val s = _state.value
        return s.cleared * 100 / (s.cleared + s.missed).coerceAtLeast(1)
    }

    /**
     * Picks a spawn candidate that doesn't collide with any active word's reading prefix,
     * weighted per §5 to favor missed/unmet words over reliably-cleared ones, damped for
     * words already shown this run.
     */
    private fun pickCandidate(activeWords: List<FallingWord>): WordEntity? {
        val activeReadings = activeWords.map { it.word.reading }
        val candidates = pool.filter { candidate ->
            activeReadings.none { existing ->
                existing.startsWith(candidate.reading) || candidate.reading.startsWith(existing)
            }
        }
        if (candidates.isEmpty()) return null

        // Frequency bias (spaced-repetition-style word introduction) only applies to
        // Endless — Campaign's fixed section/level structure doesn't fit a rolling
        // introduction model, see GameTuning.frequencyBias's doc.
        val isEndless = runConfig.targetClears == null
        val weights = candidates.map { candidate ->
            val met = metWordSnapshot[candidate.id]
            var w = GameTuning.spawnWeight(
                timesMissed = met?.timesMissed ?: 0,
                timesCleared = met?.timesCleared ?: 0,
                unmet = (met?.timesCleared ?: 0) == 0,
            )
            if (candidate.id in seenThisRun) w *= GameTuning.REPEAT_DAMP
            if (isEndless) w *= GameTuning.frequencyBias(candidate.frequencyRank, metWordSnapshot.size)
            w
        }
        val totalWeight = weights.sum()
        if (totalWeight <= 0f) return candidates.randomOrNull()

        var r = Random.nextFloat() * totalWeight
        for (i in candidates.indices) {
            r -= weights[i]
            if (r <= 0f) return candidates[i]
        }
        return candidates.last()
    }

    /**
     * Picks a spawn x-fraction, resampling a few times to avoid overlapping a word still
     * near the top of the screen (two spawns in quick succession used to be able to land
     * on top of each other). Best-effort — accepts the overlap after
     * [GameTuning.SPAWN_PLACEMENT_ATTEMPTS] tries rather than skipping the spawn.
     */
    private fun pickSpawnX(activeWords: List<FallingWord>): Float {
        repeat(GameTuning.SPAWN_PLACEMENT_ATTEMPTS) {
            val x = Random.nextFloat() * 0.68f + 0.05f
            val tooClose = activeWords.any { word ->
                (ys[word.id] ?: 1f) < GameTuning.SPAWN_SEPARATION_Y &&
                    abs(word.xFraction - x) < GameTuning.SPAWN_SEPARATION_X
            }
            if (!tooClose) return x
        }
        return Random.nextFloat() * 0.68f + 0.05f
    }

    companion object {
        fun factory(wordRepository: WordRepository, metWordRepository: MetWordRepository, runConfig: RunConfig) = viewModelFactory {
            initializer { GameViewModel(wordRepository, metWordRepository, runConfig) }
        }
    }
}
