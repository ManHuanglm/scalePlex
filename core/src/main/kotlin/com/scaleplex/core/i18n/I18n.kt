package com.scaleplex.core.i18n

import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight i18n manager. Loads UTF-8 resource bundles from
 * `i18n/messages_<lang>.properties` and supports live locale switching.
 */
object I18n {
    private val BASE = "i18n.messages"
    private val listeners = CopyOnWriteArrayList<(Locale) -> Unit>()

    @Volatile
    var locale: Locale = Locale.SIMPLIFIED_CHINESE
        private set

    private var bundle: ResourceBundle = load(locale)

    private fun load(locale: Locale): ResourceBundle {
        // Control to force UTF-8 reading (Java 9+ defaults to UTF-8 already, but be explicit).
        val control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES)
        return ResourceBundle.getBundle(BASE, locale, I18n::class.java.classLoader, control)
    }

    fun supported(): List<Locale> = listOf(Locale.SIMPLIFIED_CHINESE, Locale.US)

    fun setLocale(locale: Locale) {
        this.locale = locale
        Locale.setDefault(locale)
        bundle = load(locale)
        listeners.forEach { it(locale) }
    }

    fun onLocaleChange(listener: (Locale) -> Unit) {
        listeners.add(listener)
    }

    /** Translate a key. Falls back to the key itself when missing. */
    fun t(key: String): String = try {
        bundle.getString(key)
    } catch (_: Exception) {
        key
    }
}

/** Convenience global `tr("key")`. */
fun tr(key: String): String = I18n.t(key)
