package com.murimgod.kuas_cafeteria_app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.murimgod.kuas_cafeteria_app.MainActivity
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.analytics.Analytics
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import com.murimgod.kuas_cafeteria_app.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: UserPreferences
    private var selectedCampus = "uzumasa"
    private var selectedLanguage = "en"
    private var selectedAllergens: Set<String> = emptySet()

    private val pages = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPreferences(this)

        setupViewPager()
        setupDots()
        setupButtons()
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter(
            this,
            onCampusSelected = { campus -> selectedCampus = campus },
            onLanguageSelected = { lang -> selectedLanguage = lang },
            onAllergensChanged = { allergens -> selectedAllergens = allergens }
        )
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                val isLast = position == pages - 1
                binding.btnNext.text = if (isLast) getString(R.string.get_started) else getString(R.string.next)
            }
        })
    }

    private fun setupDots() {
        binding.dotsContainer.removeAllViews()
        repeat(pages) { i ->
            val dot = TextView(this).apply {
                text = "•"
                textSize = 20f
                setTextColor(
                    if (i == 0) getColor(R.color.accent)
                    else getColor(R.color.border_secondary)
                )
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 4, 0) }
            }
            binding.dotsContainer.addView(dot)
        }
    }

    private fun updateDots(currentPage: Int) {
        for (i in 0 until binding.dotsContainer.childCount) {
            val dot = binding.dotsContainer.getChildAt(i) as? TextView ?: continue
            dot.setTextColor(
                if (i == currentPage) getColor(R.color.accent)
                else getColor(R.color.border_secondary)
            )
        }
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding(skipped = false)
            }
        }

        binding.btnSkip.setOnClickListener { finishOnboarding(skipped = true) }
    }

    private fun finishOnboarding(skipped: Boolean) {
        // Skip means "use the documented defaults" regardless of any partial
        // picks the user made before tapping Skip.
        val campus = if (skipped) "uzumasa" else selectedCampus
        val language = if (skipped) "en" else selectedLanguage
        val allergens = if (skipped) emptySet() else selectedAllergens
        lifecycleScope.launch {
            prefs.setCampus(campus)
            prefs.setLang(language)
            prefs.setExcludedAllergens(allergens)
            prefs.setOnboardingDone(true)
            // Fire-and-forget the first-launch survey to our backend.
            Analytics.sendOnboarding(applicationContext, campus, language, skipped)
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language)
            )
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }
}
