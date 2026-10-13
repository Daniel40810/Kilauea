package com.dan.kilauea.tools;

import com.dan.kilauea.life.FlightPlan;
import com.dan.kilauea.life.Life;
import com.dan.kilauea.life.World;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.Place;

import java.util.Locale;

/**
 * Prüfhilfe Phase 15: das Leben ohne Fenster laufen lassen und zählen. Je Lage (Tag, Uhrzeit, Kamera)
 * eine Zeile: Figuren je Gruppe, Autos (Tempo, Abstand zur Spur, Halte für Nēnē), Wanderer (Abstand zum
 * Weg), Hubschrauber (Höhe über Grund, Abstand zur Küste).
 * <p>Aufruf: {@code LebenLauf [sekunden]}</p>
 */
public final class LebenLauf {
    public static void main(String[] args) throws Exception {
        double secs = args.length > 0 ? Double.parseDouble(args[0]) : 180;
        Renderer.vorab();
        while (Renderer.vorabFortschritt() < 1) Thread.sleep(200);
        System.out.println(Renderer.vorabText());
        run("Do 8. Okt., 11:00, Uēkahuna", 281, 11.0, Place.UEKAHUNA, 120, secs, 0);
        run("Do 8. Okt., 11:15, Puʻu ʻŌʻō aus 3 km", 281, 11.25, Place.PUU_OO, 3000, secs, 0);
        run("Di 6. Okt., 10:10, Küste (nach Südwesten)", 279, 10.17, Place.KAMOKUNA, 2000, secs, 0);
        run("Mi 7. Okt., 11:00, Puʻu ʻŌʻō", 280, 11.0, Place.PUU_OO, 3000, secs, 0);
        run("Do 8. Okt., 6:45, Nāhuku (Morgen)", 281, 6.75, Place.NAHUKU, 40, secs, 0);
        run("Do 8. Okt., 22:00, Kīlauea Iki (Nacht)", 281, 22.0, Place.KILAUEA_IKI, 300, secs, 0);
        run("Do 8. Okt., 11:00, Kīlauea Iki gesperrt", 281, 11.0, Place.KILAUEA_IKI, 300, secs, 1);
        run("So 11. Okt., 11:00, Hōlei Pali", 284, 11.0, Place.HOLEI_PALI, 500, secs, 0);
        run("Sa 4. Juli, 11:00, Besucherzentrum (Trupps)", 185, 11.0, Place.UEKAHUNA, 200, secs, 0);
    }

    static void run(String label, int day, double hour, Place at, double above, double secs, int hazard) {
        Life life = new Life(2026);
        World w = new World();
        w.day = day;
        w.hour = hour;
        com.dan.kilauea.atmos.SunClock sc = new com.dan.kilauea.atmos.SunClock();
        sc.set(day, hour);
        w.sunElevation = sc.elevationDeg;
        w.camX = at.x();
        w.camZ = at.z();
        w.camY = Ground.height(w.camX, w.camZ) + above;
        if (hazard == 1) w.hazard(Place.KILAUEA_IKI.x(), Place.KILAUEA_IKI.z(), 1500);
        double dt = 1 / 30.0;
        int[] max = new int[Life.Group.values().length];
        double maxLane = 0, maxSpeed = 0, maxTrail = 0, minAgl = 1e9, minCoast = 1e9, minGap = 1e9;
        int hikersInHazard = 0;
        long t0 = System.nanoTime();
        com.dan.kilauea.life.Path[] trails = null;
        for (double t = 0; t < secs; t += dt) {
            life.update(dt, w);
            for (Life.Group g : Life.Group.values()) max[g.ordinal()] = Math.max(max[g.ordinal()], life.shown(g));
            for (double[] c : life.cars.state()) {
                if (c[7] == 0 && c[2] > 6) maxLane = Math.max(maxLane, Math.abs(Math.abs(c[3]) - com.dan.kilauea.life.Cars.LANE));
                maxSpeed = Math.max(maxSpeed, c[2]);
            }
            var cs = life.cars.state();
            for (int i = 0; i < cs.size(); i++)
                for (int j = i + 1; j < cs.size(); j++) {
                    double[] a = cs.get(i), b = cs.get(j);
                    if (a[7] == 1 || b[7] == 1) continue;
                    if (a[4] == b[4] && a[6] == b[6]) minGap = Math.min(minGap, Math.hypot(a[0] - b[0], a[1] - b[1]));
                }
            for (double[] p : life.hikers.positions()) if (w.closed(p[0], p[2], 0)) hikersInHazard++;
            for (var h : life.helicopters()) {
                if (h.hvo) continue;
                minAgl = Math.min(minAgl, h.y - Ground.height(h.x, h.z));
                if (h.route > 0) minCoast = Math.min(minCoast, coast(h.x, h.z));
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6 / (secs / dt);
        System.out.printf(Locale.GERMAN, "%-46s Nēnē %2d (gesamt %2d) · Vögel %2d · Hubschr. %d · Wanderer %2d (gesperrt %d) · Autos %2d (gesamt %2d, ≤ %.1f m/s, Spur ±%.2f m, Abstand ≥ %.1f m, Halte für Nēnē %d)%n",
                label, max[0], life.nene.size(), max[1], max[2], max[3], hikersInHazard, max[4], life.cars.size(), maxSpeed, maxLane, minGap, life.cars.neneStops);
        System.out.printf(Locale.GERMAN, "      Hubschrauber: geringste Höhe über Grund %.0f m (Regel ≥ %.0f), Küstenstrecke ≥ %.0f m vor dem Land (Regel ≥ %.0f); Rechenzeit %.3f ms je Bild%n",
                minAgl == 1e9 ? Double.NaN : minAgl, FlightPlan.MIN_AGL, minCoast == 1e9 ? Double.NaN : minCoast, FlightPlan.COAST_OFFSHORE, ms);
    }

    /** Abstand zum nächsten Land (m), auf 50 m genau. */
    public static double coast(double x, double z) {
        for (double r = 0; r <= 3000; r += 50)
            for (int k = 0; k < 32; k++) {
                double a = 2 * Math.PI * k / 32;
                if (Ground.height(x + Math.cos(a) * r, z + Math.sin(a) * r) > 0) return r;
            }
        return 3000;
    }
}
