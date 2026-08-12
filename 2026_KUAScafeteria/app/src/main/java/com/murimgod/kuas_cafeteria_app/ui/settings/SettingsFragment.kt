package com.murimgod.kuas_cafeteria_app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.murimgod.kuas_cafeteria_app.BuildConfig
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.model.AllergenInfo
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import com.murimgod.kuas_cafeteria_app.data.repository.MenuRepository
import com.murimgod.kuas_cafeteria_app.databinding.FragmentSettingsBinding
import com.murimgod.kuas_cafeteria_app.ui.ThemeManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: UserPreferences
    private val repo by lazy { MenuRepository(requireContext()) }

    private val supportedLanguages = listOf(
        "en" to "English",
        "ja" to "日本語",
        "zh" to "中文",
        "ko" to "한국어",
        "vi" to "Tiếng Việt",
        "es" to "Español",
        "fr" to "Français",
        "ru" to "Русский"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = UserPreferences(requireContext())

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusInsets.top)
            insets
        }

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        viewLifecycleOwner.lifecycleScope.launch {
            val lang = prefs.langFlow.first()
            binding.tvLanguageValue.text = supportedLanguages.find { it.first == lang }?.second ?: "English"

            val campus = prefs.campusFlow.first()
            binding.tvCampusValue.text = if (campus == "uzumasa")
                getString(R.string.campus_uzumasa) else getString(R.string.campus_kameoka)

            binding.tvThemeValue.text = themeLabel(prefs.themeFlow.first())

            val excluded = prefs.excludedAllergensFlow.first()
            updateAllergenCount(excluded)

            loadAllergenChips(lang, excluded)
        }

        binding.rowLanguage.setOnClickListener { showLanguagePicker() }
        binding.rowCampus.setOnClickListener { showCampusPicker() }
        binding.rowTheme.setOnClickListener { showThemePicker() }
        binding.rowAbout.setOnClickListener { showAbout() }
        // Material You toggle removed: the app always uses the brand design
        // system so it stays visually identical to the web app. The row stays
        // android:visibility="gone" in the layout.
    }

    private fun themeLabel(theme: String): String = when (theme) {
        ThemeManager.LIGHT -> getString(R.string.theme_light)
        ThemeManager.DARK -> getString(R.string.theme_dark)
        else -> getString(R.string.theme_system)
    }

    private fun showThemePicker() {
        val labels = ThemeManager.options.map { themeLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme)
            .setItems(labels) { _, which ->
                val theme = ThemeManager.options[which]
                binding.tvThemeValue.text = themeLabel(theme)
                viewLifecycleOwner.lifecycleScope.launch { prefs.setTheme(theme) }
                // Recreates activities with a cross-fade (windowAnimationStyle).
                ThemeManager.apply(theme)
            }
            .show()
    }

    private fun showLanguagePicker() {
        val labels = supportedLanguages.map { it.second }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.language)
            .setItems(labels) { _, which ->
                val selected = supportedLanguages[which]
                viewLifecycleOwner.lifecycleScope.launch {
                    prefs.setLang(selected.first)
                    // Don't fade the decor out first — that reveals black behind
                    // the window during the recreate. Just switch the locale; the
                    // themed windowBackground covers the recreate and MainActivity
                    // fades its content back in (no black flash).
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(selected.first)
                    )
                }
            }
            .show()
    }

    private fun showCampusPicker() {
        val options = arrayOf(
            getString(R.string.campus_uzumasa),
            getString(R.string.campus_kameoka)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.default_campus)
            .setItems(options) { _, which ->
                val campus = if (which == 0) "uzumasa" else "kameoka"
                viewLifecycleOwner.lifecycleScope.launch {
                    prefs.setCampus(campus)
                    binding.tvCampusValue.text = options[which]
                }
            }
            .show()
    }

    private fun loadAllergenChips(lang: String, excluded: Set<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val allergens = runCatching { repo.getAllergens(lang) }.getOrElse { emptyList() }
            renderAllergenChips(allergens, excluded)
        }
    }

    private fun renderAllergenChips(allergens: List<AllergenInfo>, excluded: Set<String>) {
        binding.chipsAllergenSettings.removeAllViews()
        allergens.forEach { allergen ->
            val isSelected = allergen.id in excluded
            val chip = TextView(requireContext()).apply {
                text = allergen.nameEn
                textSize = 12f
                setPadding(16, 8, 16, 8)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 8, 8)
                layoutParams = lp
                updateChipStyle(this, isSelected)
                setOnClickListener {
                    toggleAllergen(allergen.id, this, allergens)
                }
            }
            binding.chipsAllergenSettings.addView(chip)
        }
    }

    private fun toggleAllergen(allergenId: String, chip: TextView, all: List<AllergenInfo>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val excluded = prefs.excludedAllergensFlow.first().toMutableSet()
            if (allergenId in excluded) excluded.remove(allergenId) else excluded.add(allergenId)
            prefs.setExcludedAllergens(excluded)
            updateChipStyle(chip, allergenId in excluded)
            updateAllergenCount(excluded)
        }
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_allergen)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_allergen_fg))
        } else {
            chip.setBackgroundResource(R.drawable.bg_week_arrow)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    private fun updateAllergenCount(excluded: Set<String>) {
        binding.tvAllergenCount.text = if (excluded.isEmpty())
            getString(R.string.none_hidden)
        else
            getString(R.string.n_allergens_hidden, excluded.size)
    }

    private fun showAbout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.about)
            .setMessage(getString(R.string.about_app, BuildConfig.VERSION_NAME))
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
