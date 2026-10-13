package com.dan.kilauea.life;

import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;

import java.util.SplittableRandom;

/**
 * Vögel: Singvögel im Wald um die Kamera, Koaʻe kea in den Kratern, ein ʻIo über dem Wald.
 * <ul>
 * <li>ʻApapane sind in den hohen Wäldern am Crater Rim Drive häufig, besonders wenn das ʻōhiʻa blüht;
 * ʻAmakihi gehört zu den häufigsten heimischen Waldvögeln; ʻŌmaʻo lebt im Bergregenwald über rund 900 m
 * [L NPS]. Der eingeführte Japanbrillenvogel ist in tieferen Lagen häufig [L]. Anteile, Dichte und
 * Flugbahnen gestaltet [G]; am Morgen sind sie am regsten (Morgenchor) [L NPS „Dawn Chorus“].</li>
 * <li>Koaʻe kea (Weißschwanz-Tropikvogel) kreisen über der Caldera und im Halemaʻumaʻu und brüten an
 * Kraterwänden [L NPS]; im Kīlauea Iki, Zahl und Bahnen [G].</li>
 * <li>ʻIo (Hawaiibussard) ist auf der Insel verbreitet und im Park regelmäßig zu sehen [L NPS]; ein
 * kreisender Vogel in der Nähe der Kamera [G].</li>
 * </ul>
 */
public final class Birds {
    static final int SONG = 36;

    static final class Song {
        boolean live;
        int kind;
        double x, y, z, heading, timer, phase;
        boolean flying;
        double x0, y0, z0, x1, y1, z1, ft, fdur;
    }

    static final class Soar {
        double cx, cz, r, h, ang, speed, phase, ground;
    }

    private final Life life;
    private final SplittableRandom rnd;
    final Song[] songs = new Song[SONG];
    final Soar[] tropic = new Soar[6];
    final Soar hawk = new Soar();
    private boolean hawkPlaced;
    private static final float[] TINT = {1, 1, 1};
    /** Mittelpunkte der Krater mit Tropikvögeln: Halemaʻumaʻu, Kīlauea Iki. */
    static final double[][] CRATERS = {{19.4072, -155.2834, 420, 4}, {19.4160, -155.2465, 380, 2}};

    Birds(Life life, long seed) {
        this.life = life;
        rnd = new SplittableRandom(seed);
        for (int i = 0; i < SONG; i++) songs[i] = new Song();
        int k = 0;
        for (double[] c : CRATERS)
            for (int j = 0; j < c[3]; j++) {
                Soar s = new Soar();
                s.cx = Geo.x(c[0], c[1]) + rnd.nextDouble(-80, 80);
                s.cz = Geo.z(c[0]) + rnd.nextDouble(-80, 80);
                s.r = c[2] * (0.45 + 0.5 * rnd.nextDouble());
                s.h = 40 + 90 * rnd.nextDouble();
                s.ang = rnd.nextDouble() * 2 * Math.PI;
                s.speed = (rnd.nextBoolean() ? 1 : -1) * (10 + 3 * rnd.nextDouble());   // rund 10–13 m/s [G]
                s.phase = rnd.nextDouble() * 10;
                tropic[k++] = s;
            }
    }

    /** Wie rege die Vögel sind: am Morgen am meisten, mittags weniger, nachts still [G nach L Morgenchor]. */
    public static double activity(double sunElevation, double hour) {
        if (sunElevation < -4) return 0;
        double dawn = Math.exp(-Math.pow((hour - 6.6) / 1.2, 2));
        double dusk = 0.5 * Math.exp(-Math.pow((hour - 17.8) / 1.0, 2));
        return Math.min(1, 0.35 + dawn + dusk);
    }

