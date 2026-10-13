# Datenquellen

Jeder Wert in der Szene trägt eine Herkunft: **M** gemessen · **L** Literatur/Karte · **A** abgeleitet · **G** gestaltet.
Stand 07.10.2026 (Phase 3).

## Lage der Szene
- Ursprung: Halemaʻumaʻu, 19,4072° N, 155,2834° W [L, auf ±50 m genähert].
- Tangentialebene um den Ursprung, Meter: x = Osten, y = Höhe, z = Süden. Breite/Länge ↔ Szene in `terrain/Geo`.
- Erdkrümmung im Shader: Absenkung d²/2R (100 km → 785 m).

## Höhen, die Phase 2 verwendet (von Daniel ins Projekt gelegt, 07.10.2026)

| Datei | Was | Auflösung | Stand | Herkunft |
|---|---|---|---|---|
| `N18W156/n18w156.bil`, `N19W155/n19w155.bil`, `N19W156/n19w156.bil` (+ `.hdr`) | GLSDEM (aus SRTM), Breite 18–20° N, Länge 156–154° W: Kīlauea, Mauna Loa, Südküste, Puna | 3″ ≈ 90 m | Februar 2000 | **M** |
| `gt30w180n40.tif` | GTOPO30, gelesen nur 18–21° N, 157–154° W: Norden der Insel (Mauna Kea Nordhang, Kohala, Hāmākua) | 30″ ≈ 1 km | 1996 | **M** |

- Lesen: `terrain/DemSource` (ESRI-BIL mit `.hdr`, Int16; GTOPO30-GeoTIFF unkomprimiert). Die feinste Kachel, die einen Punkt deckt, gewinnt.
- Umrechnung: bilinear auf das Szenenraster 2 560 × 2 560 Zellen zu 75 m (192 × 192 km) **[A]**; Abfrage im Bild und für die Kamera mit kubischem B-Spline **[A]**.
- Küste: Land, wo die Daten über 0,5 m liegen **[A]**.
- Zwischenspeicher: `data/gelaende_75m.bin` (rund 39 MB, wird beim ersten Start gebaut, rund 6 s; kommt nicht ins Git). Ändern sich die Höhendateien im Projekt, baut der nächste Start neu.

## Änderungen seit 2000 (Schicht `terrain/Changes2018`, abschaltbar)
- Gipfel 2018: Calderaboden in zwei Stufen 45 + 65 m abgesackt, Halemaʻumaʻu mit Boden auf 830 m und Wand von rund 700 m Halbmesser **[G nach USGS-Berichten; Lage L]**.
- Puʻu ʻŌʻō 2018: Krater um 300 m eingestürzt **[G nach USGS]**.
- Kapoho 2018: Strom der Spalte 8 10–20 m dick, rund 3,5 km² neues Land **[G nach USGS-Karte vom August 2018]**.

## Gestaltet, bis bessere Daten im Projekt liegen
- **Meeresboden [G]**: aus dem Abstand zur Küste (Schelf, Hang, Tiefsee bis rund −5 300 m), weil keine Tiefendaten vorliegen. Der Link zu GEBCO funktionierte nicht.
- **Feinrelief [G]**: Wellen und Buckel von 6 bis 420 m Wellenlänge, höchstens rund 12 m (`terrain/Relief` = `relief()` in `terrain_height.glsl`); frische Lava unruhiger, Wald glatter.

