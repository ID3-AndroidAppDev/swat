package com.murimgod.kuas_cafeteria_app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.murimgod.kuas_cafeteria_app.databinding.FragmentOnboardingAllergensBinding
import com.murimgod.kuas_cafeteria_app.ui.dayview.HardcodedAllergens

/**
 * Optional onboarding step: pick allergens to hide. Reports the selected id set
 * up to the activity, which persists it as the excluded-allergen filter.
 */
class AllergenStepFragment(
    private val onChanged: (Set<String>) -> Unit
) : Fragment() {

    private var _binding: FragmentOnboardingAllergensBinding? = null
    private val binding get() = _binding!!
    private val selected = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingAllergensBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        HardcodedAllergens.list.forEach { allergen ->
            val chip = Chip(requireContext()).apply {
                text = allergen.nameEn
                isCheckable = true
                isCheckedIconVisible = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selected.add(allergen.id) else selected.remove(allergen.id)
                    onChanged(selected.toSet())
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
