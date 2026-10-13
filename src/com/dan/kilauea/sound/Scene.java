package com.dan.kilauea.sound;

/**
 * Was der Klang von der Szene wissen muss, als Schnappschuss (der Renderer füllt rund 20-mal je Sekunde
 * einen neuen; der Klangfaden liest nur). Lagen in Metern der Szene (x Osten, y oben, z Süden).
 */
public final class Scene {
    // Hörer
    public double x, y, z;
    /** Blickrichtung waagerecht (fx, fz) und rechts davon (rx, rz). */
    public double fx = 0, fz = -1, rx = 1, rz = 0;
    public double aboveGround = 2, elevation;
    public boolean underwater, inTube, walking;
    public double walkSpeed;
    /** Untergrund beim Gehen: 0 Pāhoehoe, 1 ʻaʻā oder Schlacke, 2 Weg, 3 Röhre, 4 Asche und Bimssand. */
    public int surface;
    // Luft
    public double wind, rain, trees, veg, rainYear = 2500, hour = 12, sunElevation = 40;
    // Feuer
    public final double[] fountains = new double[4 * 5];   // x, y, z, Höhe, Länge
    public int fountainCount;
    public final double[] hot = new double[8 * 3];          // x, z, Fläche (m²) heißer Lava
    public int hotCount;
    public boolean lake;
    public double lakeX, lakeY, lakeZ, lakeLevel;
    public final double[] entries = new double[4 * 3];      // Meereseintritt: x, z, Wärmestrom (MW)
    public int entryCount;
    public double pillowX = Double.NaN, pillowY, pillowZ;
    /** Nächste Dampfspalte (x, y, z) und ihr Abstand. */
    public double ventX, ventY, ventZ, ventDist = 1e9;
    // Meer
    public final double[] coast = new double[24 * 4];       // x, z, Klippe (1/0), Abstand
    public int coastCount;
    public double swellH, swellT = 14, swellDx, swellDz = 1, seaH, seaT = 6, seaDx = -1, seaDz;
    public double seaTime;
    /** Klangzeit, zu der der Klang diesen Schnappschuss zuerst benutzt hat (setzt die Maschine). */
    public double stamp = -1;
    // Leben
    public final float[] sources = new float[8 * 96];
    public int sourceCount;

    /** Den Schnappschuss mit Hörer und Blick setzen. */
    public void listener(double x, double y, double z, double yawFx, double yawFz) {
        this.x = x; this.y = y; this.z = z;
        double l = Math.hypot(yawFx, yawFz);
        if (l < 1e-9) { yawFx = 0; yawFz = -1; l = 1; }
        fx = yawFx / l; fz = yawFz / l;
        rx = -fz; rz = fx;
    }
}
