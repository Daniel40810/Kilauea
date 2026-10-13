# Kīlauea – Stand für andere Werkzeuge

Führend ist das **Kīlauea Werkbuch** (Artefakt). Diese Datei ist die kurze, ehrliche Übergabe. Einstieg für Menschen: `README.md`.
Terras Vorlage (`KILAUEA_WORKFLOW_PROMPT.md`), der alte Stand und `SCHEMA_KILAUEA.sql` liegen in `_alt/` (verworfen am 07.10.2026).

## Auftrag
Der ganze Kīlauea in 3D auf der Grafikkarte (JOGL, OpenGL 4.3), vom Gipfel bis unter das Meer; Meereseintritt ist eines von vielen Themen. 18 Phasen, alle Planungsfragen mit A beantwortet (07.10.2026).

## Stand: Phase 17 (Schliff) geliefert, 13.10.2026
- Phase 16 bestätigt (Status_Phase16b.1–3): Einrichter „alles in Ordnung“ (04–06: 0 Fehler, 128/128 Geometrien gültig, wo_liegt ok), QGIS/Map-Ansicht mit KIL_STROM.UMRISS und KIL_KRATER.RAND.
- Tempo (Statusbild 15.2: zu Fuß am Kīlauea Iki „Himmel+Boden“ 16,7 von 23 ms): `terrain.frag` neu geordnet: erst Masken (Asche, Bewuchs, Wand, Sand, Lava des Laufs), Farben nur mit Gewicht (alte Lava nur bei Sichtbarkeit > 0,4 %, Asche/Alpin/Klippe/Talus/Sand/Schwefel/Flechten nur wenn ihr Gewicht > 0), Schwefelstellen außer Reichweite ohne Rauschen, `ReliefBase` (Lobenraum, Zellen, Verwirbelungen einmal für drei Reliefproben). Fassung `FRUEH_Z` ohne discard mit `layout(early_fragment_tests)`, benutzt ohne Schnitt und > 1,5 km vom Röhreneingang (`Renderer.terrainFast`, `-Dkilauea.fruehZ=false`). Geländeknoten je Instanzpuffer von vorn nach hinten (`TerrainLod.sortNearFirst`). Schatten: Proben umsortiert (erste vier je Viertel), Abbruch nach vier, wenn alle hell/dunkel. Unter Mesa „Himmel+Boden“ −11 bis −22 % (iki 431→382, hoehe 348→293, rand 374→332, wald 358→281 ms bei 640×400), Bilder gleich (höchstens 0,44 % der Punkte > 8/255).
- Qualität: `Quality` mit shadowTaps (4/8/12/12, `uShadowTaps`), anisotropy (2/4/8/16, `TerrainLod.applyAnisotropy`), cloudSteps (12/16/28/36), `summary()`. „Auflösung selbst regeln“ mit Ziel 30/40/60 Bilder/s (`Renderer.nextScale`, Budget 85 %, `bild.ziel` im Zustand); aus → Auflösung der Stufe.
- Dauerlauf: `ui/Dauerlauf` (8 Abschnitte, 600 s: Gipfel kreisend, Rundflug Gipfel, Drehbuch 2024, zu Fuß Kīlauea Iki, Wärmebild, Rundflug Riftzone, Tag in 90 s, freier Ausbruch Ost 18 km), Wanduhr-Takt, Messung je Sekunde (Bilder/s, GPU, CPU, längstes Bild `Renderer.takeWorstFrameMs`, Speicher, Fäden, Fehler `Dienst.fehlerZahl`), danach Zustand zurück (`panel.anwenden`), Bericht `logs/dauerlauf_*.txt/.csv`, Protokoll INFO/WARNUNG „DAUERLAUF“. Prüfliste mit 7 Punkten (`pruefliste`, ohne Fenster prüfbar). Knopf im Block „Bild“, `-Dkilauea.dauerlauf=true`, `-Dkilauea.dauerlaufFaktor` (Prüfläufe), `-Dkilauea.dauerlaufKonsole`.
- Verknüpfung: `Verknuepfung.ps1` neu (ASCII/CRLF, ī als 0x012B, Java-Version ≥ 21, Desktop + Startmenü „Kīlauea“, „Kīlauea Dauerlauf.lnk“ im Projekt, alte „Kilauea.lnk“ weg, wenn sie aufs Projekt zeigt). `Kilauea.bat`: Hinweis Dauerlauf.
- Unterlagen: `README.md` (11 Bilder aus `tools/Standbild` in `docs/bilder/`, Starten, Bedienung, Stufen, Dauerlauf, Datenbank, Werkzeuge, Ordner, Grenzen). `docs/DATA_SOURCES.md` um Phase 12, 13, 14, 16, 17 und eine Quellenliste (67 Einträge) ergänzt.
- `tools/Tempo bild <ort> <b> <h> <bilder> [png] [stufe]`: fester Ort, Wanduhr bis glFinish, Zeiten je Durchgang (`Renderer.lastPassTimes`).
- Version: Fenster „Phase 17“, `Dienst.APP_VERSION` 17.0, Startbild-Text.

## Geprüft 17 (Cloud, Mesa llvmpipe, JOGL 2.6 übersetzt / 2.5 ausgeführt)
- Pruefung 309/309 (neu 13: Stufen steigen, Zusammenfassung, FRUEH_Z ohne discard, Schattenproben je Viertel, Knoten nahe zuerst, Auflösung nach Ziel, Dauerlauf Plan/gut/schlecht, Fehlerzählung, Verknüpfung, README-Bilder, Quellenliste). 52 Shader-Fassungen mit glslang.
- App unter Xvfb mit Dauerlauf (Faktor 0,1): alle 8 Abschnitte, Zustand zurück, Bericht geschrieben; „Kein Hänger“ und „Bilder/s“ unter Mesa erwartungsgemäß offen (1 Bild/s).
- Offen: Tempo und Dauerlauf auf der Radeon (Statusbild).

## Stand: Phase 16b (Wissen und Geschichte in der Datenbank) geliefert, 13.10.2026
- 16a bestätigt: Einrichter „alles in Ordnung“ gegen Oracle 21c (Schema KILAUEA), App mit DB 43 Bilder/s.
- db/: `04_wissen.sql` (KIL_ORT, KIL_KRATER, KIL_STROM, KIL_STATION, KIL_DREHBUCH, KIL_KAPITEL, KIL_LAUF, KIL_BEBEN, KIL_MESSUNG; Marken M/L/A/G als CK; Kommentar an jeder Spalte), `05_wissen_objekte.sql` (9 Folgen/Trigger, 6 Indizes, 10 Metadaten 8307, 10 Spatial-Indizes; Paket KIL_WISSEN: version, geom_neu/geom_teil/geom_setze (Whitelist, nur wenn leer, VALIDATE 0,05), wo_liegt(breite, laenge), lauf_start/lauf_stand/lauf_ende, aufraeumen_laeufe(10)), `06_grunddaten.sql` (vom Werkzeug geschrieben: 19 Orte, 14 Krater, 49 Ströme/Flächen, 12 Stationen, 6 Drehbücher, 29 Kapitel; 545 Anweisungen, 22 727 Punkte; „-- ERWARTET T.SPALTE=n“ für Geometrien). README.md erweitert.
- `db/Grunddaten` + `tools/Grunddaten [bildordner]`: Höhe der Orte (Ground.rawHeight), Kraterrand in 72 Richtungen (höchster Anstieg bis zum ersten Abstieg > 15 m, Fenster 0,5·min..1,35·max, Median 5, Zacken ≤ 1,25 × Median ±30°), Keanakākoʻi auf 19,4008/−155,2643 (Grube im DEM); Ströme mit denselben Keimen wie Geology (`Geology.owners`), Raster-Alterscode maßgeblich, Marching Squares 75 m, Douglas–Peucker 25/15 m. `db/Geometrie` (Umriss, Vereinfachen, einfach, innen).
- App: `Player.Listener` (started/chapter/finished/ended), `Script.Frei` beim freien Ausbruch, `Quake.Kind.COLLAPSE` (Stage-Einsturzbeben), `db/Laeufe` (sammelt Beben und Messnetz alle 900 s Simulation im Speicher, schreibt jede Sekunde gebündelt auf dem DB-Faden, Ende mit lauf_ende; beim Beenden der App gesichert). `Dienst`: TABELLEN_WISSEN, `wissen()`/`bereitMitWissen()`, Hinweis „16b fehlt“ in Statuszeile und Startbild; Betrieb läuft ohne 16b weiter.
- Einrichter: Phase 16b, 12 Geometriespalten, Zeilen und Geometrien je Spalte, Gültigkeit aller geladenen Flächen/Linien, zweite Funktionsprobe (wo_liegt, Lauf mit Beben/Messungen, Zählung, SDO_WITHIN_DISTANCE) mit Rücknahme.

