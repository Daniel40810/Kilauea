package com.dan.kilauea.life;

import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Nēnē (Hawaiigans) auf den offenen, grasigen Flächen am Gipfel und an der Chain of Craters Road.
 * <ul>
 * <li>Brutzeit September bis Mai: Paare, das Männchen hält Wache; ab Januar mit Gösseln, die bis zur
 * nächsten Brutzeit bei den Eltern bleiben [L NPS, Wikipedia]. Juni bis August sammeln sie sich zu
 * kleinen Trupps [G].</li>
 * <li>Am Parkplatz Uēkahuna brütete im Winter 2024/25 ein Paar; viele Nēnē stehen in der Brutzeit an
 * oder auf den Parkstraßen, Autos sind die häufigste Todesursache erwachsener Nēnē im Park [L NPS].
 * Die übrigen Plätze und ihre Größe sind gestaltet [G].</li>
 * <li>Verhalten: grasen mit gesenktem Kopf, sichern mit erhobenem Hals, gehen ein Stück, fliegen selten
 * als Trupp zu einem anderen Platz; leise Rufe, im Flug häufiger [G].</li>
 * </ul>
 */
public final class Nene {
    /** Plätze: Breite, Länge, Halbmesser (m), 1 = an der Straße. */
    static final double[][] SITES = {
            {19.4204, -155.2880, 70, 1},     // Uēkahuna, Parkplatz und Kraterrand
            {19.4293, -155.2578, 110, 1},    // Besucherzentrum, Rasen
            {19.4180, -155.2502, 70, 1},     // Kīlauea Iki, Aussicht
            {19.4110, -155.2532, 90, 1},     // Devastation Trail, Parkplatz am Puʻu Puaʻi
            {19.3790, -155.2245, 140, 1},    // Chain of Craters Road an der Hilina Pali Road
            {19.3292, -155.1660, 140, 1},    // Kealakomo
            {19.3018, -155.1375, 130, 1},    // unter der Hōlei Pali
            {19.4400, -155.3040, 160, 0}};   // Kīpukapuaulu
    static final String[] SITE_NAMES = {"Uēkahuna", "Besucherzentrum", "Kīlauea Iki", "Devastation Trail", "Hilina Pali Road", "Kealakomo", "Hōlei Pali", "Kīpukapuaulu"};

    static final int GRAZE = 0, ALERT = 1, WALK = 2, FLY = 3, ROOST = 4;

    static final class Bird {
        int site, state;
        double x, z, y, heading, timer, neck, gait, phase, scale = 1;
        double tx, tz;
        boolean sentinel;
        // Flug
        double fx0, fz0, fx1, fz1, ft, fdur, fh;
    }

    private final Life life;
    private final SplittableRandom rnd;
    final List<Bird> birds = new ArrayList<>();
    private int season = -1;
    private final double[] siteX = new double[SITES.length], siteZ = new double[SITES.length];
    /** Nächster Punkt der Straßenachse je Platz (NaN ohne Straße in der Nähe) und ihre Richtung. */
    private final double[] roadX = new double[SITES.length], roadZ = new double[SITES.length], roadDx = new double[SITES.length], roadDz = new double[SITES.length];
    private boolean roadsKnown;
    private static final float[] TINT = {1, 1, 1};

    Nene(Life life, long seed) {
        this.life = life;
        rnd = new SplittableRandom(seed);
        for (int i = 0; i < SITES.length; i++) { siteX[i] = Geo.x(SITES[i][0], SITES[i][1]); siteZ[i] = Geo.z(SITES[i][0]); }
    }

    /** 0 Brutzeit ohne Gössel (Sep–Dez), 1 Brutzeit mit Gösseln (Jan–Mai), 2 Trupps (Jun–Aug). */
    public static int season(int day) { return day >= 244 ? 0 : day <= 151 ? 1 : 2; }

    public static boolean nesting(int day) { return season(day) != 2; }