## Gestein und Grün (Phase 3, `terrain/Geology`)
- **Ströme mit Jahr**: 40 datierte Ströme von 1801 bis 2024 (Kīlauea 20, Mauna Loa 18, Hualālai 2), dazu 9 Flächen wie Kraterböden und Lavaseen. Jahr, Schlot und Länge **[L, USGS HVO]**, Lage der Schlote auf rund 1 km genähert. Der Weg entsteht aus mehreren Spuren, die vom Schlot bergab laufen, wo nötig über bekannte Wegpunkte (etwa Pāhoa 2014, Kapoho 2018) **[A]**. Flächen im Raster: Mauna Ulu 42 km² (USGS rund 46), Puʻu ʻŌʻō 1983–2018 rund 170 km² (USGS rund 144).
- **Grundalter** außerhalb der Ströme je Vulkan als Spanne **[L]**, Muster gestaltet **[G]**.
- **Regen** aus einem Passatmodell (Feuchte zieht aus 70° über die Insel, regnet bergauf aus, Inversion bei rund 2 000 m) und dem Kona-Gürtel **[A]**, abgestimmt auf Werte des Rainfall Atlas of Hawaiʻi (Hilo rund 2 800 mm, Hāmākua-Hang bis 7 000, Kaʻū-Wüste rund 560, Kona-Küste rund 250).
- **Bewuchs** aus Alter und Regen (Sukzession: Flechten, Farn, ʻōhiʻa, Regenwald mit Baumfarnen; im Trockenen Gras) **[A]**; Gase am Gipfel bremsen den Bewuchs **[G]**.
- **Asche**: Keanakākoʻi-Tephra (1790) in der Kaʻū-Wüste, Bimsfall 1959 **[G nach Karte]**. **Schwefel**: Haʻakulamanu, Steaming Bluff, Ränder von Halemaʻumaʻu und Puʻu ʻŌʻō **[L Lage, G Umriss]**.
- **Bäume**: ʻōhiʻa lehua nach Wuchsbeschreibung (Little & Skolmen 1989) **[L/G]**, gebaut mit `com.dan.forest`; hāpuʻu als eigenes Netz **[G]**.
- **Straßen**: Crater Rim Drive (offener Teil) und Chain of Craters Road nach NPS-Karte **[L, auf 100–300 m]**; Querschnitt aus `com.dan.road` **[G]**; die Fahrbahn liegt auf dem Gelände (keine Einschnitte).

## Gipfel (Phase 4, `terrain/SummitTile`, `SummitPaths`, `LavaTube`, `atmos/Steam`)
- **Form der Krater** aus den vorhandenen Höhen (75 m) **[L]**; die 4-m-Kachel schärft nur, was die Daten zeigen: steile Hänge werden zu Wänden mit Kante oben und Fuß unten **[G]**. Halemaʻumaʻu nach dem Einsturz 2018 und den Ausbrüchen bis 2024 so, wie die Höhendaten mit `Changes2018` ihn liefern.
- **Wege**: Kīlauea Iki Trail, Crater Rim Trail (Steam Vents, Steaming Bluff, Uēkahuna), Devastation Trail, Nāhuku-Rundweg nach NPS-Karten **[L, auf 30–80 m]**; Kīlauea Iki Trail auf den Kraterboden der Daten gelegt (rund 1 060 m).
- **Nāhuku**: Lage und Länge (rund 180 m begehbar) **[L, NPS]**, Querschnitt, Tiefe und Gruben **[G]**.
- **Dampf**: Orte (Halemaʻumaʻu, Steaming Bluff, Haʻakulamanu/Sulphur Banks, Kīlauea Iki) **[L]**; Mengen, Größen und Tempo **[G]**; die Fahne zieht mit dem Passat nach Südwesten **[L]**.
- **Bodendecke**: Gras, Farn (uluhe) und Steine nach Bewuchs und Alter der Lava **[G]**, gebaut mit `com.dan.ground`.

