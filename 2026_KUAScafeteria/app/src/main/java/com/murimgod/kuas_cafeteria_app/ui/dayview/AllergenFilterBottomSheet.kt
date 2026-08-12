package com.murimgod.kuas_cafeteria_app.ui.dayview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.murimgod.kuas_cafeteria_app.data.model.AllergenInfo
import com.murimgod.kuas_cafeteria_app.databinding.BottomSheetAllergenFilterBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AllergenFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAllergenFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DayViewViewModel by lazy {
        ViewModelProvider(requireParentFragment())[DayViewViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAllergenFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.navBarSpacer) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navInsets.bottom)
            insets
        }

        // Filter switch — init from current state
        binding.switchFilterActive.isChecked = viewModel.filterActive.value
        binding.switchFilterActive.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.filterActive.value) viewModel.toggleFilter()
        }

        // Build chips once when allergens are available
        viewLifecycleOwner.lifecycleScope.launch {
            val allergens = viewModel.allergens.first { it.isNotEmpty() }
            buildChips(allergens, viewModel.excludedAllergens.value)
        }
    }

    private fun buildChips(allergens: List<AllergenInfo>, currentExcluded: Set<String>) {
        binding.tvAllergensLoading.visibility = View.GONE
        binding.chipGroupAllergens.removeAllViews()

        allergens.forEach { allergen ->
            val chip = Chip(requireContext()).apply {
                text = allergen.nameEn
                isCheckable = true
                isChecked = allergen.id in currentExcluded
                // Listener set AFTER isChecked to avoid spurious fire
                setOnCheckedChangeListener { _, isChecked ->
                    val current = viewModel.excludedAllergens.value.toMutableSet()
                    if (isChecked) current.add(allergen.id) else current.remove(allergen.id)
                    viewModel.setExcludedAllergens(current)
                }
            }
            binding.chipGroupAllergens.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
