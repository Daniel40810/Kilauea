package com.dan.kilauea.tools;

import com.dan.kilauea.camera.OrbitCamera;
import com.dan.kilauea.camera.Tour;
import com.dan.kilauea.camera.TourPlayer;
import com.dan.kilauea.terrain.DetailTiles;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;
import com.dan.kilauea.terrain.TerrainBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüflauf der Kamera ohne Fenster (Phase 13): fliegt alle Rundflüge, alle Orte nacheinander und alle Blicke
 * der Drehbücher mit 30 Bildern je Sekunde und prüft je Bild:
 * <ul>
 * <li><b>Sprung</b>: Das Auge legt in einem Bild mehr als das Dreifache des Weges der Nachbarbilder plus 0,5 m
 * zurück, oder die Blickrichtung dreht sich um mehr als 4° in einem Bild.</li>
 * <li><b>Kollision</b>: Das Auge liegt tiefer als 2 m über Boden oder Meer.</li>
 * <li><b>Blick durch das Gelände</b>: Zwischen Auge und Drehpunkt ragt Gelände mehr als 0,5 m über die Sichtlinie.</li>
 * </ul>
 * Aufruf: {@code TourLauf} (alles) oder {@code TourLauf gipfel rift 2018 orte drehbuecher}.
 */
public final class TourLauf {
    private TourLauf() { }

    /** Ergebnis eines Laufs. */
    public record Result(String name, int frames, double seconds, double km, int jumps, int collisions, int blocked, double maxLiftDeg, String first) {
        public boolean ok() { return jumps == 0 && collisions == 0 && blocked == 0; }

        @Override public String toString() {
            return String.format(Locale.GERMAN, "%-34s %6d Bilder, %5.0f s, %6.1f km Weg · Sprünge %d · Kollisionen %d · Blick durchs Gelände %d · freie Sicht hob bis %.0f°%s",
                    name, frames, seconds, km, jumps, collisions, blocked, maxLiftDeg, first.isEmpty() ? "" : " · zuerst: " + first);
        }
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
        HeightGrid g = TerrainBuilder.load(dir);
        com.dan.kilauea.terrain.ParkRoads.build(g);
        DetailTiles.use(DetailTiles.load(dir, g, null));
        Ground.use(g);
        List<String> want = List.of(args);
        List<Result> rs = new ArrayList<>();
        for (Tour t : Tour.all()) if (want.isEmpty() || want.contains(t.id)) rs.add(tour(t, 30));
        if (want.isEmpty() || want.contains("orte")) rs.add(places(30));
        if (want.isEmpty() || want.contains("drehbuecher")) rs.add(scriptViews(30));
        boolean ok = true;
        for (Result r : rs) { System.out.println(r); ok &= r.ok(); }
        System.out.println(ok ? "ERGEBNIS: kein Sprung, keine Kollision, kein Blick durch das Gelände" : "ERGEBNIS: VERSTOSS");
        if (!ok) System.exit(1);
    }

    /** Kamera, die von der ganzen Insel aus startet (wie nach Pos1). */
    static OrbitCamera start() {
        OrbitCamera c = new OrbitCamera();
        Place p = Place.MAUNA_LOA;
        c.lookAt(p.x() + 8000, 1500, p.z() - 6000, 5, 55, 125_000);
        c.snap();
        return c;
    }

    /** Einen Rundflug ganz durchfliegen. */
    public static Result tour(Tour t, int fps) {
        OrbitCamera c = start();
        TourPlayer tp = new TourPlayer();
        Recorder rec = new Recorder(t.title);
        tp.update(0, c);
        tp.start(t);
        double dt = 1.0 / fps;
        tp.update(dt, c);
        int guard = 0;
        while (tp.active() && guard++ < fps * 600) {
            tp.update(dt, c);
            c.update(dt);
            rec.frame(c, dt);
        }
        return rec.result();
    }

    /** Alle Orte nacheinander anfliegen und an jedem 2 s bleiben. */
    public static Result places(int fps) {
        OrbitCamera c = start();
        Recorder rec = new Recorder("Alle " + Place.values().length + " Orte nacheinander");
        double dt = 1.0 / fps;
        for (Place p : Place.values()) {
            c.lookAt(p.x(), Math.max(0, Ground.height(p.x(), p.z())), p.z(), p.viewYaw, p.viewPitch, p.viewDist);
            int guard = 0;
            while (c.flying() && guard++ < fps * 20) { c.update(dt); rec.frame(c, dt); }
            for (int i = 0; i < 2 * fps; i++) { c.update(dt); rec.frame(c, dt); }
        }
        return rec.result();
    }

    /** Alle Blicke der Drehbücher nacheinander (so wie „Kamera folgt“ sie anfliegt). */
    public static Result scriptViews(int fps) {
        OrbitCamera c = start();
        Recorder rec = new Recorder("Blicke der Drehbücher");
        double dt = 1.0 / fps;
        for (var s : com.dan.kilauea.script.Scripts.all())
            for (var ch : s.chapters()) {
                double[] v = ch.view();
                if (v == null) continue;
                double x = Geo.x(v[0], v[1]), z = Geo.z(v[0]);
                c.lookAt(x, Math.max(0, Ground.height(x, z)) + v[2], z, v[3], v[4], v[5]);
                int guard = 0;
                while (c.flying() && guard++ < fps * 20) { c.update(dt); rec.frame(c, dt); }
                for (int i = 0; i < fps; i++) { c.update(dt); rec.frame(c, dt); }
            }
        return rec.result();
    }