## Riftzonen und Küste (Phase 5, `terrain/DetailTiles`, `SeaArch`)
- **Grubenkrater**: Lage aus den Höhendaten, wo sie sich zeigen (Hiʻiaka, Pauahi, Makaopuhi, Nāpau, Keanakākoʻi) **[M]**, sonst nach Karte **[L/G]** (Lua Manu, Puhimau, Koʻokoʻolau: im 75-m-Raster nur 9 m tief). Maße: Pauahi rund 610 × 400 m, 91 m tief **[L, Wikipedia]**; Puhimau rund 150 m tief **[L]**; Makaopuhi 1,6 × 1,0 km, früher über 210 m tief, teils von Mauna Ulu gefüllt **[L, USGS Volcano Watch]**; übrige Maße **[G]**.
- **Mauna Ulu**: Lage aus dem Höhenmaximum der Daten (19,3672° N, 155,2022° W) **[M]**, Gipfelkrater **[G]**.
- **Spalte 8 (Ahuʻailāʻau)**: Kegel rund 55 m hoch **[L, USGS 2018]**, Lage **[L, gerundet]**, Hufeisenform offen zum Kanal **[L]**; Spaltenreihe und Kanal **[G nach USGS-Karte]**.
- **Pali und Meeresklippe**: Lage und Höhe aus den Daten **[M]**, Schärfe **[A]**; Höhe der Klippe 12–28 m **[G]**, am Felsbogen rund 25 m (der Bogen war 27 m hoch **[L, NPS]**).
- **Felsbogen Hōlei**: Lage 19,2945° N, 155,0979° W **[L, Wikipedia]**, an die Wasserlinie der Daten gesetzt; Höhe 27 m **[L]**; Form **[G]**. Eingestürzt zwischen 23. und 27.09.2026 **[L, NPS-Mitteilung vom 01.10.2026]**; was stehen blieb, ist nicht beschrieben, darum Pfeiler und Blöcke **[G]**.

## Magmasystem (Phase 6, `magma/`)
- **Speicher**: Halemaʻumaʻu-Speicher rund 1–2 km unter dem Krater, Südcaldera-Speicher rund 3–5 km tief und etwas südlich; der Südcaldera-Speicher speist den Halemaʻumaʻu-Speicher und die Riftzonen **[L, USGS Volcano Watch, Januar 2024]**. Volumen, Zusammendrückbarkeit und Leitwerte **[G]**.
- **Zufluss** im Mittel rund 0,1 km³ im Jahr **[L, Größenordnung nach USGS]**.
- **Gleitfläche** der Südflanke 10–12 km tief **[L, USGS]**; im Schnitt bei rund 11 km.
- **Gang**: Bruchgrenze 10 MPa, Höhe 2 km, Dicke 1,5 m, Tempo einige km am Tag **[A nach 2018]**.
- **Beben**: Gutenberg–Richter mit b = 1 **[L]**, Raten **[G]**. **Hebung** nach Mogi **[L]**, Neigungsmesser UWE am Uēkahuna-Rand **[L, Lage gerundet]**.
- Das Modell ist qualitativ und keine Vorhersage.

