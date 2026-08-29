package com.osala.BuzzMePlease.core

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * La langue choisie dans les réglages, à la disposition de tout ce qui fabrique du texte sans
 * passer par Compose : le réseau et ses messages de liaison, les libellés de la sonothèque, les
 * avertissements ponctuels. Sans cela, forcer le français sur un téléphone anglais laisserait
 * ces textes-là dans la langue du système.
 */
object AppLocale {

    @Volatile
    var current: AppLanguage = AppLanguage.SYSTEM

    /**
     * La locale de mise en forme des nombres : c'est elle qui décide du séparateur décimal,
     * virgule en français, point en anglais.
     */
    val locale: Locale
        get() = when (current) {
            AppLanguage.FRENCH -> Locale.FRENCH
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.SYSTEM -> Locale.getDefault()
        }

    /** Le même contexte, mais dont les ressources parlent la langue choisie. */
    fun wrap(base: Context): Context {
        val tag = current.tag ?: return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
        }
        return base.createConfigurationContext(configuration)
    }
}
