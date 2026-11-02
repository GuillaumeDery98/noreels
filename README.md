# NoReels

Application Android qui bloque les vidéos courtes **dans les applications officielles**, sans changer ta façon de les utiliser : Reels Instagram, Shorts YouTube, Reels Facebook, Spotlight Snapchat, TikTok, et leurs équivalents dans Brave et Chrome.

Principe : un service d'accessibilité reconnaît les écrans de vidéos courtes à partir des identifiants de vues, puis déclenche un « retour » global (ou « accueil » si le retour ne suffit pas). Il ne clique jamais dans les applications et n'agit jamais sur tes comptes. L'application n'a **pas** la permission Internet : rien ne quitte le téléphone.

## Prérequis

- JDK 21 : `brew install openjdk@21`
- SDK Android (plateforme 37, build-tools 37) dans `~/Library/Android/sdk`
- `local.properties` à la racine (non versionné) :

```
sdk.dir=/Users/<toi>/Library/Android/sdk
```

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest   # tests JVM
./gradlew :app:assembleDebug       # APK : app/build/outputs/apk/debug/app-debug.apk
```

## Installation sur le téléphone

1. Brancher le téléphone (débogage USB activé), puis :

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   Une installation via `adb` n'est pas soumise au blocage « paramètre restreint » d'Android 13+. Si l'APK est installé autrement (fichier téléchargé), il faut d'abord aller dans *Infos de l'appli → ⋮ → Autoriser les paramètres restreints*.

2. Ouvrir NoReels → **Activer dans les réglages** → *Applications téléchargées* → *NoReels – blocage des Reels* → activer.
3. Facultatif : **Autoriser l'accès** (statistiques d'utilisation) pour afficher le temps passé par application.

## Temps de Reels par jour

La carte **Temps de Reels par jour** de l'écran d'accueil fixe une réserve quotidienne (0 h 00 à 23 h 59), commune à toutes les plateformes. Tant qu'elle n'est pas épuisée, les vidéos courtes restent accessibles et le temps passé dessus est décompté ; ensuite, le blocage habituel reprend jusqu'à minuit. Par défaut, la réserve vaut 0 : blocage immédiat.

- Seul le temps passé sur un écran bloquable compte (onglet Reels, Short, Spotlight, TikTok, URL dans le navigateur), écran allumé. Une vidéo en pause sur ces écrans compte aussi.
- Un écran désactivé par son interrupteur n'est ni bloqué ni décompté.
- L'écran d'accueil affiche le temps utilisé avec un décalage de 15 s au plus (écriture périodique).
- Le faux positif connu de Facebook (onglet « Reels » d'un profil) consomme la réserve comme un vrai Reel.

## Test sur appareil (environ 5 minutes)

Les tests automatisés valident la logique sur deux captures réelles d'Instagram 453 (fil d'accueil et onglet Reels). Les comportements ci-dessous dépendent des versions installées et doivent être vérifiés sur le téléphone.

| # | Action | Résultat attendu | Fiabilité de la règle |
|---|--------|------------------|-----------------------|
| 1 | Instagram → onglet Reels | Retour immédiat + toast « Bloqué par NoReels » | Vérifiée sur capture réelle |
| 2 | Instagram → toucher un Reel dans le fil | Retour au fil | Vérifiée sur capture réelle |
| 3 | Instagram → fil d'accueil | Aucun blocage | Vérifiée sur capture réelle |
| 3b | Instagram → stories, messages, profil | Aucun blocage | **À vérifier** (non capturés : zones sensibles aux faux positifs) |
| 4 | YouTube (app) → onglet Shorts | Retour | Identifiants repris de SocialLite, à vérifier |
| 5 | Snapchat → Spotlight | Retour | Identifiants repris de SocialLite, à vérifier |
| 6 | Facebook → onglet Reels | Retour | **Expérimental** (hypothèse sur la description d'accessibilité) |
| 6a | Facebook → toucher un Reel du carrousel « Reels » dans le fil | Retour au fil (le carrousel lui-même n'est pas bloqué) | Vérifiée sur capture réelle (Facebook 585, interface en français : description « Détails du reel ») |
| 6b | Facebook → onglet « Reels » d'un profil | Risque de blocage (faux positif connu de la règle) : désactiver « Reels (expérimental) » si gênant | **Expérimental** |
| 7 | Ouvrir TikTok | Retour à l'écran d'accueil du téléphone | Fiable (toute l'app) |
| 8 | Brave ou Chrome → `youtube.com/shorts/…` | Retour | Vérifié sur émulateur (Brave 1.98.62 et Chrome) : la barre d'adresse contient le chemin |
| 8b | Brave ou Chrome → taper « tiktok.com » dans la barre, ou chercher « tiktok » | Aucun blocage pendant la saisie ni sur la page de résultats | Testé (JVM) |
| 9 | Désactiver « Reels » d'Instagram dans NoReels, refaire 1 | Pas de blocage | Testé (JVM) |
| 10 | Écran d'accueil NoReels | Compteur « Aujourd'hui » incrémenté | Testé (JVM) |
| 11 | Réserve à 1 min, ouvrir un Short | Pas de blocage pendant environ 1 min, puis retour ; la carte affiche « 1 min sur 1 min » | Vérifié sur émulateur |

## Mettre à jour une règle

Toutes les signatures sont dans [`app/src/main/res/raw/blocking_rules.json`](app/src/main/res/raw/blocking_rules.json), sans aucune valeur dans le code Kotlin.

Pour trouver l'identifiant d'un écran après une mise à jour d'une application :

```bash
# écran ouvert sur le téléphone, puis :
adb shell dumpsys window | grep mCurrentFocus          # → package/activité au premier plan
adb shell dumpsys activity activity <package/activité> > dump.txt
grep -o 'app:id/[a-z_]*' dump.txt | sort -u            # identifiants des vues
```

`uiautomator dump` échoue souvent sur les écrans vidéo (« could not get idle state ») ; `dumpsys activity` fonctionne. Un identifiant `app:id/x` s'écrit `<package>:id/x` dans le JSON. Pour ajouter un test sur capture réelle, extraire la section `View Hierarchy:` dans `app/src/test/resources/fixtures/` (voir `SurfaceDetectorTest`).

Facebook masque ses identifiants de vues (`(name removed)`) : ses règles reposent sur les descriptions d'accessibilité, qui dépendent de la langue du téléphone. Pour les capturer, `adb shell uiautomator dump /sdcard/ui.xml` puis `adb pull /sdcard/ui.xml` ; le fichier XML se charge tel quel dans les tests avec `UiAutomatorFixture`.

Types de règle :

- `nodes` : un nœud visible correspond (`viewId` exact, `textRegex`, `contentDescriptionRegex`, `selected`), avec une taille minimale relative à l'écran (`minWidthRatio`, `minHeightRatio`) ;
- `wholeApp` : toute l'application ;
- `url` : la barre d'adresse d'un navigateur listé dans `browsers` contient l'un des `urlPatterns`.

Délais : `debounceMs` (ignore les rafales d'événements), `escalationWindowMs` (si l'écran est toujours là après un retour, passage à « accueil »), `contentEvaluationIntervalMs` (fréquence d'analyse maximale par application), `allowanceTickMs` (vérification pendant le visionnage dans la réserve), `allowanceMaxGapMs` (écart maximal compté entre deux vérifications), `allowanceFlushMs` (fréquence d'écriture du temps visionné).

## Limites connues

- Les identifiants changent avec les mises à jour des applications : d'où le fichier de règles.
- Les Reels reçus en message privé sur Instagram sont eux aussi bloqués.
- Pas de masquage visuel des onglets : le blocage intervient à l'ouverture.
- Non destiné au Play Store en l'état (la politique sur l'accessibilité exige une déclaration).

## Architecture

```
app/src/main/java/app/noreels/
├── rules/       modèle + parseur validant blocking_rules.json
├── detection/   ScreenNode (abstraction), SurfaceDetector, BlockDecider — Kotlin pur, testé en JVM
├── allowance/   AllowanceMeter (temps visionné du jour), décision du tick — Kotlin pur, testé en JVM
├── service/     ReelsBlockerService (accessibilité), adaptateur AccessibilityNodeInfo, statut
├── data/        DataStore (réglages, réserve, blocages et temps visionné du jour), temps d'usage (UsageStatsManager)
└── ui/          écran d'accueil Compose + ViewModel
```