## Lava-Modell (Phase 7, `lava/`)
- **Fließgesetz**: Bingham-Flüssigkeit im Zellenmodell wie MAGFLOW **[L, Vicari u. a. 2007; Del Negro u. a. 2008]**: Fluss erst über der kritischen Dicke, darüber mit der dritten Potenz der Dicke. Zellen 10 m, acht Nachbarn, Kantenbreiten **[A]**; Ausgleichsgrenze in Teichen (DT_EQ 0,25 s) **[A]**.
- **Temperatur am Schlot** 1 150 °C **[L, Helz & Thornber 1987: 1 140–1 170 °C]**; Erstarren bei 1 000 °C **[A, knapp über dem Solidus]**.
- **Zähigkeit** 160 Pa·s am Schlot bis 3·10⁷ Pa·s beim Erstarren, **Fließgrenze** 100 Pa bis 6·10⁵ Pa; Form log η = a + b·T nach Ishihara u. a. 1990 **[L]**, Werte für Hawaiʻi abgestimmt **[A]**.
- **Dichte** 2 200 kg/m³ (blasig), **Wärmekapazität** 1 150 J/(kg·K), **Kristallisationswärme** 3,5·10⁵ J/kg, **Emissionsgrad** 0,95 **[L]**.
- **Kruste**: Dicke 0,0779 m · √Stunden **[L, Hon u. a. 1994]**; Oberflächentemperatur nach Alter der Kruste **[A, angelehnt an Hon u. a. 1994, Keszthelyi & Denlinger 1996]**; über 0,5 m/s reißt sie immer wieder auf **[G]**.
- **Wärmeverlust**: Strahlung und Wind an der Oberfläche, Leitung in den Untergrund (Halbraum) **[L]**; in Röhren nur durch das Dach, Boden vorgewärmt (25 %) **[A]**; im Meer Abschrecken **[G]**.
- **Pāhoehoe → ʻaʻā**: Zähigkeit · Scherrate über 250 Pa bei unter 1 128 °C und mindestens 0,3 m/s, über fünf Minuten anhaltend **[L, Übergang nach Peterson & Tilling 1980; Werte A, abgestimmt auf den Wechsel bei 5–10 m³/s nach Rowland & Walker 1990]**.
- **Röhren**: ruhige Pāhoehoe mit über 0,15 m Kruste, darunter mindestens 0,03 m/s **[A]**; Oberlichter zufällig (1,2 %) und an Stufen über 0,5 Gefälle **[G]**; leergelaufen unter 60 % des höchsten Stands **[G]**. Gemessen sind in Kīlauea-Röhren rund 1 °C Abkühlung je km **[L, Helz u. a. 2003]**; das Modell kühlt unter Dach deutlich langsamer als offen, aber noch schneller als gemessen.
- **Schlote**: Lage nach USGS-Karten **[L]**, Spaltenlänge und genaue Stelle **[G]**; Mauna Ulu an der Südflanke statt im Gipfelkrater, Puʻu ʻŌʻō am Schlot 61g an der Ostflanke **[L, Lage gerundet]**. Förderraten als Größenordnung **[L]**: Puʻu ʻŌʻō im Mittel rund 4 m³/s, Spalte 8 bis rund 100 m³/s.
- **Lavasee**: Platten treiben mit 0,1–0,5 m/s **[L, Patrick u. a. 2016 für den Overlook-See]**, hier 0,15 m/s in der Mitte **[A]**; Größe des Sees, der Platten, Lage von Quelle und Senke **[G]**; Gaskolben (Spiegel steigt über Minuten um einige Meter und fällt binnen einer Minute) **[L, beobachtet am Overlook-See]**, Abstände und Höhen **[G]**; Spiegel nach dem Druck im Halemaʻumaʻu-Speicher **[G]**.
- Das Modell ist qualitativ und keine Vorhersage.

## Lava im Bild (Phase 8, `lava/Glow*`, `Fountain`, `LakeImage`, `render/Lava*`, Shader `lava.glsl`)
- **Glut**: Planck-Strahlung mal Farbabgleich CIE 1931 (analytische Näherung nach Wyman, Sloan & Shirley 2013) **[L]**, in lineares sRGB und in die Engine-Einheiten der Sonne (22 je Kanal) **[A]**; Emissionsgrad 0,95 **[L]**. Tafel mit 48 Werten von 400 °C in Schritten von 18,5 °C. Prüfung: unter 550 °C (Draper-Punkt) kaum sichtbar, 1 150 °C rot zu grün rund 8 : 1.
- **Kruste und Risse**: Kruste strahlt mit ihrer Oberflächentemperatur, offene Risse mit der Kerntemperatur; Rissanteil aus dem Verhältnis Oberfläche zu Kern, frisch fast ganz offen, alte Kruste 0,3 % **[G]**. Treibende Schollen über eine Fließkarte, ʻaʻā mit glühenden Lücken, Front und Ränder im Vorrücken mit mindestens 35 % offen **[G]**. Gestein kühlt nach dem Erstarren mit τ = max(1 h, Dicke²/(4κ)) **[A]**.
- **Lichtschein**: Karte 96 × 96 über dem Gebiet der Lava, Kugelstrahler in 12 m Höhe, ohne Abschattung durch das Gelände **[A, grobe Näherung]**.
- **Fontänen**: Teilchen ballistisch mit v = √(2gH) **[L]**, Streuung um 8°, Abkühlung im Flug τ 4–30 s, Versatz im Passat **[A/G]**; Peles Haar **[L, Erscheinung; Mengen G]**. Höhen je Schlot (Halemaʻumaʻu 250 m, Kīlauea Iki 1959 bis rund 580 m, hier 300 m; Mauna Ulu 25 m; Puʻu ʻŌʻō 6 m Brodeln; Spalte 8 bis 60 m) als Größenordnung **[L]**, Höhe mit der Förderrate^0,7 **[G]**. Unter 40 m kein geschlossener Kern **[G]**.
- **Lavasee**: Grube 45 m unter dem Rand **[G]**; Nähte aus der Voronoi-Teilung der Plattenmitten, breiter, wo sie sich öffnen **[G]**.
- **Belichtung**: nahe glühender Lava höchstens 25-fach, nachts Mischung zum Nachtsehen (Stäbchen) **[A]**.

