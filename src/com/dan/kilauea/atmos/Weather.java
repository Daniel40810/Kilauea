package com.dan.kilauea.atmos;

import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.HeightGrid;

/**
 * Passatwetter über der Insel, ohne Grafik (Phase 11). Eine Karte von 256 × 256 Feldern (750 m) über das
 * ganze Höhenraster mit vier Werten je Feld, die der Wolken-Shader liest:
 * <ul>
 * <li><b>Bedeckung</b> 0..1: über dem Meer vereinzelte Passatwolken (rund 20 %); wo der Wind den Hang
 * hinaufläuft und es viel regnet, ein Wolkenband zwischen rund 600 und 2 000 m; darüber, oberhalb der
 * Passatinversion, wolkenlos (die Gipfel von Mauna Loa und Mauna Kea ragen heraus) [L, Giambelluca u. a.;
 * Cao u. a. 2007 für die Inversion]. Nachmittags mehr Wolken am Hang, an der Kona-Seite Quellwolken aus der
 * Seebrise; nachts klart das Land auf [L Erscheinung; Stärke G].</li>
 * <li><b>Regen</b> 0..1: wo das Wolkenband dicht ist und der Hang steigt; Schauer ziehen mit dem Wind [A/G].</li>
 * <li><b>Vog</b> 0..1: die Gasfahne aus dem Halemaʻumaʻu als Fahne im Wind (wird mit dem Abstand breiter und
 * dünner), dazu Dunst an der Leeküste, wohin der Vog zieht [L Erscheinung, USGS; Ausbreitung A].</li>
 * <li><b>Hebung</b> 0..1: wie stark der Wind die Luft am Hang hebt [A].</li>
 * </ul>
 * Aus der Karte folgt das Wetter an der Kamera: Regen, Nässe, Regenbogen, Nebel in der Wolke.
 */
public final class Weather {
    public static final int N = 256;
    public static final double CELL = HeightGrid.EXTENT / N;
    /** Wolkenuntergrenze und Obergrenze (Passatinversion), m ü. M. [L, gerundet]. */
    public static final double BASE = 650, TOP = 2100;
    public final float[] map = new float[N * N * 4];
    private final float[] next = new float[N * N * 4];
    public volatile int version;
    private final float[] h = new float[N * N], rain = new float[N * N];
    private boolean terrain;
    private volatile double lastWindDir = Double.NaN, lastHour = Double.NaN, lastWind = Double.NaN;
    private static final double SX = Geo.x(19.4072, -155.2834), SZ = Geo.z(19.4072);

