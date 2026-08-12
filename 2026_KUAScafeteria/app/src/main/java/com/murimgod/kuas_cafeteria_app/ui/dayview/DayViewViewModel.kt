package com.murimgod.kuas_cafeteria_app.ui.dayview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.murimgod.kuas_cafeteria_app.data.model.AllergenInfo
import com.murimgod.kuas_cafeteria_app.data.model.DailyMenu
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import com.murimgod.kuas_cafeteria_app.data.repository.MenuRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

sealed class MenuUiState {
    object Loading : MenuUiState()
    data class Success(val menu: DailyMenu) : MenuUiState()
    object Empty : MenuUiState()
    data class Closed(val reason: String?) : MenuUiState()
    data class Error(val message: String, val isOffline: Boolean = false) : MenuUiState()
}

data class DayPill(
    val date: LocalDate,
    val isSelected: Boolean,
    val isAvailable: Boolean
)

class DayViewViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MenuRepository(app)
    private val prefs = UserPreferences(app)

    private val _campus = MutableStateFlow("uzumasa")
    val campus: StateFlow<String> = _campus.asStateFlow()

    private val _lang = MutableStateFlow("en")
    val lang: StateFlow<String> = _lang.asStateFlow()

    private val _excludedAllergens = MutableStateFlow<Set<String>>(emptySet())
    val excludedAllergens: StateFlow<Set<String>> = _excludedAllergens.asStateFlow()

    private val _currentWeek = MutableStateFlow(isoWeekOf(defaultFocusDate()))
    val currentWeek: StateFlow<String> = _currentWeek.asStateFlow()

    private val _selectedDate = MutableStateFlow(defaultFocusDate())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _menuState = MutableStateFlow<MenuUiState>(MenuUiState.Loading)
    val menuState: StateFlow<MenuUiState> = _menuState.asStateFlow()

    private val _dayPills = MutableStateFlow<List<DayPill>>(emptyList())
    val dayPills: StateFlow<List<DayPill>> = _dayPills.asStateFlow()

    // All days of the loaded week — used by the tablet/landscape week grid.
    private val _weekDays = MutableStateFlow<List<DailyMenu>>(emptyList())
    val weekDays: StateFlow<List<DailyMenu>> = _weekDays.asStateFlow()

    private val _weekLabel = MutableStateFlow("")
    val weekLabel: StateFlow<String> = _weekLabel.asStateFlow()

    private val _canGoBack = MutableStateFlow(true)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _allergens = MutableStateFlow<List<AllergenInfo>>(HardcodedAllergens.list)
    val allergens: StateFlow<List<AllergenInfo>> = _allergens.asStateFlow()

    private val _filterActive = MutableStateFlow(false)
    val filterActive: StateFlow<Boolean> = _filterActive.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _compareItems = MutableStateFlow<List<com.murimgod.kuas_cafeteria_app.data.model.MenuItem>>(emptyList())
    val compareItems: StateFlow<List<com.murimgod.kuas_cafeteria_app.data.model.MenuItem>> = _compareItems.asStateFlow()

    val hasExcludedAllergens: StateFlow<Boolean> = _excludedAllergens
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var availableWeeks: List<String> = emptyList()
    private val weeklyMenuCache = mutableMapOf<String, List<DailyMenu>>()

    init {
        // Show a usable week strip immediately, derived from the device
        // calendar (current week, Mon–Fri, today pre-selected). The API
        // response replaces it once weeks/menu data arrive.
        updateWeekLabel(_currentWeek.value)
        updateDayPills(_currentWeek.value, emptyList())

        viewModelScope.launch {
            prefs.campusFlow.collect { _campus.value = it }
        }
        viewModelScope.launch {
            prefs.langFlow.collect { _lang.value = it }
        }
        viewModelScope.launch {
            prefs.excludedAllergensFlow.collect { _excludedAllergens.value = it }
        }
        viewModelScope.launch {
            prefs.filterActiveFlow.collect { _filterActive.value = it }
        }
        viewModelScope.launch {
            combine(campus, lang) { c, l -> Pair(c, l) }.collectLatest { (c, l) ->
                loadAllergens(l)
                loadWeeks(c)
                clampCurrentWeekToAvailable()
                loadWeek(c, _currentWeek.value, l)
                updateNavState()
            }
        }
    }

    private suspend fun loadAllergens(lang: String) {
        runCatching { _allergens.value = repo.getAllergens(lang) }
    }

    private suspend fun loadWeeks(campus: String) {
        runCatching {
            val weeks = repo.getWeeks(campus)
            availableWeeks = weeks.map { it.isoWeek }
        }
        updateNavState()
    }

    // Item 7: localize dish names via the backend. Pass the real UI language
    // through so the API returns translated names (dish_dictionary translations);
    // the backend falls back to English for any language it lacks.
    private fun apiMenuLang(uiLang: String) = uiLang

    private suspend fun loadWeek(campus: String, week: String, lang: String) {
        _menuState.value = MenuUiState.Loading
        updateWeekLabel(week)
        updateDayPills(week, emptyList())

        runCatching {
            val excluded = _excludedAllergens.value.joinToString(",").ifEmpty { null }
            val result = repo.getMenuByWeek(campus, week, apiMenuLang(lang), excluded)
            _isOffline.value = result.offline
            val days = result.response.days
            weeklyMenuCache[week] = days
            _weekDays.value = days
            updateDayPills(week, days)
            selectDay(_selectedDate.value, days)
        }.onFailure {
            _isOffline.value = false
            _menuState.value = errorState(it)
        }
    }

    // No connectivity (DNS failure, timeout, refused connection…) surfaces as
    // IOException from OkHttp; anything else is a real API/parsing error.
    private fun errorState(e: Throwable): MenuUiState.Error {
        if (e is kotlinx.coroutines.CancellationException) throw e
        val app = getApplication<Application>()
        return if (e is java.io.IOException) {
            MenuUiState.Error(
                app.getString(com.murimgod.kuas_cafeteria_app.R.string.error_no_network_title),
                isOffline = true
            )
        } else {
            MenuUiState.Error(
                app.getString(com.murimgod.kuas_cafeteria_app.R.string.error_load_menu)
            )
        }
    }

    private fun updateDayPills(week: String, days: List<DailyMenu>) {
        val monday = mondayOfWeek(week)
        val datesWithData = days.map { it.date }.toSet()
        val pills = (0..4).map { offset ->
            val date = monday.plusDays(offset.toLong())
            DayPill(
                date = date,
                isSelected = date == _selectedDate.value,
                isAvailable = datesWithData.contains(date.toString()) || days.isEmpty()
            )
        }
        _dayPills.value = pills
    }

    private fun selectDay(date: LocalDate, days: List<DailyMenu>) {
        _selectedDate.value = date
        _dayPills.value = _dayPills.value.map { it.copy(isSelected = it.date == date) }

        val dayMenu = days.firstOrNull { it.date == date.toString() }
        _menuState.value = when {
            dayMenu == null -> MenuUiState.Empty
            !dayMenu.isOpen -> MenuUiState.Closed(dayMenu.reason)
            dayMenu.sections.isEmpty() -> MenuUiState.Empty
            else -> MenuUiState.Success(dayMenu)
        }
    }

    fun selectDate(date: LocalDate) {
        val cached = weeklyMenuCache[_currentWeek.value]
        if (cached != null) {
            selectDay(date, cached)
        } else {
            _selectedDate.value = date
            viewModelScope.launch {
                _menuState.value = MenuUiState.Loading
                runCatching {
                    val excluded = _excludedAllergens.value.joinToString(",").ifEmpty { null }
                    val menu = repo.getMenuByDate(
                        _campus.value, date.toString(), apiMenuLang(_lang.value), excluded
                    )
                    _menuState.value = when {
                        !menu.isOpen -> MenuUiState.Closed(menu.reason)
                        menu.sections.isEmpty() -> MenuUiState.Empty
                        else -> MenuUiState.Success(menu)
                    }
                }.onFailure {
                    _menuState.value = errorState(it)
                }
            }
        }
    }

    fun switchCampus(campus: String) {
        viewModelScope.launch {
            weeklyMenuCache.clear()
            availableWeeks = emptyList()
            _currentWeek.value = isoWeekOf(defaultFocusDate())
            _selectedDate.value = defaultFocusDate()
            updateWeekLabel(_currentWeek.value)
            updateDayPills(_currentWeek.value, emptyList())
            prefs.setCampus(campus)
            val app = getApplication<Application>()
            com.murimgod.kuas_cafeteria_app.widget.TodayWidgetProvider.refreshAll(app)
            com.murimgod.kuas_cafeteria_app.widget.HighlightsWidgetProvider.refreshAll(app)
            com.murimgod.kuas_cafeteria_app.widget.StatusWidgetProvider.refreshAll(app)
        }
    }

    fun goToPreviousWeek() {
        val prev = previousWeek(_currentWeek.value)
        _currentWeek.value = prev
        _selectedDate.value = mondayOfWeek(prev)
        updateNavState()
        viewModelScope.launch {
            loadWeek(_campus.value, prev, _lang.value)
            updateNavState()
        }
    }

    fun goToNextWeek() {
        val next = nextWeek(_currentWeek.value)
        _currentWeek.value = next
        _selectedDate.value = mondayOfWeek(next)
        updateNavState()
        viewModelScope.launch {
            loadWeek(_campus.value, next, _lang.value)
            updateNavState()
        }
    }

    /** Move the selected day one weekday left/right; crosses week boundaries
     *  when at an edge and that direction is available. For swipe navigation. */
    fun goToAdjacentDay(forward: Boolean) {
        val dates = _dayPills.value.map { it.date }
        val idx = dates.indexOf(_selectedDate.value)
        if (idx == -1) return
        val target = idx + if (forward) 1 else -1
        when {
            target in dates.indices -> selectDate(dates[target])
            forward && _canGoForward.value -> goToNextWeek()
            !forward && _canGoBack.value -> goToPreviousWeek()
        }
    }

    fun toggleFilter() {
        val newValue = !_filterActive.value
        _filterActive.value = newValue
        viewModelScope.launch { prefs.setFilterActive(newValue) }
    }

    fun setExcludedAllergens(allergens: Set<String>) {
        _excludedAllergens.value = allergens
        viewModelScope.launch { prefs.setExcludedAllergens(allergens) }
    }

    fun refresh() {
        repo.clearCache()
        weeklyMenuCache.clear()
        viewModelScope.launch {
            loadWeek(_campus.value, _currentWeek.value, _lang.value)
        }
    }

    /** Add a dish to the side-by-side comparison set (max 3, deduped by id). */
    fun addToCompare(item: com.murimgod.kuas_cafeteria_app.data.model.MenuItem): Boolean {
        val current = _compareItems.value
        if (current.any { it.id == item.id }) return false
        if (current.size >= MAX_COMPARE) return false
        _compareItems.value = current + item
        return true
    }

    fun removeFromCompare(itemId: String) {
        _compareItems.value = _compareItems.value.filterNot { it.id == itemId }
    }

    fun clearCompare() {
        _compareItems.value = emptyList()
    }

    // Nav is driven purely by what the backend actually has (availableWeeks),
    // not by a hardcoded "current week" ceiling. As soon as a new week's menu
    // is published upstream it shows up here and forward nav unlocks.
    private fun updateNavState() {
        _canGoForward.value = availableWeeks.any { it > _currentWeek.value }
        _canGoBack.value = availableWeeks.any { it < _currentWeek.value }
    }

    // On load, snap to a week that actually has data: prefer the current real
    // week if published, else the most recent published week, else the earliest
    // (e.g. only future weeks available). Keeps the app from opening on an empty
    // week when the menu window has shifted.
    private fun clampCurrentWeekToAvailable() {
        if (availableWeeks.isEmpty()) return
        if (_currentWeek.value in availableWeeks) return
        val now = currentIsoWeek()
        val target = availableWeeks.filter { it <= now }.maxOrNull()
            ?: availableWeeks.minOrNull()
            ?: return
        _currentWeek.value = target
        _selectedDate.value = mondayOfWeek(target)
    }

    private fun updateWeekLabel(week: String) {
        try {
            val monday = mondayOfWeek(week)
            val friday = monday.plusDays(4)
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            _weekLabel.value = "${monday.format(fmt)} – ${friday.dayOfMonth}"
        } catch (_: Exception) {
            _weekLabel.value = week
        }
    }

    companion object {
        const val MAX_COMPARE = 3

        fun currentIsoWeek(): String {
            val now = LocalDate.now()
            val year = now.get(IsoFields.WEEK_BASED_YEAR)
            val week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            return "%04d-W%02d".format(year, week)
        }

        /**
         * The weekday the app should open on. The cafeteria runs Mon–Fri, so
         * on weekends we jump to the nearest serving day:
         *  - Saturday → Friday of the *same* week (yesterday)
         *  - Sunday   → Monday of the *next* week (tomorrow)
         * Mon–Fri returns today unchanged.
         */
        fun defaultFocusDate(today: LocalDate = LocalDate.now()): LocalDate =
            when (today.dayOfWeek) {
                DayOfWeek.SATURDAY -> today.minusDays(1)
                DayOfWeek.SUNDAY -> today.plusDays(1)
                else -> today
            }

        /** ISO "YYYY-Www" for an arbitrary date. */
        fun isoWeekOf(date: LocalDate): String {
            val year = date.get(IsoFields.WEEK_BASED_YEAR)
            val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            return "%04d-W%02d".format(year, week)
        }

        fun mondayOfWeek(isoWeek: String): LocalDate {
            return try {
                val parts = isoWeek.split("-W")
                val year = parts[0].toInt()
                val week = parts[1].toInt()
                LocalDate.of(year, 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                    .with(DayOfWeek.MONDAY)
            } catch (_: Exception) {
                LocalDate.now().with(DayOfWeek.MONDAY)
            }
        }

        fun previousWeek(isoWeek: String): String {
            val monday = mondayOfWeek(isoWeek)
            val prev = monday.minusWeeks(1)
            val year = prev.get(IsoFields.WEEK_BASED_YEAR)
            val week = prev.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            return "%04d-W%02d".format(year, week)
        }

        fun nextWeek(isoWeek: String): String {
            val monday = mondayOfWeek(isoWeek)
            val next = monday.plusWeeks(1)
            val year = next.get(IsoFields.WEEK_BASED_YEAR)
            val week = next.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            return "%04d-W%02d".format(year, week)
        }
    }
}
