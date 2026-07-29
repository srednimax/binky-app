package app.binky.tracker.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.binky.tracker.R

/**
 * The languages Binky ships in (ADR-0013).
 *
 * **This list and `res/xml/locales_config.xml` are the same claim in two files**, and they have to
 * agree: the XML is what Android 13+ reads to build the app's entry in system Settings and what
 * AppCompat's backport reads below 13, while this enum is what the in-app switcher offers.
 * `AppLanguageTest` parses the XML and asserts the two match, because "remember to edit both" is
 * exactly the kind of promise that survives right up until the translation lands and nobody does.
 *
 * English alone at 1.0. Polish joins it at 3i, as one entry here and one line of XML.
 */
enum class AppLanguage(
    val tag: String,
    @param:StringRes val labelRes: Int,
) {
    ENGLISH("en", R.string.settings_language_english),
}

/**
 * The language the owner has chosen for the app, or `null` for "follow the phone".
 *
 * `null` is the ordinary state and not a missing value: an app locale is an *override*, and having
 * never set one is what most owners will do forever.
 *
 * Read from [AppCompatDelegate] rather than from a preference of our own — deliberately. On Android
 * 13+ this is stored by the platform and is editable from system Settings too, so a DataStore key
 * would be a second copy of an answer the owner can change somewhere Binky never sees.
 */
fun currentAppLanguage(): AppLanguage? {
    val locales = AppCompatDelegate.getApplicationLocales()
    val language = locales[0]?.language ?: return null
    // Matched on the language subtag alone: the platform may hand back a region-qualified locale
    // ("en-GB") for a list that only ever names a language ("en").
    return AppLanguage.entries.firstOrNull { it.tag.equals(language, ignoreCase = true) }
}

/**
 * Applies [language], or clears the override when it is `null`.
 *
 * This **recreates the Activity** — that is how a locale change reaches every already-composed
 * string, and it is the platform's behaviour on 13+ and AppCompat's below it, not something this
 * app arranges. Nothing else has to be told: `MainActivity` is rebuilt and every `stringResource`
 * resolves against the new configuration.
 *
 * Persistence is AppCompat's, via the `AppLocalesMetadataHolderService` in the manifest — a
 * DataStore key would be read asynchronously and let the app draw a frame in the wrong language
 * (ADR-0013).
 */
fun setAppLanguage(language: AppLanguage?) {
    AppCompatDelegate.setApplicationLocales(
        language?.let { LocaleListCompat.forLanguageTags(it.tag) } ?: LocaleListCompat.getEmptyLocaleList(),
    )
}
