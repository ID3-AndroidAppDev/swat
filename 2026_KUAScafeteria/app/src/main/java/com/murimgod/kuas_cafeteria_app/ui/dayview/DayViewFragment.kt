package com.murimgod.kuas_cafeteria_app.ui.dayview

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import kotlin.math.abs
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.CafeteriaStatus
import kotlinx.coroutines.delay
import com.murimgod.kuas_cafeteria_app.data.model.DailyMenu
import com.murimgod.kuas_cafeteria_app.data.model.MenuItem
import com.murimgod.kuas_cafeteria_app.data.model.Section
import com.murimgod.kuas_cafeteria_app.databinding.FragmentDayViewBinding
import com.murimgod.kuas_cafeteria_app.databinding.ItemDayPillBinding
import com.murimgod.kuas_cafeteria_app.databinding.ItemMenuItemBinding
import com.murimgod.kuas_cafeteria_app.databinding.ViewSectionHeaderBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class DayViewFragment : Fragment() {

    private var _binding: FragmentDayViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DayViewViewModel by viewModels()
    private val dayPillViews = mutableListOf<ItemDayPillBinding>()
    private val dayNameFmt = DateTimeFormatter.ofPattern("EEE")
    private val a11yDateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    private val gson = Gson()

    private var skeletonAnimator: ValueAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var isWeekGrid = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDayViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setupDayStrip()
        setupCampusSwitcher()
        setupWeekNavigation()
        setupCompare()
        setupStatusTicker()
        setupSwipeAndGrid()
        observeViewModel()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = insets.top)
            windowInsets
        }
    }

    private fun setupDayStrip() {
        val inflater = LayoutInflater.from(requireContext())
        dayPillViews.clear()
        binding.dayStrip.removeAllViews()
        repeat(5) {
            val b = ItemDayPillBinding.inflate(inflater, binding.dayStrip, false)
            binding.dayStrip.addView(b.root)
            dayPillViews.add(b)
        }
    }

    private fun bindDayPill(b: ItemDayPillBinding, pill: DayPill) {
        b.tvDayName.text = pill.date.format(dayNameFmt).uppercase()
        b.tvDayNumber.text = pill.date.dayOfMonth.toString()
        b.root.isSelected = pill.isSelected
        b.root.alpha = if (pill.isAvailable) 1f else 0.35f
        // Selected pill inverts: text-primary fill + background-primary text (web parity).
        val textColor = if (pill.isSelected)
            ContextCompat.getColor(requireContext(), R.color.background_primary)
        else
            ContextCompat.getColor(requireContext(), R.color.text_secondary)
        b.tvDayName.setTextColor(textColor)
        b.tvDayNumber.setTextColor(textColor)
        // TalkBack: read the full date instead of "MON 8" split across two views.
        b.tvDayName.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        b.tvDayNumber.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        b.root.contentDescription = pill.date.format(a11yDateFmt)
        b.root.isEnabled = pill.isAvailable
        b.root.setOnClickListener { viewModel.selectDate(pill.date) }
    }

    private fun setupCampusSwitcher() {
        binding.btnCampusUzumasa.setOnClickListener { viewModel.switchCampus("uzumasa") }
        binding.btnCampusKameoka.setOnClickListener { viewModel.switchCampus("kameoka") }
    }

    private fun setupWeekNavigation() {
        binding.btnPrevWeek.setOnClickListener { viewModel.goToPreviousWeek() }
        binding.btnNextWeek.setOnClickListener { viewModel.goToNextWeek() }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dayView_to_settings)
        }
        binding.btnFilterAllergens.setOnClickListener {
            AllergenFilterBottomSheet().show(childFragmentManager, "allergen_filter")
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
    }

    private fun observeViewModel() {
        if (isWeekGrid) observeWeekGrid()
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.campus.collectLatest { campus ->
                binding.tvCampusLabel.text =
                    if (campus == "uzumasa") "UZUMASA CAMPUS" else "KAMEOKA CAMPUS"
                binding.tvCampusName.text =
                    if (campus == "uzumasa") getString(R.string.campus_uzumasa)
                    else getString(R.string.campus_kameoka)
                val uzSelected = campus == "uzumasa"
                binding.btnCampusUzumasa.isSelected = uzSelected
                binding.btnCampusKameoka.isSelected = !uzSelected
                val onSelected = ContextCompat.getColor(requireContext(), R.color.background_primary)
                val secondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                binding.btnCampusUzumasa.setTextColor(if (uzSelected) onSelected else secondary)
                binding.btnCampusKameoka.setTextColor(if (!uzSelected) onSelected else secondary)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.weekLabel.collectLatest { binding.tvWeekLabel.text = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isOffline.collectLatest { offline ->
                binding.tvOfflineBanner.visibility = if (offline) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.compareItems.collectLatest { items ->
                binding.btnCompare.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                binding.btnCompare.text = getString(R.string.compare_count, items.size)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dayPills.collectLatest { pills ->
                pills.forEachIndexed { idx, pill ->
                    if (idx < dayPillViews.size) bindDayPill(dayPillViews[idx], pill)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.canGoBack.collectLatest {
                binding.btnPrevWeek.isEnabled = it
                binding.btnPrevWeek.alpha = if (it) 1f else 0.3f
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.canGoForward.collectLatest {
                binding.btnNextWeek.isEnabled = it
                binding.btnNextWeek.alpha = if (it) 1f else 0.3f
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.menuState,
                viewModel.filterActive,
                viewModel.excludedAllergens
            ) { state, active, excluded ->
                Triple(state, active, excluded.isNotEmpty())
            }.collectLatest { (state, filterActive, hasExcluded) ->
                binding.swipeRefresh.isRefreshing = false
                renderState(state, filterActive, hasExcluded)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.filterActive, viewModel.hasExcludedAllergens) { active, has ->
                active && has
            }.collectLatest { activeWithAllergens ->
                val tintColor = if (activeWithAllergens) R.color.accent else R.color.text_secondary
                binding.btnFilterAllergens.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), tintColor)
                )
            }
        }
    }

    // ─── Open-now status + live countdown ────────────────────────────────────

    private fun setupStatusTicker() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Pauses while the fragment is stopped; ticks once a second otherwise.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateOpenStatus()
                    delay(1000)
                }
            }
        }
    }

    /** Visual state of the status badge: which color trio + whether the dot pulses. */
    private enum class BadgeStyle(
        val bg: Int, val fg: Int, val dot: Int, val pulse: Boolean
    ) {
        OPEN(R.color.status_open_bg,   R.color.status_open_fg,   R.color.status_open_dot,   true),
        CLOSING(R.color.status_amber_bg, R.color.status_amber_fg, R.color.status_amber_dot, true),
        CLOSED(R.color.status_closed_bg, R.color.status_closed_fg, R.color.status_closed_dot, false),
    }

    /** Drops a leading emoji + spaces from a status string ("🟢 Open now" → "Open now"). */
    private fun stripEmoji(s: String): String =
        s.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()

    private fun updateOpenStatus() {
        val menu = (viewModel.menuState.value as? MenuUiState.Success)?.menu
        val hours = menu?.hours
        val todayTokyo = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).toString()
        if (menu == null || hours == null || menu.date != todayTokyo) {
            binding.statusBadge.visibility = View.GONE
            stopPulse()
            return
        }
        val status = CafeteriaStatus.compute(hours)
        val now = System.currentTimeMillis()
        val ms = status.msUntilNext(now)
        val soon = ms != null && ms <= CafeteriaStatus.COUNTDOWN_WINDOW_MS && ms > 0

        val (rawText, style) = when (status.phase) {
            CafeteriaStatus.Phase.BEFORE_OPEN ->
                if (soon) getString(R.string.status_opens_in, CafeteriaStatus.formatCountdown(ms!!)) to BadgeStyle.CLOSING
                else getString(R.string.status_opens_at, hours.open ?: "") to BadgeStyle.CLOSED
            CafeteriaStatus.Phase.OPEN ->
                if (soon) getString(R.string.status_closes_in, CafeteriaStatus.formatCountdown(ms!!)) to BadgeStyle.CLOSING
                else getString(R.string.status_open) to BadgeStyle.OPEN
            CafeteriaStatus.Phase.AFTER_CLOSE -> getString(R.string.status_closed) to BadgeStyle.CLOSED
            CafeteriaStatus.Phase.UNKNOWN -> {
                binding.statusBadge.visibility = View.GONE
                stopPulse()
                return
            }
        }

        val ctx = requireContext()
        binding.statusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, style.bg))
        binding.statusDot.backgroundTintList   = ColorStateList.valueOf(ContextCompat.getColor(ctx, style.dot))
        binding.tvOpenStatus.setTextColor(ContextCompat.getColor(ctx, style.fg))
        binding.tvOpenStatus.text = stripEmoji(rawText)
        binding.statusBadge.visibility = View.VISIBLE

        if (style.pulse) startPulse(fast = style == BadgeStyle.CLOSING) else stopPulse()
    }

    private fun startPulse(fast: Boolean) {
        val period = if (fast) 900L else 1800L
        // Already pulsing at the right cadence? leave it running.
        if (pulseAnimator?.isRunning == true && pulseAnimator?.duration == period) return
        stopPulse()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.statusDot,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.6f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.6f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f, 1f),
        ).apply {
            duration = period
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.statusDot.scaleX = 1f
        binding.statusDot.scaleY = 1f
        binding.statusDot.alpha = 1f
    }

    // ─── Swipe between days (#15) + tablet week grid (#17) ───────────────────

    private fun setupSwipeAndGrid() {
        // Tablets / large landscape show the whole week as columns; phones swipe.
        isWeekGrid = resources.configuration.screenWidthDp >= 600
        if (isWeekGrid) {
            binding.dayStrip.visibility = View.GONE
        } else {
            setupSwipeDays()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeDays() {
        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy) * 1.5f && abs(dx) > 120 && abs(vx) > 600) {
                    viewModel.goToAdjacentDay(forward = dx < 0)  // swipe left → next day
                    requireView().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    return true
                }
                return false
            }
        })
        binding.contentScroll.setOnTouchListener { _, e -> detector.onTouchEvent(e); false }
    }

    private fun observeWeekGrid() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.weekDays, viewModel.filterActive, viewModel.excludedAllergens
            ) { days, active, excluded -> Triple(days, active, excluded.isNotEmpty()) }
                .collectLatest { (days, active, hasExcluded) ->
                    if (days.isEmpty()) {
                        binding.weekGridScroll.visibility = View.GONE
                        binding.swipeRefresh.visibility = View.VISIBLE
                    } else {
                        renderWeekGrid(days, active, hasExcluded)
                        binding.weekGridScroll.visibility = View.VISIBLE
                        binding.swipeRefresh.visibility = View.GONE
                    }
                }
        }
    }

    private fun renderWeekGrid(days: List<DailyMenu>, filterActive: Boolean, hasExcluded: Boolean) {
        binding.weekGrid.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val colWidth = dp(300)
        val headerFmt = DateTimeFormatter.ofPattern("EEE · MMM d")

        days.sortedBy { it.date }.forEach { day ->
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                    .apply { marginEnd = dp(12) }
            }
            val header = TextView(requireContext()).apply {
                text = runCatching { java.time.LocalDate.parse(day.date).format(headerFmt) }.getOrDefault(day.date)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                setPadding(dp(4), 0, 0, dp(8))
            }
            column.addView(header)

            val scroll = NestedScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val inner = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            scroll.addView(inner)

            val rendered = if (day.isOpen) renderSections(inflater, day, inner, filterActive, hasExcluded) else false
            if (!rendered) {
                inner.addView(TextView(requireContext()).apply {
                    text = if (!day.isOpen) getString(R.string.restaurant_closed)
                    else getString(R.string.no_menu_for_day)
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
                    setPadding(dp(4), dp(24), 0, 0)
                })
            }
            column.addView(scroll)
            binding.weekGrid.addView(column)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun renderState(state: MenuUiState, filterActive: Boolean, hasExcluded: Boolean) {
        when (state) {
            is MenuUiState.Loading -> showLoading()
            is MenuUiState.Success -> showMenu(state.menu, filterActive, hasExcluded)
            is MenuUiState.Empty -> showEmpty()
            is MenuUiState.Closed -> showClosed(state.reason)
            is MenuUiState.Error -> showError(state)
        }
    }

    // ─── Skeleton ────────────────────────────────────────────────────────────

    private fun startSkeleton() {
        if (skeletonAnimator != null) return
        skeletonAnimator = ObjectAnimator.ofFloat(binding.viewLoading, View.ALPHA, 1f, 0.4f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        binding.viewLoading.alpha = 1f
    }

    private fun showLoading() {
        binding.viewLoading.visibility = View.VISIBLE
        binding.viewEmpty.visibility = View.GONE
        binding.viewClosed.visibility = View.GONE
        binding.viewError.visibility = View.GONE
        binding.sectionsContainer.visibility = View.GONE
        startSkeleton()
    }

    private fun showEmpty() {
        stopSkeleton()
        binding.viewLoading.visibility = View.GONE
        binding.viewEmpty.visibility = View.VISIBLE
        binding.viewClosed.visibility = View.GONE
        binding.viewError.visibility = View.GONE
        binding.sectionsContainer.visibility = View.GONE
    }

    private fun showClosed(reason: String?) {
        stopSkeleton()
        binding.viewLoading.visibility = View.GONE
        binding.viewEmpty.visibility = View.GONE
        binding.viewClosed.visibility = View.VISIBLE
        binding.viewError.visibility = View.GONE
        binding.sectionsContainer.visibility = View.GONE
        if (reason == "holiday") {
            binding.tvClosedReason.text = getString(R.string.closed_reason_holiday)
            binding.tvClosedReason.visibility = View.VISIBLE
        } else {
            binding.tvClosedReason.visibility = View.GONE
        }
    }

    private fun showError(state: MenuUiState.Error) {
        stopSkeleton()
        binding.viewLoading.visibility = View.GONE
        binding.viewEmpty.visibility = View.GONE
        binding.viewClosed.visibility = View.GONE
        binding.viewError.visibility = View.VISIBLE
        binding.sectionsContainer.visibility = View.GONE
        binding.ivErrorIcon.setImageResource(
            if (state.isOffline) R.drawable.ic_wifi_off else R.drawable.ic_alert
        )
        binding.tvErrorMsg.text = state.message
        if (state.isOffline) {
            binding.tvErrorSub.text = getString(R.string.error_no_network_sub)
            binding.tvErrorSub.visibility = View.VISIBLE
        } else {
            binding.tvErrorSub.visibility = View.GONE
        }
    }

    private fun showMenu(menu: DailyMenu, filterActive: Boolean, hasExcludedAllergens: Boolean) {
        stopSkeleton()
        binding.viewLoading.visibility = View.GONE
        binding.viewEmpty.visibility = View.GONE
        binding.viewClosed.visibility = View.GONE
        binding.viewError.visibility = View.GONE
        binding.sectionsContainer.visibility = View.VISIBLE

        binding.sectionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        renderSections(inflater, menu, binding.sectionsContainer, filterActive, hasExcludedAllergens)

        if (menu.hours != null) {
            addHoursBar(inflater, menu.hours.format())
        }
    }

    private fun addSectionHeader(inflater: LayoutInflater, section: Section, target: LinearLayout) {
        val headerBinding = ViewSectionHeaderBinding.inflate(inflater, target, false)
        headerBinding.tvSectionIcon.text = sectionIcon(section.kind)
        headerBinding.tvSectionTitle.text = section.title
        // Expose section titles as headings so TalkBack users can jump between them.
        headerBinding.tvSectionTitle.isAccessibilityHeading = true
        headerBinding.tvSectionIcon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        target.addView(headerBinding.root)
    }

    private fun addSectionCard(
        inflater: LayoutInflater, section: Section, campus: String, date: String, target: LinearLayout
    ) {
        val cardLinear = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_menu_card)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.card_gap)
            layoutParams = lp
        }
        section.items.forEachIndexed { idx, item ->
            if (idx > 0) addDivider(cardLinear)
            addMenuItem(inflater, cardLinear, item, campus, date)
        }
        target.addView(cardLinear)
    }

    /** Renders the visible sections of one day into [target]; returns true if anything was added. */
    private fun renderSections(
        inflater: LayoutInflater, menu: DailyMenu, target: LinearLayout,
        filterActive: Boolean, hasExcluded: Boolean
    ): Boolean {
        val shouldHideFiltered = filterActive && hasExcluded
        var any = false
        menu.sections.forEach { section ->
            val visibleItems = if (shouldHideFiltered) section.items.filter { !it.filteredOut } else section.items
            if (visibleItems.isEmpty()) return@forEach
            any = true
            val filteredSection = Section(section.kind, section.slot, section.title, visibleItems)
            addSectionHeader(inflater, filteredSection, target)
            addSectionCard(inflater, filteredSection, menu.campus, menu.date, target)
        }
        return any
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.border_tertiary))
        }
        parent.addView(divider)
    }

    private fun addMenuItem(
        inflater: LayoutInflater, parent: LinearLayout, item: MenuItem, campus: String, date: String
    ) {
        val itemBinding = ItemMenuItemBinding.inflate(inflater, parent, false)
        bindMenuItem(itemBinding, item)
        itemBinding.root.setOnClickListener {
            val args = bundleOf(
                "itemJson" to gson.toJson(item),
                "campus" to campus,
                "date" to date
            )
            findNavController().navigate(R.id.action_dayView_to_itemDetail, args)
        }
        itemBinding.root.setOnLongClickListener {
            addItemToCompare(item)
            true
        }
        parent.addView(itemBinding.root)
    }

    private fun addItemToCompare(item: MenuItem) {
        val added = viewModel.addToCompare(item)
        val msg = when {
            added -> getString(R.string.compare_added, item.name)
            viewModel.compareItems.value.size >= DayViewViewModel.MAX_COMPARE ->
                getString(R.string.compare_full, DayViewViewModel.MAX_COMPARE)
            else -> getString(R.string.compare_already_added)
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun setupCompare() {
        binding.btnCompare.setOnClickListener {
            CompareBottomSheet().show(childFragmentManager, "compare")
        }
        binding.btnCompare.setOnLongClickListener {
            viewModel.clearCompare()
            Toast.makeText(requireContext(), R.string.compare_cleared, Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun bindMenuItem(b: ItemMenuItemBinding, item: MenuItem) {
        if (item.nameJa?.isNotBlank() == true) {
            b.tvSublabel.text = item.nameJa
            b.tvSublabel.visibility = View.VISIBLE
        } else {
            b.tvSublabel.visibility = View.GONE
        }

        b.tvItemName.text = item.name

        val priceLabel = item.priceLabel()
        if (priceLabel.isNotBlank()) {
            b.tvPrice.text = priceLabel
            b.tvPrice.visibility = View.VISIBLE
        } else {
            b.tvPrice.visibility = View.GONE
        }

        val nutrition = item.nutrition
        if (nutrition != null && nutrition.kcal != null) {
            b.rowNutrition.visibility = View.VISIBLE
            b.tvKcal.text = "${nutrition.kcal.toInt()} kcal"
            b.tvProtein.text = nutrition.protein?.let { "P %.1fg".format(it) } ?: ""
            b.tvFat.text = nutrition.fat?.let { "F %.1fg".format(it) } ?: ""
            b.tvProtein.visibility = if (nutrition.protein != null) View.VISIBLE else View.GONE
            b.tvFat.visibility = if (nutrition.fat != null) View.VISIBLE else View.GONE
        } else {
            b.rowNutrition.visibility = View.GONE
        }

        // When the hide-function is OFF, flag the user's own excluded allergens in red.
        val filterActive = viewModel.filterActive.value
        val excluded = viewModel.excludedAllergens.value
        b.chipsAllergen.removeAllViews()
        item.allergens.forEach { allergen ->
            val danger = !filterActive && excluded.contains(allergen)
            val chip = TextView(requireContext()).apply {
                text = allergen.replaceFirstChar { it.uppercase() }
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(),
                    if (danger) R.color.chip_danger_fg else R.color.chip_allergen_fg))
                background = ContextCompat.getDrawable(requireContext(),
                    if (danger) R.drawable.bg_chip_danger else R.drawable.bg_chip_allergen)
                if (danger) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(10, 4, 10, 4)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 6, 0)
                layoutParams = lp
            }
            b.chipsAllergen.addView(chip)
        }

        b.chipsDiet.removeAllViews()
        item.tags.forEach { tag ->
            val (bg, fg, label) = when (tag) {
                "halal" -> Triple(R.drawable.bg_chip_halal, R.color.chip_halal_fg, "✓ Halal-friendly")
                "thai" -> Triple(R.drawable.bg_chip_thai, R.color.chip_thai_fg, "Thai")
                "calcium" -> Triple(R.drawable.bg_chip_calcium, R.color.chip_calcium_fg, "Calcium")
                else -> Triple(R.drawable.bg_chip_allergen, R.color.chip_allergen_fg, tag)
            }
            val chip = TextView(requireContext()).apply {
                text = label
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), fg))
                background = ContextCompat.getDrawable(requireContext(), bg)
                setPadding(10, 4, 10, 4)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 6, 0)
                layoutParams = lp
            }
            b.chipsDiet.addView(chip)
        }

        if (item.filteredOut) {
            b.tvFilteredNotice.visibility = View.VISIBLE
            b.tvFilteredNotice.text = "Contains: ${item.allergens.joinToString(", ")}"
            b.root.alpha = 0.5f
        } else {
            b.tvFilteredNotice.visibility = View.GONE
            b.root.alpha = 1f
        }
    }

    private fun addHoursBar(inflater: LayoutInflater, hours: String) {
        val hoursBinding = com.murimgod.kuas_cafeteria_app.databinding.ViewHoursBarBinding
            .inflate(inflater, binding.sectionsContainer, false)
        hoursBinding.tvHours.text = hours
        binding.sectionsContainer.addView(hoursBinding.root)
    }

    private fun sectionIcon(kind: String): String = when (kind) {
        "campus_lunch", "set" -> "🍴"
        "curry" -> "🍛"
        "ramen", "udon_soba" -> "🍜"
        "rice_bowl" -> "🍚"
        "salad" -> "🥗"
        "side", "a_la_carte" -> "🍽️"
        "live_kitchen" -> "👨‍🍳"
        else -> "🍴"
    }

    override fun onDestroyView() {
        stopSkeleton()
        pulseAnimator?.cancel()
        pulseAnimator = null
        dayPillViews.clear()
        super.onDestroyView()
        _binding = null
    }
}