## Meer und Brandung (Phase 9, `sea/`, Shader `sea.glsl`, `ocean.*`)
- **Wellen**: lineare Theorie (Airy) mit der Dispersion ω² = g k tanh(k d) **[L]**, im Shader nach Eckart (1952) genähert (höchstens 5 % Abweichung, geprüft) **[L]**. Aufsteilen K_s = √(c_g,tief / c_g) **[L]**; Brechen bei H > 0,78 d (McCowan) **[L]**; Art des Brechers nach der Iribarren-Zahl (Battjes 1974): unter 0,5 Schwall, bis 3,3 Sturz, darüber Reflexion an der Klippe **[L]**.
- **Dünung**: Südswell 1 m aus 190°, Periode 14 s (Sommerswell aus dem Südpazifik meist 12–20 s) **[L, PacIOOS/NOAA als Größenordnung; Werte G]**. See aus dem Passat: Höhe 0,16 · Windstärke, Periode 3,3 + 0,62 · Windstärke **[A]**. Abschattung an Küsten, die der Welle abgewandt sind **[G]**.
- **Vor der Küste**: Raster 512 × 512 zu 10 m um die Kamera mit Tiefe, Uferabstand (Abstandstransformation nach Felzenszwalb & Huttenlocher **[L, Verfahren]**) und Richtung zum Meer. Kämme laufen mit √(g d) aufs Ufer zu **[A]**.
- **Klippen und Gischt**: Klippe, wo das Land binnen 25 m um mehr als 6 m steigt und das Wasser davor tiefer als 3 m ist **[G]**. Gischt im Takt der Dünung, Wellengruppen, Wurfhöhe bis zwölffache Wellenhöhe, höchstens 35 m **[G nach Fotos]**.
- **Spiegelung**: Himmel aus der Himmelstabelle, Insel und Glut im Bildraum (Strahl gegen die Tiefe der Szene) **[A, übliches Verfahren]**; was nicht im Bild ist, spiegelt sich nicht.
- **Küstenbewuchs**: an trockenen Küsten (unter rund 1 700 mm Regen) keine ʻōhiʻa bis rund 1,5 km landeinwärts, an der nassen Puna-Küste bis 150 m **[L Erscheinung; Breiten G]**.
- **Kamokuna**: Röhrenausgang rund 350 m vor der Klippe, 4 m³/s **[Lage G, Rate L als Größenordnung]**; der Meereseintritt selbst folgt in Phase 10.

