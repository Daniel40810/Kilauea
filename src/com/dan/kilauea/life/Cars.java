package com.dan.kilauea.life;

import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.ParkRoads;
import com.dan.road.Way;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Autos auf dem Crater Rim Drive und der Chain of Craters Road. Sie fahren auf ihrer Spur rechts,
 * bremsen vor Kurven (Kurvengeschwindigkeit des Straßenpakets) und vor dem Vordermann, halten an
 * Aussichtspunkten, kehren an den Enden um und halten an, wenn eine Nēnē auf der Fahrbahn steht —
 * Autos sind im Park die häufigste Todesursache erwachsener Nēnē [L NPS]. Vor einer Sperrzone wenden sie.
 * Nachts mit Licht. Zahl nach der Tageszeit, Tempo höchstens rund 15 m/s, Farben gestaltet [G].
 */
public final class Cars {
    public static final double VMAX = 15.6, LANE = 1.6, PARK = 4.7, LENGTH = 5.0;

    static final class Car {
        int way, dir, type, spot = -1;
        double s, v, vmax, u, timer;
        boolean parked, waiting;
        float[] paint;
        double x, y, z, vx, vz;
    }

    /** Parkplätze an Aussichten: Breite, Länge. */
    static final double[][] SPOTS = {
            {19.4206, -155.2878}, {19.4296, -155.2585}, {19.4192, -155.2498}, {19.4108, -155.2528}, {19.3965, -155.2660},
            {19.3932, -155.2480}, {19.3686, -155.2220}, {19.3740, -155.2090}, {19.3292, -155.1660}, {19.2970, -155.0950}};
    static final float[][] PAINT = {Life.rgb(0.62, 0.62, 0.60), Life.rgb(0.62, 0.62, 0.60), Life.rgb(0.30, 0.30, 0.31), Life.rgb(0.30, 0.30, 0.31),
            Life.rgb(0.02, 0.02, 0.022), Life.rgb(0.08, 0.08, 0.085), Life.rgb(0.32, 0.015, 0.012), Life.rgb(0.02, 0.06, 0.22), Life.rgb(0.10, 0.11, 0.08)};

    private final Life life;
    private final SplittableRandom rnd;
    final List<Car> cars = new ArrayList<>();
    private Way[] ways;
    private double junctionS = -1, entranceS = -1;
    private int[] spotWay;
    private double[] spotS;
    private int[] spotUsed;
    /** Wie oft ein Auto für eine Nēnē gehalten hat (Prüfung). */
    public int neneStops;
    private final float[] o = new float[6];

    Cars(Life life, long seed) {
        this.life = life;
        rnd = new SplittableRandom(seed);
    }

    private boolean build() {
        if (ways != null) return true;
        com.dan.road.Network n = ParkRoads.network();
        if (n == null || n.ways.size() < 2) return false;
        ways = new Way[]{n.ways.get(0), n.ways.get(1)};
        Way coc = ways[1];
        junctionS = nearestS(ways[0], coc.x[0], coc.z[0]);
        entranceS = nearestS(ways[0], Geo.x(19.4296, -155.2585), Geo.z(19.4296));
        spotWay = new int[SPOTS.length];
        spotS = new double[SPOTS.length];
        spotUsed = new int[SPOTS.length];
        for (int i = 0; i < SPOTS.length; i++) {
            double x = Geo.x(SPOTS[i][0], SPOTS[i][1]), z = Geo.z(SPOTS[i][0]);
            double d0 = dist(ways[0], x, z), d1 = dist(ways[1], x, z);
            spotWay[i] = d0 <= d1 ? 0 : 1;
            spotS[i] = nearestS(ways[spotWay[i]], x, z);
        }
        return true;
    }

    static double nearestS(Way w, double x, double z) {
        double best = Double.MAX_VALUE, bs = 0;
        for (int i = 0; i < w.n; i++) {
            double d = Math.hypot(w.x[i] - x, w.z[i] - z);
            if (d < best) { best = d; bs = i * w.step(); }
        }
        return Math.min(bs, w.length);
    }

