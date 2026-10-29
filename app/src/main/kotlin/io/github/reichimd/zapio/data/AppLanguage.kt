package io.github.reichimd.zapio.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Language of the app's texts; [SYSTEM] follows the phone. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de"),
    ;

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "language"

        fun current(context: Context): AppLanguage {
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == stored } ?: SYSTEM
        }

        fun set(context: Context, language: AppLanguage) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        }

        /** Returns [base] with the chosen language; used when an activity is created. */
        fun wrap(base: Context): Context {
            val tag = current(base).tag ?: return base
            val config = Configuration(base.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            return base.createConfigurationContext(config)
        }
    }
}