    void update(double dt, World w) {
        HeightGrid g = Ground.grid();
        double agl = w.camY - Ground.height(w.camX, w.camZ);
        double act = activity(w.sunElevation, w.hour);
        boolean near = agl < 350 && act > 0;
        for (Song s : songs) {
            if (s.live && (Math.hypot(s.x - w.camX, s.z - w.camZ) > 320 || !near)) s.live = false;
            if (!s.live && near && rnd.nextDouble() < dt * 0.8 * act) spawn(s, g, w);
            if (s.live) stepSong(s, dt, w, act);
        }
        // Tropikvögel: am Tag, um die Fontäne herum weiter und höher
        if (w.daylight()) for (Soar s : tropic) soarTropic(s, dt, w);
        if (w.sunElevation > 0) soarHawk(dt, w, g);
        else hawkPlaced = false;
    }

    private void spawn(Song s, HeightGrid g, World w) {
        for (int tries = 0; tries < 6; tries++) {
            double a = rnd.nextDouble() * 2 * Math.PI, r = 25 + 250 * Math.sqrt(rnd.nextDouble());
            double x = w.camX + Math.cos(a) * r, z = w.camZ + Math.sin(a) * r;
            double trees = g.cover(x, z, 1), h = Ground.height(x, z);
            if (trees < 0.35 || h < 5 || w.closed(x, z, 200)) continue;
            double rain = g.geo(x, z, 3) * com.dan.kilauea.terrain.Geology.RAIN_MAX;
            double u = rnd.nextDouble();
            if (h > 900) s.kind = u < 0.5 ? 0 : u < 0.75 ? 1 : (u < 0.87 && rain > 2000) ? 2 : 3;
            else s.kind = u < 0.1 ? 0 : u < 0.4 ? 1 : 3;
            s.x = x; s.z = z;
            s.y = h + 6 + 9 * rnd.nextDouble();   // in der Krone [G]
            s.live = true;
            s.flying = false;
            s.timer = rnd.nextDouble(1, 8);
            s.heading = rnd.nextDouble() * 2 * Math.PI;
            s.phase = rnd.nextDouble() * 10;
            return;
        }
    }

    private void stepSong(Song s, double dt, World w, double act) {
        s.phase += dt;
        if (s.flying) {
            s.ft += dt;
            double f = Math.min(1, s.ft / s.fdur);
            double nx = s.x0 + (s.x1 - s.x0) * f, nz = s.z0 + (s.z1 - s.z0) * f;
            double ny = s.y0 + (s.y1 - s.y0) * f + 4 * Math.sin(Math.PI * f) - 2.5 * Math.sin(2 * Math.PI * f) * 0.3;
            s.heading = Math.atan2(nx - s.x, -(nz - s.z));
            s.x = nx; s.y = ny; s.z = nz;
            double flap = 1.1 * Math.sin(s.phase * 2 * Math.PI * 14);   // kleine Vögel schlagen rasch [G]
            // Wellenflug: zwischen Schlagfolgen kurz angelegt
            if (Math.sin(s.phase * 2 * Math.PI * 1.5) < -0.3) flap = -0.9;
            life.put(Life.K_APAPANE + s.kind, s.x, s.y, s.z, Nene.yaw(s.heading), 1, 0, 0, 0, TINT, flap, 0, 0, 0);
            if (f >= 1) { s.flying = false; s.timer = rnd.nextDouble(2, 14); }
            return;
        }
        // in der Krone: singen, dann zum nächsten Baum
        s.timer -= dt;
        double rate = act * (s.kind == 0 ? 1 / 6.0 : s.kind == 2 ? 1 / 9.0 : 1 / 7.0);
        if (rnd.nextDouble() < dt * rate) life.call(Life.C_APAPANE + s.kind, s.x, s.y, s.z);
        if (s.timer < 0) {
            double a = rnd.nextDouble() * 2 * Math.PI, d = 15 + 50 * rnd.nextDouble();
            s.x0 = s.x; s.y0 = s.y; s.z0 = s.z;
            s.x1 = s.x + Math.cos(a) * d;
            s.z1 = s.z + Math.sin(a) * d;
            s.y1 = Ground.height(s.x1, s.z1) + 6 + 9 * rnd.nextDouble();
            s.ft = 0;
            s.fdur = d / (8 + 4 * rnd.nextDouble());   // 8–12 m/s [G]
            s.flying = true;
        }
        // sitzend: im Laub kaum zu sehen, als ruhende Figur mit angelegten Flügeln
        life.put(Life.K_APAPANE + s.kind, s.x, s.y, s.z, Nene.yaw(s.heading), 1, 0.25, 0, 0, TINT, -0.95, 0, 0, 0);
    }

