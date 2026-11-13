# Microcosm

NeoForge-Mod für Minecraft 26.1.2: Blöcke, die einen 16×16×16-Raum eine Skalenebene tiefer enthalten.
Dort werden Gegenstände und ganze Maschinenblöcke über 3D-Rezepte gefertigt, automatisiert mit beliebigen Mods.

## Stand (v0.1.0)

- **Mikrokosmos-Zelle** (`microcosm:cell`): jede Zelle besitzt einen 16³-Raum in `microcosm:level_1`.
  Zellen in Level 1 haben Räume in Level 2, und so weiter bis Level 3.
- **Gespiegeltes Layout:** Zellen, die sich im selben 16³-Abschnitt berühren, haben Räume, die sich berühren.
  Zwischen ihnen gibt es keine Wand, sodass Maschinen in Nachbarräumen in den Raum hinein bauen können.
  Alle anderen Seiten sind mit unzerstörbaren `microcosm:cell_wall` abgeschlossen.
- **Chunk-Loading als Kette:** Ein Raum ist geladen, solange seine Zelle geladen ist.
- **Abbauschutz:** Spieler können eine Zelle nicht abbauen, solange ihr Raum nicht leer ist.
- **Zellport** (`microcosm:port`): Wird im Raum an eine Wand gesetzt und verbindet diese Raumseite mit derselben
  Seite der Zelle eine Ebene höher. Pro Seite ist ein Port aktiv.
  - **Capabilities in beide Richtungen** (Items, Flüssigkeiten, Energie): Von außen erreicht man über die Zellseite
    den Block hinter dem Port im Raum, von innen über den Port den Block neben der Zellseite. Das funktioniert mit
    jeder Mod, die die NeoForge-Capabilities nutzt (Pipes, Trichter, AE2, Mekanism, ...). Weitere Capabilities lassen
    sich in `Microcosm.registerCapabilities` ergänzen.
  - **Redstone in eine Richtung**, umschaltbar per Rechtsklick: `input` (grün) leitet das Signal von außen in den Raum,
    `output` (orange) vom Raum nach außen. Jede Grenze kostet 1 Tick.
  - Seiten mit Port lösen keine Würfelrezept-Prüfung aus, nur Seiten ohne Port.
- **Zugangsgeräte** (neben eine Zelle setzen). Ohne „Betreten“ wird der Raum per Gerät gesehen und bearbeitet:

  | Gerät | Stufe | Funktion |
  |---|---|---|
  | Lichtmikroskop | 1 | Live-Farbansicht des Raums (Ebene 1), Schicht für Schicht |
  | Manipulatorarm | 1 | 9 Slots; setzt, baut ab und benutzt Items in Räumen der Ebene 1 |
  | Elektronenmikroskop | 2 | Graustufen, Aktualisierung alle 3 s, braucht Energie, kann in verschachtelte Zellen (Ebene 2) zoomen |
  | Nanosonde | 2 | wie der Arm, langsamer, braucht Energie, erreicht Ebene 2 |
  | Feldmanipulator | 3 | Ansicht und Manipulator in einem, **ohne Live-Bild**: arbeitet nur mit Scans (Energie, 5 s), erreicht Ebene 3 |

  - **Bedienung:** Rechtsklick auf den Viewer öffnet die Raumansicht. Linksklick führt den gewählten Modus aus
    (Setzen, Abbauen, Benutzen), Rechtsklick auf eine Zelle zoomt hinein, das Mausrad wechselt die Schicht.
    Benutzen mit einem Werkzeug löst lokale Fertigungsrezepte aus.
  - **Inventar:** Rechtsklick auf Arm oder Sonde, beim Feldmanipulator Schleichen + Rechtsklick. Trichter und Pipes
    können Items einfüllen, Energie kommt über die normale NeoForge-Energie-Capability.
  - **Progression:** Nanolinsen werden auf Ebene 1 gefertigt (lokales Rezept), Quantengitter auf Ebene 2. Stufe-2-Geräte
    brauchen Nanolinsen, der Feldmanipulator Quantengitter.
  - Alle Zeiten, Energiekosten und Puffergrößen stehen in `serverconfig/microcosm-server.toml`. Dort lässt sich auch
    verbieten, dass Spieler in Räumen von Hand bauen.
