package com.osala.BuzzMePlease.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
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
            AppLanguage.GERMAN -> Locale.GERMAN
            AppLanguage.SPANISH -> SPANISH
            AppLanguage.ITALIAN -> Locale.ITALIAN
            AppLanguage.ARABIC -> ARABIC
            AppLanguage.SYSTEM -> Locale.getDefault()
        }

    /** `Locale` n'a de constante que pour une poignée de langues ; les autres se construisent. */
    private val SPANISH: Locale = Locale.forLanguageTag("es")

    /**
     * L'arabe, mais avec les chiffres latins (`-u-nu-latn`). Sans cette extension, l'arabe prend
     * les chiffres arabes orientaux du CLDR — ٠١٢٣ — et c'est tout le jeu qui bascule : le chrono
     * à la milliseconde, l'écart entre deux buzz, les scores, le décompte 3 · 2 · 1. Or on
     * compare ici des durées au millième, souvent d'un téléphone à l'autre, à côté d'un code de
     * salon qui reste en lettres latines. Les chiffres latins sont lus partout dans le monde
     * arabe, et le Maghreb n'écrit plus guère autrement.
     *
     * L'extension ne change pas le choix des ressources : c'est la langue, `ar`, qui désigne
     * `values-ar`, et c'est bien elle aussi qui met la mise en page de droite à gauche.
     */
    private val ARABIC: Locale = Locale.forLanguageTag("ar-u-nu-latn")

    /** Le même tag, pour la configuration : la langue de l'interface et celle des nombres accordées. */
    fun tagOf(language: AppLanguage): String? =
        if (language == AppLanguage.ARABIC) ARABIC.toLanguageTag() else language.tag

    /**
     * Le même contexte, mais dont les ressources parlent la langue choisie.
     *
     * L'enveloppe garde le contexte d'origine pour base, et ne détourne que les ressources.
     * C'est indispensable : `createConfigurationContext` rend un contexte détaché, sans lien de
     * parenté avec l'activité, et tout ce qui remonte jusqu'à elle en déroulant les enveloppes —
     * le sélecteur de fichiers du son de buzzer, par exemple — ne la trouverait plus.
     */
    fun wrap(base: Context): Context {
        val tag = tagOf(current) ?: return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
        }
        val localized = base.createConfigurationContext(configuration)
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = localized.resources
            override fun getAssets(): AssetManager = localized.assets
        }
    }
}