    private void populate(int s) {
        birds.clear();
        for (int i = 0; i < SITES.length; i++) {
            int adults = s == 2 ? (i % 3 == 0 ? 6 + rnd.nextInt(5) : rnd.nextInt(3)) : 2;
            int young = s == 1 ? 1 + rnd.nextInt(3) : 0;
            for (int k = 0; k < adults + young; k++) {
                Bird b = new Bird();
                b.site = i;
                b.scale = k < adults ? 0.95 + 0.1 * rnd.nextDouble() : 0.55 + 0.1 * rnd.nextDouble();
                b.sentinel = k == 0;
                double a = rnd.nextDouble() * 2 * Math.PI, r = SITES[i][2] * 0.6 * Math.sqrt(rnd.nextDouble());
                b.x = siteX[i] + Math.cos(a) * r;
                b.z = siteZ[i] + Math.sin(a) * r;
                if (k > 0 && !birds.isEmpty()) {   // nah beieinander
                    Bird lead = birds.get(birds.size() - 1);
                    b.x = lead.x + rnd.nextDouble(-3, 3);
                    b.z = lead.z + rnd.nextDouble(-3, 3);
                }
                b.heading = rnd.nextDouble() * 2 * Math.PI;
                b.state = GRAZE;
                b.timer = rnd.nextDouble(1, 10);
                b.neck = 1.6;
                b.phase = rnd.nextDouble() * 10;
                settle(b);
                birds.add(b);
            }
        }
    }

    /** Auf festen Boden über dem Meer stellen; sonst Richtung Platzmitte. */
    private void settle(Bird b) {
        for (int k = 0; k < 6; k++) {
            double h = Ground.height(b.x, b.z);
            if (h > 2) { b.y = h; return; }
            b.x += (siteX[b.site] - b.x) * 0.5;
            b.z += (siteZ[b.site] - b.z) * 0.5;
        }
        b.y = Ground.height(b.x, b.z);
    }

    /** Je Platz den nächsten Punkt der Parkstraße suchen (einmal, sobald die Straßen gebaut sind). */
    private void findRoads() {
        com.dan.road.Network n = com.dan.kilauea.terrain.ParkRoads.network();
        if (n == null) return;
        for (int i = 0; i < SITES.length; i++) {
            roadX[i] = Double.NaN;
            if (SITES[i][3] < 0.5) continue;
            double best = SITES[i][2] * 1.3;
            for (com.dan.road.Way wy : n.ways)
                for (int k = 0; k < wy.n; k++) {
                    double d = Math.hypot(wy.x[k] - siteX[i], wy.z[k] - siteZ[i]);
                    if (d < best) { best = d; roadX[i] = wy.x[k]; roadZ[i] = wy.z[k]; roadDx[i] = wy.tx[k]; roadDz[i] = wy.tz[k]; }
                }
        }
        roadsKnown = true;
    }

    void update(double dt, World w) {
        if (!roadsKnown) findRoads();
        int s = season(w.day);
        if (s != season) { season = s; populate(s); }
        boolean day = w.daylight();
        // Trupp fliegt auf, wenn sein Platz gesperrt ist (Ausbruch) oder selten von selbst [G]
        for (int i = 0; i < SITES.length; i++) {
            boolean closed = w.closed(siteX[i], siteZ[i], 150);
            if (!day) continue;
            if (closed || rnd.nextDouble() < dt / 1500.0) takeOff(i, w);
        }
        for (Bird b : birds) {
            step(b, dt, w, day);
            draw(b);
        }
    }

