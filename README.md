# Adless Arcade

Eine werbefreie Sammlung kleiner Spiele für Android – offline, ohne Tracking, ohne Internet-Permission.

**Spiele**
- **Block Break**: Blöcke auf ein 8×8-Feld ziehen, volle Reihen und Spalten löschen, Combos aufbauen.
  - Modus *Endless*: Spielen, bis nichts mehr passt, mit gespeichertem Highscore.

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