## Geprüft 16b (Cloud, ohne Oracle)
- Pruefung 296/296 (neu 17: Skripte 01–06, 13/13/13/44 Objekte, 445 PL/SQL-Blöcke, Kommentare, Folgen, 10 Geometriespalten, 06 = Code, 1 567 Literale gegen Länge/Stellen/CK, 229 Ringe gültig, Kratermaße, Lauf ohne DB: 2 082 Beben = Modell, 113 Messzeilen im 15-min-Takt).
- Shapely: alle 128 Geometrien in 06 gültig, Ringe ≥ 52 m auseinander; 5 Achsen kreuzen sich selbst (für Linien in Oracle erlaubt).
- OberflaecheLauf setzen/pruefen: alles gleich. Offen: Einrichter 04–06 und KIL_WISSEN gegen Oracle nur beim Nutzer.

## Stand: Phase 16a (Oberfläche und Betrieb in der Datenbank) geliefert, 13.10.2026
- Phase 15 bestätigt: aus 5,8 km 39 Bilder/s, GPU 21,0 ms; zu Fuß am Kīlauea Iki mit Wanderern 33 Bilder/s, 23,0 ms (Himmel+Boden 16,7 ms → Phase 17), CPU Leben+Klang 0,2 ms; Klang läuft unter Windows.
- Phase 16 geplant (Werkbuch, Fragen 20–29 „alle A“), geteilt in 16a (jetzt) und 16b (Wissen, Geschichte, Grunddaten, wo_liegt).
- db/: `01_tabellen.sql` (KIL_SITZUNG, KIL_ZUSTAND mit 17 festen Spalten + GEOM, KIL_SCHALTER Name/Wert mit UK und Namensprüfung, KIL_PROTOKOLL mit 12 Arten + GEOM; Kommentar an jeder Spalte), `02_objekte.sql` (4 Folgen, 4 Trigger, 5 Indizes, USER_SDO_GEOM_METADATA 8307 für 2 Spalten, 2 Indizes SPATIAL_INDEX_V2), `03_api.sql` (KIL_API: version, punkt, sitzung_start/grafik/ende, protokoll, zustand_letzter, aufraeumen). README.md.
- Paket `db/` (Vorbild Kneiphof): `Db` (db.properties, KILAUEA_DB_PASSWORD, 3 s Zeitlimit, Passwort speichern), `Skript` (aus Kneiphof), `Zustand` (Name → Wert; feste Spalten mit Grenzen/Rundung wie in der Tabelle; Datei-Rückfall), `ZustandDb` (Zustand + Schalter in einer Transaktion, laden, Ansichten), `Dienst` (Lage, Sitzung, Protokoll gebündelt alle 5 s + logs/kilauea_JJJJ-MM-TT.log 14 Tage, letzte 20, Zwischensicherung 60 s, Ansichten DB oder ~/.kilauea/ansichten, Beenden ≤ 2 s), `Einrichter` (Verbindung, Voraussetzungen: Rechte/Spatial/AL32UTF8/Quote, Bestand, Skripte, Objekte, Spatial-Metadaten und -Indizes, Funktionsprobe mit Rücknahme inkl. VALIDATE_GEOMETRY_WITH_CONTEXT und SDO_WITHIN_DISTANCE).
- Oberfläche: `Startbild` neu (Datenbankzeile, „Zuletzt“, Passwortfeld, Weiter wie zuletzt/Neu am Gipfel/Einrichten/Ohne Datenbank, nach 3 s von selbst), `EinrichterDialog` (FDialog), `ControlPanel`: 11 Blöcke zum Auf-/Zuklappen (Vorgabe offen: Ansicht, Zeit, Bild, Daten), 62 Werte im Zustand (39 gebundene Schalter/Regler, 12 Gruppen Leben/Klang, 11 Blöcke), `erfassen()`/`anwenden()`, eigene Ansichten (V, Hinfliegen, Umbenennen, Löschen, mit/ohne Zeit), Block Daten (Lage, Einrichter, Neu prüfen, Protokoll), F1-Tastenfenster. `row()`: Höchsthöhe folgt dem Inhalt. `SceneView.setKeyAction`, `walkAt`. `Renderer.photoHook`, `cpuMs()`. `KilaueaApp`: Dienst vor dem Startbild, Wahl anwenden, Kontext, Sitzung, Sicherung 60 s, windowClosing + Shutdown-Hook, Fehler/Foto ins Protokoll, Datenbankstand rechts in der Statuszeile.
- Werkzeug `tools/OberflaecheLauf setzen|pruefen <ordner>`: echte App unter Xvfb, Zustand einstellen/erfassen/sichern, Neustart mit „Weiter wie zuletzt“, vergleichen, Bilder.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6 aus py5; ohne Oracle)
- Pruefung: 279/279 (neu 33: Skripte zerlegt, 4/4/4/12 Objekte, PL/SQL-Enden, keine ;, Zustandsspalten = DDL, Arten = CHECK, Grenzen/Rundung, Namen, Dienst ohne DB: Logs aufräumen, Tagesprotokoll mit Szenenzeit/Ort/ʻokina, unbekannte Art → INFO, letzte Einträge, Zustand über Datei gleich, Ansichten merken/laden/umbenennen/löschen, Startbild-Text, Beenden < 2,5 s, unerreichbare DB bricht nach 3,6 s ab, Schalternamen im Bedienfeld).
- OberflaecheLauf: 29/29 eingestellt = erfasst, 71/71 in der Datei beim Beenden, 71/71 nach dem Neustart; Ansicht aus der Datei in der Liste.
- Offen: Einrichter und Funktionsprobe gegen echte Oracle 21c nur beim Nutzer; Tempo auf der Radeon (Zustand alle 60 s erfassen ist billig).

