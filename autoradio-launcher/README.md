# Autoradio Launcher

Écran d'accueil façon **Apple CarPlay** pour autoradio Android (« head unit ») avec peu de RAM.
Fond noir, cartes gris anthracite, icônes style iOS, et deux écrans :

**Tableau de bord**
- Salutation (Bonjour / Bon après-midi / Bonsoir), heure en grand, date.
- **Météo locale** (Open-Meteo, gratuit, sans compte), mise à jour toutes les 30 min.
- L'A3 noire et la figurine PLK.
- **À l'écoute** : titre, artiste, pochette et progression du morceau en cours, avec les boutons.
- **Maison / Travail** : un appui lance l'itinéraire dans Waze.
- **Vitesse GPS** en km/h avec la direction.
- Gros bouton **CarPlay** (ZLink).

**Applis** : grille d'icônes (Waze, Spotify, Téléphone, Messages, CarPlay, Radio, Bluetooth,
Fichiers, Réglages, Toutes les applis).

Barre latérale toujours visible : heure, réseau, Waze, Spotify, Téléphone, Messages, et le bouton
pour changer d'écran (on peut aussi glisser le doigt vers la gauche ou la droite).

## Pourquoi c'est léger

- Aucune bibliothèque (pas d'AndroidX, pas de Kotlin) : l'APK fait environ 125 Ko.
- Aucun service en arrière-plan : GPS et progression de la musique s'arrêtent dès qu'une autre appli
  (CarPlay, Waze…) passe devant.
- Fond noir uni, aucune animation, pas de flou en temps réel.
- La scène voiture + figurine est une image fixe (environ 2 Mo de RAM), pas de 3D.
- Météo : une petite requête au plus toutes les 30 minutes, résultat gardé en mémoire.

## Installation

1. Télécharger l'APK (lien direct, sans compte GitHub) :
   <https://github.com/nicoletmathis-sketch/Ecc/releases/download/autoradio-latest/autoradio-launcher.apk>
2. L'ouvrir directement dans le navigateur de l'autoradio, ou le copier sur une clé USB, puis l'installer (autoriser les « sources inconnues »).
3. Appuyer sur le bouton Accueil, choisir **Autoradio** puis **Toujours**.
   Si l'autoradio ne le propose pas : *Paramètres → Applications → Applications par défaut → Appli d'accueil*.

## Autorisations (une seule fois)

- **Localisation** : demandée au premier lancement, pour la vitesse et la météo.
  Si vous avez refusé : touchez la carte vitesse ou la météo pour la redemander.
- **Accès aux notifications** : pour afficher le vrai titre et la pochette du morceau.
  Touchez la carte « À l'écoute » → activez **Autoradio** dans la liste → confirmez.
  Sans cet accès, les boutons lecture / suivant / précédent marchent quand même.

## Utilisation

- **Appui court** sur une icône : ouvre l'application.
- **Appui long** sur une icône, sur une case de la grille ou sur la carte CarPlay : choisir
  l'application à utiliser (utile si votre appli CarPlay, Radio ou Téléphone a un autre nom).
- **Maison / Travail** : au premier appui, tapez l'adresse ; ensuite un appui lance Waze.
  Appui long pour changer ou effacer l'adresse.
- **Carte « À l'écoute »** : toucher ouvre l'appli qui joue (ou Spotify).
- **Badge PLK** : ouvre PLK dans Spotify.
- **Appui long sur la grande carte** (voiture) : changer l'image, le nom, ou revenir à l'image d'origine.
- **Glisser à gauche / à droite** ou bouton en bas de la barre : changer d'écran.
- **Bouton Retour / Accueil** : revient au tableau de bord.
- **Appui long sur l'heure** (barre latérale) : paramètres Android.

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
