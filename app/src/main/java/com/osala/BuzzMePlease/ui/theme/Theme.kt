package com.osala.BuzzMePlease.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Palette « plateau télé » : une scène très sombre, des projecteurs violets, et de l'or pour
 * tout ce qui est officiel (le code du salon, le vainqueur, les scores). Les buzzers reprennent
 * le code couleur universel du jeu : vert on peut, rouge c'est parti, noir on est éliminé.
 */
object Stage {
    val Deep = Color(0xFF05050C)
    val Night = Color(0xFF0B0B18)
    val Panel = Color(0xFF15152A)
    val PanelHigh = Color(0xFF1F1F3A)
    val Line = Color(0xFF2C2C4E)

    val Gold = Color(0xFFF5C542)
    val GoldSoft = Color(0xFFFFE7A3)
    val Violet = Color(0xFF7C5CFF)
    val VioletSoft = Color(0xFFB9A6FF)
    val Cyan = Color(0xFF35E2FF)

    val Green = Color(0xFF24E07A)
    val GreenDeep = Color(0xFF0B7A40)
    val Red = Color(0xFFFF2E4E)
    val RedDeep = Color(0xFF8C0C22)
    val Amber = Color(0xFFFFA726)

    val TextPrimary = Color(0xFFF3F1FF)
    val TextSecondary = Color(0xFFA6A4C8)
    val TextMuted = Color(0xFF6F6D92)

    val Medal = listOf(Color(0xFFF5C542), Color(0xFFC9CBD6), Color(0xFFCD7F32))
}

private val BuzzColorScheme = darkColorScheme(
    primary = Stage.Violet,
    onPrimary = Color.White,
    primaryContainer = Stage.PanelHigh,
    onPrimaryContainer = Stage.TextPrimary,
    secondary = Stage.Gold,
    onSecondary = Color(0xFF241A00),
    tertiary = Stage.Cyan,
    background = Stage.Night,
    onBackground = Stage.TextPrimary,
    surface = Stage.Panel,
    onSurface = Stage.TextPrimary,
    surfaceVariant = Stage.PanelHigh,
    onSurfaceVariant = Stage.TextSecondary,
    outline = Stage.Line,
    error = Stage.Red,
    onError = Color.White,
)

/** Titres larges et espacés, comme un générique d'émission. */
private val BuzzTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 1.5.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    ),
)

/** Chiffres à chasse fixe : les millisecondes ne doivent pas danser d'une manche à l'autre. */
val MonoDigits = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    letterSpacing = 0.sp,
)

@Composable
fun BuzzMeTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Le plateau est toujours sombre : c'est ce qui fait ressortir les buzzers.
    MaterialTheme(
        colorScheme = BuzzColorScheme,
        typography = BuzzTypography,
        content = content,
    )
}