## Stand: Phase 15 (Leben und Klang) geliefert, 13.10.2026
- Phase 14 mit zwei Statusbildern bestätigt: Drehbuch 2024/25 Episode 1 mit Tafel 31 Bilder/s, GPU 22,4 ms; Wärmebild 39 Bilder/s, GPU 19,5 ms. Befunde behoben: (1) ʻokina im Titel der Tafel als Kästchen (Georgia kennt U+02BB nicht) → `ui/Glyphs.fit/draw` ersetzt fehlende Zeichen (ʻ → ‘, ʼ → ’, sonst ohne Makron), in `CaptionOverlay` und `MonitorPainter`. (2) Wärmebild aus 5,8 km: die ganze Insel „Wolke“, weil die Durchlässigkeit aus `clouds.frag` auch Vog und Regen enthielt → `uVogInT` (im Wärmebild 0), nur Wolken verdecken.
- Neues Paket `life/` (ohne GL): `Life` (Gruppen NENE, BIRDS, HELI, HIKERS, CARS, je schaltbar ohne Neuladen; 16 Arten in Eimern zu 16 Zahlen: Lage/Gier, Maßstab/Neigung/Rollen/Takt, Farbe, Flügel/Schritt/Hals/Licht; Schallquellen `emit`, Rufe im Ringpuffer), `World` (Uhr, Kamera, Wind, Regen, Sperrzonen, Wochentag), `Path`, `Shapes`/`LifeMesh` (Netze mit Teilen und Drehpunkten: Nēnē stehend/fliegend, ʻapapane/ʻamakihi/ʻōmaʻo/Japanbrillenvogel, Koaʻe kea, ʻIo, Hubschrauber, 4 Wanderer, Limousine/Geländewagen/Kleinbus), `Nene` (8 Plätze, Brutzeit Sep–Mai paarweise, ab Jan mit Gösseln, Sommer Trupps; grasen/sichern/gehen/fliegen; queren an Straßenplätzen die Fahrbahn), `Birds` (Singvögel um die Kamera im Wald, Morgenchor; Koaʻe kea in Halemaʻumaʻu/Kīlauea Iki; ʻIo), `FlightPlan` (ATMP 2023: Tage, 10–14 Uhr, Unternehmen, Schleife und Küste, Höhen), `Helicopters` (Plan, Schweben ≤ 60 s, HVO-Beobachtungsflug bei Drehbuch-Ausbruch, Vorführung), `Hikers` (Kīlauea Iki, Crater Rim, Devastation, Nāhuku durch die Röhre), `Cars` (Spur, Kurven, Abstand, Aussichten, Wenden, Halt für Nēnē, Licht).
- Neues Paket `sound/`: `SoundEngine` (Faden, 44,1 kHz Stereo, Blöcke zu 512; 7 Gruppen schaltbar, Lautstärke, stumm; Ereignisse; `offline` und `wav`), `Dsp`, `Ear` (Kugelwelle, Luftdämpfung, Stereo, Abbildung SPL → dBFS), `Spot`, `Tone`, `Reverb`, Synthesen `FountainSound`, `LavaSound` (Kruste, See, Eintritt, Abbrüche, Dampfspalten, Kissen), `SeaSound` (Brandung im Takt der Wellenphase, Laufzeit), `WindSound` (Windprofil, Böen, Laub, Regen), `ForestSound` (Lieder je Art, Coquí nachts, Tropfen in Nāhuku), `QuakeSound` (P/S-Laufzeit, Grollen nach Ausschlag, Dauergrollen), `TrafficSound` (Hubschrauber mit Blattfolge und Doppler, Autos, Nēnē-Rufe, eigene Schritte nach Untergrund). Pegel je Gruppe am Bezugspunkt geeicht.
- Render: `LifeRenderer` + `life.vert/frag` (Teile bewegen, Lichter, Wärme mit Deckung 2 → `Thermal.Kind.LIFE`), Schatten in Kaskade 0/1, Wärmebild; `cars.glsl` (Scheinwerfer der 8 nächsten Autos in Gelände, Straße, Bodendecke, Pflanzen, Figuren); `SoundSampler` (Schnappschuss 20/s, Beben, Rufe, Abbrüche); `Renderer.fillWorld/warmLife/lifeFrozen`, CPU „Leben+Klang“. Bedienfeld „Leben und Klang“, Taste M, Startbild Phase 15.
- Werkzeuge: `tools/LebenLauf` (Figuren ohne Fenster zählen), `tools/KlangProbe` (16 Hörproben als WAV mit Pegel und Bändern), `tools/Standbild … p15`.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6 übersetzt / 2.5 ausgeführt; 64 Shader-Fassungen mit glslang)
- Pruefung: 246/246 in Ordnung (neu 28: Uhr verstellt (Flüge an ihr Zeitfenster gebunden), Wochentag, Flugplan, Strecken über Grund/vor der Küste/fern vom Gipfel, Hubschrauber im Flug, Autos Spur/Tempo/Abstand, Halt für Nēnē, Nēnē je Jahreszeit und Sperrzone, Wanderer am Weg und aus der Sperrzone, Nacht, Morgenchor, jede Gruppe aus/an ohne Neuladen, Maße der Figuren, Wärmebild-Art, ʻokina-Ersatz; Klang: 16 Proben endlich und hörbar, Rechenzeit, jede Klanggruppe aus/an, Entfernung, Stereo, Doppler, Coquí-Frequenzen, P-Laufzeit, stumm, Luftdämpfung).
- App im Fenster unter Xvfb: startet, Bedienfeld, Taste M, ohne Tonausgabe stumm mit Meldung.

## Stand: Phase 14 (Messnetz und Wissen) bestätigt (31 Bilder/s Drehbuch, 39 Bilder/s Wärmebild), 12.10.2026
- Phase 13 mit zwei Statusbildern bestätigt: Rundflug „Der Gipfel“ an Uēkahuna 34 Bilder/s, GPU 20,5 ms, CPU 5,0 ms; Drehbuch 2018 „Der Gipfel sackt ab“ 41 Bilder/s, GPU 18,2 ms, CPU 6,3 ms, davon Lava 2,2 ms (vorher 9,0). Befund: Rundflug um 17:15 HST in Wolke und Vog, Blick trüb → „Rundflug am Morgen (8 Uhr)“ (Uhr beim Start, nach dem Ende zurück; `Renderer.startTour`, `tourMorning`).
- Neues Paket `monitor/` (ohne GL): `Prov` (M/L/A/G), `Reading` (Wert + Einheit + Herkunft + Quelle; ohne Herkunft nicht baubar), `Station` (UWD/UWE Uēkahuna [L], SDH Sand Hill [L Name, Lage G], GPS UWEV/CRIM [L Name, Lage G], 8 Seismometer [G]), `Seismo` (Wood-Anderson-Ausschlag nach Hutton & Boore 1987, Dauer nach Lee u. a. 1972, P/S 6,0/3,5 km/s, Tremor aus √Rate·(1+Fontäne/100) [G], Rauschen, Trommel-Hüllkurve, RSAM), `Gas` (SO₂ 420 t/d je m³/s [A], Lavasee 5 000 t/d [L], Ruhe 30 t/d [L], vom Drehbuch vorgegeben), `Monitor` (Kanäle UWD, SDH radial = Hebung positiv, UWEV–CRIM cm, Hebung, SO₂, Förderung, Tremor je Seismometer; drei Stufen 2 s/60 s/900 s; Fenster nach Zeitraffer, höchstens etwas länger als die Aufzeichnung; `View` mit Trommel ≤ 20-fach, sonst RSAM + Beben), `MonitorPainter` (Java2D-Tafel und Skala des Wärmebilds), `Thermal` (Skala −20…60 °C auf 45 %, dann log bis 1 200 °C; Ironbow; Stefan–Boltzmann-Mischung), `Lexicon` (Tafeln zu 19 Orten, 35 Kurztexte zu Strömen/Flächen [L], „Was liegt hier?“ aus Alterscode, ʻaʻā, Bewuchs, Regen; Lava des Laufs aus `LavaField.probe`).
- `MagmaSystem.displacement` (Mogi waagerecht + senkrecht), `intrude(…, width)`; `HeightGrid.geoNearest`.
- Render: `thermal.frag` (Tiefe → Welt, Lava/See/Boden/Wald/Meer/Dampfstellen/Himmel/Wolken, Messkreuz; Probe 1×1 RG32F mit PBO), `fountain.frag` mit `THERMAL` (Teilchen in RG16F mit MAX), `HudOverlay` (Tafeln im Hintergrund gemalt, Upload im Bildlauf), Tafel des Messnetzes oben rechts (bei Drehbüchern / immer / aus), Skala unten rechts, Infotafel zu Ort/Boden über `CaptionOverlay` („Auskunft“). Tasten T (Wärmebild), I (Was liegt hier?). `LakeRenderer.bindForThermal`.
- Drehbücher: 2024/25 Hebung/Senkung im Maß von Episode 20 (UWD −6, SDH −5 µrad [L]): `summitFeed` 0,7 / −70 / 0,55 m³/s, Pause SO₂ 1 750 t/d [L]. 2018: Gipfel hebt sich (6 m³/s), Gang 0,4 m dick, Leilani −300 m³/s, Spalte 8 und Einsturz −900 m³/s mit Sägezahn (`Stage.collapseStep` 1,2·10⁵ m³ je Einsturzbeben) [Verlauf L, Maß G]. `Stage.passiveSO2`, `collapseStep`; Player setzt beides zurück.
- Bedienfeld: Block „Messnetz und Wissen“ (Tafel, Seismometer, Wärmebild, Was liegt hier?, Tafel zu Orten, Zeile), „Rundflug am Morgen“. Startbild Phase 14.
- `tools/MessLauf [ordner] [id …]`: Drehbücher ohne Fenster mit Messnetz, je Kapitel Tabelle, PNG der Tafel, CSV. `tools/Standbild … p14`: Messnetz in 2018/2024, Trommel in Echtzeit, Wärmebilder (See, Mauna Ulu, Gipfel) mit Farbbild, Tafeln „Was liegt hier?“ und Ort.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6; 45 Shader-Fassungen mit glslang)
- Pruefung: 218/218 in Ordnung (neu 17: Hutton & Boore, Lee, P vor S, Neigung/GPS/Hebung Vorzeichen, GPS senkrecht = Hebung, Stationen, SO₂, Trommel und Herkunft aller Werte, Tafeln malbar, RSAM beim Gang, 2024/25 Neigung und SO₂, Wärmebild-Skala und Luft, Orte, Was liegt hier? an drei Stellen).
- DrehbuchLauf 2018 nach den neuen Mengen: läuft durch, „zurück: Zustand wie vorher“. Alle 210 Quell- und Shaderdateien auf dem Rechner identisch (Prüfsumme über alle).
- MessLauf über alle fünf Drehbücher (Tabelle im Werkbuch). Episode 26: UWD −8,0, SDH −5,2 µrad (gemessen Episode 20: −6/−5); SO₂ bis 17 700 t/d (gemessen bis 50 000; Raten der Episoden im Modell kleiner).
- Offen: Kosten des Wärmebilds und der Tafel auf der Radeon; SO₂ der Episoden zu klein (Raten erzählt); Seismometer ohne echte Kürzel; Dampf im Wärmebild durchsichtig.

