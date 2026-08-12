package com.murimgod.kuas_cafeteria_app

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import com.murimgod.kuas_cafeteria_app.databinding.ActivityMainBinding
import com.murimgod.kuas_cafeteria_app.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Material You (wallpaper-based dynamic color) is intentionally not
        // applied: the app always keeps the brand design system for web parity.
        super.onCreate(savedInstanceState)

        val prefs = UserPreferences(this)
        val onboardingDone = runBlocking { prefs.onboardingDoneFlow.first() }

        if (!onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fade in to replace locale-change black flash
        binding.root.alpha = 0f
        binding.root.post {
            binding.root.animate()
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
