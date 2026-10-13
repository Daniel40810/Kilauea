package com.dan.kilauea.terrain;

/**
 * Gerechnete Insel (Phase 1; seit Phase 2 Ersatz, wenn keine Höhendaten im Projekt liegen, und
 * Quelle für Bodenart und Meeresboden): die Insel Hawaiʻi als gerechnete Fläche. Fünf Schildvulkane
 * (Mauna Loa, Mauna Kea, Hualālai, Kohala, Kīlauea mit seinen Riftzonen), die Küstenlinie als
 * Vieleck nach Karte, Caldera, Krater und Kegel, die Steilstufen der Südflanke und der Meeresboden.
 * <p>
 * <b>Herkunft:</b> Gipfelhöhen und Lagen [L, USGS und NPS, gerundet]; Küstenlinie aus rund 50 Punkten
 * nach Karte [L, auf etwa 1 km genähert]; Form der Schilde, Kraterfiguren, Steilstufen und Meeresboden
 * sind gestaltet [G]. Seit Phase 2 liefert {@link TerrainBuilder} die Höhen aus den Daten im Projekt.
 */
public final class Island {
    private Island() { }

    // ------------------------------------------------------------------ Küstenlinie (Breite, Länge), im Uhrzeigersinn ab Upolu Point
    private static final double[][] COAST = {
            {20.270, -155.858}, {20.240, -155.780}, {20.205, -155.733}, {20.120, -155.585}, {20.090, -155.470},
            {20.050, -155.370}, {19.990, -155.240}, {19.900, -155.120}, {19.840, -155.080}, {19.735, -155.060},
            {19.730, -155.000}, {19.640, -154.925}, {19.580, -154.870}, {19.525, -154.808}, {19.500, -154.815},
            {19.460, -154.835}, {19.400, -154.905}, {19.350, -154.970}, {19.322, -155.030}, {19.312, -155.050},
            {19.291, -155.093}, {19.276, -155.150}, {19.268, -155.250}, {19.262, -155.320}, {19.230, -155.400},
            {19.135, -155.505}, {19.090, -155.550}, {18.970, -155.620}, {18.911, -155.681}, {18.990, -155.800},
            {19.070, -155.890}, {19.180, -155.910}, {19.380, -155.900}, {19.480, -155.930}, {19.560, -155.970},
            {19.640, -155.995}, {19.730, -156.060}, {19.840, -155.990}, {19.920, -155.890}, {19.970, -155.850},
            {20.035, -155.830}, {20.180, -155.900}};
    private static final double[] CX = new double[COAST.length], CZ = new double[COAST.length];

    // Schilde: Breite, Länge, Höhe (m), Halbachsen (m) längs und quer, Richtung der Längsachse (Grad von Nord), Exponent
    private static final double[][] SHIELDS = {
            {19.4721, -155.5922, 4169, 74000, 40000, 40, 1.35},   // Mauna Loa, Längsachse NO–SW
            {19.8207, -155.4681, 4207, 46000, 40000, 70, 1.30},   // Mauna Kea
            {19.6920, -155.8700, 2521, 30000, 22000, 150, 1.30},  // Hualālai
            {20.0860, -155.7170, 1670, 34000, 18000, 135, 1.10}}; // Kohala
    private static final double[][] SH = new double[SHIELDS.length][];

    // Kīlauea: Kammlinie der Riftzonen (Breite, Länge, Kammhöhe m) — Ostriftzone und Südwestriftzone
    private static final double[][] ERZ = {
            {19.4072, -155.2834, 1235}, {19.4150, -155.2470, 1170}, {19.3930, -155.2280, 1060}, {19.3710, -155.2050, 960},
            {19.3800, -155.1500, 820}, {19.3880, -155.1050, 700}, {19.4100, -155.0300, 470}, {19.4400, -154.9600, 290},
            {19.4602, -154.9100, 120}, {19.4900, -154.8600, 70}, {19.5160, -154.8110, 12}};
    private static final double[][] SWRZ = {
            {19.4072, -155.2834, 1235}, {19.3800, -155.3250, 1010}, {19.3400, -155.3800, 760}, {19.2900, -155.4350, 450},
            {19.2400, -155.4800, 180}};
    private static final double[] EX = new double[ERZ.length], EZ = new double[ERZ.length], SX = new double[SWRZ.length], SZ = new double[SWRZ.length];

