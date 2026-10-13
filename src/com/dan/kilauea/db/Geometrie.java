package com.dan.kilauea.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 16b: Geometrie für die Grunddaten, ohne Oracle. Alle Koordinaten in Metern in der Ebene (Ost, Nord), also
 * (x, −z) der Szene. Ringe sind offen gespeichert (der erste Punkt wird nicht wiederholt).
 * <ul>
 * <li>{@link #umriss}: Umriss einer Zellmenge als Polygone mit Löchern (Marching Squares auf der Mitte zwischen den
 * Zellmitten; die Linien berühren und kreuzen sich nie)</li>
 * <li>{@link #vereinfache}, {@link #vereinfacheRing}: Douglas–Peucker</li>
 * <li>{@link #einfach}, {@link #schneiden}: Prüfungen, die Oracle bei VALIDATE_GEOMETRY_WITH_CONTEXT auch macht</li>
 * </ul>
 */
public final class Geometrie {
    private Geometrie() { }

    /** Ein Polygon: Außenring gegen den Uhrzeigersinn, Löcher im Uhrzeigersinn (wie Oracle es verlangt). */
    public static final class Polygon {
        public double[] aussen;
        public final List<double[]> loecher = new ArrayList<>();
        public double flaeche() {
            double a = Math.abs(Geometrie.flaeche(aussen));
            for (double[] h : loecher) a -= Math.abs(Geometrie.flaeche(h));
            return a;
        }
        public int punkte() {
            int n = aussen.length / 2;
            for (double[] h : loecher) n += h.length / 2;
            return n;
        }
    }

    /** Vorzeichenbehaftete Fläche eines offenen Rings (x, y, x, y, …); positiv = gegen den Uhrzeigersinn. */
    public static double flaeche(double[] r) {
        double a = 0;
        int n = r.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a += r[2 * i] * r[2 * j + 1] - r[2 * j] * r[2 * i + 1];
        }
        return a / 2;
    }

    public static double[] umgedreht(double[] r) {
        int n = r.length / 2;
        double[] o = new double[r.length];
        for (int i = 0; i < n; i++) { o[2 * i] = r[2 * (n - 1 - i)]; o[2 * i + 1] = r[2 * (n - 1 - i) + 1]; }
        return o;
    }

    /** Liegt (x, y) im Ring (gerade/ungerade)? */
    public static boolean innen(double[] r, double x, double y) {
        boolean in = false;
        int n = r.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = r[2 * i], yi = r[2 * i + 1], xj = r[2 * j], yj = r[2 * j + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    /**
     * Umriss der Zellen mit maske[j * w + i] == true. Zelle (i, j) hat die Mitte (x0 + i·d, y0 − j·d) in (Ost, Nord):
     * j läuft nach Süden wie z in der Szene. Liefert Polygone mit Löchern; Ringe unter minFlaeche (m²) fallen weg.
     */
    public static List<Polygon> umriss(boolean[] maske, int w, int h, double x0, double y0, double d, double minFlaeche) {
        // Kanten: waagerecht (i,j)-(i+1,j) → 2·(j·w+i), senkrecht (i,j)-(i,j+1) → 2·(j·w+i)+1
        Map<Long, long[]> nachbar = new HashMap<>();
        List<long[]> segmente = new ArrayList<>();
        for (int j = -1; j < h; j++)
            for (int i = -1; i < w; i++) {
                boolean a = an(maske, w, h, i, j), b = an(maske, w, h, i + 1, j), c = an(maske, w, h, i + 1, j + 1), e = an(maske, w, h, i, j + 1);
                int fall = (a ? 1 : 0) | (b ? 2 : 0) | (c ? 4 : 0) | (e ? 8 : 0);
                if (fall == 0 || fall == 15) continue;
                long oben = kante(w, i, j, false), rechts = kante(w, i + 1, j, true), unten = kante(w, i, j + 1, false), links = kante(w, i, j, true);
                switch (fall) {
                    case 1: case 14: seg(segmente, links, oben); break;
                    case 2: case 13: seg(segmente, oben, rechts); break;
                    case 3: case 12: seg(segmente, links, rechts); break;
                    case 4: case 11: seg(segmente, rechts, unten); break;
                    case 6: case 9: seg(segmente, oben, unten); break;
                    case 7: case 8: seg(segmente, links, unten); break;
                    // Sattel: die beiden Innenecken bleiben getrennt (die Linien berühren sich nicht)
                    case 5: seg(segmente, links, oben); seg(segmente, rechts, unten); break;
                    case 10: seg(segmente, oben, rechts); seg(segmente, unten, links); break;
                    default: break;
                }
            }
        for (int s = 0; s < segmente.size(); s++) {
            long[] sg = segmente.get(s);
            for (int e = 0; e < 2; e++) {
                long[] l = nachbar.computeIfAbsent(sg[e], k -> new long[]{-1, -1});
                if (l[0] < 0) l[0] = s; else l[1] = s;
            }
        }
        boolean[] benutzt = new boolean[segmente.size()];
        List<double[]> ringe = new ArrayList<>();
        for (int s0 = 0; s0 < segmente.size(); s0++) {
            if (benutzt[s0]) continue;
            List<Long> kanten = new ArrayList<>();
            int s = s0;
            long start = segmente.get(s0)[0], cur = start;
            while (true) {
                benutzt[s] = true;
                long[] sg = segmente.get(s);
                long next = sg[0] == cur ? sg[1] : sg[0];
                kanten.add(cur);
                cur = next;
                if (cur == start) break;
                long[] l = nachbar.get(cur);
                int ns = (int) (l[0] == s ? l[1] : l[0]);
                if (ns < 0 || benutzt[ns]) break;
                s = ns;
            }
            double[] r = new double[kanten.size() * 2];
            for (int k = 0; k < kanten.size(); k++) {
                long id = kanten.get(k);
                long c = id >> 1;
                boolean senk = (id & 1) == 1;
                long ci = Math.floorMod(c, (long) (w + 2)) - 1, cj = Math.floorDiv(c, (long) (w + 2)) - 1;
                double x = x0 + ci * d + (senk ? 0 : d / 2), y = y0 - cj * d - (senk ? d / 2 : 0);
                r[2 * k] = x;
                r[2 * k + 1] = y;
            }
            r = ohneGerade(r);
            if (r.length >= 6 && Math.abs(flaeche(r)) >= minFlaeche) ringe.add(r);
        }
        // Verschachtelung: ein Ring ist ein Loch, wenn er in ungerade vielen anderen liegt
        int n = ringe.size();
        int[] tiefe = new int[n];
        int[] eltern = new int[n];
        Arrays.fill(eltern, -1);
        for (int a = 0; a < n; a++) {
            double[] ra = ringe.get(a);
            double best = Double.MAX_VALUE;
            for (int b = 0; b < n; b++) {
                if (a == b) continue;
                double[] rb = ringe.get(b);
                if (innen(rb, ra[0], ra[1])) {
                    tiefe[a]++;
                    double fb = Math.abs(flaeche(rb));
                    if (fb < best) { best = fb; eltern[a] = b; }
                }
            }
        }
        Map<Integer, Polygon> polys = new HashMap<>();
        List<Polygon> out = new ArrayList<>();
        for (int a = 0; a < n; a++) {
            if (tiefe[a] % 2 != 0) continue;
            Polygon p = new Polygon();
            double[] r = ringe.get(a);
            p.aussen = flaeche(r) < 0 ? umgedreht(r) : r;
            polys.put(a, p);
            out.add(p);
        }
        for (int a = 0; a < n; a++) {
            if (tiefe[a] % 2 == 0) continue;
            Polygon p = polys.get(eltern[a]);
            if (p == null) continue;
            double[] r = ringe.get(a);
            p.loecher.add(flaeche(r) > 0 ? umgedreht(r) : r);
        }
        out.sort((p, q) -> Double.compare(q.flaeche(), p.flaeche()));
        return out;
    }

    private static boolean an(boolean[] m, int w, int h, int i, int j) { return i >= 0 && j >= 0 && i < w && j < h && m[j * w + i]; }

    /** Kante zwischen Zelle (i, j) und ihrem rechten (senk = false) oder unteren (senk = true) Nachbarn; mit Rand −1. */
    private static long kante(int w, int i, int j, boolean senk) { return (((long) (j + 1) * (w + 2) + (i + 1)) << 1) | (senk ? 1 : 0); }

    private static void seg(List<long[]> l, long a, long b) { l.add(new long[]{a, b}); }

    /** Entfernt Punkte, die genau auf der Geraden ihrer Nachbarn liegen (verlustfrei), und doppelte Punkte. */
    public static double[] ohneGerade(double[] r) {
        int n = r.length / 2;
        if (n < 4) return r;
        List<Double> o = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            double ax = r[2 * a], ay = r[2 * a + 1], x = r[2 * i], y = r[2 * i + 1], bx = r[2 * b], by = r[2 * b + 1];
            double kreuz = (x - ax) * (by - ay) - (y - ay) * (bx - ax);
            if (Math.abs(kreuz) < 1e-6) continue;
            o.add(x);
            o.add(y);
        }
        double[] out = new double[o.size()];
        for (int i = 0; i < out.length; i++) out[i] = o.get(i);
        return out;
    }

    /** Douglas–Peucker für eine offene Linie (x, y, …) mit Toleranz tol (m). */
    public static double[] vereinfache(double[] l, double tol) {
        int n = l.length / 2;
        if (n <= 2) return l;
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        dp(l, 0, n - 1, tol, keep);
        int m = 0;
        for (boolean k : keep) if (k) m++;
        double[] o = new double[2 * m];
        int k = 0;
        for (int i = 0; i < n; i++) if (keep[i]) { o[k++] = l[2 * i]; o[k++] = l[2 * i + 1]; }
        return o;
    }

    private static void dp(double[] l, int a, int b, double tol, boolean[] keep) {
        // ohne Rekursion, damit lange Achsen den Stapel nicht sprengen
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{a, b});
        while (!st.isEmpty()) {
            int[] s = st.pop();
            int i0 = s[0], i1 = s[1];
            double ax = l[2 * i0], ay = l[2 * i0 + 1], bx = l[2 * i1], by = l[2 * i1 + 1];
            double best = -1;
            int bi = -1;
            for (int i = i0 + 1; i < i1; i++) {
                double dd = abstand(l[2 * i], l[2 * i + 1], ax, ay, bx, by);
                if (dd > best) { best = dd; bi = i; }
            }
            if (bi > 0 && best > tol) {
                keep[bi] = true;
                st.push(new int[]{i0, bi});
                st.push(new int[]{bi, i1});
            }
        }
    }

    /** Douglas–Peucker für einen Ring: geteilt am weitesten entfernten Punktpaar; mindestens 4 Punkte bleiben. */
    public static double[] vereinfacheRing(double[] r, double tol) {
        int n = r.length / 2;
        if (n <= 8) return r;
        int far = 0;
        double best = -1;
        for (int i = 1; i < n; i++) {
            double d = Math.hypot(r[2 * i] - r[0], r[2 * i + 1] - r[1]);
            if (d > best) { best = d; far = i; }
        }
        double[] a = new double[2 * (far + 1)], b = new double[2 * (n - far + 1)];
        System.arraycopy(r, 0, a, 0, 2 * (far + 1));
        System.arraycopy(r, 2 * far, b, 0, 2 * (n - far));
        b[b.length - 2] = r[0];
        b[b.length - 1] = r[1];
        double[] va = vereinfache(a, tol), vb = vereinfache(b, tol);
        // va endet mit far, vb beginnt mit far und endet mit 0: beides nicht doppelt nehmen
        double[] fertig = new double[va.length + vb.length - 4];
        System.arraycopy(va, 0, fertig, 0, va.length);
        System.arraycopy(vb, 2, fertig, va.length, vb.length - 4);
        if (fertig.length < 8) return r;
        return fertig;
    }

    static double abstand(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        if (l2 == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }

    /** Ist der Ring einfach (keine zwei nicht benachbarten Kanten schneiden oder berühren sich)? */
    public static boolean einfach(double[] r) {
        int n = r.length / 2;
        if (n < 3) return false;
        for (int i = 0; i < n; i++) {
            int i2 = (i + 1) % n;
            for (int j = i + 1; j < n; j++) {
                int j2 = (j + 1) % n;
                if (j == i2 || j2 == i) continue;
                if (schneidet(r[2 * i], r[2 * i + 1], r[2 * i2], r[2 * i2 + 1], r[2 * j], r[2 * j + 1], r[2 * j2], r[2 * j2 + 1])) return false;
            }
        }
        return true;
    }

    /** Schneiden oder berühren sich zwei Ringe? */
    public static boolean schneiden(double[] a, double[] b) {
        int n = a.length / 2, m = b.length / 2;
        for (int i = 0; i < n; i++) {
            int i2 = (i + 1) % n;
            double minx = Math.min(a[2 * i], a[2 * i2]), maxx = Math.max(a[2 * i], a[2 * i2]);
            for (int j = 0; j < m; j++) {
                int j2 = (j + 1) % m;
                if (Math.max(b[2 * j], b[2 * j2]) < minx || Math.min(b[2 * j], b[2 * j2]) > maxx) continue;
                if (schneidet(a[2 * i], a[2 * i + 1], a[2 * i2], a[2 * i2 + 1], b[2 * j], b[2 * j + 1], b[2 * j2], b[2 * j2 + 1])) return true;
            }
        }
        return false;
    }

    static boolean schneidet(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
        double d1 = kreuz(cx, cy, dx, dy, ax, ay), d2 = kreuz(cx, cy, dx, dy, bx, by), d3 = kreuz(ax, ay, bx, by, cx, cy), d4 = kreuz(ax, ay, bx, by, dx, dy);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;
        return (d1 == 0 && auf(cx, cy, dx, dy, ax, ay)) || (d2 == 0 && auf(cx, cy, dx, dy, bx, by))
                || (d3 == 0 && auf(ax, ay, bx, by, cx, cy)) || (d4 == 0 && auf(ax, ay, bx, by, dx, dy));
    }

    private static double kreuz(double ax, double ay, double bx, double by, double px, double py) { return (bx - ax) * (py - ay) - (by - ay) * (px - ax); }

    private static boolean auf(double ax, double ay, double bx, double by, double px, double py) {
        return Math.min(ax, bx) <= px && px <= Math.max(ax, bx) && Math.min(ay, by) <= py && py <= Math.max(ay, by);
    }

    /**
     * Vereinfacht ein Polygon mit Toleranz tol, aber nur so weit, wie es gültig bleibt: jeder Ring einfach, kein Loch
     * schneidet den Außenring oder ein anderes Loch, Orientierung bleibt. Sonst bleibt der unvereinfachte Ring.
     */
    public static Polygon vereinfache(Polygon p, double tol) {
        Polygon o = new Polygon();
        o.aussen = sicher(p.aussen, tol, true);
        for (double[] h : p.loecher) {
            double[] v = sicher(h, tol, false);
            boolean gut = !schneiden(v, o.aussen);
            for (double[] g : o.loecher) gut &= !schneiden(v, g);
            if (!gut) v = h;
            o.loecher.add(v);
        }
        return o;
    }

    private static double[] sicher(double[] r, double tol, boolean ccw) {
        double[] v = vereinfacheRing(r, tol);
        if (v.length >= 8 && einfach(v) && (flaeche(v) > 0) == ccw) return v;
        return r;
    }
}
