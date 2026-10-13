package com.dan.kilauea.life;

import com.dan.kilauea.terrain.LavaTube;
import com.dan.kilauea.terrain.ParkRoads;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Wanderer auf den Wegen am Gipfel: Kīlauea Iki Trail (Rundweg hinab auf den Lavasee von 1959),
 * Crater Rim Trail, Devastation Trail und der Weg durch die Lavaröhre Nāhuku. Gruppen von einer bis vier
 * Personen gehen mit 1,0–1,4 m/s, bleiben an Aussichten stehen und kehren am Ende eines Weges um. Am Tag
 * mehr, nachts niemand; in Nāhuku bis in den Abend [G]. Liegt der Weg in einer Sperrzone (aktiver Schlot,
 * heiße Lava), kehren sie um, und dort beginnt niemand [G nach den Sperrungen des Parks].
 */
public final class Hikers {
    static final class Party {
        int path, dir, size;
        double s, speed, stop, look;
        final double[] ds = new double[4], u = new double[4], phase = new double[4], scale = new double[4];
        final int[] variant = new int[4];
        final float[][] jacket = new float[4][];
    }

    /** Jacken: Regenjacken in kräftigen Farben, dazu gedeckte [G]. */
    static final float[][] JACKETS = {Life.rgb(0.40, 0.03, 0.02), Life.rgb(0.45, 0.20, 0.01), Life.rgb(0.42, 0.36, 0.02), Life.rgb(0.02, 0.18, 0.06),
            Life.rgb(0.02, 0.08, 0.32), Life.rgb(0.20, 0.02, 0.18), Life.rgb(0.03, 0.03, 0.035), Life.rgb(0.22, 0.22, 0.22), Life.rgb(0.05, 0.20, 0.22)};

    private final Life life;
    private final SplittableRandom rnd;
    public final List<Path> paths = new ArrayList<>();
    /** Höchstzahl der Personen je Weg zur Mittagszeit [G]. */
    final List<Integer> cap = new ArrayList<>();
    final List<Party> parties = new ArrayList<>();
    private boolean built;

    Hikers(Life life, long seed) {
        this.life = life;
        rnd = new SplittableRandom(seed);
    }

    /** Wege aus dem Straßenpaket und der Röhre bauen (sobald die Wege gebaut sind). */
    boolean build() {
        if (built) return true;
        com.dan.road.Network dirt = ParkRoads.trails(0), paved = ParkRoads.trails(1);
        if (dirt == null || paved == null || dirt.ways.size() < 2 || paved.ways.size() < 3) return false;
        paths.add(Path.of("Kīlauea Iki Trail", dirt.ways.get(0)));
        cap.add(14);
        paths.add(Path.of("Crater Rim Trail", dirt.ways.get(1)));
        cap.add(8);
        paths.add(Path.of("Devastation Trail", paved.ways.get(0)));
        cap.add(6);
        Path nahuku = nahuku(paved.ways.get(1), paved.ways.get(2));
        if (nahuku != null) { paths.add(nahuku); cap.add(12); }
        built = true;
        return true;
    }

    /** Rundweg Nāhuku: Parkplatz → Eingangsgrube → durch die Röhre (Boden der Röhre) → Ausgangsgrube → zurück. */
    static Path nahuku(com.dan.road.Way in, com.dan.road.Way back) {
        LavaTube t = LavaTube.get();
        List<double[]> p = new ArrayList<>();
        for (int i = 0; i < in.n; i++) p.add(new double[]{in.x[i], in.z[i], Double.NaN});
        if (t != null) for (int i = 0; i < t.n; i += 2) p.add(new double[]{t.x[i], t.z[i], t.floor[i]});
        for (int i = 0; i < back.n; i++) p.add(new double[]{back.x[i], back.z[i], Double.NaN});
        // doppelte Punkte entfernen
        List<double[]> q = new ArrayList<>();
        for (double[] a : p) if (q.isEmpty() || Math.hypot(a[0] - q.get(q.size() - 1)[0], a[1] - q.get(q.size() - 1)[1]) > 0.3) q.add(a);
        double[] x = new double[q.size()], z = new double[q.size()], y = new double[q.size()];
        for (int i = 0; i < q.size(); i++) { x[i] = q.get(i)[0]; z[i] = q.get(i)[1]; y[i] = q.get(i)[2]; }
        return new Path("Nāhuku", x, z, y, true);
    }

    /** Wie viele Personen zu dieser Stunde unterwegs sind (Anteil der Höchstzahl) [G]. */
    public static double busy(double hour, double sunElevation, boolean tube) {
        if (sunElevation < -3 && !(tube && hour < 20.5 && hour > 6)) return 0;
        double h = hour;
        double v = h < 7 ? 0.15 : h < 9 ? 0.15 + 0.85 * (h - 7) / 2 : h < 15 ? 1 : h < 18.5 ? 1 - 0.7 * (h - 15) / 3.5 : 0.25;
        return Math.max(0, Math.min(1, v));
    }

