package com.example.kotobadrop.app

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.Settings
import com.example.kotobadrop.core.model.SpeedDifficulty
import com.example.kotobadrop.core.model.ThemePreference
import com.example.kotobadrop.core.model.UiLanguage
import com.example.kotobadrop.core.ui.rememberReducedMotionEnabled
import com.example.kotobadrop.core.ui.theme.KotobaDropTheme
import com.example.kotobadrop.dictionary.DictionaryScreen
import com.example.kotobadrop.game.GameScreen
import com.example.kotobadrop.game.GameTuning
import com.example.kotobadrop.game.RunConfig
import com.example.kotobadrop.game.RunMode
import com.example.kotobadrop.game.campaign.CampaignScreen
import com.example.kotobadrop.game.campaign.LevelResultScreen
import com.example.kotobadrop.game.campaign.campaignLevelById
import com.example.kotobadrop.game.campaign.nextCampaignLevel
import com.example.kotobadrop.settings.CreditsScreen
import com.example.kotobadrop.settings.SettingsScreen
import com.example.kotobadrop.stats.StatsScreen
import com.example.kotobadrop.tutorial.TutorialScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val ENDLESS_SETUP = "endlessSetup"
    const val CAMPAIGN = "campaign"
    const val GAME = "game/{speed}/{tierMin}/{tierMax}/{furigana}/{inputMode}/{targetClears}/{levelId}/{lives}/{mode}"
    const val RESULTS = "results/{score}/{maxCombo}/{cleared}/{missed}/{accuracy}/{best}/{newRecord}/{mode}"
    const val LEVEL_RESULT = "levelResult/{won}/{score}/{cleared}/{targetClears}/{levelId}"
    const val DICTIONARY = "dictionary"
    const val SETTINGS = "settings"
    const val CREDITS = "credits"
    const val TUTORIAL = "tutorial"
    const val STATS = "stats"

    fun game(config: RunConfig, levelId: String?) =
        "game/${config.speed.name}/${config.tierMin}/${config.tierMax}/${config.furigana}/${config.inputMode.name}/${config.targetClears ?: -1}/${levelId ?: "-"}/${config.lives}/${config.mode.name}"

    fun results(score: Int, maxCombo: Int, cleared: Int, missed: Int, accuracy: Int, best: Int, newRecord: Boolean, mode: RunMode) =
        "results/$score/$maxCombo/$cleared/$missed/$accuracy/$best/$newRecord/${mode.name}"

    fun levelResult(won: Boolean, score: Int, cleared: Int, targetClears: Int, levelId: String) =
        "levelResult/$won/$score/$cleared/$targetClears/$levelId"
}

