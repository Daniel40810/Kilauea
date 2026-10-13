package com.dan.kilauea.terrain;

import com.dan.road.Network;
import com.dan.road.Way;
import com.dan.road.WayType;

import java.util.List;

/**
 * Straßen im Nationalpark: Crater Rim Drive (offener Teil von Uēkahuna über das Besucherzentrum bis
 * Keanakākoʻi) und die Chain of Craters Road vom Gipfel an den Grubenkratern vorbei, über die Ströme
 * von Mauna Ulu und die Hōlei Pali hinab bis an die Küste beim Felsbogen [L Verlauf nach NPS-Karte,
 * Punkte auf rund 100–300 m genähert]. Achsen und Querschnitt aus {@code com.dan.road}: Catmull-Rom
 * durch die Stützpunkte, zwei Fahrstreifen zu 3 m mit gelber Mittellinie (amerikanisch), schmales
 * Bankett aus Lavasplitt.
 * <p>
 * Die Fahrbahn liegt auf dem Gelände (die Straße ist auf die Ströme gelegt, kaum eingeschnitten); das
 * Feinrelief wird in einem Band um die Straße ausgeschaltet ({@link #mask}), damit die Fahrbahn eben ist.
 */
public final class ParkRoads {
    private ParkRoads() { }

    /** Stützpunkte (Breite, Länge). */
    public static final double[][] CRATER_RIM = {
            {19.4206, -155.2878}, {19.4250, -155.2810}, {19.4290, -155.2730}, {19.4313, -155.2655}, {19.4300, -155.2590},
            {19.4285, -155.2550}, {19.4245, -155.2510}, {19.4205, -155.2500}, {19.4180, -155.2430}, {19.4152, -155.2385},
            {19.4105, -155.2430}, {19.4100, -155.2520}, {19.4060, -155.2575}, {19.4020, -155.2605}, {19.3985, -155.2635}, {19.3965, -155.2660}};
    public static final double[][] CHAIN_OF_CRATERS = {
            {19.4020, -155.2605}, {19.3955, -155.2560}, {19.3905, -155.2510}, {19.3885, -155.2460}, {19.3860, -155.2400},
            {19.3840, -155.2360}, {19.3812, -155.2318}, {19.3790, -155.2240}, {19.3770, -155.2150}, {19.3740, -155.2090},
            {19.3680, -155.2010}, {19.3600, -155.1950}, {19.3520, -155.1880}, {19.3440, -155.1820}, {19.3370, -155.1760},
            {19.3290, -155.1660}, {19.3220, -155.1560}, {19.3170, -155.1480}, {19.3125, -155.1435}, {19.3090, -155.1495},
            {19.3055, -155.1445}, {19.3015, -155.1370}, {19.2985, -155.1280}, {19.2975, -155.1150}, {19.2965, -155.1030},
            {19.2970, -155.0950}};

    private static volatile Network net;

    public static Network network() { return net; }

    /** Querschnitt der Parkstraßen. */
    public static WayType type() {
        WayType t = WayType.road();
        t.name = "Parkstraße";
        t.lanes = 1;
        t.laneWidth = 3.0f;
        t.shoulder = 0.25f;
        t.verge = 1.2f;
        t.marking = WayType.Marking.US;
        t.maxGrade = 0.25f;
        t.smoothing = 6;
        t.guardrail = false;
        t.snowPoles = false;
        t.age = 0.7f;
        t.surface = new float[]{0.045f, 0.044f, 0.043f};
        t.worn = new float[]{0.075f, 0.072f, 0.068f};
        t.vergeColor = new float[]{0.06f, 0.055f, 0.05f};
        return t;
    }

    /** Baut die Achsen auf dem Raster g und legt die Maske in g ab. */
    public static Network build(HeightGrid g) {
        Network n = new Network();
        for (double[][] route : List.of(CRATER_RIM, CHAIN_OF_CRATERS)) {
            double[] xz = new double[route.length * 2];
            for (int i = 0; i < route.length; i++) { xz[2 * i] = Geo.x(route[i][0], route[i][1]); xz[2 * i + 1] = Geo.z(route[i][0]); }
            n.add(new Way(type(), false, xz));
        }
        n.build((x, z) -> (float) g.height(x, z));
        g.road = mask(n);
        net = n;
        return n;
    }

    private static volatile Network dirt, paved;

    /** Wege am Gipfel: 0 Pfade (Erde, Splitt), 1 befestigte Wege. */
    public static Network trails(int kind) { return kind == 0 ? dirt : paved; }

    /** Baut die Wege aus {@link SummitPaths} (nach der Gipfelkachel, damit die Achsen ihre Höhen kennen). */
    public static void buildTrails() {
        Network d = new Network(), p = new Network();
        for (SummitPaths.Trail t : SummitPaths.TRAILS) {
            double[][] l = t.xz();
            double[] xz = new double[l.length * 2];
            for (int i = 0; i < l.length; i++) { xz[2 * i] = l[i][0]; xz[2 * i + 1] = l[i][1]; }
            WayType w = WayType.path();
            w.laneWidth = t.kind() == 1 ? 1.8f : 1.1f;
            w.verge = t.kind() == 1 ? 0.3f : 0.4f;
            w.marking = WayType.Marking.NONE;
            w.maxGrade = 0.6f;
            w.smoothing = 3;
            w.surface = t.kind() == 1 ? new float[]{0.050f, 0.048f, 0.046f} : new float[]{0.085f, 0.072f, 0.058f};
            w.worn = t.kind() == 1 ? new float[]{0.075f, 0.072f, 0.068f} : new float[]{0.11f, 0.095f, 0.075f};
            w.vergeColor = new float[]{0.07f, 0.065f, 0.055f};
            (t.kind() == 1 ? p : d).add(new Way(w, false, xz));
        }
        d.build((x, z) -> (float) Ground.height(x, z));
        p.build((x, z) -> (float) Ground.height(x, z));
        dirt = d;
        paved = p;
    }

    /** 1 bis 40 m neben der Achse, dann bis 110 m auf 0 (je Zelle des Rasters). */
    static byte[] mask(Network n) {
        int N = HeightGrid.N;
        byte[] m = new byte[N * N];
        for (Way w : n.ways)
            for (int i = 0; i < w.n; i += 4) {
                double x = w.x[i], z = w.z[i];
                int ci = (int) Math.round((x - HeightGrid.X0) / HeightGrid.CELL), cj = (int) Math.round((z - HeightGrid.Z0) / HeightGrid.CELL);
                for (int j = cj - 2; j <= cj + 2; j++)
                    for (int k = ci - 2; k <= ci + 2; k++) {
                        if (k < 0 || j < 0 || k >= N || j >= N) continue;
                        double d = Math.hypot(HeightGrid.X0 + k * HeightGrid.CELL - x, HeightGrid.Z0 + j * HeightGrid.CELL - z);
                        double v = Island.smooth(110, 40, d);
                        int b = (int) Math.round(v * 255);
                        if (b > (m[j * N + k] & 255)) m[j * N + k] = (byte) b;
                    }
            }
        return m;
    }

    /** Abstand zur nächsten Straßenachse (m), oder ein großer Wert. */
    public static double distance(double x, double z) {
        Network n = net;
        if (n == null) return 1e9;
        Network.Hit h = new Network.Hit();
        return n.locate(x, z, h) ? Math.abs(h.u) : 1e9;
    }
}
