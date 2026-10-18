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
  Kristalle sammeln, aufsteigen und aus bis zu 18 Waffen und 9 Talenten einen Build bauen (4 Talente pro Lauf). 9 Klassen – zu jeder Grundwaffe eine: Krieger, Paladin (Heilige Aura, Gottesschild und Richturteil), Assassine
  (Wurfdolche, Hinterhalt), Sturmrufer (Blitz, Donnerhall) und Piratin (Kettenanker, Schatzsucher) sind von Anfang an spielbar, Magierin,
  Waldläuferin, Nekromant und Kryomantin sowie 9 weitere Waffen schaltet man mit Runen in der Runen-Schmiede frei.
  Jede Klasse hat eine eigene Passive; Truhen bieten 3 Belohnungen zur Wahl, darunter stapelbare Items
  (Krit/Superkrit, Gift, Lebensraub, Funken u. v. m.) mit abnehmendem Ertrag.
  - Stages: *Fantasy-Wiese* und *Vergessener Friedhof* (100 Runen, nach dem ersten Sieg über den Dämonenfürsten) mit
    Hindernissen, Fernkämpfern und Bossen, deren Attacken angekündigt werden und denen man ausweichen muss.
  - Modus *15 Minuten*: überleben und den Endboss der Stage besiegen.
  - Modus *Endlos*: immer stärkere Horden ohne Ende, ab Minute 20 werden sie rasend.
- **Galaxy Towers 2**: TriPeaks-Solitaire im Weltraum (im Querformat), Nachfolger von Galaxy Towers. Freie Karten aus drei Türmen ablegen,
  die einen Wert höher oder tiefer sind (K–A rundum). Ab einer 3er-Kette öffnet sich eine zweite Ablage mit der nächsten
  Stapelkarte, Nachziehen schließt sie wieder. Lange Ketten, Turmspitzen, abgeräumte Runden und Restzeit bringen Punkte; Ketten ab 3 Karten können Gold abwerfen.
  Beim Ablegen fallen zufällig Power-ups (Joker, Zeitsprung, Scanner, Warp) und in Modi mit Zeitlimit Bonuszeit;
  Kartenrückseiten und Hintergründe gibt es im Shop.
  - Modus *Classic*: 10 Runden mit Sanduhr (90 s, jede Runde 5 s weniger); weiter geht es auch ohne Abräumen.
  - Modus *Timeless*: 10 Runden ohne Zeitlimit.
  - Modus *Single Round*: eine Runde mit 80 Sekunden.
  - Modus *Tägliche Challenge*: Classic mit gleichen Karten und Funden für alle pro Tag, eigener Tagesrekord.
- **Rune Tactics**: Auto-Chess gegen endlose KI-Armeen. In der Planungsphase Einheiten im Shop kaufen (Würfeln 2 Gold,
  XP 4 Gold), auf Bank und Brett (7×4 eigene Felder) ziehen, zum Verkaufen in den Shop ziehen; 3 gleiche verschmelzen zu ★★,
  3 ★★ zu ★★★. 15 Einheiten in 5 Kostenstufen mit eigener Fähigkeit, je eine Herkunft (Untote, Elfen, Zwerge) und Klasse
  (Krieger, Wächter, Magier) als Synergien mit Stufen bei 2 und 4. Gekämpft wird automatisch (1×/2×/4× Tempo);
  Gold aus Grundeinkommen, Zinsen und Serien, 100 Leben, Niederlagen kosten je nach Runde und überlebenden Gegnern.
  - Items: 6 Komponenten (Schwert, Bogen, Stab, Weste, Umhang, Gürtel) verschmelzen auf einer Einheit zu
    21 fertigen Items mit Sondereffekten (z. B. Raserei, Lebensraub, Dornen, Betäubungsimmunität), max. 3 pro Einheit.
  - 5er-Zyklus: Runde x4 Kreaturen (lassen Komponenten fallen), Runde x5 Boss (Knochendrache, Steintitan, Sturmhexe)
    mit Gefolge und angekündigten Flächenangriffen; ein Bosssieg bringt eine von 3 Belohnungen zur Wahl
    (Gold, Komponenten, fertiges Item oder Einheit).
  - Modus *Endlos*: Ein KI-Kommandant baut aus einem wachsenden Budget Armeen mit eigenen Synergien und ab Runde 8
    auch mit Items, ab Runde 25 werden alle Gegner mit jedem Durchgang stärker (Aufstieg). Score = erreichte Runde.

**Rund um die Spiele**
- **Coins & Shop**: In allen Spielen Coins verdienen und gegen Skins, Kartenrückseiten, Hintergründe, App-Themes, Avatare und Titel tauschen.
- **Bestenlisten**: Lokale Top 10 pro Modus (mit App-Version je Eintrag), bei Rune Survivors zusätzlich pro Stage und Klasse.
- **Erfolge**: 19 Erfolge über alle Spiele, jeder schaltet einen exklusiven Titel frei, den es nicht im Shop gibt.
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