## Meereseintritt und unter Wasser (Phase 10, `lava/SeaEntry`, `lava/Littoral`, `sea/Pillows`, Shader `underwater.*`, `pillow.*`)
- **Eintritt**: Zellen mit beweglicher Lava im Meer oder auf dem neuen Ufer knapp über dem Spiegel, zu Gruppen von 40 m; Wärmestrom aus dem Abschrecken im Modell (Phase 7), auf dem Ufer nur ein Randanteil von 5 % **[G]**.
- **Laze und Dampf**: weiß, dicht, Menge mit der Wurzel des Wärmestroms **[Erscheinung L, Menge G]**; ein Abbruch des Deltas gibt einen Stoß Dampf.
- **Glutbrocken**: littorale Explosionen werfen Fetzen bis rund 15 m **[L, Mattox & Mangan 1997; Häufigkeit G]**.
- **Lavafälle, Feuerwehrschlauch**: Abfluss über eine Stufe von mehr als 4 m; Geschwindigkeit aus dem Fluss durch rund 1,5 × 5 m **[A]**; Erscheinung wie Kamokuna 2017 **[L]**.
- **Delta**: neues Land im Meer; Abbruch mit 2,5 je Stunde und Hektar ab 0,2 ha, Umkreis 30–70 m **[G nach dem Ablauf an Kamokuna 2016/17]**. Volumenbilanz: gefördert = beweglich + erstarrt + abgebrochen (geprüft).
- **Schwarzer Sand**: im Meer erstarrte Lava nahe dem Spiegel als Glassand **[Erscheinung L]**.
- **Wasser**: Schwächung 0,38 / 0,065 / 0,035 je Meter (RGB) **[A nach Pope & Fry 1997, Smith & Baker 1981]**; Trübe vor dem Eintritt bräunlich, rund 50 m weit **[G]**. Snellsches Fenster 48,6° **[L]**. Kaustik und Lichtstrahlen aus demselben Muster **[G]**.
- **Kissenlava**: Knospen alle 10–30 s an bis zu vier Spitzen, Kissen 0,8–2 m lang, 0,35–0,7 m hoch, ab rund 8 m Tiefe hangabwärts **[L Größen und Ablauf nach Beobachtungen von Tauchern vor Kīlauea (Moore 1975; Tribble 1991); Takt G]**; Haut glüht rund 2 s, Risse rund 40 s **[G]**; Blasen 0,2–0,45 m/s **[G]**.

## Luft und Licht (Phase 11, `atmos/Weather`, `render/CloudLayer`, Shader `weather.glsl`, `clouds.frag`, `composite.frag`)
- **Wolkenband**: Untergrenze rund 650 m, Obergrenze an der Passatinversion rund 2 100 m **[L, Cao u. a. 2007, J. Climate 20 (Inversion meist 1 800–2 400 m); gerundet]**; darüber klar, die Gipfel von Mauna Loa und Mauna Kea ragen heraus **[L Erscheinung]**.
- **Wo die Wolken stehen**: wo der Wind den Hang hinaufläuft (Hebung aus Wind · Gefälle) und wo der Jahresregen hoch ist **[A, Regen aus Giambelluca u. a. 2013, Rainfall Atlas of Hawaiʻi, wie seit Phase 3]**; über dem Meer rund 20 % Passatwolken **[G]**.
- **Tagesgang**: nachmittags mehr Wolken am Hang, an der Kona-Seite Quellwolken aus der Seebrise, nachts klart das Land auf **[L Erscheinung, Giambelluca u. a.; Stärke G]**.
- **Regen**: wo das Band dicht ist (Bedeckung über 0,55) und der Hang steigt **[A/G]**; Kamera wird in Minuten nass, trocknet in rund einer halben Stunde **[G]**.
- **Vog**: Gasfahne aus dem Halemaʻumaʻu als Fahne im Wind, Breite 600 m + 0,18 je Meter Weg, Abklingen über rund 120 km **[A, Gaußsche Fahne; Erscheinung und Zugrichtung L, USGS HVO „Vog“: bei Passat nach Südwesten und um die Insel nach Kona, bei Kona-Wind nach Norden und Osten]**; Dunst an der Leeküste **[G]**.
- **Wolken im Bild**: Dichte aus der Wetterkarte und kachelndem 3D-Rauschen (Worley und Wertrauschen), Schwächung 0,012 je Meter, Licht über drei Proben zur Sonne, Henyey-Greenstein-Phase **[A, übliches Verfahren für Echtzeitwolken]**; Glut der Lava von unten **[G]**; Wolkenschatten am Boden **[A]**.
- **Dampf über heißer Lava**: offene Lava über 650 °C an der Oberfläche, mehr bei Regen **[Erscheinung L; Mengen G]**.
- **Hitzeflimmern**: Bildversatz über heißer Lava bis rund 3 km Abstand **[Erscheinung L; Stärke G]**.
- **Zeitraffer**: Belichtung folgt der Schätzung aus dem Sonnenstand sofort mit und gleicht den Rest zur Messung sanft aus **[G]**.