    private void soarTropic(Soar s, double dt, World w) {
        boolean fire = w.closed(s.cx, s.cz, 0);
        double r = s.r + (fire ? 280 : 0);
        s.ang += s.speed / r * dt;
        s.phase += dt;
        double x = s.cx + Math.cos(s.ang) * r, z = s.cz + Math.sin(s.ang) * r;
        double gnd = Ground.height(x, z);
        s.ground += (gnd - s.ground) * (s.ground == 0 ? 1 : Math.min(1, dt * 0.5));
        double y = s.ground + s.h + (fire ? 150 : 0) + 12 * Math.sin(s.phase * 0.31);
        double dx = -Math.sin(s.ang) * Math.signum(s.speed), dz = Math.cos(s.ang) * Math.signum(s.speed);
        double heading = Math.atan2(dx, -dz);
        // Gleiten mit gelegentlichen Schlägen; in der Kurve nach innen geneigt
        double flap = Math.sin(s.phase * 0.5) > 0.6 ? 0.7 * Math.sin(s.phase * 2 * Math.PI * 4.0) : 0.12;
        double roll = Math.atan(s.speed * s.speed / (r * 9.81)) * Math.signum(s.speed);
        life.put(Life.K_TROPIC, x, y, z, Nene.yaw(heading), 1, 0, roll, s.phase * 3, TINT, flap, 0, 0, 0);
        if (rnd.nextDouble() < dt / 60.0) life.call(Life.C_TROPIC, x, y, z);
    }

    private void soarHawk(double dt, World w, HeightGrid g) {
        Soar s = hawk;
        if (!hawkPlaced || Math.hypot(s.cx - w.camX, s.cz - w.camZ) > 5000) {
            for (int tries = 0; tries < 10; tries++) {
                double a = rnd.nextDouble() * 2 * Math.PI, d = 600 + 2000 * rnd.nextDouble();
                double x = w.camX + Math.cos(a) * d, z = w.camZ + Math.sin(a) * d;
                if (g.cover(x, z, 1) < 0.25 || Ground.height(x, z) < 50) continue;
                s.cx = x; s.cz = z;
                s.r = 90 + 70 * rnd.nextDouble();
                s.h = 150 + 150 * rnd.nextDouble();
                s.speed = (rnd.nextBoolean() ? 1 : -1) * 11;
                s.ground = Ground.height(x, z);
                hawkPlaced = true;
                break;
            }
            if (!hawkPlaced) return;
        }
        s.ang += s.speed / s.r * dt;
        s.phase += dt;
        // der Kreis treibt mit dem Wind (thermischer Aufwind) [G]
        s.cx += w.windX * 0.25 * dt;
        s.cz += w.windZ * 0.25 * dt;
        double x = s.cx + Math.cos(s.ang) * s.r, z = s.cz + Math.sin(s.ang) * s.r;
        double y = s.ground + s.h + 8 * Math.sin(s.phase * 0.2);
        double dx = -Math.sin(s.ang) * Math.signum(s.speed), dz = Math.cos(s.ang) * Math.signum(s.speed);
        double roll = Math.atan(s.speed * s.speed / (s.r * 9.81)) * Math.signum(s.speed);
        life.put(Life.K_HAWK, x, y, z, Nene.yaw(Math.atan2(dx, -dz)), 1, 0, roll, 0, TINT, 0.08, 0, 0, 0);
        if (rnd.nextDouble() < dt / 90.0) life.call(Life.C_IO, x, y, z);
    }

    /** Lebende Singvögel um die Kamera (Prüfung). */
    public int songCount() { int n = 0; for (Song s : songs) if (s.live) n++; return n; }
}
