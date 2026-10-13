package com.dan.kilauea.terrain;

/**
 * Verformung des Geländes zur Laufzeit (Phase 12) als eigene Schicht über dem Gelände von heute: zuerst der
 * Einsturz des Gipfels 2018. Die Schicht kennt den Gipfel vor dem Einsturz (Unterschied zum Gelände von heute,
 * je 8-m-Zelle) und einen Fortschritt F von 0 (vor dem Einsturz) bis 1 (heute). Der Krater sackt zuerst, der
 * Ring außen zuletzt, wie im Sommer 2018: erst vertiefte und weitete sich Halemaʻumaʻu, dann brach der
 * Calderaboden in Stufen ab [L, USGS; Ablauf über den Ort G]. Ist die Schicht aus, gilt das Gelände von heute.
 * <p>
 * Der Gipfel vor 2018 ist gestaltet [G]: der abgesackte Block wird aus der Höhe seines Randes aufgefüllt
 * (gewichtetes Mittel über den Rand), darin der alte Halemaʻumaʻu mit rund 900 m Weite und 85 m Tiefe [L,
 * Größenordnung]. Der Overlook-Krater mit dem Lavasee von 2008–2018 ist die Grube des Lavasees.
 * <p>
 * Java ({@link #at}) und Shader ({@code deformAt} in lava.glsl) rechnen dasselbe: bilinear aus dem Raster,
 * mal (1 − g(F, r)), g = clamp(1,6 F − 0,6 r) mit r = 0 in der Mitte und 1 am Ring.
 */
public final class Deformation {
    private Deformation() { }

    public static final int N = 500;
    public static final double CELL = 8, SIDE = N * CELL;
    /** Mitte des Einsturzes (wie Changes2018). */
    static final double[] CENTER = Changes2018.HMM;
    /** Alter Halemaʻumaʻu (vor 2008), etwas westlich des Overlook-Kraters [L Lage, gerundet]. */
    static final double[] OLD_HMM = {19.4063, -155.2862};
    static final double OLD_R = 460, OLD_DEPTH = 85;

    private static volatile float[] delta, ring;
    private static double x0, z0;
    private static volatile boolean on;
    private static volatile double progress = 1;
    private static volatile float maxUp;
    private static volatile int version;

    /** Raster bauen (einmal, braucht das Höhenraster; rund 0,3 s). Ändert nichts am Bild, solange die Schicht aus ist. */
    public static synchronized void build() {
        if (delta != null || Ground.grid() == null) return;
        boolean was = on;
        on = false;
        double cx = Geo.x(CENTER[0], CENTER[1]), cz = Geo.z(CENTER[0]);
        x0 = cx - SIDE / 2;
        z0 = cz - SIDE / 2;
        // Rand des Blocks (ohne Rauschen bei rb = 1,3): Höhen von heute als Stützstellen
        int M = 240;
        double[] bx = new double[M], bz = new double[M], bh = new double[M];
        double a = Math.toRadians(35), ca = Math.cos(a), sa = Math.sin(a);
        for (int k = 0; k < M; k++) {
            double t = 2 * Math.PI * k / M, u = 1.3 * Math.cos(t) * 1300, v = 1.3 * Math.sin(t) * 1000;
            bx[k] = cx + u * ca - v * sa;
            bz[k] = cz + u * sa + v * ca;
            bh[k] = Ground.baseHeight(bx[k], bz[k]);
        }
        double ox = Geo.x(OLD_HMM[0], OLD_HMM[1]), oz = Geo.z(OLD_HMM[0]);
        float[] d = new float[N * N], r = new float[N * N];
        float up = 0;
        for (int j = 0; j < N; j++) {
            double z = z0 + (j + 0.5) * CELL;
            for (int i = 0; i < N; i++) {
                double x = x0 + (i + 0.5) * CELL;
                double dx = x - cx, dz = z - cz;
                double u = (dx * ca + dz * sa) / 1300, v = (-dx * sa + dz * ca) / 1000;
                double rb = Math.sqrt(u * u + v * v) + 0.16 * (Noise.fbm(x * 0.0011 + 3, z * 0.0011 - 1, 3) - 0.5) * 2;
                double m = Island.smooth(1.32, 1.12, rb);
                int k = j * N + i;
                r[k] = (float) Math.max(0, Math.min(1, rb / 1.2));
                if (m <= 0) continue;
                // gewichtetes Mittel des Randes (Shepard, p = 2)
                double sw = 0, sh = 0;
                for (int q = 0; q < M; q++) {
                    double w = 1 / (Math.pow(x - bx[q], 2) + Math.pow(z - bz[q], 2) + 2500);
                    sw += w;
                    sh += w * bh[q];
                }
                double fill = sh / sw + 6 * (Noise.fbm(x * 0.004 + 2, z * 0.004, 3) - 0.5);
                // alter Halemaʻumaʻu: steile Wand, ebener Boden, etwas unruhig
                double ro = Math.hypot(x - ox, (z - oz) * 1.1) + 40 * (Noise.fbm(x * 0.003 - 5, z * 0.003 + 1, 3) - 0.5);
                fill -= OLD_DEPTH * Island.smooth(OLD_R + 25, OLD_R - 35, ro);
                double today = Ground.rawHeight(x, z);
                double dd = (fill - today) * m;
                d[k] = (float) dd;
                up = (float) Math.max(up, dd);
            }
        }
        delta = d;
        ring = r;
        maxUp = up;
        version++;
        on = was;
    }

