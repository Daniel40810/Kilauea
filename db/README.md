# Datenbank (Phase 16)

Die App läuft auch ohne Datenbank. Dann liegen Zustand und Ansichten in `~/.kilauea/`.

1. `db/USER.sql` legt das Schema `KILAUEA` mit der Rolle `DB_CONNECT` an (über `manage_user`, Dienst `PDBORCL`).
2. `db/db.properties` prüfen: URL `jdbc:oracle:thin:@//localhost:1521/PDBORCL`, Benutzer `KILAUEA`.
   Fehlt das Passwort, fragt das Startbild danach und trägt es nach dem ersten Erfolg ein.
   Die Umgebungsvariable `KILAUEA_DB_PASSWORD` geht auch.
3. Im Startbild auf **„Einrichten …“** klicken (oder im Bedienfeld unter „Daten“ auf **„Einrichter …“**).
   Der Einrichter legt 13 Tabellen mit dem Präfix `KIL_` an, dazu die Pakete `KIL_API` und `KIL_WISSEN`, und lädt die Grunddaten.
   Er lässt sich beliebig oft ausführen. Am Ende muss stehen: `ERGEBNIS: alles in Ordnung`.
   Wer 16a schon eingerichtet hat, lässt ihn einfach noch einmal laufen: er ergänzt nur, was fehlt.

Auf der Konsole geht es auch, aus dem Projektordner:
`java -cp "build/classes;lib/*" com.dan.kilauea.db.Einrichter`

## Was der Einrichter tut

1. **Verbindung**: Benutzer, Container und Dienst.
2. **Voraussetzungen**:
   - Rechte: CREATE TABLE, SEQUENCE, TRIGGER und PROCEDURE.
   - Oracle Spatial (`MDSYS.SDO_GEOMETRY`).
   - Zeichensatz: AL32UTF8 wegen ʻokina und Makron. Ein anderer Zeichensatz ist nur ein Hinweis.
   - Platz im Tablespace, rund 50 MB.
3. **Bestand**: alle Objekte des Schemas, aufgeschrieben in `db/bestand.txt`.
4. **Skripte**: `01_tabellen.sql`, `02_objekte.sql` (Folgen, Trigger, Indizes, Spatial-Metadaten und -Indizes) und `03_api.sql`;
   seit 16b `04_wissen.sql` (9 Tabellen), `05_wissen_objekte.sql` (Folgen, Trigger, Indizes, 10 Geometriespalten, Paket `KIL_WISSEN`)
   und `06_grunddaten.sql` (Grunddaten aus dem Code, rund 550 Anweisungen, etwa eine halbe Minute).
   Was es schon gibt, zählt als „vorhanden“ und bleibt, wie es ist. Grunddaten werden nur eingefügt, wenn die Zeile fehlt,
   Geometrien nur gesetzt, wenn die Spalte leer ist: was du in der Datenbank änderst, bleibt.
5. **Prüfung**:
   - Jedes Objekt ist da und gültig.
   - Spatial ist richtig eingerichtet: SRID 8307, zwei Dimensionen, gültiger Index.
   - Eine Funktionsprobe schreibt eine Sitzung, einen Protokolleintrag mit Ort und einen Zustand mit Schaltern und Geometrie.
     Danach liest sie den Zustand zurück, prüft die Geometrie mit `VALIDATE_GEOMETRY_WITH_CONTEXT` und sucht im Umkreis von 50 m.
     Am Ende nimmt sie alles zurück.
   - Grunddaten: Zeilen je Tabelle und gefüllte Geometrien je Spalte, jede Fläche und Linie gültig (`VALIDATE_GEOMETRY_WITH_CONTEXT`).
   - Eine zweite Probe ruft `KIL_WISSEN.wo_liegt` am Halemaʻumaʻu, schreibt einen Lauf mit zwei Beben und zwei Messungen,
     schließt ihn und sucht die Beben im Umkreis; auch sie nimmt alles zurück.

Das Protokoll steht in `db/einrichtung.txt`.

