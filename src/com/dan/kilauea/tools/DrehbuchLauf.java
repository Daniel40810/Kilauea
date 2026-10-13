package com.dan.kilauea.tools;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaSim;
import com.dan.kilauea.lava.Vent;
import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.script.Player;
import com.dan.kilauea.script.Script;
import com.dan.kilauea.script.Scripts;
import com.dan.kilauea.script.Stage;
import com.dan.kilauea.terrain.Deformation;
import com.dan.kilauea.terrain.DetailTiles;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.TerrainBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lässt die Drehbücher ohne Fenster durchlaufen (Phase 12) und prüft, dass danach alles wie vorher ist:
 * <pre>DrehbuchLauf [id …]</pre> (ohne Angabe alle fünf; „frei“ für einen freien Ausbruch in der Ostriftzone).
 * Je Kapitel: Simulationszeit, Rechenzeit, Lava, Schlote, Fontäne, Einsturz, Gang, Beben, Meereseintritt.
 * Am Ende: Zustand vorher = Zustand nachher (Speicher, Feld, Gelände, Lavasee).
 */
public final class DrehbuchLauf {
    private DrehbuchLauf() { }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
        HeightGrid g = TerrainBuilder.load(dir);
        com.dan.kilauea.terrain.ParkRoads.build(g);
        DetailTiles.use(DetailTiles.load(dir, g, null));
        Ground.use(g);
        MagmaSystem magma = new MagmaSystem(2026);
        LavaSim lava = new LavaSim(() -> { synchronized (magma) { return magma.hmm.pressure(); } });
        List<Script> list = new ArrayList<>();
        if (args.length == 0) list.addAll(Scripts.all());
        for (String a : args) list.add(a.equals("frei") ? Scripts.free(com.dan.kilauea.magma.Rift.EAST, 30_000, 20, 400, 3) : Scripts.byId(a));
        boolean allOk = true;
        for (Script s : list) allOk &= run(s, lava, magma);
        System.out.println(allOk ? "ERGEBNIS: alle Drehbücher in Ordnung" : "ERGEBNIS: VERSTOSS");
        if (!allOk) System.exit(1);
    }

    /** Ein Drehbuch durchlaufen lassen; true, wenn es zu Ende kam und der Zustand danach gleich dem vorher ist. */
    public static boolean run(Script s, LavaSim lava, MagmaSystem magma) {
        lava.installGround();
        Stage stage = new Stage(lava, magma);
        Player p = new Player(stage, new Player.Host() { });
        p.outroSeconds = 0;
        String before = state(lava, magma);
        System.out.println("== " + s.title + " (" + s.chapters().size() + " Kapitel)");
        p.headless = true;
        long t0 = System.nanoTime();
        int lastChapter = -1;
        long tc = t0;
        p.start(s);
        p.update(0);   // Befehle ausführen
        double story0 = 0;
        int guard = 0;
        while (p.state() == Player.State.PLAYING && guard++ < 200_000) {
            int ci = p.chapterIndex();
            Script.Chapter c = s.chapters().get(ci);
            p.advance(Math.min(c.duration, 60 * c.speed));
            if (p.chapterIndex() != ci || p.state() != Player.State.PLAYING) {
                long now = System.nanoTime();
                System.out.printf(Locale.GERMAN, "  %d %-36s %8.1f h Sim  %6.1f s Rechnung  danach: %s%n", ci + 1, c.title, (p.storyTime() - story0) / 3600,
                        (now - tc) / 1e9, line(lava, magma));
                tc = now;
                story0 = p.storyTime();
            }
        }
        boolean finished = p.state() == Player.State.FINISHED;
        if (stage.lastCollapse > 0) System.out.printf(Locale.GERMAN, "  Abbruch des Deltas: %,.0f m³%n", stage.lastCollapse);
        p.returnToStart();
        String after = state(lava, magma);
        boolean same = before.equals(after);
        System.out.printf(Locale.GERMAN, "  zu Ende: %s · %.1f h Simulation in %.1f s · zurück: %s%n", finished ? "ja" : "NEIN",
                p.storyTime() / 3600, (System.nanoTime() - t0) / 1e9, same ? "Zustand wie vorher" : "ANDERS");
        if (!same) {
            System.out.println("    vorher:  " + before);
            System.out.println("    nachher: " + after);
        }
        return finished && same;
    }

    /** Zustand als Text (was nach einem Drehbuch wieder so sein muss). */
    public static String state(LavaSim lava, MagmaSystem magma) {
        LavaField f = lava.field();
        int vents = 0;
        double er = 0, mob = 0, rock = 0;
        if (f != null) synchronized (f) {
            LavaField.Stats st = f.stats(0, 0);
            vents = f.vents().size();
            er = st.erupted;
            mob = st.mobile;
            rock = st.rock;
        }
        String field = String.format(Locale.ROOT, "Lava: %d Schlote, gefördert %.0f, beweglich %.0f, Gestein %.0f", vents, er, mob, rock);
        double hx = Geo.x(19.4062, -155.2846), hz = Geo.z(19.4062);
        MagmaSystem.Snapshot m;
        synchronized (magma) { m = magma.snapshot(); }
        double cx = Geo.x(19.4100, -155.2830), cz = Geo.z(19.4100);
        return String.format(Locale.ROOT, "%s · Speicher %.3e/%.3e Zufluss %.1f Gänge %d · Gelände %s %.2f · Grube %.2f · Caldera %.2f · See %s Rand %.2f Absenkung %.1f · Ort %s",
                field, m.sc(), m.hmm(), m.supplyFactor(), m.dikes(), Deformation.active() ? "verformt" : "heute", Deformation.progress(),
                Ground.solidHeight(hx, hz), Ground.height(cx, cz), lava.showLake, lava.lake().rimY, lava.lake().drop, lava.focusLabel);
    }

    static String line(LavaSim lava, MagmaSystem magma) {
        LavaField f = lava.field();
        String l = "";
        if (f != null) synchronized (f) {
            LavaField.Stats st = f.stats(lava.focusX, lava.focusZ);
            double rate = 0;
            for (Vent v : f.vents()) rate += Math.max(0, v.rate);
            l = String.format(Locale.GERMAN, "Lava %,.0f m³, %.2f km², %.2f km lang, %.0f m³/s, %d Blöcke", st.erupted, st.area / 1e6, st.length / 1000, rate, f.blocksUsed());
        }
        double[] fo = lava.fountain();
        if (fo != null) l += String.format(Locale.GERMAN, ", Fontäne %.0f m", fo[3]);
        synchronized (lava.seaEntry) { if (lava.seaEntry.count > 0) l += ", " + lava.seaEntry.count + " Eintritte"; }
        if (Deformation.active()) l += String.format(Locale.GERMAN, ", Einsturz %.0f %%, Boden Caldera %.0f m", Deformation.progress() * 100,
                Ground.height(Geo.x(19.4100, -155.2830), Geo.z(19.4100)));
        synchronized (magma) {
            var d = magma.activeDike();
            if (d != null) l += String.format(Locale.GERMAN, ", Gang %.1f km", d.length() / 1000);
            double[] t = magma.tilt(MagmaSystem.UWE[0], MagmaSystem.UWE[1]);
            l += String.format(Locale.GERMAN, ", Neigung UWE %.1f µrad, Hebung %+.1f cm, Beben %d", Math.hypot(t[0], t[1]), magma.uplift(magma.hmm.x, magma.hmm.z) * 100, magma.quakeCount());
        }
        return l;
    }
}
