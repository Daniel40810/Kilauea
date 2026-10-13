package com.dan.kilauea.terrain;

/**
 * Die Lavaröhre Nāhuku (Thurston Lava Tube): eine Röhre von rund 180 m, in der ʻAilāʻau-Lava vor rund
 * 500 Jahren floss, mit einer Grube am Eingang und am Ausgang [L Länge, Lage; G Form und Tiefe].
 * <p>
 * Die Achse läuft als Catmull-Rom-Linie in 1-m-Schritten durch {@link SummitPaths#NAHUKU_TUBE}; der
 * Boden liegt 7 bis 11 m unter dem Gelände und geht an den Enden in den Boden der Gruben über.
 * Querschnitt: rund 3,2 m Halbmesser, der Boden flach, die Decke gewölbt (gezeichnet von
 * {@code render/TubeRenderer}). Die Kamera fragt hier, wo man gehen kann.
 */
public final class LavaTube {
    public final int n;
    public final double[] x, z, floor, tx, tz, radius;
    public final double length;
    /** Ab welchem Abstand (m) vom Anfang bzw. bis wohin die Röhre eine Decke hat (davor offene Grube). */
    public final double roofFrom, roofTo;

    private static volatile LavaTube tube;

    public static LavaTube get() { return tube; }

    public static void use(LavaTube t) { tube = t; }