## Stand Phase 13 (bestätigt: Rundflug 34 Bilder/s, GPU 20,5 ms; 2018 CPU Lava 2,2 ms statt 9,0), geliefert 12.10.2026
- Phase 12 mit zwei Statusbildern bestätigt: 2018 „Der Gipfel sackt ab“ 24 Bilder/s, GPU 20,1 ms, CPU 18,4 ms, davon Lava 9,0 ms; Fontänen 2024/25 35 Bilder/s, 17,9 ms, Dampf 2,8 ms (halbe Auflösung wirkt). → `LavaSim` sperrt das Feld nur noch je Teilschritt (`LavaField.stepOnce`), `LavaLayer.update` kopiert unter der Sperre nur in Zwischenspeicher und lädt danach hoch; die volle Auffrischung alle 5 s verteilt sich über die folgenden Runden.
- `camera/OrbitCamera`: geplanter Flug (`Flight`): Lage, Blick, Neigung, Abstand mit Kurve fünften Grades, Dauer 1,5–9 s nach Weg, Drehung und Zoom; weiter Weg → Aufstieg (`hop`); Drehpunkthöhe folgt dem Boden (`ys`, weich an Anfang und Ende). Freie Sicht: `liftFor` tastet das Gelände einmal entlang der Blickrichtung ab (alle 4 m, ≤ 320 Proben, 1,5 m Reserve) und halbiert die kleinste freie Neigung; im Flug vorab geplant (Hüllkurve, Anstieg ≤ 20°/s, parallel, jede 2. Stützstelle), ohne Flug mit 4 s Vorschau (rollend, 17 Lagen) und begrenzter Beschleunigung (10°/s², ≤ 10°/s). Fluggeschwindigkeit nach Höhe über Grund (`flySpeed`: 8 m/s am Boden, 0,7 × Höhe). `wrap`, `hold`, `flightProgress`, `lineBlocked` (Prüfung).
- `camera/Tour` (Gipfel 7 Stationen, Riftzone bis zum Meer 10, Ausbruch 2018 7) und `TourPlayer` (fliegt, kreist 2°/s an jeder Station, Tafel mit Station, Lage und Höhe; Maus/Taste beendet). Renderer: Kinobalken über `post.frag uBars` (12 %), bei Rundflügen oder immer; Tafel rückt über den Balken. Bedienfeld: „Kreisen (R)“, Block „Rundflug“ mit Auswahl, Starten/Beenden, Kinobalken, „Freie Sicht“ (`camera.keepView`).
- `tools/TourLauf [gipfel|rift|2018|orte|drehbuecher]`: fliegt alle Touren, alle 19 Orte und alle Blicke der Drehbücher mit 30 Bildern/s und zählt Sprünge, Kollisionen (Auge < Boden + 2 m) und Blicke durchs Gelände. `tools/Standbild … p13`: 7 Bilder aus den Rundflügen.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6; alle 40 Shader mit glslang)
- Pruefung: 201/201 in Ordnung (neu 9: Winkel kurz herum, Geschwindigkeit nach Höhe, freie Sicht in Puhimau, drei Touren auf der Insel, Prüflauf über 3 Touren, 19 Orte und alle Drehbuchblicke).
- TourLauf: 15 293 Bilder, 0 Sprünge, 0 Kollisionen, 0 Blicke durchs Gelände. DrehbuchLauf weiter in Ordnung.
- Kamera-Rechnung (2 Kerne): im Mittel 0,3–0,6 ms je Bild; Planen eines Flugs einmalig 10–15 ms (auf mehr Kernen weniger).
- Offen: Bilder/s während eines Rundflugs und CPU „Lava“ im Drehbuch 2018 auf der Radeon.

## Stand Phase 12 (bestätigt: 2018 24 Bilder/s, GPU 20,1 ms, CPU 18,4 ms mit Lava 9,0 ms; Fontänen 35 Bilder/s, Dampf 2,8 ms), geliefert 12.10.2026
- Phase 11 mit zwei Statusbildern bestätigt: über der Ostflanke 35 Bilder/s, GPU 20,7 ms (Wolken 2,5 ms, Himmel+Boden 10,8 ms); nah an der Fahne am Halemaʻumaʻu 29 Bilder/s, 28,2 ms, davon Dampf 8,5 ms. → Dampf, Gischt und Asche rechnen jetzt in halber Auflösung (`Renderer.halfResSteam`, Puffer `steamFbo`, zurück mit `overlay.frag`; nur bei umgekehrter Tiefe, sonst wie bisher; `-Dkilauea.dampfVoll=true` schaltet es ab).
- Neues Paket `script/`: `Stage` (Bühne ohne Grafik: Schlote öffnen/Rate/Fontäne/Rückfluss, Lauf beginnen, Lavasee zeigen/absenken, Einsturz F, Einsturzbeben mit Asche, Gang auslösen, Zufluss in den Gipfelspeicher `summitFeed`, Kopplung: geförderte Lava ×0,75 fehlt dem Speicher), `Script` (Baukasten: Kapitel mit Datum oder Datumsspanne, Text, Dauer, Tempo, `lavaCap`, Blick, Uhrzeit, Stichworte `at`, Bedingung `until`), `Player` (sichert Speicher, See, Uhr, Tempo, Schnitt, Kamera; spielt; folgt der Lava, wenn sie mitrechnet; Überspringen führt die Stichworte aus; am Ende 12 s stehen, dann `returnToStart`), `Scripts` (1959, Mauna Ulu, Puʻu ʻŌʻō/Kamokuna, 2018, Fontänen 2024/25, `free(Rift, s, Rate, Länge, h)`).
- `lava/`: `Vent.fountain` (vorgegebene Höhe) und `Vent.site`; negative Rate = Rückfluss (`LavaField`, Bilanz hält); `Fountain` mit bis zu 8 Schloten (Teilchen nach Höhe × Länge, Feuervorhang über langen Spalten, Boden je Teilchen); `LavaSim`: `begin`, `open`, `fountains()`, `clearAll`, `refreshRim`, `stepNow` (ohne Faden), `focusLabel/X/Z/Key` (Karte), Vorschuss des Fadens auf max(20 s, 1,5 × Tempo) begrenzt; `LavaLake.drop`.
- `magma/`: `intrude(Rift, s, Tempo)` (Gang mit vorgegebenem Tempo bis zum Ziel, Volumen 85 % Südcaldera, 15 % Halemaʻumaʻu), `endDike`, `withdraw`, `inject`, `event` (Beben von außen), `snapshot/restore`; der Gang wurzelt im Schnitt jetzt im Speicher (Lücke aus Phase 6 geschlossen).
- `terrain/Deformation`: Gipfel vor 2018 als Raster 500 × 500 zu 8 m (Unterschied zu heute, Ringmaß), Fortschritt F, Krater zuerst, Ring zuletzt; in `Ground.rawHeight/baseHeight` und in `lava.glsl` (`deformAt`, Einheit 17, `LavaLayer.bindDeform`), `terrain_height.glsl`, Straßen und Schnitt; Pflanzen und Bodendecke im verformten Gebiet ausgeblendet; `TerrainLod` erweitert die Höhe der Knoten.
- `atmos/Steam.ash` (Art 4, braungrau). `render/CaptionOverlay` (Infotafel mit Java2D, Goldkante) + `overlay.frag`. Bedienfeld: Block „Ausbrüche (Drehbuch)“ mit Auswahl, Abspielen, Anhalten, Nächstes Kapitel, Beenden, Kamera folgt, Infotafel, freier Ausbruch (Riftzone, km, Rate, Länge, „Ort unter dem Blick“).
- `tools/DrehbuchLauf [id …]`: Drehbücher ohne Fenster, je Kapitel Zeit und Zustand, am Ende Vergleich vorher/nachher. `tools/Standbild … p12`: 22 Standbilder aus den Drehbüchern mit Tafel.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6; alle Shader mit glslang)
- Pruefung: 192/192 in Ordnung (neu 13: Gipfel vor 2018 und heute, Einsturz stufenweise und bei F = 1 exakt heute, Reihenfolge Krater/Ring, zwei Fontänen, Rückfluss mit Bilanz, ausgelöster Gang trifft das Ziel, Magma zurück, fünf Drehbücher, Kapitel endet bei Bedingung, Stichworte zur Zeit, Überspringen, Rückkehr, Drehbuch 2024/25 ganz durch, Riftzone unter dem Blick, Absenkung des Sees).
- DrehbuchLauf: alle fünf und der freie Ausbruch laufen durch und kehren in den Zustand vorher zurück (Rechenzeit ohne Fenster: 1959 40 s, Mauna Ulu 117 s, Puʻu ʻŌʻō 64 s, 2018 131 s, 2024 19 s, frei 11 s).
- Offen: Nacht nahe Lava bleibt dunkel (Belichtung folgt der Glut); 2018 erreicht die Lava des Modells Kapoho nicht (das Feld von 2018 liegt im Gelände); das Delta bei Kamokuna ist nach Stunden klein; Kosten auf der Radeon unbekannt.

