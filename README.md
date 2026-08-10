# Buzz Me

Buzzer de quiz pour Android, synchronisé entre amis. L'animateur crée un salon, les autres le
rejoignent avec un code à 5 lettres, et tout le monde joue sur son téléphone comme sur un
plateau de jeu télévisé.

Le mode principal fonctionne **en Wi-Fi local, sans Internet et sans serveur**. Le mode en ligne
(Firebase) n'est qu'un repli pour jouer à distance.

## Ce que ça fait

- **Salon par code.** L'animateur crée, les autres rejoignent. En Wi-Fi local, le salon est
  détecté automatiquement (mDNS) : un appui suffit, sans même taper le code.
- **Le top.** Un décompte 3 · 2 · 1 se déclenche au même instant sur tous les téléphones, puis
  les buzzers passent au vert.
- **Le buzz.** Le premier qui appuie passe au rouge et verrouille tous les autres. Son heure
  exacte — heure, minute, seconde, milliseconde — remonte à l'animateur.
- **Le plateau de l'animateur.** Pseudos, état de chaque buzzer, heure du buzz, temps de
  réaction, écart avec le meilleur, marge d'incertitude, qualité de la liaison.
- **Élimination.** Buzzer noir en un appui, réactivation quand l'animateur le décide.
- **Scores.** +1, +3, −1 par joueur, remise à zéro globale.
- **Relance / reset.** Nouvelle manche immédiate, même si un buzzer est déjà pris.
- **Passation d'animation.** Le rôle change de téléphone sans que personne ne quitte le salon,
  scores et statuts compris.
- **Pseudos modifiables** à tout moment.
- **Deux modes de jeu.** *Duel* (le premier verrouille tout le monde) ou *Course* (tout le monde
  buzze, classement complet) — utile pour un tie-break ou une épreuve de rapidité.
- **Tutoriel intégré**, ouvert automatiquement au premier lancement.

## La partie délicate : départager deux réflexes

Deux joueurs peuvent buzzer à 8 ms d'intervalle. Trois précautions rendent ce verdict honnête.

**1. On mesure l'appui, pas le téléphone.** L'horodatage est lu sur l'événement tactile
lui-même (`PointerInputChange.uptimeMillis`), pas au moment où l'application y réagit. Le temps
de recomposition et la charge de l'appareil ne se retrouvent donc pas dans le chrono du joueur.

**2. On ramène tout sur une seule horloge.** Chaque joueur sonde l'hôte en continu, façon NTP :
aller-retour mesuré, décalage estimé, et on ne garde que l'échantillon dont l'aller-retour a été
le plus court — celui qui a le moins souffert des files d'attente Wi-Fi. Sur un réseau local,
l'erreur résiduelle tombe à quelques millisecondes. C'est cette heure corrigée qui est comparée.

**3. Photo-finish.** Le verrouillage part immédiatement pour l'effet visuel, mais l'hôte garde la
fenêtre ouverte 350 ms de plus : un buzz parti *avant* que le verrouillage ne l'atteigne est
encore accepté, et c'est le meilleur temps qui gagne — jamais « celui dont le paquet est arrivé
en premier ». Le classement se corrige tout seul pendant cette fenêtre, signalée à l'écran.

Incertitude typique : **1 à 5 ms en Wi-Fi local**, 15 à 60 ms en ligne. Elle est affichée à côté
du temps : en dessous, deux réflexes sont à égalité et c'est à l'animateur de trancher.

## Comment ça communique

### Wi-Fi local (par défaut, recommandé)

Topologie en étoile autour de l'animateur, sans aucun serveur :

- l'hôte ouvre un `ServerSocket` sur le port **47821** et annonce le salon en **mDNS/NSD**
  (`_buzzme._tcp`, code du salon dans les attributs) ;
- chaque joueur ouvre une connexion **TCP persistante** avec `TCP_NODELAY` — un buzz part
  immédiatement, sans attendre le remplissage d'un tampon ;
