package com.dan.kilauea.tools;

import com.dan.kilauea.lava.LavaSim;
import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.monitor.Monitor;
import com.dan.kilauea.monitor.MonitorPainter;
import com.dan.kilauea.monitor.Reading;
import com.dan.kilauea.script.Player;
import com.dan.kilauea.script.Script;
import com.dan.kilauea.script.Scripts;
import com.dan.kilauea.script.Stage;
import com.dan.kilauea.terrain.DetailTiles;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.TerrainBuilder;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Das Messnetz während der Drehbücher, ohne Fenster (Phase 14):
 * <pre>MessLauf [Ausgabeordner] [id …]</pre>
 * Je Kapitel: Änderung der Neigung an UWD und SDH, des Abstands UWEV–CRIM, höchster SO₂-Ausstoß, Zahl der Beben,
 * größtes Beben, höchster Tremor. Dazu je Kapitel die Tafel des Messnetzes als PNG (wie im Bild) und eine CSV-Datei
 * mit allen Proben. Die Geschichte läuft in Schritten von einer „Sekunde Wanduhr“ im Tempo des Kapitels, so wie in
 * der App; das Messnetz wählt sein Zeitfenster danach.
 */
public final class MessLauf {
    private MessLauf() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(System.getProperty("kilauea.dir", System.getProperty("user.dir")));
        File out = new File(args.length > 0 ? args[0] : "messlauf");
        out.mkdirs();
        HeightGrid g = TerrainBuilder.load(dir);
        com.dan.kilauea.terrain.ParkRoads.build(g);
        DetailTiles.use(DetailTiles.load(dir, g, null));
        Ground.use(g);
        MagmaSystem magma = new MagmaSystem(2026);
        LavaSim lava = new LavaSim(() -> { synchronized (magma) { return magma.hmm.pressure(); } });
        List<Script> list = new ArrayList<>();
        for (int i = 1; i < args.length; i++) list.add(args[i].equals("frei") ? Scripts.free(com.dan.kilauea.magma.Rift.EAST, 30_000, 20, 400, 3) : Scripts.byId(args[i]));
        if (list.isEmpty()) list.addAll(Scripts.all());
        for (Script s : list) run(s, lava, magma, out);
    }

    /** Ergebnis je Kapitel. */
    public record Row(String chapter, double dUwd, double dSdh, double dBase, double so2Max, double so2End, int quakes, double maxM, double tremorMax, double hours) { }

    public static List<Row> run(Script s, LavaSim lava, MagmaSystem magma, File out) throws Exception {
        lava.installGround();
        Stage stage = new Stage(lava, magma);
        Player p = new Player(stage, new Player.Host() { });
        p.outroSeconds = 0;
        p.headless = true;
        Monitor mon = new Monitor(magma, lava);
        System.out.println("== " + s.title);
        p.start(s);
        p.update(0);
        mon.clear();
        List<Row> rows = new ArrayList<>();
        PrintWriter csv = out == null ? null : new PrintWriter(new File(out, "mess_" + s.id + ".csv"), "UTF-8");
        if (csv != null) csv.println("Kapitel;Stunde;Neigung UWD µrad;Neigung SDH µrad;UWEV-CRIM cm;Hebung cm;SO2 t/d;Förderung m3/s;Beben gesamt");
        int ci = -1;
        double uw0 = 0, sd0 = 0, b0 = 0, so2Max = 0, trMax = 0, t0 = 0;
        int q0 = 0;
        double maxM = Double.NaN;
        int guard = 0;
        while (p.state() == Player.State.PLAYING && guard++ < 500_000) {
            int idx = p.chapterIndex();
            Script.Chapter c = s.chapters().get(idx);
            if (idx != ci) {
                ci = idx;
                uw0 = mon.latest(Monitor.CH_UWD); sd0 = mon.latest(Monitor.CH_SDH); b0 = mon.latest(Monitor.CH_BASE);
                so2Max = 0; trMax = 0; maxM = Double.NaN;
                synchronized (magma) { q0 = magma.quakeCount(); t0 = magma.time(); }
            }
            mon.passiveSO2 = stage.passiveSO2;
            mon.rateOverride = c.speed;
            // eine „Sekunde Wanduhr“ in Stücken, damit das Messnetz fein genug mitschreibt
            double step = Math.min(c.speed, Math.max(1, c.duration - p.chapterTime()));
            for (int k = 0; k < 4 && p.state() == Player.State.PLAYING && p.chapterIndex() == idx; k++) {
                p.advance(step / 4);
                mon.sample(0.25);
                if (p.chapterIndex() != idx) break;   // die Stichworte des nächsten Kapitels zählen nicht mehr hierher
                so2Max = Math.max(so2Max, mon.latest(Monitor.CH_SO2));
                for (int i = 0; i < com.dan.kilauea.monitor.Station.SEISMIC.size(); i++) trMax = Math.max(trMax, mon.latest(Monitor.CH_SEIS + i));
            }
            double hour;
            int qc;
            synchronized (magma) { hour = (magma.time() - mon.start()) / 3600; qc = magma.quakeCount(); }
            if (csv != null) csv.printf(Locale.ROOT, "%d;%.3f;%.3f;%.3f;%.3f;%.3f;%.0f;%.1f;%d%n", idx + 1, hour, mon.latest(Monitor.CH_UWD), mon.latest(Monitor.CH_SDH),
                    mon.latest(Monitor.CH_BASE), mon.latest(Monitor.CH_UPLIFT), mon.latest(Monitor.CH_SO2), mon.latest(Monitor.CH_RATE), qc);
            if (p.chapterIndex() != idx || p.state() != Player.State.PLAYING) {
                int nq;
                double tEnd;
                synchronized (magma) { nq = magma.quakeCount() - q0; tEnd = magma.time(); for (var q : magma.quakesSince(t0)) if (Double.isNaN(maxM) || q.mag() > maxM) maxM = q.mag(); }
                Row r = new Row(c.title, mon.latest(Monitor.CH_UWD) - uw0, mon.latest(Monitor.CH_SDH) - sd0, mon.latest(Monitor.CH_BASE) - b0, so2Max,
                        mon.latest(Monitor.CH_SO2), nq, maxM, trMax, (tEnd - t0) / 3600);
                rows.add(r);
                System.out.printf(Locale.GERMAN, "  %d %-34s %7.1f h  UWD %+6.1f µrad  SDH %+6.1f µrad  GPS %+5.1f cm  SO₂ max %,8.0f t/d  Beben %4d  größtes M %s  Tremor %.2f mm%n",
                        idx + 1, c.title, r.hours(), r.dUwd(), r.dSdh(), r.dBase(), r.so2Max(), r.quakes(), Double.isNaN(r.maxM()) ? "–" : String.format(Locale.GERMAN, "%.1f", r.maxM()), r.tremorMax());
                if (out != null) {
                    // die Tafel so, wie sie am Ende des Kapitels im Bild stünde (Blick auf den Schauplatz)
                    double[] cv = c.view();
                    double tx = cv != null ? com.dan.kilauea.terrain.Geo.x(cv[0], cv[1]) : lava.focusX;
                    double tz = cv != null ? com.dan.kilauea.terrain.Geo.z(cv[0]) : lava.focusZ;
                    Monitor.View v = mon.view(tx, tz, 360);
                    for (Reading rd : v.all()) if (rd.prov() == null || rd.source().isBlank()) throw new IllegalStateException("Wert ohne Herkunft: " + rd.label());
                    ImageIO.write(MonitorPainter.paint(v, 440, 900), "png", new File(out, String.format("mess_%s_%d.png", s.id, idx + 1)));
                }
            }
        }
        if (csv != null) csv.close();
        p.returnToStart();
        return rows;
    }
}