## Nachtrag 12.10.2026: Meer pulsierte
- Befund Daniel: nur das Meer pulsiert als ganze Fläche, bei stehender Kamera, beim Fliegen, im Zeitraffer und auch mit `-Dkilauea.meerStatisch=true`; Land und Himmel ruhig.
- Ursache: `LakeRenderer.update` (neues Seebild rund 5×/s) und `SeaLayer.upload` binden auf der aktiven Einheit 0 und lassen dort Textur 0 zurück. Die Himmelstabelle (`uSkyLut`, Einheit 0) wurde nur einmal vor dem Himmel gebunden; das Meer las danach in diesen Bildern eine leere Tabelle (keine Spiegelung, keine Luftperspektive). `Tageslauf` friert den See ein, darum dort nicht messbar.
- Behoben: `Renderer.common()` bindet `lutTex` auf Einheit 0 je Pass; `lakeR.update` und `sea.upload` laufen vor `use()/common()` ihres Passes.
- Zurückgenommen: `aerial()` wieder `max(dir.y, 0.0) + 0.02` (der Versuch `abs(dir.y) + 0.04` vom 10.10. ging von falscher Ursache aus). Diagnoseschalter `kilauea.meer*` bleiben. Alte Fassung in `_alt/meer_flackern_vorher/`.
- Geprüft: 130 Quelldateien gegen JDK 21/JOGL 2.6 übersetzt, 38 Shader mit glslang. Bestätigung auf der Radeon steht aus.

## Stand: Phase 11 (Luft und Licht) geliefert, 09.10.2026
- `atmos/Weather` (ohne GL): Wetterkarte 256 × 256 über das Höhenraster mit Bedeckung, Regen, Vog und Hebung. Wolkenband 650–2 100 m (Passatinversion), wo der Wind den Hang hinaufläuft und der Jahresregen hoch ist; darüber klar. Nachmittags mehr, an der Kona-Seite Quellwolken, nachts klart das Land auf. Vog als Fahne aus dem Halemaʻumaʻu im Wind, Dunst an der Leeküste. Wetter an der Kamera: Regen, Nässe (nass in Minuten, trocken in einer halben Stunde), Regenbogen, Nebel in der Wolke. Rechnet im eigenen Faden (rund 60 ms), wenn sich Stunde (0,03 h), Wind oder Richtung ändern.
- `render/CloudLayer` + `weather.glsl` + `clouds.frag` (halbe Auflösung, 28 bzw. 16 Schritte) + `composite.frag`: Wolken mit 3D-Rauschen (64³, Einheit 16), Licht über vier Proben zur Sonne, Mond, Himmel und Glut von unten; darunter Vog, Regenschleier und Lichtstrahlen (Schatten im Dunst). Wolkenschatten in allen Shadern über `sunShadow` (dieselbe Dichte wie im Bild). Zusammenbau mit tiefengewichteter Glättung und Hitzeflimmern über heißer Lava. Neuer Pass „Wolken“ in der Zeile Grafikkarte.
- Dampf über heißer Lava (`LavaSim.hot`, `Steam.hot`), mehr bei Regen. Lavasee allein leuchtet jetzt die Umgebung, die Fahne und die Wolken an (eigene Glutkarte in `LavaLayer`, war seit Phase 8 aus, wenn kein Strom lief).
- Belichtung: folgt im Zeitraffer der Schätzung aus dem Sonnenstand sofort mit, Rest sanft (`SceneState.adaptBoost`, bei 960-fach rund viermal schneller).
- Bedienfeld: Zeitraffer „Ein Tag in 90 Sekunden“, Windrichtung (Passat, Kona-Wind), „Wolken, Regen und Vog“, Zeile zum Wetter. Kissen sinken nach einem Abbruch des Deltas auf den Grund (Statusbild 10.3).
- `tools/Tageslauf`: rechnet einen Tag in 90 s, misst Helligkeit, Flackern und ausgebrannte Bildpunkte je Bild (CSV, Bilder alle 2,5 s). Standbilder `p11_*`.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6; alle Shader mit glslang)
- Pruefung: 177/177 in Ordnung (neu 12: Bedeckung Luv/Lee, Gipfel klar, Regen Luv/Lee, Vog bei Passat und Kona-Wind, nachts weniger Wolken, Karte deterministisch, Nässe an der Kamera, Fahne folgt dem Wind aus 70° und 200°, Dampf über Lava bei Regen, Belichtung im Zeitraffer gleitet).
- Tageslauf (320 × 200, 15 Bilder je Sekunde, 1 351 Bilder): größtes Flackern 1,7 von 255 (Morgendämmerung), kein ausgebrannter Bildpunkt.
- Offen: Kosten der Wolken auf der Radeon; Nacht nahe Lava schwarz (Belichtung folgt der Glut, Sterne nur fern der Lava); Wolken aus der Nähe körnig; über dem Meer kaum Passatwolken; Hitzeflimmern nur in Bewegung.