    private void takeOff(int from, World w) {
        int to = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < SITES.length; i++) {
            if (i == from || w.closed(siteX[i], siteZ[i], 300)) continue;
            double d = Math.hypot(siteX[i] - siteX[from], siteZ[i] - siteZ[from]) * (0.7 + 0.6 * rnd.nextDouble());
            if (d < best) { best = d; to = i; }
        }
        if (to < 0 || best > 9000) return;
        for (Bird b : birds) {
            if (b.site != from || b.state == FLY) continue;
            b.state = FLY;
            b.fx0 = b.x; b.fz0 = b.z;
            double a = rnd.nextDouble() * 2 * Math.PI, r = SITES[to][2] * 0.5 * Math.sqrt(rnd.nextDouble());
            b.fx1 = siteX[to] + Math.cos(a) * r;
            b.fz1 = siteZ[to] + Math.sin(a) * r;
            b.ft = -rnd.nextDouble(0, 1.5);   // nicht alle im selben Augenblick
            b.fdur = Math.max(8, Math.hypot(b.fx1 - b.fx0, b.fz1 - b.fz0) / 17.0);   // rund 17 m/s [G]
            b.fh = 25 + 30 * rnd.nextDouble();
            b.site = to;
            life.call(Life.C_NENE_FLIGHT, b.x, b.y + 0.4, b.z);
        }
    }

    private void step(Bird b, double dt, World w, boolean day) {
        b.phase += dt;
        if (b.state == FLY) {
            b.ft += dt;
            if (b.ft < 0) return;
            double f = Math.min(1, b.ft / b.fdur);
            double e = f * f * (3 - 2 * f);
            double nx = b.fx0 + (b.fx1 - b.fx0) * e, nz = b.fz0 + (b.fz1 - b.fz0) * e;
            double dx = nx - b.x, dz = nz - b.z;
            if (dx * dx + dz * dz > 1e-6) b.heading = Math.atan2(dx, -dz);
            b.x = nx; b.z = nz;
            double g = Ground.height(nx, nz);
            b.y = Math.max(g + 0.2, g + b.fh * Math.sin(Math.PI * f) + (1 - f) * 0 + 2 * Math.min(1, f * 6));
            if (rnd.nextDouble() < dt / 2.5) life.call(Life.C_NENE_FLIGHT, b.x, b.y, b.z);
            if (f >= 1) {
                b.state = GRAZE;
                b.timer = rnd.nextDouble(3, 12);
                settle(b);
            }
            return;
        }
        if (!day) {
            b.state = ROOST;
            b.neck += (0.25 - b.neck) * Math.min(1, dt * 2);
            b.gait = 0;
            return;
        }
        if (b.state == ROOST) { b.state = GRAZE; b.timer = rnd.nextDouble(2, 8); }
        b.timer -= dt;
        double targetNeck = switch (b.state) { case GRAZE -> 1.65; case WALK -> 0.35; default -> -0.05; };
        if (b.sentinel && b.state == GRAZE) targetNeck = 1.2;
        b.neck += (targetNeck - b.neck) * Math.min(1, dt * 3);
        b.gait += ((b.state == WALK ? 1 : b.state == GRAZE ? 0.15 : 0) - b.gait) * Math.min(1, dt * 4);
        if (b.state == WALK) {
            double dx = b.tx - b.x, dz = b.tz - b.z, d = Math.hypot(dx, dz);
            if (d < 0.3 || b.timer < 0) { b.state = GRAZE; b.timer = rnd.nextDouble(4, 14); }
            else {
                double v = 0.35 * b.scale;   // gemächlich [G]
                b.x += dx / d * v * dt;
                b.z += dz / d * v * dt;
                double want = Math.atan2(dx, -dz);
                b.heading += angle(want - b.heading) * Math.min(1, dt * 4);
                double h = Ground.height(b.x, b.z);
                if (h < 1.5) { b.x -= dx / d * v * dt * 2; b.z -= dz / d * v * dt * 2; b.state = GRAZE; }
                else b.y = h;
            }
        } else if (b.state == GRAZE) {
            // kleine Schritte beim Grasen
            if (rnd.nextDouble() < dt / 2.0) {
                b.heading += rnd.nextDouble(-0.6, 0.6);
                b.x += Math.sin(b.heading) * 0.12;
                b.z -= Math.cos(b.heading) * 0.12;
                b.y = Ground.height(b.x, b.z);
            }
            if (b.timer < 0) {
                if (rnd.nextDouble() < 0.4) { b.state = ALERT; b.timer = rnd.nextDouble(1, 4); }
                else walkTo(b);
            }
            if (rnd.nextDouble() < dt / 45.0) life.call(Life.C_NENE, b.x, b.y + 0.4, b.z);
        } else if (b.state == ALERT) {
            if (b.timer < 0) { if (rnd.nextDouble() < 0.5) walkTo(b); else { b.state = GRAZE; b.timer = rnd.nextDouble(3, 12); } }
            if (rnd.nextDouble() < dt / 20.0) life.call(Life.C_NENE, b.x, b.y + 0.4, b.z);
        }
    }

    private void walkTo(Bird b) {
        double r = SITES[b.site][2];
        double a = rnd.nextDouble() * 2 * Math.PI, d = r * Math.sqrt(rnd.nextDouble());
        b.tx = siteX[b.site] + Math.cos(a) * d;
        b.tz = siteZ[b.site] + Math.sin(a) * d;
        // an Plätzen an der Straße geht ein Teil der Wege über die Fahrbahn auf die andere Seite [G nach L NPS]
        int si = b.site;
        if (!Double.isNaN(roadX[si]) && rnd.nextDouble() < 0.3) {
            double l = Math.hypot(roadDx[si], roadDz[si]) + 1e-9, ux = -roadDz[si] / l, uz = roadDx[si] / l;
            double side = ((b.x - roadX[si]) * ux + (b.z - roadZ[si]) * uz) > 0 ? -1 : 1;
            double along = rnd.nextDouble(-15, 15);
            b.tx = roadX[si] + roadDx[si] / l * along + ux * side * (6 + 6 * rnd.nextDouble());
            b.tz = roadZ[si] + roadDz[si] / l * along + uz * side * (6 + 6 * rnd.nextDouble());
        }
        // Junge folgen dem nächsten Erwachsenen
        if (b.scale < 0.8) {
            Bird lead = null;
            double best = 1e9;
            for (Bird o : birds) if (o != b && o.site == b.site && o.scale > 0.8) {
                double dd = Math.hypot(o.x - b.x, o.z - b.z);
                if (dd < best) { best = dd; lead = o; }
            }
            if (lead != null) { b.tx = lead.x + rnd.nextDouble(-1.5, 1.5); b.tz = lead.z + rnd.nextDouble(-1.5, 1.5); }
        }
        b.state = WALK;
        b.timer = 60;
    }

    private void draw(Bird b) {
        if (b.state == FLY && b.ft >= 0) {
            double f = b.ft / Math.max(1e-6, b.fdur);
            double climb = f < 0.2 ? 0.25 : f > 0.8 ? -0.2 : 0;
            double flap = 1.15 * Math.sin(b.phase * 2 * Math.PI * 3.2);   // rund 3 Schläge je Sekunde [G]
            life.put(Life.K_NENE_FLY, b.x, b.y, b.z, yaw(b.heading), b.scale, climb, 0, 0, TINT, flap, 0, 0, 0);
        } else {
            double walk = b.phase * 2 * Math.PI * 1.6;
            life.put(Life.K_NENE, b.x, b.y, b.z, yaw(b.heading), b.scale, 0, 0, walk, TINT, 0, b.gait, b.neck, 0);
        }
    }

    /** Kompassrichtung (0 Nord, im Uhrzeigersinn) in den Gierwinkel des Netzes. */
    static double yaw(double heading) { return Path.yaw(Math.sin(heading), -Math.cos(heading)); }

    static double angle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** Steht eine Nēnē im Umkreis r um (x, z)? (Autos halten an.) */
    public boolean near(double x, double z, double r) {
        for (Bird b : birds) {
            if (b.state == FLY) continue;
            double dx = b.x - x, dz = b.z - z;
            if (dx * dx + dz * dz < r * r) return true;
        }
        return false;
    }

    /** Zahl der Vögel (für Prüfung). */
    public int size() { return birds.size(); }

    /** Lage aller Vögel: x, y, z, Zustand (Prüfung). */
    public List<double[]> positions() {
        List<double[]> l = new ArrayList<>();
        for (Bird b : birds) l.add(new double[]{b.x, b.y, b.z, b.state, b.site, b.scale});
        return l;
    }

    public double siteX(int i) { return siteX[i]; }
    public double siteZ(int i) { return siteZ[i]; }
}
