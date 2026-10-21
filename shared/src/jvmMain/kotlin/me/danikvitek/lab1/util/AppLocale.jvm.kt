package me.danikvitek.lab1.util

import java.util.Locale

actual fun detectSystemLanguage(): AppLanguage {
    val sysLang = Locale.getDefault().language
    return when (sysLang) {
        "uk" -> AppLanguage.UKRAINIAN
        else -> AppLanguage.ENGLISH
    }
}

actual fun applyPlatformLocale(language: AppLanguage) {
    Locale.setDefault(Locale.forLanguageTag(language.tag))
}
