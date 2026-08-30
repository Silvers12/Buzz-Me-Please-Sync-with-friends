package com.osala.BuzzMePlease.core

import android.content.Context
import android.os.Build

/**
 * La version installée, telle que le système la connaît — et non une constante recopiée dans le
 * code, qui finirait par mentir.
 *
 * Le numéro sert à deux choses : le dire à l'auteur dans un rapport de problème, et vérifier que
 * tout le salon joue bien avec la même. Deux versions différentes autour d'une même table, ce
 * sont deux règles différentes.
 */
fun Context.appVersionCode(): Long = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
}.getOrDefault(0L)

/** « 1.06 », ou « 1.06-debug » sur une version de développement. */
fun Context.appVersionName(): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull().orEmpty()