    static double dist(Way w, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < w.n; i++) best = Math.min(best, Math.hypot(w.x[i] - x, w.z[i] - z));
        return best;
    }

    /** Anteil des Höchstverkehrs zur Stunde [G]. */
    public static double traffic(double hour) {
        double h = hour;
        if (h < 5) return 0.06;
        if (h < 9) return 0.06 + 0.94 * (h - 5) / 4;
        if (h < 16) return 1;
        if (h < 21) return 1 - 0.8 * (h - 16) / 5;
        return 0.2 - 0.14 * (h - 21) / 3;
    }

    void update(double dt, World w) {
        if (!build()) return;
        int target = (int) Math.round(30 * traffic(w.hour));
        if (cars.isEmpty() && target > 0) for (int i = 0; i < target; i++) spawn(w, true);
        else if (cars.size() < target && rnd.nextDouble() < dt * 0.25) spawn(w, false);
        boolean lights = w.sunElevation < 3;
        // zu viele für die Stunde: außer Sicht fährt je Sekunde eines hinaus
        if (cars.size() > target && rnd.nextDouble() < dt) {
            for (Iterator<Car> it = cars.iterator(); it.hasNext(); ) {
                Car c = it.next();
                if (Math.hypot(c.x - w.camX, c.z - w.camZ) < 1200) continue;
                if (c.spot >= 0) spotUsed[c.spot]--;
                it.remove();
                break;
            }
        }
        for (Iterator<Car> it = cars.iterator(); it.hasNext(); ) {
            Car c = it.next();
            if (!step(c, dt, w)) { if (c.spot >= 0) spotUsed[c.spot]--; it.remove(); continue; }
            // zu viele: wer an der Einfahrt vorbeikommt, fährt hinaus
            if (cars.size() > target && !c.parked && c.way == 0 && Math.abs(c.s - entranceS) < 4 && rnd.nextDouble() < 0.5) {
                it.remove();
                continue;
            }
        }
        for (Car c : cars) {
            double yaw = Path.yaw(c.vx == 0 && c.vz == 0 ? 0 : c.vx, c.vx == 0 && c.vz == 0 ? 1 : c.vz);
            ways[c.way].at(c.s, c.u, o);
            double dx = o[3] * c.dir, dz = o[4] * c.dir;
            yaw = Path.yaw(dx, dz);
            // Neigung aus der Fahrbahn
            ways[c.way].at(c.s + 2 * c.dir, c.u, o);
            double yf = o[1];
            ways[c.way].at(c.s - 2 * c.dir, c.u, o);
            double pitch = Math.atan2(yf - o[1], 4);
            int kind = Life.K_SEDAN + c.type;
            life.put(kind, c.x, c.y, c.z, yaw, 1, pitch, 0, 0, c.paint, 0, 0, 0, lights && !c.parked ? 1 : 0);
            if (!c.parked && c.v > 0.5) life.source(Life.E_CAR, c.x, c.y + 0.5, c.z, c.vx, 0, c.vz, c.v);
        }
    }

    private void spawn(World w, boolean anywhere) {
        Car c = new Car();
        c.type = rnd.nextDouble() < 0.5 ? 0 : rnd.nextDouble() < 0.8 ? 1 : 2;
        c.paint = c.type == 2 ? PAINT[0] : PAINT[rnd.nextInt(PAINT.length)];
        c.vmax = VMAX * (0.78 + 0.22 * rnd.nextDouble());
        if (anywhere) {
            c.way = rnd.nextDouble() < 0.45 ? 0 : 1;
            c.s = rnd.nextDouble() * ways[c.way].length;
            c.dir = rnd.nextBoolean() ? 1 : -1;
            if (rnd.nextDouble() < 0.3) {   // ein Teil steht schon an einer Aussicht
                int i = rnd.nextInt(SPOTS.length);
                if (spotUsed[i] < 6) { park(c, i); }
            }
        } else {
            c.way = 0;
            c.s = entranceS;
            c.dir = rnd.nextBoolean() ? 1 : -1;
        }
        if (!c.parked) c.u = LANE * c.dir;
        ways[c.way].at(c.s, c.u, o);
        if (w.closed(o[0], o[2], 200)) return;
        // nicht auf ein anderes Auto setzen
        for (Car d : cars) if (d.way == c.way && Math.abs(d.s - c.s) < 12 && Math.signum(d.u) == Math.signum(c.u)) return;
        c.x = o[0]; c.y = o[1]; c.z = o[2];
        c.v = c.parked ? 0 : c.vmax * 0.7;
        cars.add(c);
    }

    private void park(Car c, int spot) {
        c.way = spotWay[spot];
        c.s = spotS[spot] + (spotUsed[spot] - 2.5) * 6.5;
        c.parked = true;
        c.spot = spot;
        c.u = PARK * (rnd.nextBoolean() ? 1 : -1);
        c.timer = 60 + 300 * rnd.nextDouble();   // 1–6 Minuten [G]
        spotUsed[spot]++;
    }

    /** Ein Auto weiterbewegen; false, wenn es den Park verlässt. */
    private boolean step(Car c, double dt, World w) {
        Way way = ways[c.way];
        if (c.parked) {
            c.timer -= dt;
            if (c.timer < 0) {
                c.parked = false;
                if (c.spot >= 0) spotUsed[c.spot]--;
                c.spot = -1;
                c.dir = rnd.nextBoolean() ? 1 : -1;
                c.v = 0;
            }
            way.at(c.s, c.u, o);
            set(c, o, dt);
            return true;
        }
        // Spur einnehmen (vom Parkplatz zurück auf die Fahrbahn)
        double lane = LANE * c.dir;
        c.u += Math.max(-1.5 * dt, Math.min(1.5 * dt, lane - c.u));
        // Wunschtempo: Kurve, Vordermann, Nēnē, Sperrzone, Ende
        double want = Math.min(c.vmax, way.curveSpeed(c.s + c.dir * 25));
        double gap = 1e9;
        for (Car d : cars) {
            if (d == c || d.way != c.way || d.parked || d.dir != c.dir) continue;
            double ahead = (d.s - c.s) * c.dir;
            if (ahead > 0 && ahead < gap) gap = ahead;
        }
        if (gap < 200) want = Math.min(want, Math.max(0, (gap - LENGTH - 6) / 1.8));
        way.at(c.s + c.dir * 14, lane, o);
        boolean nene = life.nene.near(o[0], o[2], 4.5) || life.nene.near((o[0] + c.x) / 2, (o[2] + c.z) / 2, 4.5);
        if (nene) {
            want = 0;
            if (!c.waiting) { c.waiting = true; neneStops++; }
        } else c.waiting = false;
        way.at(c.s + c.dir * 120, lane, o);
        boolean blocked = w.closed(o[0], o[2], 100);
        boolean end = (c.dir > 0 && c.s > way.length - 15) || (c.dir < 0 && c.s < 15);
        // Abzweig: Chain of Craters ↔ Crater Rim Drive
        if (c.way == 1 && c.dir < 0 && c.s < 6) {
            c.way = 0;
            c.s = junctionS;
            c.dir = rnd.nextBoolean() ? 1 : -1;
            c.u = LANE * c.dir;
            return true;
        }
        if (c.way == 0 && Math.abs(c.s - junctionS) < c.v * dt + 0.5 && rnd.nextDouble() < 0.35 && !c.waiting) {
            c.way = 1;
            c.s = 6;
            c.dir = 1;
            c.u = LANE;
            return true;
        }
        if (blocked || end) want = 0;
        double acc = want > c.v ? 1.6 : -3.5;
        c.v += Math.max(-4.5 * dt, Math.min(1.8 * dt, (want - c.v))) * (acc > 0 ? 1 : 1);
        if (c.v < 0) c.v = 0;
        if ((blocked || end) && c.v < 0.6) {
            // an Aussicht am Ende parken oder wenden
            int spot = nearSpot(c);
            if (spot >= 0 && spotUsed[spot] < 6 && rnd.nextDouble() < 0.6) park(c, spot);
            else { c.dir = -c.dir; c.v = 0; }
            return true;
        }
        // unterwegs an einer Aussicht anhalten
        if (c.v > 3) {
            int spot = nearSpot(c);
            if (spot >= 0 && spotUsed[spot] < 6 && rnd.nextDouble() < dt * 0.15) { park(c, spot); return true; }
        }
        c.s += c.dir * c.v * dt;
        c.s = Math.max(0, Math.min(way.length, c.s));
        way.at(c.s, c.u, o);
        set(c, o, dt);
        return true;
    }

    private int nearSpot(Car c) {
        for (int i = 0; i < SPOTS.length; i++) if (spotWay[i] == c.way && Math.abs(spotS[i] - c.s) < 25) return i;
        return -1;
    }

    private void set(Car c, float[] p, double dt) {
        if (dt > 0) { c.vx = (p[0] - c.x) / dt; c.vz = (p[2] - c.z) / dt; }
        if (Math.hypot(c.vx, c.vz) > 40) { c.vx = 0; c.vz = 0; }
        c.x = p[0]; c.y = p[1]; c.z = p[2];
    }

    /** Autos im Park (Prüfung). */
    public int size() { return cars.size(); }

    /** Lage, Tempo und Spur aller Autos: x, z, v, Querlage, Weg, Bogenlänge, Richtung, geparkt (Prüfung). */
    public List<double[]> state() {
        List<double[]> l = new ArrayList<>();
        for (Car c : cars) l.add(new double[]{c.x, c.z, c.v, c.u, c.way, c.s, c.dir, c.parked ? 1 : 0});
        return l;
    }
}
