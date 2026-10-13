package com.dan.kilauea.life;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Rundflüge nach dem Air Tour Management Plan des Hawaiʻi Volcanoes National Park (Dezember 2023, in
 * Kraft seit Mitte 2024) [L Hawaii Tribune-Herald, 22.12.2023]:
 * <ul>
 * <li>rund 1 548 Rundflüge im Jahr statt zuvor rund 11 300; Blue Hawaiian 4 am Tag (1 176 im Jahr),
 * Safari 185, Sunshine 109, Paradise 77 im Jahr, je höchstens einer am Tag;</li>
 * <li>drei Strecken: die Puʻu-ʻŌʻō-Schleife, die Küste von Puna (in beide Richtungen) und der Highway 11
 * im Kahuku-Teil — nicht über den Gipfel;</li>
 * <li>mindestens 1 500 ft (457 m) über Grund; auf der Küstenstrecke 2 000 ft (610 m) über dem Meer,
 * nach Südwesten 3 000 ft (914 m), 2 000 ft (610 m) seitlich vor der Küste;</li>
 * <li>Schweben und Kreisen nur auf der Puʻu-ʻŌʻō-Schleife, je höchstens 1 Minute, zusammen 5 Minuten;</li>
 * <li>Montag, Dienstag, Donnerstag, Freitag, Samstag von 10 bis 14 Uhr; nicht sonntags, nicht am 9. Februar
 * und 19. Dezember (und an sechs hawaiischen Feiertagen, deren Tage jährlich festgelegt werden und hier
 * fehlen). Leise Maschinen dürften 9–17 Uhr und auch mittwochs fliegen; die Szene nimmt keine an [G].</li>
 * </ul>
 * Der Kahuku-Teil liegt weit im Südwesten außerhalb der feinen Szene und fehlt. Lage der Strecken nach
 * der Beschreibung, im Einzelnen gestaltet [G].
 */
public final class FlightPlan {
    private FlightPlan() { }

    public static final double FT = 0.3048;
    public static final double MIN_AGL = 1500 * FT, COAST_ASL = 2000 * FT, COAST_ASL_SW = 3000 * FT, COAST_OFFSHORE = 2000 * FT;
    public static final double OPEN = 10, CLOSE = 14;
    /** Rundflüge im Jahr je Unternehmen (ohne die eine Mokulele-Maschine). */
    public static final int[] PER_YEAR = {1176, 185, 109, 77};
    public static final String[] OPERATORS = {"Unternehmen A (4 am Tag)", "Unternehmen B", "Unternehmen C", "Unternehmen D"};

    /** Erlaubte Flugtage im Jahr 2026 (für die Rate der kleinen Unternehmen). */
    public static int flyingDays() {
        int n = 0;
        for (int d = 1; d <= 365; d++) if (allowedDay(d)) n++;
        return n;
    }

    /** Darf an diesem Tag geflogen werden? */
    public static boolean allowedDay(int day) {
        int wd = World.weekday(day);
        if (wd == 2 || wd == 6) return false;      // Mittwoch, Sonntag
        return day != 40 && day != 353;            // 9. Februar, 19. Dezember
    }

    /** Darf zu dieser Stunde geflogen werden? */
    public static boolean allowed(int day, double hour) { return allowedDay(day) && hour >= OPEN && hour < CLOSE; }

    /** Ein geplanter Flug: Start (Stunde), Strecke (0 Schleife, 1 Küste nach Südwesten, 2 Küste nach Nordosten), Unternehmen. */
    public record Tour(double start, int route, int operator) { }

