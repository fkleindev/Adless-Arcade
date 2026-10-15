# Adless Arcade

Eine werbefreie Sammlung kleiner Spiele für Android – offline, ohne Tracking, ohne Internet-Permission.

**Spiele**
- **Block Break**: Blöcke auf ein 8×8-Feld ziehen, volle Reihen und Spalten löschen, Combos aufbauen.
  - Modus *Endless*: Spielen, bis nichts mehr passt, mit gespeichertem Highscore.
- **Gate Rush**: Held auf einer Pseudo-3D-Straße links/rechts steuern, automatisch schießen, durch Upgrade-Tore laufen,
  Kisten freischießen, Power-ups sammeln, Hindernissen ausweichen – kein Gegner darf die Linie erreichen.
  - Modus *Endless*: Gegner werden immer zahlreicher und stärker, Bosse in regelmäßigen Abständen.
  - Modus *Boss Rush*: Rüstphase und Bosswelle im Wechsel, jeder Boss stärker als der letzte.
  - Modus *Tägliche Challenge*: festes Level pro Tag (gleicher Seed), eigener Tagesrekord.
- **Rune Survivors**: Survivors-Like im Fantasy-Stil – mit dem Joystick durch die Welt laufen, Waffen kämpfen automatisch,
  Kristalle sammeln, aufsteigen und aus bis zu 17 Waffen und 9 Talenten einen Build bauen (4 Talente pro Lauf). 4 Klassen und 9 weitere Waffen schaltet man mit Runen in der Runen-Schmiede frei.
  Jede Klasse hat eine eigene Passive; Truhen bieten 3 Belohnungen zur Wahl, darunter stapelbare Items
  (Krit/Superkrit, Gift, Lebensraub, Funken u. v. m.) mit abnehmendem Ertrag.
  - Stages: *Fantasy-Wiese* und *Vergessener Friedhof* (100 Runen, nach dem ersten Sieg über den Dämonenfürsten) mit
    Hindernissen, Fernkämpfern und Bossen, deren Attacken angekündigt werden und denen man ausweichen muss.
  - Modus *15 Minuten*: überleben und den Endboss der Stage besiegen.
  - Modus *Endlos*: immer stärkere Horden ohne Ende, ab Minute 20 werden sie rasend.

**Rund um die Spiele**
- **Coins & Shop**: In allen Spielen Coins verdienen und gegen Skins, App-Themes, Avatare und Titel tauschen.
- **Bestenlisten**: Lokale Top 10 pro Modus (mit App-Version je Eintrag), bei Rune Survivors zusätzlich pro Stage und Klasse.
- **Erfolge**: 13 Erfolge über alle Spiele, jeder schaltet einen exklusiven Titel frei, den es nicht im Shop gibt.
- **Fortsetzen**: Jedes Spiel lässt sich pausieren und später weiterspielen, auch nach einem App-Neustart.

## Bauen & Starten

1. Den Ordner in **Android Studio** öffnen.
2. Falls Gradle meckert: *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*
   auf das mitgelieferte **JetBrains Runtime (JBR 17/21)** stellen. Java 26 wird von Gradle 8.11 nicht unterstützt.
3. Gerät oder Emulator wählen und auf ▶ **Run** klicken.

Tests der Spiellogik: `./gradlew test` (bzw. `gradlew.bat test` unter Windows).

## Aufbau

```
app/src/main/java/com/adlessarcade/
├── MainActivity.kt, AdlessArcadeApp.kt
├── core/            GameRegistry (Spiele + Modi), HighscoreRepository (DataStore)
├── navigation/      AppNavHost: home → game/{gameId} → play/{gameId}/{modeId}
├── ui/theme/        Farben, Typografie, dunkles Theme
├── ui/components/   Scaffold, Karten, Buttons, Game-Over-Overlay, Block-Zeichnung
├── ui/home/         Hauptmenü
├── ui/modes/        Generisches Modus-Menü pro Spiel
└── games/blockbreak Engine (reine Logik), ViewModel, Screen, Vorschau
```

## Neues Spiel hinzufügen

1. Paket unter `games/<name>/` anlegen (Engine, ViewModel, Screen, Vorschau-Composable).
2. In `core/GameRegistry.kt` einen `GameInfo`-Eintrag mit Modi ergänzen.
3. In `navigation/AppNavHost.kt` in `GameHost` die `gameId` auf den Screen mappen.

Hauptmenü, Modus-Menü und Highscore-Anzeige funktionieren dann automatisch.
