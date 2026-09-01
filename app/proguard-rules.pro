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
