# Règles R8 pour la version publiée.
#
# Le jeu ne survit à l'obscurcissement qu'à une condition : que les classes du protocole gardent
# de quoi se sérialiser. Tout le reste — écrans, moteur de jeu, sons — peut être renommé et
# élagué sans dommage.

# kotlinx.serialization ------------------------------------------------------
# Les sérialiseurs sont générés à la compilation sous forme de classes internes `$$serializer` et
# de méthodes `serializer()` sur le Companion. Rien ne les appelle par leur nom dans le code : R8
# les croirait inutiles et les supprimerait, et le salon ne saurait plus lire une seule ligne.
-keepattributes *Annotation*, InnerClasses

-keepclassmembers class com.osala.BuzzMePlease.** {
    *** Companion;
}
-keepclasseswithmembers class com.osala.BuzzMePlease.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.osala.BuzzMePlease.**$$serializer { *; }

# Les noms des valeurs d'énumération voyagent en clair dans le JSON (« DUEL », « ARMED »,
# « YELLOW_CARD »…). Renommées, elles ne seraient plus relues à l'autre bout.
-keepclassmembers enum com.osala.BuzzMePlease.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Firebase Crashlytics -------------------------------------------------------
# Les regles ci-dessus laissent R8 renommer tout ce qui n'est pas le protocole,
# ce qui est voulu. Encore faut-il pouvoir relire les traces : sans ces deux
# attributs, R8 retire le nom du fichier source et les numeros de ligne, et un
# rapport arrive sans le « at Foo.kt:42 » qui le rend exploitable, meme avec le
# mapping envoye. `proguard-android-optimize.txt` les conserve deja ; on les
# redeclare pour que la garantie ne depende pas du fichier fourni par l'AGP.
-keepattributes SourceFile,LineNumberTable

# Masque l'arborescence reelle dans l'APK tout en gardant la correspondance
# dans le mapping envoye : traces lisibles en console, structure du projet non
# exposee dans le binaire.
-renamesourcefileattribute SourceFile

# Crashlytics regroupe les non-fatales par type d'exception. Obfusquee en
# `a.a.b`, l'exception maison qui porte les anomalies applicatives serait
# impossible a distinguer dans la console.
-keep class com.osala.BuzzMePlease.core.AppAnomalyException { *; }
