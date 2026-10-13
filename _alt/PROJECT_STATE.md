# KĪLAUEA SIMULATION: PROJECT_STATE & SPRINT LEDGER
> **Stand:** 07.10.2026  
> **Repository:** `C:\Users\Dan40810\Documents\NetBeansProjects\Kīlauea`  
> **Technologie-Stack:** Java 21, JOGL 2.4+ (OpenGL 4.5 Core), GLSL, Oracle 21c Spatial (PDBORCL), NetBeans  

---

## 📊 GESAMTSTATUS

| Sprint | Modul | Schwerpunkt | Status |
|---|---|---|---|
| **0** | Projekt-Initialisierung & Assets | Icon mit Goldrand (`kilauea.ico`, `kilauea.png`), NetBeans-Setup, DDL `SCHEMA_KILAUEA.sql` | **DONE** |
| **1** | `com.dan.kilauea.db` & `.geo` | Oracle 21c Tabellen, SDO_GEOMETRY, USGS Geländegitter | **READY** |
| **2** | `com.dan.kilauea.lava` | Lavarheologie (Herschel-Bulkley), Pāhoehoe/ʻAʻā, Planck-Blackbody | **TODO** |
| **3** | `com.dan.kilauea.ocean` | **KERNTOPIC:** Ozeaneintritt, Hydrovulkanismus, Lavakissen (Pillow Lava) | **TODO** |
| **4** | `com.dan.kilauea.water` | Gerstner-Ozeanwellen, Laze-Dampfplume, Gischt, Kaustik | **TODO** |
| **5** | `com.dan.kilauea.shader` | GLSL PBR, Volumetrische God Rays, Heat Haze Distortion, Bloom | **TODO** |
| **6** | `com.dan.kilauea.ecosystem` | Interaktion mit `com.dan.road`, `com.dan.forest`, `com.dan.river`, `com.dan.ground` | **TODO** |
| **7** | `com.dan.kilauea.app` | JFrame maximiert (`MAXIMIZED_BOTH`), Kamera-Splines, Orbit/Zoom UI | **TODO** |

---

## 🎯 AKTUELLER SPRINT: SPRINT 1 (ORACLE 21c & GEOLOGIE)

### Aufgaben für nächste Session:
1. Erstelle `src/com/dan/kilauea/db/DatabaseManager.java` zur Verbindung mit `jdbc:oracle:thin:@//localhost:1521/PDBORCL` über `db/db.properties`.
2. Initialisiere SQL DDL Script `db/SCHEMA_KILAUEA.sql` für Geländekacheln (`KILAUEA_TERRAIN_TILES`) und Telemetrie (`KILAUEA_TELEMETRY`).
3. Schreibe Terrain-Generator mit USGS Kīlauea Topographie (Caldera, Halemaʻumaʻu, Rift Zones bis zur Küste).

---

## 📁 BESTEHENDE SYSTEME IM WORKSPACE

- `lib/jogl-all.jar`, `lib/gluegen-rt.jar` (Native OpenGL 3D Rendering)
- `lib/ojdbc11.jar`, `lib/sdoapi.jar`, `lib/sdogr.jar`, `lib/sdoutl.jar` (Oracle 21c Spatial)
- `icon/kilauea.ico`, `icon/kilauea.png` (App-Icon mit Goldrand und Lavakissen-Meeresmündung)
- `com.dan.river`: Strömungsfeld, Manning-Formel, Kaustik, Wellen
- `com.dan.road`: Trassierung, Catmull-Rom, Belag, Verkehr, Wetter
- `com.dan.ground`: Vegetationsdecke, Kachelung, Detailstufen
- `com.dan.forest`: Baumbestände, Windfeld, Jahreszeiten