## Leben und Klang (Phase 15, `life/`, `sound/`, `render/LifeRenderer`, `render/SoundSampler`, Shader `life.vert`, `life.frag`, `cars.glsl`)
- **Nēnē**: rund 41 cm hoch, schwarzer Kopf und Hinterhals, gelbbraune Wangen, gefurchter Hals mit hellen und dunklen Streifen, schwarzer Schnabel und Beine; Weibchen 1,5–2,6 kg, Männchen 1,7–3,1 kg; grasen; Gössel bleiben bis zur nächsten Brutzeit bei den Eltern **[L, Wikipedia „Nene (bird)“]**. Brutzeit September bis Mai, ein Brutpaar am Parkplatz Uēkahuna 2024/25, viele Nēnē an oder auf den Parkstraßen, Autos häufigste Todesursache erwachsener Nēnē **[L, NPS HAVO, Meldung 12.11.2024, „Drive Chain of Craters Road“]**. Plätze, Zahl je Platz, Trupps im Sommer, Gangart, Flug (rund 17 m/s, 25–55 m hoch), Spannweite rund 1,1 m, Rufhäufigkeit **[G]**.
- **Waldvögel**: ʻapapane (karminrot, Flügel und Schwanz schwarz, Bauch weiß; häufig am Crater Rim Drive, wenn das ʻōhiʻa blüht; sehr vielgestaltiger Gesang), ʻamakihi (einer der häufigsten heimischen Waldvögel), ʻōmaʻo (Bergregenwald über rund 900 m), ʻio (regelmäßig im Park) **[L, NPS PACN „Native Hawaiian Forest Birds of HAVO“]**; Morgenchor **[L, NPS „Dawn Chorus“]**. Japanbrillenvogel als eingeführte, häufige Art **[L]**. Gesangsmuster, Dichte, Flug **[G]**.
- **Koaʻe kea**: kreisen über der Caldera und im Halemaʻumaʻu (nach 2018 auf der Suche nach neuen Brutplätzen), brüten an Klippen und Kraterwänden; schwarzes Band am inneren Flügel, schwarze Augenmaske, oranger Schnabel, Schwanzfedern der Männchen bis rund 40 cm **[L, NPS HAVO „Koaʻe Kea“]**; im Kīlauea Iki, Zahl, Bahn, Rufe **[G]**.
- **Hubschrauber**: Air Tour Management Plan des Parks (Dezember 2023): rund 1 548 Rundflüge im Jahr; Unternehmen A 4 am Tag (1 176/Jahr), B 185, C 109, D 77; Strecken Puʻu-ʻŌʻō-Schleife, Küste von Puna (beide Richtungen), Highway 11 im Kahuku-Teil; ≥ 1 500 ft über Grund, Küste 2 000 ft über dem Meer (nach Südwesten 3 000 ft) und 2 000 ft vor der Küste; Schweben nur auf der Schleife, je ≤ 1 min, zusammen ≤ 5 min; Mo/Di/Do/Fr/Sa 10–14 Uhr, nicht am 9.2. und 19.12. **[L, Hawaii Tribune-Herald, 22.12.2023]**. Die sechs hawaiischen Feiertage (jährlich festgelegt), leise Maschinen und der Kahuku-Teil fehlen. Mit vier Flügen je Flugtag an 259 Flugtagen ergeben sich rund 1 400 statt 1 548 Flüge **[A]**. Lage der Strecken, Tempo (46 m/s, Schleife 30 m/s), Beobachtungsflug des HVO bei Ausbrüchen **[G]**; Rotor 10,7 m, drei Blätter, rund 390 U/min **[Größenordnung L]**.
- **Wanderer, Autos**: Wege und Straßen wie seit Phase 3/4 **[L NPS-Karten]**; Zahl nach Tageszeit, Tempo (Wandern 1,0–1,4 m/s, Autos bis 15,6 m/s), Halte an Aussichten, Sperrzonen um Fontänen (600 m + 1,5 × Höhe, 0,8–1,5 km) **[G]**. Scheinwerfer: zwei Lampen zusammen rund 40 000 cd **[Größenordnung L]**, Abblendkegel **[G]**.
- **Klang**: ganz synthetisch, ohne Tondateien. Ausbreitung als Kugelwelle (−6 dB je Verdopplung), Luftdämpfung nach Größenordnung ISO 9613-1, Schall 343 m/s, Doppler c/(c ∓ v) **[L]**; Abbildung Schalldruck → Pegel gestaucht: dBFS = −56 + 0,62 · (SPL − 25) **[G]**. Bezugspegel **[G]**: Fontäne 95 dB in 100 m bei 100 m Höhe (+10 dB je Verzehnfachung), Lavasee 86 dB in 50 m, Brandung 82 dB in 20 m bei 1 m Welle, Hubschrauber 88 dB in 150 m, Auto 68 dB in 7,5 m, Nēnē 72 dB in 1 m. Coquí: Ruf „ko-kí“ aus einem tieferen (rund 1,1–1,2 kHz) und einem höheren, steigenden Ton (rund 2 kHz) **[L Größenordnung]**, Chor rund 70 dB **[L, CTAHR]**, breitet sich hangaufwärts zum Kīlauea aus **[L, NPS]**; Grenze bei rund 1 100–1 250 m **[G]**. Beben: Laufzeit P 6 km/s, S 3,5 km/s, Dauer nach Lee u. a. (hier ein Viertel), Stärke aus dem Ausschlag nach Hutton & Boore **[L wie Phase 14]**, Klang und Abbildung auf Schalldruck **[G]**. Brandung im Takt der Wellenphase wie im Bild **[A]**. Filter nach dem „Audio EQ Cookbook“ (R. Bristow-Johnson) **[L]**, Hall nach Schroeder **[L Verfahren]**.

