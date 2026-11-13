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
- **3D-Fertigungsrezepte** (Datapack, `/reload`-fähig): lokale Rezepte mit Werkzeug und Würfelrezepte.
- **Debug-Befehle** (OP): `/microcosm enter [pos]`, `/microcosm exit`, `/microcosm check [pos]`, `/microcosm info`.
  Das Betreten ist vorerst nur per Befehl möglich, bis Mikroskop und Manipulator existieren.

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

## Entwicklung

Benötigt Java 25.

```bash
./gradlew build          # Jar in build/libs
./gradlew runClient      # Testclient
```

Smoke-Test auf dem Dedicated Server:

```bash
rm -rf run/world && mkdir -p run/world/datapacks && cp -r dev/smoketest-datapack run/world/datapacks/mctest
echo "eula=true" > run/eula.txt
./gradlew runServer      # gibt PASS/FAIL-Zeilen aus und stoppt danach
```

## Nächste Schritte

1. Ports (Items, Flüssigkeiten, Energie, Redstone) zwischen Raum und Ebene darüber
2. Mikroskop: synchronisierte Spiegelung des Raums auf dem Client plus Rendering
3. Manipulator: Platzieren und Abbauen aus der Ferne
4. Elektronenmikroskop + Sonde (Level 2), Feldmanipulator (Level 3)
5. JEI/EMI mit 3D-Ansicht, KubeJS-Unterstützung, Config