- **3D-Fertigungsrezepte** (Datapack, `/reload`-fähig): lokale Rezepte mit Werkzeug und Würfelrezepte.
- **Debug-Befehle** (OP): `/microcosm enter [pos]`, `/microcosm exit`, `/microcosm check [pos]`, `/microcosm info`,
  `/microcosm act <manipulator> <place|break|use> <slot> <x> <y> <z> ["x y z;..." verschachtelte Zellen]`.

## Fertigungsrezepte

Ablageort: `data/<namespace>/microcosm/fabrication/<name>.json`

| Feld | Bedeutung |
|---|---|
| `mode` | `local` oder `cube` |
| `scale` | Zahl oder Liste: Ebenen, auf denen das Rezept funktioniert (0 = normale Welt, 1–3). Fehlt das Feld: alle. |
| `pattern` | Schichten von unten nach oben → Zeilen (Nord→Süd) → Zeichen (West→Ost). `' '` = Luft, `'.'` = egal. |
| `key` | Zeichen → Block-ID, Block-Tag (`#c:…`) oder Liste |
| `default` | nur bei `cube`: Zeichen für nicht abgedeckte Zellen (Standard `.`) |
| `fill` | nur bei `cube`: `[{"from":[x,y,z],"to":[x,y,z],"key":"I"}]` |
| `structure` | nur bei `cube`: Struktur-`.nbt` (`data/<ns>/structure/…`). Blöcke müssen exakt passen, Luft muss Luft sein, Strukturleere = egal. |
| `tool`, `tool_damage` | nur bei `local`: Werkzeug-Ingredient und Haltbarkeitsverlust |
| `result` | `{"id": "...", "count": n}` |

- **`local`:** Die Struktur kann überall stehen und in allen 4 Drehungen. Wird das Werkzeug auf einen ihrer Blöcke
  angewendet (von einem Spieler oder einem Fake-Player wie dem Create-Deployer), wird sie verbraucht und das
  Ergebnis droppt an Ort und Stelle.
- **`cube`:** Der ganze Raum einer Zelle ist das Muster, in 4 Drehungen. Ein Redstone-Puls auf die Zelle
  (oder `/microcosm check`) prüft ihn. Bei einem Treffer werden die Rezeptblöcke verbraucht, alle übrigen Blöcke
  droppen neben der Zelle, und die Zelle wird zum Ergebnisblock (bei Nicht-Block-Items droppt das Item).

Beispiele: `src/main/resources/data/microcosm/microcosm/fabrication/`.

**KubeJS:** Die Rezepte sind normale Datapack-Dateien. Mit KubeJS legt man sie einfach unter
`kubejs/data/<namespace>/microcosm/fabrication/<name>.json` ab; `/reload` lädt sie neu.

## Entwicklung

Benötigt Java 25.

```bash
./gradlew build          # Jar in build/libs
./gradlew runClient      # Testclient
```

Testclient direkt in eine Welt: `./gradlew runClient -PquickPlay=<weltname>`.

Smoke-Test auf dem Dedicated Server (27 Prüfungen: Räume, Rezepte, Ports, Geräte):

```bash
rm -rf run/world && mkdir -p run/world/datapacks && cp -r dev/smoketest-datapack run/world/datapacks/mctest
echo "eula=true" > run/eula.txt
./gradlew runServer      # gibt PASS/FAIL-Zeilen aus und stoppt danach
```

## Nächste Schritte

1. JEI-Integration (Rezeptansicht Schicht für Schicht)
2. 3D-Darstellung im Mikroskop statt Schichtansicht
3. Betreten von Räumen als freischaltbares Late-Game-Feature
