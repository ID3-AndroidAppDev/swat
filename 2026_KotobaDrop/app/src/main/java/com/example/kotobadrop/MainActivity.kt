package com.example.kotobadrop

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.kotobadrop.app.KotobaDropApp

// AppCompatActivity (not plain ComponentActivity) so AppCompatDelegate.setApplicationLocales()
// (per-app language, §9) actually applies — its static entry point resolves the locale
// through the delegate machinery that only a real AppCompatDelegate instance wires up.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KotobaDropApp()
        }
    }
}
