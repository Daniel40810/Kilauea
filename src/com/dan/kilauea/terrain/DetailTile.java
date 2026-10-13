package com.dan.kilauea.terrain;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Eine Detailkachel: ein feines Raster (4 bis 8 m) über einem Ausschnitt, das nur den Unterschied zum
 * 75-m-Gelände speichert. Shader und {@link Ground} zählen ihn dazu. Gebaut wird in vier Schritten:
 * <ol>
 * <li><b>Vorher</b> ({@link Spec#pre}): Formen, die die Höhendaten nicht auflösen, etwa Grubenkrater
 * oder ein Kegel von 2018 [L Lage und Maß, G Form].</li>
 * <li><b>Schärfen</b> ({@link Sharpen}): Je Punkt das obere und untere Niveau im Umkreis; wo der Hang
 * steil und hoch genug ist, wird der Übergang auf einen Bruchteil seiner Breite zusammengezogen, mit
 * flacherem Anlauf (Schutt) am Fuß [A]. Ebene Flächen bleiben. Entlang von Wegen und Straßen wird nicht
 * geschärft ({@link Spec#exempt}).</li>
 * <li><b>Küste</b> (wahlweise): Land in Ufernähe wird bis an die Wasserlinie auf die Höhe der Klippe
 * gehoben, das Wasser davor wird tief — eine senkrechte Meeresklippe statt eines flachen Hangs [G].</li>
 * <li><b>Nachher</b> ({@link Spec#post}): Feines wie Risse und Spalten, Gräben, Dämme.</li>
 * </ol>
 * Zum Rand läuft der Unterschied auf 0 aus. Die Kachel wird einmal gebaut und im Projekt unter
 * {@code data/kachel_<id>.bin} abgelegt (mit Prüfsumme der Höhen und Version der Formen).
 */
public final class DetailTile {
    static final int MAGIC = 0x4B494C44;   // "KILD"

    /**
     * Wie geschärft wird: Halbmesser und Glättung (Zellen), Mindesthöhe und Mindestneigung (je von–bis),
     * Lage der Kante (t von–bis), Anteil des Anlaufs, Welle der Kante, Anteil des Hebens (1 = die
     * Oberkante geht ganz auf das obere Niveau; kleiner, wo sonst ein Wulst um Gruben entsteht).
     */
    public record Sharpen(int radius, int smooth, double rangeLo, double rangeHi, double slopeLo, double slopeHi,
                          double tLo, double tHi, double foot, double wobble, double lift) {
        public static final Sharpen NONE = new Sharpen(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Was eine Kachel ausmacht. */
    public abstract static class Spec {
        public final String id, name;
        public final double x0, z0, cell;
        public final int nx, nz;
        public final int version;
        public final Sharpen sharpen;
        public final boolean coast;
        /** Breite des Randes (m), in dem der Unterschied ausläuft. */
        public double edge = 300;

        protected Spec(String id, String name, double lat0, double lon0, double lat1, double lon1, double cell, int version, Sharpen sharpen, boolean coast) {
            this(id, name, Geo.x(0.5 * (lat0 + lat1), lon0), Geo.z(lat1),
                    (int) Math.round((Geo.x(0.5 * (lat0 + lat1), lon1) - Geo.x(0.5 * (lat0 + lat1), lon0)) / cell) + 1,
                    (int) Math.round((Geo.z(lat0) - Geo.z(lat1)) / cell) + 1, cell, version, sharpen, coast);
        }

        protected Spec(String id, String name, double x0, double z0, int nx, int nz, double cell, int version, Sharpen sharpen, boolean coast) {
            this.id = id;
            this.name = name;
            this.x0 = x0;
            this.z0 = z0;
            this.nx = nx;
            this.nz = nz;
            this.cell = cell;
            this.version = version;
            this.sharpen = sharpen;
            this.coast = coast;
        }

        /** Höhe vor dem Schärfen (Grundhöhe base); Standard: unverändert. */
        public double pre(double x, double z, double base) { return base; }

        /** Höhe nach dem Schärfen (h) und der Küste; Standard: unverändert. */
        public double post(double x, double z, double h, double base) { return h; }

        /**
         * Höhe der Meeresklippe an der Wasserlinie (m) oder NaN: dann wird das Land bis 250 m landein
         * auf eine Rampe zu dieser Kante gehoben (die 75-m-Daten mitteln die Klippe mit dem Meer weg).
         */
        public double cliffTop(double x, double z) { return Double.NaN; }

        /** Je Zelle 0..1: wo nicht geschärft wird (Wege, Straßen); null = überall. */
        public float[] exempt() { return null; }

        public double x1() { return x0 + (nx - 1) * cell; }
        public double z1() { return z0 + (nz - 1) * cell; }
        public boolean inside(double x, double z) { return x >= x0 && z >= z0 && x <= x1() && z <= z1(); }
    }

    public final Spec spec;
    /** Unterschied zum 75-m-Gelände (m), Zeile für Zeile (z), je Zeile nx Werte. */
    public final float[] delta;
    public final float minDelta, maxDelta;

    DetailTile(Spec spec, float[] delta) {
        this.spec = spec;
        this.delta = delta;
        float lo = 0, hi = 0;
        for (float v : delta) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        minDelta = lo;
        maxDelta = hi;
    }

    /** Unterschied an (x, z), bilinear; 0 außerhalb. */
    public double at(double x, double z) {
        Spec s = spec;
        double u = (x - s.x0) / s.cell, v = (z - s.z0) / s.cell;
        if (u < 0 || v < 0 || u > s.nx - 1 || v > s.nz - 1) return 0;
        int i = Math.min(s.nx - 2, (int) u), j = Math.min(s.nz - 2, (int) v);
        double fx = u - i, fz = v - j;
        int k = j * s.nx + i;
        double a = delta[k], b = delta[k + 1], c = delta[k + s.nx], d = delta[k + s.nx + 1];
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    // ------------------------------------------------------------------ Laden und Bauen

    public static File cacheFile(File projectDir, Spec s) { return new File(projectDir, "data/kachel_" + s.id + ".bin"); }

    /** Lädt die Kachel oder baut sie aus dem Raster g (und legt sie ab). */
    public static DetailTile load(File projectDir, HeightGrid g, Spec s) {
        long sum = checksum(g, s);
        File f = cacheFile(projectDir, s);
        if (f.isFile()) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 20))) {
                if (in.readInt() == MAGIC && in.readInt() == s.version && in.readLong() == sum && in.readInt() == s.nx && in.readInt() == s.nz) {
                    byte[] raw = new byte[s.nx * s.nz * 2];
                    in.readFully(raw);
                    float[] d = new float[s.nx * s.nz];
                    for (int k = 0; k < d.length; k++) d[k] = (short) (((raw[2 * k] & 255) << 8) | (raw[2 * k + 1] & 255)) / 50f;
                    return new DetailTile(s, d);
                }
            } catch (IOException e) {
                System.err.println("Kachel " + s.name + " nicht lesbar, baue neu: " + e.getMessage());
            }
        }
        DetailTile t = build(g, s);
        try {
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f), 1 << 20))) {
                out.writeInt(MAGIC);
                out.writeInt(s.version);
                out.writeLong(sum);
                out.writeInt(s.nx);
                out.writeInt(s.nz);
                // 2 cm Schritte, ±655 m
                for (float v : t.delta) out.writeShort((short) Math.max(-32767, Math.min(32767, Math.round(v * 50))));
            }
        } catch (IOException e) {
            System.err.println("Kachel " + s.name + " nicht gesichert: " + e.getMessage());
        }
        return t;
    }

    /** Prüfsumme der Höhen unter der Kachel: ändert sich das Gelände, wird neu gebaut. */
    static long checksum(HeightGrid g, Spec s) {
        long h = 1469598103934665603L;
        for (int j = 0; j < 64; j++)
            for (int i = 0; i < 64; i++) {
                double x = s.x0 + (s.nx - 1) * s.cell * i / 63.0, z = s.z0 + (s.nz - 1) * s.cell * j / 63.0;
                h ^= Double.doubleToLongBits(Math.round(g.height(x, z) * 100) / 100.0);
                h *= 1099511628211L;
            }
        return h;
    }

    public static DetailTile build(HeightGrid g, Spec s) {
        int nx = s.nx, nz = s.nz, n = nx * nz;
        double cell = s.cell, X0 = s.x0, Z0 = s.z0;
        float[] base = new float[n], pre = new float[n];
        IntStream.range(0, nz).parallel().forEach(j -> {
            double z = Z0 + j * cell;
            for (int i = 0; i < nx; i++) {
                double x = X0 + i * cell, b = g.height(x, z);
                base[j * nx + i] = (float) b;
                pre[j * nx + i] = (float) s.pre(x, z, b);
            }
        });
        float[] h = sharpen(pre, s);
        if (s.coast) cliff(h, s);
        float[] d = new float[n];
        final float[] fh = h;
        IntStream.range(0, nz).parallel().forEach(j -> {
            double z = Z0 + j * cell;
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                double x = X0 + i * cell;
                double v = s.post(x, z, fh[k], base[k]);
                double edge = Math.min(Math.min(x - X0, X0 + (nx - 1) * cell - x), Math.min(z - Z0, Z0 + (nz - 1) * cell - z));
                d[k] = (float) ((v - base[k]) * Island.smooth(0, s.edge, edge));
            }
        });
        return new DetailTile(s, d);
    }

    /** Wände schärfen (siehe Klassenbeschreibung). */
    static float[] sharpen(float[] base, Spec s) {
        Sharpen p = s.sharpen;
        if (p.radius() <= 0) return base.clone();
        int nx = s.nx, nz = s.nz, n = nx * nz;
        double cell = s.cell;
        float[] up = maxFilter(base, nx, nz, p.radius(), true), lo = maxFilter(base, nx, nz, p.radius(), false);
        if (p.smooth() > 0) {
            up = box(up, nx, nz, p.smooth());
            lo = box(lo, nx, nz, p.smooth());
        }
        float[] exempt = s.exempt();
        float[] out = new float[n];
        final float[] fu = up, fl = lo;
        int gs = Math.max(1, (int) Math.round(12 / cell));   // Neigung über rund ±12 m
        IntStream.range(0, nz).parallel().forEach(j -> {
            double z = s.z0 + j * cell;
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                double x = s.x0 + i * cell;
                double b = base[k], U = Math.max(fu[k], b), L = Math.min(fl[k], b), range = U - L;
                int il = Math.max(0, i - gs), ir = Math.min(nx - 1, i + gs), jl = Math.max(0, j - gs), jr = Math.min(nz - 1, j + gs);
                double gx = (base[j * nx + ir] - base[j * nx + il]) / ((ir - il) * cell), gz = (base[jr * nx + i] - base[jl * nx + i]) / ((jr - jl) * cell);
                double slope = Math.hypot(gx, gz);
                double w = Island.smooth(p.rangeLo(), p.rangeHi(), range) * Island.smooth(p.slopeLo(), p.slopeHi(), slope) * (exempt == null ? 1 : 1 - exempt[k]);
                double h = b;
                if (w > 0 && range > 1e-3) {
                    double t = (b - L) / range;
                    // Lage der Kante etwas gewellt, damit Wände nicht wie mit dem Lineal gezogen wirken
                    t += p.wobble() * (Noise.fbm(x * 0.012, z * 0.012, 3) - 0.5);
                    double t2 = Island.smooth(p.tLo(), p.tHi(), t);
                    // Schutt am Fuß: unten flacher Anlauf
                    t2 = t2 * (1 - p.foot()) + p.foot() * Island.smooth(0.0, 0.64, t);
                    double target = L + range * t2;
                    // heben nach Anteil lift: das obere Niveau ist das Höchste im Umkreis und läge sonst als
                    // Wulst um Gruben; senken voll
                    if (target > b) target = b + (target - b) * p.lift();
                    h = b + (target - b) * w;
                }
                out[k] = (float) h;
            }
        });
        return out;
    }

    /**
     * Meeresklippe: Land, das bis 60 m an Wasser grenzt, wird auf das Niveau der Klippe im Umkreis
     * gehoben (die Wasserlinie bleibt, wo sie ist); flaches Wasser vor der Klippe wird mindestens 6 m tief.
     */
    static void cliff(float[] h, Spec s) {
        int nx = s.nx, nz = s.nz, n = nx * nz;
        int r = Math.max(2, (int) Math.round(60 / s.cell));
        float[] wet = new float[n], landH = new float[n], isLand = new float[n];
        for (int k = 0; k < n; k++) {
            boolean land = h[k] > 0;
            wet[k] = land ? 0 : 1;
            isLand[k] = land ? 1 : 0;
            landH[k] = land ? h[k] : 0;
        }
        float[] frac = box(wet, nx, nz, r), sumH = box(landH, nx, nz, r), cnt = box(isLand, nx, nz, r);
        float[] dist = distanceToWater(isLand, nx, nz, s.cell, 260);
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                double v = h[k];
                double x = s.x0 + i * s.cell, z = s.z0 + j * s.cell;
                double ct = s.cliffTop(x, z);
                if (v > 0 && !Double.isNaN(ct) && dist[k] < 250) {
                    // Rampe von der Kante (ct) landein bis auf die Höhe der Daten in 250 m
                    double f = dist[k] / 250;
                    v = Math.max(v, v + (ct - v) * (1 - f) * (1 - f * 0.5));
                    h[k] = (float) v;
                }
                if (frac[k] <= 0) continue;
                if (v > 0) {
                    // auf das mittlere Land ringsum heben, am Ufer ganz, 60 m landein nicht mehr;
                    // die Höhe der Klippe schwankt mit Bänken und Buchten (70–100 %)
                    double plateau = sumH[k] / Math.max(1e-3, cnt[k]) * (0.7 + 0.3 * Noise.fbm(x * 0.01 + 7, z * 0.01, 3));
                    h[k] = (float) (v + Math.max(0, plateau - v) * Math.min(1, frac[k] * 3));
                } else if (v > -6) {
                    h[k] = (float) Math.min(v, -6 + 4 * Noise.value(x * 0.05, z * 0.05));
                }
            }
        });
        // Die Wasserlinie folgt sonst den Zellen: schräg zum Raster eine Treppe, deren Stufen als
        // senkrechte Rippen in der Klippe stehen. Darum die Kante aus einem geglätteten Landanteil legen.
        float[] land2 = new float[n], top2 = new float[n];
        for (int k = 0; k < n; k++) { land2[k] = h[k] > 0 ? 1 : 0; top2[k] = h[k] > 0 ? h[k] : 0; }
        float[] lf = box(land2, nx, nz, 2), ts = box(top2, nx, nz, 2);
        IntStream.range(0, n).parallel().forEach(k -> {
            float L = lf[k];
            if (L <= 0.02f || L >= 0.98f) return;
            double top = ts[k] / L, sea = Math.min(h[k], -4);
            if (top <= 0) return;
            h[k] = (float) (sea + (top - sea) * Island.smooth(0.30, 0.62, L));
        });
    }

    /** Abstand jeder Landzelle zum nächsten Wasser (m, zwei Durchläufe mit Schachbrett-Schritten), höchstens cap. */
    static float[] distanceToWater(float[] isLand, int nx, int nz, double cell, double cap) {
        float[] d = new float[nx * nz];
        float c1 = (float) cell, c2 = (float) (cell * Math.sqrt(2));
        for (int k = 0; k < d.length; k++) d[k] = isLand[k] > 0.5f ? (float) cap : 0;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                if (d[k] == 0) continue;
                float v = d[k];
                if (i > 0) v = Math.min(v, d[k - 1] + c1);
                if (j > 0) v = Math.min(v, d[k - nx] + c1);
                if (i > 0 && j > 0) v = Math.min(v, d[k - nx - 1] + c2);
                if (i < nx - 1 && j > 0) v = Math.min(v, d[k - nx + 1] + c2);
                d[k] = v;
            }
        for (int j = nz - 1; j >= 0; j--)
            for (int i = nx - 1; i >= 0; i--) {
                int k = j * nx + i;
                if (d[k] == 0) continue;
                float v = d[k];
                if (i < nx - 1) v = Math.min(v, d[k + 1] + c1);
                if (j < nz - 1) v = Math.min(v, d[k + nx] + c1);
                if (i < nx - 1 && j < nz - 1) v = Math.min(v, d[k + nx + 1] + c2);
                if (i > 0 && j < nz - 1) v = Math.min(v, d[k + nx - 1] + c2);
                d[k] = v;
            }
        return d;
    }

    // ------------------------------------------------------------------ Filter

    /** Gleitendes Maximum (oder Minimum) über ein Quadrat mit Halbmesser r, getrennt nach Zeilen und Spalten. */
    static float[] maxFilter(float[] a, int nx, int nz, int r, boolean max) {
        float[] t = new float[a.length], o = new float[a.length];
        IntStream.range(0, nz).parallel().forEach(j -> slide(a, t, j * nx, 1, nx, r, max));
        IntStream.range(0, nx).parallel().forEach(i -> slide(t, o, i, nx, nz, r, max));
        return o;
    }

    private static void slide(float[] src, float[] dst, int off, int stride, int n, int r, boolean max) {
        int[] q = new int[n];
        int head = 0, tail = 0;
        for (int i = 0; i < n + r; i++) {
            if (i < n) {
                float v = src[off + i * stride];
                while (tail > head && (max ? src[off + q[tail - 1] * stride] <= v : src[off + q[tail - 1] * stride] >= v)) tail--;
                q[tail++] = i;
            }
            int c = i - r;
            if (c < 0) continue;
            while (q[head] < c - r) head++;
            dst[off + c * stride] = src[off + q[head] * stride];
        }
    }

    /** Mittel über ein Quadrat mit Halbmesser r (zweimal getrennt, mit Rändern). */
    static float[] box(float[] a, int nx, int nz, int r) {
        float[] t = new float[a.length], o = new float[a.length];
        IntStream.range(0, nz).parallel().forEach(j -> boxLine(a, t, j * nx, 1, nx, r));
        IntStream.range(0, nx).parallel().forEach(i -> boxLine(t, o, i, nx, nz, r));
        return o;
    }

    private static void boxLine(float[] src, float[] dst, int off, int stride, int n, int r) {
        double[] pre = new double[n + 1];
        for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + src[off + i * stride];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - r), b = Math.min(n, i + r + 1);
            dst[off + i * stride] = (float) ((pre[b] - pre[a]) / (b - a));
        }
    }

    // ------------------------------------------------------------------ Formen für pre/post

    /** Grube: Ellipse mit Halbachsen ra, rb (m), gedreht um rot (Grad, von Ost gegen Uhrzeiger), Tiefe unter dem Rand (m). */
    public record Pit(String name, double lat, double lon, double ra, double rb, double rot, double depth) {
        public double x() { return Geo.x(lat, lon); }
        public double z() { return Geo.z(lat); }
    }

    /**
     * Grube in die Höhe b schneiden: ebener Boden, steile Wand (das Schärfen macht sie senkrecht),
     * Rand leicht unregelmäßig. rim: Höhe des Randes (Mittel ringsum).
     */
    public static double carve(Pit p, double x, double z, double b, double rim) {
        double a = Math.toRadians(p.rot());
        double dx = x - p.x(), dz = z - p.z();
        double u = (dx * Math.cos(a) - dz * Math.sin(a)) / p.ra(), v = (dx * Math.sin(a) + dz * Math.cos(a)) / p.rb();
        double q = Math.sqrt(u * u + v * v);
        if (q > 1.35) return b;
        // Umriss gebuchtet, nicht gezirkelt: zwei Wellen um den Rand und Rauschen
        double ang = Math.atan2(v, u), ph = p.ra() * 0.37;
        q *= 1 + 0.09 * Math.sin(3 * ang + ph) + 0.06 * Math.sin(5 * ang + ph * 2.3);
        q += 0.22 * (Noise.fbm(x * 0.007 + p.ra(), z * 0.007, 3) - 0.5);
        double floor = rim - p.depth();
        // innen eben, zur Wand hin bis auf die eigene Höhe: außen bleibt das Gelände, wie es ist
        double target = floor + (Math.max(b, floor) - floor) * Island.smooth(0.78, 1.0, q);
        return Math.min(b, target);
    }

    /** Mittlere Grundhöhe auf einem Ring um die Grube (aus dem 75-m-Gelände). */
    public static double rim(HeightGrid g, Pit p) {
        double s = 0;
        for (int k = 0; k < 24; k++) {
            double an = k * Math.PI / 12, r = 1.25;
            double a = Math.toRadians(p.rot());
            double u = Math.cos(an) * p.ra() * r, v = Math.sin(an) * p.rb() * r;
            double x = p.x() + u * Math.cos(a) + v * Math.sin(a), z = p.z() - u * Math.sin(a) + v * Math.cos(a);
            s += g.height(x, z);
        }
        return s / 24;
    }

    /** Abstand zu einer Linie aus Punkten (x, z) und Lage darauf (0..1). */
    public static double[] lineDist(double[][] l, double x, double z) {
        double best = Double.MAX_VALUE, bt = 0, acc = 0, total = 0;
        for (int i = 1; i < l.length; i++) total += Math.hypot(l[i][0] - l[i - 1][0], l[i][1] - l[i - 1][1]);
        for (int i = 1; i < l.length; i++) {
            double ax = l[i - 1][0], az = l[i - 1][1], vx = l[i][0] - ax, vz = l[i][1] - az, len = Math.hypot(vx, vz);
            double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / Math.max(1e-9, len * len)));
            double d = Math.hypot(x - ax - vx * t, z - az - vz * t);
            if (d < best) { best = d; bt = (acc + t * len) / Math.max(1e-9, total); }
            acc += len;
        }
        return new double[]{best, bt};
    }

    public static double[][] xz(double[][] latLon) {
        double[][] r = new double[latLon.length][];
        for (int i = 0; i < latLon.length; i++) r[i] = new double[]{Geo.x(latLon[i][0], latLon[i][1]), Geo.z(latLon[i][0])};
        return r;
    }
}