    /** Schicht an (Fortschritt F: 0 = Gipfel vor 2018, 1 = heute) oder aus. */
    public static void set(boolean active, double f) {
        if (active && delta == null) build();
        progress = Math.max(0, Math.min(1, f));
        on = active && delta != null;
        version++;
    }

    public static boolean active() { return on; }
    public static double progress() { return progress; }
    /** Zählt jede Änderung (die Grafik lädt dann neu oder setzt die Uniforms). */
    public static int version() { return version; }
    public static float[] delta() { return delta; }
    public static float[] ring() { return ring; }
    public static double originX() { return x0; }
    public static double originZ() { return z0; }

    /** Anteil des Absackens an einer Stelle mit Ringmaß r (0 Mitte, 1 Ring) bei Fortschritt F. */
    public static double sunk(double f, double r) { return Math.max(0, Math.min(1, 1.6 * f - 0.6 * r)); }

    /** Höhenänderung (m) gegenüber heute an (x, z); 0, wenn aus. */
    public static double at(double x, double z) {
        if (!on) return 0;
        float[] d = delta, r = ring;
        if (d == null) return 0;
        double fi = (x - x0) / CELL - 0.5, fj = (z - z0) / CELL - 0.5;
        if (fi < 0 || fj < 0 || fi >= N - 1 || fj >= N - 1) return 0;
        int i = (int) fi, j = (int) fj;
        double tx = fi - i, tz = fj - j;
        int k = j * N + i;
        double dv = (d[k] * (1 - tx) + d[k + 1] * tx) * (1 - tz) + (d[k + N] * (1 - tx) + d[k + N + 1] * tx) * tz;
        if (dv == 0) return 0;
        double rv = (r[k] * (1 - tx) + r[k + 1] * tx) * (1 - tz) + (r[k + N] * (1 - tx) + r[k + N + 1] * tx) * tz;
        return dv * (1 - sunk(progress, rv));
    }

    /** Spanne der Höhenänderung in einem Quadrat erweitern (für die Auswahl der Geländeknoten): yr = {unten, oben}. */
    public static void range(double nx, double nz, double size, double[] yr) {
        if (!on) return;
        if (nx > x0 + SIDE || nz > z0 + SIDE || nx + size < x0 || nz + size < z0) return;
        yr[1] += maxUp + 1;
    }

    /** Fläche, in der die Schicht wirkt (x0, z0, Seite), für Pflanzen und Bodendecke. */
    public static double[] rect() { return new double[]{x0, z0, SIDE}; }
}