## Stand Phase 10 (bestätigt: unter Wasser 27–36 Bilder/s, GPU 21,9–25,6 ms; Gelände 12,4–13,7 ms, Fontäne 2,4–2,8 ms, Meer 1,3–1,4 ms)
- `lava/SeaEntry` (Eintrittsstellen in Gruppen von 40 m mit Wärmestrom, Lavafälle über Stufen > 4 m, neues Land, Abbrüche des Deltas 2,5 je Stunde und Hektar ab 0,2 ha), `LavaField`: `forEachSeaContact`, `forEachFall`, `newLand`, `collapse` (Bilanz: gefördert = beweglich + erstarrt + abgebrochen). `LavaSim.seaEntry` viermal je Sekunde.
- `lava/Littoral` (Glutbrocken bis rund 15 m, Lavafälle und Feuerwehrschlauch als Teilchen), `lava/Sparks` (gemeinsam mit der Fontäne, `FountainRenderer` für beide). `atmos/Steam`: Laze über den Eintritten, Dampfstoß bei Abbrüchen.
- `sea/Pillows` (Kissenlava ab rund 8 m Tiefe hangabwärts, Knospen alle 10–30 s an bis zu vier Spitzen, ältere Kissen hangaufwärts, Blasen), `render/PillowRenderer` mit `pillow.vert/.frag`.
- Unter Wasser: `underwater.glsl` (Schwächung 0,38/0,065/0,035 je m, Trübe vor dem Eintritt, Kaustik), `underwater.frag` (Wassersäule und Lichtstrahlen, wenn das Auge unter dem Spiegel ist), `ocean.frag` von unten (Snellsches Fenster), braunes und kochendes Wasser am Eintritt, `terrain.frag` Licht unter Wasser und schwarzer Sand. Keine Pflanzen und Bodendecke unter dem Spiegel. Kamera taucht (`OrbitCamera.diving`).
- Bedienfeld: „Zu den Kissen tauchen“, „Tauchen erlauben“, Zeile zum Meereseintritt. Standbilder `p10_*`.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6 aus dem py5-Paket; alle Shader mit glslang)
- Pruefung: 165/165 in Ordnung (neu 8: Eintritt, Fälle, neues Land; Abbruch mit Bilanz; Abbrüche über 6 h; Glutbrocken; Kissenlava ≥ 2 Knospungen je Minute, unter Wasser, Größen; Blasen; ohne Lava keine Knospen; Snellsches Fenster).
- Standbilder: 3–4 Knospungen je Minute. Offen: Meeresgrund dunkel und schnell im Wasser verschwommen; Feuerwehrschlauch nur als Teilchen; kein eigener Dampf über dem kochenden Wasser; Kosten auf der Radeon unbekannt. Das Gelände kostet 12–13 ms (Phase 9), Ursache offen.

## Stand Phase 9 (bestätigt: 38 Bilder/s an der Klippe, Meer 3,5 ms; 33 am Halemaʻumaʻu; Gelände 12–13 ms)
- `sea/` (ohne GL): `Waves` (lineare Theorie: Dispersion genau und nach Eckart, Aufsteilen, Brechen bei H > 0,78 d, Brecherart nach Iribarren, Phase vor dem Ufer), `Nearshore` (Raster 512 × 512 zu 10 m um die Kamera: Tiefe, Uferabstand per Abstandstransformation, Richtung zum Meer; im Hintergrund neu, wenn die Kamera 1,2 km wandert), `Coast` (Uferpunkte, Klippen), `Spray` (Gischt an Klippen im Takt der Dünung, Wellengruppen, bis 35 m).
- Grafik: `render/SeaLayer` (Textur, Uniforms, Gischt), `shader/sea.glsl` (Südswell 14 s und Passatsee; vor der Küste parallel zum Ufer, aufgesteilt, gebrochen, Schaumwalze und Spur, Schwall an der Klippe), `ocean.vert` hebt die Fläche bis rund 3 km, `ocean.frag` mit Spiegelung der Insel im Bildraum (nur Qualität ab Mittel), Glutreflex aus der Glutkarte, Lichtschein der Lava auf Wasser und Schaum, Prüfbild `-Dkilauea.meerPruefbild=true`. `steam.frag`: Gischt (Art 2), Dampf vom Lichtschein der Lava angestrahlt. `atmos/Puffs`: Dampf und Gischt durch denselben Zeichner.
- Küstenbewuchs: Salzzone ohne ʻōhiʻa an trockenen Küsten (bis 1,5 km), an der nassen Puna-Küste 150 m; Geländespeicher Version 6 (baut beim ersten Start neu).
- `VentSite.KAMOKUNA` (Röhrenausgang 350 m vor der Klippe, 4 m³/s) für Glut am Meer. Bedienfeld: Periode der Dünung, „Brandung und Gischt“, Zeile zur Küste. GPU-Zeit jetzt je Pass: Lavasee, Meer, Dampf, Fontäne getrennt; Prozessor mit „Meer“.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.6 aus dem py5-Paket; alle Shader mit glslang)
- Pruefung: 157/157 in Ordnung (neu 9: Dispersion und Eckart, Aufsteilen und Brechtiefe, Brecherart, Kämme mit √(g d), gerades Ufer im Raster, Steilufer als Klippe, Gischt nur bei auflaufender Dünung, Hōlei mit Klippen, Küste bei Hōlei kaum ʻōhiʻa).
- Standbilder `p9_*`. Offen: Meer kostet unter Mesa rund doppelt so viel wie vorher (Spiegelung); Gischt eher Wolken als Strahlen; Spiegelung nur, was im Bild ist; Halemaʻumaʻu 24 Bilder/s (Phase 8) noch nicht geklärt.

## Stand Phase 8 (bestätigt: 50 Bilder/s, 14 ms am Mauna Ulu; 24 Bilder/s, 28 ms am Halemaʻumaʻu mit Fontäne und Fahne; Schnitt 56 Bilder/s ohne Ausbrennen)
- `lava/Glow` (Planck × CIE 1931 nach Wyman 2013 → lineares sRGB in Engine-Einheiten, Sonne 22; Tafel 48 Werte ab 400 °C), `lava/GlowMap` (Lichtschein 96 × 96 über dem Gebiet der Lava, Rissanteil `crackShare` wie im Shader), `lava/Fountain` (Teilchen ballistisch mit Wind, Abkühlung, Peles Haar, Kernflecken erst ab 40 m Höhe), `lava/LakeImage` (Seebild 256² mit Nähten, Plattenalter, Spritzern).
- `LavaField`: Blöcke für die Grafik (`exportBlock`, schmutzige Blöcke), Gestein kühlt nach; `LavaLake`: Grube 45 m unter dem Rand (`pit`, `surfaceY`); `LavaSim`: Gelände mit Lava (`Ground.overlay`) und Grube (`Ground.shape`), Glutkarte alle 0,5 s, Seebild alle 0,2 s, Fontäne je Schlot (`VentSite.fountain`). `Ground.solidHeight` = Gelände ohne Lava (für das Modell).
- Grafik: `render/LavaLayer` (Blockatlas R32I + RGBA32F + RG16F, Glutkarte), `shader/lava.glsl` (Höhe mit Lava, Grube, Glut, Lichtschein), `terrain.frag` (frische Lava glasig dunkel, Risse als treibende Schollen, ʻaʻā mit glühenden Lücken, vorrückende Ränder reißen auf, Oberlichter, im Mittel, wenn ein Riss schmaler als ein Bildpunkt ist), `LakeRenderer` (`lake.vert/.frag`), `FountainRenderer` (`fountain.vert/.frag`, additiv). Pflanzen, Bodendecke, Straßen weichen der Lava; Pflanzen nahe der Glut verkohlen. Belichtung nahe Glut höchstens 25-fach, nachts Nachtsehen (`post.frag`). `section.frag`: Glühen ohne `uExposureInv` (brannte in Phase 7 aus).
- Bedienfeld: „Lava im Bild“, „Lavasee zeigen“; Startbild und Version „Phase 8“. `tools/Standbild`: Bilder `p8_*` mit vorgerechnetem Ausbruch.

## Geprüft (Cloud, Mesa llvmpipe 25.2 mit JOGL 2.6 aus dem py5-Paket; alle Shader mit glslang)
- Pruefung: 148/148 in Ordnung (neu 8: Glut nach Planck, Rissanteile, Blöcke endlich, Lichtschein nimmt ab, Fontäne 100 m steigt bis 99 m und fällt nach dem Ende zusammen, niedrige Fontäne ohne Kern, Seebild mit Nähten, Grube).
- 11 Standbilder `p8_*` (Fontäne Halemaʻumaʻu, See abends/nachts, Kīlauea Iki, Mauna Ulu Tag/Abend/Nacht, Front Tag/Nacht, Spalte 8, Puʻu ʻŌʻō).
- Offen: Hitzeflimmern, Lavafälle ohne eigene Darstellung (nur lange Schlieren bei schnellem Fließen), Dampf von der Glut beleuchtet (Phase 11), Lichtschein ohne Abschattung, einzelne heiße Tumulus-Zelle glüht als Kegel. Tempo auf der Radeon unbekannt (Statusbild).