## Was die App schreibt

| Tabelle | Wann |
|---|---|
| `KIL_SITZUNG` | eine Zeile je Start; beim Beenden Dauer und Mittel von Bildern/s, GPU- und CPU-Zeit |
| `KIL_ZUSTAND` + `KIL_SCHALTER` | `AUTO` alle 60 s und beim Beenden (die letzten 10 bleiben), `ANSICHT` mit Taste V |
| `KIL_PROTOKOLL` | Start, Ende, Fehler, Ansichten, Drehbücher, Rundflüge, Ausbrüche, Fotos, Einrichter; 90 Tage, gebündelt alle 5 s |
| `KIL_LAUF` | jedes Drehbuch und jeder freie Ausbruch: Start, erreichtes Kapitel, Ende oder Abbruch, beim freien Ausbruch Riftzone, km, Rate, Spalte |
| `KIL_BEBEN` | jedes Beben des Magmamodells im Lauf (Stärke, Tiefe, Quelle, Epizentrum), gebündelt jede Sekunde; für die letzten 10 Läufe |
| `KIL_MESSUNG` | Messnetz alle 15 min Simulation: Neigung UWD/SDH, GPS, Hebung, SO₂, Rate, RSAM; für die letzten 10 Läufe |

Das Protokoll steht immer auch in `logs/kilauea_JJJJ-MM-TT.log`. Dateien älter als 14 Tage löscht die App beim Start.

Geometrien: SRID 8307 (WGS 84), Länge und Breite, Toleranz 0,05 m. In QGIS lassen sich alle 12 Geometriespalten öffnen:
`KIL_STROM.UMRISS` nach `JAHR_MODELL` eingefärbt, darüber `KIL_KRATER.RAND`, `KIL_ORT`, `KIL_STATION` und die Beben eines Laufs.

## Wissen und Geschichte (Phase 16b)

| Tabelle | Inhalt | Zeilen |
|---|---|---|
| `KIL_ORT` | die Blickpunkte der App mit Höhe aus dem Höhenmodell und Tafeltext | 19 |
| `KIL_KRATER` | Caldera, Krater, Grubenkrater, Kegelkrater; Rand, Tiefe und Maße aus dem Gelände [A] | 14 |
| `KIL_STROM` | datierte Ströme und Flächen der App: Schlot, Spalte, Achse und Umriss genau wie im Gelände [A] | 49 |
| `KIL_STATION` | das Messnetz (Neigung, GPS, Seismometer) | 12 |
| `KIL_DREHBUCH`, `KIL_KAPITEL` | fünf Drehbücher und der freie Ausbruch, Kapitel mit Datum, Text und Tempo | 6, 29 |

Der Code bleibt die Quelle: `06_grunddaten.sql` schreibt das Werkzeug `com.dan.kilauea.tools.Grunddaten`, nicht die Hand.
Die Umrisse sind der sichtbare Teil jedes Stroms auf dem 75-m-Raster der App, vereinfacht auf 25 m; sie sind nicht die geologische Karte.

„Was liegt hier?“ als SQL:

```sql
SELECT KIL_WISSEN.wo_liegt(19.4072, -155.2834) FROM DUAL;
-- Ort: Halemaʻumaʻu (0 m) · Krater: Halemaʻumaʻu in Kaluapele · Strom: …
```

Läufe der letzten Tage:

```sql
SELECT L.LAUF_ID, D.TITEL, L.GESTARTET, L.KAPITEL_BIS, L.ABGEBROCHEN, L.BEBEN, L.MAX_STAERKE, L.MESSUNGEN
  FROM KIL_LAUF L LEFT JOIN KIL_DREHBUCH D ON D.DREHBUCH_ID = L.DREHBUCH_ID
 ORDER BY L.LAUF_ID DESC;
```

Schalter für den Start ohne Datenbank: `-Dkilauea.nodb=true`. Eigener Ordner für Zustand und Ansichten: `-Dkilauea.state.dir=…`.

