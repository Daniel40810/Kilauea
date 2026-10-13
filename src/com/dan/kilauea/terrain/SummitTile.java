package com.dan.kilauea.terrain;

/**
 * Die Gipfelkachel (Phase 4): 4 m über 12 × 8 km um Kaluapele, Kīlauea Iki und Nāhuku. Seit Phase 5
 * eine von mehreren {@link DetailTile Detailkacheln}; hier stehen ihre Lage und ihre eigenen Formen.
 * <ul>
 * <li><b>Scharfe Wände</b>: Uēkahuna-Klippe, Ringstufen von 2018, Halemaʻumaʻu, Kīlauea Iki und
 * Keanakākoʻi (Schärfen im Umkreis von 80 m) [A]. Entlang der Wege und Straßen wird nicht geschärft.</li>
 * <li><b>Dampfspalten</b> am Steaming Bluff als Risse von einigen Metern [G nach Fotos].</li>
 * <li><b>Gruben</b> am Eingang und Ausgang von Nāhuku [G].</li>
 * <li><b>Grubenkrater</b> Lua Manu, Puhimau und Koʻokoʻolau am oberen Ende der Kette [Lage aus den
 * Höhendaten bzw. G, Tiefe von Puhimau L].</li>
 * </ul>
 */
public final class SummitTile extends DetailTile.Spec {
    public static final double X0 = -5000, Z0 = -5000, CELL = 4;
    public static final int NX = 3000, NZ = 2000;

    private final double[] rims;

    public SummitTile(HeightGrid g) {
        super("gipfel", "Gipfel", X0, Z0, NX, NZ, CELL, 4,
                new DetailTile.Sharpen(20, 10, 20, 50, 0.12, 0.3, 0.40, 0.60, 0.15, 0.06, 1.0), false);
        rims = new double[DetailTiles.CHAIN_PITS.length];
        for (int i = 0; i < rims.length; i++) rims[i] = g == null ? 0 : DetailTile.rim(g, DetailTiles.CHAIN_PITS[i]);
    }

    /** Die oberen Grubenkrater der Kette (Lua Manu, Puhimau, Koʻokoʻolau) liegen in dieser Kachel. */
    @Override public double pre(double x, double z, double b) {
        double h = b;
        for (int i = 0; i < rims.length; i++) if (inside(DetailTiles.CHAIN_PITS[i].x(), DetailTiles.CHAIN_PITS[i].z())) h = DetailTile.carve(DetailTiles.CHAIN_PITS[i], x, z, h, rims[i]);
        return h;
    }

    @Override public double post(double x, double z, double h, double base) { return h + cracks(x, z) + pits(x, z); }

    @Override public float[] exempt() {
        java.util.List<double[][]> lines = new java.util.ArrayList<>();
        for (SummitPaths.Trail t : SummitPaths.TRAILS) lines.add(t.xz());
        lines.add(DetailTile.xz(ParkRoads.CRATER_RIM));
        lines.add(DetailTile.xz(ParkRoads.CHAIN_OF_CRATERS));
        return exemptMask(this, lines, 10, 30);
    }

    /** 1 bis inner m neben einer Linie, bis outer m auf 0. */
    static float[] exemptMask(DetailTile.Spec s, java.util.List<double[][]> lines, double inner, double outer) {
        int nx = s.nx, nz = s.nz;
        float[] m = new float[nx * nz];
        int reach = (int) Math.ceil(outer / s.cell);
        for (double[][] l : lines)
            for (int k = 1; k < l.length; k++) {
                double ax = l[k - 1][0], az = l[k - 1][1], bx = l[k][0], bz = l[k][1];
                double len = Math.hypot(bx - ax, bz - az);
                for (double t = 0; t <= len; t += Math.max(1, s.cell * 0.5)) {
                    double px = ax + (bx - ax) * t / Math.max(len, 1e-9), pz = az + (bz - az) * t / Math.max(len, 1e-9);
                    int ci = (int) Math.round((px - s.x0) / s.cell), cj = (int) Math.round((pz - s.z0) / s.cell);
                    if (ci < -reach || cj < -reach || ci > nx + reach || cj > nz + reach) continue;
                    for (int j = cj - reach; j <= cj + reach; j++)
                        for (int i = ci - reach; i <= ci + reach; i++) {
                            if (i < 0 || j < 0 || i >= nx || j >= nz) continue;
                            double dd = Math.hypot(s.x0 + i * s.cell - px, s.z0 + j * s.cell - pz);
                            float v = (float) Island.smooth(outer, inner, dd);
                            if (v > m[j * nx + i]) m[j * nx + i] = v;
                        }
                }
            }
        return m;
    }

    private static final double[][][] CRACKS_XZ;
    private static final double[][] PITS_XZ;
    static {
        CRACKS_XZ = new double[SummitPaths.STEAM_CRACKS.length][][];
        for (int c = 0; c < CRACKS_XZ.length; c++) CRACKS_XZ[c] = DetailTile.xz(SummitPaths.STEAM_CRACKS[c]);
        PITS_XZ = new double[SummitPaths.NAHUKU_PITS.length][];
        for (int i = 0; i < PITS_XZ.length; i++) {
            double[] p = SummitPaths.NAHUKU_PITS[i];
            PITS_XZ[i] = new double[]{Geo.x(p[0], p[1]), Geo.z(p[0]), p[2], p[3]};
        }
    }

    /** Risse: bis 3 m tief, 2 bis 5 m breit, mit Lücken. */
    static double cracks(double x, double z) {
        double best = 0;
        for (double[][] l : CRACKS_XZ) {
            double[] sd = Island.segDist(l, x, z);
            if (sd[0] > 8) continue;
            double width = 1.2 + 1.3 * Noise.value(x * 0.02, z * 0.02);
            double gap = Island.smooth(0.25, 0.45, Noise.value(x * 0.006 + 3, z * 0.006));
            best = Math.min(best, -3.0 * Island.smooth(width, width * 0.4, sd[0]) * gap);
        }
        return best;
    }

    /** Gruben am Eingang und Ausgang von Nāhuku: steile, farnbewachsene Wände, flacher Boden. */
    static double pits(double x, double z) {
        double v = 0;
        for (double[] p : PITS_XZ) {
            double d = Math.hypot(x - p[0], z - p[1]);
            if (d < p[2] * 1.4) v = Math.min(v, -p[3] * Island.smooth(p[2], p[2] * 0.55, d));
        }
        return v;
    }
}
