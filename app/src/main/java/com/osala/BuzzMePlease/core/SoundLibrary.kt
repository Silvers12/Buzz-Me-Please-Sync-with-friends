package com.osala.BuzzMePlease.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.osala.BuzzMePlease.R
import java.text.Collator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Un son embarqué : le nom du fichier sans extension, le libellé montré à l'écran, et son
 * chemin dans les assets (« sound/correct.mp3 »), ou l'URI d'un fichier importé.
 */
data class SoundClip(val id: String, val label: String, val path: String)

/**
 * Les sons livrés avec l'application, et ceux que le joueur apporte.
 *
 * **Un seul dossier.** Le buzzer et la sonothèque puisent dans le même : ce qu'on peut poser sur
 * une touche, on peut le mettre sur son buzzer, et réciproquement. Les séparer obligeait à
 * choisir pour l'utilisateur, et à livrer deux fois le même fichier quand il servait aux deux.
 *
 * **Ajouter un son** : déposer le fichier dans `app/src/main/assets/sound`. Rien d'autre à
 * faire, le dossier est lu à l'exécution. Le libellé se traduit en ajoutant une clé
 * `sound_<nom du fichier>` dans `strings.xml` ; sans elle, le nom du fichier s'affiche tel quel.
 */
object SoundLibrary {

    /** Le dossier des sons livrés avec le jeu. */
    const val FOLDER = "sound"

    /** Nombre de touches de la sonothèque : trois rangées de trois, à portée du pouce. */
    const val SLOTS = 9

    /**
     * Les sons livrés, classés par libellé.
     *
     * Le tri suit la langue affichée — un [Collator] plutôt qu'une comparaison de chaînes, pour
     * que « Bébé qui pleure » se range après « Battements de cœur » et non après le Z.
     */
    fun clips(context: Context): List<SoundClip> {
        val files = runCatching { context.assets.list(FOLDER) }.getOrNull().orEmpty()
        val collator = Collator.getInstance(AppLocale.locale)
        return files.mapNotNull { file ->
            val id = file.substringBeforeLast('.', missingDelimiterValue = "")
            if (id.isBlank()) return@mapNotNull null
            SoundClip(id, label(context, id), "$FOLDER/$file")
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /**
     * La bibliothèque complète : les sons du jeu, puis ceux apportés par l'utilisateur. Les
     * siens restent groupés à la fin plutôt que fondus dans le classement — il les a choisis,
     * il doit les retrouver sans parcourir la liste entière.
     *
     * Un fichier importé est identifié par son URI : c'est ce qui est enregistré sur la touche
     * ou dans le réglage du buzzer, et ce qui survit au redémarrage.
     */
    fun all(context: Context, imports: List<String>): List<SoundClip> {
        val collator = Collator.getInstance(AppLocale.locale)
        val mine = imports
            .map { uri -> SoundClip(id = uri, label = importedName(context, uri), path = uri) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        return clips(context) + mine
    }

    /**
     * Le nom du fichier importé, tel que le téléphone l'affiche. Faute de pouvoir l'interroger —
     * fichier déplacé, autorisation perdue — on annonce simplement « son importé ».
     */
    fun importedName(context: Context, uri: String): String = runCatching {
        context.contentResolver.query(
            Uri.parse(uri),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast('.') else null
        }
    }.getOrNull() ?: context.getString(R.string.settings_buzzer_imported)

    /**
     * Ramène un chemin enregistré par une version antérieure, du temps où les sons vivaient en
     * deux dossiers. Sans cela, le buzzer choisi avant la mise à jour redeviendrait muet.
     */
    fun migratePath(source: String): String = when {
        source.startsWith("buzzer/") -> "$FOLDER/${source.removePrefix("buzzer/")}"
        source.startsWith("soundbox/") -> "$FOLDER/${source.removePrefix("soundbox/")}"
        else -> source
    }

    /** Le chemin de la fanfare, jouée quand le pupitre change de main. */
    fun tadaaPath(context: Context): String? = pathOf(context, "tadaa")

    /** Le chemin du verre brisé, joué quand un joueur est éliminé. */
    fun glassPath(context: Context): String? = pathOf(context, "glass_breaking")

    /** Le chemin du feu d'artifice, tiré à la fin de la partie. */
    fun fireworksPath(context: Context): String? = pathOf(context, "fireworks")

    /** Le chemin du son « mauvaise réponse », joué chez celui qui perd la parole. */
    fun wrongPath(context: Context): String? = pathOf(context, "wrong")

    /** Le chemin du son « bonne réponse », joué chez celui qui vient de marquer. */
    fun correctPath(context: Context): String? = pathOf(context, "correct")

    /**
     * Le chemin d'un son livré, sans passer par [clips] : celui-ci traduit et trie les
     * trente-six libellés, ce qui n'a aucun intérêt pour retrouver un fichier — et se
     * paierait sur le fil principal, à l'instant précis où l'annonce s'anime.
     *
     * Les fichiers livrés ne bougent pas : la table est construite une fois.
     */
    @Volatile
    private var assetPaths: Map<String, String>? = null

    private fun pathOf(context: Context, id: String): String? {
        val known = assetPaths ?: runCatching { context.assets.list(FOLDER) }
            .getOrNull().orEmpty()
            .mapNotNull { file ->
                val key = file.substringBeforeLast('.', missingDelimiterValue = "")
                if (key.isBlank()) null else key to "$FOLDER/$file"
            }
            .toMap()
            .also { assetPaths = it }
        return known[id]
    }

    /** Le libellé traduit du son, ou son nom de fichier rendu lisible faute de traduction. */
    private fun label(context: Context, id: String): String {
        @Suppress("DiscouragedApi")
        val labelId = context.resources.getIdentifier("sound_$id", "string", context.packageName)
        if (labelId != 0) return context.getString(labelId)
        return id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

/**
 * Lecteur de sons. Un seul à la fois : réappuyer coupe le précédent, ce qui évite l'empilement
 * de jingles quand l'animateur enchaîne les manches.
 *
 * Il lit aussi bien un son embarqué qu'un fichier choisi par l'utilisateur sur son téléphone,
 * d'où la source en texte : un chemin d'asset, ou une URI `content://`.
 */
class ClipPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    /** Identifiant du son en cours : la touche correspondante s'allume tant qu'il joue. */
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    fun play(clip: SoundClip, onFinished: () -> Unit = {}) = play(clip.path, clip.id, onFinished)

    fun play(source: String, tag: String = source, onFinished: () -> Unit = {}) {
        stop()
        val created = open(appContext, source) ?: return
        created.setOnCompletionListener {
            _playing.value = null
            onFinished()
            it.release()
            if (player === it) player = null
        }
        player = created
        _playing.value = tag
        runCatching { created.start() }
    }

    fun stop() {
        val current = player ?: return
        player = null
        _playing.value = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    fun release() = stop()
}

/**
 * Prépare un lecteur sur la source demandée, prêt à démarrer, ou null si elle est illisible —
 * un fichier importé peut avoir été supprimé ou déplacé depuis, et le jeu doit continuer.
 */
internal fun open(context: Context, source: String): MediaPlayer? = runCatching<MediaPlayer> {
    val player = MediaPlayer()
    player.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
    )
    if (source.startsWith("content://") || source.startsWith("file://")) {
        player.setDataSource(context, Uri.parse(source))
    } else {
        context.assets.openFd(source).use { fd ->
            player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
    }
    player.prepare()
    player
}.onFailure { Log.w("SoundLibrary", "son illisible : $source", it) }.getOrNull()