    /** Flüge eines Tages, fest aus dem Tag gewürfelt. */
    public static List<Tour> day(int day) {
        List<Tour> l = new ArrayList<>();
        if (!allowedDay(day)) return l;
        SplittableRandom r = new SplittableRandom(7_919L * day + 2026);
        int days = flyingDays();
        for (int op = 0; op < PER_YEAR.length; op++) {
            int n = op == 0 ? 4 : (r.nextDouble() < PER_YEAR[op] / (double) days ? 1 : 0);
            for (int k = 0; k < n; k++) {
                // Unternehmen A: ein Flug je Strecke zugleich, darum auf das Fenster verteilt
                double start = op == 0 ? OPEN + k * 0.9 + r.nextDouble(0, 0.4) : r.nextDouble(OPEN, CLOSE - 0.45);
                int route = r.nextDouble() < 0.7 ? 0 : (r.nextBoolean() ? 1 : 2);
                l.add(new Tour(start, route, op));
            }
        }
        l.sort((a, b) -> Double.compare(a.start, b.start));
        return l;
    }

    // ------------------------------------------------------------------ Strecken (Breite, Länge)

    /** Puʻu-ʻŌʻō-Schleife: von Norden (Hilo) herein, um den Kegel, nach Nordosten hinaus. */
    public static Path loop() {
        double cx = com.dan.kilauea.terrain.Place.PUU_OO.x(), cz = com.dan.kilauea.terrain.Place.PUU_OO.z();
        List<double[]> pts = new ArrayList<>();
        double[][] in = {{19.540, -155.075}, {19.480, -155.085}, {19.425, -155.098}};
        for (double[] p : in) pts.add(new double[]{com.dan.kilauea.terrain.Geo.x(p[0], p[1]), com.dan.kilauea.terrain.Geo.z(p[0])});
        // ein Kreis von 1,3 km um den Kegel, im Uhrzeigersinn, von Norden beginnend
        double R = 1300;
        for (int i = 0; i <= 36; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / 36.0;
            pts.add(new double[]{cx + Math.cos(a) * R, cz + Math.sin(a) * R});
        }
        double[][] out = {{19.420, -155.060}, {19.470, -155.020}, {19.540, -154.990}};
        for (double[] p : out) pts.add(new double[]{com.dan.kilauea.terrain.Geo.x(p[0], p[1]), com.dan.kilauea.terrain.Geo.z(p[0])});
        return fromXZ("Puʻu-ʻŌʻō-Schleife", pts);
    }

    /** Küste von Puna, vor der Küste von Kalapana nach Südwesten bis vor die Hōlei Pali. */
    public static Path coast(boolean southwest) {
        double[][] ll = {{19.340, -154.940}, {19.318, -154.990}, {19.302, -155.030}, {19.287, -155.075},
                {19.272, -155.115}, {19.256, -155.160}, {19.248, -155.205}, {19.236, -155.250}};
        List<double[]> pts = new ArrayList<>();
        for (double[] p : ll) pts.add(new double[]{com.dan.kilauea.terrain.Geo.x(p[0], p[1]), com.dan.kilauea.terrain.Geo.z(p[0])});
        if (!southwest) java.util.Collections.reverse(pts);
        return fromXZ(southwest ? "Küste von Puna, nach Südwesten" : "Küste von Puna, nach Nordosten", pts);
    }

    static Path fromXZ(String name, List<double[]> pts) {
        double[] x = new double[pts.size()], z = new double[pts.size()], y = new double[pts.size()];
        for (int i = 0; i < x.length; i++) { x[i] = pts.get(i)[0]; z[i] = pts.get(i)[1]; y[i] = 0; }
        return new Path(name, x, z, y, false);
    }

    /** Flughöhe über dem Meer an Bogenlänge s: über Grund mit Vorausschau (Schleife) oder fest (Küste). */
    public static double altitude(Path p, int route, double s) {
        double ground = 0;
        double[] o = new double[5];
        for (double d = -300; d <= 900; d += 100) {
            p.at(s + d, 0, o);
            ground = Math.max(ground, com.dan.kilauea.terrain.Ground.height(o[0], o[2]));
        }
        double agl = ground + MIN_AGL + 70;   // etwas über der Untergrenze [G]
        if (route == 0) return agl;
        return Math.max(route == 1 ? COAST_ASL_SW : COAST_ASL, agl);
    }
}
