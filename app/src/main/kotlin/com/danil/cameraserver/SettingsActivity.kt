package com.danil.cameraserver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // тулбар + стрелка «Назад»
        setSupportActionBar(findViewById(R.id.toolbar_settings))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.menu_settings)

        // вставляем Pref‑фрагмент
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()                         // закрываем активити
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)

            // переключатель темы уже есть; колл‑бэк остаётся тем же
            findPreference<androidx.preference.SwitchPreferenceCompat>("dark_theme")
                ?.setOnPreferenceChangeListener { _, value ->
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        if (value as Boolean)
                            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        else
                            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    )
                    true
                }
        }
    }
}
