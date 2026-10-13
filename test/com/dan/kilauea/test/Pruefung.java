package com.dan.kilauea.test;

import com.dan.kilauea.atmos.SunClock;
import com.dan.kilauea.render.Cascades;
import com.dan.kilauea.render.Mat4;
import com.dan.kilauea.render.Quality;
import com.dan.kilauea.render.SceneState;
import com.dan.kilauea.render.TerrainLod;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.Island;
import com.dan.kilauea.flora.FloraField;
import com.dan.kilauea.terrain.Geology;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;
import com.dan.kilauea.terrain.Relief;
import com.dan.kilauea.terrain.TerrainBuilder;

import java.io.File;
import java.util.Locale;

/**
 * Prüfprogramm ohne Grafikkarte und ohne Datenbank. In NetBeans: Rechtsklick → „Run File“.
 * Endet mit „ERGEBNIS: alles in Ordnung“ oder mit der Liste der Verstöße (Rückgabewert 1).
 * Wächst mit jeder Phase (Kissenlava, Drehbücher …).
 */
public final class Pruefung {
    private static int ok, bad;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        geo();
        sun();
        island();
        HeightGrid g = grid();
        lod(g);
        geology(g);
        flora(g);
        summit(g);
        rifts(g);
        magma();
        lava();
        lake();
        glut();
        sea();
        entry();
        weather(g);
        camera();
        scripts();
        monitor();
        leben();
        klang();
        maths();
        state();
        datenbank();
        wissen(g);
        schliff();
        System.out.printf(Locale.GERMAN, "%n%d Prüfungen, %d Verstöße, %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + bad + " Verstöße");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean cond, String what) {
        if (cond) ok++; else bad++;
        System.out.println((cond ? "  ok    " : "  FEHLER ") + what);
    }

    /** Lage: Hin- und Rückrechnung, Abstände nach bekannter Entfernung. */
    static void geo() {
        System.out.println("Lage");
        for (Place p : Place.values()) {
            double x = p.x(), z = p.z();
            double lat = Geo.lat(z), lon = Geo.lon(x, z);
            check(Math.abs(lat - p.lat) < 1e-9 && Math.abs(lon - p.lon) < 1e-6, p.label + ": Breite/Länge zurückgerechnet");
        }
        double d = Math.hypot(Place.MAUNA_KEA.x() - Place.MAUNA_LOA.x(), Place.MAUNA_KEA.z() - Place.MAUNA_LOA.z());
        check(d > 40_000 && d < 44_000, String.format(Locale.GERMAN, "Mauna Loa – Mauna Kea %.1f km (Karte: rund 42 km)", d / 1000));
        check(Math.abs(Geo.curvatureDrop(100_000) - 785) < 2, "Erdkrümmung: 100 km → 785 m Absenkung");
    }

    /** Sonne für Hawaiʻi: Mittagshöhe, Sonnenaufgang, Mittag im Norden im Juni. */
    static void sun() {
        System.out.println("Sonne und Mond");
        SunClock c = new SunClock();
        double best = -90, bestH = 0;
        for (double h = 10; h < 15; h += 1 / 60.0) {
            c.set(172, h);
            if (c.elevationDeg > best) { best = c.elevationDeg; bestH = h; }
        }
        check(Math.abs(best - 85.95) < 0.6, String.format(Locale.GERMAN, "21. Juni: Mittagshöhe %.2f° (erwartet 90 − 19,41 + 23,44 ≈ 85,95° Nordhimmel)", best));
        check(bestH > 12.2 && bestH < 12.75, "21. Juni: höchster Stand " + SunClock.timeLabel(bestH) + " HST (erwartet gegen 12:30)");
        c.set(172, bestH);
        check(c.azimuthDeg < 20 || c.azimuthDeg > 340, String.format(Locale.GERMAN, "21. Juni mittags steht die Sonne im Norden (Azimut %.0f°)", c.azimuthDeg));
        double rise = -1;
        for (double h = 5; h < 8; h += 1 / 120.0) {
            c.set(280, h);
            if (c.elevationDeg > -0.83) { rise = h; break; }
        }
        check(rise > 6.0 && rise < 6.6, "7. Oktober: Sonnenaufgang " + SunClock.timeLabel(rise) + " HST (erwartet zwischen 6:00 und 6:35)");
        c.set(280, 12.3);
        check(c.moonLit >= 0 && c.moonLit <= 1 && Double.isFinite(c.moonElevationDeg), "Mond: Phase und Höhe endlich");
        c.set(365, 23.9);
        c.advance(0.2);
        check(c.day() == 1, "Jahreswechsel: Tag 365 + 0,2 h → Tag 1");
    }

    /** Insel: Höhen an bekannten Orten, Meer, Küste, keine NaN. */
    static void island() {
        System.out.println("Gerechnete Insel (Ersatz ohne Höhendaten, Toleranzen weit)");
        double ml = Island.height(Place.MAUNA_LOA.x(), Place.MAUNA_LOA.z() - 2500);
        check(ml > 3700 && ml < 4400, String.format(Locale.GERMAN, "Mauna Loa am Calderarand %.0f m (Gipfel 4 169 m)", ml));
        double mk = Island.height(Place.MAUNA_KEA.x(), Place.MAUNA_KEA.z());
        check(mk > 3950 && mk < 4400, String.format(Locale.GERMAN, "Mauna Kea %.0f m (4 207 m)", mk));
        double rim = Island.height(Place.UEKAHUNA.x(), Place.UEKAHUNA.z());
        check(rim > 1000 && rim < 1350, String.format(Locale.GERMAN, "Uēkahuna-Klippe %.0f m (rund 1 240 m)", rim));
        double floor = Island.height(0, 0);
        check(floor < rim - 100, String.format(Locale.GERMAN, "Halemaʻumaʻu liegt tiefer als der Rand (%.0f m)", floor));
        check(Island.height(Place.KAPOHO.x() + 8000, Place.KAPOHO.z()) < -50, "Östlich von Kapoho ist Meer");
        check(Island.height(0, 40_000) < -1000, "40 km südlich des Gipfels ist tiefes Meer");
        double arch = Island.height(Place.HOLEI_ARCH.x(), Place.HOLEI_ARCH.z());
        check(arch > -5 && arch < 80, String.format(Locale.GERMAN, "Felsbogen Hōlei an der Küste (%.0f m)", arch));
        boolean finite = true;
        for (int i = 0; i < 4000; i++) {
            double x = Island.X0 + (Island.X1 - Island.X0) * ((i * 0.618034) % 1), z = Island.Z0 + (Island.Z1 - Island.Z0) * ((i * 0.414214) % 1);
            double h = Island.height(x, z);
            float[] cov = Island.cover(x, z, h, Island.coastDistance(x, z), new float[4]);
            if (!Double.isFinite(h) || h > 4400 || h < -5600) finite = false;
            for (float f : cov) if (!(f >= 0 && f <= 1)) finite = false;
        }
        check(finite, "4 000 Stichproben: Höhe endlich und im Rahmen, Bodenart 0..1");
        check(Island.height(12345.6, -6543.2) == Island.height(12345.6, -6543.2), "gleiche Eingabe, gleiche Höhe (deterministisch)");
    }

    /** Höhenraster: Herkunft, Höhen an bekannten Orten, Glätte, Feinrelief, Min-Max-Pyramide. */
    static HeightGrid grid() {
        System.out.println("Höhenraster");
        long t0 = System.nanoTime();
        File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
        HeightGrid g = TerrainBuilder.load(dir);
        Ground.use(g);
        double s = (System.nanoTime() - t0) / 1e9;
        boolean data = g.source.startsWith("Höhen");
        check(g.h.length == HeightGrid.N * HeightGrid.N && g.cover.length == g.h.length * 4,
                String.format(Locale.GERMAN, "%d × %d Zellen zu %.0f m, geladen in %.1f s", HeightGrid.N, HeightGrid.N, HeightGrid.CELL, s));
        System.out.println("        Quelle: " + g.source);
        check(data, "Höhendaten aus dem Projekt verwendet (sonst gerechnete Insel)");
        double tol = data ? 1 : 4;
        place(Place.MAUNA_KEA, 4150, 4215, tol, "Gipfel 4 207 m");
        place(Place.MAUNA_LOA, 3900, 4175, tol, "Mokuʻāweoweo, Gipfel 4 169 m");
        place(Place.UEKAHUNA, 1180, 1270, tol, "rund 1 240 m");
        place(Place.HALEMAUMAU, 790, 880, tol, "Boden nach 2018/2024 rund 830 m");
        place(Place.PUU_OO, 450, 700, tol, "eingestürzter Krater 2018");
        place(Place.HOLEI_ARCH, 0, 40, tol, "an der Meeresklippe");
        place(Place.KAPOHO, 0, 40, tol, "neues Land 2018");
        check(Ground.height(Place.KAPOHO.x() + 8000, Place.KAPOHO.z()) < -50, "Östlich von Kapoho ist Meer");
        check(Ground.height(0, 40_000) < -1000, "40 km südlich des Gipfels ist tiefes Meer");
        // Glätte: der B-Spline springt nicht an Zellgrenzen
        double jump = 0;
        for (int k = 0; k < 2000; k++) {
            double x = -60_000 + 120_000 * ((k * 0.618034) % 1), z = -60_000 + 120_000 * ((k * 0.414214) % 1);
            x = HeightGrid.X0 + Math.floor((x - HeightGrid.X0) / HeightGrid.CELL) * HeightGrid.CELL;
            jump = Math.max(jump, Math.abs(g.height(x + 1e-4, z) - g.height(x - 1e-4, z)));
        }
        check(jump < 0.05, String.format(Locale.GERMAN, "Höhe stetig an Zellgrenzen (größter Sprung %.3f m)", jump));
        double rmax = 0;
        boolean finite = true;
        for (int k = 0; k < 20000; k++) {
            double x = -100_000 + 200_000 * ((k * 0.618034) % 1), z = -100_000 + 200_000 * ((k * 0.414214) % 1);
            double r = Relief.at(x, z, 1, 1, 0, 100);
            rmax = Math.max(rmax, Math.abs(r));
            if (!Double.isFinite(Ground.height(x, z))) finite = false;
        }
        check(rmax < 20, String.format(Locale.GERMAN, "Feinrelief höchstens %.1f m (Spielraum der Knoten 20 m)", rmax));
        check(finite, "20 000 Stichproben: Höhe endlich");
        float[] mm = new float[2];
        boolean inside = true;
        for (int k = 0; k < 500; k++) {
            double x0 = -90_000 + 170_000 * ((k * 0.618034) % 1), z0 = -90_000 + 170_000 * ((k * 0.414214) % 1), size = 100 * (1 << (k % 10));
            g.range(x0, z0, size, mm);
            for (int q = 0; q < 9; q++) {
                double v = g.height(x0 + size * (q % 3) / 2, z0 + size * (q / 3) / 2);
                if (v < mm[0] - 1 || v > mm[1] + 1) inside = false;
            }
        }
        check(inside, "Min-Max-Pyramide umfasst die Höhen jedes Knotens");
        return g;
    }

    static void place(Place p, double lo, double hi, double tol, String note) {
        double h = Ground.baseHeight(p.x(), p.z());
        double w = (hi - lo) * (tol - 1) / 2;
        check(h >= lo - w && h <= hi + w, String.format(Locale.GERMAN, "%s %.0f m (%s)", p.label, h, note));
    }