    // Steilstufen der Südflanke (Pali): Linie (Breite, Länge) … und Sprunghöhe (m)
    private static final double[][][] PALI = {
            {{19.3150, -155.3700}, {19.3050, -155.3100}, {19.3000, -155.2500}, {19.3050, -155.1900}, {19.3100, -155.1300}},   // Hilina / Poliokeawe
            {{19.2950, -155.2400}, {19.2960, -155.1700}, {19.3000, -155.1100}, {19.3200, -155.0600}}};                        // Hōlei Pali
    private static final double[] PALI_DROP = {360, 300};
    private static final double[][][] PL = new double[PALI.length][][];

    // Senken: Breite, Länge, Halbachse a (m), b (m), Drehung (Grad), Tiefe (m), Wandschärfe (0..1, 1 = senkrecht)
    private static final double[][] PITS = {
            {19.4085, -155.2790, 2900, 1900, 40, 115, 0.85},    // Caldera Kaluapele (Ringstufen 2018)
            {19.4072, -155.2834, 900, 750, 30, 230, 0.92},      // Halemaʻumaʻu nach 2018, Boden teils wieder gefüllt
            {19.4160, -155.2465, 850, 420, 70, 120, 0.90},      // Kīlauea Iki
            {19.3970, -155.2680, 420, 380, 0, 55, 0.88},        // Keanakākoʻi
            {19.3900, -155.2500, 230, 200, 0, 60, 0.9},         // Lua Manu / Puhimau
            {19.3820, -155.2350, 260, 220, 0, 75, 0.9},         // Koʻokoʻolau / Hiʻiaka
            {19.3780, -155.2200, 300, 180, 70, 95, 0.9},        // Pauahi
            {19.4721, -155.5922, 2400, 1400, 30, 170, 0.85}};   // Mokuʻāweoweo (Mauna Loa)
    private static final double[][] PT = new double[PITS.length][];

    // Kegel: Breite, Länge, Fußhalbmesser (m), Höhe (m), Kraterhalbmesser (m), Kratertiefe (m)
    private static final double[][] CONES = {
            {19.3710, -155.2050, 1500, 120, 220, 90},    // Mauna Ulu
            {19.3880, -155.1050, 1300, 150, 420, 230},   // Puʻu ʻŌʻō, nach dem Einsturz 2018
            {19.4602, -154.9100, 420, 55, 120, 40},      // Spalte 8
            {19.4180, -155.2520, 300, 40, 0, 0}};        // Puʻu Puaʻi (1959)
    private static final double[][] CN = new double[CONES.length][];

    static {
        for (int i = 0; i < COAST.length; i++) { CX[i] = Geo.x(COAST[i][0], COAST[i][1]); CZ[i] = Geo.z(COAST[i][0]); }
        for (int i = 0; i < SHIELDS.length; i++) {
            double[] s = SHIELDS[i];
            double a = Math.toRadians(s[5]);
            SH[i] = new double[]{Geo.x(s[0], s[1]), Geo.z(s[0]), s[2], s[3], s[4], Math.sin(a), -Math.cos(a), s[6]};
        }
        for (int i = 0; i < ERZ.length; i++) { EX[i] = Geo.x(ERZ[i][0], ERZ[i][1]); EZ[i] = Geo.z(ERZ[i][0]); }
        for (int i = 0; i < SWRZ.length; i++) { SX[i] = Geo.x(SWRZ[i][0], SWRZ[i][1]); SZ[i] = Geo.z(SWRZ[i][0]); }
        for (int k = 0; k < PALI.length; k++) {
            PL[k] = new double[PALI[k].length][];
            for (int i = 0; i < PALI[k].length; i++) PL[k][i] = new double[]{Geo.x(PALI[k][i][0], PALI[k][i][1]), Geo.z(PALI[k][i][0])};
        }
        for (int i = 0; i < PITS.length; i++) {
            double[] p = PITS[i];
            double a = Math.toRadians(p[4]);
            PT[i] = new double[]{Geo.x(p[0], p[1]), Geo.z(p[0]), p[2], p[3], Math.cos(a), Math.sin(a), p[5], p[6]};
        }
        for (int i = 0; i < CONES.length; i++) {
            double[] c = CONES[i];
            CN[i] = new double[]{Geo.x(c[0], c[1]), Geo.z(c[0]), c[2], c[3], c[4], c[5]};
        }
    }