    /** Gelände und Jahresregen einlesen (einmal, sobald das Raster steht). */
    public void terrain(HeightGrid g) {
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double x = HeightGrid.X0 + (i + 0.5) * CELL, z = HeightGrid.Z0 + (j + 0.5) * CELL;
                h[j * N + i] = (float) g.height(x, z);
                rain[j * N + i] = (float) (g.geo(x, z, 3) * com.dan.kilauea.terrain.Geology.RAIN_MAX);
            }
        terrain = true;
        lastHour = Double.NaN;
    }

    /** Für Prüfungen ohne Raster: Gelände und Regen aus Funktionen. */
    public void terrain(java.util.function.DoubleBinaryOperator height, java.util.function.DoubleBinaryOperator rainMm) {
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double x = HeightGrid.X0 + (i + 0.5) * CELL, z = HeightGrid.Z0 + (j + 0.5) * CELL;
                h[j * N + i] = (float) height.applyAsDouble(x, z);
                rain[j * N + i] = (float) rainMm.applyAsDouble(x, z);
            }
        terrain = true;
        lastHour = Double.NaN;
    }

    public boolean ready() { return terrain; }

    /**
     * Karte neu rechnen, wenn sich Wind oder Stunde merklich geändert haben. wind: Stärke (m/s), Richtung,
     * aus der er kommt (Grad); hour: Uhrzeit (HST).
     */
    public synchronized boolean due(double windSpeed, double windFromDeg, double hour) {
        return terrain && (Double.isNaN(lastHour) || Math.abs(hour - lastHour) >= 0.03 || Math.abs(windFromDeg - lastWindDir) >= 2 || Math.abs(windSpeed - lastWind) >= 0.3);
    }

    public void update(double windSpeed, double windFromDeg, double hour) {
        if (!terrain) return;
        if (!Double.isNaN(lastHour) && Math.abs(hour - lastHour) < 0.03 && Math.abs(windFromDeg - lastWindDir) < 2 && Math.abs(windSpeed - lastWind) < 0.3) return;
        lastHour = hour; lastWindDir = windFromDeg; lastWind = windSpeed;
        double a = Math.toRadians(windFromDeg + 180);
        double wx = Math.sin(a), wz = -Math.cos(a);           // wohin der Wind weht
        double strength = Math.min(1.5, windSpeed / 7.5);
        float[] map = next;
        // Tagesgang: nachmittags mehr Wolken über Land, nachts weniger [G]
        double after = smooth(10, 14, hour) * (1 - smooth(17, 20, hour));
        double night = 1 - smooth(6, 9, hour) * (1 - smooth(19, 22, hour));
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int k = j * N + i;
                double hh = h[k];
                double x = HeightGrid.X0 + (i + 0.5) * CELL, z = HeightGrid.Z0 + (j + 0.5) * CELL;
                double gx = (h[j * N + Math.min(N - 1, i + 1)] - h[j * N + Math.max(0, i - 1)]) / (2 * CELL);
                double gz = (h[Math.min(N - 1, j + 1) * N + i] - h[Math.max(0, j - 1) * N + i]) / (2 * CELL);
                double up = wx * gx + wz * gz;                          // > 0: der Wind läuft den Hang hinauf
                double lift = clamp(up / 0.06) * strength;
                double cover;
                if (hh < 1) cover = 0.18 + 0.05 * strength;            // vereinzelte Passatwolken über dem Meer
                else {
                    double band = smooth(150, 700, hh) * (1 - smooth(1900, 2400, hh));
                    double wet = smooth(900, 4500, rain[k]);
                    double windward = Math.max(lift, 0.5 * wet);
                    cover = 0.15 + band * (0.75 * windward * (0.75 + 0.25 * after));
                    // Lee (Kona): Seebrise baut nachmittags Quellwolken an den Hängen [L Erscheinung]
                    double lee = clamp(-up / 0.04);
                    cover += band * lee * 0.45 * after;
                    cover *= 1 - 0.35 * night * (1 - wet);
                    if (hh > 2300) cover *= 1 - smooth(2300, 2700, hh);    // über der Inversion klar
                }
                cover = clamp(cover);
                double r = clamp((cover - 0.55) / 0.35) * (0.4 + 0.6 * Math.max(lift, smooth(1500, 5000, rain[k])));
                // Vog: Gasfahne aus dem Halemaʻumaʻu im Wind
                double dx = x - SX, dz = z - SZ;
                double s = dx * wx + dz * wz, c = -dx * wz + dz * wx;
                double vog = 0;
                if (s > -1500) {
                    double sig = 600 + 0.18 * Math.max(0, s);
                    vog = Math.exp(-c * c / (2 * sig * sig)) * 900 / sig * smooth(-1500, 500, s) * Math.exp(-Math.max(0, s) / 120_000);
                }
                // Dunst im Lee, wohin der Vog zieht (Kona-Küste) [L Erscheinung]
                double leeCoast = hh < 900 ? clamp(-up / 0.03 + 0.3) * smooth(30_000, 0, Math.hypot(dx, dz) - 40_000) * 0.25 : 0;
                vog = clamp(vog + leeCoast);
                map[4 * k] = (float) cover;
                map[4 * k + 1] = (float) r;
                map[4 * k + 2] = (float) vog;
                map[4 * k + 3] = (float) lift;
            }
        synchronized (this) {
            System.arraycopy(next, 0, this.map, 0, next.length);
            version++;
        }
    }

    /** Bilinear: Kanal ch (0 Bedeckung, 1 Regen, 2 Vog, 3 Hebung) an (x, z). */
    public double at(double x, double z, int ch) {
        double u = (x - HeightGrid.X0) / CELL - 0.5, v = (z - HeightGrid.Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        return (m(i, j, ch) * (1 - fx) + m(i + 1, j, ch) * fx) * (1 - fz) + (m(i, j + 1, ch) * (1 - fx) + m(i + 1, j + 1, ch) * fx) * fz;
    }

    private double m(int i, int j, int ch) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return map[4 * (j * N + i) + ch];
    }

    // ------------------------------------------------------------ Wetter an der Kamera
    /** Sanft nachgeführt: Regen, Nässe, Regenbogen, Nebel (alle 0..1). */
    public double rainHere, wetHere, rainbowHere, mistHere;

    /**
     * Wetter an der Kamera nachführen. sunEl: Höhe der Sonne (Grad), sunAz: Richtung (Grad, wohin sie
     * scheint ist egal; gebraucht wird der Gegenpunkt).
     */
    public void camera(double dt, double x, double y, double z, double sunElDeg, double[] sunDir) {
        if (!terrain) return;
        double cover = at(x, z, 0), r = at(x, z, 1);
        double under = y < BASE + 200 ? 1 : 0;
        double inCloud = y > BASE && y < TOP ? smooth(0.45, 0.85, cover) : 0;
        double rainTarget = r * under;
        double k = 1 - Math.exp(-dt / 20);
        rainHere += (rainTarget - rainHere) * k;
        // nass in Minuten, trocken in einer halben Stunde [G]
        double wk = rainHere > wetHere ? 1 - Math.exp(-dt / 180) : 1 - Math.exp(-dt / 1800);
        wetHere += (Math.max(rainHere, 0) - wetHere) * wk;
        mistHere += (inCloud - mistHere) * k;
        // Regenbogen: Sonne tief (unter 42°) und frei, Regen im Gegenpunkt rund 3 km entfernt
        double rb = 0;
        if (sunElDeg > 1 && sunElDeg < 42 && sunDir != null) {
            double ax = x - sunDir[0] * 3000 / Math.max(0.2, Math.hypot(sunDir[0], sunDir[2])), az = z - sunDir[2] * 3000 / Math.max(0.2, Math.hypot(sunDir[0], sunDir[2]));
            double sunFree = 1 - smooth(0.4, 0.8, cover) * (y < BASE ? 1 : 0);
            rb = at(ax, az, 1) * sunFree * smooth(42, 30, sunElDeg);
        }
        rainbowHere += (clamp(rb * 1.5) - rainbowHere) * k;
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    static double smooth(double a, double b, double v) {
        double t = clamp((v - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
}
