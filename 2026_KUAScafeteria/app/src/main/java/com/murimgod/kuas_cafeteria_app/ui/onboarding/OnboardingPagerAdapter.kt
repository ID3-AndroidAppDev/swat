package com.murimgod.kuas_cafeteria_app.ui.onboarding

import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(
    activity: OnboardingActivity,
    private val onCampusSelected: (String) -> Unit,
    private val onLanguageSelected: (String) -> Unit,
    private val onAllergensChanged: (Set<String>) -> Unit
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 4

    override fun createFragment(position: Int) = when (position) {
        0 -> WelcomeStepFragment()
        1 -> CampusStepFragment { onCampusSelected(it) }
        2 -> LanguageStepFragment { onLanguageSelected(it) }
        else -> AllergenStepFragment { onAllergensChanged(it) }
    }
}