    void update(double dt, World w) {
        if (!build()) return;
        double[] o = new double[5];
        // Zahl je Weg nach der Stunde: neue Gruppen beginnen, überzählige verschwinden außer Sicht
        for (int pi = 0; pi < paths.size(); pi++) {
            boolean tube = paths.get(pi).name.equals("Nāhuku");
            int target = (int) Math.round(cap.get(pi) * busy(w.hour, w.sunElevation, tube));
            int now = 0;
            for (Party p : parties) if (p.path == pi) now += p.size;
            if (now < target && rnd.nextDouble() < dt * 0.5 + (parties.isEmpty() ? 1 : 0)) spawn(pi, w, o, target - now);
            if (now > target) for (Iterator<Party> it = parties.iterator(); it.hasNext(); ) {
                Party p = it.next();
                if (p.path != pi) continue;
                paths.get(pi).at(p.s, 0, o);
                if (Math.hypot(o[0] - w.camX, o[2] - w.camZ) > 300 || target == 0) { it.remove(); break; }
            }
        }
        // gesperrt: wer schon in der Zone ist, verlässt sie (der Weg wird geräumt) [G]
        for (Iterator<Party> it = parties.iterator(); it.hasNext(); ) {
            Party p = it.next();
            paths.get(p.path).at(p.s, 0, o);
            if (w.closed(o[0], o[2], 0)) it.remove();
        }
        for (Party p : parties) step(p, dt, w, o);
    }

    private void spawn(int pi, World w, double[] o, int missing) {
        Path path = paths.get(pi);
        for (int tries = 0; tries < 5; tries++) {
            double s = rnd.nextDouble() * path.length;
            path.at(s, 0, o);
            if (w.closed(o[0], o[2], 250)) continue;
            Party p = new Party();
            p.path = pi;
            p.s = s;
            p.dir = path.loop ? 1 : (rnd.nextBoolean() ? 1 : -1);
            p.size = Math.min(missing, 1 + rnd.nextInt(4));
            p.speed = 1.0 + 0.4 * rnd.nextDouble();   // 1,0–1,4 m/s [G]
            for (int k = 0; k < p.size; k++) {
                p.ds[k] = -k * (1.4 + 0.6 * rnd.nextDouble());
                p.u[k] = (k % 2 == 0 ? 1 : -1) * (0.25 + 0.2 * rnd.nextDouble());
                p.phase[k] = rnd.nextDouble() * 6;
                p.scale[k] = 0.92 + 0.14 * rnd.nextDouble();
                p.variant[k] = rnd.nextInt(Life.HIKER_VARIANTS);
                p.jacket[k] = JACKETS[rnd.nextInt(JACKETS.length)];
            }
            parties.add(p);
            return;
        }
    }

    private void step(Party p, double dt, World w, double[] o) {
        Path path = paths.get(p.path);
        boolean walking = p.stop <= 0;
        if (walking) {
            p.s += p.dir * p.speed * dt;
            if (!path.loop && (p.s <= 0 || p.s >= path.length)) { p.dir = -p.dir; p.s = path.wrap(p.s); p.stop = 3 + 10 * rnd.nextDouble(); }
            if (rnd.nextDouble() < dt / 110.0) { p.stop = 8 + 35 * rnd.nextDouble(); p.look = rnd.nextDouble(-1.2, 1.2); }
            // Sperrzone voraus: umkehren
            path.at(p.s + p.dir * 40, 0, o);
            if (w.closed(o[0], o[2], 150)) { p.dir = -p.dir; if (path.loop) p.dir = 1; p.stop = 2; if (path.loop) p.s = path.wrap(p.s - 60); }
        } else p.stop -= dt;
        double f = 1.8 * p.speed / 1.25;   // Schrittfolge rund 1,8 Hz [G]
        for (int k = 0; k < p.size; k++) {
            if (walking) p.phase[k] += 2 * Math.PI * f * dt;
            path.at(p.s + p.dir * p.ds[k], p.u[k] * p.dir, o);
            double dx = o[3] * p.dir, dz = o[4] * p.dir;
            double yaw = Path.yaw(dx, dz) + (walking ? 0 : p.look + 0.3 * k);
            life.put(Life.K_HIKER + p.variant[k], o[0], o[1], o[2], yaw, p.scale[k], 0, 0, p.phase[k], p.jacket[k], 0, walking ? 1 : 0, 0, 0);
        }
    }

    /** Personen unterwegs (Prüfung). */
    public int people() { int n = 0; for (Party p : parties) n += p.size; return n; }

    /** Lage aller Personen: x, z, Weg (Prüfung). */
    public List<double[]> positions() {
        List<double[]> l = new ArrayList<>();
        double[] o = new double[5];
        for (Party p : parties)
            for (int k = 0; k < p.size; k++) {
                paths.get(p.path).at(p.s + p.dir * p.ds[k], p.u[k] * p.dir, o);
                l.add(new double[]{o[0], o[1], o[2], p.path});
            }
        return l;
    }
}
