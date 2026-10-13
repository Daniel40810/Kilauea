package com.dan.kilauea.lava;

import java.util.SplittableRandom;

/**
 * Der Lavasee im Halemaʻumaʻu als eigenes Modell, ohne Grafik (Phase 7): Krustenplatten treiben auf der
 * Oberfläche von einer Quelle, wo frische Lava aufquillt, zu einer Senke am Rand, wo sie abtauchen. Dazwischen
 * reißen Nähte auf, in denen die Glut zu sehen ist; wo Platten auseinanderdriften, sind die Nähte breit und
 * hell, wo sie sich stauen, schmal. Der Spiegel folgt dem Druck im Halemaʻumaʻu-Speicher und hebt und senkt
 * sich zusätzlich in Gaskolben-Zyklen. Spritzer entstehen an der Senke, am Ufer und beim Absacken.
 * <ul>
 * <li>Größe [G]: Ellipse von rund 460 × 320 m mit unregelmäßigem Ufer; die Seen 2008–18 (Overlook) und
 * 2020–23 lagen zwischen rund 200 m und über 1 km [L].</li>
 * <li>Oberfläche [L, Patrick u. a. 2016 für den Overlook-See]: Platten treiben mit 0,1–0,5 m/s; hier 0,15 m/s
 * in der Mitte [A].</li>
 * <li>Platten [G]: 9–15 m Halbmesser (Schollen von rund 20–30 m), höchstens {@link #MAX} gleichzeitig.</li>
 * <li>Gaskolben [L, beobachtet am Overlook-See]: der Spiegel steigt über Minuten um einige Meter, die
 * Oberfläche wird ruhig, dann entweicht das Gas, der Spiegel fällt binnen etwa einer Minute und es spritzt.
 * Abstände und Höhen [G].</li>
 * </ul>
 * Fester Schritt von einer Sekunde, fester Keim: gleicher Ablauf. Keine Allokation im Schritt.
 */
public final class LavaLake {
    public static final int MAX = 256;
    /** Halbmesser der Platten (m) [G]. */
    public static final double PLATE_MIN = 9, PLATE_MAX = 15;
    /** Strömung in der Mitte (m/s) [A]. */
    public static final double U = 0.15;
    /** Umkreis um Quelle und Senke, in dem die Lava auf- und abquillt (m) [G]. */
    public static final double R_UP = 70;
    /** Spiegel über dem Kraterboden (m): Ruhe, größte Höhe; Anstieg je MPa im Speicher [G]. */
    public static final double LEVEL_REST = 20, LEVEL_MAX = 60, LEVEL_PER_MPA = 6, PRESSURE_REF = 4.5e6;
    /** Zeitkonstante, mit der der Spiegel dem Druck folgt (s) [G]. */
    public static final double LEVEL_TAU = 3 * 3600;

    public final double cx, cz, a, b, rot;
    private final double cosR, sinR;
    /** Quelle und Senke in Seekoordinaten (m). */
    public final double srcU, srcV, snkU, snkV;

    private final double[] pu = new double[MAX], pv = new double[MAX], pr = new double[MAX], page = new double[MAX];
    private final double[] vu = new double[MAX], vv = new double[MAX], wu = new double[MAX], wv = new double[MAX];
    private int n;
    private long born, sunk;
    private final SplittableRandom rnd;
    private double time, rest;

    // Spiegel
    private double level = LEVEL_REST, target = LEVEL_REST, piston, pistonPeak;
    private double nextPiston, pistonStart = -1, pistonRise;
    private int pistons;

    // Spritzer: Ringpuffer
    public static final int SPATTER = 64;
    private final double[] sx = new double[SPATTER], sz = new double[SPATTER], st = new double[SPATTER], ss = new double[SPATTER];
    private int sHead, sCount;
    private long spatters;

    /**
     * @param cx  Mitte in der Szene (m, Osten)
     * @param cz  Mitte in der Szene (m, Süden)
     * @param a   halbe Länge (m)
     * @param b   halbe Breite (m)
     * @param rotDeg Drehung der Längsachse gegen Osten (Grad, im Uhrzeigersinn von oben)
     */
    public LavaLake(double cx, double cz, double a, double b, double rotDeg, long seed) {
        this.cx = cx;
        this.cz = cz;
        this.a = a;
        this.b = b;
        this.rot = Math.toRadians(rotDeg);
        cosR = Math.cos(rot);
        sinR = Math.sin(rot);
        srcU = -0.80 * a;
        srcV = 0.10 * b;
        snkU = 0.84 * a;
        snkV = -0.12 * b;
        rnd = new SplittableRandom(seed);
        nextPiston = 1200 + rnd.nextDouble() * 1800;
        // Anfangszustand: den See locker mit Platten füllen, dann eine halbe Stunde einschwingen
        for (int k = 0; k < 4000 && n < MAX; k++) {
            double u = (rnd.nextDouble() * 2 - 1) * a, v = (rnd.nextDouble() * 2 - 1) * b;
            if (shape(u, v) > 0.9) continue;
            double r = PLATE_MIN + (PLATE_MAX - PLATE_MIN) * rnd.nextDouble();
            boolean free = true;
            for (int i = 0; i < n && free; i++) if (Math.hypot(pu[i] - u, pv[i] - v) < 0.9 * (r + pr[i])) free = false;
            if (!free) continue;
            pu[n] = u;
            pv[n] = v;
            pr[n] = r;
            page[n] = rnd.nextDouble() * 1800;
            n++;
        }
        for (int s = 0; s < 1800; s++) tick(1);
        time = 0;
        born = sunk = 0;
        spatters = 0;
        sCount = 0;
    }

