package com.osala.BuzzMePlease.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.osala.BuzzMePlease.R
import java.util.Locale

/**
 * Tout ce qui mène hors de l'application : la fiche du Store, le compte de l'auteur, son adresse.
 *
 * Réunis ici parce que deux écrans s'en servent, et qu'une adresse recopiée est une adresse qui
 * finit par diverger.
 */
object Links {

    /**
     * Le paquet publié. La variante de développement porte un suffixe qui n'existe pas sur le
     * Store : c'est bien la fiche du jeu qu'il faut ouvrir, pas une page introuvable.
     */
    const val STORE_ID = "com.osala.buzzmeplease"

    const val DONATE = "https://paypal.me/SalaOlivier"

    const val SUPPORT_MAIL = "silversvsil@gmail.com"
}

/**
 * Ouvre la fiche du jeu sur le Play Store, à l'endroit où l'on peut le noter. On tente d'abord
 * l'application Play — c'est elle qui porte les étoiles — puis le site, pour les appareils qui
 * ne l'ont pas.
 */
fun Context.openStorePage() {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Links.STORE_ID}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(app) }.isSuccess) return
    openLink("https://play.google.com/store/apps/details?id=${Links.STORE_ID}")
}

/**
 * Ouvre le courrielleur sur un message déjà rempli : modèle, version d'Android, version de
 * l'application. Un rapport sans ces trois lignes oblige à un aller-retour, et la personne qui
 * l'écrit n'a en général aucune idée d'où les trouver.
 *
 * On n'interroge pas le système pour savoir s'il existe une application de courriel : depuis
 * Android 11 la réponse serait « non » sans une déclaration `queries`, alors que l'ouverture,
 * elle, fonctionne. On tente, et on retombe sur le lien `mailto:` nu en cas d'échec.
 */
fun Context.startSupportMail() {
    val version = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    }.getOrDefault("?")

    val subject = getString(R.string.settings_contact_subject, version)
    val body = getString(
        R.string.settings_contact_body,
        "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        Build.VERSION.RELEASE,
        Build.VERSION.SDK_INT,
        version,
        Locale.getDefault().toLanguageTag(),
    )

    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Links.SUPPORT_MAIL}")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { startActivity(mail) }.isSuccess) return
    openLink("mailto:${Links.SUPPORT_MAIL}")
}

/** Ouvre un lien avec ce que l'appareil a sous la main. Rien ne se passe s'il n'a rien. */
fun Context.openLink(url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(view) }
}
