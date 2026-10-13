package com.dan.kilauea.life;

import java.util.Arrays;

/**
 * Die Figuren der Szene als kleine, gerechnete Netze (keine Dateien). Maße nach Quellen, wo es sie gibt,
 * sonst gestaltet:
 * <ul>
 * <li>Nēnē (Branta sandvicensis): rund 41 cm hoch, schwarzer Kopf und Hinterhals, gelbbraune Wangen,
 * gefurchter Hals mit hellen und dunklen Streifen, schwarzer Schnabel und schwarze Beine [L]; Spannweite
 * rund 1,1 m [G].</li>
 * <li>ʻApapane: karminrot, Flügel und Schwanz schwarz, Bauch und Unterschwanz weiß, gebogener Schnabel [L NPS];
 * Hawaiʻi-ʻAmakihi gelbgrün, ʻŌmaʻo graubraun, Japanbrillenvogel olivgrün mit weißem Augenring [Farben G].</li>
 * <li>Koaʻe kea (Weißschwanz-Tropikvogel): weiß, schwarze Augenmaske, schwarzes Band am inneren Flügel,
 * oranger Schnabel, Schwanzfedern bis rund 40 cm [L NPS]; Form [G]. ʻIo (Hawaiibussard) als dunkler Greif
 * mit rund 1 m Spannweite [G].</li>
 * <li>Hubschrauber in der Klasse der einmotorigen Rundflug-Maschinen (Rotor rund 10,7 m, Länge rund 10 m,
 * drei Blätter) [Größenordnung L, Form G].</li>
 * <li>Wanderer 1,75 m mit Rucksack; Autos 4,8–5,5 m [G].</li>
 * </ul>
 */
public final class Shapes {
    private Shapes() { }

    // ------------------------------------------------------------------ Farben (linear)
    static final float[] BLACK = {0.012f, 0.012f, 0.013f}, DARK = {0.03f, 0.03f, 0.032f}, GLASS = {0.018f, 0.022f, 0.028f},
            WHITE = {0.62f, 0.62f, 0.60f}, RUBBER = {0.015f, 0.015f, 0.016f}, STEEL = {0.20f, 0.20f, 0.21f};

    interface Tint { float[] at(double nx, double ny, double nz, double t); }

    // ------------------------------------------------------------------ Baukasten
    static final class B {
        private float[] p = new float[3 * 512], n = new float[3 * 512], c = new float[3 * 512], pv = new float[3 * 512];
        private int[] pa = new int[512], t = new int[3 * 1024];
        private int nv, nt;
        int part;
        float px, py, pz;

        B part(int part, double x, double y, double z) { this.part = part; px = (float) x; py = (float) y; pz = (float) z; return this; }
        B body() { return part(LifeMesh.BODY, 0, 0, 0); }

        int v(double x, double y, double z, double nx, double ny, double nz, float[] col) {
            if (nv * 3 + 3 > p.length) {
                p = Arrays.copyOf(p, p.length * 2); n = Arrays.copyOf(n, n.length * 2);
                c = Arrays.copyOf(c, c.length * 2); pv = Arrays.copyOf(pv, pv.length * 2); pa = Arrays.copyOf(pa, pa.length * 2);
            }
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9) { nx = 0; ny = 1; nz = 0; l = 1; }
            int i = nv++;
            p[3 * i] = (float) x; p[3 * i + 1] = (float) y; p[3 * i + 2] = (float) z;
            n[3 * i] = (float) (nx / l); n[3 * i + 1] = (float) (ny / l); n[3 * i + 2] = (float) (nz / l);
            c[3 * i] = col[0]; c[3 * i + 1] = col[1]; c[3 * i + 2] = col[2];
            pv[3 * i] = px; pv[3 * i + 1] = py; pv[3 * i + 2] = pz;
            pa[i] = part;
            return i;
        }

        void tri(int a, int b, int d) {
            if (nt * 3 + 3 > t.length) t = Arrays.copyOf(t, t.length * 2);
            t[3 * nt] = a; t[3 * nt + 1] = b; t[3 * nt + 2] = d;
            nt++;
        }

        void quad(int a, int b, int d, int e) { tri(a, b, d); tri(a, d, e); }

