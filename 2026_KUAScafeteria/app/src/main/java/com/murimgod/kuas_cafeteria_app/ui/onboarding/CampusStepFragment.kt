package com.murimgod.kuas_cafeteria_app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.databinding.FragmentOnboardingCampusBinding

class CampusStepFragment(
    private val onSelected: (String) -> Unit
) : Fragment() {

    private var _binding: FragmentOnboardingCampusBinding? = null
    private val binding get() = _binding!!
    private var selected = "uzumasa"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingCampusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectCampus("uzumasa")

        binding.cardUzumasa.setOnClickListener { selectCampus("uzumasa") }
        binding.cardKameoka.setOnClickListener { selectCampus("kameoka") }
    }

    private fun selectCampus(campus: String) {
        selected = campus
        onSelected(campus)
        val accentColor = ContextCompat.getColor(requireContext(), R.color.accent)
        val borderColor = ContextCompat.getColor(requireContext(), R.color.border_tertiary)
        val softFill = ContextCompat.getColor(requireContext(), R.color.accent_soft)
        val plainFill = ContextCompat.getColor(requireContext(), R.color.background_primary)
        val uz = campus == "uzumasa"

        binding.cardUzumasa.setStrokeColor(
            android.content.res.ColorStateList.valueOf(if (uz) accentColor else borderColor)
        )
        binding.cardKameoka.setStrokeColor(
            android.content.res.ColorStateList.valueOf(if (!uz) accentColor else borderColor)
        )
        binding.cardUzumasa.setCardBackgroundColor(if (uz) softFill else plainFill)
        binding.cardKameoka.setCardBackgroundColor(if (!uz) softFill else plainFill)
        binding.ivCheckUzumasa.visibility = if (uz) View.VISIBLE else View.INVISIBLE
        binding.ivCheckKameoka.visibility = if (!uz) View.VISIBLE else View.INVISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