    private LavaTube(double[] x, double[] z, double[] floor, double[] radius, double roofFrom, double roofTo) {
        this.n = x.length;
        this.x = x; this.z = z; this.floor = floor; this.radius = radius;
        this.tx = new double[n];
        this.tz = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dx = x[b] - x[a], dz = z[b] - z[a], l = Math.hypot(dx, dz) + 1e-9;
            tx[i] = dx / l; tz[i] = dz / l;
        }
        this.length = n - 1;
        this.roofFrom = roofFrom;
        this.roofTo = roofTo;
    }

    /** Baut die Röhre auf dem Gelände (nach {@link Ground#use} und der Gipfelkachel). */
    public static LavaTube build() {
        double[][] c = SummitPaths.NAHUKU_TUBE;
        int m = c.length;
        double[][] p = new double[m][];
        for (int i = 0; i < m; i++) p[i] = new double[]{Geo.x(c[i][0], c[i][1]), Geo.z(c[i][0]), c[i][2]};
        java.util.List<double[]> dense = new java.util.ArrayList<>();
        for (int k = 0; k < m - 1; k++) {
            double[] p0 = p[Math.max(0, k - 1)], p1 = p[k], p2 = p[k + 1], p3 = p[Math.min(m - 1, k + 2)];
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int st = Math.max(2, (int) Math.ceil(len / 0.25));
            for (int s = 0; s < st; s++) {
                double t = s / (double) st;
                dense.add(new double[]{cat(p0[0], p1[0], p2[0], p3[0], t), cat(p0[1], p1[1], p2[1], p3[1], t), p1[2] + (p2[2] - p1[2]) * t});
            }
        }
        dense.add(p[m - 1]);
        // gleichmäßig in 1-m-Schritten
        double[] acc = new double[dense.size()];
        for (int i = 1; i < acc.length; i++) acc[i] = acc[i - 1] + Math.hypot(dense.get(i)[0] - dense.get(i - 1)[0], dense.get(i)[1] - dense.get(i - 1)[1]);
        int n = (int) Math.floor(acc[acc.length - 1]) + 1;
        double[] x = new double[n], z = new double[n], depth = new double[n], floor = new double[n], rad = new double[n];
        int j = 0;
        for (int i = 0; i < n; i++) {
            double s = i;
            while (j + 2 < acc.length && acc[j + 1] < s) j++;
            double f = Math.max(0, Math.min(1, (s - acc[j]) / Math.max(1e-9, acc[j + 1] - acc[j])));
            x[i] = dense.get(j)[0] + (dense.get(j + 1)[0] - dense.get(j)[0]) * f;
            z[i] = dense.get(j)[1] + (dense.get(j + 1)[1] - dense.get(j)[1]) * f;
            depth[i] = dense.get(j)[2] + (dense.get(j + 1)[2] - dense.get(j)[2]) * f;
        }
        // Boden: unter dem Gelände ohne Gruben, an den Enden auf dem Boden der Grube; dann geglättet
        for (int i = 0; i < n; i++) {
            double top = Ground.baseHeight(x[i], z[i]) - SummitTile.pits(x[i], z[i]);   // ohne Grube
            double withPit = Ground.baseHeight(x[i], z[i]);
            floor[i] = Math.min(withPit, top - depth[i]);
            // Höhe der Röhre schwankt: weite Hallen und enge Stellen
            rad[i] = 3.0 + 0.6 * Math.sin(i * 0.07) + 0.4 * Noise.value(i * 0.05, 3.3);
        }
        double[] sm = floor.clone();
        for (int pass = 0; pass < 4; pass++)
            for (int i = 1; i < n - 1; i++) sm[i] = (floor[i - 1] + 2 * floor[i] + floor[i + 1]) / 4;
        // Decke beginnt, wo die Achse die Wand der Grube erreicht
        double[] pit0 = SummitPaths.NAHUKU_PITS[0], pit1 = SummitPaths.NAHUKU_PITS[1];
        double p0x = Geo.x(pit0[0], pit0[1]), p0z = Geo.z(pit0[0]), p1x = Geo.x(pit1[0], pit1[1]), p1z = Geo.z(pit1[0]);
        double from = 0, to = n - 1;
        for (int i = 0; i < n; i++) if (Math.hypot(x[i] - p0x, z[i] - p0z) > pit0[2] * 0.62) { from = i; break; }
        for (int i = n - 1; i >= 0; i--) if (Math.hypot(x[i] - p1x, z[i] - p1z) > pit1[2] * 0.62) { to = i; break; }
        return new LavaTube(x, z, sm, rad, from, to);
    }

    private static double cat(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Nächste Stelle der Achse zu (x, z): {Bogenlänge s, Querabstand (rechts positiv), Abstand}. */
    public double[] locate(double px, double pz) {
        double best = Double.MAX_VALUE, bs = 0, bu = 0;
        for (int i = 0; i < n - 1; i++) {
            double ax = x[i], az = z[i], vx = x[i + 1] - ax, vz = z[i + 1] - az;
            double l2 = vx * vx + vz * vz;
            double t = Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / l2));
            double qx = ax + vx * t, qz = az + vz * t;
            double d = Math.hypot(px - qx, pz - qz);
            if (d < best) {
                best = d;
                bs = i + t;
                bu = ((px - qx) * -vz + (pz - qz) * vx) / Math.sqrt(l2);
            }
        }
        return new double[]{bs, bu, best};
    }

    /** Boden an der Bogenlänge s (linear). */
    public double floorAt(double s) {
        s = Math.max(0, Math.min(n - 1, s));
        int i = Math.min(n - 2, (int) s);
        double f = s - i;
        return floor[i] + (floor[i + 1] - floor[i]) * f;
    }

    public double radiusAt(double s) {
        s = Math.max(0, Math.min(n - 1, s));
        int i = Math.min(n - 2, (int) s);
        double f = s - i;
        return radius[i] + (radius[i + 1] - radius[i]) * f;
    }

    /**
     * Wo man an (x, z) steht, wenn die Füße auf Höhe footY sind: in der Röhre ihr Boden, sonst NaN.
     * In der Röhre ist man, wenn die Füße weit unter dem Gelände sind und man nahe der Achse steht.
     */
    public double walkFloor(double px, double pz, double footY) {
        double[] q = locate(px, pz);
        if (q[2] > radiusAt(q[0]) * 0.92) return Double.NaN;
        double f = floorAt(q[0]);
        return Math.abs(footY - f) < 2.5 ? f : Double.NaN;
    }

    /** Ob (x, z) mit Füßen auf footY in der Röhre unter der Decke liegt (dann gelten die Wände). */
    public boolean underRoof(double px, double pz, double footY) {
        double[] q = locate(px, pz);
        return q[0] > roofFrom - 1 && q[0] < roofTo + 1 && q[2] < radiusAt(q[0]) + 2 && footY < Ground.height(px, pz) - 2.5;
    }
}
