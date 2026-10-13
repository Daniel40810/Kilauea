package com.dan.kilauea.lava;

import com.dan.kilauea.terrain.Geo;

/**
 * Schlote zum Ausprobieren, nach echten Ausbrüchen. Lage nach USGS-Karten [L], Länge der Spalte und
 * genaue Stelle gestaltet [G]; die üblichen Förderraten als Größenordnung [L] (als blasige Lava gerechnet).
 * Die Fontäne wächst mit der Rate hoch 0,7 [A]: Austrittsgeschwindigkeit und Schlotweite wachsen beide mit.
 */
public enum VentSite {
    //            Name                        Breite    Länge       Breite2   Länge2      m³/s
    HALEMAUMAU("Halemaʻumaʻu (Gipfel)", 19.4060, -155.2850, 19.4064, -155.2842, 20, 250,
            "Schlot im Krater seit 2020; die Lava bleibt im Krater, der See steigt. Fontänen-Episoden 2024/25 mit hohen Raten für Stunden"),
    KILAUEA_IKI("Kīlauea Iki 1959", 19.4135, -155.2512, 19.4140, -155.2500, 50, 300,
            "Schlot an der Südwestwand; die Lava füllte den Krater zu einem See"),
    MAUNA_ULU("Mauna Ulu, Südflanke", 19.3654, -155.2026, 19.3653, -155.2018, 10, 25,
            "Austritt an der Südflanke des Schildkegels (1969–74); die Ströme liefen über Hōlei Pali bis ins Meer, oft in Röhren. "
                    + "Der Gipfelkrater selbst würde sich erst nach Tagen füllen"),
    PUU_OO("Puʻu ʻŌʻō, Schlot 61g", 19.3846, -155.0992, 19.3841, -155.0984, 4, 6,
            "Schlot 61g an der Ostflanke des Kegels (2016–18); im Mittel rund 4 m³/s über 35 Jahre, lange Röhren bis zur Küste"),
    KAMOKUNA("Kamokuna, Röhrenausgang", 19.3200, -155.0450, 19.3198, -155.0446, 4, 0,
            "Die Lava von 61g kam 2016/17 in einer Röhre zur Küste und lief bei Kamokuna über die Klippe ins Meer. Hier tritt sie "
                    + "rund 350 m vor der Klippe aus [Lage G], ohne Fontäne. Für Glut auf dem Wasser; der Meereseintritt selbst folgt in Phase 10"),
    FISSURE8("Spalte 8, 2018", 19.4623, -154.9089, 19.4627, -154.9081, 100, 60,
            "Untere Ostriftzone, Mai bis August 2018; ein Kanal trug bis rund 100 m³/s nach Kapoho"),
    SW_RIFT("Südwestriftzone 2024", 19.3790, -155.3080, 19.3775, -155.3095, 15, 20,
            "Kurzer Ausbruch im Juni 2024 südwestlich der Caldera, Spalten in der Kaʻū-Wüste [Lage G]");

    public final String label, note;
    public final double lat0, lon0, lat1, lon1, rate;
    /**
     * Höhe der Fontäne bei der üblichen Rate (m): Halemaʻumaʻu 2024/25 bis rund 300 m [L], Kīlauea Iki 1959
     * bis rund 580 m [L], Spalte 8 2018 meist 50–80 m [L], die übrigen [G].
     */
    public final double fountain;

    VentSite(String label, double lat0, double lon0, double lat1, double lon1, double rate, double fountain, String note) {
        this.fountain = fountain;
        this.label = label;
        this.lat0 = lat0;
        this.lon0 = lon0;
        this.lat1 = lat1;
        this.lon1 = lon1;
        this.rate = rate;
        this.note = note;
    }

    public double x0() { return Geo.x(lat0, lon0); }
    public double z0() { return Geo.z(lat0); }
    public double x1() { return Geo.x(lat1, lon1); }
    public double z1() { return Geo.z(lat1); }
    public double x() { return 0.5 * (x0() + x1()); }
    public double z() { return 0.5 * (z0() + z1()); }

    /** Höhe der Fontäne bei der Rate r (m). */
    public double fountainHeight(double r) { return r <= 0 ? 0 : fountain * Math.pow(r / rate, 0.7); }

    @Override public String toString() { return label; }
}