    /** Der See im Halemaʻumaʻu, am Schlot von {@link VentSite#HALEMAUMAU} [Lage G]. */
    public static LavaLake halemaumau(long seed) {
        return new LavaLake(VentSite.HALEMAUMAU.x(), VentSite.HALEMAUMAU.z(), 230, 160, -20, seed);
    }

    // ------------------------------------------------------------------ Form

    /** Form des Ufers: &lt; 1 innen, 1 auf dem Ufer (Ellipse mit Buchten). */
    public double shape(double u, double v) {
        double th = Math.atan2(v / b, u / a);
        double wob = 1 + 0.05 * Math.sin(3 * th + 1.1) + 0.03 * Math.sin(7 * th + 2.3);
        return Math.sqrt((u / a) * (u / a) + (v / b) * (v / b)) / wob;
    }

    /** Seekoordinaten zu Szene: x, z. */
    public double toX(double u, double v) { return cx + u * cosR - v * sinR; }
    public double toZ(double u, double v) { return cz + u * sinR + v * cosR; }
    /** Szene zu Seekoordinaten. */
    public double toU(double x, double z) { return (x - cx) * cosR + (z - cz) * sinR; }
    public double toV(double x, double z) { return -(x - cx) * sinR + (z - cz) * cosR; }

    /** Liegt (x, z) auf dem See? */
    public boolean contains(double x, double z) { return shape(toU(x, z), toV(x, z)) < 1; }

    // ------------------------------------------------------------------ Ablauf

    /** Druck im Halemaʻumaʻu-Speicher (Pa): der Spiegel folgt ihm. */
    public void pressure(double p) {
        target = Math.max(0, Math.min(LEVEL_MAX - 8, LEVEL_REST + LEVEL_PER_MPA * (p - PRESSURE_REF) / 1e6));
    }

    /** Rechnet dt Sekunden weiter, in festen Schritten von 1 s. */
    public void step(double dt) {
        rest += dt;
        while (rest >= 1) {
            tick(1);
            rest -= 1;
        }
    }