        /** Ellipsoid, Farbe nach der Normale (Tint) oder einfarbig. */
        void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, int seg, int rings, Tint tint) {
            int base = nv;
            for (int r = 0; r <= rings; r++) {
                double th = Math.PI * r / rings, st = Math.sin(th), ct = Math.cos(th);
                for (int s = 0; s <= seg; s++) {
                    double ph = 2 * Math.PI * s / seg, sp = Math.sin(ph), cp = Math.cos(ph);
                    double ux = st * cp, uy = ct, uz = st * sp;
                    v(cx + rx * ux, cy + ry * uy, cz + rz * uz, ux / rx, uy / ry, uz / rz, tint.at(ux, uy, uz, r / (double) rings));
                }
            }
            for (int r = 0; r < rings; r++)
                for (int s = 0; s < seg; s++) {
                    int a = base + r * (seg + 1) + s, b = a + seg + 1;
                    quad(a, a + 1, b + 1, b);
                }
        }

        void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, int seg, int rings, float[] col) {
            ellipsoid(cx, cy, cz, rx, ry, rz, seg, rings, (a, b, d, e) -> col);
        }

        /** Rohr von A nach B mit Halbmesser ra → rb; Farbe je Ring (t von 0 bis 1). */
        void tube(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, int seg, int rings, Tint tint, boolean caps) {
            double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len; dy /= len; dz /= len;
            double[] u = Math.abs(dy) < 0.9 ? cross(dx, dy, dz, 0, 1, 0) : cross(dx, dy, dz, 1, 0, 0);
            double[] w = cross(u[0], u[1], u[2], dx, dy, dz);
            int base = nv;
            double slope = (ra - rb) / len;
            for (int r = 0; r <= rings; r++) {
                double f = r / (double) rings, rad = ra + (rb - ra) * f;
                double cx = ax + (bx - ax) * f, cy = ay + (by - ay) * f, cz = az + (bz - az) * f;
                for (int s = 0; s <= seg; s++) {
                    double ph = 2 * Math.PI * s / seg, cp = Math.cos(ph), sp = Math.sin(ph);
                    double ox = u[0] * cp + w[0] * sp, oy = u[1] * cp + w[1] * sp, oz = u[2] * cp + w[2] * sp;
                    v(cx + ox * rad, cy + oy * rad, cz + oz * rad, ox + dx * slope, oy + dy * slope, oz + dz * slope, tint.at(ox, oy, oz, f));
                }
            }
            for (int r = 0; r < rings; r++)
                for (int s = 0; s < seg; s++) {
                    int a = base + r * (seg + 1) + s, b = a + seg + 1;
                    quad(a, b, b + 1, a + 1);
                }
            if (caps) {
                float[] ca = tint.at(-dx, -dy, -dz, 0), cb = tint.at(dx, dy, dz, 1);
                int c0 = v(ax, ay, az, -dx, -dy, -dz, ca), c1 = v(bx, by, bz, dx, dy, dz, cb);
                int i0 = nv;
                for (int s = 0; s <= seg; s++) {
                    double ph = 2 * Math.PI * s / seg, cp = Math.cos(ph), sp = Math.sin(ph);
                    double ox = u[0] * cp + w[0] * sp, oy = u[1] * cp + w[1] * sp, oz = u[2] * cp + w[2] * sp;
                    v(ax + ox * ra, ay + oy * ra, az + oz * ra, -dx, -dy, -dz, ca);
                    v(bx + ox * rb, by + oy * rb, bz + oz * rb, dx, dy, dz, cb);
                }
                for (int s = 0; s < seg; s++) {
                    tri(c0, i0 + 2 * (s + 1), i0 + 2 * s);
                    tri(c1, i0 + 2 * s + 1, i0 + 2 * (s + 1) + 1);
                }
            }
        }

        void tube(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, int seg, float[] col) {
            tube(ax, ay, az, bx, by, bz, ra, rb, seg, 1, (a, b, d, e) -> col, true);
        }

        /** Quader um (cx, cy, cz) mit halben Kanten, flache Normalen. */
        void box(double cx, double cy, double cz, double hx, double hy, double hz, float[] col) {
            hull(new double[][]{
                    {cx - hx, cy - hy, cz - hz}, {cx + hx, cy - hy, cz - hz}, {cx + hx, cy + hy, cz - hz}, {cx - hx, cy + hy, cz - hz},
                    {cx - hx, cy - hy, cz + hz}, {cx + hx, cy - hy, cz + hz}, {cx + hx, cy + hy, cz + hz}, {cx - hx, cy + hy, cz + hz}}, col);
        }

        /**
         * Sechsflächner aus acht Ecken in der Reihenfolge eines Quaders (unten hinten links, unten hinten rechts,
         * oben hinten rechts, oben hinten links, dann dasselbe vorn): für Kabinen, Motorhauben, Rümpfe.
         */
        void hull(double[][] k, float[] col) { hull(k, new float[][]{col, col, col, col, col, col}); }

        /** Wie {@link #hull(double[][], float[])}, Farbe je Fläche: hinten, vorn, links, rechts, unten, oben. */
        void hull(double[][] k, float[][] faces) {
            int[][] f = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {0, 1, 5, 4}, {3, 7, 6, 2}};
            double cx = 0, cy = 0, cz = 0;
            for (double[] q : k) { cx += q[0] / 8; cy += q[1] / 8; cz += q[2] / 8; }
            for (int i = 0; i < 6; i++) {
                double[] a = k[f[i][0]], b = k[f[i][1]], d = k[f[i][2]];
                double[] nn = cross(b[0] - a[0], b[1] - a[1], b[2] - a[2], d[0] - a[0], d[1] - a[1], d[2] - a[2]);
                // nach außen zeigen lassen
                double ox = a[0] - cx, oy = a[1] - cy, oz = a[2] - cz;
                boolean flip = nn[0] * ox + nn[1] * oy + nn[2] * oz < 0;
                if (flip) { nn[0] = -nn[0]; nn[1] = -nn[1]; nn[2] = -nn[2]; }
                int[] id = new int[4];
                for (int j = 0; j < 4; j++) { double[] q = k[f[i][j]]; id[j] = v(q[0], q[1], q[2], nn[0], nn[1], nn[2], faces[i]); }
                if (flip) quad(id[0], id[3], id[2], id[1]); else quad(id[0], id[1], id[2], id[3]);
            }
        }

        /** Balken von A nach B, Breite w (waagerecht quer) und Höhe h (senkrecht dazu). */
        void beam(double ax, double ay, double az, double bx, double by, double bz, double w, double h, float[] col) {
            double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len; dy /= len; dz /= len;
            double[] u = Math.abs(dy) < 0.95 ? cross(dx, dy, dz, 0, 1, 0) : cross(dx, dy, dz, 0, 0, 1);
            double ul = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
            u[0] /= ul; u[1] /= ul; u[2] /= ul;
            double[] w2 = cross(u[0], u[1], u[2], dx, dy, dz);
            double hw = w / 2, hh = h / 2;
            double[][] k = new double[8][];
            int i = 0;
            for (int end = 0; end < 2; end++) {
                double ox = end == 0 ? ax : bx, oy = end == 0 ? ay : by, oz = end == 0 ? az : bz;
                double[][] cs = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
                for (double[] cc : cs) k[i++] = new double[]{ox + u[0] * cc[0] + w2[0] * cc[1], oy + u[1] * cc[0] + w2[1] * cc[1], oz + u[2] * cc[0] + w2[2] * cc[1]};
            }
            hull(k, col);
        }

        /** Ebene Fläche (konvex, Punkte im Kreis), beidseitig sichtbar über den Shader. */
        void poly(double[][] q, float[] col) {
            double[] nn = cross(q[1][0] - q[0][0], q[1][1] - q[0][1], q[1][2] - q[0][2], q[2][0] - q[0][0], q[2][1] - q[0][1], q[2][2] - q[0][2]);
            if (nn[1] < 0) { nn[0] = -nn[0]; nn[1] = -nn[1]; nn[2] = -nn[2]; }
            int base = nv;
            for (double[] x : q) v(x[0], x[1], x[2], nn[0], nn[1], nn[2], col);
            for (int i = 1; i + 1 < q.length; i++) tri(base, base + i, base + i + 1);
        }

        LifeMesh build(String name) {
            return new LifeMesh(name, Arrays.copyOf(p, 3 * nv), Arrays.copyOf(n, 3 * nv), Arrays.copyOf(c, 3 * nv), Arrays.copyOf(pa, nv),
                    Arrays.copyOf(pv, 3 * nv), Arrays.copyOf(t, 3 * nt));
        }
    }

    static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        double x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
        double l = Math.sqrt(x * x + y * y + z * z) + 1e-12;
        return new double[]{x / l, y / l, z / l};
    }

    static float[] rgb(double r, double g, double b) { return new float[]{(float) r, (float) g, (float) b}; }

    // ------------------------------------------------------------------ Nēnē

    /** Stehende, gehende oder grasende Nēnē (Füße bei 0, Hals am Drehpunkt beweglich). */
    public static LifeMesh nene() {
        B b = new B().body();
        float[] back = rgb(0.075, 0.062, 0.048), buff = rgb(0.30, 0.24, 0.15), wing = rgb(0.055, 0.046, 0.036);
        // Körper: oben graubraun, unten heller gebändert
        b.ellipsoid(0, 0.20, -0.02, 0.11, 0.10, 0.20, 12, 8, (x, y, z, t) -> y > 0.1 ? back : (((int) Math.floor((z + 1) * 9)) % 2 == 0 ? buff : rgb(0.20, 0.16, 0.10)));
        b.ellipsoid(-0.072, 0.235, -0.05, 0.045, 0.05, 0.16, 8, 5, wing);
        b.ellipsoid(0.072, 0.235, -0.05, 0.045, 0.05, 0.16, 8, 5, wing);
        b.ellipsoid(0, 0.225, -0.215, 0.05, 0.025, 0.06, 8, 4, BLACK);
        b.ellipsoid(0, 0.17, -0.185, 0.05, 0.04, 0.05, 8, 4, WHITE);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.LEG_L : LifeMesh.LEG_R, 0.05 * s, 0.14, 0);
            b.tube(0.05 * s, 0.14, 0, 0.05 * s, 0.015, 0.01, 0.012, 0.010, 5, BLACK);
            b.box(0.05 * s, 0.008, 0.035, 0.022, 0.008, 0.035, BLACK);
        }
        // Hals mit hellen und dunklen Furchen, Kopf schwarz mit gelbbraunen Wangen
        b.part(LifeMesh.NECK, 0, 0.26, 0.13);
        b.tube(0, 0.26, 0.13, 0, 0.38, 0.18, 0.032, 0.025, 8, 6, (x, y, z, t) -> ((int) (t * 6)) % 2 == 0 ? rgb(0.34, 0.29, 0.20) : rgb(0.06, 0.05, 0.04), false);
        b.ellipsoid(0, 0.405, 0.20, 0.028, 0.032, 0.045, 8, 6, (x, y, z, t) -> Math.abs(x) > 0.55 && y < 0.35 && z > -0.6 ? rgb(0.42, 0.30, 0.13) : BLACK);
        b.tube(0, 0.398, 0.24, 0, 0.392, 0.275, 0.012, 0.003, 6, BLACK);
        return b.build("Nēnē");
    }

    /** Fliegende Nēnē (Ursprung in der Körpermitte, Hals gestreckt, Beine angelegt, Flügel am Schultergelenk). */
    public static LifeMesh neneFlying() {
        B b = new B().body();
        float[] back = rgb(0.075, 0.062, 0.048), wing = rgb(0.06, 0.05, 0.04), tip = rgb(0.02, 0.018, 0.016);
        b.ellipsoid(0, 0, 0, 0.10, 0.09, 0.22, 10, 6, (x, y, z, t) -> y > 0 ? back : rgb(0.22, 0.18, 0.12));
        b.ellipsoid(0, 0.005, -0.22, 0.05, 0.02, 0.07, 6, 4, BLACK);
        b.tube(0, 0.02, 0.17, 0, 0.05, 0.38, 0.03, 0.024, 8, 5, (x, y, z, t) -> ((int) (t * 5)) % 2 == 0 ? rgb(0.34, 0.29, 0.20) : rgb(0.06, 0.05, 0.04), false);
        b.ellipsoid(0, 0.055, 0.41, 0.026, 0.03, 0.045, 8, 5, (x, y, z, t) -> Math.abs(x) > 0.55 && y < 0.35 ? rgb(0.42, 0.30, 0.13) : BLACK);
        b.tube(0, 0.05, 0.45, 0, 0.045, 0.485, 0.011, 0.003, 6, BLACK);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.WING_L : LifeMesh.WING_R, 0.08 * s, 0.04, 0.02);
            b.poly(new double[][]{{0.08 * s, 0.04, 0.10}, {0.30 * s, 0.04, 0.12}, {0.30 * s, 0.04, -0.10}, {0.08 * s, 0.04, -0.10}}, wing);
            b.poly(new double[][]{{0.30 * s, 0.04, 0.12}, {0.50 * s, 0.04, 0.06}, {0.56 * s, 0.04, -0.03}, {0.30 * s, 0.04, -0.10}}, tip);
        }
        return b.build("Nēnē im Flug");
    }

    // ------------------------------------------------------------------ Waldvögel, Tropikvogel, Bussard

    /** Kleiner Singvogel im Flug (Ursprung Körpermitte): Art 0 ʻapapane, 1 ʻamakihi, 2 ʻōmaʻo, 3 Japanbrillenvogel. */
    public static LifeMesh songbird(int kind) {
        float[] body, belly, wing, bill;
        double scale;
        switch (kind) {
            case 0 -> { body = rgb(0.42, 0.012, 0.02); belly = rgb(0.58, 0.57, 0.55); wing = BLACK; bill = BLACK; scale = 1.0; }
            case 1 -> { body = rgb(0.20, 0.24, 0.03); belly = rgb(0.30, 0.32, 0.06); wing = rgb(0.07, 0.08, 0.025); bill = DARK; scale = 0.9; }
            case 2 -> { body = rgb(0.075, 0.068, 0.058); belly = rgb(0.16, 0.15, 0.13); wing = rgb(0.05, 0.045, 0.038); bill = BLACK; scale = 1.35; }
            default -> { body = rgb(0.13, 0.17, 0.04); belly = rgb(0.30, 0.30, 0.26); wing = rgb(0.06, 0.08, 0.02); bill = DARK; scale = 0.85; }
        }
        B b = new B().body();
        double k = scale;
        float[] fb = body, fbel = belly;
        b.ellipsoid(0, 0, 0, 0.022 * k, 0.022 * k, 0.045 * k, 8, 5, (x, y, z, t) -> y < -0.45 ? fbel : fb);
        b.ellipsoid(0, 0.012 * k, 0.05 * k, 0.017 * k, 0.017 * k, 0.018 * k, 8, 5, fb);
        if (kind == 3) {
            b.ellipsoid(0.012 * k, 0.017 * k, 0.057 * k, 0.006 * k, 0.007 * k, 0.006 * k, 6, 4, WHITE);
            b.ellipsoid(-0.012 * k, 0.017 * k, 0.057 * k, 0.006 * k, 0.007 * k, 0.006 * k, 6, 4, WHITE);
        }
        double drop = kind == 0 ? 0.012 : kind == 1 ? 0.008 : 0.003;
        b.tube(0, 0.01 * k, 0.064 * k, 0, (0.01 - drop) * k, 0.088 * k, 0.004 * k, 0.0012 * k, 5, bill);
        b.poly(new double[][]{{-0.012 * k, 0.003 * k, -0.035 * k}, {0.012 * k, 0.003 * k, -0.035 * k}, {0.018 * k, 0.003 * k, -0.09 * k}, {-0.018 * k, 0.003 * k, -0.09 * k}}, wing);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.WING_L : LifeMesh.WING_R, 0.018 * s * k, 0.008 * k, 0.012 * k);
            b.poly(new double[][]{{0.018 * s * k, 0.008 * k, 0.03 * k}, {0.07 * s * k, 0.008 * k, 0.02 * k}, {0.11 * s * k, 0.008 * k, -0.01 * k},
                    {0.07 * s * k, 0.008 * k, -0.03 * k}, {0.018 * s * k, 0.008 * k, -0.02 * k}}, wing);
        }
        String[] n = {"ʻApapane", "ʻAmakihi", "ʻŌmaʻo", "Japanbrillenvogel"};
        return b.build(n[Math.max(0, Math.min(3, kind))]);
    }

    /** Koaʻe kea im Flug: weiß, schwarzer Augenstreif, schwarze Binde und Spitzen, zwei lange Schwanzspieße. */
    public static LifeMesh tropicbird() {
        B b = new B().body();
        float[] white = rgb(0.72, 0.72, 0.70);
        b.ellipsoid(0, 0, 0, 0.055, 0.05, 0.17, 10, 6, white);
        b.ellipsoid(0, 0.02, 0.17, 0.04, 0.042, 0.05, 8, 5, (x, y, z, t) -> Math.abs(x) > 0.6 && Math.abs(y) < 0.35 ? BLACK : white);
        b.tube(0, 0.018, 0.21, 0, 0.01, 0.27, 0.012, 0.003, 6, rgb(0.60, 0.33, 0.03));
        b.part(LifeMesh.TAIL, 0, 0, -0.16);
        b.poly(new double[][]{{-0.012, 0.0, -0.16}, {0.012, 0.0, -0.16}, {0.01, -0.01, -0.52}, {-0.002, -0.01, -0.52}}, white);
        b.poly(new double[][]{{-0.004, 0.003, -0.16}, {0.018, 0.003, -0.16}, {0.008, -0.004, -0.50}, {-0.006, -0.004, -0.50}}, white);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.WING_L : LifeMesh.WING_R, 0.05 * s, 0.02, 0.03);
            b.poly(new double[][]{{0.05 * s, 0.02, 0.09}, {0.16 * s, 0.02, 0.09}, {0.16 * s, 0.02, -0.05}, {0.05 * s, 0.02, -0.05}}, BLACK);   // Binde
            b.poly(new double[][]{{0.16 * s, 0.02, 0.09}, {0.36 * s, 0.02, 0.05}, {0.36 * s, 0.02, -0.06}, {0.16 * s, 0.02, -0.05}}, white);
            b.poly(new double[][]{{0.36 * s, 0.02, 0.05}, {0.47 * s, 0.02, 0.0}, {0.36 * s, 0.02, -0.06}}, BLACK);
        }
        return b.build("Koaʻe kea");
    }

    /** ʻIo, kreisend mit breiten Flügeln und Fächerschwanz. */
    public static LifeMesh hawk() {
        B b = new B().body();
        float[] brown = rgb(0.045, 0.032, 0.022), pale = rgb(0.16, 0.12, 0.08);
        b.ellipsoid(0, 0, 0, 0.075, 0.07, 0.22, 10, 6, (x, y, z, t) -> y < -0.5 ? pale : brown);
        b.ellipsoid(0, 0.02, 0.21, 0.05, 0.05, 0.055, 8, 5, brown);
        b.tube(0, 0.015, 0.255, 0, -0.005, 0.29, 0.014, 0.003, 6, BLACK);
        b.poly(new double[][]{{-0.04, 0.0, -0.18}, {0.04, 0.0, -0.18}, {0.09, 0.0, -0.40}, {-0.09, 0.0, -0.40}}, brown);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.WING_L : LifeMesh.WING_R, 0.07 * s, 0.03, 0.03);
            b.poly(new double[][]{{0.07 * s, 0.03, 0.12}, {0.30 * s, 0.05, 0.13}, {0.30 * s, 0.05, -0.12}, {0.07 * s, 0.03, -0.12}}, brown);
            b.poly(new double[][]{{0.30 * s, 0.05, 0.13}, {0.50 * s, 0.07, 0.08}, {0.54 * s, 0.07, -0.04}, {0.30 * s, 0.05, -0.12}}, brown);
        }
        return b.build("ʻIo");
    }

    // ------------------------------------------------------------------ Hubschrauber

    /** Einmotoriger Rundflug-Hubschrauber, Kufen bei y = 0, Hauptrotor drei Blätter zu 5,35 m. */
    public static LifeMesh helicopter() {
        B b = new B().body();
        float[] paint = LifeMesh.PAINT;
        double cy = 1.45;
        // Kabine: Front und Seitenfenster dunkel
        b.ellipsoid(0, cy, 0.9, 0.92, 0.85, 1.75, 16, 10, (x, y, z, t) -> (z > 0.30 && y > -0.30) || (Math.abs(x) > 0.75 && y > 0.05 && y < 0.62 && z > -0.55) ? GLASS : paint);
        b.box(0, 2.12, -0.35, 0.55, 0.26, 0.95, paint);
        b.tube(0, 1.85, -0.9, 0, 2.05, -6.2, 0.38, 0.13, 10, paint);
        b.poly(new double[][]{{0, 2.0, -5.85}, {0, 3.05, -6.55}, {0, 3.10, -6.95}, {0, 1.65, -6.75}}, paint);
        b.box(0, 2.0, -5.2, 0.85, 0.03, 0.22, paint);
        b.part(LifeMesh.HOT, 0, 0, 0);
        b.tube(0, 2.22, -1.20, 0, 2.30, -1.55, 0.19, 0.17, 8, DARK);
        b.body();
        for (int s = -1; s <= 1; s += 2) {
            b.tube(1.05 * s, 0.06, -1.4, 1.05 * s, 0.06, 1.9, 0.05, 0.05, 6, DARK);
            b.tube(1.05 * s, 0.06, 1.9, 1.05 * s, 0.26, 2.25, 0.05, 0.05, 6, DARK);
            b.tube(1.05 * s, 0.06, 1.0, 0.55 * s, 0.78, 1.0, 0.04, 0.04, 5, DARK);
            b.tube(1.05 * s, 0.06, -0.8, 0.55 * s, 0.78, -0.8, 0.04, 0.04, 5, DARK);
        }
        b.tube(0, 2.38, 0.2, 0, 2.95, 0.2, 0.08, 0.07, 6, STEEL);
        b.part(LifeMesh.ROTOR, 0, 2.98, 0.2);
        b.ellipsoid(0, 2.98, 0.2, 0.2, 0.12, 0.2, 8, 4, STEEL);
        for (int k = 0; k < 3; k++) {
            double a = 2 * Math.PI * k / 3, ca = Math.cos(a), sa = Math.sin(a);
            b.beam(0.15 * ca, 2.98, 0.2 + 0.15 * sa, 5.35 * ca, 2.96, 0.2 + 5.35 * sa, 0.35, 0.05, DARK);
        }
        b.part(LifeMesh.TAIL_ROTOR, -0.3, 2.35, -6.55);   // rechts (x zeigt nach links)
        for (int k = -1; k <= 1; k += 2) b.beam(-0.3, 2.35, -6.55, -0.3, 2.35 + 0.93 * k, -6.55, 0.18, 0.03, DARK);
        b.body();
        b.part(LifeMesh.LIGHT, 0, 0, 0);
        b.box(0, 3.08, -0.3, 0.08, 0.05, 0.08, rgb(1.0, 0.04, 0.02));   // Blitzlicht oben [G]
        return b.build("Hubschrauber");
    }

    // ------------------------------------------------------------------ Wanderer

    /** Wanderer 1,75 m mit Rucksack; Variante wählt Hose, Haut, Rucksack und Mütze. Jacke = Farbe der Figur. */
    public static LifeMesh hiker(int variant) {
        float[][] pants = {rgb(0.035, 0.035, 0.04), rgb(0.16, 0.13, 0.08), rgb(0.03, 0.05, 0.10)};
        float[][] skin = {rgb(0.45, 0.30, 0.22), rgb(0.27, 0.16, 0.10), rgb(0.12, 0.07, 0.045)};
        float[][] pack = {rgb(0.03, 0.06, 0.03), rgb(0.12, 0.02, 0.02), rgb(0.04, 0.04, 0.05)};
        float[] pa = pants[variant % 3], sk = skin[(variant / 3 + variant) % 3], pk = pack[(variant + 1) % 3];
        B b = new B().body();
        float[] paint = LifeMesh.PAINT, shoe = rgb(0.04, 0.025, 0.015);
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.LEG_L : LifeMesh.LEG_R, 0.1 * s, 0.92, 0);
            b.beam(0.1 * s, 0.92, 0, 0.1 * s, 0.07, 0, 0.14, 0.15, pa);
            b.box(0.1 * s, 0.05, 0.05, 0.055, 0.05, 0.13, shoe);
        }
        b.body();
        b.box(0, 1.18, 0, 0.19, 0.30, 0.115, paint);
        b.box(0, 0.93, 0, 0.18, 0.06, 0.11, pa);
        b.box(0, 1.24, -0.19, 0.15, 0.24, 0.08, pk);
        b.tube(0, 1.47, 0, 0, 1.53, 0, 0.05, 0.05, 6, sk);
        b.ellipsoid(0, 1.63, 0.01, 0.09, 0.11, 0.10, 10, 7, sk);
        if (variant % 2 == 0) {
            b.ellipsoid(0, 1.71, 0.0, 0.10, 0.045, 0.11, 10, 4, pk);
            b.box(0, 1.69, 0.12, 0.08, 0.008, 0.06, pk);
        } else b.ellipsoid(0, 1.70, -0.01, 0.095, 0.06, 0.105, 10, 4, rgb(0.03, 0.02, 0.015));   // Haar
        for (int s = -1; s <= 1; s += 2) {
            b.part(s < 0 ? LifeMesh.ARM_L : LifeMesh.ARM_R, 0.25 * s, 1.43, 0);
            b.beam(0.25 * s, 1.43, 0, 0.27 * s, 0.86, 0.03, 0.09, 0.10, paint);
            b.box(0.27 * s, 0.81, 0.03, 0.04, 0.05, 0.04, sk);
        }
        return b.build("Wanderer " + (variant + 1));
    }

    // ------------------------------------------------------------------ Autos

    /** Auto: 0 Limousine, 1 Geländewagen, 2 Kleinbus. Lack = Farbe der Figur; Lichter leuchten nachts. */
    public static LifeMesh car(int type) {
        B b = new B().body();
        float[] paint = LifeMesh.PAINT, trim = rgb(0.02, 0.02, 0.022), rim = rgb(0.16, 0.16, 0.17);
        double hw, len, sill, belt, roof, rWheel, axle;
        double cabF, cabB, topF, topB, inset;
        switch (type) {
            case 1 -> { hw = 0.97; len = 4.85; sill = 0.36; belt = 1.12; roof = 1.78; rWheel = 0.38; axle = 1.47; cabF = 1.15; cabB = -2.20; topF = 0.65; topB = -2.15; inset = 0.08; }
            case 2 -> { hw = 1.0; len = 5.5; sill = 0.36; belt = 1.25; roof = 2.30; rWheel = 0.36; axle = 1.75; cabF = 2.15; cabB = -2.68; topF = 1.55; topB = -2.68; inset = 0.04; }
            default -> { hw = 0.91; len = 4.8; sill = 0.30; belt = 0.95; roof = 1.45; rWheel = 0.33; axle = 1.42; cabF = 1.05; cabB = -1.35; topF = 0.35; topB = -0.95; inset = 0.17; }
        }
        double hl = len / 2;
        // Unterbau mit leicht abfallender Haube und Heck
        b.hull(new double[][]{
                {-hw, sill, -hl}, {hw, sill, -hl}, {hw, belt - 0.05, -hl}, {-hw, belt - 0.05, -hl},
                {-hw, sill, hl}, {hw, sill, hl}, {hw, belt - (type == 2 ? 0.0 : 0.12), hl}, {-hw, belt - (type == 2 ? 0.0 : 0.12), hl}}, paint);
        // Kabine: Glas rundum, Dach in Lackfarbe
        double ti = hw - inset;
        b.hull(new double[][]{
                {-hw + 0.02, belt, cabB}, {hw - 0.02, belt, cabB}, {ti, roof - 0.04, topB}, {-ti, roof - 0.04, topB},
                {-hw + 0.02, belt, cabF}, {hw - 0.02, belt, cabF}, {ti, roof - 0.04, topF}, {-ti, roof - 0.04, topF}}, GLASS);
        b.box(0, roof - 0.02, 0.5 * (topF + topB), ti, 0.025, 0.5 * (topF - topB), paint);
        if (type == 2) {   // Säulen und Fensterband des Busses in Lack
            b.box(0, belt + 0.02, 0, hw + 0.005, 0.03, hl - 0.05, paint);
            for (double z = -2.0; z <= 1.6; z += 1.2) b.box(0, 0.5 * (belt + roof), z, hw + 0.004, 0.5 * (roof - belt) - 0.03, 0.06, paint);
        }
        b.box(0, sill + 0.06, hl + 0.03, hw - 0.02, 0.09, 0.05, trim);
        b.box(0, sill + 0.06, -hl - 0.03, hw - 0.02, 0.09, 0.05, trim);
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                double x = (hw - 0.12) * sx, z = axle * sz;
                b.tube(x - 0.11, rWheel, z, x + 0.11, rWheel, z, rWheel, rWheel, 12, RUBBER);
                b.tube(x + 0.11 * sx, rWheel, z, x + 0.12 * sx, rWheel, z, rWheel * 0.55, rWheel * 0.55, 10, rim);
            }
        b.part(LifeMesh.LIGHT, 0, 0, 0);
        double ly = type == 2 ? 0.85 : belt - 0.2;
        for (int s = -1; s <= 1; s += 2) {
            b.box((hw - 0.25) * s, ly, hl + 0.005, 0.16, 0.055, 0.02, rgb(1.0, 0.92, 0.80));
            b.box((hw - 0.2) * s, ly + 0.05, -hl - 0.005, 0.14, 0.06, 0.02, rgb(1.0, 0.03, 0.015));
        }
        String[] n = {"Limousine", "Geländewagen", "Kleinbus"};
        return b.build(n[Math.max(0, Math.min(2, type))]);
    }
}
