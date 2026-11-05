# Autoradio Launcher

Écran d'accueil ultra-léger pour autoradio Android (« head unit ») avec peu de RAM.
Seulement 5 grosses tuiles, une horloge et rien d'autre :

| Tuile | Ouvre |
|-------|-------|
| Waze | Waze |
| Spotify | Spotify (ou le lecteur de musique par défaut) |
| Téléphone | L'appli Bluetooth/téléphone de l'autoradio |
| Messages | L'appli SMS / messages |
| CarPlay | L'appli CarPlay (ZLink, AutoKit…) qui gère le dongle ou le boîtier CarPlay |

## Pourquoi c'est léger

- Aucune bibliothèque (pas d'AndroidX, pas de Kotlin) : l'APK fait quelques dizaines de Ko.
- Aucun service en arrière-plan, aucune notification, aucun widget, pas de fond d'écran animé.
- Fond en dégradé fixe, sans 3D, sans flou animé ni animations de transition.
- La photo est réduite à 1024 px et chargée en 16 bits : environ 1 Mo de RAM.
- L'horloge (`TextClock`) se met à jour toute seule une fois par minute, sans tâche en boucle.
- Quand Waze, Spotify ou CarPlay est au premier plan, le lanceur ne consomme presque rien.

## Installation

1. Télécharger l'APK (lien direct, sans compte GitHub) :
   <https://github.com/nicoletmathis-sketch/Ecc/releases/download/autoradio-latest/autoradio-launcher.apk>
2. L'ouvrir directement dans le navigateur de l'autoradio, ou le copier sur une clé USB, puis l'installer (autoriser les « sources inconnues »).
3. Appuyer sur le bouton Accueil, choisir **Autoradio** puis **Toujours**.
   Si l'autoradio ne le propose pas : *Paramètres → Applications → Applications par défaut → Appli d'accueil*.

## Utilisation

L'écran ressemble à CarPlay : une barre d'icônes à gauche, la photo de la voiture au centre,
l'heure et un gros bouton **CarPlay** à droite.

- **Appui court** sur une icône : ouvre l'application.
- **Appui long** sur une icône (ou sur le bouton CarPlay) : choisir l'application à utiliser
  (utile si votre appli CarPlay ou téléphone a un autre nom). Le choix est mémorisé.
- **Appui long sur la photo** : changer la photo, changer le nom affiché, ou revenir à la photo d'origine.
- **Icône grille** (en bas à gauche) : liste de toutes les applis, pour les cas ponctuels.
- **Appui long sur l'heure** (en haut à gauche) : ouvre les paramètres Android.

Si une appli n'est pas trouvée, le lanceur vous demande directement laquelle utiliser.

## CarPlay plus fluide

Le CarPlay sur un autoradio Android passe par une appli (souvent **ZLink** ou **AutoKit**) et
un dongle USB ou un module intégré. Pour qu'il soit fluide :

- Dans l'appli CarPlay, baisser la résolution/le débit vidéo si l'image saccade,
  et activer la connexion automatique.
- Préférer le CarPlay **filaire** (USB) au sans-fil si l'autoradio est lent.
- Fermer les autres applis avant de lancer CarPlay : avec CarPlay, Waze, Spotify, le téléphone
  et les messages tournent sur l'iPhone, donc il n'est pas nécessaire d'installer Waze ou
  Spotify sur l'autoradio. C'est le mode le plus économe en RAM.

## Alléger encore l'autoradio (optionnel)

Désactiver les applis préinstallées inutiles libère de la RAM. Avec un ordinateur et `adb` :

```bash
adb shell pm list packages            # voir les applis installées
adb shell pm disable-user --user 0 <nom.du.paquet>   # désactiver (réversible)
adb shell pm enable <nom.du.paquet>   # réactiver si besoin
```

Ne désactivez que ce que vous reconnaissez (jeux, navigateur, météo, ancien lanceur, etc.) :
évitez tout ce qui ressemble à `bluetooth`, `canbus`, `radio`, `systemui` ou au fabricant
(`syu`, `microntek`, `ts`…), sinon le téléphone, la radio ou les commandes au volant peuvent cesser
de fonctionner.

Dans les options développeur d'Android, mettre les 3 « échelles d'animation » sur **0.5x** ou
**désactivé** rend aussi l'interface plus réactive.

## Compiler soi-même

Prérequis : JDK 17 et le SDK Android (`ANDROID_HOME`).

```bash
cd autoradio-launcher
./gradlew assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

L'APK « release » est signé avec la clé de debug pour pouvoir être installé directement :
c'est fait pour un usage personnel, pas pour le Play Store.