    /** Viererbaum: Fläche lückenlos, Nachbarn höchstens eine Stufe auseinander, Punktezahl im Rahmen. */
    static void lod(HeightGrid g) {
        System.out.println("Viererbaum");
        TerrainLod lod = new TerrainLod(g);
        lod.lodRange = Quality.HOCH.lodRange;
        double[][] cams = {{0, 1500, 3000}, {Place.MAUNA_KEA.x(), 4300, Place.MAUNA_KEA.z()}, {20_000, 80_000, 40_000}, {Place.HOLEI_ARCH.x(), 40, Place.HOLEI_ARCH.z()}};
        for (double[] cam : cams) {
            lod.collect(cam, null);
            float[] f = lod.selection(false), q = lod.selection(true);
            int n = f.length / 4 + q.length / 4;
            double[][] nd = new double[n][];
            double area = 0;
            for (int i = 0; i < n; i++) {
                boolean qu = i >= f.length / 4;
                float[] src = qu ? q : f;
                int o = (qu ? i - f.length / 4 : i) * 4;
                double size = src[o + 2];
                nd[i] = new double[]{src[o], src[o + 1], size, size / (qu ? TerrainLod.DIM / 2 : TerrainLod.DIM)};
                area += size * size;
            }
            double root = TerrainLod.ROOT * TerrainLod.ROOT;
            int badPairs = 0;
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++) {
                    double[] a = nd[i], b = nd[j];
                    boolean touchX = Math.abs(a[0] + a[2] - b[0]) < 0.01 || Math.abs(b[0] + b[2] - a[0]) < 0.01;
                    boolean touchZ = Math.abs(a[1] + a[2] - b[1]) < 0.01 || Math.abs(b[1] + b[2] - a[1]) < 0.01;
                    boolean overlapX = a[0] < b[0] + b[2] - 0.01 && b[0] < a[0] + a[2] - 0.01;
                    boolean overlapZ = a[1] < b[1] + b[2] - 0.01 && b[1] < a[1] + a[2] - 0.01;
                    if ((touchX && overlapZ) || (touchZ && overlapX)) {
                        double ratio = Math.max(a[3], b[3]) / Math.min(a[3], b[3]);
                        if (ratio > 2.01) badPairs++;
                    }
                }
            String at = String.format(Locale.GERMAN, "Auge %.0f/%.0f/%.0f", cam[0], cam[1], cam[2]);
            check(Math.abs(area - root) < root * 1e-6, at + String.format(Locale.GERMAN, ": %d Knoten decken die Fläche lückenlos", n));
            check(badPairs == 0, at + ": Nachbarn höchstens eine Stufe auseinander" + (badPairs > 0 ? " (" + badPairs + " Verstöße)" : ""));
            check(lod.vertices < 3_000_000, at + String.format(Locale.GERMAN, ": %.2f Mio. Punkte", lod.vertices / 1e6));
        }
    }

    /** Gestein und Grün: Alterscode, Ströme, Alter an bekannten Orten, Regen, Bewuchs, Straßen. */
    static void geology(HeightGrid g) {
        System.out.println("Gestein und Grün");
        boolean round = true;
        for (double a : new double[]{0.5, 3, 30, 300, 3000, 30000}) {
            double b = Geology.ageOf(Geology.ageCode(a));
            if (Math.abs(b - a) / a > 0.05) round = false;
        }
        check(round, "Alterscode: 0,5 bis 30 000 Jahre auf 5 % genau zurück");
        if (Geology.AREA.isEmpty()) {
            System.out.println("        (Gelände aus dem Zwischenspeicher: Flächen der Ströme erst nach Neubau)");
        } else {
            boolean all = Geology.FLOWS.stream().allMatch(f -> Geology.AREA.getOrDefault(f.name(), 0.0) > 0.5);
            check(all, "jeder der " + Geology.FLOWS.size() + " Ströme bedeckt Fläche");
            double pu = 0;
            for (var e : Geology.AREA.entrySet()) {
                String k = e.getKey();
                if (k.contains("Puʻu ʻŌʻō") || k.contains("Kupaianaha") || k.contains("Thanksgiving") || k.contains("Kahaualeʻa") || k.contains("2014") || k.contains("61g")) pu += e.getValue();
            }
            check(pu > 70 && pu < 220, String.format(Locale.GERMAN, "Feld des Puʻu ʻŌʻō 1983–2018: %.0f km² (USGS: rund 144 km²)", pu));
        }
        age(g, 19.4072, -155.2834, 0, 3, "Halemaʻumaʻu, Lavasee 2020–25");
        age(g, 19.4160, -155.2465, 55, 80, "Kīlauea Iki, Lavasee 1959");
        age(g, 19.5000, -154.8130, 5, 10, "Kapoho, neues Land 2018");
        age(g, 19.4721, -155.5922, 30, 50, "Mokuʻāweoweo, Boden 1984");
        age(g, 20.0860, -155.7170, 50_000, 400_000, "Kohala");
        rain(g, 19.72, -155.09, 2500, 6000, "Hilo");
        rain(g, 20.00, -155.30, 4000, 8000, "Hāmākua-Hang");
        rain(g, 19.36, -155.37, 0, 1000, "Kaʻū-Wüste");
        rain(g, 19.64, -155.99, 0, 700, "Kona-Küste");
        rain(g, 19.47, -155.59, 0, 600, "Gipfel Mauna Loa");
        double vx = Geo.x(19.43, -155.23), vz = Geo.z(19.43);
        check(g.cover(vx, vz, 1) > 0.4, String.format(Locale.GERMAN, "Volcano (Dorf): Wald (Baumanteil %.2f)", g.cover(vx, vz, 1)));
        double kx = Geo.x(19.8207, -155.4681), kz = Geo.z(19.8207);
        check(g.cover(kx, kz, 0) < 0.05, "Gipfel Mauna Kea: kahl");
        double fx = Geo.x(19.4700, -154.8900), fz = Geo.z(19.4700);
        check(g.cover(fx, fz, 0) < 0.05, "Strom der Spalte 8 (2018): kahl");
        var net = com.dan.kilauea.terrain.ParkRoads.build(g);
        double len = 0;
        for (var w : net.ways) len += w.length;
        check(len > 30_000 && len < 50_000, String.format(Locale.GERMAN, "Parkstraßen %.1f km (Crater Rim Drive und Chain of Craters Road)", len / 1000));
        Ground.use(g);
        double maxRel = 0;
        for (var w : net.ways)
            for (int i = 0; i < w.n; i += 25) maxRel = Math.max(maxRel, Math.abs(Ground.height(w.x[i], w.z[i]) - Ground.baseHeight(w.x[i], w.z[i])));
        check(maxRel < 0.05, String.format(Locale.GERMAN, "Straßenband ohne Feinrelief (größte Abweichung %.2f m)", maxRel));
        double top = Double.MAX_VALUE, end = 0;
        var coc = net.ways.get(1);
        top = g.height(coc.x[0], coc.z[0]);
        end = g.height(coc.x[coc.n - 1], coc.z[coc.n - 1]);
        check(top > 1000 && end < 120, String.format(Locale.GERMAN, "Chain of Craters Road fällt von %.0f m auf %.0f m", top, end));
    }

    static void age(HeightGrid g, double lat, double lon, double lo, double hi, String what) {
        double a = g.age(Geo.x(lat, lon), Geo.z(lat));
        check(a >= lo && a <= hi, String.format(Locale.GERMAN, "%s: %.1f Jahre", what, a));
    }

    static void rain(HeightGrid g, double lat, double lon, double lo, double hi, String what) {
        double r = g.geo(Geo.x(lat, lon), Geo.z(lat), 3) * Geology.RAIN_MAX;
        check(r >= lo && r <= hi, String.format(Locale.GERMAN, "Regen %s: %.0f mm (Spanne %.0f–%.0f)", what, r, lo, hi));
    }

    /** Pflanzen: Wald bei Volcano, keine auf frischer Lava und in der Wüste, keine auf der Straße. */
    static void flora(HeightGrid g) {
        System.out.println("Bäume und Farne");
        FloraField f = new FloraField();
        int[] forest = count(f, g, 19.43, -155.23), lava = count(f, g, 19.4700, -154.8900), desert = count(f, g, 19.36, -155.37);
        check(forest[0] > 150, String.format(Locale.GERMAN, "Volcano (Dorf), 300 m Umkreis: %d ʻōhiʻa, %d hāpuʻu", forest[0], forest[1]));
        check(forest[1] > 5, "Baumfarne im Regenwald");
        check(lava[0] + lava[1] == 0, "Strom von 2018: keine Pflanzen (" + (lava[0] + lava[1]) + ")");
        check(desert[0] + desert[1] < 5, "Kaʻū-Wüste: höchstens vereinzelt (" + (desert[0] + desert[1]) + ")");
        int[] holei = count(f, g, 19.2962, -155.0975);
        check(holei[0] < 15, "Küste bei Hōlei (trocken, Gischt), 300 m Umkreis: kaum ʻōhiʻa (" + holei[0] + ")");
        // Straße: kein Baum auf der Fahrbahn
        var w = com.dan.kilauea.terrain.ParkRoads.network().ways.get(0);
        double[] cam = {w.x[w.n / 2], 1200, w.z[w.n / 2]};
        f.setRanges(250, 250, 45, 260);
        for (int i = 0; i < 40 && !f.complete(cam); i++) f.update(g, cam, null, 200);
        f.update(g, cam, null, 0);
        int onRoad = 0;
        for (int b = 0; b < FloraField.BUCKETS; b++)
            for (int k = 0; k < f.count[b]; k++)
                if (com.dan.kilauea.terrain.ParkRoads.distance(f.bucket[b][8 * k], f.bucket[b][8 * k + 2]) < 4) onRoad++;
        check(onRoad == 0, "keine Pflanze auf der Straße (" + onRoad + ")");
    }

    static int[] count(FloraField f, HeightGrid g, double lat, double lon) {
        double[] cam = {Geo.x(lat, lon), Ground.height(Geo.x(lat, lon), Geo.z(lat)) + 2, Geo.z(lat)};
        f.setRanges(300, 300, 45, 260);
        for (int i = 0; i < 60 && !f.complete(cam); i++) f.update(g, cam, null, 200);
        f.update(g, cam, null, 0);
        int o = 0, fe = 0;
        for (int b = 0; b < FloraField.BUCKETS; b++) {
            if (b < FloraField.OHIA_VARIANTS * FloraField.OHIA_LODS) o += f.count[b]; else fe += f.count[b];
        }
        return new int[]{o, fe};
    }

    /** Gipfel: scharfe Wände, Lavaröhre, Gehen (Kante hält auf, Röhre und Pfad gehen), Dampf, Bodendecke. */
    static void summit(HeightGrid g) {
        System.out.println("Gipfel und Gehen");
        long t0 = System.nanoTime();
        var tiles = com.dan.kilauea.terrain.DetailTiles.load(new File(System.getProperty("kilauea.dir", System.getProperty("user.dir"))), g, null);
        com.dan.kilauea.terrain.DetailTiles.use(tiles);
        var st = com.dan.kilauea.terrain.DetailTiles.get("gipfel");
        check(tiles.size() == 5 && st.delta.length == com.dan.kilauea.terrain.SummitTile.NX * com.dan.kilauea.terrain.SummitTile.NZ,
                String.format(Locale.GERMAN, "5 Detailkacheln in %.1f s; Gipfel 12 × 8 km zu 4 m, Unterschied %.0f bis %.0f m", (System.nanoTime() - t0) / 1e9, st.minDelta, st.maxDelta));
        // Uēkahuna: steilste Stelle der Wand auf dem Weg zum Halemaʻumaʻu
        double ux = Place.UEKAHUNA.x(), uz = Place.UEKAHUNA.z(), steep = 0, steep0 = 0;
        for (int k = 0; k < 120; k++) {
            double f = k / 120.0 * 0.6, x = ux * (1 - f), z = uz * (1 - f), e = 8;
            double dx = ux * 0.6 / 120, dz = uz * 0.6 / 120, l = Math.hypot(dx, dz);
            double a = Ground.baseHeight(x - dx / l * e, z - dz / l * e), b = Ground.baseHeight(x + dx / l * e, z + dz / l * e);
            steep = Math.max(steep, Math.abs(a - b) / (2 * e));
            steep0 = Math.max(steep0, Math.abs(g.height(x - dx / l * e, z - dz / l * e) - g.height(x + dx / l * e, z + dz / l * e)) / (2 * e));
        }
        check(steep > 1.2 && steep > steep0 * 2, String.format(Locale.GERMAN, "Uēkahuna-Wand: Neigung %.1f (75-m-Raster %.1f)", steep, steep0));
        Ground.use(g);
        var tube = com.dan.kilauea.terrain.LavaTube.build();
        com.dan.kilauea.terrain.LavaTube.use(tube);
        check(tube.length > 150 && tube.length < 230, String.format(Locale.GERMAN, "Nāhuku: Röhre %.0f m, Decke von %.0f bis %.0f m", tube.length, tube.roofFrom, tube.roofTo));
        int mid = tube.n / 2;
        double cover = Ground.height(tube.x[mid], tube.z[mid]) - tube.floor[mid];
        check(cover > 6 && cover < 15, String.format(Locale.GERMAN, "Nāhuku: Boden in der Mitte %.1f m unter dem Gelände", cover));
        com.dan.kilauea.camera.WalkWorld.install();
        com.dan.kilauea.camera.OrbitCamera cam = new com.dan.kilauea.camera.OrbitCamera();
        // Uēkahuna: geradeaus in die Caldera — die Kante hält auf
        var sp = com.dan.kilauea.terrain.SummitPaths.WALK_SPOTS.get(0);
        cam.startWalk(sp.x(), sp.z(), Math.toDegrees(Math.atan2(-sp.x(), sp.z())));   // geradeaus zum Halemaʻumaʻu
        double foot0 = cam.wfoot;
        cam.slide = false;
        cam.kFwd = true;
        for (int i = 0; i < 600; i++) cam.update(0.1);
        cam.kFwd = false;
        cam.slide = true;
        check(cam.wfoot > foot0 - 25, String.format(Locale.GERMAN, "Uēkahuna: 84 m Richtung Halemaʻumaʻu gegangen, auf %.0f m (Start %.0f m), nicht über die Kante", cam.wfoot, foot0));
        // Röhre: vom Eingang zum Ausgang
        int i0 = Math.max(0, (int) tube.roofFrom - 3);
        double heading = Math.toDegrees(Math.atan2(tube.tx[i0], -tube.tz[i0]));
        cam.startWalk(tube.x[i0], tube.z[i0], heading);
        cam.wfoot = tube.floorAt(i0);
        double maxS = 0, minDepth = 0;
        for (int i = 0; i < 2400; i++) {
            double[] q = tube.locate(cam.wx, cam.wz);
            int k = (int) Math.min(tube.n - 2, q[0] + 3);
            cam.wyaw = -Math.atan2(tube.x[k] - cam.wx, -(tube.z[k] - cam.wz));
            cam.kFwd = true;
            cam.update(0.1);
            maxS = Math.max(maxS, q[0]);
            minDepth = Math.max(minDepth, Ground.height(cam.wx, cam.wz) - cam.wfoot);
        }
        cam.kFwd = false;
        check(maxS > tube.roofTo - 5, String.format(Locale.GERMAN, "Durch Nāhuku gegangen: %.0f von %.0f m, bis %.1f m unter dem Gelände", maxS, tube.length, minDepth));
        // Kīlauea Iki: dem Pfad folgen bis auf den Boden
        double[][] tr = com.dan.kilauea.terrain.SummitPaths.KILAUEA_IKI.xz();
        cam.startWalk(tr[0][0], tr[0][1], 90);
        double lowest = cam.wfoot;
        int stuck = 0;
        for (int p = 1; p < 10; p++) {
            for (int i = 0; i < 2000 && Math.hypot(tr[p][0] - cam.wx, tr[p][1] - cam.wz) > 3; i++) {
                cam.wyaw = -Math.atan2(tr[p][0] - cam.wx, -(tr[p][1] - cam.wz));
                double bx = cam.wx, bz = cam.wz;
                cam.kFwd = true;
                cam.update(0.1);
                if (Math.hypot(cam.wx - bx, cam.wz - bz) < 1e-3) { stuck++; break; }
            }
            lowest = Math.min(lowest, cam.wfoot);
        }
        cam.kFwd = false;
        check(stuck == 0 && lowest < 1075, String.format(Locale.GERMAN, "Kīlauea Iki Trail hinab bis %.0f m (Boden rund 1 060 m)%s", lowest, stuck > 0 ? ", " + stuck + "× steckengeblieben" : ""));
        // Dampf
        var steam = new com.dan.kilauea.atmos.Steam();
        double[] eye = {Place.KALUAPELE.x(), 1300, Place.KALUAPELE.z()};
        for (double t = 0; t < 150; t += 0.5) steam.update(0.5, new double[]{-7, 2.6}, eye, t);
        boolean fin = true;
        for (int i = 0; i < steam.outCount * 8; i++) if (!Float.isFinite(steam.out[i])) fin = false;
        check(steam.outCount > 300 && fin, String.format(Locale.GERMAN, "Dampf nach 150 s: %d Teilchen, alle endlich", steam.outCount));
        // Bodendecke
        var meadow = new com.dan.ground.Meadow(com.dan.kilauea.flora.HawaiiGround.biome(), com.dan.kilauea.flora.HawaiiGround.site());
        meadow.setSeason(280, 0);
        meadow.update(0, Geo.x(19.4163, -155.2378), Geo.z(19.4163));
        check(meadow.batch.nt > 1000, String.format(Locale.GERMAN, "Bodendecke am Weg nach Nāhuku: %,d Dreiecke", meadow.batch.nt));
    }

    /** Riftzonen und Küste (Phase 5): Grubenkrater, Kegel der Spalte 8, Pali, Meeresklippe, Felsbogen, Orte. */
    static void rifts(HeightGrid g) {
        System.out.println("Riftzonen und Küste");
        for (var p : com.dan.kilauea.terrain.DetailTiles.CHAIN_PITS) {
            double c = Ground.baseHeight(p.x(), p.z()), rim = com.dan.kilauea.terrain.DetailTile.rim(g, p);
            double raw = rim - g.height(p.x(), p.z());
            if (p.name().equals("Puhimau") || p.name().equals("Pauahi") || p.name().equals("Hiʻiaka"))
                check(rim - c > p.depth() * 0.8, String.format(Locale.GERMAN, "%s: %.0f m tief (Ziel %.0f m, 75-m-Raster %.0f m)", p.name(), rim - c, p.depth(), raw));
        }
        // Kegel der Spalte 8
        double fx = Geo.x(com.dan.kilauea.terrain.DetailTiles.FISSURE8[0], com.dan.kilauea.terrain.DetailTiles.FISSURE8[1]), fz = Geo.z(com.dan.kilauea.terrain.DetailTiles.FISSURE8[0]);
        double top = -1e9, ring = 0;
        for (int k = 0; k < 36; k++) {
            double a = k * Math.PI / 18;
            for (double r = 60; r <= 200; r += 10) top = Math.max(top, Ground.baseHeight(fx + Math.cos(a) * r, fz + Math.sin(a) * r));
            ring += Ground.baseHeight(fx + Math.cos(a) * 450, fz + Math.sin(a) * 450) / 36;
        }
        check(top - ring > 35 && top - ring < 80, String.format(Locale.GERMAN, "Kegel der Spalte 8: Rand %.0f m über dem Umland (USGS rund 55 m)", top - ring));
        // Hōlei Pali: steilste Stelle auf einer Linie Nord–Süd
        double steep = 0, steep0 = 0;
        for (double lat = 19.300; lat < 19.330; lat += 0.0001) {
            double x = Geo.x(lat, -155.160), z = Geo.z(lat), e = 8;
            steep = Math.max(steep, Math.abs(Ground.baseHeight(x, z - e) - Ground.baseHeight(x, z + e)) / (2 * e));
            steep0 = Math.max(steep0, Math.abs(g.height(x, z - e) - g.height(x, z + e)) / (2 * e));
        }
        check(steep > 1.0 && steep > steep0 * 1.5, String.format(Locale.GERMAN, "Hōlei Pali: Neigung bis %.1f (75-m-Raster %.1f)", steep, steep0));
        // Felsbogen und Meeresklippe
        var arch = com.dan.kilauea.terrain.SeaArch.build(true);
        var ruin = com.dan.kilauea.terrain.SeaArch.build(false);
        boolean fin = true;
        for (float v : arch.vertices) if (!Float.isFinite(v)) fin = false;
        for (float v : ruin.vertices) if (!Float.isFinite(v)) fin = false;
        double inland = Ground.baseHeight(arch.ox - arch.sx * 25, arch.oz - arch.sz * 25), sea = Ground.baseHeight(arch.ox + arch.sx * 8, arch.oz + arch.sz * 8);
        check(fin && arch.indices.length > ruin.indices.length, String.format(Locale.GERMAN, "Felsbogen Hōlei: %,d Dreiecke vor dem Einsturz, heute %,d (Pfeiler und Blöcke)", arch.indices.length / 3, ruin.indices.length / 3));
        check(inland - sea > 18, String.format(Locale.GERMAN, "Meeresklippe am Felsbogen: %.0f m über %.0f m Wasser", inland, -sea));
        // Orte: alle auf der Insel, jeder mit einem Blick
        int bad = 0;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Place p : Place.values()) {
            double h = Ground.baseHeight(p.x(), p.z());
            if (!Double.isFinite(h) || h < -30 || p.viewDist <= 0 || !names.add(p.label)) bad++;
        }
        check(bad == 0, String.format(Locale.GERMAN, "%d Orte, alle an Land oder an der Küste, jeder mit Blick", Place.values().length));
    }

    /** Magmasystem (Phase 6): Druck → Hebung, Beben, Gang; Massenbilanz; gleicher Ablauf; begrenzt. */
    static void magma() {
        System.out.println("Magmasystem");
        var m = new com.dan.kilauea.magma.MagmaSystem(42);
        double day = 86400;
        // 30 Tage normaler Zufluss: Druck steigt, Gipfel hebt sich, UWE neigt sich, kein Gang
        double p0 = m.sc.pressure(), u0 = m.uplift(m.hmm.x, m.hmm.z);
        m.step(30 * day);
        double p1 = m.sc.pressure(), u1 = m.uplift(m.hmm.x, m.hmm.z);
        double[] t1 = m.tilt(com.dan.kilauea.magma.MagmaSystem.UWE[0], com.dan.kilauea.magma.MagmaSystem.UWE[1]);
        check(p1 > p0 && u1 > u0 && m.activeDike() == null && m.dikes().isEmpty(),
                String.format(Locale.GERMAN, "30 Tage Zufluss: Druck %.2f → %.2f MPa, Gipfel hebt sich %.1f cm, Neigung UWE %.1f µrad, kein Gang", p0 / 1e6, p1 / 1e6, (u1 - u0) * 100, Math.hypot(t1[0], t1[1])));
        // Mogi gegen die Formel: Hebung über der Quelle (1−ν)ΔV/(π d²)
        double ana = 0.75 * 1e6 / (Math.PI * 1500 * 1500), mog = com.dan.kilauea.magma.MagmaSystem.mogiUplift(1e6, 1500, 0);
        check(Math.abs(ana - mog) < 1e-12, String.format(Locale.GERMAN, "Mogi: 1 Mio. m³ in 1,5 km Tiefe heben %.1f cm", mog * 100));
        // Zufluss verstärken: Druck bis zur Bruchgrenze, dann Gang in die Ostrift
        int q0 = m.quakesSince(m.time() - day).size();
        m.nextRift = com.dan.kilauea.magma.Rift.EAST;
        m.boost(20);
        double tStart = -1, pPeak = 0, uPeak = 0;
        for (int i = 0; i < 24 * 120 && tStart < 0; i++) {
            m.step(3600);
            pPeak = Math.max(pPeak, m.sc.pressure());
            uPeak = Math.max(uPeak, m.uplift(m.hmm.x, m.hmm.z));
            if (m.activeDike() != null) tStart = m.time();
        }
        int qBefore = m.quakesSince(tStart - day).size();
        check(tStart > 0, String.format(Locale.GERMAN, "Zufluss ×20: Gang nach %.0f Tagen bei %.1f MPa, %d Beben am Tag davor (vorher %d)", (tStart - 30 * day) / day, pPeak / 1e6, qBefore, q0));
        m.boost(1);
        var dk = m.activeDike();
        // Gang laufen lassen: Spitze wandert, Beben folgen der Spitze, Gipfel sackt
        double tipNear = 0;
        int near = 0, far = 0;
        for (int i = 0; i < 24 * 10 && dk.active(); i++) {
            double tq = m.time();
            m.step(3600);
            double[] tip = dk.tip();
            for (var q : m.quakesSince(tq)) {
                if (q.kind() != com.dan.kilauea.magma.Quake.Kind.DIKE) continue;
                if (Math.hypot(q.x() - tip[0], q.z() - tip[1]) < 2500) near++; else far++;
            }
        }
        double uAfter = m.uplift(m.hmm.x, m.hmm.z);
        check(dk.length() > 5000 && !dk.active(), String.format(Locale.GERMAN, "Gang in der %s: %.1f km, %.2f km³", dk.rift.label, dk.length() / 1000, dk.volume() / 1e9));
        check(near > 20 && near > 4 * far, String.format(Locale.GERMAN, "Bebenschwarm wandert mit der Spitze: %d nahe, %d weiter weg", near, far));
        check(uAfter < uPeak - 0.05 && m.sc.pressure() < 3.5e6, String.format(Locale.GERMAN, "Gipfel sackt um %.0f cm, Druck %.1f MPa", (uPeak - uAfter) * 100, m.sc.pressure() / 1e6));
        // Massenbilanz
        double balance = m.supplied() - m.erupted() - m.intruded() - (m.stored() - (0.45 * 10e6 * 10 + 0.45 * 10e6 * 1));
        check(Math.abs(balance) < 1, String.format(Locale.GERMAN, "Massenbilanz: zugeflossen %.3f km³ = gespeichert + Gang (Rest %.2e m³)", m.supplied() / 1e9, balance));
        // gleicher Startwert, gleicher Ablauf
        var a = new com.dan.kilauea.magma.MagmaSystem(7);
        var b = new com.dan.kilauea.magma.MagmaSystem(7);
        a.boost(20); b.boost(20);
        for (int i = 0; i < 400; i++) { a.step(3600); b.step(3600); }
        var qa = a.quakesSince(0);
        var qb = b.quakesSince(0);
        check(qa.equals(qb) && a.sc.pressure() == b.sc.pressure(), String.format(Locale.GERMAN, "Gleicher Startwert, gleicher Ablauf (%d Beben)", qa.size()));
        // Gipfelschlot: Episoden
        var e = new com.dan.kilauea.magma.MagmaSystem(3);
        e.summitVent = true;
        int opens = 0;
        boolean was = false;
        double maxP = 0;
        for (int i = 0; i < 24 * 365; i++) {
            e.step(3600);
            if (e.ventOpen() && !was) opens++;
            was = e.ventOpen();
            maxP = Math.max(maxP, Math.max(e.sc.pressure(), e.hmm.pressure()));
        }
        boolean finite = Double.isFinite(e.sc.pressure()) && Double.isFinite(e.hmm.pressure());
        check(finite && opens >= 3 && maxP < 12e6, String.format(Locale.GERMAN, "Ein Jahr mit Gipfelschlot: %d Episoden, Druck höchstens %.1f MPa, %.3f km³ ausgeflossen", opens, maxP / 1e6, e.erupted() / 1e9));
    }

    // ------------------------------------------------------------------ Phase 7: Lava

    /** Schwerpunkt der beweglichen Lava (x, z), Volumen, ob Lava oberhalb des Schlots liegt. */
    static double[] centroid(com.dan.kilauea.lava.LavaField f) {
        double[] c = new double[4];
        f.forEachCovered((x, z, g, r, h, t, aa, u, fl, arr) -> {
            double v = h + r;
            c[0] += x * v;
            c[1] += z * v;
            c[2] += v;
            c[3] = Math.min(c[3], z);
        });
        return new double[]{c[0] / c[2], c[1] / c[2], c[2], c[3]};
    }

    static void lava() {
        System.out.println("Lava-Modell");
        // Stoffwerte: Zähigkeit und Fließgrenze steigen beim Abkühlen
        double e1 = com.dan.kilauea.lava.Rheology.viscosity(1150), e2 = com.dan.kilauea.lava.Rheology.viscosity(1050);
        double y1 = com.dan.kilauea.lava.Rheology.yieldStrength(1150), y2 = com.dan.kilauea.lava.Rheology.yieldStrength(1050);
        check(e1 > 50 && e1 < 1000 && e2 / e1 > 1000 && y2 > y1 * 50, String.format(Locale.GERMAN,
                "Zähigkeit %.0f Pa·s am Schlot, %.1e bei 1 050 °C; Fließgrenze %.0f → %.0f Pa", e1, e2, y1, y2));
        // Bingham: unter der kritischen Dicke fließt nichts, darüber mit der dritten Potenz (Newton-Grenzfall)
        double hcr = y1 / (com.dan.kilauea.lava.Rheology.RHO * com.dan.kilauea.lava.Rheology.G * 0.05);
        double q0 = com.dan.kilauea.lava.Rheology.flux(0.9 * hcr, 1150, 0.5, 10, 10);
        double qa = com.dan.kilauea.lava.Rheology.flux(40 * hcr, 1150, 0.5, 10, 10), qb = com.dan.kilauea.lava.Rheology.flux(80 * hcr, 1150, 0.5, 10, 10);
        check(q0 == 0 && qa > 0 && Math.abs(qb / qa - 8) < 0.7, String.format(Locale.GERMAN,
                "Bingham: unter %.2f m steht die Lava; doppelte Dicke, %.1f-facher Fluss", hcr, qb / qa));
        // Krustendicke nach Hon u. a. 1994
        check(Math.abs(com.dan.kilauea.lava.Rheology.crust(3600 * 100) - 0.779) < 1e-6, "Kruste nach 100 Stunden 0,78 m (Hon u. a. 1994)");

        // schiefe Ebene, 3 % nach Süden, 10 m³/s, eine Stunde
        com.dan.kilauea.lava.LavaField.Terrain plane = (x, z) -> 1000 - 0.03 * z;
        var f = new com.dan.kilauea.lava.LavaField(plane, 10, 128, 11);
        var v = new com.dan.kilauea.lava.Vent("Prüfschlot", 0, 0, 10);
        f.addVent(v);
        f.step(3600);
        var s = f.stats(0, 0);
        double[] c = centroid(f);
        check(s.finite && c[1] > 150 && c[3] > -60 && s.lowest < 1000 - 0.03 * 300, String.format(Locale.GERMAN,
                "Ebene 3 %%, 10 m³/s, 1 h: Strom fließt bergab, Schwerpunkt %.0f m unterhalb, höchstens %.0f m oberhalb des Schlots, Front %.2f km", c[1], -c[3], s.length / 1000));
        check(s.frontT < s.maxT - 15 && s.meanT > com.dan.kilauea.lava.Rheology.T_SOLID && s.maxT <= com.dan.kilauea.lava.Rheology.T_VENT + 0.5
                        && s.minT >= com.dan.kilauea.lava.Rheology.T_AIR, String.format(Locale.GERMAN,
                "Strom kühlt: Schlot %.0f °C, Mittel %.0f, Front %.0f; alles zwischen Luft und Schlot", s.maxT, s.meanT, s.frontT));
        double bal = (s.mobile + s.rock + s.collapsed - s.erupted) / s.erupted;
        check(Math.abs(bal) < 1e-9, String.format(Locale.GERMAN, "Volumen: %,.0f m³ gefördert = beweglich + erstarrt (Rest %.1e)", s.erupted, bal));
        var g2 = new com.dan.kilauea.lava.LavaField(plane, 10, 128, 11);
        g2.addVent(new com.dan.kilauea.lava.Vent("Prüfschlot", 0, 0, 10));
        g2.step(3600);
        check(g2.fingerprint() == f.fingerprint() && g2.steps() == f.steps(), String.format(Locale.GERMAN, "Gleicher Ablauf: zweimal %d Schritte, gleiche Prüfsumme", f.steps()));
        // nach dem Versiegen: die Lava kommt zur Ruhe und erstarrt von den Rändern her
        v.rate = 0;
        f.step(4 * 3600);
        var s2 = f.stats(0, 0);
        check(s2.rock > s.rock * 3 && s2.activeArea < s2.area && s2.length < s.length * 1.6, String.format(Locale.GERMAN,
                "Schlot aus, 4 h später: Front %.2f km, erstarrt %,.0f m³ (vorher %,.0f), Kern im Mittel %.0f °C", s2.length / 1000, s2.rock, s.rock, s2.meanT));

        // ebene Fläche: die Lava breitet sich rund aus
        var fl = new com.dan.kilauea.lava.LavaField((x, z) -> 500, 10, 64, 3);
        fl.addVent(new com.dan.kilauea.lava.Vent("Mitte", 0, 0, 5));
        fl.step(1800);
        double[] ext = new double[4];
        fl.forEachCovered((x, z, g, r, h, t, aa, u, fg, arr) -> {
            ext[0] = Math.min(ext[0], x); ext[1] = Math.max(ext[1], x); ext[2] = Math.min(ext[2], z); ext[3] = Math.max(ext[3], z);
        });
        double ratio = (ext[1] - ext[0]) / (ext[3] - ext[2]);
        double hMean = fl.stats(0, 0).mobile / fl.stats(0, 0).activeArea;
        check(ratio > 0.85 && ratio < 1.18 && hMean > 0.2, String.format(Locale.GERMAN, "Ebene Fläche: rund (%.0f × %.0f m), im Mittel %.2f m dick", ext[1] - ext[0], ext[3] - ext[2], hMean));

        // Pāhoehoe und ʻaʻā nach Förderrate und Gefälle
        var lo = new com.dan.kilauea.lava.LavaField((x, z) -> 1000 - 0.02 * z, 10, 128, 5);
        lo.addVent(new com.dan.kilauea.lava.Vent("klein", 0, 0, 3));
        lo.step(4 * 3600);
        var hi = new com.dan.kilauea.lava.LavaField((x, z) -> 1000 - 0.08 * z, 10, 128, 5);
        hi.addVent(new com.dan.kilauea.lava.Vent("groß", 0, 0, 100));
        hi.step(3600);
        double aLo = lo.stats(0, 0).aaShare, aHi = hi.stats(0, 0).aaShare;
        check(aLo < 0.1 && aHi > 0.4, String.format(Locale.GERMAN, "3 m³/s auf 2 %%: %.0f %% ʻaʻā; 100 m³/s auf 8 %%: %.0f %% ʻaʻā (Übergang bei hoher Rate und Gefälle)", aLo * 100, aHi * 100));

        // Röhren: kleine Rate, Stunden; Dach, Oberlichter, Leerlaufen nach dem Versiegen
        var t = new com.dan.kilauea.lava.LavaField((x, z) -> 1000 - 0.03 * z, 10, 128, 7);
        var tv = new com.dan.kilauea.lava.Vent("Röhre", 0, 0, 3);
        t.addVent(tv);
        t.step(6 * 3600);
        var ts = t.stats(0, 0);
        double[] slope = tubeCooling(t, 0, 0);
        check(ts.roofArea > 1e4 && ts.skylights >= 1, String.format(Locale.GERMAN, "3 m³/s, 6 h: %.1f ha unter Dach, %d Oberlichter", ts.roofArea / 1e4, ts.skylights));
        check(slope[0] > slope[1] && slope[0] < 0, String.format(Locale.GERMAN, "Unter Dach kühlt die Lava langsamer: %.0f °C/km gegen %.0f °C/km offen", slope[0], slope[1]));
        tv.rate = 0;
        t.step(2 * 3600);
        int[] dr = new int[2];
        t.forEachCovered((x, z, g, r, h, tt, aa, u, fg, arr) -> {
            if ((fg & com.dan.kilauea.lava.LavaField.TUBE) != 0) dr[0]++;
            if ((fg & com.dan.kilauea.lava.LavaField.DRAINED) != 0) dr[1]++;
        });
        check(dr[1] > dr[0] / 3, String.format(Locale.GERMAN, "Schlot aus: %d von %d Röhrenzellen leergelaufen, das Dach bleibt", dr[1], dr[0]));

        // Meer: Lava erstarrt im Wasser und baut neues Land
        var m = new com.dan.kilauea.lava.LavaField((x, z) -> 15 - 0.05 * z, 10, 96, 9);
        m.addVent(new com.dan.kilauea.lava.Vent("Küste", 0, 0, 20));
        m.step(3 * 3600);
        int[] sea = new int[2];
        m.forEachCovered((x, z, g, r, h, tt, aa, u, fg, arr) -> {
            if (g < 0 && r > 0) sea[0]++;
            if (g < 0 && g + r + h > 0) sea[1]++;
        });
        var ms = m.stats(0, 0);
        check(sea[0] > 10 && sea[1] > 0 && Math.abs((ms.mobile + ms.rock - ms.erupted) / ms.erupted) < 1e-9, String.format(Locale.GERMAN,
                "Meer: in %d Zellen erstarrt Lava unter Wasser, %d ragen als neues Land heraus", sea[0], sea[1]));

        // echtes Gelände: Mauna Ulu, 10 m³/s, eine Stunde (begrenzt, bergab)
        if (Ground.grid() != null) {
            var site = com.dan.kilauea.lava.VentSite.MAUNA_ULU;
            var r = new com.dan.kilauea.lava.LavaField(Ground::solidHeight, 10, 256, 1);
            r.addVent(com.dan.kilauea.lava.Vent.at(site, site.rate));
            long t0 = System.nanoTime();
            r.step(3600);
            double cpu = (System.nanoTime() - t0) / 1e9;
            var rs = r.stats(site.x(), site.z());
            double vh = Ground.height(site.x(), site.z());
            double[] rc = centroid(r);
            check(rs.finite && !r.full() && r.blocksUsed() < 64 && cpu < 20 && rs.lowest < vh - 20 && rc[1] > site.z(), String.format(Locale.GERMAN,
                    "Mauna Ulu, 10 m³/s, 1 h: begrenzt (%d Blöcke, %d Schritte, %.1f s), Front %.0f m tiefer als der Schlot, %.2f km lang",
                    r.blocksUsed(), r.steps(), cpu, vh - rs.lowest, rs.length / 1000));
            check(Math.abs((rs.mobile + rs.rock - rs.erupted) / rs.erupted) < 1e-9 && rs.maxT <= 1150.5 && rs.minT >= 20, String.format(Locale.GERMAN,
                    "Mauna Ulu: Volumen geht auf, Kern %.0f…%.0f °C", rs.minT, rs.maxT));
            // Halemaʻumaʻu: die Lava bleibt im Krater
            var hs = com.dan.kilauea.lava.VentSite.HALEMAUMAU;
            var hm = new com.dan.kilauea.lava.LavaField(Ground::solidHeight, 10, 64, 1);
            hm.addVent(com.dan.kilauea.lava.Vent.at(hs, hs.rate));
            hm.step(3600);
            var hst = hm.stats(hs.x(), hs.z());
            check(hst.finite && hst.length < 700 && hst.highest < 900, String.format(Locale.GERMAN,
                    "Halemaʻumaʻu, 20 m³/s, 1 h: die Lava bleibt im Krater (%.0f m weit, Oberfläche %.0f…%.0f m)", hst.length, hst.lowest, hst.highest));
        }
    }

    /** Abkühlung entlang des Abstands zum Schlot (°C/km): unter Dach, offen (nur fließende Zellen). */
    static double[] tubeCooling(com.dan.kilauea.lava.LavaField f, double vx, double vz) {
        double[][] a = new double[2][5];
        f.forEachCovered((x, z, g, r, h, t, aa, u, fg, arr) -> {
            if (h < 0.3 || u < 0.03 || (fg & com.dan.kilauea.lava.LavaField.VENT) != 0) return;
            boolean roofed = (fg & com.dan.kilauea.lava.LavaField.TUBE) != 0 && (fg & com.dan.kilauea.lava.LavaField.SKY) == 0;
            double[] s = a[roofed ? 0 : 1];
            double d = Math.hypot(x - vx, z - vz) / 1000;
            s[0] += d; s[1] += t; s[2] += d * d; s[3] += d * t; s[4]++;
        });
        double[] out = new double[2];
        for (int k = 0; k < 2; k++) {
            double[] s = a[k];
            out[k] = s[4] < 5 ? 0 : (s[4] * s[3] - s[0] * s[1]) / (s[4] * s[2] - s[0] * s[0]);
        }
        return out;
    }

    static void glut() {
        System.out.println("Lava im Bild (ohne Grafikkarte)");
        // Planck × CIE 1931: unter rund 550 °C kaum sichtbar, bei 1 150 °C orangegelb, rot vor grün
        double[] lo = new double[3], hi = new double[3], mid = new double[3];
        com.dan.kilauea.lava.Glow.radiance(550, lo);
        com.dan.kilauea.lava.Glow.radiance(900, mid);
        com.dan.kilauea.lava.Glow.radiance(1150, hi);
        boolean mono = true;
        double prev = -1;
        double[] q = new double[3];
        for (int t = 400; t <= 1250; t += 10) {
            com.dan.kilauea.lava.Glow.radiance(t, q);
            double y = 0.2126 * q[0] + 0.7152 * q[1] + 0.0722 * q[2];
            if (!(y >= prev) || !Double.isFinite(y)) mono = false;
            prev = y;
        }
        check(mono && lo[0] < hi[0] * 2e-3 && hi[0] / hi[1] > 5 && hi[0] / hi[1] < 12 && hi[0] < 22 && mid[0] / mid[1] > hi[0] / hi[1],
                String.format(Locale.GERMAN, "Glut nach Planck: 550 °C %.1e, 1 150 °C R %.2f G %.2f (Sonne 22); heißer = heller und gelber", lo[0], hi[0], hi[1]));
        double c1 = com.dan.kilauea.lava.GlowMap.crackShare(1150, 1150), c2 = com.dan.kilauea.lava.GlowMap.crackShare(500, 1140),
                c3 = com.dan.kilauea.lava.GlowMap.crackShare(120, 1100);
        check(Math.abs(c1 - 1) < 1e-9 && c2 < c1 && c3 < c2 && Math.abs(c3 - 0.003) < 1e-9, String.format(Locale.GERMAN,
                "Risse: frisch %.0f %%, angekühlt %.1f %%, alte Kruste %.1f %%", 100 * c1, 100 * c2, 100 * c3));

        // Strom auf der Ebene: Blöcke für die Grafik endlich, Lichtschein nimmt mit dem Abstand ab
        var f = new com.dan.kilauea.lava.LavaField((x, z) -> 1000 - 0.03 * z, 10, 128, 11);
        f.addVent(new com.dan.kilauea.lava.Vent("Prüfschlot", 0, 0, 10));
        f.step(1800);
        float[] a = new float[4 * com.dan.kilauea.lava.LavaField.BB], c = new float[4 * com.dan.kilauea.lava.LavaField.BB],
                v = new float[2 * com.dan.kilauea.lava.LavaField.BB];
        boolean fin = true;
        int hot = 0;
        for (int b = 0; b < f.blocksUsed(); b++) {
            f.exportBlock(b, a, c, v);
            for (float x : a) if (!Float.isFinite(x)) fin = false;
            for (float x : c) if (!Float.isFinite(x)) fin = false;
            for (float x : v) if (!Float.isFinite(x)) fin = false;
            for (int l = 0; l < com.dan.kilauea.lava.LavaField.BB; l++) if (a[4 * l + 1] > 0.02 && a[4 * l + 3] > 1100) hot++;
        }
        check(fin && hot > 20, String.format(Locale.GERMAN, "Lava für die Grafik: %d Blöcke endlich, %d Zellen über 1 100 °C", f.blocksUsed(), hot));
        var gm = new com.dan.kilauea.lava.GlowMap();
        gm.update(f, new java.util.ArrayList<>());
        double near = 0, far = Double.MAX_VALUE, sum = 0;
        boolean gfin = true;
        int G = com.dan.kilauea.lava.GlowMap.G;
        for (int k = 0; k < G * G; k++) {
            float r = gm.rgb[3 * k];
            if (!Float.isFinite(r) || r < 0) gfin = false;
            near = Math.max(near, r);
            far = Math.min(far, r);
            sum += r;
        }
        double cornerR = gm.rgb[0];
        check(gfin && !gm.empty && near > 0 && cornerR < near * 0.05, String.format(Locale.GERMAN,
                "Lichtschein: am Strom %.2e, in der Ecke der Karte %.2e (Engine-Einheiten)", near, cornerR));

        // Fontäne 100 m: die Fetzen steigen bis etwa H, alles endlich; ohne Nachschub fällt sie zusammen
        var fo = new com.dan.kilauea.lava.Fountain();
        double[] src = {-20, 0, 20, 0, 1000, 100}, wind = {0, 0};
        double top = 0;
        boolean ffin = true;
        for (int i = 0; i < 400; i++) {
            fo.update(0.1, src, wind);
            for (int k = 0; k < fo.outCount; k++) {
                for (int j = 0; j < 8; j++) if (!Float.isFinite(fo.out[8 * k + j])) ffin = false;
                if (fo.out[8 * k + 3] > 0 && fo.out[8 * k + 7] > 0) top = Math.max(top, fo.out[8 * k + 1] - 1000);
            }
        }
        int during = fo.count();
        for (int i = 0; i < 300; i++) fo.update(0.1, (double[]) null, wind);
        check(ffin && top > 85 && top < 115 && during > 500 && fo.count() < during / 4, String.format(Locale.GERMAN,
                "Fontäne 100 m: höchster Fetzen %.0f m, %d Teilchen; 30 s nach dem Ende noch %d", top, during, fo.count()));
        var low = new com.dan.kilauea.lava.Fountain();
        low.update(0.1, new double[]{0, 0, 0, 0, 1000, 25}, wind);
        int lowCore = 0;
        for (int k = 0; k < low.outCount; k++) if (low.out[8 * k + 3] < 0) lowCore++;
        check(lowCore == 0, "Niedrige Fontäne (25 m): keine Kernflecken, nur Fetzen");

        // Lavasee: Nähte im Bild, Grube 45 m unter dem Rand
        var L = com.dan.kilauea.lava.LavaLake.halemaumau(5);
        for (int k = 0; k < 30; k++) L.step(60);
        var img = new com.dan.kilauea.lava.LakeImage();
        img.update(L);
        int inside = 0;
        for (int k = 0; k < com.dan.kilauea.lava.LakeImage.N * com.dan.kilauea.lava.LakeImage.N; k++) if (img.rgba[4 * k + 3] != 0) inside++;
        check(img.seamShare > 0.01 && img.seamShare < 0.4 && inside > 1000 && img.version == 1, String.format(Locale.GERMAN,
                "Seebild: %.1f %% Nähte, %d Bildpunkte auf dem See", 100 * img.seamShare, inside));
        L.rimY = 900;
        double inPit = L.pit(L.cx, L.cz, 905), outPit = L.pit(L.cx + 3 * L.a, L.cz, 905);
        check(Math.abs(inPit - (900 - com.dan.kilauea.lava.LavaLake.PIT_DEPTH)) < 1e-6 && outPit == 905 && L.surfaceY() < 900 && L.surfaceY() > inPit,
                String.format(Locale.GERMAN, "Grube: Boden %.0f m, Spiegel %.1f m, außen unverändert", inPit, L.surfaceY()));
    }

    static void sea() {
        System.out.println("Meer und Brandung");
        double T = 14;
        double L0 = com.dan.kilauea.sea.Waves.deepLength(T);
        double errE = 0;
        for (double d = 0.5; d < 300; d *= 1.3) {
            double ke = com.dan.kilauea.sea.Waves.eckart(T, d), kx = com.dan.kilauea.sea.Waves.k(T, d);
            errE = Math.max(errE, Math.abs(ke / kx - 1));
        }
        double c1 = com.dan.kilauea.sea.Waves.celerity(T, 1), cs = Math.sqrt(9.81);
        check(Math.abs(L0 - 306) < 1 && errE < 0.06 && Math.abs(c1 / cs - 1) < 0.03, String.format(Locale.GERMAN,
                "Dispersion: 14 s → %.0f m im Tiefen; in 1 m Tiefe %.2f m/s (√(g d) = %.2f); Eckart weicht höchstens %.1f %% ab", L0, c1, cs, 100 * errE));
        double ksDeep = com.dan.kilauea.sea.Waves.shoaling(T, 1000), ks20 = com.dan.kilauea.sea.Waves.shoaling(T, 20), ks2 = com.dan.kilauea.sea.Waves.shoaling(T, 2);
        double db = com.dan.kilauea.sea.Waves.breakingDepth(1.0, T);
        check(Math.abs(ksDeep - 1) < 0.01 && ks20 < 1 && ks2 > 1.3 && db > 1.0 && db < 2.5, String.format(Locale.GERMAN,
                "Aufsteilen: K_s %.2f im Tiefen, %.2f in 20 m, %.2f in 2 m; 1 m Swell bricht in %.1f m Tiefe", ksDeep, ks20, ks2, db));
        var bA = com.dan.kilauea.sea.Waves.breaker(0.02, 1, T);
        var bB = com.dan.kilauea.sea.Waves.breaker(0.1, 1, T);
        var bC = com.dan.kilauea.sea.Waves.breaker(1.0, 1, T);
        check(bA == com.dan.kilauea.sea.Waves.Breaker.SCHWALL && bB == com.dan.kilauea.sea.Waves.Breaker.STURZ && bC == com.dan.kilauea.sea.Waves.Breaker.REFLEXION,
                "Brecher nach Iribarren: Strand 1:50 " + bA.label + ", 1:10 " + bB.label + ", Klippe " + bC.label);
        // Kämme vor dem Ufer laufen mit √(g d) auf das Ufer zu
        double m = 0.03, s0 = 100, dt = 0.01;
        double p0 = com.dan.kilauea.sea.Waves.shorePhase(s0, m, T, 0), p1 = com.dan.kilauea.sea.Waves.shorePhase(s0, m, T, dt);
        double dpds = (com.dan.kilauea.sea.Waves.shorePhase(s0 + 0.01, m, T, 0) - com.dan.kilauea.sea.Waves.shorePhase(s0 - 0.01, m, T, 0)) / 0.02;
        double speed = -(p1 - p0) / dt / dpds;                       // ds/dt bei fester Phase
        check(Math.abs(speed + Math.sqrt(9.81 * m * s0)) < 0.02 * Math.sqrt(9.81 * m * s0), String.format(Locale.GERMAN,
                "Kämme in 3 m Tiefe laufen mit %.2f m/s auf das Ufer zu (√(g d) = %.2f)", -speed, Math.sqrt(9.81 * m * s0)));

        // Raster an einem geraden Ufer: Abstand und Richtung
        var ns = com.dan.kilauea.sea.Nearshore.build((x, z) -> (z - 300) * 0.05, 0, 0);
        double sd = ns.shoreDistance(0, -500);
        int k = 4 * ((com.dan.kilauea.sea.Nearshore.N / 2) * com.dan.kilauea.sea.Nearshore.N + com.dan.kilauea.sea.Nearshore.N / 2);
        check(Math.abs(sd - 800) < 12 && ns.data[k + 3] < -0.99 && ns.coast.cliffs() == 0 && ns.coast.points.size() > 400, String.format(Locale.GERMAN,
                "Gerades Ufer: 800 m vor der Küste %.0f m Abstand, Richtung zum Meer (%.2f, %.2f), %d Uferpunkte, keine Klippe",
                sd, ns.data[k + 2], ns.data[k + 3], ns.coast.points.size()));
        var nc = com.dan.kilauea.sea.Nearshore.build((x, z) -> z < 0 ? -15 : 20 + 0.01 * z, 0, 0);
        check(nc.coast.cliffs() > 0.9 * nc.coast.points.size() && nc.coast.points.size() > 400,
                "Steilufer: " + nc.coast.cliffs() + " von " + nc.coast.points.size() + " Uferpunkten sind Klippe");
        // Gischt: Swell läuft gegen die Klippe (nach Norden, +z) — oder von ihr weg
        var sp = new com.dan.kilauea.sea.Spray();
        sp.coast(nc.coast);
        double[] wind = {-6, 0}, cam = {0, 50, -200};
        boolean fin = true;
        double top = 0;
        for (int i = 0; i < 600; i++) {
            sp.update(0.1, i * 0.1, T, new double[]{0, 1}, 1.5, wind, cam);
            for (int q = 0; q < sp.outCount * 8; q++) if (!Float.isFinite(sp.out[q])) fin = false;
            top = Math.max(top, sp.top());
        }
        var sp2 = new com.dan.kilauea.sea.Spray();
        sp2.coast(nc.coast);
        for (int i = 0; i < 600; i++) sp2.update(0.1, i * 0.1, T, new double[]{0, -1}, 1.5, wind, cam);
        check(fin && sp.bursts >= 4 && top > 4 && top < 30 && sp2.bursts == 0, String.format(Locale.GERMAN,
                "Gischt: 60 s Swell 1,5 m gegen die Klippe: %d Würfe, bis %.0f m hoch; vom Ufer weg: %d", sp.bursts, top, sp2.bursts));

        // Am echten Ufer: Hōlei
        long t0 = System.nanoTime();
        var holei = com.dan.kilauea.sea.Nearshore.build(Ground::height, Place.HOLEI_ARCH.x(), Place.HOLEI_ARCH.z());
        double ms = (System.nanoTime() - t0) / 1e6;
        int near = 0;
        for (var q : holei.coast.points) if (q.cliff && Math.hypot(q.x - Place.HOLEI_ARCH.x(), q.z - Place.HOLEI_ARCH.z()) < 2500) near++;
        double sArch = holei.shoreDistance(Place.HOLEI_ARCH.x(), Place.HOLEI_ARCH.z() + 400);
        check(near > 40 && sArch > 150 && ms < 20000, String.format(Locale.GERMAN,
                "Hōlei: %d Klippenpunkte im Umkreis von 2,5 km; 400 m südlich des Bogens %.0f m vor dem Ufer; Raster in %.1f s", near, sArch, ms / 1000));
    }

    static void entry() {
        System.out.println("Meereseintritt und unter Wasser");
        // Küstenebene 2 % geneigt, ab z = 500 m eine Klippe von 15 m in 12 m tiefes Wasser
        com.dan.kilauea.lava.LavaField.Terrain t = (x, z) -> z < 500 ? 15 - 0.02 * z + 0.02 * 500 - 10 : -12 - 0.05 * (z - 500);
        var f = new com.dan.kilauea.lava.LavaField(t, 10, 128, 7);
        f.addVent(new com.dan.kilauea.lava.Vent("Prüfschlot", 0, 0, 6));
        var e = new com.dan.kilauea.lava.SeaEntry();
        e.allowCollapse = false;
        int falls = 0;
        double heat = 0;
        for (int k = 0; k < 18; k++) {
            f.step(600);
            e.update(f);
            falls = Math.max(falls, e.fallCount);
            heat = Math.max(heat, e.totalHeat());
        }
        var s = f.stats(0, 0);
        check(e.count > 0 && heat > 50 && falls > 0 && e.newLand > 0, String.format(Locale.GERMAN,
                "Klippe 15 m, 6 m³/s, 3 h: %d Eintrittsstellen, bis %,.0f MW ins Wasser, %d Lavafälle, neues Land %.2f ha", e.count, heat, falls, e.newLand / 1e4));
        double v = f.collapse(e.count > 0 ? e.entries[0] : 0, e.count > 0 ? e.entries[1] : 520, 60, -2);
        var s2 = f.stats(0, 0);
        double bal = (s2.mobile + s2.rock + s2.collapsed - s2.erupted) / s2.erupted;
        check(v > 0 && Math.abs(bal) < 1e-9 && f.newLand() < e.newLand, String.format(Locale.GERMAN,
                "Abbruch des Deltas: %,.0f m³ ins Meer; gefördert = beweglich + erstarrt + abgebrochen (Rest %.1e)", v, bal));
        // Abbrüche über die Zeit (fester Keim)
        var f2 = new com.dan.kilauea.lava.LavaField(t, 10, 128, 7);
        f2.addVent(new com.dan.kilauea.lava.Vent("Prüfschlot", 0, 0, 6));
        var e2 = new com.dan.kilauea.lava.SeaEntry();
        for (int k = 0; k < 72; k++) { f2.step(300); e2.update(f2); }
        check(e2.collapses >= 1 && f2.stats(0, 0).finite, String.format(Locale.GERMAN, "6 h: %d Abbrüche, zusammen %,.0f m³; neues Land jetzt %.2f ha",
                e2.collapses, e2.collapsedVolume, e2.newLand / 1e4));
        // Glutbrocken und Lavafälle
        var lit = new com.dan.kilauea.lava.Littoral();
        boolean fin = true;
        double top = -1e9;
        for (int k = 0; k < 600; k++) {
            lit.update(0.1, e, new double[]{-6, 0});
            for (int q = 0; q < lit.outCount * 8; q++) if (!Float.isFinite(lit.out[q])) fin = false;
            top = Math.max(top, lit.top());
        }
        check(fin && lit.bursts >= 1 && top > 2 && top < 40 && lit.count() > 0, String.format(Locale.GERMAN,
                "Glutbrocken: %d Explosionen in 60 s, bis %.0f m hoch; %d glühende Teilchen in Fällen und Würfen", lit.bursts, top, lit.count()));
        // Kissenlava: vor dem Eintritt hangabwärts, mindestens zwei Knospungen je Minute
        var pil = new com.dan.kilauea.sea.Pillows();
        com.dan.kilauea.sea.Coast.Terrain sea = (x, z) -> -0.15 * (z - 100);
        double[] en = {0, 100, 2000};
        double[] cam = {0, -5, 140};
        for (int k = 0; k < 480; k++) pil.update(0.25, en, sea, cam);
        boolean deep = true, sizes = true;
        for (int i = 0; i < pil.count(); i++) {
            double[] sz = pil.size(i);
            if (sz[3] > -1.0) deep = false;
            if (sz[0] < 0.7 || sz[0] > 2.1 || sz[2] < 0.3 || sz[2] > 0.75) sizes = false;
        }
        int lastMin = pil.budsWithin(60);
        check(lastMin >= 2 && deep && sizes && pil.count() >= 5, String.format(Locale.GERMAN,
                "Kissenlava: %d Kissen in 2 min, %d Knospungen in der letzten Minute, alle unter Wasser, 0,8–2 m lang", pil.count(), lastMin));
        check(pil.bubbleCount() > 20 && pil.bubbleTop() < 0, String.format(Locale.GERMAN, "Blasen: %d steigen auf, höchste %.1f m (unter dem Spiegel)", pil.bubbleCount(), pil.bubbleTop()));
        // ohne Eintritt knospt nichts mehr
        int before = pil.buds;
        for (int k = 0; k < 240; k++) pil.update(0.25, null, sea, cam);
        check(pil.buds == before, "Ohne Lava im Meer knospt nichts (" + (pil.buds - before) + ")");
        // Optik: Snellsches Fenster
        double win = Math.toDegrees(Math.asin(1 / 1.333));
        check(Math.abs(win - 48.6) < 0.1 && Math.exp(-0.38 * 20) < 1e-3 && Math.exp(-0.035 * 20) > 0.4, String.format(Locale.GERMAN,
                "Unter Wasser: Snellsches Fenster %.1f°; in 20 m bleibt %.2f %% des Rots und %.0f %% des Blaus", win, 100 * Math.exp(-0.38 * 20), 100 * Math.exp(-0.035 * 20)));
    }

    static void weather(HeightGrid g) {
        System.out.println("Luft und Licht");
        var W = new com.dan.kilauea.atmos.Weather();
        W.terrain(g);
        long t0 = System.nanoTime();
        W.update(7.5, 70, 14);
        double ms = (System.nanoTime() - t0) / 1e6;
        double wx = Geo.x(19.4155, -155.2385), wz = Geo.z(19.4155);          // Nāhuku, Luvhang 1 200 m
        double hx = Geo.x(19.70, -155.25), hz = Geo.z(19.70);                // Hang über Hilo, rund 900 m
        double kx = Geo.x(19.64, -155.995), kz = Geo.z(19.64);               // Küste bei Kailua-Kona
        double luv = W.at(wx, wz, 0), hilo = W.at(hx, hz, 0), kona = W.at(kx, kz, 0);
        check(luv > 0.55 && hilo > 0.55 && kona < 0.3, String.format(Locale.GERMAN,
                "Passat 14 Uhr: Bedeckung Nāhuku %.2f, Hang über Hilo %.2f, Küste bei Kona %.2f (Rechenzeit %.0f ms)", luv, hilo, kona, ms));
        double ml = W.at(Place.MAUNA_LOA.x(), Place.MAUNA_LOA.z(), 0), mk = W.at(Place.MAUNA_KEA.x(), Place.MAUNA_KEA.z(), 0);
        check(ml < 0.05 && mk < 0.05, String.format(Locale.GERMAN, "Über der Passatinversion klar: Mauna Loa %.2f, Mauna Kea %.2f", ml, mk));
        double rl = W.at(hx, hz, 1), rk = W.at(kx, kz, 1);
        check(rl > 0.1 && rk < 0.02, String.format(Locale.GERMAN, "Regen am Luvhang %.2f, an der Kona-Küste %.2f", rl, rk));
        double sx = Geo.x(19.4072, -155.2834), sz = Geo.z(19.4072);
        double[] down = dirXZ(70 + 180), up = dirXZ(70);
        double vd = W.at(sx + down[0] * 20_000, sz + down[1] * 20_000, 2), vu = W.at(sx + up[0] * 20_000, sz + up[1] * 20_000, 2);
        check(vd > 0.1 && vd > 5 * vu, String.format(Locale.GERMAN, "Vog beim Passat: 20 km im Lee (Südwest) %.2f, 20 km im Luv %.2f", vd, vu));
        W.update(7.5, 200, 14);
        double[] kd = dirXZ(200 + 180);
        double vk = W.at(sx + kd[0] * 20_000, sz + kd[1] * 20_000, 2), vs = W.at(sx + down[0] * 20_000, sz + down[1] * 20_000, 2);
        check(vk > 0.1 && vk > 5 * vs, String.format(Locale.GERMAN, "Vog beim Kona-Wind aus 200°: 20 km nach Nordnordost %.2f, nach Südwest %.2f", vk, vs));
        W.update(7.5, 70, 3);
        double night = 0, day = 0;
        int nl = 0;
        float[] m3 = W.map.clone();
        W.update(7.5, 70, 14);
        for (int k = 0; k < com.dan.kilauea.atmos.Weather.N * com.dan.kilauea.atmos.Weather.N; k++) {
            double x = HeightGrid.X0 + (k % com.dan.kilauea.atmos.Weather.N + 0.5) * com.dan.kilauea.atmos.Weather.CELL;
            double z = HeightGrid.Z0 + (k / com.dan.kilauea.atmos.Weather.N + 0.5) * com.dan.kilauea.atmos.Weather.CELL;
            if (g.height(x, z) < 1) continue;
            night += m3[4 * k];
            day += W.map[4 * k];
            nl++;
        }
        check(night < day, String.format(Locale.GERMAN, "Über Land nachts weniger Wolken: 3 Uhr %.2f, 14 Uhr %.2f im Mittel", night / nl, day / nl));
        var W2 = new com.dan.kilauea.atmos.Weather();
        W2.terrain(g);
        W2.update(7.5, 70, 14);
        check(java.util.Arrays.equals(W.map, W2.map), "Wetterkarte: gleiche Eingabe, gleiche Karte");
        // Kamera im Regen wird nass; trocknet langsamer, als sie nass wird
        for (int k = 0; k < 600; k++) W.camera(1, hx, 300, hz, 50, new double[]{0, 1, 0});
        double wet = W.wetHere;
        for (int k = 0; k < 600; k++) W.camera(1, kx, 20, kz, 50, new double[]{0, 1, 0});
        check(W.rainHere < 0.05 && wet > 0.1 && W.wetHere > 0.3 * wet, String.format(Locale.GERMAN,
                "Regen an der Kamera: nach 10 min im Regen nass %.2f, 10 min später im Trockenen noch %.2f", wet, W.wetHere));
        // die Fahne folgt dem Wind: Schwerpunkt der Dampfteilchen aus dem Halemaʻumaʻu
        for (double from : new double[]{70, 200}) {
            var st = new com.dan.kilauea.atmos.Steam();
            double[] wv = dirXZ(from + 180);
            double[] wind = {wv[0] * 7.5, wv[1] * 7.5};
            double[] cam = {sx - 25_000, 1500, sz};                        // weit genug, dass nur der Halemaʻumaʻu ausgibt
            for (int k = 0; k < 1200; k++) st.update(0.25, wind, cam, k * 0.25);
            double cx = 0, cz = 0, n = 0;
            for (int i = 0; i < st.puffCount(); i++) { cx += st.out[8 * i] - sx; cz += st.out[8 * i + 2] - sz; n++; }
            cx /= Math.max(1, n); cz /= Math.max(1, n);
            double ang = Math.toDegrees(Math.atan2(cx, -cz)), want = (from + 180) % 360;
            double err = Math.abs(((ang - want) % 360 + 540) % 360 - 180);
            check(n > 50 && err < 15 && Math.hypot(cx, cz) > 300, String.format(Locale.GERMAN,
                    "Fahne bei Wind aus %.0f°: zieht nach %.0f° (soll %.0f°), Schwerpunkt %.1f km vom Krater", from, (ang + 360) % 360, want, Math.hypot(cx, cz) / 1000));
        }
        // Dampf über heißer Lava: ohne Regen wenig, mit Regen mehr
        int[] pc = new int[2];
        for (int q = 0; q < 2; q++) {
            var st = new com.dan.kilauea.atmos.Steam();
            double lx = Place.MAUNA_ULU.x(), lz = Place.MAUNA_ULU.z();
            st.hot(new double[]{lx, lz, 200_000}, 1, q == 0 ? 0 : 1);
            for (int k = 0; k < 240; k++) st.update(0.25, new double[]{-5, 2}, new double[]{lx, 1000, lz}, k * 0.25);
            for (int i = 0; i < st.puffCount(); i++) if (st.out[8 * i + 6] == 1) pc[q]++;     // Art 1: nur der Dampf über der Lava
        }
        check(pc[0] > 5 && pc[1] > 2 * pc[0], String.format(Locale.GERMAN, "Dampf über 20 ha heißer Lava: %d Schwaden trocken, %d bei Regen", pc[0], pc[1]));
        // Belichtung im Zeitraffer (ein Tag in 90 s, 60 Bilder je Sekunde): kein Sprung von Bild zu Bild
        SceneState ss = new SceneState();
        ss.clock.set(280, 0);
        ss.adaptBoost = 4;
        ss.update(0);
        double maxJump = 0, maxLag = 0, last = 0;
        int turns = 0;
        for (int k = 0; k < 90 * 60; k++) {
            double before = ss.exposure;
            ss.clock.advance(960.0 / 60 / 3600);
            ss.update(1.0 / 60);
            double dl = Math.log(ss.exposure / before);
            maxJump = Math.max(maxJump, Math.abs(dl));
            maxLag = Math.max(maxLag, Math.abs(Math.log(ss.exposure / ss.targetExposure)));
            if (Math.abs(dl) > 1e-4) {
                if (last != 0 && Math.signum(dl) != Math.signum(last)) turns++;
                last = dl;
            }
        }
        // Dämmerung: das Licht ändert sich in wenigen Sekunden um Größenordnungen; die Belichtung folgt gleitend,
        // ohne hin und her zu springen, und liegt nie weit neben dem Ziel
        check(turns <= 6 && maxLag < Math.log(3), String.format(Locale.GERMAN,
                "Ein Tag in 90 s: Belichtung gleitet (%d Richtungswechsel, höchstens %.1f %% je Bild, nie mehr als %.1f-fach neben dem Ziel)",
                turns, 100 * (Math.exp(maxJump) - 1), Math.exp(maxLag)));
    }

    /** Einheitsvektor (x Osten, z Süden) in Richtung bearing (Grad von Nord). */
    static double[] dirXZ(double bearing) {
        double a = Math.toRadians(bearing);
        return new double[]{Math.sin(a), -Math.cos(a)};
    }

    static void lake() {
        System.out.println("Lavasee");
        var L = com.dan.kilauea.lava.LavaLake.halemaumau(5);
        int min = Integer.MAX_VALUE, max = 0, outside = 0;
        double vMean = 0;
        int vn = 0;
        for (int k = 0; k < 60; k++) {
            L.step(60);
            min = Math.min(min, L.plates());
            max = Math.max(max, L.plates());
            for (int i = 0; i < L.plates(); i++) {
                if (L.shape(L.plateU(i), L.plateV(i)) > 1) outside++;
                vMean += L.plateVU(i);
                vn++;
            }
        }
        check(min >= 40 && max <= com.dan.kilauea.lava.LavaLake.MAX && outside == 0 && Math.abs(L.born() - L.sunk()) < 20, String.format(Locale.GERMAN,
                "1 h: %d–%d Platten, keine außerhalb des Ufers, %d neu an der Quelle, %d an der Senke abgetaucht", min, max, L.born(), L.sunk()));
        double ageSrc = 0, ageSnk = 0;
        int nS = 0, nK = 0;
        for (int i = 0; i < L.plates(); i++) {
            if (Math.hypot(L.plateU(i) - L.srcU, L.plateV(i) - L.srcV) < 80) { ageSrc += L.plateAge(i); nS++; }
            if (Math.hypot(L.plateU(i) - L.snkU, L.plateV(i) - L.snkV) < 80) { ageSnk += L.plateAge(i); nK++; }
        }
        ageSrc /= Math.max(1, nS);
        ageSnk /= Math.max(1, nK);
        check(vMean / vn > 0.05 && ageSnk > 4 * ageSrc, String.format(Locale.GERMAN,
                "Platten treiben von der Quelle zur Senke (%.2f m/s); Alter an der Quelle %.0f min, an der Senke %.0f min", vMean / vn, ageSrc / 60, ageSnk / 60));
        double opS = 0, opK = 0;
        int cS = 0, cK = 0;
        for (int i = 0; i < L.plates(); i++)
            for (int j = i + 1; j < L.plates(); j++) {
                double o = L.seamOpening(i, j);
                if (Double.isNaN(o)) continue;
                double mu = 0.5 * (L.plateU(i) + L.plateU(j)), mv = 0.5 * (L.plateV(i) + L.plateV(j));
                if (Math.hypot(mu - L.srcU, mv - L.srcV) < 60) { opS += o; cS++; }
                else if (Math.hypot(mu - L.snkU, mv - L.snkV) < 60) { opK += o; cK++; }
            }
        check(cS > 3 && cK > 3 && opS / cS > 0 && opK / cK < 0, String.format(Locale.GERMAN,
                "Nähte öffnen sich an der Quelle (%+.1f cm/s) und schließen sich an der Senke (%+.1f cm/s)", 100 * opS / cS, 100 * opK / cK));
        check(L.pistons() >= 1 && L.spatters() > 30, String.format(Locale.GERMAN, "Gaskolben: %d in der Stunde, %d Spritzer", L.pistons(), L.spatters()));
        double l0 = L.level();
        L.pressure(7.5e6);
        L.step(9 * 3600);
        double l1 = L.level();
        L.pressure(2e6);
        L.step(9 * 3600);
        double l2 = L.level();
        check(l1 > l0 + 10 && l2 < l1 - 20 && l2 >= 0 && l1 <= com.dan.kilauea.lava.LavaLake.LEVEL_MAX, String.format(Locale.GERMAN,
                "Spiegel folgt dem Druck: %.0f m → %.0f m bei 7,5 MPa → %.0f m bei 2 MPa", l0, l1, l2));
        var A = com.dan.kilauea.lava.LavaLake.halemaumau(9);
        var B = com.dan.kilauea.lava.LavaLake.halemaumau(9);
        A.step(1800);
        B.step(1800);
        check(A.fingerprint() == B.fingerprint(), "Lavasee: gleicher Keim, gleicher Ablauf");
    }

    /** Phase 13: Kamera und Regie – Flug, freie Sicht, Touren und der Prüflauf über alles. */
    static void camera() {
        System.out.println("Kamera und Regie (Phase 13)");
        double w = com.dan.kilauea.camera.OrbitCamera.wrap(Math.toRadians(350)), w2 = com.dan.kilauea.camera.OrbitCamera.wrap(Math.toRadians(-190));
        check(Math.abs(Math.toDegrees(w) + 10) < 1e-9 && Math.abs(Math.toDegrees(w2) - 170) < 1e-9, "Blickwinkel kurz herum: 350° → −10°, −190° → 170°");
        double s1 = com.dan.kilauea.camera.OrbitCamera.flySpeed(3, 500), s2 = com.dan.kilauea.camera.OrbitCamera.flySpeed(1000, 3000),
                s3 = com.dan.kilauea.camera.OrbitCamera.flySpeed(60_000, 125_000);
        check(s1 <= 10 && s2 > 300 && s2 < 1500 && 60_000 / s3 < 3, String.format(Locale.GERMAN,
                "Fluggeschwindigkeit nach Höhe: %.0f m/s am Boden, %.0f m/s in 1 km, 60 km in %.1f s von oben", s1, s2, 60_000 / s3));
        // freie Sicht: aus dem Grund der Grube Puhimau flach hinausgeschaut ist das Gelände im Weg; mit Hub nicht mehr
        double px = Geo.x(19.3908, -155.2497), pz = Geo.z(19.3908), py = Ground.height(px, pz);
        for (int i = -20; i <= 20; i++) for (int j = -20; j <= 20; j++) {    // tiefster Punkt der Grube
            double x = Geo.x(19.3908, -155.2497) + i * 15, z = Geo.z(19.3908) + j * 15, h = Ground.height(x, z);
            if (h < py) { py = h; px = x; pz = z; }
        }
        double yaw = Math.toRadians(90), pitch = Math.toRadians(3), d = 1100;
        double lift = com.dan.kilauea.camera.OrbitCamera.liftFor(px, py, pz, yaw, pitch, d), p2 = pitch + lift;
        boolean before = com.dan.kilauea.camera.OrbitCamera.lineBlocked(px, py, pz, px + Math.sin(yaw) * Math.cos(pitch) * d, py + Math.sin(pitch) * d, pz + Math.cos(yaw) * Math.cos(pitch) * d);
        boolean after = com.dan.kilauea.camera.OrbitCamera.lineBlocked(px, py, pz, px + Math.sin(yaw) * Math.cos(p2) * d, py + Math.sin(p2) * d, pz + Math.cos(yaw) * Math.cos(p2) * d);
        check(before && lift > 0 && !after, String.format(Locale.GERMAN, "Freie Sicht in der Grube Puhimau (Boden %.0f m): flach verdeckt, %.0f° Hub machen den Blick frei", py, Math.toDegrees(lift)));
        // drei Touren mit Stationen auf der Insel
        var tours = com.dan.kilauea.camera.Tour.all();
        boolean ids = tours.size() == 3 && com.dan.kilauea.camera.Tour.byId("gipfel") != null && com.dan.kilauea.camera.Tour.byId("rift") != null
                && com.dan.kilauea.camera.Tour.byId("2018") != null;
        boolean stops = true;
        for (var t : tours) for (var st : t.stops) stops &= st.x() > Island.X0 && st.x() < Island.X1 && st.z() > Island.Z0 && st.z() < Island.Z1
                && st.hold() > 0 && st.dist() > 0 && !st.title().isEmpty() && Ground.height(st.x(), st.z()) > -5;
        check(ids && stops, "Drei Touren (Gipfel, Riftzone bis zum Meer, Ausbruch 2018), alle Stationen auf der Insel");
        // Prüflauf: jede Tour, alle Orte, alle Blicke der Drehbücher – kein Sprung, keine Kollision, kein Blick durchs Gelände
        java.util.List<com.dan.kilauea.tools.TourLauf.Result> rs = new java.util.ArrayList<>();
        for (var t : tours) rs.add(com.dan.kilauea.tools.TourLauf.tour(t, 30));
        rs.add(com.dan.kilauea.tools.TourLauf.places(30));
        rs.add(com.dan.kilauea.tools.TourLauf.scriptViews(30));
        for (var r : rs) check(r.ok(), String.format(Locale.GERMAN, "Prüflauf %s: %d Bilder, %.0f s, Sprünge %d, Kollisionen %d, Blick durchs Gelände %d",
                r.name(), r.frames(), r.seconds(), r.jumps(), r.collisions(), r.blocked()));
    }

    /** Phase 12: Verformung, mehrere Schlote, Rückfluss, ausgelöster Gang, Drehbücher und Rückkehr. */
    static void scripts() {
        System.out.println("Ausbrüche als Drehbuch (Phase 12)");
        // Verformung: Gipfel vor 2018 und heute
        com.dan.kilauea.terrain.Deformation.build();
        double hx = Geo.x(19.4063, -155.2862), hz = Geo.z(19.4063), cx = Geo.x(19.4100, -155.2830), cz = Geo.z(19.4100);
        double todayH = Ground.height(hx, hz), todayC = Ground.height(cx, cz);
        com.dan.kilauea.terrain.Deformation.set(true, 0);
        double preH = Ground.height(hx, hz), preC = Ground.height(cx, cz);
        check(preH > 1000 && preH < 1080 && preC > 1090, String.format(Locale.GERMAN,
                "Gipfel vor 2018: alter Halemaʻumaʻu %.0f m, Calderaboden %.0f m (heute %.0f und %.0f m)", preH, preC, todayH, todayC));
        boolean mono = true;
        double last = preC;
        for (int k = 1; k <= 20; k++) {
            com.dan.kilauea.terrain.Deformation.set(true, k / 20.0);
            double h = Ground.height(cx, cz);
            if (h > last + 0.01) mono = false;
            last = h;
        }
        double maxDiff = 0;
        java.util.Random rnd = new java.util.Random(12);
        for (int i = 0; i < 300; i++) {
            double x = cx + (rnd.nextDouble() - 0.5) * 4000, z = cz + (rnd.nextDouble() - 0.5) * 4000;
            com.dan.kilauea.terrain.Deformation.set(true, 1);
            double a = Ground.height(x, z);
            com.dan.kilauea.terrain.Deformation.set(false, 1);
            maxDiff = Math.max(maxDiff, Math.abs(a - Ground.height(x, z)));
        }
        check(mono && maxDiff < 1e-9, String.format(Locale.GERMAN, "Einsturz: der Boden sinkt stufenweise, bei F = 1 genau das Gelände von heute (Abweichung %.1e m)", maxDiff));
        check(com.dan.kilauea.terrain.Deformation.sunk(0.625, 0) == 1 && com.dan.kilauea.terrain.Deformation.sunk(0.37, 1) == 0
                && com.dan.kilauea.terrain.Deformation.sunk(1, 1) == 1, "Einsturz: der Krater sackt zuerst, der Ring zuletzt");

        // Fontänen aus zwei Schloten
        com.dan.kilauea.lava.Fountain fo = new com.dan.kilauea.lava.Fountain();
        double[] wind = {0, 0};
        double[][] two = {{0, 0, 0, 0, 1000, 300}, {500, 0, 500, 0, 1000, 50}};
        double top = 0;
        int nearA = 0, nearB = 0;
        for (int i = 0; i < 400; i++) fo.update(0.1, two, wind);
        for (int i = 0; i < fo.sparkCount(); i++) {
            float[] o = fo.out;
            double x = o[8 * i], y = o[8 * i + 1];
            top = Math.max(top, y - 1000);
            if (Math.abs(x) < 120) nearA++;
            if (Math.abs(x - 500) < 80) nearB++;
        }
        check(nearA > 0 && nearB > 0 && top > 250 && top < 330 && fo.count() <= com.dan.kilauea.lava.Fountain.MAX,
                String.format(Locale.GERMAN, "Zwei Fontänen zugleich: %d und %d Teilchen, die höhere bis %.0f m", nearA, nearB, top));

        // Rückfluss: der Schlot schluckt Lava, die Bilanz bleibt
        com.dan.kilauea.lava.LavaField f = new com.dan.kilauea.lava.LavaField((x, z) -> -0.03 * Math.hypot(x, z), 10, 64, 7);
        com.dan.kilauea.lava.Vent v = new com.dan.kilauea.lava.Vent("Test", 0, 0, 0, 0, 20);
        f.addVent(v);
        f.step(1800);
        double up = f.erupted();
        v.rate = -20;
        f.step(600);
        var st = f.stats(0, 0);
        double bal = Math.abs(st.erupted - (st.mobile + st.rock + st.collapsed)) / Math.max(1, st.erupted);
        check(f.erupted() < up && bal < 1e-6, String.format(Locale.GERMAN, "Rückfluss: %,.0f m³ zurück in den Schlot, Bilanz hält (Rest %.1e)", up - f.erupted(), bal));

        // Gang auf Bestellung
        com.dan.kilauea.magma.MagmaSystem m = new com.dan.kilauea.magma.MagmaSystem(5);
        var snap = m.snapshot();
        var d = m.intrude(com.dan.kilauea.magma.Rift.EAST, 20_000, 0.12);
        double tArr = 0;
        while (!d.arrived() && tArr < 10 * 86400) { m.step(600); tArr += 600; }
        double[] tip = d.tip(), want = com.dan.kilauea.magma.Rift.EAST.at(20_000);
        check(d.arrived() && Math.abs(tArr / 86400 - 18_500 / 0.12 / 86400) < 0.1 && Math.hypot(tip[0] - want[0], tip[1] - want[1]) < 50,
                String.format(Locale.GERMAN, "Ausgelöster Gang: nach %.2f Tagen 20 km vom Gipfel, Spitze am Ziel", tArr / 86400));
        m.endDike();
        m.restore(snap);
        var after = m.snapshot();
        check(after.equals(snap) && m.activeDike() == null, "Magma nach dem Drehbuch wie vorher (Speicher, Zufluss, Gänge)");

        // Drehbücher: Aufbau
        boolean shape = true;
        int chapters = 0;
        for (var sc : com.dan.kilauea.script.Scripts.all()) {
            chapters += sc.chapters().size();
            if (sc.chapters().size() < 4) shape = false;
            for (var c : sc.chapters()) if (c.title.isBlank() || c.text.length() < 60 || c.duration <= 0 || c.speed <= 0 || c.date(c.duration / 2).isBlank()) shape = false;
        }
        check(shape && com.dan.kilauea.script.Scripts.all().size() == 5, "Fünf Drehbücher mit " + chapters + " Kapiteln, jedes mit Datum, Überschrift, Text und Dauer");

        // Spieler: ein kleines Drehbuch mit Stichworten, Bedingung, Überspringen und Rückkehr
        com.dan.kilauea.lava.LavaSim sim = new com.dan.kilauea.lava.LavaSim(() -> 4.5e6);
        com.dan.kilauea.magma.MagmaSystem mm = new com.dan.kilauea.magma.MagmaSystem(9);
        com.dan.kilauea.script.Stage stage = new com.dan.kilauea.script.Stage(sim, mm);
        com.dan.kilauea.script.Player pl = new com.dan.kilauea.script.Player(stage, new com.dan.kilauea.script.Player.Host() { });
        pl.headless = true;
        pl.outroSeconds = 0;
        sim.installGround();
        double[] fired = {-1, -1, -1};
        var mini = com.dan.kilauea.script.Script.of("test", "Test", "Prüfung")
                .chapter("1", "Gang", "Ein Gang läuft bis 8 km, das Kapitel endet bei seiner Ankunft, nicht nach seiner Dauer.")
                .lasts(30 * 86400, 86400)
                .at(0, x -> x.intrude(com.dan.kilauea.magma.Rift.EAST, 8000, 0.12))
                .until(com.dan.kilauea.script.Stage::dikeArrived)
                .chapter("2", "Spalte", "Eine Spalte öffnet sich; nach 600 s steigt die Rate, nach 1 200 s fällt der Boden des Gipfels.")
                .lasts(1800, 60)
                .at(0, x -> { x.begin("Test", 19.3654, -155.2026); x.open("a", com.dan.kilauea.lava.VentSite.MAUNA_ULU, 5, 20); fired[0] = pl.storyTime(); })
                .at(600, x -> { x.rate("a", 10); fired[1] = pl.chapterTime(); })
                .at(1200, x -> { x.collapse(0.5); fired[2] = pl.chapterTime(); })
                .chapter("3", "Ende", "Das Kapitel wird übersprungen; seine Stichworte werden trotzdem ausgeführt, damit der Zustand stimmt.")
                .lasts(3600, 60)
                .at(1800, x -> x.closeAll())
                .build();
        String before = com.dan.kilauea.tools.DrehbuchLauf.state(sim, mm);
        pl.start(mini);
        pl.update(0);
        while (pl.state() == com.dan.kilauea.script.Player.State.PLAYING && pl.chapterIndex() < 2) pl.advance(600);
        double dikeDays = fired[0] / 86400;
        boolean cues = Math.abs(fired[1] - 600) < 1e-6 && Math.abs(fired[2] - 1200) < 1e-6;
        boolean lavaOn = sim.field() != null && sim.field().erupted() > 0;
        pl.skip();
        pl.update(0);
        boolean closed = stage.vent("a").rate == 0 && pl.state() != com.dan.kilauea.script.Player.State.PLAYING;
        pl.returnToStart();
        String back = com.dan.kilauea.tools.DrehbuchLauf.state(sim, mm);
        check(dikeDays > 0.5 && dikeDays < 0.8, String.format(Locale.GERMAN, "Drehbuch: das erste Kapitel endet mit der Ankunft des Gangs nach %.2f Tagen statt nach 30", dikeDays));
        check(cues && lavaOn, "Drehbuch: Stichworte genau zur Zeit (600 s und 1 200 s), die Lava fließt");
        check(closed, "Drehbuch: Überspringen führt die Stichworte des Kapitels aus (Schlot zu), danach Ende");
        check(before.equals(back) && !com.dan.kilauea.terrain.Deformation.active(), "Drehbuch: zurück in den Ausgangszustand (Lava, Speicher, Gelände, See)");

        // ein ganzes Drehbuch: die Fontänen-Episoden 2024/25
        long t0 = System.nanoTime();
        boolean ok2024 = com.dan.kilauea.tools.DrehbuchLauf.run(com.dan.kilauea.script.Scripts.fountains2024(), sim, mm);
        check(ok2024, String.format(Locale.GERMAN, "Drehbuch „Fontänen 2024/25“ läuft ohne Eingriff durch und kehrt sauber zurück (%.0f s)", (System.nanoTime() - t0) / 1e9));

        // Riftzonen: Punkt und nächster Punkt
        double[] rp = com.dan.kilauea.script.Stage.riftPoint(com.dan.kilauea.magma.Rift.SOUTHWEST, 7000);
        Object[] nr = com.dan.kilauea.script.Stage.nearestRift(rp[0] + 30, rp[1]);
        check(nr[0] == com.dan.kilauea.magma.Rift.SOUTHWEST && Math.abs((Double) nr[1] - 7000) <= 60 && Math.abs(Math.hypot(rp[2], rp[3]) - 1) < 1e-9,
                "Freier Ausbruch: Ort unter dem Blick findet Riftzone und Abstand vom Gipfel");

        // Lavasee sinkt
        var lk = com.dan.kilauea.lava.LavaLake.halemaumau(1);
        lk.rimY = 900;
        double s0 = lk.surfaceY();
        lk.drop = 30;
        check(Math.abs(s0 - lk.surfaceY() - 30) < 1e-9, "Lavasee: Absenkung 2018 senkt den Spiegel um genau so viel");
    }


    /** Phase 14: Messnetz, Wärmebild, Wissen. */
    static void monitor() {
        System.out.println("Messnetz und Wissen (Phase 14)");
        com.dan.kilauea.monitor.Seismo.amplitudeMm(3, 100);
        double a3 = com.dan.kilauea.monitor.Seismo.amplitudeMm(3.0, 100), m3 = com.dan.kilauea.monitor.Seismo.magnitude(2.5, 7);
        check(Math.abs(a3 - 1) < 1e-9 && Math.abs(com.dan.kilauea.monitor.Seismo.amplitudeMm(m3, 7) - 2.5) < 1e-9,
                "Seismograf: M 3 in 100 km gibt 1 mm Wood-Anderson (Hutton & Boore), Umkehrung stimmt");
        double d2 = com.dan.kilauea.monitor.Seismo.durationS(2, 0), d4 = com.dan.kilauea.monitor.Seismo.durationS(4, 0);
        check(Math.abs(d2 - Math.pow(10, 1.435)) < 1e-6 && Math.abs(d4 / d2 - 10) < 1e-6,
                String.format(Locale.GERMAN, "Seismograf: Dauer nach Lee u. a.: M 2 → %.0f s, M 4 → %.0f s", d2, d4));
        var q = new com.dan.kilauea.magma.Quake(100, 0, -2000, 0, 2.5, com.dan.kilauea.magma.Quake.Kind.SUMMIT);
        double r = 10;   // km
        double tp = 100 + r * 1000 / com.dan.kilauea.monitor.Seismo.VP, ts = 100 + r * 1000 / com.dan.kilauea.monitor.Seismo.VS;
        double before = 0, pMax = 0, sMax = 0;
        for (double t = 99; t < tp; t += 0.01) before = Math.max(before, Math.abs(com.dan.kilauea.monitor.Seismo.quake(q, t, r)));
        for (double t = tp; t < ts; t += 0.01) pMax = Math.max(pMax, Math.abs(com.dan.kilauea.monitor.Seismo.quake(q, t, r)));
        for (double t = ts; t < ts + 10; t += 0.01) sMax = Math.max(sMax, Math.abs(com.dan.kilauea.monitor.Seismo.quake(q, t, r)));
        double aq = com.dan.kilauea.monitor.Seismo.amplitudeMm(2.5, r);
        check(before == 0 && pMax > 0 && sMax > 2 * pMax && sMax < 1.5 * aq && sMax > 0.4 * aq,
                String.format(Locale.GERMAN, "Seismograf: vor P nichts, P %.2f mm, S %.2f mm (Hutton & Boore %.2f mm), S %.1f s nach P", pMax, sMax, aq, ts - tp));

        // Neigung, GPS, Hebung: Vorzeichen und Größe
        com.dan.kilauea.magma.MagmaSystem m = new com.dan.kilauea.magma.MagmaSystem(3);
        com.dan.kilauea.lava.LavaSim sim = new com.dan.kilauea.lava.LavaSim(() -> 0);
        sim.showLake = false;
        com.dan.kilauea.monitor.Monitor mon = new com.dan.kilauea.monitor.Monitor(m, sim);
        mon.clear();
        mon.rateOverride = 3600;
        mon.sample(0);
        m.inject(1e6, true);
        mon.sample(1);
        double up = mon.latest(com.dan.kilauea.monitor.Monitor.CH_UWD), sp = mon.latest(com.dan.kilauea.monitor.Monitor.CH_SDH),
                bp = mon.latest(com.dan.kilauea.monitor.Monitor.CH_BASE), hp = mon.latest(com.dan.kilauea.monitor.Monitor.CH_UPLIFT);
        m.withdraw(2e6, true);
        mon.sample(1);
        double un = mon.latest(com.dan.kilauea.monitor.Monitor.CH_UWD), bn = mon.latest(com.dan.kilauea.monitor.Monitor.CH_BASE);
        check(up > 0 && sp > 0 && bp > 0 && hp > 0 && un < 0 && bn < 0 && Math.abs(un + up) < 1e-6 * Math.max(1, Math.abs(up)) * 1e3,
                String.format(Locale.GERMAN, "Neigung: 1 Mio. m³ mehr im flachen Speicher hebt UWD um %+.1f µrad, SDH %+.1f µrad, UWEV–CRIM %+.2f cm, Gipfel %+.1f cm; weniger kehrt es um", up, sp, bp, hp));
        double[] dd = m.displacement(com.dan.kilauea.monitor.Station.CRIM.x(), com.dan.kilauea.monitor.Station.CRIM.z());
        double rr = Math.hypot(com.dan.kilauea.monitor.Station.CRIM.x() - m.hmm.x, com.dan.kilauea.monitor.Station.CRIM.z() - m.hmm.z);
        check(Math.abs(Math.abs(dd[2]) - Math.abs(m.uplift(com.dan.kilauea.monitor.Station.CRIM.x(), com.dan.kilauea.monitor.Station.CRIM.z()))) < 1e-12 && rr > 500,
                "GPS: senkrechte Verschiebung gleich der Hebung nach Mogi");
        double base = Math.hypot(com.dan.kilauea.monitor.Station.CRIM.x() - com.dan.kilauea.monitor.Station.UWEV.x(), com.dan.kilauea.monitor.Station.CRIM.z() - com.dan.kilauea.monitor.Station.UWEV.z());
        double toUwe = Math.hypot(com.dan.kilauea.monitor.Station.UWD.x() - com.dan.kilauea.magma.MagmaSystem.UWE[0], com.dan.kilauea.monitor.Station.UWD.z() - com.dan.kilauea.magma.MagmaSystem.UWE[1]);
        check(base > 2500 && base < 5000 && toUwe < 1 && com.dan.kilauea.monitor.Station.CRIM.z() > m.hmm.z && com.dan.kilauea.monitor.Station.UWEV.z() < m.hmm.z,
                String.format(Locale.GERMAN, "Stationen: UWD am Uēkahuna-Rand, UWEV im Norden, CRIM im Süden der Caldera, %.1f km auseinander", base / 1000));

        // Gas
        double g100 = com.dan.kilauea.monitor.Gas.so2(100, false, Double.NaN), lake = com.dan.kilauea.monitor.Gas.so2(0, true, Double.NaN), quiet = com.dan.kilauea.monitor.Gas.so2(0, false, Double.NaN);
        check(g100 > com.dan.kilauea.monitor.Gas.MEASURED_2018_EARLY * 0.6 && g100 < com.dan.kilauea.monitor.Gas.MEASURED_2018_REVISED && lake == 5000 && quiet == 30
                        && com.dan.kilauea.monitor.Gas.so2(0, false, 1750) == 1750,
                String.format(Locale.GERMAN, "SO₂: 100 m³/s → %,.0f t/d (gemessen 2018: über 50 000, revidiert fast 200 000), Lavasee 5 000, Ruhe 30, Pause 2025 1 750", g100));

        // Ansicht: Trommel bei Echtzeit, alle Werte mit Herkunft, Tafeln malen
        mon.rateOverride = 1;
        for (int i = 0; i < 300; i++) { m.step(1); mon.sample(1); }
        var v = mon.view(m.hmm.x, m.hmm.z, 300);
        boolean prov = true;
        for (var rd : v.all()) if (rd.prov() == null || rd.source().isBlank() || rd.withTag().isBlank()) prov = false;
        check(v.drum() && v.drumMin().length == com.dan.kilauea.monitor.Monitor.DRUM_LINES && v.drumMin()[0].length == 300 && v.all().size() >= 10 && prov,
                String.format(Locale.GERMAN, "Messnetz: in Echtzeit die Trommel mit 6 Zeilen; %d Werte, jeder mit Marke und Quelle", v.all().size()));
        var img = com.dan.kilauea.monitor.MonitorPainter.paint(v, 440, 900);
        var tim = com.dan.kilauea.monitor.MonitorPainter.paintThermal(com.dan.kilauea.monitor.Thermal.probe(1080, com.dan.kilauea.monitor.Thermal.Kind.LAVA),
                com.dan.kilauea.monitor.Thermal.what(com.dan.kilauea.monitor.Thermal.Kind.LAVA, 1080), 360);
        check(img.getWidth() == 440 && img.getHeight() > 300 && img.getHeight() <= 900 && tim.getHeight() > 60, "Tafeln des Messnetzes und des Wärmebilds lassen sich malen");

        // RSAM steigt, wenn ein Gang läuft (Schwarm an der Spitze)
        com.dan.kilauea.magma.MagmaSystem m2 = new com.dan.kilauea.magma.MagmaSystem(4);
        com.dan.kilauea.monitor.Monitor mon2 = new com.dan.kilauea.monitor.Monitor(m2, sim);
        mon2.clear();
        mon2.rateOverride = 3600;
        for (int i = 0; i < 24; i++) { m2.step(3600); mon2.sample(1); }
        m2.intrude(com.dan.kilauea.magma.Rift.EAST, 12_000, 0.12);
        for (int i = 0; i < 24; i++) { m2.step(3600); mon2.sample(1); }
        var v2 = mon2.view(com.dan.kilauea.magma.Rift.EAST.at(8000)[0], com.dan.kilauea.magma.Rift.EAST.at(8000)[1], 0);
        double quietR = 0, dikeR = 0;
        int nb = v2.rsam().length;
        for (int i = 0; i < nb; i++) if (v2.rsamT()[i] < m2.time() - 24 * 3600) quietR = Math.max(quietR, v2.rsam()[i]); else dikeR = Math.max(dikeR, v2.rsam()[i]);
        check(dikeR > 3 * quietR && !v2.drum(), String.format(Locale.GERMAN, "RSAM am Seismometer %s: ruhig bis %.0f, mit dem Gang bis %.0f", v2.seis().name(), quietR, dikeR));

        // 2024/25: Neigung und SO₂ im Maß der Messungen
        com.dan.kilauea.magma.MagmaSystem m3s = new com.dan.kilauea.magma.MagmaSystem(2026);
        com.dan.kilauea.lava.LavaSim sim3 = new com.dan.kilauea.lava.LavaSim(() -> { synchronized (m3s) { return m3s.hmm.pressure(); } });
        try {
            var rows = com.dan.kilauea.tools.MessLauf.run(com.dan.kilauea.script.Scripts.fountains2024(), sim3, m3s, null);
            var ep26 = rows.get(3);
            var pause = rows.get(2);
            check(ep26.dUwd() < -3 && ep26.dUwd() > -15 && ep26.dSdh() < 0 && pause.dUwd() > 3,
                    String.format(Locale.GERMAN, "Fontänen 2024/25: Episode 26 UWD %+.1f µrad, SDH %+.1f µrad (gemessen Episode 20: −6 und −5); Pause %+.1f µrad", ep26.dUwd(), ep26.dSdh(), pause.dUwd()));
            check(ep26.so2Max() > 5000 && Math.abs(pause.so2Max() - 1750) < 1 && ep26.tremorMax() > 3 * pause.tremorMax(),
                    String.format(Locale.GERMAN, "Fontänen 2024/25: SO₂ in der Episode bis %,.0f t/d (gemessen bis rund 50 000), in der Pause 1 750 [L]; Tremor %.1f gegen %.1f mm",
                            ep26.so2Max(), ep26.tremorMax(), pause.tremorMax()));
        } catch (Exception e) {
            check(false, "Messnetz im Drehbuch 2024/25: " + e);
        }

        // Wärmebild
        boolean mono = true;
        for (double t = -20; t < 1200; t += 5) if (com.dan.kilauea.monitor.Thermal.scale(t + 5) <= com.dan.kilauea.monitor.Thermal.scale(t)) mono = false;
        double br = com.dan.kilauea.monitor.Thermal.brightness(300, 1150, 0.1);
        check(mono && br > 520 && br < 760 && Math.abs(com.dan.kilauea.monitor.Thermal.scale(60) - 0.45) < 1e-9,
                String.format(Locale.GERMAN, "Wärmebild: Skala steigt, 60 °C bei 45 %%; Kruste 300 °C mit 10 %% Rissen bei 1 150 °C zeigt %.0f °C", br));
        check(com.dan.kilauea.monitor.Thermal.air(1200) > 15 && com.dan.kilauea.monitor.Thermal.air(1200) < 18 && com.dan.kilauea.monitor.Thermal.air(4170) < 0,
                String.format(Locale.GERMAN, "Wärmebild: Luft am Gipfel %.1f °C, auf dem Mauna Loa %.1f °C", com.dan.kilauea.monitor.Thermal.air(1200), com.dan.kilauea.monitor.Thermal.air(4170)));

        // Wissen: Orte und Boden
        boolean places = true;
        for (Place p : Place.values()) {
            var in = com.dan.kilauea.monitor.Lexicon.place(p);
            if (in.text().length() < 40 || !in.text().contains("[L") || in.readings().isEmpty()) places = false;
        }
        check(places, "Wissen: jeder der 19 Orte hat eine Tafel mit Quelle");
        var iki = com.dan.kilauea.monitor.Lexicon.ground(Geo.x(19.4160, -155.2465), Geo.z(19.4160), null);
        var f8 = com.dan.kilauea.monitor.Lexicon.ground(Geo.x(19.4800, -154.8900), Geo.z(19.4800), null);
        var mu = com.dan.kilauea.monitor.Lexicon.ground(Geo.x(19.3400, -155.1800), Geo.z(19.3400), null);
        boolean tagged = true;
        for (var in : java.util.List.of(iki, f8, mu)) for (var rd : in.readings()) if (rd.prov() == null || rd.source().isBlank()) tagged = false;
        check(iki.title().contains("1959") && f8.title().contains("2018") && tagged,
                "Was liegt hier? Kīlauea Iki: „" + iki.title() + "“; bei Kapoho: „" + f8.title() + "“; jeder Wert mit Herkunft");
        check(!mu.title().isBlank() && mu.text().contains("["), "Was liegt hier? Südflanke unter Mauna Ulu: „" + mu.title() + "“");
    }


    // ------------------------------------------------------------------ Phase 15: Leben und Klang

    static void leben() {
        System.out.println("Leben (Phase 15)");
        com.dan.kilauea.terrain.ParkRoads.buildTrails();
        // Tage und Flugplan
        check(com.dan.kilauea.life.World.weekday(1) == 3 && com.dan.kilauea.life.World.weekday(281) == 3 && com.dan.kilauea.life.World.weekday(284) == 6,
                "Wochentag: 1. Januar und 8. Oktober 2026 Donnerstag, 11. Oktober Sonntag");
        boolean days = com.dan.kilauea.life.FlightPlan.allowedDay(281) && !com.dan.kilauea.life.FlightPlan.allowedDay(280) && !com.dan.kilauea.life.FlightPlan.allowedDay(284)
                && !com.dan.kilauea.life.FlightPlan.allowedDay(40) && !com.dan.kilauea.life.FlightPlan.allowedDay(353);
        boolean hours = !com.dan.kilauea.life.FlightPlan.allowed(281, 9.9) && com.dan.kilauea.life.FlightPlan.allowed(281, 10.0) && !com.dan.kilauea.life.FlightPlan.allowed(281, 14.0);
        int year = 0;
        for (int d = 1; d <= 365; d++) {
            for (var t : com.dan.kilauea.life.FlightPlan.day(d)) {
                year++;
                hours &= com.dan.kilauea.life.FlightPlan.allowed(d, t.start());
            }
        }
        check(days && hours && Math.abs(year - 1547) < 160, String.format(Locale.GERMAN,
                "Flugplan 2023: nicht mittwochs, sonntags, am 9.2. und 19.12.; nur 10–14 Uhr; %d Rundflüge im Jahr (Plan 1 548) [L]", year));
        // Strecken: Höhe über Grund, Abstand zur Küste, nie über dem Gipfel
        var loop = com.dan.kilauea.life.FlightPlan.loop();
        double minAgl = 1e9, minSummit = 1e9;
        double[] o = new double[5];
        for (double t = 0; t <= loop.length; t += 50) {
            loop.at(t, 0, o);
            double alt = com.dan.kilauea.life.FlightPlan.altitude(loop, 0, t);
            minAgl = Math.min(minAgl, alt - Ground.height(o[0], o[2]));
            minSummit = Math.min(minSummit, Math.hypot(o[0] - Place.HALEMAUMAU.x(), o[2] - Place.HALEMAUMAU.z()));
        }
        double minShore = 1e9, minAsl = 1e9;
        for (boolean sw : new boolean[]{true, false}) {
            var c = com.dan.kilauea.life.FlightPlan.coast(sw);
            for (double t = 0; t <= c.length; t += 100) {
                c.at(t, 0, o);
                minShore = Math.min(minShore, com.dan.kilauea.tools.LebenLauf.coast(o[0], o[2]));
                minAsl = Math.min(minAsl, com.dan.kilauea.life.FlightPlan.altitude(c, sw ? 1 : 2, t) - (sw ? 914.4 : 609.6));
                minSummit = Math.min(minSummit, Math.hypot(o[0] - Place.HALEMAUMAU.x(), o[2] - Place.HALEMAUMAU.z()));
            }
        }
        check(minAgl >= 457 && minShore >= 610 && minAsl >= -0.01 && minSummit > 15_000, String.format(Locale.GERMAN,
                "Strecken: Schleife ≥ %.0f m über Grund (≥ 457), Küste ≥ %.0f m vor dem Land (≥ 610) in 2 000/3 000 ft, Gipfel ≥ %.1f km entfernt [L]",
                minAgl, minShore, minSummit / 1000));

        // Ein Tag im Park: Donnerstag, 8. Oktober
        com.dan.kilauea.life.Life life = new com.dan.kilauea.life.Life(2026);
        com.dan.kilauea.life.World w = new com.dan.kilauea.life.World();
        Place pu = Place.PUU_OO;
        w.day = 281; w.hour = 11.25; w.sunElevation = 60;
        w.camX = pu.x(); w.camZ = pu.z(); w.camY = Ground.height(pu.x(), pu.z()) + 2500;
        double heliAgl = 1e9, slow = 0, dt = 1 / 30.0;
        int heliSeen = 0;
        for (double t = 0; t < 600; t += dt) {
            life.update(dt, w);
            for (var h : life.helicopters()) {
                heliSeen = Math.max(heliSeen, 1);
                heliAgl = Math.min(heliAgl, h.y - Ground.height(h.x, h.z));
                if (Math.hypot(h.vx, h.vz) < 3) slow += dt;
            }
        }
        check(heliSeen > 0 && heliAgl >= 457 && slow <= 62, String.format(Locale.GERMAN,
                "Hubschrauber nach Plan in der Luft: nie unter %.0f m über Grund, geschwebt %.0f s (≤ 60 s je Flug) [L]", heliAgl, slow));
        // Uhr springt auf den Abend und zurück: abends keiner in der Luft, danach fliegt der Plan wieder
        {
            com.dan.kilauea.life.Life lj = new com.dan.kilauea.life.Life(7);
            com.dan.kilauea.life.World wj = new com.dan.kilauea.life.World();
            wj.day = 281; wj.sunElevation = 60;
            wj.camX = w.camX; wj.camZ = w.camZ; wj.camY = w.camY;
            double h0 = com.dan.kilauea.life.FlightPlan.day(281).get(0).start() + 0.05;
            wj.hour = h0;
            lj.update(dt, wj);
            int heliBefore = lj.helicopters().size();
            wj.hour = 19.4; wj.sunElevation = -8;
            lj.update(dt, wj);
            int evening = lj.helicopters().size();
            wj.hour = h0; wj.sunElevation = 60;
            lj.update(dt, wj);
            int again = lj.helicopters().size();
            check(heliBefore > 0 && evening == 0 && again == heliBefore, String.format(Locale.GERMAN,
                    "Uhr verstellt: im Flugfenster %d in der Luft, auf 19:24 gestellt %d, zurückgestellt wieder %d [G]", heliBefore, evening, again));
        }
        // Autos und Nēnē am Gipfel
        Place uk = Place.UEKAHUNA;
        w.camX = uk.x(); w.camZ = uk.z(); w.camY = Ground.height(uk.x(), uk.z()) + 50;
        w.hour = 11; w.sunElevation = 60;
        double lane = 0, vmax = 0, gap = 1e9;
        int nene0 = life.cars.neneStops;
        for (double t = 0; t < 900; t += dt) {
            life.update(dt, w);
            var cs = life.cars.state();
            for (double[] c : cs) {
                if (c[7] == 0 && c[2] > 10) lane = Math.max(lane, Math.abs(Math.abs(c[3]) - com.dan.kilauea.life.Cars.LANE));
                vmax = Math.max(vmax, c[2]);
            }
            for (int i = 0; i < cs.size(); i++)
                for (int j = i + 1; j < cs.size(); j++) {
                    double[] a = cs.get(i), b = cs.get(j);
                    if (a[7] == 1 || b[7] == 1 || a[4] != b[4] || a[6] != b[6]) continue;
                    gap = Math.min(gap, Math.abs(a[5] - b[5]));
                }
        }
        int stops = life.cars.neneStops - nene0;
        check(life.cars.size() >= 20 && lane < 0.05 && vmax <= com.dan.kilauea.life.Cars.VMAX + 0.01 && gap > com.dan.kilauea.life.Cars.LENGTH,
                String.format(Locale.GERMAN, "Autos: %d im Park, auf ihrer Spur (±%.2f m), höchstens %.1f m/s, Abstand ≥ %.1f m", life.cars.size(), lane, vmax, gap));
        check(stops >= 1, String.format(Locale.GERMAN, "Autos halten für Nēnē auf der Straße: %d-mal in 15 Minuten", stops));
        boolean onLand = true;
        int far = 0;
        for (double[] b : life.nene.positions()) {
            if (b[3] == 3) continue;
            if (b[1] < 0.5) onLand = false;
            if (Math.hypot(b[0] - life.nene.siteX((int) b[4]), b[2] - life.nene.siteZ((int) b[4])) > 200) far++;
        }
        check(onLand && far == 0 && life.nene.size() == 16, String.format(Locale.GERMAN, "Nēnē im Oktober: %d (Paare an 8 Plätzen), alle auf Land und an ihrem Platz", life.nene.size()));
        // Jahreszeiten
        com.dan.kilauea.life.Life spring = new com.dan.kilauea.life.Life(7), summer = new com.dan.kilauea.life.Life(7);
        w.day = 60; spring.update(dt, w);
        w.day = 185; summer.update(dt, w);
        int young = 0;
        for (double[] b : spring.nene.positions()) if (b[5] < 0.8) young++;
        check(young > 0 && spring.nene.size() > 16 && summer.nene.size() != 16 && com.dan.kilauea.life.Nene.nesting(60) && !com.dan.kilauea.life.Nene.nesting(185),
                String.format(Locale.GERMAN, "Nēnē: im März %d mit %d Gösseln, im Juli Trupps (%d), Brutzeit September bis Mai [L]", spring.nene.size(), young, summer.nene.size()));
        // Sperrzone: Fontäne am Platz Uēkahuna, die Nēnē fliegen weg; Wanderer kehren um
        w.day = 281;
        w.hazard(life.nene.siteX(0), life.nene.siteZ(0), 400);
        for (double t = 0; t < 120; t += dt) life.update(dt, w);
        int left = 0;
        for (double[] b : life.nene.positions()) if (b[4] == 0) left++;
        w.clearHazards();
        check(left == 0, "Nēnē verlassen einen gesperrten Platz (Fontäne) und fliegen zu einem anderen");
        Place ik = Place.KILAUEA_IKI;
        w.camX = ik.x(); w.camZ = ik.z(); w.camY = Ground.height(ik.x(), ik.z()) + 200;
        for (double t = 0; t < 120; t += dt) life.update(dt, w);
        double off = 0;
        for (double[] p : life.hikers.positions()) {
            var path = life.hikers.paths.get((int) p[3]);
            off = Math.max(off, path.distance(p[0], p[2], null));
        }
        int people = life.hikers.people();
        check(people >= 10 && off < 2.0, String.format(Locale.GERMAN, "Wanderer: %d unterwegs, höchstens %.1f m neben dem Weg", people, off));
        w.hazard(ik.x(), ik.z(), 1500);
        int inside = 0;
        for (double t = 0; t < 200; t += dt) {
            life.update(dt, w);
            if (t > 100) for (double[] p : life.hikers.positions()) if (w.closed(p[0], p[2], 0)) inside++;
        }
        w.clearHazards();
        check(inside == 0, "Wanderer: niemand bleibt in der Sperrzone um eine Fontäne im Kīlauea Iki");
        w.hour = 22.5; w.sunElevation = -40;
        for (double t = 0; t < 60; t += dt) life.update(dt, w);
        int carsNight = life.cars.size();
        check(life.shown(com.dan.kilauea.life.Life.Group.HIKERS) == 0 && carsNight < 12, String.format(Locale.GERMAN, "Nachts: keine Wanderer, %d Autos (am Tag %d)", carsNight, 30));
        // Waldvögel am Morgen, nachts still
        Place nh = Place.NAHUKU;
        w.camX = nh.x(); w.camZ = nh.z(); w.camY = Ground.height(nh.x(), nh.z()) + 2;
        w.hour = 6.8; w.sunElevation = 3;
        for (double t = 0; t < 60; t += dt) life.update(dt, w);
        int birds = life.birds.songCount();
        long calls0 = life.callSeq();
        for (double t = 0; t < 30; t += dt) life.update(dt, w);
        long calls = life.callSeq() - calls0;
        check(birds >= 10 && calls >= 15, String.format(Locale.GERMAN, "Waldvögel am Morgen bei Nāhuku: %d im Umkreis, %d Rufe in 30 s", birds, calls));
        // Jede Gruppe einzeln aus und wieder an, ohne Neuladen
        w.hour = 11; w.sunElevation = 60;
        double[] nb0 = life.nene.positions().get(0);
        w.camX = nb0[0] + 30; w.camZ = nb0[2] + 30; w.camY = Ground.height(w.camX, w.camZ) + 60;
        life.demoHelicopter(w);
        for (double t = 0; t < 20; t += dt) life.update(dt, w);
        var grid = Ground.grid();
        boolean toggles = true;
        StringBuilder tb = new StringBuilder();
        for (var g : com.dan.kilauea.life.Life.Group.values()) {
            int before = life.shown(g);
            int[] others = new int[com.dan.kilauea.life.Life.Group.values().length];
            life.set(g, false);
            life.update(dt, w);
            boolean off0 = life.shown(g) == 0;
            for (var h : com.dan.kilauea.life.Life.Group.values()) others[h.ordinal()] = life.shown(h);
            life.set(g, true);
            life.update(dt, w);
            int after = life.shown(g);
            boolean ok = before > 0 && off0 && after > 0 && Math.abs(after - before) <= Math.max(2, before / 5);
            toggles &= ok;
            tb.append(String.format(Locale.GERMAN, "%s %d→0→%d · ", g.label, before, after));
        }
        check(toggles && Ground.grid() == grid, "Jede Gruppe einzeln aus und an, sofort, ohne Neuladen: " + tb);
        // Netze: Maße
        var nene = com.dan.kilauea.life.Shapes.nene();
        var heli = com.dan.kilauea.life.Shapes.helicopter();
        var car = com.dan.kilauea.life.Shapes.car(0);
        var hiker = com.dan.kilauea.life.Shapes.hiker(0);
        float rotor = 0;
        for (int i = 0; i < heli.vertices(); i++)
            if (heli.part[i] == com.dan.kilauea.life.LifeMesh.ROTOR)
                rotor = (float) Math.max(rotor, 2 * Math.hypot(heli.pos[3 * i] - heli.pivot[3 * i], heli.pos[3 * i + 2] - heli.pivot[3 * i + 2]));
        boolean normals = true;
        for (int k = 0; k < com.dan.kilauea.life.Life.KINDS; k++) {
            var m = com.dan.kilauea.life.Life.mesh(k);
            for (int i = 0; i < m.vertices(); i++) {
                double l = Math.sqrt(m.nrm[3 * i] * m.nrm[3 * i] + m.nrm[3 * i + 1] * m.nrm[3 * i + 1] + m.nrm[3 * i + 2] * m.nrm[3 * i + 2]);
                if (Math.abs(l - 1) > 1e-3) normals = false;
            }
            for (int t : m.tri) if (t < 0 || t >= m.vertices()) normals = false;
        }
        check(nene.height > 0.38 && nene.height < 0.47 && rotor > 10.5 && rotor < 10.9 && car.length > 4.6 && car.length < 5.2 && hiker.height > 1.65 && hiker.height < 1.85 && normals,
                String.format(Locale.GERMAN, "Figuren: Nēnē %.2f m hoch (rund 0,41 [L]), Rotor %.1f m, Auto %.1f m, Wanderer %.2f m; Normalen und Dreiecke gültig",
                        nene.height, rotor, car.length, hiker.height));
        check(com.dan.kilauea.monitor.Thermal.Kind.of(9) == com.dan.kilauea.monitor.Thermal.Kind.LIFE, "Wärmebild: Figuren als eigene Art (Körper, Motor, Abgas)");
        // Schrift: Ersatz für das ʻokina
        String name = "Halemaʻumaʻu";
        java.awt.Font lacking = null;
        for (String fam : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            java.awt.Font f = new java.awt.Font(fam, java.awt.Font.PLAIN, 20);
            if (!f.canDisplay(0x02BB) && f.canDisplay('H') && f.getFamily().equals(fam)) { lacking = f; break; }
        }
        if (lacking != null) {
            String fit = com.dan.kilauea.ui.Glyphs.fit(lacking, name);
            check(lacking.canDisplayUpTo(fit) < 0 && fit.startsWith("Halema") && fit.length() == name.length(),
                    "Schrift ohne ʻokina (" + lacking.getFamily() + "): „" + fit + "“ statt Kästchen (Statusbild Phase 14)");
        } else check(com.dan.kilauea.ui.Glyphs.fit(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12), name).equals(name), "Schrift: ʻokina bleibt, wo die Schrift es kann");
    }

    static void klang() {
        System.out.println("Klang (Phase 15)");
        var probes = com.dan.kilauea.tools.KlangProbe.probes();
        boolean finite = true;
        double peak = 0, worst = 0;
        StringBuilder lv = new StringBuilder();
        long t0 = System.nanoTime();
        double secs = 0;
        for (var p : probes) {
            var e = new com.dan.kilauea.sound.SoundEngine();
            float[][] lr = com.dan.kilauea.tools.KlangProbe.render(p, 5, e);
            secs += 5;
            for (float[] ch : lr) for (float v : ch) { if (!Float.isFinite(v)) finite = false; peak = Math.max(peak, Math.abs(v)); }
            double db = com.dan.kilauea.tools.KlangProbe.dbfs(lr[0], lr[1], 0, lr[0].length);
            worst = Math.min(worst, db);
            lv.append(String.format(Locale.GERMAN, "%s %.0f · ", p.name(), db));
        }
        double rt = (System.nanoTime() - t0) / 1e9 / secs;
        check(finite && peak <= 1.0 && worst > -70, String.format(Locale.GERMAN, "16 Hörproben: endlich, Spitze %.2f, jede hörbar (dBFS: %s)", peak, lv));
        check(rt < 0.5, String.format(Locale.GERMAN, "Rechenzeit: %.1f %% der Echtzeit (mit den Lagen der Proben, 2 Kerne)", 100 * rt));
        // jede Gruppe einzeln aus: nach dem Ausblenden still, andere unberührt
        var all = com.dan.kilauea.sound.SoundEngine.Group.values();
        String[] probeOf = {"fontaene_uekahuna", "strom_maunaulu", "meer_klippe", "wind_sturm", "wald_nacht", "beben_einsturz", "hubschrauber"};
        boolean groups = true;
        StringBuilder gb = new StringBuilder();
        for (int i = 0; i < all.length; i++) {
            String pn = probeOf[i];
            var p = probes.stream().filter(q -> q.name().equals(pn)).findFirst().orElseThrow();
            var e = new com.dan.kilauea.sound.SoundEngine();
            for (var g : all) e.set(g, g == all[i]);
            float[][] on = com.dan.kilauea.tools.KlangProbe.render(p, 4.5, e);
            int n = on[0].length;
            double dbOn = com.dan.kilauea.tools.KlangProbe.dbfs(on[0], on[1], n / 2, n);
            e.set(all[i], false);
            float[][] off = e.offline(1.0, t -> p.at().apply(4.5 + t));
            int m = off[0].length;
            double dbOff = com.dan.kilauea.tools.KlangProbe.dbfs(off[0], off[1], m / 2, m);
            boolean ok = dbOn > -65 && dbOff < -110;
            groups &= ok;
            gb.append(String.format(Locale.GERMAN, "%s %.0f→%s · ", all[i].label, dbOn, dbOff < -110 ? "still" : String.format(Locale.GERMAN, "%.0f", dbOff)));
        }
        check(groups, "Jede Klanggruppe einzeln an und aus, sofort: " + gb);
        // Entfernung: Fontäne in 500 m und 2 000 m
        double[] dbAt = new double[2];
        for (int k = 0; k < 2; k++) {
            double d = k == 0 ? 500 : 2000;
            var e = new com.dan.kilauea.sound.SoundEngine();
            for (var g : all) e.set(g, g == com.dan.kilauea.sound.SoundEngine.Group.FOUNTAIN);
            float[][] lr = e.offline(4, t -> {
                var s = new com.dan.kilauea.sound.Scene();
                s.listener(0, 1.7, 0, 0, -1);
                s.fountainCount = 1;
                s.fountains[0] = 0; s.fountains[1] = -60; s.fountains[2] = -d; s.fountains[3] = 200; s.fountains[4] = 20;
                return s;
            });
            int n = lr[0].length;
            dbAt[k] = com.dan.kilauea.tools.KlangProbe.dbfs(lr[0], lr[1], n / 4, n);
        }
        double drop = dbAt[0] - dbAt[1];
        check(drop > 10 && drop < 24, String.format(Locale.GERMAN, "Entfernung: Fontäne in 2 km %.1f dB leiser als in 500 m (Kugelwelle 12 dB, gestaucht, dazu Luftdämpfung)", drop));
        // Stereo: Auto rechts
        var e = new com.dan.kilauea.sound.SoundEngine();
        float[][] lr = e.offline(2, t -> {
            var s = new com.dan.kilauea.sound.Scene();
            s.listener(0, 1.7, 0, 0, -1);   // Blick nach Norden, rechts ist Osten
            s.sourceCount = 1;
            s.sources[0] = com.dan.kilauea.life.Life.E_CAR; s.sources[1] = 20; s.sources[2] = 1.7f; s.sources[3] = 0; s.sources[7] = 14;
            return s;
        });
        int n = lr[0].length;
        double l = com.dan.kilauea.tools.KlangProbe.dbfs(lr[0], new float[n], n / 2, n), r = com.dan.kilauea.tools.KlangProbe.dbfs(new float[n], lr[1], n / 2, n);
        check(r - l > 6, String.format(Locale.GERMAN, "Stereo: Auto 20 m rechts, rechter Kanal %.1f dB lauter", r - l));
        // Doppler
        double toward = com.dan.kilauea.sound.TrafficSound.doppler(-46, 0, 0, 1000, 0, 0), away = com.dan.kilauea.sound.TrafficSound.doppler(46, 0, 0, 1000, 0, 0);
        check(Math.abs(toward - 343 / (343 - 46.0)) < 1e-6 && Math.abs(away - 343 / (343 + 46.0)) < 1e-6,
                String.format(Locale.GERMAN, "Doppler: Hubschrauber mit 46 m/s kommt %.3f-fach höher, entfernt sich %.3f-fach tiefer (c / (c ∓ v)) [L]", toward, away));
        // Coquí nachts, am Tag still; Frequenzen
        var nacht = probes.stream().filter(q -> q.name().equals("wald_nacht")).findFirst().orElseThrow();
        e = new com.dan.kilauea.sound.SoundEngine();
        for (var g : all) e.set(g, g == com.dan.kilauea.sound.SoundEngine.Group.FOREST);
        lr = com.dan.kilauea.tools.KlangProbe.render(nacht, 8, e);
        n = lr[0].length;
        double ko = com.dan.kilauea.tools.KlangProbe.band(lr[0], n / 4, n, 1050, 1250), kee = com.dan.kilauea.tools.KlangProbe.band(lr[0], n / 4, n, 1900, 2300),
                other = com.dan.kilauea.tools.KlangProbe.band(lr[0], n / 4, n, 3000, 5000);
        check(ko > 30 * other && kee > 30 * other, String.format(Locale.GERMAN, "Coquí nachts: „ko“ bei 1,1–1,2 kHz und „kí“ bei 1,9–2,3 kHz %.0f- und %.0f-mal stärker als 3–5 kHz [L Größenordnung]",
                ko / other, kee / other));
        // Beben: Laufzeit der P-Welle
        e = new com.dan.kilauea.sound.SoundEngine();
        for (var g : all) e.set(g, g == com.dan.kilauea.sound.SoundEngine.Group.QUAKE);
        e.post(com.dan.kilauea.sound.SoundEngine.EV_QUAKE, 6000, -500, 0, 3.5);
        lr = e.offline(4, t -> {
            var s = new com.dan.kilauea.sound.Scene();
            s.listener(0, 0, 0, 0, -1);
            return s;
        });
        n = lr[0].length;
        int onset = -1;
        for (int i = 0; i < n; i++) if (Math.abs(lr[0][i]) + Math.abs(lr[1][i]) > 1e-3) { onset = i; break; }
        double tp = Math.sqrt(6000.0 * 6000 + 500 * 500) / com.dan.kilauea.monitor.Seismo.VP;
        check(onset > 0 && Math.abs(onset / 44100.0 - tp) < 0.08, String.format(Locale.GERMAN,
                "Beben M 3,5 in 6 km: Grollen beginnt nach %.2f s (P-Welle %.2f s) [L Laufzeit]", onset / 44100.0, tp));
        // stumm (Taste M)
        e = new com.dan.kilauea.sound.SoundEngine();
        e.muted(true);
        var mu = probes.get(0);
        lr = com.dan.kilauea.tools.KlangProbe.render(mu, 1.5, e);
        n = lr[0].length;
        check(com.dan.kilauea.tools.KlangProbe.dbfs(lr[0], lr[1], n / 2, n) <= -119.9, "Stumm (Taste M): kein Ton");
        boolean mono = true;
        double lastC = 1e9, lastL = 0;
        for (double d = 10; d < 20000; d *= 1.5) {
            double c = com.dan.kilauea.sound.Ear.cutoff(d), lev = com.dan.kilauea.sound.Ear.level(Math.max(0, 120 - d / 100));
            if (c > lastC + 1e-9) mono = false;
            lastC = c;
        }
        for (double spl = 0; spl < 130; spl += 5) { double lev = com.dan.kilauea.sound.Ear.level(spl); if (lev < lastL) mono = false; lastL = lev; }
        check(mono, "Luftdämpfung: Eckfrequenz sinkt mit der Entfernung; Pegel steigt mit dem Schalldruck");
    }

    static void maths() {
        System.out.println("Matrizen");
        float[] v = Mat4.lookAt(1000, 2000, 3000, 0, 0, 0);
        float[] p = Mat4.perspective(50, 1.6, 0.5, 1e5, true);
        float[] vp = Mat4.mul(p, v), inv = Mat4.invert(vp), id = Mat4.mul(vp, inv);
        double err = 0;
        for (int i = 0; i < 16; i++) err = Math.max(err, Math.abs(id[i] - (i % 5 == 0 ? 1 : 0)));
        check(err < 1e-3, String.format(Locale.GERMAN, "Inverse von Projektion·Blick (Fehler %.1e)", err));
        Cascades cs = new Cascades();
        cs.update(new double[]{0, 1500, 8000}, new double[]{0, -0.3, -0.954}, 50, 1.7, new double[]{0.3, 0.5, 0.81}, 16000, true);
        check(cs.splits[0] < cs.splits[1] && cs.splits[1] < cs.splits[2] && cs.splits[2] <= 120_000, "Schattenkaskaden aufsteigend, höchstens 120 km");
    }

    static void state() {
        System.out.println("Licht und Qualität");
        SceneState st = new SceneState();
        st.clock.set(280, 12);
        st.update(0);
        double day = st.exposure;
        st.clock.set(280, 23);
        st.update(0);
        check(st.exposure > day * 1000, String.format(Locale.GERMAN, "Nacht wird stärker belichtet als der Tag (%.0f-fach)", st.exposure / day));
        check(st.stars > 0 && st.night > 0.5, "Nachts Sterne und Nachtsehen");
        check(Quality.NIEDRIG.renderScale < Quality.HOCH.renderScale && !Quality.NIEDRIG.bloom, "Qualität NIEDRIG ist billiger als HOCH");
        double[] w = st.windVector();
        check(w[0] < 0 && w[1] > 0, "Passat aus Ostnordost weht nach Westsüdwest");
    }


    /** Phase 16a: Skripte, Zustand, Dienst ohne Datenbank (Datei, Protokoll, Ansichten), Zeitlimit, Schalter des Bedienfelds. */
    static void datenbank() {
        System.out.println("Oberfläche und Datenbank (ohne Oracle)");
        try {
            java.util.List<com.dan.kilauea.db.Skript> sk = com.dan.kilauea.db.Skript.laden(new File("db"));
            StringBuilder names = new StringBuilder();
            for (com.dan.kilauea.db.Skript x : sk) names.append(x.name).append(' ');
            check(sk.size() == 6 && names.toString().equals("01_tabellen.sql 02_objekte.sql 03_api.sql 04_wissen.sql 05_wissen_objekte.sql 06_grunddaten.sql "),
                    "Sechs Skripte in db: " + names.toString().trim());
            int tab = 0, seq = 0, trg = 0, idx = 0, pkg = 0, body = 0, plsql = 0, plsqlOk = 0, semi = 0;
            StringBuilder namen = new StringBuilder();
            for (com.dan.kilauea.db.Skript x : sk) {
                for (com.dan.kilauea.db.Skript.Objekt o : x.erwartet()) {
                    switch (o.typ) {
                        case "TABLE": tab++; break;
                        case "SEQUENCE": seq++; break;
                        case "TRIGGER": trg++; break;
                        case "INDEX": idx++; break;
                        case "PACKAGE": pkg++; break;
                        case "PACKAGE BODY": body++; break;
                        default: break;
                    }
                    if (!o.name.startsWith("KIL_") || o.name.length() > 30) namen.append(o.name).append(' ');
                }
                for (String a : x.anweisungen) {
                    String u = a.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
                    boolean pl = u.startsWith("BEGIN") || u.startsWith("CREATE OR REPLACE");
                    if (pl) { plsql++; if (u.endsWith("END;") || u.matches(".*END \\w+;$")) plsqlOk++; }
                    else if (a.trim().endsWith(";")) semi++;
                }
            }
            check(namen.length() == 0, "Alle Objektnamen mit KIL_ und höchstens 30 Zeichen" + (namen.length() > 0 ? ": " + namen : ""));
            check(tab == 13 && seq == 13 && trg == 13 && pkg == 2 && body == 2, String.format(Locale.GERMAN,
                    "Erwartete Objekte: %d Tabellen, %d Folgen, %d Trigger, Pakete und Körper KIL_API und KIL_WISSEN", tab, seq, trg));
            check(idx == 44, "44 Indizes: 13 Schlüssel, 8 eindeutig, 11 für Abfragen, 12 Spatial (" + idx + ")");
            check(plsql == plsqlOk && plsql == 29 + 416, "Jeder PL/SQL-Block endet mit END; (" + plsqlOk + " von " + plsql + ", davon 416 in den Grunddaten)");
            check(semi == 0, "Keine SQL-Anweisung endet mit ; (Oracle über JDBC verträgt das nicht)");
            String ddl = new String(java.nio.file.Files.readAllBytes(new File("db/01_tabellen.sql").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String zt = ddl.substring(ddl.indexOf("CREATE TABLE KIL_ZUSTAND"), ddl.indexOf("CREATE TABLE KIL_SCHALTER"));
            int fehlt = 0;
            for (com.dan.kilauea.db.Zustand.Spalte sp : com.dan.kilauea.db.Zustand.SPALTEN) if (!zt.matches("(?s).*\\n  " + sp.spalte + "\\s.*")) fehlt++;
            check(fehlt == 0, "Alle " + com.dan.kilauea.db.Zustand.SPALTEN.length + " festen Zustandsspalten stehen in KIL_ZUSTAND");
            check(zt.contains("'NIEDRIG','MITTEL','HOCH','ULTRA'") && zt.contains("'FLUG','KREISEN','GEHEN'"), "Qualität und Modus wie in der Prüfung der Tabelle");
            String pt = ddl.substring(ddl.indexOf("CREATE TABLE KIL_PROTOKOLL"));
            String ck = pt.substring(pt.indexOf("ART IN ("), pt.indexOf("))", pt.indexOf("ART IN (")));
            boolean arten = true;
            for (String a : com.dan.kilauea.db.Dienst.ARTEN) arten &= ck.contains("'" + a + "'");
            check(arten && ck.split("'").length / 2 == com.dan.kilauea.db.Dienst.ARTEN.length, "Arten im Protokoll: Programm und KIL_PROTOKOLL_ART_CK gleich (" + com.dan.kilauea.db.Dienst.ARTEN.length + ")");
        } catch (Exception e) {
            check(false, "Skripte lesbar: " + e);
        }

        // Zustand: Grenzen, Runden, Namen
        com.dan.kilauea.db.Zustand z = com.dan.kilauea.db.Einrichter.probe();
        check(z.hatKamera() && z.hatZeit(), "Probe-Zustand hat Kamera und Zeit");
        int fest = 0;
        for (com.dan.kilauea.db.Zustand.Spalte sp : com.dan.kilauea.db.Zustand.SPALTEN) if (z.hat(sp.name)) fest++;
        check(fest == com.dan.kilauea.db.Zustand.SPALTEN.length && z.schalter().size() == 2, "Probe füllt alle " + fest + " Spalten und 2 Schalter");
        com.dan.kilauea.db.Zustand t = new com.dan.kilauea.db.Zustand();
        t.setze("kamera.gier", 359.999).setze("zeit.stunde", 24.0).setze("kamera.neigung", 95).setze("bild.qualitaet", "hoch").setze("kamera.modus", "seitwärts");
        check("0".equals(t.text("kamera.gier")) && "23.999".equals(t.text("zeit.stunde")) && "90".equals(t.text("kamera.neigung")),
                "Grenzen wie in der Tabelle: Gier 359,999 → 0, Stunde 24 → 23,999, Neigung 95 → 90");
        check("HOCH".equals(t.text("bild.qualitaet")) && !t.hat("kamera.modus"), "Qualität groß geschrieben, unbekannter Modus wird abgelehnt");
        t.setze("kamera.gier", -10);
        check("350".equals(t.text("kamera.gier")), "Gier −10° wird 350°");
        boolean wirft = false;
        try { t.setze("Leben.Nēnē", "J"); } catch (IllegalArgumentException e) { wirft = true; }
        check(wirft && com.dan.kilauea.db.Zustand.gueltigerName("leben.nene") && !com.dan.kilauea.db.Zustand.gueltigerName("leben"),
                "Schalternamen wie KIL_SCHALTER_NAME_CK: leben.nene ja, Leben.Nēnē und leben nein");

        // Dienst ohne Datenbank: Datei, Protokoll, Ansichten
        try {
            File tmp = java.nio.file.Files.createTempDirectory("kil_p16").toFile();
            File logs = new File(tmp, "logs");
            logs.mkdirs();
            File alt = new File(logs, "kilauea_2026-01-01.log"), jung = new File(logs, "kilauea_" + java.time.LocalDate.now().minusDays(3) + ".log");
            java.nio.file.Files.write(alt.toPath(), "alt\n".getBytes());
            java.nio.file.Files.write(jung.toPath(), "jung\n".getBytes());
            System.setProperty("kilauea.state.dir", new File(tmp, "state").getPath());
            System.setProperty("kilauea.log.dir", logs.getPath());
            System.setProperty("kilauea.nodb", "true");
            com.dan.kilauea.db.Dienst.start(new com.dan.kilauea.db.Dienst.Kontext() {
                @Override public int tag() { return 280; }
                @Override public double stunde() { return 17.25; }
                @Override public double[] ort() { return new double[]{Geo.LON0, Geo.LAT0}; }
            });
            com.dan.kilauea.db.Dienst.warte(2000);
            check(com.dan.kilauea.db.Dienst.lage() == com.dan.kilauea.db.Dienst.Lage.AUS, "Mit -Dkilauea.nodb=true: ohne Datenbank (" + com.dan.kilauea.db.Dienst.kurz() + ")");
            check(!alt.exists() && jung.exists(), "Tagesprotokolle älter als 14 Tage gelöscht, jüngere bleiben");
            com.dan.kilauea.db.Dienst.sitzungStart(1508, 940, "HOCH", false);
            com.dan.kilauea.db.Dienst.ereignis("DREHBUCH", "Fontänen im Halemaʻumaʻu 2024/25", "abgespielt", true);
            com.dan.kilauea.db.Dienst.ereignis("UNBEKANNT", "x", "y", false);
            File heute = new File(logs, "kilauea_" + java.time.LocalDate.now() + ".log");
            String log = heute.isFile() ? new String(java.nio.file.Files.readAllBytes(heute.toPath()), java.nio.charset.StandardCharsets.UTF_8) : "";
            check(log.contains("\tSTART\t") && log.contains("\tDREHBUCH\tFontänen im Halemaʻumaʻu 2024/25\tabgespielt\tSzene Tag 280 17.25 h\t19.40720 -155.28340"),
                    "Tagesprotokoll mit Art, Ziel, Szenenzeit und Ort, ʻokina erhalten");
            check(log.contains("\tINFO\tx\tUNBEKANNT: y"), "Unbekannte Art wird INFO (KIL_PROTOKOLL_ART_CK bleibt erfüllt)");
            java.util.List<String> letzte = com.dan.kilauea.db.Dienst.letzte();
            check(letzte.size() == 4 && letzte.get(0).contains("INFO") && letzte.get(2).contains("START") && letzte.get(3).contains("DATENBANK ohne Datenbank"),
                    "Die letzten Einträge fürs Bedienfeld, neueste zuerst: Datenbank, Start, Drehbuch, Info (" + letzte.size() + ")");

            com.dan.kilauea.db.Zustand auto = com.dan.kilauea.db.Einrichter.probe();
            auto.setze("leben.birds", false).setze("panel.leben", true).setze("klang.lautstaerke", 55);
            com.dan.kilauea.db.Dienst.zwischensicherung(auto.kopie());
            com.dan.kilauea.db.Zustand back = com.dan.kilauea.db.Dienst.ladeLetzten();
            check(back != null && back.werte().equals(auto.werte()) && "AUTO".equals(back.art), "Zustand über die Datei unverändert zurück (" + auto.werte().size() + " Werte)");

            com.dan.kilauea.db.Zustand ans = auto.nurKamera(false);
            check(ans.hatKamera() && !ans.hatZeit() && ans.schalter().isEmpty(), "Ansicht ohne Zeit: nur die 7 Werte der Kamera");
            String m1 = com.dan.kilauea.db.Dienst.merken(ans, "Blick auf Halemaʻumaʻu");
            Thread.sleep(5);
            String m2 = com.dan.kilauea.db.Dienst.merken(auto.nurKamera(true), "Uēkahuna am Abend");
            java.util.List<com.dan.kilauea.db.Dienst.Ansicht> l = com.dan.kilauea.db.Dienst.ansichten();
            check(l.size() == 2 && l.get(0).name.equals("Uēkahuna am Abend") && l.get(0).mitZeit && !l.get(1).mitZeit && l.get(0).ausDatei,
                    "Zwei Ansichten in der Datei, neueste zuerst, eine mit Zeit (" + m1 + " / " + m2 + ")");
            com.dan.kilauea.db.Zustand la = com.dan.kilauea.db.Dienst.laden(l.get(1));
            check(la != null && la.hatKamera() && la.zahl("kamera.abstand", 0) == 2600, "Ansicht lädt mit Kamera (Abstand 2 600 m)");
            check(com.dan.kilauea.db.Dienst.umbenennen(l.get(1), "Krater von oben") && com.dan.kilauea.db.Dienst.ansichten().get(1).name.equals("Krater von oben"), "Ansicht umbenennen");
            check(com.dan.kilauea.db.Dienst.loeschen(com.dan.kilauea.db.Dienst.ansichten().get(0)) && com.dan.kilauea.db.Dienst.ansichten().size() == 1, "Ansicht löschen");
            String beschr = com.dan.kilauea.ui.Startbild.beschreibe(back);
            check(beschr.contains("Halemaʻumaʻu") && beschr.contains("HST") && beschr.contains("Okt"), "Startbild beschreibt den letzten Zustand: " + beschr);

            long t0 = System.nanoTime();
            com.dan.kilauea.db.Zustand ende = auto.kopie();
            ende.setze("kamera.abstand", 1234);
            com.dan.kilauea.db.Dienst.beenden(ende);
            double sek = (System.nanoTime() - t0) / 1e9;
            com.dan.kilauea.db.Zustand ausDatei = com.dan.kilauea.db.Zustand.laden(com.dan.kilauea.db.Dienst.zustandsDatei());
            check(sek < 2.5 && ausDatei != null && ausDatei.zahl("kamera.abstand", 0) == 1234, String.format(Locale.GERMAN, "Beenden in %.2f s, Zustand in der Datei", sek));
            log = new String(java.nio.file.Files.readAllBytes(heute.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            check(log.contains("\tENDE\t") && log.contains("\tANSICHT\tKrater von oben\tumbenannt"), "Ende und Ansichten im Tagesprotokoll");
        } catch (Exception e) {
            check(false, "Dienst ohne Datenbank: " + e);
        }

        // Zeitlimit: eine Datenbank, die nicht antwortet, hält höchstens rund 3 s auf
        try {
            File d = java.nio.file.Files.createTempDirectory("kil_db").toFile();
            java.nio.file.Files.write(new File(d, "db.properties").toPath(),
                    "url=jdbc:oracle:thin:@//10.255.255.1:1521/PDBORCL\nuser=KILAUEA\npassword=x\n".getBytes());
            System.setProperty("kilauea.db.dir", d.getPath());
            com.dan.kilauea.db.Db.Konfig k = com.dan.kilauea.db.Db.konfig();
            long t0 = System.nanoTime();
            String fehler = "keiner";
            try (java.sql.Connection c = com.dan.kilauea.db.Db.oeffnen(k)) { /* sollte nicht gehen */ } catch (java.sql.SQLException e) { fehler = e.getClass().getSimpleName(); }
            double sek = (System.nanoTime() - t0) / 1e9;
            check(!fehler.equals("keiner") && sek < 5.0, String.format(Locale.GERMAN, "Unerreichbare Datenbank: Abbruch nach %.1f s (%s)", sek, fehler));
            System.clearProperty("kilauea.db.dir");
        } catch (Exception e) {
            check(false, "Zeitlimit: " + e);
        }

        // Schalter des Bedienfelds (aus dem Quelltext; das Bedienfeld selbst braucht eine Grafikkarte)
        try {
            String cp = new String(java.nio.file.Files.readAllBytes(new File("src/com/dan/kilauea/ui/ControlPanel.java").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:flag|bind|bindIndex|bindSlider)\\(\"([a-z0-9_.]+)\"").matcher(cp);
            java.util.Set<String> keys = new java.util.TreeSet<>();
            int n = 0;
            boolean gueltig = true;
            while (m.find()) {
                if (m.group(1).endsWith(".")) continue; // „leben.“ + Gruppe, „panel.“ + Block: zählen unten
                n++;
                keys.add(m.group(1));
                gueltig &= com.dan.kilauea.db.Zustand.gueltigerName(m.group(1));
            }
            int bloecke = cp.split("head\\(\"", -1).length - 1;
            int schleifen = com.dan.kilauea.life.Life.Group.values().length + com.dan.kilauea.sound.SoundEngine.Group.values().length;
            check(gueltig && n == keys.size(), "Schalternamen im Bedienfeld gültig und eindeutig (" + keys.size() + " feste Namen)");
            boolean festeDa = true;
            for (String f : new String[]{"zeit.raffer", "luft.dunst", "luft.wind", "luft.richtung", "meer.swell", "meer.periode", "bild.qualitaet", "bild.belichtung"})
                festeDa &= keys.contains(f) && com.dan.kilauea.db.Zustand.fest(f);
            check(festeDa, "Alle Regler mit fester Spalte sind im Bedienfeld gebunden");
            int gesamt = keys.size() + schleifen + bloecke;
            check(bloecke == 11 && gesamt >= 45, "Zum Zustand gehören " + gesamt + " Werte: " + keys.size() + " Schalter und Regler, " + schleifen
                    + " Gruppen von Leben und Klang, " + bloecke + " Blöcke offen/zu");
        } catch (Exception e) {
            check(false, "Bedienfeld lesbar: " + e);
        }
    }

    /** Phase 16b: Grunddaten aus dem Code, Literale gegen die Tabellen, Geometrien, Läufe ohne Datenbank. */
    static void wissen(HeightGrid g) {
        System.out.println("Wissen und Geschichte (Phase 16b, ohne Oracle)");
        String ddl = "", dml = "", obj = "";
        try {
            ddl = new String(java.nio.file.Files.readAllBytes(new File("db/04_wissen.sql").toPath()), java.nio.charset.StandardCharsets.UTF_8)
                    + new String(java.nio.file.Files.readAllBytes(new File("db/01_tabellen.sql").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            dml = new String(java.nio.file.Files.readAllBytes(new File("db/06_grunddaten.sql").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            obj = new String(java.nio.file.Files.readAllBytes(new File("db/05_wissen_objekte.sql").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            check(false, "Skripte 04 bis 06 lesbar: " + e);
            return;
        }
        // jede Tabelle aus 16b: Kommentar an jeder Spalte, Folge und Trigger, Name im Dienst
        java.util.Map<String, java.util.Map<String, String>> spalten = spalten(ddl);
        int ohne = 0, tabs = 0;
        boolean folgen = true;
        for (String t : com.dan.kilauea.db.Dienst.TABELLEN_WISSEN) {
            java.util.Map<String, String> sp = spalten.get(t);
            if (sp == null) { ohne++; continue; }
            tabs++;
            if (!ddl.contains("COMMENT ON TABLE " + t + " IS")) ohne++;
            for (String c : sp.keySet()) if (!ddl.contains("COMMENT ON COLUMN " + t + "." + c + " IS")) ohne++;
            String k = t.substring(4);
            folgen &= obj.contains("CREATE SEQUENCE " + t + "_SEQ") && obj.contains(":NEW." + k + "_ID := " + t + "_SEQ.NEXTVAL");
        }
        check(tabs == 9 && ohne == 0, "Neun Tabellen für Wissen und Geschichte, Kommentar an jeder Tabelle und Spalte (" + ohne + " ohne)");
        check(folgen, "Jede Tabelle aus 16b hat Folge und Trigger für die ID");
        int meta = 0, sx = 0;
        for (String[] ge : new String[][]{{"KIL_ORT", "GEOM"}, {"KIL_KRATER", "MITTE"}, {"KIL_KRATER", "RAND"}, {"KIL_STROM", "SCHLOT"}, {"KIL_STROM", "SPALTE"},
                {"KIL_STROM", "ACHSE"}, {"KIL_STROM", "UMRISS"}, {"KIL_STATION", "GEOM"}, {"KIL_LAUF", "GEOM"}, {"KIL_BEBEN", "GEOM"}}) {
            if (obj.contains("VALUES ('" + ge[0] + "', '" + ge[1] + "',")) meta++;
            if (obj.contains("CREATE INDEX " + ge[0] + "_" + ge[1] + "_SX ON " + ge[0] + " (" + ge[1] + ") INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2")) sx++;
            if (!"MDSYS.SDO_GEOMETRY".equals(spalten.getOrDefault(ge[0], java.util.Map.of()).get(ge[1]))) meta = -99;
        }
        check(meta == 10 && sx == 10, "Zehn Geometriespalten aus 16b mit Metadaten und Spatial-Index (" + meta + ", " + sx + ")");

        // Grunddaten: gleich dem, was der Code heute schreibt
        long t0 = System.nanoTime();
        com.dan.kilauea.db.Grunddaten.Ergebnis e = null;
        try {
            // genau wie tools/Grunddaten: frisches Raster, Straßen eingeebnet, Kacheln am Gipfel
            File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
            HeightGrid g2 = TerrainBuilder.load(dir);
            com.dan.kilauea.terrain.ParkRoads.build(g2);
            com.dan.kilauea.terrain.DetailTiles.use(com.dan.kilauea.terrain.DetailTiles.load(dir, g2, null));
            Ground.use(g2);
            e = com.dan.kilauea.db.Grunddaten.rechne(g2);
            if (!e.sql.equals(dml)) java.nio.file.Files.write(new File(System.getProperty("java.io.tmpdir"), "06_grunddaten_neu.sql").toPath(), e.sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception x) {
            check(false, "Grunddaten rechnen: " + x);
            return;
        }
        check(e.sql.equals(dml), String.format(Locale.GERMAN, "db/06_grunddaten.sql entspricht dem Code (%d Anweisungen, %,d Punkte, %.1f s)", e.anweisungen, e.punkte, (System.nanoTime() - t0) / 1e9));
        check(e.zeilen.equals(java.util.Map.of("KIL_ORT", Place.values().length, "KIL_KRATER", 14, "KIL_STROM", Geology.FLOWS.size() + Geology.PATCHES.size(),
                "KIL_STATION", 12, "KIL_DREHBUCH", 6, "KIL_KAPITEL", e.zeilen.get("KIL_KAPITEL"))) && e.zeilen.get("KIL_KAPITEL") >= 25, "Zeilen der Grunddaten: " + e.zeilen);
        check(e.hinweise.isEmpty() || e.hinweise.stream().allMatch(h -> h.startsWith("Zellen:")), "Keine Hinweise beim Rechnen außer der Zuordnung der Zellen");

        // Literale gegen Länge, Stellen und erlaubte Werte der Spalten
        java.util.List<String> fehler = new java.util.ArrayList<>();
        int werte = literale(ddl, dml, fehler);
        check(werte > 1500 && fehler.isEmpty(), "Alle " + werte + " Werte der Grunddaten passen in ihre Spalten" + (fehler.isEmpty() ? "" : ": " + fehler.subList(0, Math.min(4, fehler.size()))));

        // Geometrien: Ringe geschlossen, außen gegen den Uhrzeigersinn, Löcher mit, keine doppelten Punkte, einfach
        int ringe = 0, falsch = 0;
        for (com.dan.kilauea.db.Grunddaten.Krater k : e.krater) {
            ringe++;
            if (!ringOk(k.rand, true)) falsch++;
        }
        int teile = 0, loecher = 0;
        for (com.dan.kilauea.db.Grunddaten.Strom st : e.stroeme)
            for (com.dan.kilauea.db.Geometrie.Polygon pg : st.umriss) {
                teile++; ringe++;
                if (!ringOk(pg.aussen, true)) falsch++;
                for (double[] h : pg.loecher) { loecher++; ringe++; if (!ringOk(h, false) || !com.dan.kilauea.db.Geometrie.innen(pg.aussen, h[0], h[1])) falsch++; }
            }
        check(falsch == 0 && teile >= 49, "Ringe der Krater und Ströme gültig: " + ringe + " Ringe (" + teile + " Teile, " + loecher + " Löcher), " + falsch + " falsch");

        // Krater: Maße im Rahmen der Karten
        java.util.Map<String, com.dan.kilauea.db.Grunddaten.Krater> kr = new java.util.HashMap<>();
        for (com.dan.kilauea.db.Grunddaten.Krater k : e.krater) kr.put(k.def.name(), k);
        com.dan.kilauea.db.Grunddaten.Krater kp = kr.get("Kaluapele"), ki = kr.get("Kīlauea Iki"), ke = kr.get("Keanakākoʻi"), pu = kr.get("Puhimau");
        check(kp != null && kp.laenge > 3000 && kp.laenge < 5000 && kp.breite > 2200 && kp.breite < 3800,
                String.format(Locale.GERMAN, "Kaluapele %.0f × %.0f m (Karte rund 4,0 × 3,2 km)", kp == null ? 0 : kp.laenge, kp == null ? 0 : kp.breite));
        check(ki != null && ki.laenge > 1100 && ki.laenge < 2000 && ki.tiefe > 60, String.format(Locale.GERMAN, "Kīlauea Iki %.0f m lang, %.0f m tief (Karte rund 1,6 km)", ki == null ? 0 : ki.laenge, ki == null ? 0 : ki.tiefe));
        check(ke != null && ke.laenge > 350 && ke.laenge < 900, String.format(Locale.GERMAN, "Keanakākoʻi %.0f × %.0f m (Karte rund 0,5 × 0,4 km)", ke == null ? 0 : ke.laenge, ke == null ? 0 : ke.breite));
        check(pu != null && pu.tiefe > 100, String.format(Locale.GERMAN, "Puhimau %.0f m tief (rund 150 m)", pu == null ? 0 : pu.tiefe));

        // Läufe ohne Datenbank: der freie Ausbruch, gesammelt wie in der App
        try {
            com.dan.kilauea.magma.MagmaSystem mm = new com.dan.kilauea.magma.MagmaSystem(16);
            com.dan.kilauea.lava.LavaSim sim = new com.dan.kilauea.lava.LavaSim(() -> { synchronized (mm) { return mm.hmm.pressure(); } });
            sim.installGround();
            com.dan.kilauea.script.Stage stage = new com.dan.kilauea.script.Stage(sim, mm);
            com.dan.kilauea.script.Player pl = new com.dan.kilauea.script.Player(stage, new com.dan.kilauea.script.Player.Host() { });
            pl.headless = true;
            pl.outroSeconds = 0;
            com.dan.kilauea.monitor.Monitor mon = new com.dan.kilauea.monitor.Monitor(mm, sim);
            com.dan.kilauea.db.Laeufe lf = new com.dan.kilauea.db.Laeufe(mm, mon);
            pl.listener = lf;
            com.dan.kilauea.script.Script fr = com.dan.kilauea.script.Scripts.free(com.dan.kilauea.magma.Rift.EAST, 12_000, 20, 300, 2);
            mon.clear();
            pl.start(fr);
            pl.update(0);
            double ts;
            synchronized (mm) { ts = mm.time(); }
            int guard = 0, kapMax = 0;
            while (pl.state() == com.dan.kilauea.script.Player.State.PLAYING && guard++ < 200_000) {
                var c = fr.chapters().get(pl.chapterIndex());
                mon.rateOverride = c.speed;
                pl.advance(Math.min(c.speed, Math.max(1, c.duration - pl.chapterTime())));
                mon.sample(1);
                lf.sammelnJetzt();
                kapMax = Math.max(kapMax, lf.kapitel());
            }
            lf.sammelnJetzt();
            java.util.List<com.dan.kilauea.db.Laeufe.Beben> bb = lf.wartendeBeben();
            java.util.List<com.dan.kilauea.db.Laeufe.Messung> ms = lf.wartendeMessungen();
            int erwartetBeben;
            double te;
            synchronized (mm) { erwartetBeben = mm.quakesSince(ts).size(); te = mm.time(); }
            double h = (te - ts) / 3600;
            boolean monoton = true, gang = false;
            for (int i = 1; i < bb.size(); i++) monoton &= bb.get(i).stunde() >= bb.get(i - 1).stunde();
            for (var q : bb) gang |= q.quelle().equals("GANG") && q.lon() > -155.2 && q.lat() < 19.45;
            boolean takt = !ms.isEmpty() && ms.size() == (int) Math.floor(h * 4 + 1e-6) + 1 && ms.get(1).stunde() == 0.25;
            check(lf.gezaehlt() == erwartetBeben && bb.size() == erwartetBeben && monoton && gang,
                    String.format(Locale.GERMAN, "Lauf ohne Datenbank: %d Beben übernommen (Modell %d), der Zeit nach, mit Beben im Gang", bb.size(), erwartetBeben));
            check(takt && kapMax == fr.chapters().size(), String.format(Locale.GERMAN, "Lauf: %d Messzeilen im 15-Minuten-Takt über %.1f h, %d Kapitel", ms.size(), h, kapMax));
            boolean werteOk = true;
            for (var m : ms) werteOk &= Double.isFinite(m.uwd()) && Double.isFinite(m.so2()) && m.so2() >= 0 && Math.abs(m.uwd()) < 1e6;
            double rateMax = 0;
            for (var m : ms) rateMax = Math.max(rateMax, m.rate());
            check(werteOk && rateMax > 5, String.format(Locale.GERMAN, "Messzeilen endlich, Förderrate bis %.0f m³/s", rateMax));
            pl.returnToStart();
            check(!lf.aktiv() && lf.wartendeBeben().isEmpty(), "Lauf: beim Ende abgeschlossen");
            check(com.dan.kilauea.db.Laeufe.quelle(com.dan.kilauea.magma.Quake.Kind.COLLAPSE).equals("EINSTURZ")
                    && fr.frei != null && fr.frei.riftzone().equals("OST") && Math.abs(fr.frei.km() - 12) < 1e-9, "Einsturzbeben heißen EINSTURZ; der freie Ausbruch trägt Riftzone und km");
        } catch (Exception x) {
            check(false, "Lauf ohne Datenbank: " + x);
        }
    }

    /** Spalten der Tabellen aus CREATE TABLE: Name → Typ (wie geschrieben). */
    static java.util.Map<String, java.util.Map<String, String>> spalten(String ddl) {
        java.util.Map<String, java.util.Map<String, String>> r = new java.util.HashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?s)CREATE TABLE (\\w+) \\((.*?)\\n\\)\\n/").matcher(ddl);
        while (m.find()) {
            java.util.Map<String, String> sp = new java.util.LinkedHashMap<>();
            for (String line : m.group(2).split("\n")) {
                java.util.regex.Matcher c = java.util.regex.Pattern.compile("^  ([A-Z0-9_]+)\\s+((?:VARCHAR2|CHAR|NUMBER|TIMESTAMP)\\([^)]*\\)|DATE|MDSYS\\.SDO_GEOMETRY)").matcher(line);
                if (c.find() && !c.group(1).equals("CONSTRAINT")) sp.put(c.group(1), c.group(2));
            }
            r.put(m.group(1), sp);
        }
        return r;
    }

    /** Prüft jedes Literal der INSERT-Anweisungen gegen Länge, Stellen und CHECK … IN (…) der Spalte; gibt die Zahl der Werte zurück. */
    static int literale(String ddl, String dml, java.util.List<String> fehler) {
        java.util.Map<String, java.util.Map<String, String>> sp = spalten(ddl);
        java.util.Map<String, java.util.Set<String>> erlaubt = new java.util.HashMap<>();
        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("(?s)CREATE TABLE (\\w+) \\((.*?)\\n\\)\\n/").matcher(ddl);
        while (tm.find()) {
            java.util.regex.Matcher ck = java.util.regex.Pattern.compile("CHECK \\(\\(?(\\w+) IN \\(([^)]*)\\)").matcher(tm.group(2));
            while (ck.find()) {
                java.util.Set<String> w = new java.util.HashSet<>();
                for (String x : ck.group(2).split(",")) w.add(x.trim().replaceAll("^'|'$", ""));
                erlaubt.put(tm.group(1) + "." + ck.group(1), w);
            }
        }
        int n = 0;
        java.util.regex.Matcher im = java.util.regex.Pattern.compile("(?s)INSERT INTO (\\w+) \\(([^)]*)\\)\\nSELECT (.*?)\\s+FROM (?:DUAL|KIL_DREHBUCH D)\\n WHERE").matcher(dml);
        while (im.find()) {
            String t = im.group(1);
            String[] cols = im.group(2).split(",\\s*");
            java.util.List<String> vals = teile(im.group(3));
            if (vals.size() != cols.length) { fehler.add(t + ": " + cols.length + " Spalten, " + vals.size() + " Werte"); continue; }
            for (int i = 0; i < cols.length; i++) {
                String c = cols[i].trim(), v = vals.get(i).trim(), typ = sp.getOrDefault(t, java.util.Map.of()).get(c);
                n++;
                if (typ == null) { fehler.add(t + "." + c + " gibt es nicht"); continue; }
                if (v.equals("NULL")) continue;
                if (v.startsWith("'")) {
                    String lit = v.substring(1, v.length() - 1).replace("''", "'");
                    java.util.regex.Matcher l = java.util.regex.Pattern.compile("(?:VARCHAR2|CHAR)\\((\\d+)").matcher(typ);
                    if (l.find()) {
                        int max = Integer.parseInt(l.group(1));
                        int len = typ.contains("CHAR)") && typ.contains(" CHAR") ? lit.codePointCount(0, lit.length()) : lit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                        if (len > max) fehler.add(t + "." + c + " zu lang (" + len + " > " + max + ")");
                    } else fehler.add(t + "." + c + ": Text für " + typ);
                    java.util.Set<String> w = erlaubt.get(t + "." + c);
                    if (w != null && !w.contains(lit)) fehler.add(t + "." + c + " = " + lit + " nicht erlaubt");
                } else if (v.matches("-?\\d+(\\.\\d+)?")) {
                    java.util.regex.Matcher nm = java.util.regex.Pattern.compile("NUMBER\\((\\d+)(?:,(\\d+))?\\)").matcher(typ);
                    if (!nm.find()) { fehler.add(t + "." + c + ": Zahl für " + typ); continue; }
                    int p = Integer.parseInt(nm.group(1)), sc = nm.group(2) == null ? 0 : Integer.parseInt(nm.group(2));
                    if (Math.abs(Double.parseDouble(v)) >= Math.pow(10, p - sc)) fehler.add(t + "." + c + " = " + v + " passt nicht in " + typ);
                }
            }
        }
        return n;
    }

    /** Werteliste an Kommas der obersten Ebene teilen (Text in Hochkommas, Klammern). */
    static java.util.List<String> teile(String s) {
        java.util.List<String> r = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') q = !q;
            else if (!q && ch == '(') depth++;
            else if (!q && ch == ')') depth--;
            else if (!q && depth == 0 && ch == ',') { r.add(s.substring(start, i)); start = i + 1; }
        }
        r.add(s.substring(start));
        return r;
    }

    /** Offener Ring (der Schreiber schließt ihn): mindestens 3 Punkte, keine doppelten, außen gegen den Uhrzeigersinn (Löcher mit), ohne Selbstschnitt. */
    static boolean ringOk(double[] r, boolean aussen) {
        int n = r.length / 2;
        if (n < 3) return false;
        for (int i = 0; i < n; i++) { int j = (i + 1) % n; if (Math.hypot(r[2 * j] - r[2 * i], r[2 * j + 1] - r[2 * i + 1]) < 0.05) return false; }
        double f = com.dan.kilauea.db.Geometrie.flaeche(r);
        return (aussen ? f > 0 : f < 0) && com.dan.kilauea.db.Geometrie.einfach(r);
    }

    /** Phase 17: Qualitätsstufen, früher Tiefentest, Schattenproben, Reihenfolge der Knoten, Dauerlauf, Verknüpfung, Unterlagen. */
    static void schliff() {
        System.out.println("Schliff (Phase 17)");
        Quality[] q = Quality.values();
        boolean steigt = true;
        for (int i = 1; i < q.length; i++) {
            Quality a = q[i - 1], b = q[i];
            steigt &= b.renderScale >= a.renderScale && b.shadowSize >= a.shadowSize && b.shadowTaps >= a.shadowTaps
                    && b.anisotropy >= a.anisotropy && b.cloudSteps >= a.cloudSteps && b.lodRange >= a.lodRange && b.detail >= a.detail;
        }
        check(steigt && Quality.NIEDRIG.shadowTaps == 4 && Quality.HOCH.shadowTaps == 12 && Quality.HOCH.cloudSteps == 28,
                "Qualitätsstufen steigen von Niedrig bis Ultra in jedem Wert (Proben 4/8/12/12, Anisotropie 2/4/8/16, Wolken 12/16/28/36)");
        check(Quality.HOCH.summary().contains("12 Schattenproben") && Quality.NIEDRIG.summary().contains("ohne Bloom"),
                "Zusammenfassung der Stufe: " + Quality.MITTEL.summary());

        // Gelände ohne discard: in der Fassung FRUEH_Z steht kein discard, dafür der frühe Tiefentest
        String t = com.dan.kilauea.render.Shader.read("terrain.frag");
        int ifn = t.indexOf("#ifndef FRUEH_Z"), end = t.indexOf("#endif", ifn), ifd = t.indexOf("#ifdef FRUEH_Z");
        int discards = 0;
        for (int i = t.indexOf("discard"); i >= 0; i = t.indexOf("discard", i + 1)) if (t.lastIndexOf("//", i) < t.lastIndexOf('\n', i)) discards++;
        String block = ifn >= 0 && end > ifn ? t.substring(ifn, end) : "";
        String early = ifd >= 0 ? t.substring(ifd, t.indexOf("#endif", ifd)) : "";
        boolean includesClean = true;
        for (String inc : new String[]{"common.glsl", "lighting.glsl", "cars.glsl", "shadow.glsl", "terrain_height.glsl", "underwater.glsl", "weather.glsl", "lava.glsl"})
            includesClean &= !com.dan.kilauea.render.Shader.read(inc).contains("discard");
        check(discards == 2 && block.split("discard", -1).length - 1 == 2 && early.contains("layout(early_fragment_tests) in;") && includesClean,
                "Gelände: beide discard nur ohne FRUEH_Z, mit FRUEH_Z früher Tiefentest; die eingebundenen Dateien verwerfen nichts");

        // Schattenproben: die ersten vier liegen in vier verschiedenen Vierteln
        String sh = com.dan.kilauea.render.Shader.read("shadow.glsl");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("vec2\\((-?[0-9.]+), (-?[0-9.]+)\\)").matcher(sh.substring(sh.indexOf("POISSON[12]")));
        java.util.Set<Integer> viertel = new java.util.HashSet<>();
        int n = 0;
        while (m.find() && n < 4) { viertel.add((Double.parseDouble(m.group(1)) >= 0 ? 1 : 0) + (Double.parseDouble(m.group(2)) >= 0 ? 2 : 0)); n++; }
        check(viertel.size() == 4 && sh.contains("sum < 1e-4 || sum > float(first) - 1e-4"),
                "Schatten: die ersten vier Proben in vier Vierteln; voll hell oder voll dunkel endet nach vier");

        // Knoten von vorn nach hinten: Abstand nimmt nie ab, dieselben Knoten
        java.util.Random rnd = new java.util.Random(17);
        int nn = 300;
        float[] nodes = new float[nn * 4];
        for (int i = 0; i < nn; i++) {
            float size = (float) (75 * 64 * Math.pow(2, rnd.nextInt(6)));
            nodes[4 * i] = (float) (rnd.nextDouble() * 120_000 - 60_000);
            nodes[4 * i + 1] = (float) (rnd.nextDouble() * 120_000 - 60_000);
            nodes[4 * i + 2] = size;
            nodes[4 * i + 3] = i;
        }
        float[] sorted = nodes.clone();
        TerrainLod.sortNearFirst(sorted, nn, 1234, -567);
        boolean order = true;
        double last = -1;
        java.util.Set<Float> ids = new java.util.HashSet<>();
        for (int i = 0; i < nn; i++) {
            double h = sorted[4 * i + 2] / 2, d = Math.hypot(sorted[4 * i] + h - 1234, sorted[4 * i + 1] + h + 567);
            if (d < last - 4.0) order = false;   // Rundung auf 16 m² im Schlüssel
            last = Math.max(last, d);
            ids.add(sorted[4 * i + 3]);
        }
        check(order && ids.size() == nn, "Geländeknoten nahe zuerst: 300 Knoten geordnet, keiner verloren");

        // Auflösung selbst regeln: Ziel 30 Bilder/s
        double s1 = com.dan.kilauea.render.Renderer.nextScale(1.0, 32.0, 30, 1.0), s2 = com.dan.kilauea.render.Renderer.nextScale(0.8, 15.0, 30, 1.0),
                s3 = com.dan.kilauea.render.Renderer.nextScale(0.8, 23.0, 30, 1.0), s4 = com.dan.kilauea.render.Renderer.nextScale(0.5, 60.0, 30, 1.0),
                s5 = com.dan.kilauea.render.Renderer.nextScale(1.0, 5.0, 60, 0.85);
        check(Math.abs(s1 - 0.95) < 1e-9 && Math.abs(s2 - 0.85) < 1e-9 && s3 == 0.8 && s4 == 0.5 && s5 == 0.85,
                String.format(Locale.GERMAN, "Auflösung nach Ziel 30 Bilder/s: GPU 32 ms senkt (%.2f), 15 ms hebt (%.2f), 23 ms hält, nie unter 50 %%, nie über die Stufe", s1, s2));

        // Dauerlauf: Plan und Prüfliste
        var plan = com.dan.kilauea.ui.Dauerlauf.plan();
        java.util.Set<String> namen = new java.util.HashSet<>();
        for (var x : plan) namen.add(x.name());
        check(plan.size() == 8 && com.dan.kilauea.ui.Dauerlauf.dauer() == 600 && namen.size() == 8,
                "Dauerlauf: 8 Abschnitte, zusammen 10 Minuten");
        java.util.List<com.dan.kilauea.ui.Dauerlauf.Messung> gut = new java.util.ArrayList<>();
        for (int i = 1; i <= 600; i++) gut.add(new com.dan.kilauea.ui.Dauerlauf.Messung(i, (i - 1) / 75, 38 + (i % 7), 21, 4, i % 97 == 0 ? 140 : 30, 900 + (i % 50), 31, 0));
        var lg = com.dan.kilauea.ui.Dauerlauf.pruefliste(gut, 850, 905, 31, 32, true, null, false, false);
        boolean alleOk = lg.stream().allMatch(com.dan.kilauea.ui.Dauerlauf.Punkt::ok);
        java.util.List<com.dan.kilauea.ui.Dauerlauf.Messung> schlecht = new java.util.ArrayList<>(gut);
        schlecht.set(300, new com.dan.kilauea.ui.Dauerlauf.Messung(301, 4, 9, 80, 30, 820, 1200, 31, 1));
        var ls = com.dan.kilauea.ui.Dauerlauf.pruefliste(schlecht, 850, 1180, 31, 40, false, null, true, false);
        java.util.Set<String> offen = new java.util.HashSet<>();
        for (var p : ls) if (!p.ok()) offen.add(p.name());
        check(alleOk && lg.size() == 7, "Dauerlauf, guter Lauf: alle 7 Punkte in Ordnung");
        check(offen.containsAll(java.util.List.of("Keine Fehler", "Kein Hänger", "Bilder/s", "Speicher", "Fäden", "Drehbuch zurück")),
                "Dauerlauf, schlechter Lauf: Fehler, Hänger, Einbruch der Bilder/s, Speicher, Fäden und Drehbuch fallen auf (" + offen.size() + " Punkte)");
        boolean infoArt = false;
        for (String a : com.dan.kilauea.db.Dienst.ARTEN) infoArt |= a.equals("INFO") || a.equals("WARNUNG");
        int f0 = com.dan.kilauea.db.Dienst.fehlerZahl();
        com.dan.kilauea.db.Dienst.ereignis("FEHLER", "Prüfung", "Zählprobe (Phase 17)", false);
        check(infoArt && com.dan.kilauea.db.Dienst.fehlerZahl() == f0 + 1, "Protokoll: Dauerlauf schreibt als INFO/WARNUNG, Fehler werden gezählt");

        // Verknüpfung, Startdatei, Unterlagen
        File root = new File(System.getProperty("user.dir"));
        try {
            byte[] ps = java.nio.file.Files.readAllBytes(new File(root, "Verknuepfung.ps1").toPath());
            boolean ascii = true;
            for (byte b : ps) ascii &= b >= 0;
            String pst = new String(ps, java.nio.charset.StandardCharsets.US_ASCII), bat = new String(java.nio.file.Files.readAllBytes(new File(root, "Kilauea.bat").toPath()), java.nio.charset.StandardCharsets.US_ASCII);
            String opts = bat.substring(bat.indexOf("set OPTS=") + 9, bat.indexOf('\r', bat.indexOf("set OPTS=")));
            check(ascii && pst.contains("[char]0x012B") && pst.contains("icon\\kilauea.ico") && pst.contains("-Dkilauea.dauerlauf=true") && pst.contains(opts)
                            && pst.contains("'Programs'") && pst.contains("\r\n"),
                    "Verknüpfung: reines ASCII mit CRLF, ī als Zeichen 0x012B, Symbol, dieselben JVM-Schalter wie Kilauea.bat, Startmenü und Dauerlauf");
            String readme = new String(java.nio.file.Files.readAllBytes(new File(root, "README.md").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher im = java.util.regex.Pattern.compile("\\]\\((docs/bilder/[^)]+)\\)").matcher(readme);
            int bilder = 0, fehlen = 0;
            while (im.find()) { bilder++; if (!new File(root, im.group(1)).isFile()) fehlen++; }
            boolean teile = true;
            for (String h : new String[]{"## Starten", "## Bedienung", "## Qualitätsstufen", "## Dauerlauf", "## Datenbank", "## Werkzeuge", "## Grenzen"}) teile &= readme.contains(h);
            check(bilder >= 8 && fehlen == 0 && teile, "README: " + bilder + " Bilder aus dem Projekt, alle vorhanden; Starten, Bedienung, Stufen, Dauerlauf, Datenbank, Werkzeuge, Grenzen");
            String ds = new String(java.nio.file.Files.readAllBytes(new File(root, "docs/DATA_SOURCES.md").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            boolean alle = true;
            for (String h : new String[]{"Phase 12", "Phase 13", "Phase 14", "Phase 16", "Phase 17", "## Quellen"}) alle &= ds.contains(h);
            int eintraege = ds.substring(ds.indexOf("## Quellen")).split("\n- ", -1).length - 1;
            check(alle && eintraege >= 50, "Quellenliste: Abschnitte bis Phase 17, " + eintraege + " Quellen");
        } catch (java.io.IOException e) {
            check(false, "Verknüpfung, README, Quellen lesbar: " + e);
        }
    }

    private Pruefung() { }
}