## Stand Phase 7 (bestätigt: 49–56 Bilder/s, GPU 11–13 ms beim Ausbruch; Karte geht; Schnitt brannte aus → in Phase 8 behoben)
- `lava/` (ohne GL): `Rheology` (Stoffwerte mit Herkunft; Bingham-Fluss wie MAGFLOW; Zähigkeit/Fließgrenze nach Temperatur; Kruste nach Hon u. a. 1994; ʻaʻā-Schwelle), `LavaField` (Zellen 10 m in Blöcken 32 × 32 aus festem Vorrat; Fluss in 8 Nachbarn, Schrittweite nach Abfluss und Schlot, Ausgleichsgrenze in Teichen; Wärme wandert mit; Strahlung/Wind nach Krustenalter, Leitung in den Boden, Röhrendach, Meer schreckt ab; Pāhoehoe → ʻaʻā über Spannung und Tempo, nie zurück; Röhren unter Dach mit Oberlichtern, laufen leer; Erstarren unter 1 000 °C, spätere Ströme laufen darüber; Prüfsumme, Kennzahlen), `Vent`, `VentSite` (6 Schlote: Halemaʻumaʻu, Kīlauea Iki 1959, Mauna Ulu Südflanke, Puʻu ʻŌʻō 61g, Spalte 8, Südwestrift 2024), `LavaLake` (Platten Quelle → Senke, Nähte öffnen/schließen nach der Strömung, Spiegel nach Druck im Halemaʻumaʻu-Speicher, Gaskolben, Spritzer), `LavaSim` (eigener Thread, höchstens 30 ms je 50 ms, See in Echtzeit bis 30-fach).
- App: Bedienblock „Lava (Modell)“ mit Ort, Rate, Zeitraffer, Start/Schlot schließen/Hinfliegen, `ui/LavaMap` (2D-Karte des Stroms + Lavasee) und Zustandszeilen. Im 3D-Bild glüht noch nichts (Phase 8).
- `ui/LavaPainter` zeichnet Karte und See ohne GPU; `tools/LavaLauf` rechnet ohne Fenster, Tabelle alle 10 min, Karte und Verlauf als PNG (`LavaLauf MAUNA_ULU 10 6 6 bild.png`, `LavaLauf SEE 60 see.png`).
- `section.frag`: Glühen weich begrenzt und gedämpft, Beben als Punkt mit dünnem Ring (Statusbild 6: Speicher, Gang und Beben brannten weiß aus).

## Geprüft (Cloud, Übersetzung gegen JOGL 2.6 aus `lib/`; kein GL-Lauf, JOGL-Natives für Linux nicht erreichbar; `section.frag` mit glslang geprüft)
- Pruefung: 140/140 in Ordnung (neu 23: Stoffwerte, Bingham, Kruste; Ebene 3 % fließt bergab und kühlt; Volumenbilanz; gleicher Ablauf; Erstarren nach dem Versiegen; runde Ausbreitung auf der Ebene; Pāhoehoe/ʻaʻā nach Rate und Gefälle; Röhren mit Oberlichtern, langsamer kühlend, leerlaufend; Meer baut Land; Mauna Ulu 1 h begrenzt und bergab; Halemaʻumaʻu bleibt im Krater; Lavasee: Plattenzahl, Ufer, Drift, Alter, Nähte, Gaskolben, Spiegel nach Druck, gleicher Ablauf).
- Offen: Unter Dach kühlt die Lava 14–24 °C/km (offen rund 50), gemessen sind rund 1 °C/km: im 10-m-Gitter entstehen breite Decken statt enger Röhren. Daraus folgt auch spätes ʻaʻā an langen Pāhoehoe-Strömen.

## Stand Phase 6 (bestätigt: 60 Bilder/s, GPU 10,2 ms mit offenem Schnitt)
- `magma/` (ohne Grafik): `MagmaSystem` mit `Reservoir` (Halemaʻumaʻu-Speicher 1–2 km, Südcaldera-Speicher 3–5 km tief), Zufluss 0,1 km³/Jahr, Ausgleich zwischen den Speichern, `Dike` (Gang in `Rift` Ost oder Südwest ab 10 MPa, erstarrt, wenn der Überdruck auf 15 % gefallen ist), wahlweise Gipfelschlot mit Episoden, `Quake` (Gipfel, Gang-Schwarm, Südflanke; Gutenberg–Richter b = 1), Hebung und Neigung nach Mogi (UWE am Uēkahuna-Rand). Deterministisch mit Startwert.
- `render/SectionRenderer` + `section.vert/.frag`: der Schnitt klappt den Berg auf (Ostrift, Südwestrift, Nord–Süd). Davor zeichnen Gelände, Meer, Pflanzen, Straßen und Fels nichts (`cutAway` in `common.glsl`); der herausgenommene Block hat Schnittebene, Stirnseiten und Boden. Auf der Ebene: Lavabänke, ozeanische Kruste ab rund 11 km, Erdmantel ab 17 km, Gleitfläche, Speicher nach Druck, Zufuhr, Gänge, Beben als Ringe.
- Bedienfeld „Unter der Oberfläche“: Schnitt, „Vor den Schnitt fliegen“, Zeitraffer des Magmas, „Zufluss verstärken (×20)“, „Gipfelschlot offen“, Anzeige von Druck, Hebung, Neigung, Beben und Gang.
- `tools/MagmaLauf`: 60 Tage als Tabelle (Druck, Hebung, Neigung, Beben, Gang).

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.5; Übersetzung gegen JOGL 2.6 aus `lib/`)
- Pruefung: 117/117 in Ordnung (neu: Druck → Hebung und Neigung, Mogi gegen die Formel, Gang nach verstärktem Zufluss, Schwarm an der Spitze, Absacken, Massenbilanz, gleicher Ablauf, Episoden des Gipfelschlots).
- Standbilder `p6_*`.

## Stand Phase 5 (bestätigt: 42 Bilder/s im Wald bei Nāhuku, CPU 5,5 ms; 44 Bilder/s bei Hōlei; GPU 17,5 ms)
- `terrain/DetailTile` + `DetailTiles`: die Gipfelkachel ist jetzt eine von fünf Detailkacheln (Gipfel 4 m, Kette der Krater 6 m, Puʻu ʻŌʻō 4 m, Spalten von 2018 und Kapoho 6 m, Südküste 8 m). Schritte: Formen vorher (Grubenkrater), Schärfen, Meeresklippe, Formen nachher (Kegel, Spalten, Kanal). Zwischenspeicher `data/kachel_<id>.bin`; `data/gipfel_4m.bin` wird nicht mehr gebraucht.
- `render/TerrainLod`: alle Kacheln in einem Atlas (R16F mit Mips), `terrain_height.glsl` liest bis zu 8 Kacheln.
- Grubenkrater Lua Manu, Puhimau (150 m), Koʻokoʻolau, Hiʻiaka, Pauahi (91 m), Mauna Ulu, Makaopuhi; Kegel der Spalte 8 (rund 55 m) mit Lücke nach Nordosten, Spaltenrampen, Lavakanal mit Dämmen bis in die Kapoho-Bucht; Hōlei und Hilina Pali geschärft; Meeresklippe 12–28 m an der Südküste.
- `terrain/SeaArch` + `render/RockRenderer` (`rock.vert/.frag`): Felsbogen Hōlei als eigenes Netz; Stand heute (im September 2026 eingestürzt: Pfeiler und Blöcke), Schalter „Felsbogen Hōlei vor dem Einsturz“.
- Orte: 19 Blickpunkte (neu Puhimau, Pauahi, Makaopuhi, Hōlei Pali; Mauna Ulu, Spalte 8, Felsbogen und Hilina Pali neu gesetzt); Beschreibung folgt der Auswahl.
- Tempo (Statusbild Phase 4: 20 bzw. 35 Bilder/s bei 22,7 bzw. 20,3 ms GPU → die CPU bremste): Bodendecke ohne verschränkten Puffer und nur jedes zweite Bild im Wind bewegt (im Wald 27,7 → 6,6 ms auf 2 Kernen), Belichtung ohne Warten auf die Grafikkarte, ferne Schattenkaskade jedes zweite Bild und gröber, Dampf ohne Boxen; CPU-Zeit je Schritt im Bedienfeld; `tools/Tempo` misst sie ohne Fenster.
- Optik: Kraterwände mit ungleich dicken Bänken, Rinnen und Schutthalden; ʻōhiʻa-Ballen der groben Stufen mit gestreuten Normalen; Streifen in Nāhuku behoben.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.5; Übersetzung gegen JOGL 2.6 aus `lib/`)
- Pruefung: 108/108 in Ordnung (neu: Tiefe von Puhimau, Hiʻiaka, Pauahi; Kegel der Spalte 8; Neigung der Hōlei Pali; Meeresklippe und Felsbogen; alle Orte mit Blick).
- Standbilder `p5_*` und je Ort ein Bild `ort_*`.
- Nicht geprüft: Tempo auf der Radeon (Statusbild von Daniel).