- les messages sont du **JSON délimité par des sauts de ligne** (kotlinx.serialization) ;
- l'hôte diffuse l'état complet du salon à chaque changement. Il est petit, et cela rend toute
  divergence entre appareils impossible ;
- une coupure réseau déclenche une reconnexion automatique, avec redécouverte mDNS si l'adresse
  de l'hôte a changé. L'identifiant du joueur étant stable, il retrouve son score et son statut.

La **passation d'animation** bascule le serveur d'un téléphone à l'autre : l'ancien hôte diffuse
l'état complet et l'adresse du nouvel animateur, tout le monde s'y reconnecte, et l'ancien
animateur redevient un joueur ordinaire.

### En ligne (repli)

Firebase Realtime Database, base de temps commune fournie par `.info/serverTimeOffset`. Le
verrouillage suit directement la publication du premier buzz, sans attendre une écriture de
l'hôte, ce qui économise un aller-retour au moment le plus critique.

Aucun `google-services.json` n'est embarqué : les identifiants se saisissent dans l'application
(*Réglages → Mode en ligne*). Le dépôt ne contient donc aucune clé, et chacun branche son propre
projet. Voir **[docs/FIREBASE.md](docs/FIREBASE.md)** et les règles de sécurité fournies dans
[`firebase/database.rules.json`](firebase/database.rules.json).

## Compiler

Prérequis : Android Studio (Ladybug ou plus récent) ou le SDK Android en ligne de commande,
JDK 17. `minSdk 26`, `compileSdk 35`.

```bash
git clone <ce dépôt>
cd Buzz-Me-Please-Sync-with-friends
./gradlew assembleDebug          # APK dans app/build/outputs/apk/debug/
./gradlew installDebug           # sur un appareil branché
```

`assembleRelease` produit également un APK installable : il est signé avec la clé de debug pour
qu'un clone se compile sans mise en place de keystore. Remplacez cette configuration par la
vôtre avant toute publication.

Les tests unitaires du moteur de jeu, du protocole et de la synchronisation d'horloge tournent
sans appareil ni émulateur :

```bash
./gradlew test
```

### APK par intégration continue

[`.github/workflows/build.yml`](.github/workflows/build.yml) compile l'APK de debug sur un runner
GitHub et le publie en artefact (`buzz-me-debug`), avec le rapport de tests. Le workflow se
déclenche à chaque push et peut aussi être lancé à la main depuis l'onglet *Actions*.

Il faut pour cela que GitHub Actions soit actif sur le dépôt : sur un dépôt **privé**, les runs
consomment des minutes et échouent immédiatement (`startup_failure`, durée nulle) si le quota est
épuisé ou si la limite de dépense est à zéro. À vérifier dans *Settings → Actions → General* et
dans *Billing → Spending limits*. Sur un dépôt public, Actions est gratuit et sans quota.

## Organisation du code

```
app/src/main/java/fr/buzzme/
├── core/            horloges, code de salon, réglages persistants, sons et vibrations
├── model/           Player, RoomState, Buzz, options — sérialisables, sans dépendance Android
├── game/            GameEngine (moteur faisant autorité) et contrat RoomSession
├── net/
│   ├── Protocol.kt  messages du protocole local
│   ├── lan/         serveur/client TCP, découverte mDNS, synchronisation d'horloge
│   └── online/      session Firebase et configuration à l'exécution
└── ui/              thème « plateau », buzzer, plateau de l'animateur, écrans
```

Le cœur du jeu (`model`, `game`) ne connaît ni Android ni le réseau : les deux transports
implémentent la même interface `RoomSession`, et l'interface graphique ne sait pas lequel des
deux elle utilise.

## Conseils de partie

- Tout le monde sur le **même Wi-Fi** en mode local — pas un joueur en données mobiles.
- Activez **« Garder l'écran allumé »** dans les réglages avant de commencer.
- Certains réseaux publics isolent les appareils entre eux (*client isolation*) et bloquent le
  mode local : un partage de connexion depuis le téléphone de l'animateur règle le problème.