    private void tick(double h) {
        // Strömung und Abstoßung
        for (int i = 0; i < n; i++) {
            flow(pu[i], pv[i]);
            vu[i] = wu[i] = fu;
            vv[i] = wv[i] = fv;
        }
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double du = pu[j] - pu[i], dv = pv[j] - pv[i];
                double d = Math.sqrt(du * du + dv * dv), min = 0.92 * (pr[i] + pr[j]);
                if (d >= min || d < 1e-6) continue;
                double push = 0.02 * (min - d) / d;   // m/s je m Überlappung
                vu[i] -= du * push;
                vv[i] -= dv * push;
                vu[j] += du * push;
                vv[j] += dv * push;
            }
        double quiet = pistonStart >= 0 && time - pistonStart < pistonRise ? 0.6 : 1;   // beim Steigen ruhiger
        for (int i = 0; i < n; i++) {
            pu[i] += vu[i] * h * quiet;
            pv[i] += vv[i] * h * quiet;
            page[i] += h;
            // am Ufer halten
            double s = shape(pu[i], pv[i]);
            if (s > 0.97) {
                pu[i] *= 0.97 / s;
                pv[i] *= 0.97 / s;
            }
        }
        // Senke: Platten tauchen ab, es spritzt
        for (int i = n - 1; i >= 0; i--) {
            if (Math.hypot(pu[i] - snkU, pv[i] - snkV) < 0.6 * pr[i]) {
                spatter(pu[i], pv[i], 0.6 + 0.4 * rnd.nextDouble());
                remove(i);
                sunk++;
            }
        }
        // Quelle: neue Platten, wo im Aufquellbereich Platz ist (bis zu drei Versuche je Schritt)
        for (int tries = 0; tries < 3 && n < MAX; tries++) {
            double r = PLATE_MIN + (PLATE_MAX - PLATE_MIN) * rnd.nextDouble();
            double ang = rnd.nextDouble() * Math.PI * 2, rad = 0.6 * R_UP * Math.sqrt(rnd.nextDouble());
            double ju = srcU + Math.cos(ang) * rad, jv = srcV + Math.sin(ang) * rad;
            if (shape(ju, jv) > 0.92) continue;
            boolean free = true;
            for (int i = 0; i < n && free; i++) if (Math.hypot(pu[i] - ju, pv[i] - jv) < 0.85 * (r + pr[i])) free = false;
            if (free) {
                pu[n] = ju;
                pv[n] = jv;
                pr[n] = r;
                page[n] = 0;
                n++;
                born++;
            }
        }
        // Spritzer am Ufer: rund einer je Minute
        if (rnd.nextDouble() < h / 60) {
            double th = rnd.nextDouble() * Math.PI * 2;
            double u = Math.cos(th) * a, v = Math.sin(th) * b;
            double s = shape(u, v);
            spatter(u / s * 0.98, v / s * 0.98, 0.3 + 0.4 * rnd.nextDouble());
        }
        level(h);
        time += h;
    }

    private double fu, fv;

    /** Oberflächenströmung am Punkt (u, v): Quelle-Senke-Strömung, am Ufer entlanggeführt. */
    private void flow(double u, double v) {
        double au = u - srcU, av = v - srcV, ku = u - snkU, kv = v - snkV;
        double a2 = au * au + av * av + 400, k2 = ku * ku + kv * kv + 400;
        double wu = au / a2 - ku / k2, wv = av / a2 - kv / k2;
        double len = Math.sqrt(wu * wu + wv * wv);
        // Stärke so, dass in der Mitte U herauskommt
        double mu = -srcU / (srcU * srcU + srcV * srcV + 400) + snkU / (snkU * snkU + snkV * snkV + 400);
        double ref = Math.abs(mu);
        double sp = U * Math.max(0.25, Math.min(2.5, len / ref));
        // Aufquellen und Abtauchen: im Umkreis R_UP wächst das Tempo mit dem Abstand (Staupunkt), so
        // spreizt sich die Oberfläche an der Quelle und staut sich an der Senke [A]
        double ds = Math.sqrt(au * au + av * av), dk = Math.sqrt(ku * ku + kv * kv);
        sp *= Math.max(0.12, Math.min(1, ds / R_UP)) * Math.max(0.2, Math.min(1, dk / R_UP));
        fu = len > 1e-12 ? wu / len * sp : 0;
        fv = len > 1e-12 ? wv / len * sp : 0;
        // am Ufer: Anteil nach außen wegnehmen
        double s = shape(u, v);
        if (s > 0.8) {
            double nu = u / (a * a), nv = v / (b * b), nl = Math.sqrt(nu * nu + nv * nv);
            nu /= nl;
            nv /= nl;
            double out = fu * nu + fv * nv;
            if (out > 0) {
                double w = Math.min(1, (s - 0.8) / 0.15);
                fu -= out * nu * w;
                fv -= out * nv * w;
            }
        }
    }

    private void remove(int i) {
        n--;
        pu[i] = pu[n];
        pv[i] = pv[n];
        pr[i] = pr[n];
        page[i] = page[n];
        vu[i] = vu[n];
        vv[i] = vv[n];
        wu[i] = wu[n];
        wv[i] = wv[n];
    }

    private void spatter(double u, double v, double strength) {
        sx[sHead] = toX(u, v);
        sz[sHead] = toZ(u, v);
        st[sHead] = time;
        ss[sHead] = strength;
        sHead = (sHead + 1) % SPATTER;
        sCount = Math.min(SPATTER, sCount + 1);
        spatters++;
    }

    private void level(double h) {
        level += (target - level) * Math.min(1, h / LEVEL_TAU);
        // Gaskolben: steigen über pistonRise, dann binnen einer Minute zurück
        if (pistonStart < 0 && time >= nextPiston) {
            pistonStart = time;
            pistonRise = 480 + rnd.nextDouble() * 420;
            pistonPeak = 1.5 + rnd.nextDouble() * 3.5;
            pistons++;
        }
        if (pistonStart >= 0) {
            double t = time - pistonStart;
            if (t < pistonRise) piston = pistonPeak * t / pistonRise;
            else if (t < pistonRise + 60) {
                piston = pistonPeak * (1 - (t - pistonRise) / 60);
                if (rnd.nextDouble() < 0.25) {
                    double th = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * 0.9;
                    spatter(Math.cos(th) * a * r, Math.sin(th) * b * r, 0.8 + 0.4 * rnd.nextDouble());
                }
            } else {
                piston = 0;
                pistonStart = -1;
                nextPiston = time + 1200 + rnd.nextDouble() * 1800;
            }
        }
    }

    // ------------------------------------------------------------------ Grube (Phase 8)

    /** Tiefe der Grube, in der der See liegt, unter dem Kraterboden (m) [G]. */
    public static final double PIT_DEPTH = 45;
    /** Höhe des Kraterbodens am See (m ü. M.); NaN, solange das Gelände nicht steht. */
    public volatile double rimY = Double.NaN;

    /** Höhe des Spiegels (m ü. M.): Boden der Grube plus Spiegel, höchstens 2 m unter dem Rand. */
    public double surfaceY() { return rimY - PIT_DEPTH + Math.min(level(), PIT_DEPTH - 2) - drop; }

    /**
     * Wie weit der Spiegel über das Spiel des Drucks hinaus gesunken ist (m), Phase 12: 2018 lief der See in
     * wenigen Tagen leer, als das Magma zur Ostriftzone abfloss [L]. 0 = normal.
     */
    public volatile double drop;

    /**
     * Das Gelände mit der Grube: innen der Boden der Grube, am Ufer eine steile Wand (über 7 % der Weite)
     * hinauf zum Kraterboden. Gleiche Rechnung wie {@code lakePit} in terrain_height.glsl.
     */
    public double pit(double x, double z, double ground) {
        double r = rimY;
        if (Double.isNaN(r)) return ground;
        double s = shape(toU(x, z), toV(x, z));
        if (s >= PIT_OUT) return ground;
        double t = Math.max(0, Math.min(1, (s - PIT_IN) / (PIT_OUT - PIT_IN)));
        t = t * t * (3 - 2 * t);
        return Math.min(ground, r - PIT_DEPTH * (1 - t));
    }

    static final double PIT_IN = 0.985, PIT_OUT = 1.06;

    // ------------------------------------------------------------------ Abfragen

    public double time() { return time; }
    public int plates() { return n; }
    public long born() { return born; }
    public long sunk() { return sunk; }
    /** Spiegel über dem Kraterboden (m), mit Gaskolben. */
    public double level() { return Math.min(LEVEL_MAX, level + piston); }
    public double levelTarget() { return target; }
    public boolean pistonRising() { return pistonStart >= 0 && time - pistonStart < pistonRise; }
    public int pistons() { return pistons; }
    public long spatters() { return spatters; }

    /** Platte i: Lage in der Szene, Größe (m), Alter (s), Geschwindigkeit (m/s, Seekoordinaten). */
    public double plateX(int i) { return toX(pu[i], pv[i]); }
    public double plateZ(int i) { return toZ(pu[i], pv[i]); }
    public double plateU(int i) { return pu[i]; }
    public double plateV(int i) { return pv[i]; }
    public double plateRadius(int i) { return pr[i]; }
    public double plateAge(int i) { return page[i]; }
    public double plateVU(int i) { return vu[i]; }
    public double plateVV(int i) { return vv[i]; }

    /** Spritzer der letzten Zeit: k = 0 ist der jüngste. */
    public int spatterCount() { return sCount; }
    public double spatterX(int k) { return sx[(sHead - 1 - k + SPATTER) % SPATTER]; }
    public double spatterZ(int k) { return sz[(sHead - 1 - k + SPATTER) % SPATTER]; }
    public double spatterTime(int k) { return st[(sHead - 1 - k + SPATTER) % SPATTER]; }
    public double spatterStrength(int k) { return ss[(sHead - 1 - k + SPATTER) % SPATTER]; }

    /**
     * Öffnung der Naht zwischen Nachbarplatten i und j (m/s) nach der Strömung darunter: positiv, wo sie die
     * Platten auseinanderzieht (an der Quelle), negativ, wo sie sie zusammenschiebt (an der Senke); NaN, wenn
     * sie keine Nachbarn sind. Das Abstoßen der Platten untereinander zählt nicht mit.
     */
    public double seamOpening(int i, int j) {
        double du = pu[j] - pu[i], dv = pv[j] - pv[i], d = Math.hypot(du, dv);
        if (d > 1.25 * (pr[i] + pr[j]) || d < 1e-6) return Double.NaN;
        return ((wu[j] - wu[i]) * du + (wv[j] - wv[i]) * dv) / d;
    }

    /** Prüfsumme über den Zustand (für „gleicher Ablauf“). */
    public long fingerprint() {
        long x = 1469598103934665603L;
        for (int i = 0; i < n; i++) {
            x = (x ^ Double.doubleToLongBits(pu[i])) * 1099511628211L;
            x = (x ^ Double.doubleToLongBits(pv[i])) * 1099511628211L;
        }
        return (x ^ Double.doubleToLongBits(level())) * 1099511628211L;
    }
}
