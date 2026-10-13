package com.dan.kilauea.monitor;

import com.dan.kilauea.terrain.Geo;

import java.util.List;

/**
 * Eine Messstation des Messnetzes (Phase 14). Kürzel und Ort der Neigungsmesser und GPS-Stationen nach dem
 * USGS Hawaiian Volcano Observatory [L]; wo die genaue Lage nicht veröffentlicht oder hier nur genähert ist,
 * steht das in {@link #where}. Die Seismometer tragen keine echten Kürzel: ihre Lagen sind gestaltet [G], damit
 * jeder Teil des Vulkans eine Station in der Nähe hat.
 *
 * @param code  Kürzel (UWD, SDH, UWEV, CRIM) oder ein Name
 * @param name  Ort
 * @param kind  Art
 * @param lat   Breite
 * @param lon   Länge
 * @param where Herkunft der Lage
 */
public record Station(String code, String name, Kind kind, double lat, double lon, Prov where) {

    public enum Kind { TILT, GPS, SEISMIC }

    public double x() { return Geo.x(lat, lon); }
    public double z() { return Geo.z(lat); }

    /** Bezeichnung für Tafeln: „UWD (Uēkahuna)“. */
    public String title() { return code.equals(name) ? name : code + " (" + name + ")"; }

    // ------------------------------------------------------------------ Neigungsmesser

    /**
     * Uēkahuna-Neigungsmesser am Nordwestrand der Caldera. HVO nennt ihn heute UWD (Bohrloch), früher UWE
     * [L, HVO-Meldungen 2023–2025]; Lage wie {@code MagmaSystem.UWE} [L, gerundet].
     */
    public static final Station UWD = new Station("UWD", "Uēkahuna", Kind.TILT, 19.4211, -155.2938, Prov.L);
    /** Sand Hill, südlich bis südwestlich der Caldera [L Name und Seite; Lage genähert G]. */
    public static final Station SDH = new Station("SDH", "Sand Hill", Kind.TILT, 19.3965, -155.2990, Prov.G);

    // ------------------------------------------------------------------ GPS

    /** UWEV am Nordwestrand der Caldera [L Name und Seite, Lage wie UWD G]. */
    public static final Station UWEV = new Station("UWEV", "Uēkahuna", Kind.GPS, 19.4207, -155.2925, Prov.G);
    /** CRIM am Südrand der Caldera, UWEV gegenüber [L Name und Seite; Lage genähert G]. */
    public static final Station CRIM = new Station("CRIM", "Crater Rim", Kind.GPS, 19.3950, -155.2740, Prov.G);

    // ------------------------------------------------------------------ Seismometer (gestaltet)

    public static final List<Station> SEISMIC = List.of(
            new Station("Uēkahuna", "Uēkahuna", Kind.SEISMIC, 19.4211, -155.2938, Prov.G),
            new Station("Kīlauea Iki", "Kīlauea Iki", Kind.SEISMIC, 19.4185, -155.2440, Prov.G),
            new Station("Kaʻū-Wüste", "Kaʻū-Wüste", Kind.SEISMIC, 19.3700, -155.3400, Prov.G),
            new Station("Mauna Ulu", "Mauna Ulu", Kind.SEISMIC, 19.3720, -155.2100, Prov.G),
            new Station("Puʻu ʻŌʻō", "Puʻu ʻŌʻō", Kind.SEISMIC, 19.3930, -155.1110, Prov.G),
            new Station("Hōlei", "Hōlei", Kind.SEISMIC, 19.3120, -155.1150, Prov.G),
            new Station("Leilani", "Leilani", Kind.SEISMIC, 19.4700, -154.9200, Prov.G),
            new Station("Kapoho", "Kapoho", Kind.SEISMIC, 19.5050, -154.8400, Prov.G));

    /** Das Seismometer, das (x, z) am nächsten ist. */
    public static int nearestSeismic(double x, double z) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < SEISMIC.size(); i++) {
            Station s = SEISMIC.get(i);
            double d = Math.hypot(s.x() - x, s.z() - z);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }
}
