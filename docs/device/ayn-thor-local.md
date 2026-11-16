# AYN Thor — branche locale & agent

Branche dédiée au **pilotage sur appareil physique** (Greg — Mac + Thor USB) via **Cursor self-hosted worker**.

## Branche Git

```text
cursor/local-thor-pilot-95bf
```

```bash
git fetch origin
git checkout cursor/local-thor-pilot-95bf
git pull origin cursor/local-thor-pilot-95bf
```

Merges vers `main` une fois la QA Thor OK (voir [checklist QA](../qa/bpm-detector-manual-checklist.md)).

## Lancer un agent sur le Mac (pas le Cloud seul)

1. Thor en USB, `adb devices -l` → `model:AYN_Thor` en état `device`.
2. Cursor : démarrer l’agent / le Project sur le worker **MacBook Pro de Greg** (self-hosted).
3. Ouvrir ce repo sur la branche `cursor/local-thor-pilot-95bf`.
4. Demander à l’agent : *« thor-pilot install »* ou exécuter les scripts ci‑dessous.

Sans worker Mac, l’agent Cloud **ne voit pas** l’USB.

## Scripts (sur le Mac)

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"   # ou JDK Android Studio / brew openjdk@17
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

chmod +x scripts/thor-pilot.sh scripts/install-debug-device.sh
./scripts/thor-pilot.sh devices
./scripts/thor-pilot.sh install   # build, installDebug, launch
./scripts/thor-pilot.sh logs      # optionnel
./scripts/thor-pilot.sh smoke    # tests instrumentés sur le Thor
```

`local.properties` (non versionné) :

```properties
sdk.dir=/Users/greg/Library/Android/sdk
```

Copier depuis `local.properties.example` si besoin.

## Appareil de référence

| Champ | Valeur observée |
|--------|------------------|
| Modèle | AYN_Thor (`product:kalama`) |
| Exemple serial | `e653283c` |

## Dépannage

### Java / Gradle

Erreur *Unable to locate a Java Runtime* : installer JDK 17+ puis :

```bash
brew install openjdk@17
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
```

Les scripts `install-debug-device.sh` / `thor-pilot.sh` tentent `java_home`, le JBR d’Android Studio et Homebrew `openjdk@17`.

### ADB (macOS)

Utiliser **uniquement** `$ANDROID_HOME/platform-tools/adb` — pas de mélange Homebrew / Studio (`protocol fault`).
Voir `scripts/install-debug-device.sh` pour le détail.