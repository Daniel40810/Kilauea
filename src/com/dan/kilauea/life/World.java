package com.dan.kilauea.life;

/**
 * Was die Figuren je Bild von der Szene wissen: Uhr, Kamera, Wind, Regen und die Sperrzonen um aktive
 * Schlote und heiße Lava. Der Renderer füllt es; die Prüfung setzt es von Hand.
 */
public final class World {
    /** Uhrzeit (HST, Stunden) und Tag im Jahr 2026. */
    public double hour = 11;
    public int day = 280;
    public double sunElevation = 45;
    public double camX, camY = 2000, camZ;
    public double windX = -7, windZ = 2.5;
    public double rain;
    /** Sperrzonen: x, z, Halbmesser (m). */
    public final double[] hazards = new double[3 * 48];
    public int hazardCount;
    /** Stärkster aktiver Schlot (x, y, z) oder NaN: dort kreist der Beobachtungsflug. */
    public double ventX = Double.NaN, ventY, ventZ;
    /** Ob ein Drehbuch läuft. */
    public boolean script;

    public void clearHazards() { hazardCount = 0; ventX = Double.NaN; }

    public void hazard(double x, double z, double r) {
        if (hazardCount >= hazards.length / 3) return;
        hazards[3 * hazardCount] = x;
        hazards[3 * hazardCount + 1] = z;
        hazards[3 * hazardCount + 2] = r;
        hazardCount++;
    }

    /** Liegt (x, z) in einer Sperrzone (mit Rand m)? */
    public boolean closed(double x, double z, double margin) {
        for (int i = 0; i < hazardCount; i++) {
            double dx = x - hazards[3 * i], dz = z - hazards[3 * i + 1], r = hazards[3 * i + 2] + margin;
            if (dx * dx + dz * dz < r * r) return true;
        }
        return false;
    }

    /** Wochentag 0 = Montag … 6 = Sonntag (1. Januar 2026 war ein Donnerstag). */
    public static int weekday(int dayOfYear) { return Math.floorMod(3 + dayOfYear - 1, 7); }

    public static final String[] WEEKDAYS = {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};

    /** Hell genug für Tiere und Wanderer (Sonne höher als 6° unter dem Horizont, bürgerliche Dämmerung). */
    public boolean daylight() { return sunElevation > -6; }
}