@Composable
fun KotobaDropApp() {
    val app = LocalContext.current.applicationContext as KotobaDropApplication
    // Collected OUTSIDE the theme wrapper: the theme itself depends on settings now. The
    // collectAsState initial (SYSTEM) means the first frame follows the system theme until
    // the persisted value arrives — a one-frame flicker at worst for users who force the
    // opposite of their system theme, harmless for everyone else.
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = Settings())
    val darkTheme = when (settings.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    // Status-bar icon contrast must track the APP theme, not the system one — with an
    // in-app override they can differ, and enableEdgeToEdge's automatic styling only
    // follows the system setting (dark icons on a dark app background otherwise).
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    KotobaDropTheme(darkTheme = darkTheme) {
        val scope = rememberCoroutineScope()
        val navController = rememberNavController()
        val reducedMotion = rememberReducedMotionEnabled()
        // Calm crossfade only — no slide/scale — per §10: "nothing else is animated or
        // decorated" beyond the one signature element (the petal burst). Instant when the
        // system requests reduced motion.
        val transitionMillis = if (reducedMotion) 0 else 220

        // Per-app language (§9): DataStore's uiLanguage is the source of truth, applied here
        // via AppCompatDelegate (triggers an Activity recreation on real changes). Collects
        // the repository Flow directly rather than reading the `settings` snapshot above —
        // `collectAsState(initial = Settings())`'s synthetic EN default fires on every fresh
        // composition (including the one caused by the locale-driven recreate itself), which
        // previously looked like a real language change and caused an infinite recreate loop.
        // Collecting the Flow's actual emissions only means the first value seen is always
        // the real persisted one.
        LaunchedEffect(Unit) {
            app.settingsRepository.settingsFlow
                .map { it.uiLanguage }
                .distinctUntilChanged()
                .collect { uiLanguage ->
                    val tag = if (uiLanguage == UiLanguage.JA) "ja" else "en"
                    val current = AppCompatDelegate.getApplicationLocales()
                    if (current.toLanguageTags() != tag) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(transitionMillis)) },
            exitTransition = { fadeOut(tween(transitionMillis)) },
            popEnterTransition = { fadeIn(tween(transitionMillis)) },
            popExitTransition = { fadeOut(tween(transitionMillis)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onEndlessClick = { navController.navigate(Routes.ENDLESS_SETUP) },
                    onCampaignClick = { navController.navigate(Routes.CAMPAIGN) },
                    onDictionaryClick = { navController.navigate(Routes.DICTIONARY) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onTutorialClick = { navController.navigate(Routes.TUTORIAL) },
                    onStatsClick = { navController.navigate(Routes.STATS) },
                )
            }
            composable(Routes.ENDLESS_SETUP) {
                EndlessSetupScreen(
                    onStart = { config -> navController.navigate(Routes.game(config, levelId = null)) },
                )
            }
            composable(Routes.CAMPAIGN) {
                CampaignScreen(
                    onLevelSelected = { levelId ->
                        val level = campaignLevelById(levelId) ?: return@CampaignScreen
                        val config = RunConfig(
                            speed = level.speed,
                            tierMin = level.sectionTier,
                            tierMax = level.sectionTier,
                            furigana = settings.furigana,
                            targetClears = level.targetClears,
                            inputMode = settings.inputMode,
                            lives = level.lives,
                            mode = RunMode.STANDARD,
                        )
                        navController.navigate(Routes.game(config, levelId))
                    },
                )
            }
            composable(
                route = Routes.GAME,
                arguments = listOf(
                    navArgument("speed") { type = NavType.StringType },
                    navArgument("tierMin") { type = NavType.IntType },
                    navArgument("tierMax") { type = NavType.IntType },
                    navArgument("furigana") { type = NavType.BoolType },
                    navArgument("inputMode") { type = NavType.StringType },
                    navArgument("targetClears") { type = NavType.IntType },
                    navArgument("levelId") { type = NavType.StringType },
                    navArgument("lives") { type = NavType.IntType },
                    navArgument("mode") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments!!
                val levelId = args.getString("levelId")!!.let { if (it == "-") null else it }
                val targetClears = args.getInt("targetClears").let { if (it < 0) null else it }
                val runConfig = RunConfig(
                    speed = SpeedDifficulty.valueOf(args.getString("speed")!!),
                    tierMin = args.getInt("tierMin"),
                    tierMax = args.getInt("tierMax"),
                    furigana = args.getBoolean("furigana"),
                    targetClears = targetClears,
                    inputMode = InputMode.valueOf(args.getString("inputMode")!!),
                    lives = args.getInt("lives"),
                    mode = RunMode.valueOf(args.getString("mode")!!),
                )
                GameScreen(
                    runConfig = runConfig,
                    onGameEnd = { score, maxCombo, cleared, missed, accuracy, won ->
                        if (levelId != null) {
                            scope.launch {
                                app.scoreRepository.save(
                                    score = score,
                                    maxCombo = maxCombo,
                                    wordsCleared = cleared,
                                    wordsMissed = missed,
                                    speedDifficulty = runConfig.speed.name,
                                    knowledgeDifficulty = runConfig.tierMax,
                                )
                            }
                            if (won) {
                                scope.launch { app.campaignRepository.markCompleted(levelId, score) }
                            }
                            navController.navigate(Routes.levelResult(won, score, cleared, targetClears ?: 0, levelId)) {
                                popUpTo(Routes.CAMPAIGN)
                            }
                        } else if (runConfig.mode == RunMode.STANDARD) {
                            // The previous best has to be read before this run's score is
                            // inserted, so the whole read-save-navigate sequence lives in
                            // one coroutine.
                            scope.launch {
                                val previousBest = app.scoreRepository.getHighScores(1).firstOrNull()?.score ?: 0
                                app.scoreRepository.save(
                                    score = score,
                                    maxCombo = maxCombo,
                                    wordsCleared = cleared,
                                    wordsMissed = missed,
                                    speedDifficulty = runConfig.speed.name,
                                    knowledgeDifficulty = runConfig.tierMax,
                                )
                                val newRecord = score > previousBest
                                navController.navigate(
                                    Routes.results(score, maxCombo, cleared, missed, accuracy, maxOf(previousBest, score), newRecord, RunMode.STANDARD)
                                ) {
                                    popUpTo(Routes.GAME) { inclusive = true }
                                }
                            }
                        } else {
                            // REVIEW/ZEN runs are practice — never saved, no best/record
                            // comparison (best = -1 hides those lines on the Results screen).
                            navController.navigate(
                                Routes.results(score, maxCombo, cleared, missed, accuracy, -1, false, runConfig.mode)
                            ) {
                                popUpTo(Routes.GAME) { inclusive = true }
                            }
                        }
                    },
                    onQuit = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.RESULTS,
                arguments = listOf(
                    navArgument("score") { type = NavType.IntType },
                    navArgument("maxCombo") { type = NavType.IntType },
                    navArgument("cleared") { type = NavType.IntType },
                    navArgument("missed") { type = NavType.IntType },
                    navArgument("accuracy") { type = NavType.IntType },
                    navArgument("best") { type = NavType.IntType },
                    navArgument("newRecord") { type = NavType.BoolType },
                    navArgument("mode") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments!!
                ResultsScreen(
                    score = args.getInt("score"),
                    maxCombo = args.getInt("maxCombo"),
                    cleared = args.getInt("cleared"),
                    missed = args.getInt("missed"),
                    accuracy = args.getInt("accuracy"),
                    best = args.getInt("best"),
                    newRecord = args.getBoolean("newRecord"),
                    missedWords = app.lastRunMissedWords,
                    onPlayAgain = {
                        // Replays keep the run's mode: a review replay re-derives the missed
                        // pool fresh in the ViewModel; zen replays stay zen.
                        val config = RunConfig(
                            speed = settings.speedDifficulty,
                            tierMin = 0,
                            tierMax = settings.knowledgeDifficulty,
                            furigana = settings.furigana,
                            targetClears = null,
                            inputMode = settings.inputMode,
                            lives = GameTuning.DEFAULT_LIVES,
                            mode = RunMode.valueOf(args.getString("mode")!!),
                        )
                        // popUpTo ENDLESS_SETUP (not HOME): keeps the setup screen on the
                        // back stack, so backing out of a replayed run returns to the same
                        // place the original run was launched from.
                        navController.navigate(Routes.game(config, levelId = null)) {
                            popUpTo(Routes.ENDLESS_SETUP)
                        }
                    },
                    onHomeClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                )
            }
            composable(
                route = Routes.LEVEL_RESULT,
                arguments = listOf(
                    navArgument("won") { type = NavType.BoolType },
                    navArgument("score") { type = NavType.IntType },
                    navArgument("cleared") { type = NavType.IntType },
                    navArgument("targetClears") { type = NavType.IntType },
                    navArgument("levelId") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments!!
                val levelId = args.getString("levelId")!!
                val level = campaignLevelById(levelId)
                val nextLevel = nextCampaignLevel(levelId)
                LevelResultScreen(
                    won = args.getBoolean("won"),
                    score = args.getInt("score"),
                    cleared = args.getInt("cleared"),
                    targetClears = args.getInt("targetClears"),
                    hasNextLevel = nextLevel != null,
                    missedWords = app.lastRunMissedWords,
                    onNextLevel = {
                        if (nextLevel != null) {
                            val config = RunConfig(
                                speed = nextLevel.speed,
                                tierMin = nextLevel.sectionTier,
                                tierMax = nextLevel.sectionTier,
                                furigana = settings.furigana,
                                targetClears = nextLevel.targetClears,
                                inputMode = settings.inputMode,
                                lives = nextLevel.lives,
                                mode = RunMode.STANDARD,
                            )
                            navController.navigate(Routes.game(config, nextLevel.id)) {
                                popUpTo(Routes.CAMPAIGN)
                            }
                        }
                    },
                    onRetry = {
                        if (level != null) {
                            val config = RunConfig(
                                speed = level.speed,
                                tierMin = level.sectionTier,
                                tierMax = level.sectionTier,
                                furigana = settings.furigana,
                                targetClears = level.targetClears,
                                inputMode = settings.inputMode,
                                lives = level.lives,
                                mode = RunMode.STANDARD,
                            )
                            navController.navigate(Routes.game(config, level.id)) {
                                popUpTo(Routes.CAMPAIGN)
                            }
                        }
                    },
                    onLevelSelect = {
                        navController.navigate(Routes.CAMPAIGN) {
                            popUpTo(Routes.CAMPAIGN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.DICTIONARY) {
                DictionaryScreen(
                    onReviewClick = {
                        val config = RunConfig(
                            speed = settings.speedDifficulty,
                            tierMin = 0,
                            tierMax = 4,
                            furigana = settings.furigana,
                            targetClears = null,
                            inputMode = settings.inputMode,
                            lives = GameTuning.DEFAULT_LIVES,
                            mode = RunMode.REVIEW,
                        )
                        navController.navigate(Routes.game(config, levelId = null))
                    },
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onCreditsClick = { navController.navigate(Routes.CREDITS) })
            }
            composable(Routes.CREDITS) { CreditsScreen() }
            composable(Routes.TUTORIAL) { TutorialScreen() }
        }
    }
}