## Wenn mehr Daten kommen (wahlweise)
- **GLSDEM N20W156** (gleiche Quelle, gleiches Format wie die drei vorhandenen Kacheln) ersetzt für Mauna Kea Nord und Kohala die 1-km-Werte von GTOPO30. Einfach als Ordner ins Projekt legen.
- **Meerestiefen**: GEBCO-Gitter (https://download.gebco.net/, Ausschnitt 18,5–20,6° N, 156,5–154,4° W) oder NOAA-Bathymetrie. Dafür braucht `DemSource` noch einen Leser (Phase 9, Ozean bis zur Kissenlava).
- **Feiner an Land**: USGS 3DEP 1/3″ (≈ 10 m) für Gipfel und Ostriftzone, Stand nach 2018; ersetzt dann auch die gestalteten Änderungen.

## Werte aus Phase 1, weiter gültig
- Gipfelhöhen: Mauna Loa 4 169 m, Mauna Kea 4 207 m, Hualālai 2 521 m, Kohala 1 670 m [L, USGS/NPS] (Prüfung gegen die Daten: Mauna Kea 4 188 m im 75-m-Raster).
- Sonne: NOAA-Formeln [L]; Zeitzone HST, UTC−10, ohne Sommerzeit [L].
- Passat aus Ostnordost 6–9 m/s [L, gerundet]; Dunst 1,2·10⁻⁵/m, Südswell 1 m [G].
- Fontänen 2025 bis etwa 300 m (Episode 26, Juni 2025) [L, Maui Now/USGS].
