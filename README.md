# Cobblemon Undo — bouton « Annuler » en combat

Pour **Cobblemon 1.8.x** (Fabric, Minecraft 1.21.1). Ajoute un bouton **Annuler** en bas à gauche
de l'écran de combat, là où apparaissent normalement Attaque / Sac / Pokémon / Fuite.

## Deux modes (choisis automatiquement)

**1. Tu es le seul à avoir le mod (serveur normal)**
Quand tu valides ton attaque ou ton switch, ton choix est **retenu 6 secondes** sur ton PC :
*« Envoi dans 5,4 s... »* + une barre de progression + les boutons **Annuler** et **Envoyer**.
- **Annuler** → le menu de choix revient, tu peux choisir autre chose.
- **Envoyer** → part tout de suite, sans attendre.
- Sinon le choix part tout seul à la fin du délai.

Pourquoi un délai ? Sans le mod sur le serveur, un choix déjà envoyé ne peut plus être repris :
Cobblemon refuse tout nouveau choix jusqu'à la fin du tour. Le mod retient donc ton choix
**avant** qu'il parte. Ça ne change rien pour le serveur : c'est comme si tu avais cliqué 6 s plus tard.

**2. Le serveur a aussi le mod (ou tu joues en solo / LAN)**
Comme sur Showdown : après ton choix, *« En attente de l'adversaire... »* + **Annuler**, utilisable
tant que l'adversaire n'a pas joué. Pas de délai.

## Infos

- Raccourci clavier : **Retour arrière** (Options › Commandes › Cobblemon Undo).
- Par défaut, ça ne marche qu'en **combat contre un joueur**.
- Un objet du sac ou une Poké Ball n'est jamais retenu : le serveur l'a déjà pris en compte.
- Réglages dans `.minecraft/config/cobblemon_undo.json` (créé au premier lancement) :
  - `"delaiSecondes": 6.0` → durée du délai (mode 1). `0` = désactivé.
  - `"aussiContrePnjEtSauvages": false` → `true` pour avoir aussi le délai contre les PNJ et les Pokémon sauvages.

## Télécharger le .jar

Après chaque compilation réussie, le .jar est rangé dans le dossier **`telechargement/`** du dépôt :
clique dessus, puis sur le bouton **Download raw file** (flèche ↓ en haut à droite).

## Obtenir le .jar avec GitHub (sans rien installer)

1. Crée un compte sur https://github.com puis clique sur **New repository**
   (nom : `cobblemon-undo`) → **Create repository**.
2. Sur la page du dépôt vide, clique sur **uploading an existing file**.
3. Dézippe `cobblemon-undo.zip`, ouvre le dossier `cobblemon-undo`, sélectionne **tout son contenu**
   (y compris le dossier `.github`) et glisse-le dans la page. Clique **Commit changes**.
4. Onglet **Actions** : la compilation « Compiler le mod » démarre toute seule (≈ 3–5 min).
5. Quand elle est verte ✅, clique dessus et descends à **Artifacts** :
   `cobblemon-undo-pour-cobblemon-1.8`. C'est un .zip : le .jar est dedans.
6. Mets le .jar dans le dossier `mods` de ton profil Modrinth
   (clic droit sur le profil › **Ouvrir le dossier** › `mods`).

> Si l'onglet Actions ne propose rien, c'est que le dossier `.github` n'a pas été envoyé :
> **Add file › Create new file**, nom : `.github/workflows/build.yml`, et colle le contenu du fichier `build.yml`.