    /** Sammelt Bilder und zählt Verstöße. */
    static final class Recorder {
        final String name;
        final List<double[]> eyes = new ArrayList<>();
        int collisions, blocked, turns;
        double seconds, maxLift;
        String first = "";

        Recorder(String name) { this.name = name; }

        void frame(OrbitCamera c, double dt) {
            seconds += dt;
            double[] f = c.forward();
            eyes.add(new double[]{c.ex, c.ey, c.ez, f[0], f[1], f[2], seconds, Math.toDegrees(c.lastLift), c.flying() ? 1 : 0, c.distance(), c.lookPoint()[1], Ground.height(c.lookPoint()[0], c.lookPoint()[2])});
            double floor = Math.max(0, Ground.height(c.ex, c.ez)) + OrbitCamera.CLEARANCE;
            if (c.ey < floor - 1e-6) { collisions++; note("Kollision", c); }
            if (Boolean.getBoolean("tour.lift") && Math.toDegrees(c.lastLift) > 30) System.out.printf(Locale.ROOT, "    LIFT %s %.2f s %.1f deg fl %s pitch %.1f dist %.0f%n", name, seconds, Math.toDegrees(c.lastLift), c.flying(), Math.toDegrees(c.forward()[1]), c.distance());
            double[] p = c.lookPoint();
            if (OrbitCamera.lineBlocked(p[0], p[1], p[2], c.ex, c.ey, c.ez)) {
                blocked++;
                note("Blick durchs Gelände", c);
                if (Boolean.getBoolean("tour.debug") && blocked < 4) {
                    double best = -1e9, bt = 0, len = Math.hypot(c.ex - p[0], c.ez - p[2]);
                    for (int i = 1; i <= 400; i++) { double t = 0.06 + 0.94 * i / 400;
                        double e = Ground.height(p[0] + (c.ex - p[0]) * t, p[2] + (c.ez - p[2]) * t) - (p[1] + (c.ey - p[1]) * t);
                        if (e > best) { best = e; bt = t; } }
                    System.out.printf(Locale.ROOT, "    DETAIL t=%.3f excess=%.2f len=%.0f pivotY=%.1f groundAtPivot=%.1f%n", bt, best, len, p[1], Ground.height(p[0], p[2]));
                }
                if (Boolean.getBoolean("tour.debug") && blocked < 4) System.out.printf(Locale.GERMAN, "    BLOCK %s %.2f s: Hub %.1f°, Neigung %.1f°, Abstand %.0f, Flug %s, Auge %.0f über Boden%n", name, seconds,
                        Math.toDegrees(c.lastLift), c.pitchDeg(), c.distance(), c.flying(), c.ey - Ground.height(c.ex, c.ez));
            }
            maxLift = Math.max(maxLift, Math.toDegrees(c.lastLift));
        }

        void note(String what, OrbitCamera c) {
            if (first.isEmpty()) first = String.format(Locale.GERMAN, "%s nach %.1f s bei %s", what, seconds, Geo.label(c.ex, c.ez));
        }

        Result result() {
            int jumps = 0;
            double km = 0;
            for (int i = 1; i < eyes.size(); i++) {
                double[] a = eyes.get(i - 1), b = eyes.get(i);
                double step = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
                km += step / 1000;
                double before = i >= 2 ? dist(eyes.get(i - 2), a) : step, after = i + 1 < eyes.size() ? dist(b, eyes.get(i + 1)) : step;
                boolean jump = step > 3 * Math.max(before, after) + 0.5;
                double cos = a[3] * b[3] + a[4] * b[4] + a[5] * b[5];
                boolean turn = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))) > 4;
                if (jump || turn) {
                    jumps++;
                    if (Boolean.getBoolean("tour.debug")) System.out.printf(Locale.GERMAN, "    %s %.2f s: Schritt %.1f (%.1f/%.1f) Hub %.1f→%.1f° Flug %.0f Abstand %.0f%n", name, b[6], step, before, after, a[7], b[7], b[8], b[9]);
                    if (Boolean.getBoolean("tour.debug")) for (int j = Math.max(0, i - 3); j <= Math.min(eyes.size() - 1, i + 3); j++) { double[] e = eyes.get(j);
                        System.out.printf(Locale.ROOT, "      %.3f eye %.2f %.2f %.2f lift %.2f fl %.0f cy %.2f ground %.2f%n", e[6], e[0], e[1], e[2], e[7], e[8], e[10], e[11]); }
                    if (first.isEmpty()) first = String.format(Locale.GERMAN, "%s nach %.1f s (%.1f m statt %.1f/%.1f)", jump ? "Sprung" : "Ruck im Blick", b[6], step, before, after);
                }
            }
            return new Result(name, eyes.size(), seconds, km, jumps, collisions, blocked, maxLift, first);
        }

        static double dist(double[] a, double[] b) {
            return Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        }
    }
}