    /** Bereich der Fläche in Metern (x von, x bis, z von, z bis): die ganze Insel mit Meer ringsum. */
    public static final double X0 = -100_000, X1 = 75_000, Z0 = -115_000, Z1 = 75_000;

    // ------------------------------------------------------------------ Abstand zur Küste

    /** Vorzeichenbehafteter Abstand zur Küstenlinie in Metern: positiv an Land, negativ im Meer. */
    public static double coastDistance(double x, double z) {
        double best = Double.MAX_VALUE;
        boolean inside = false;
        int n = CX.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = CX[j], az = CZ[j], bx = CX[i], bz = CZ[i];
            if ((bz > z) != (az > z) && x < (ax - bx) * (z - bz) / (az - bz) + bx) inside = !inside;
            double vx = bx - ax, vz = bz - az;
            double t = ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz);
            t = Math.max(0, Math.min(1, t));
            double dx = x - (ax + vx * t), dz = z - (az + vz * t);
            double d = dx * dx + dz * dz;
            if (d < best) best = d;
        }
        double d = Math.sqrt(best);
        d = inside ? d : -d;
        // Die Küste ist nicht gerade: Buchten und Landzungen (bis rund 400 m), dazu kleine Vorsprünge [G]
        if (Math.abs(d) < 3000) {
            d += 420 * (Noise.fbm(x * 0.00042 + 5.3, z * 0.00042 - 2.1, 4) - 0.5) * 2;
            d += 90 * (Noise.fbm(x * 0.0035, z * 0.0035, 3) - 0.5) * 2;
        }
        return d;
    }

    // ------------------------------------------------------------------ Höhe

    /** Höhe über dem Meer in Metern (unter Wasser negativ). */
    public static double height(double x, double z) {
        return height(x, z, coastDistance(x, z));
    }

    /** Höhe mit schon bekanntem Küstenabstand (spart beim Netzbau die teuerste Rechnung). */
    public static double height(double x, double z, double sd) {
        if (sd < 0) return seaFloor(x, z, -sd);
        double h = land(x, z);
        // Küste: 10 bis 25 m hohe Meeresklippe (an der Kīlauea-Küste), sonst flacher Strand
        double cliff = 10 + 15 * Noise.value(x * 0.00025, z * 0.00025);
        double inland = smooth(0, 2500, sd);
        double top = Math.max(cliff, h);
        h = cliff + (top - cliff) * inland;
        // Kante der Klippe: in den letzten 25 m fällt sie auf den Meeresspiegel
        h *= smooth(0, 25, sd);
        return h;
    }

    /** Meeresboden: flacher Schelf, dann der steile Hang der Insel bis rund 5 000 m Tiefe [G, nach NOAA-Karten genähert]. */
    static double seaFloor(double x, double z, double d) {
        double depth = 4 + 0.10 * d + 4.0e-6 * d * d;
        depth = Math.min(5200, depth);
        // Unruhe am Hang (Rinnen, Schuttfächer), mit der Tiefe stärker
        depth += Math.min(depth * 0.12, 260) * (Noise.fbm(x * 0.00012, z * 0.00012, 4) - 0.5);
        return -Math.max(1.5, depth);
    }

    /** Land ohne Küste: Schilde, Kīlauea, Pali, Senken, Kegel, Feinrelief. */
    static double land(double x, double z) {
        double h;
        // Schilde weich vereinigt: paarweises weiches Maximum über 400 m, nur wo ein Schild überhaupt Höhe hat
        h = Double.NEGATIVE_INFINITY;
        for (double[] s : SH) {
            double dx = x - s[0], dz = z - s[1];
            double along = dx * s[5] + dz * s[6], across = -dx * s[6] + dz * s[5];
            double r = Math.sqrt(along * along / (s[3] * s[3]) + across * across / (s[4] * s[4]));
            if (r < 1) h = merge(h, s[2] * Math.pow(1 - r, s[7]));
        }
        double kil = kilauea(x, z);
        if (kil > 0) h = merge(h, kil);
        if (h == Double.NEGATIVE_INFINITY) h = 0;
        // Steilstufen der Südflanke
        for (int p = 0; p < PL.length; p++) {
            double[] sp = segDist(PL[p], x, z);
            double side = sp[1];                                  // > 0: südlich (seewärts) der Linie
            double env = smooth(-6000, -1500, -Math.abs(sp[2]));  // nur entlang der Linie
            double w = 120;                                       // halbe Breite der Stufe
            // seewärts der Linie sackt die Flanke ab (die Stufe hebt nichts, sie senkt nur)
            h -= PALI_DROP[p] * smooth(-w, w, side) * env;
        }
        // Senken (Caldera, Krater)
        for (double[] p : PT) {
            double dx = x - p[0], dz = z - p[1];
            double u = (dx * p[4] + dz * p[5]) / p[2], v = (-dx * p[5] + dz * p[4]) / p[3];
            double r = Math.sqrt(u * u + v * v);
            if (r < 1.15) {
                double edge = 1 - p[7] * 0.9;
                double inner = smooth(1.0, 1.0 - edge * 0.5 - 0.02, r);
                h -= p[6] * inner;
                // leichter Wall am Rand
                h += p[6] * 0.04 * Math.exp(-Math.pow((r - 1.04) / 0.05, 2));
            }
        }
        // Kegel mit Krater
        for (double[] c : CN) {
            double r = Math.hypot(x - c[0], z - c[1]);
            if (r < c[2]) {
                double t = 1 - r / c[2];
                h += c[3] * t * t * (3 - 2 * t);
                if (c[4] > 0 && r < c[4] * 1.1) h -= c[5] * smooth(c[4] * 1.05, c[4] * 0.55, r);
            }
        }
        // Feinrelief: Ströme, Rinnen, Schlackenkegel (stärker in größerer Höhe und auf den alten Schilden)
        double rough = 6 + 0.012 * Math.max(0, h);
        h += rough * (Noise.fbm(x * 0.0011, z * 0.0011, 5) - 0.5) * 2;
        h += 2.5 * (Noise.fbm(x * 0.006 + 31, z * 0.006 - 17, 3) - 0.5) * 2;
        return h;
    }

    /** Kīlauea: Kamm entlang der Riftzonen, nach Norden flach in den Mauna Loa, nach Süden zur Küste. */
    static double kilauea(double x, double z) {
        double best = -1e9;
        for (int rz = 0; rz < 2; rz++) {
            double[] px = rz == 0 ? EX : SX, pz = rz == 0 ? EZ : SZ;
            double[][] src = rz == 0 ? ERZ : SWRZ;
            // nächster Punkt auf dem Kamm, dort die Kammhöhe; quer dazu fällt die Flanke ab
            double bd = Double.MAX_VALUE, crest = 0, side = 0;
            for (int i = 0; i + 1 < px.length; i++) {
                double ax = px[i], az = pz[i], vx = px[i + 1] - ax, vz = pz[i + 1] - az;
                double len2 = vx * vx + vz * vz;
                double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
                double dx = x - (ax + vx * t), dz = z - (az + vz * t);
                double d = dx * dx + dz * dz;
                if (d < bd) { bd = d; crest = src[i][2] + (src[i + 1][2] - src[i][2]) * t; side = dz; }
            }
            double d = Math.sqrt(bd);
            // Nordseite flacher (lehnt am Mauna Loa), Südseite steiler zum Meer
            double w = side > 0 ? 11000 : 16000;
            double v = crest * Math.exp(-Math.pow(d / w, 1.6));
            if (v > best) best = v;
        }
        // Gipfelbuckel: der Schild wölbt sich um die Caldera
        double rs = Math.hypot(x, z);
        best += 120 * Math.exp(-Math.pow(rs / 6000, 2));
        return best;
    }

    /** Abstand zu einem Linienzug: {Abstand, Seite (positiv rechts in Laufrichtung = Süden bei West→Ost), Lauf über das Ende hinaus (negativ innen)}. */
    static double[] segDist(double[][] line, double x, double z) {
        double best = Double.MAX_VALUE, side = 0, over = 0;
        for (int i = 0; i + 1 < line.length; i++) {
            double ax = line[i][0], az = line[i][1], vx = line[i + 1][0] - ax, vz = line[i + 1][1] - az;
            double len = Math.sqrt(vx * vx + vz * vz);
            double tRaw = ((x - ax) * vx + (z - az) * vz) / (len * len);
            double t = Math.max(0, Math.min(1, tRaw));
            double dx = x - (ax + vx * t), dz = z - (az + vz * t);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < best) {
                best = d;
                side = ((x - ax) * (-vz) + (z - az) * vx) / len;   // Kreuzprodukt: Süden (+z) rechts von Ost
                double o = 0;
                if (i == 0 && tRaw < 0) o = -tRaw * len;
                if (i + 2 == line.length && tRaw > 1) o = (tRaw - 1) * len;
                over = -o;
            }
        }
        return new double[]{best, side, over};
    }

    // ------------------------------------------------------------------ Bodenbedeckung

    /**
     * Bodenart als Gewichte: frische Lava (jünger als rund 60 Jahre), Bewuchs, Asche und Hochwüste,
     * Strand und Brandungszone. Die Flächen junger Ströme sind grob nach Karten der Ausbrüche 1969–2018
     * gezeichnet [G]; Phase 3 ersetzt sie durch die geologische Karte des USGS.
     */
    public static float[] cover(double x, double z, double h, double sd, float[] out) {
        double fresh = 0;
        for (double[][] poly : FRESH) fresh = Math.max(fresh, polySoft(poly, x, z, 350));
        // Kraterböden und Caldera sind jung (Ströme 1971–1982, Einsturz 2018, Lavasee seit 2020)
        double rs = Math.hypot(x, z);
        fresh = Math.max(fresh, smooth(1400, 700, rs));
        double[] cal = PT[0];
        double cu = ((x - cal[0]) * cal[4] + (z - cal[1]) * cal[5]) / cal[2], cv = (-(x - cal[0]) * cal[5] + (z - cal[1]) * cal[4]) / cal[3];
        fresh = Math.max(fresh, 0.75 * smooth(1.0, 0.8, Math.sqrt(cu * cu + cv * cv)));
        double rKi = Math.hypot(x - CN[3][0] - 400, z - CN[3][1] + 200);
        fresh = Math.max(fresh, 0.8 * smooth(900, 500, rKi));
        // Mauna Loa: Ströme 1984 und 2022 nach Nordosten (als Band)
        double[] ml = segDist(ML_FLOW, x, z);
        fresh = Math.max(fresh, 0.7 * smooth(1100, 350, ml[0] + 900 * (Noise.fbm(x * 0.0004, z * 0.0004, 3) - 0.5) * 2) * smooth(-500, 0, ml[2]));
        fresh += 0.18 * (Noise.fbm(x * 0.0009 + 7, z * 0.0009, 3) - 0.5) * smooth(0.0, 0.3, fresh);
        fresh = clamp(fresh);

        // Regen: Passat aus Ostnordost, Wolken stauen sich zwischen 300 und 2 000 m; Regenschatten im Südwesten des Gipfels
        double windward = smooth(-25000, 20000, x * 0.93 - z * 0.37);
        double band = smooth(-50, 350, h) * smooth(2700, 1600, h);
        double rain = windward * band;
        double kau = (1 - smooth(0.55, 1.25, Math.hypot((x - KAU[0]) / 11000, (z - KAU[1]) / 7000) + 0.45 * (Noise.fbm(x * 0.0003 + 2, z * 0.0003, 4) - 0.5) * 2)) * 0.85;
        double desert = Math.max(kau, smooth(2600, 3300, h));
        double dry = smooth(0, 1, 1 - windward) * 0.6;
        double veg = clamp(rain * (1 - desert) - dry * 0.3 + 0.25 * smooth(0, 600, h) * (1 - desert) * windward);
        veg *= 1 - fresh;
        // Gipfel: Schwefelgase und Asche lassen um die Caldera nur Gras und Büsche wachsen
        veg *= 0.35 + 0.65 * smooth(2500, 7000, Math.hypot(x - 1500, z));
        veg *= 0.75 + 0.5 * Noise.fbm(x * 0.0007, z * 0.0007, 4);
        double ash = clamp(desert * (1 - fresh));
        double shore = smooth(180, 20, sd) * smooth(-3, 2, h);
        out[0] = (float) fresh;
        out[1] = (float) clamp(veg);
        out[2] = (float) ash;
        out[3] = (float) clamp(shore);
        return out;
    }

    private static final double[] KAU = {Geo.x(19.3650, -155.3700), Geo.z(19.3650)};
    private static final double[][] ML_FLOW = {
            {Geo.x(19.4800, -155.5700), Geo.z(19.4800)}, {Geo.x(19.5500, -155.5000), Geo.z(19.5500)}, {Geo.x(19.6300, -155.4300), Geo.z(19.6300)}};
    /** Felder junger Lava (Breite, Länge): Puʻu ʻŌʻō 1983–2018, Mauna Ulu 1969–74, Kapoho 2018, Kalapana 1990. */
    private static final double[][][] FRESH = toXZ(new double[][][]{
            {{19.398, -155.112}, {19.402, -155.060}, {19.385, -155.010}, {19.360, -154.975}, {19.345, -154.968}, {19.322, -155.030},
                    {19.312, -155.050}, {19.291, -155.093}, {19.300, -155.120}, {19.340, -155.120}, {19.380, -155.125}},
            {{19.380, -155.215}, {19.372, -155.180}, {19.320, -155.130}, {19.290, -155.100}, {19.282, -155.150}, {19.300, -155.190}, {19.340, -155.215}},
            {{19.470, -154.925}, {19.488, -154.880}, {19.528, -154.815}, {19.505, -154.805}, {19.470, -154.828}, {19.452, -154.880}, {19.455, -154.915}}});

    private static double[][][] toXZ(double[][][] polys) {
        double[][][] r = new double[polys.length][][];
        for (int k = 0; k < polys.length; k++) {
            r[k] = new double[polys[k].length][];
            for (int i = 0; i < polys[k].length; i++) r[k][i] = new double[]{Geo.x(polys[k][i][0], polys[k][i][1]), Geo.z(polys[k][i][0])};
        }
        return r;
    }

    /** 1 tief im Vieleck, 0 außen, weicher Rand der Breite w. */
    static double polySoft(double[][] poly, double x, double z, double w) {
        boolean inside = false;
        double best = Double.MAX_VALUE;
        int n = poly.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = poly[j][0], az = poly[j][1], bx = poly[i][0], bz = poly[i][1];
            if ((bz > z) != (az > z) && x < (ax - bx) * (z - bz) / (az - bz) + bx) inside = !inside;
            double vx = bx - ax, vz = bz - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            double dx = x - (ax + vx * t), dz = z - (az + vz * t);
            best = Math.min(best, dx * dx + dz * dz);
        }
        double d = Math.sqrt(best) * (inside ? 1 : -1);
        // Ränder ausfransen (Ströme sind lappig, nicht glatt)
        d += w * 1.6 * (Noise.fbm(x * 0.0016, z * 0.0016, 3) - 0.5);
        return smooth(-w, w, d);
    }

    private static double merge(double h, double v) { return h == Double.NEGATIVE_INFINITY ? v : smax(h, v, 400); }

    /** Weiches Maximum: wie max(a, b), an der Naht über die Breite k gerundet (hebt dort höchstens um k/4). */
    static double smax(double a, double b, double k) {
        double hh = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.max(a, b) + hh * hh * k * 0.25;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
