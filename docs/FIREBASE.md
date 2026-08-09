# Mode en ligne (Firebase) — optionnel

Le mode **Wi-Fi local** ne demande aucune configuration : il fonctionne hors connexion, entre
appareils du même réseau, et c'est lui qui offre la meilleure précision.

Le mode **En ligne** ne sert que de repli, quand le Wi-Fi local n'est pas partageable :
données mobiles, réseau d'entreprise ou hôtel qui isole les appareils entre eux, joueurs à
distance. Il repose sur Firebase Realtime Database.

L'application ne contient **aucun** fichier `google-services.json` : les identifiants se saisissent
dans l'écran *Réglages → Mode en ligne*. Chacun peut donc brancher son propre projet Firebase
sans recompiler, et le dépôt ne contient aucune clé.

## 1. Créer le projet

1. Ouvrir <https://console.firebase.google.com> et créer un projet (l'offre gratuite Spark suffit
   très largement pour des soirées quiz).
2. Dans **Compilation → Realtime Database**, cliquer sur *Créer une base de données*.
   Choisir la région la plus proche de vous — elle détermine la latence du buzz.
   Démarrer en **mode verrouillé**, les règles sont fournies plus bas.
3. Dans **Compilation → Authentication → Sign-in method**, activer le fournisseur **Anonyme**.
   L'application ouvre une session anonyme au démarrage ; c'est ce qui rend les règles ci-dessous
   applicables sans demander de compte aux joueurs.
4. Dans **Paramètres du projet → Général**, ajouter une application **Android**.
   Le nom de package n'a pas besoin de correspondre : renseignez `fr.buzzme`.

## 2. Relever les quatre valeurs

Toujours dans **Paramètres du projet → Général**, ouvrez le `google-services.json` proposé au
téléchargement (ou lisez les champs affichés) et relevez :

| Réglage dans l'app | Où le trouver                                                     |
|--------------------|-------------------------------------------------------------------|
| ID du projet       | `project_info.project_id` — ex. `buzzme-quiz`                      |
| ID de l'application| `client[0].client_info.mobilesdk_app_id` — ex. `1:123…:android:ab…` |
| Clé API            | `client[0].api_key[0].current_key`                                 |
| URL de la base     | `project_info.firebase_url` — ex. `https://buzzme-quiz-default-rtdb.europe-west1.firebasedatabase.app` |

Saisissez-les dans **Réglages → Mode en ligne**, puis validez. Elles sont conservées localement
sur l'appareil. Chaque joueur doit renseigner **le même projet** pour se retrouver dans le
même salon — le plus simple est de partager les quatre valeurs une fois pour toutes.

## 3. Publier les règles de sécurité

Copier le contenu de [`firebase/database.rules.json`](../firebase/database.rules.json) dans
**Realtime Database → Règles**, puis publier.

Ces règles :

- ferment la base par défaut ;
- n'ouvrent `rooms/{CODE}` qu'aux utilisateurs authentifiés (session anonyme) ;
- valident la forme de chaque champ (statuts, états de manche, longueur des pseudos) ;
- refusent tout champ inconnu, ce qui évite qu'un client bricolé n'écrive n'importe quoi ;
- interdisent d'enregistrer un buzz pour un joueur absent du salon.

Elles ne distinguent volontairement pas l'hôte des joueurs : dans un salon rejoint par code entre
amis, cette granularité coûterait une table de correspondance `uid ↔ joueur` sans bénéfice réel.
Si vous ouvrez vos salons à des inconnus, ajoutez cette correspondance et restreignez l'écriture
de `round` et `players/*/score` au seul `meta/hostId`.

## 4. Ménage

Un salon est supprimé quand son hôte le quitte. Si un salon reste orphelin (application tuée
brutalement), il suffit de le supprimer à la main dans la console, ou d'ajouter une fonction
planifiée qui efface les salons dont `meta/createdAt` remonte à plus de 24 h.

## Précision attendue

| Mode        | Base de temps                     | Incertitude typique |
|-------------|-----------------------------------|---------------------|
| Wi-Fi local | horloge de l'hôte, sondée en NTP  | 1 à 5 ms            |
| En ligne    | horloge serveur Firebase          | 15 à 60 ms          |

En ligne, deux réflexes séparés par moins de ~30 ms ne sont donc pas réellement départageables.
L'application affiche l'incertitude à côté du temps pour que l'hôte le sache et puisse trancher.
Pour un jeu où le buzz compte vraiment, préférez le mode local.