## Stand Phase 4 (bestätigt: 35 Bilder/s am Halemaʻumaʻu, 20 im Wald bei Nāhuku; GPU 20–23 ms)
- `terrain/SummitTile`: Gipfelkachel 12 × 8 km in 4-m-Zellen als Zuschlag auf das 75-m-Gelände. Wände von Kaluapele, Halemaʻumaʻu, Kīlauea Iki und Uēkahuna geschärft (Stufen statt Rampen), Dampfspalten (−3 m), Gruben von Nāhuku; im Band um Wege und Straßen keine Schärfung. Zwischenspeicher `data/gipfel_4m.bin` (Version 3, Prüfsumme).
- `terrain/SummitPaths`: Kīlauea Iki Trail, Crater Rim Trail, Devastation Trail, Nāhuku-Rundweg; 6 Ausgangspunkte zum Gehen; Achse der Lavaröhre; Dampfspalten.
- `terrain/LavaTube` + `render/TubeRenderer` (`tube.vert/.frag`): Nāhuku rund 200 m begehbar, Lampen an der Decke, Gelände über den Gruben ausgeschnitten.
- Gehen: `camera/OrbitCamera` mit Gehmodus (Augenhöhe, Stufen, nicht über Klippen), `camera/WalkWorld` (Boden der Röhre, Wände); Taste **G** und Ausgangspunkte im Bedienfeld.
- Bodendecke: `flora/HawaiiGround` + `render/GroundCoverRenderer` (`ground.vert/.frag`) mit `com.dan.ground` (Gras, Farn, Steine nahe der Kamera).
- Dampf: `atmos/Steam` + `render/SteamRenderer` (`steam.vert/.frag`): Fahne aus Halemaʻumaʻu, Steaming Bluff, Haʻakulamanu, Kīlauea Iki, schwach Puʻu ʻŌʻō; weiche Kanten am Gelände; morgens mehr.
- Tempo (nach Statusbild Phase 3, 43 Bilder/s): Schatten je Kaskade ausgewählt, ʻōhiʻa in 4 Detailstufen, keine Pflanzen bei hoher Kamera; GPU-Zeit je Pass im Bedienfeld.
- Fehler behoben: senkrechte gestrichelte Linie im Himmel (Naht der Himmelstabelle im Norden), Schleier über dem Bild nach dem Dampf (fehlendes VAO für die Vollbild-Pässe).

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.5; Übersetzung gegen JOGL 2.6 aus `lib/`)
- Pruefung: 96/96 in Ordnung (zusätzlich Gipfelkachel, Gehen: an der Uēkahuna-Klippe gestoppt, durch Nāhuku 202 von 203 m, Kīlauea Iki Trail hinab bis auf rund 1 060 m).
- Standbilder `p4_*` (Gipfel, Uēkahuna, Halemaʻumaʻu, Kīlauea Iki, Fahne, Dampf, Gehen an 6 Orten).
- Nicht geprüft: Tempo auf der Radeon (Statusbild von Daniel).

## Stand Phase 3 (bestätigt: 43 Bilder/s, GPU 19,4 ms auf Hoch, 3 695 Pflanzen)
- `terrain/Geology`: 40 datierte Ströme 1801–2024 (Spuren bergab) und 9 Flächen, Grundalter je Vulkan, Regen aus dem Passat, Bewuchs nach Alter und Regen, Asche, Schwefelstellen. Ergebnis je Zelle in `cover` (Bewuchs, Bäume, Asche, Küste) und `geo` (Alterscode, ʻaʻā, Flechten, Regen); Zwischenspeicher Version 4.
- `terrain.frag` neu: Lava nach Alter und Art (Pāhoehoe-Loben, Stricke, ʻaʻā-Klinker), Flechten, Kronendach, Farn/Gras, Asche mit Rippeln, Schwefel, Sand; Prüfbild „Alter der Lava zeigen“.
- `flora/`: `HawaiiSpecies.ohia()` (mit `com.dan.forest`), `TreeFern` (hāpuʻu), `FloraField` (Kacheln 48 m, Masken wie der Shader); `render/FloraRenderer` + `flora.vert/.frag` (Instanzen, Wind, Schatten in zwei Kaskaden).
- `terrain/ParkRoads` (mit `com.dan.road`: Way, Network, WayType) + `render/RoadRenderer` + `road.vert/.frag`: Crater Rim Drive und Chain of Craters Road auf dem Gelände; Feinrelief im Straßenband aus (`uRoad`).
- Qualität: Reichweite der Pflanzen Niedrig 350 m (ohne Farne) … Ultra 1 500 m.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.5; Übersetzung gegen JOGL 2.6 aus `lib/`)
- Pruefung: 89/89 in Ordnung (zusätzlich Alter an 5 Orten, Regen an 5 Orten, Bewuchs, Straßen, Pflanzen).
- 15 Standbilder `p3_*`.
- Nicht geprüft: Tempo auf der Radeon (Statusbild von Daniel).

## Stand Phase 2 (bestätigt: 58 Bilder/s, GPU 12,4 ms auf Hoch)
- `terrain/DemSource`: liest GLSDEM 3″ (BIL) und GTOPO30 (GeoTIFF) aus dem Projektordner und `data/raw/`.
- `terrain/TerrainBuilder` → `HeightGrid`: 2 560 × 2 560 Zellen zu 75 m, Küstenabstand, Meeresboden [G], `Changes2018`, Bodenart; Zwischenspeicher `data/gelaende_75m.bin` mit Fingerabdruck der Höhendateien.
- `terrain/Relief`: Feinrelief 6–420 m [G], gleiche Rechnung wie `terrain_height.glsl`; `Ground.height` = B-Spline + Relief.
- `render/TerrainLod`: Viererbaum (CDLOD) mit 12 Stufen, 65×65-Gitter je Knoten (Viertel 33×33), Geomorphing, Ausblenden nach Sichtkegel; Schatten aus derselben Auswahl (`terrain.vert` mit `DEPTH_ONLY`).
- Shader: `terrain_height.glsl` (B-Spline aus Mips nach Entfernung, Relief), `terrain.vert` neu, `terrain.frag` liest die Bodenart aus der Textur; Prüfbild „Detailstufen zeigen“.
- `ui/OverviewMap`: Übersichtskarte (Schummerung, Orte, Blickfeld), Klick fliegt hin.
- Qualität: `lodRange` je Stufe (Niedrig 1,6 … Ultra 3,5), Ultra mit 4096er-Schattenkarten.
- Ort Felsbogen Hōlei auf die Küstenlinie der Daten gesetzt.
- `TerrainMesh` entfällt.

## Geprüft (Cloud, Mesa llvmpipe, JOGL 2.5; Übersetzung auch gegen JOGL 2.6 aus `lib/`)
- Pruefung: 65/65 in Ordnung (Höhen an 7 Orten, Glätte, Relief, Min-Max-Pyramide, Viererbaum lückenlos, Nachbarn ≤ 1 Stufe).
- 13 Standbilder `p2_*`, App unter Xvfb mit Karte.
- Nicht geprüft: Tempo auf der Radeon (Statusbild von Daniel), Rundflug ohne Springen.

## Nächste Aufgabe
Statusbild zu Phase 16b: Einrichter erneut ausführen (erwartet „alles in Ordnung“ mit 13 Tabellen, 12 Geometriespalten, Grunddaten), ein Drehbuch spielen und KIL_LAUF/KIL_BEBEN/KIL_MESSUNG ansehen, Ebenen in QGIS. Danach Phase 17 (Himmel und Boden schneller).
