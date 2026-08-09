package fr.buzzme.core

import java.security.SecureRandom
import java.util.Locale

object Codes {

    /** Alphabet sans caractères ambigus (0/O, 1/I/L, 2/Z, 5/S, 8/B). */
    private const val ALPHABET = "ACDEFGHJKMNPQRTUVWXY34679"
    private val random = SecureRandom()

    const val LENGTH = 5

    fun newRoomCode(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Normalise une saisie utilisateur : majuscules, sans espaces, caractères valides seulement. */
    fun normalize(input: String): String = input
        .uppercase(Locale.ROOT)
        .filter { it in ALPHABET }
        .take(LENGTH)

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    fun newPlayerId(): String = buildString(16) {
        repeat(16) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
