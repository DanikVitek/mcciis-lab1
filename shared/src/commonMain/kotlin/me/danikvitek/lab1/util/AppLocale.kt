package me.danikvitek.lab1.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class AppLanguage(val tag: String, val label: String) {
    UKRAINIAN("uk-UA", "UA"),
    ENGLISH("en-US", "EN"),
}

expect fun detectSystemLanguage(): AppLanguage

expect fun applyPlatformLocale(language: AppLanguage)

object AppLocaleManager {
    val currentLanguage: StateFlow<AppLanguage>
        field = MutableStateFlow(detectSystemLanguage())

    fun setLanguage(language: AppLanguage) {
        applyPlatformLocale(language)
        currentLanguage.value = language
    }
}

data class UiText(
    val res: StringResource,
    val args: Array<out Any> = emptyArray(),
) {
    companion object {
        operator fun invoke(res: StringResource, vararg args: Any) = UiText(res, args)
    }

    val value
        @Composable
        get() = if (args.isEmpty())
            stringResource(res)
        else
            stringResource(res, *args)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UiText

        if (res != other.res) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = res.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }

}

