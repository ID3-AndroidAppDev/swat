package com.murimgod.kuas_cafeteria_app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.databinding.FragmentOnboardingLanguageBinding

class LanguageStepFragment(
    private val onSelected: (String) -> Unit
) : Fragment() {

    private var _binding: FragmentOnboardingLanguageBinding? = null
    private val binding get() = _binding!!

    // code, native label, flag emoji (8 languages, 2-column grid)
    private val languages = listOf(
        Triple("en", "English", "🇬🇧"),
        Triple("ja", "日本語", "🇯🇵"),
        Triple("zh", "中文", "🇨🇳"),
        Triple("ko", "한국어", "🇰🇷"),
        Triple("vi", "Tiếng Việt", "🇻🇳"),
        Triple("es", "Español", "🇪🇸"),
        Triple("fr", "Français", "🇫🇷"),
        Triple("ru", "Русский", "🇷🇺")
    )

    private var selectedLang = "en"
    private val chips = mutableListOf<TextView>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildLanguageGrid()
        selectLanguage("en")
    }

    private fun buildLanguageGrid() {
        binding.langGrid.removeAllViews()
        chips.clear()
        val inflater = LayoutInflater.from(requireContext())

        // 2 columns per row; rows share the grid height equally (weight=1) so the
        // grid fills the screen — 8 languages → 4 full-height rows.
        val perRow = 2
        val rows = (languages.size + perRow - 1) / perRow
        val gap = resources.getDimensionPixelSize(R.dimen.space_3)
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                if (row < rows - 1) lp.bottomMargin = gap
                layoutParams = lp
            }
            for (col in 0 until perRow) {
                val idx = row * perRow + col
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                if (col < perRow - 1) lp.marginEnd = gap
                if (idx < languages.size) {
                    val (code, label, flag) = languages[idx]
                    val chip = buildLangChip(code, label, flag)
                    chip.layoutParams = lp
                    chips.add(chip)
                    rowLayout.addView(chip)
                } else {
                    val spacer = View(requireContext())
                    spacer.layoutParams = lp
                    rowLayout.addView(spacer)
                }
            }
            binding.langGrid.addView(rowLayout)
        }
    }

    private fun buildLangChip(code: String, label: String, flag: String): TextView {
        return TextView(requireContext()).apply {
            text = "$flag  $label"
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setPadding(16, 22, 16, 22)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(requireContext(), R.drawable.selector_lang_chip)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            contentDescription = label
            setOnClickListener { selectLanguage(code) }
        }
    }

    private fun selectLanguage(code: String) {
        selectedLang = code
        onSelected(code)
        val accent = ContextCompat.getColor(requireContext(), R.color.accent)
        val secondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        chips.forEachIndexed { idx, chip ->
            val isSelected = languages[idx].first == code
            chip.isSelected = isSelected
            chip.setTextColor(if (isSelected) accent else secondary)
            chip.typeface = if (isSelected)
                android.graphics.Typeface.DEFAULT_BOLD
            else
                android.graphics.Typeface.DEFAULT
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
