package com.dan.kilauea.life;

import com.dan.kilauea.terrain.Ground;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Hubschrauber nach dem Flugplan ({@link FlightPlan}), dazu bei einem Ausbruch im Drehbuch ein
 * Beobachtungsflug, der den Schlot mit Abstand umkreist (Flüge von Behörden sind keine Rundflüge im
 * Sinn des Plans [L]; Bahn, Abstand und Höhe [G]). Auf Knopfdruck fliegt ein Rundflug sofort los
 * (Vorführung, außerhalb des Plans).
 * <p>Flug: rund 46 m/s auf der Strecke, 30 m/s in der Schleife um den Kegel, dort einmal höchstens
 * 1 Minute Schweben [L Regel, Tempo G]. Hauptrotor rund 390 Umdrehungen je Minute mit drei Blättern
 * (Blattfolge rund 20 Hz) [L Größenordnung der Bauart], Querneigung aus Tempo und Kurve, Nase im
 * Vorwärtsflug leicht unten [A].</p>
 */
public final class Helicopters {
    public static final double CRUISE = 46, LOOP = 30, HOVER = 50, ROTOR_RPM = 390, TAIL_RATIO = 5.35;
    static final float[][] PAINT = {Life.rgb(0.04, 0.10, 0.30), Life.rgb(0.55, 0.55, 0.53), Life.rgb(0.35, 0.02, 0.02), Life.rgb(0.03, 0.03, 0.035)};
    static final float[] HVO_PAINT = Life.rgb(0.62, 0.62, 0.60);

    public static final class Heli {
        public Path path;
        public int route, operator;
        public boolean hvo, demo;
        public double s, v, x, y, z, heading, vx, vy, vz, roll, pitch, rotor, hoverLeft, orbitAngle;
        boolean hovered;
        double lastHeading;
        /** Zeitfenster des Flugs (Tag, Stunde von–bis); springt die Uhr heraus, landet er sofort. */
        int day = -1, tour = -1;
        double from, to;
    }

    private final Life life;
    final List<Heli> flying = new ArrayList<>();
    private int planDay = -1;
    private List<FlightPlan.Tour> plan = List.of();
    private final boolean[] started = new boolean[16];
    private double lastHour;
    private final Path loop = FlightPlan.loop(), coastSW = FlightPlan.coast(true), coastNE = FlightPlan.coast(false);
    private final java.util.SplittableRandom rnd;

    Helicopters(Life life, long seed) {
        this.life = life;
        rnd = new java.util.SplittableRandom(seed);
    }

    Path path(int route) { return route == 0 ? loop : route == 1 ? coastSW : coastNE; }

    /** Dauer eines Flugs auf der Strecke (h). */
    double duration(int route) {
        Path p = path(route);
        double t = p.length / CRUISE;
        if (route == 0) t += 2 * Math.PI * 1300 / LOOP - 2 * Math.PI * 1300 / CRUISE + 60;
        return t / 3600;
    }

    void update(double dt, World w) {
        if (w.day != planDay) {
            planDay = w.day;
            plan = FlightPlan.day(w.day);
            java.util.Arrays.fill(started, false);
        }
        // Uhr gesprungen: Flüge außerhalb ihres Fensters beenden; zurückgestellt, beginnen spätere Flüge neu
        for (Iterator<Heli> it = flying.iterator(); it.hasNext(); ) {
            Heli h = it.next();
            if (h.hvo || h.day < 0) continue;
            if (h.day != w.day || w.hour < h.from - 0.02 || w.hour > h.to + 0.05) it.remove();
        }
        if (w.hour < lastHour - 1e-6)
            for (int i = 0; i < plan.size() && i < started.length; i++)
                if (started[i] && w.hour < plan.get(i).start() + duration(plan.get(i).route()) && !flies(i)) started[i] = false;
        lastHour = w.hour;
        // geplante Flüge starten, sobald ihre Zeit da ist (bei stehender Uhr auch mitten im Flug)
        for (int i = 0; i < plan.size() && i < started.length; i++) {
            FlightPlan.Tour t = plan.get(i);
            if (started[i]) continue;
            double dur = duration(t.route());
            if (w.hour >= t.start() && w.hour < t.start() + dur) {
                started[i] = true;
                Heli h = new Heli();
                h.path = path(t.route());
                h.route = t.route();
                h.operator = t.operator();
                h.s = Math.min(h.path.length * 0.95, (w.hour - t.start()) * 3600 * CRUISE);
                h.hovered = h.s > 0;
                h.day = w.day; h.tour = i; h.from = t.start(); h.to = t.start() + dur;
                place(h);
                flying.add(h);
            } else if (w.hour >= t.start() + dur) started[i] = true;
        }
        // Beobachtungsflug bei Ausbruch im Drehbuch
        Heli hvo = null;
        for (Heli h : flying) if (h.hvo) hvo = h;
        if (w.script && !Double.isNaN(w.ventX) && w.daylight()) {
            if (hvo == null) {
                hvo = new Heli();
                hvo.hvo = true;
                hvo.orbitAngle = rnd.nextDouble() * 2 * Math.PI;
                hvo.x = w.ventX + Math.cos(hvo.orbitAngle) * 900;
                hvo.z = w.ventZ + Math.sin(hvo.orbitAngle) * 900;
                hvo.y = w.ventY + 450;
                flying.add(hvo);
            }
        } else if (hvo != null) flying.remove(hvo);

        for (Iterator<Heli> it = flying.iterator(); it.hasNext(); ) {
            Heli h = it.next();
            if (h.hvo) orbit(h, dt, w);
            else if (!fly(h, dt)) { it.remove(); continue; }
            h.rotor += 2 * Math.PI * ROTOR_RPM / 60 * dt;
            float[] paint = h.hvo ? HVO_PAINT : PAINT[h.operator % PAINT.length];
            boolean beacon = (life.clock % 1.2) < 0.12;
            life.put(Life.K_HELI, h.x, h.y, h.z, Nene.yaw(h.heading), 1, h.pitch, h.roll, h.rotor, paint, 0, 0, 0, beacon ? 1 : 0);
            life.source(Life.E_HELI, h.x, h.y, h.z, h.vx, h.vy, h.vz, 1);
        }
    }

    private boolean flies(int tour) {
        for (Heli h : flying) if (h.tour == tour) return true;
        return false;
    }

    private void orbit(Heli h, double dt, World w) {
        double R = 900, v = 25;
        h.orbitAngle += v / R * dt;
        double tx = w.ventX + Math.cos(h.orbitAngle) * R, tz = w.ventZ + Math.sin(h.orbitAngle) * R;
        double ty = Math.max(w.ventY + 450, Ground.height(tx, tz) + FlightPlan.MIN_AGL * 0.6);
        double nx = h.x + (tx - h.x) * Math.min(1, dt * 0.5), nz = h.z + (tz - h.z) * Math.min(1, dt * 0.5);
        double ny = h.y + Math.max(-4 * dt, Math.min(4 * dt, ty - h.y));
        move(h, nx, ny, nz, dt);
    }

    /** Einen Planflug weiterführen; false am Ende der Strecke. */
    private boolean fly(Heli h, double dt) {
        Path p = h.path;
        double[] o = new double[5];
        p.at(h.s, 0, o);
        double circleFrom = h.route == 0 ? p.s[3] : -1, circleTo = h.route == 0 ? p.s[3 + 36] : -1;
        boolean inLoop = h.s > circleFrom && h.s < circleTo;
        // einmal schweben, kurz nach Beginn der Schleife (Blick auf den Kegel)
        if (h.route == 0 && !h.hovered && h.s > circleFrom + 400) { h.hovered = true; h.hoverLeft = HOVER; }
        double target = h.hoverLeft > 0 ? 0 : inLoop ? LOOP : CRUISE;
        h.v += Math.max(-3 * dt, Math.min(2.5 * dt, target - h.v));
        if (h.hoverLeft > 0) h.hoverLeft -= dt;
        h.s += h.v * dt;
        if (h.s >= p.length) return false;
        p.at(h.s, 0, o);
        double ty = FlightPlan.altitude(p, h.route, h.s);
        double ny = h.y == 0 ? ty : h.y + Math.max(-6 * dt, Math.min(6 * dt, ty - h.y));
        move(h, o[0], ny, o[2], dt);
        if (h.v < 3) {   // beim Schweben zum Kegel drehen
            double cx = com.dan.kilauea.terrain.Place.PUU_OO.x(), cz = com.dan.kilauea.terrain.Place.PUU_OO.z();
            double want = Math.atan2(cx - h.x, -(cz - h.z));
            h.heading += Nene.angle(want - h.heading) * Math.min(1, dt * 0.4);
        }
        return true;
    }

    private void move(Heli h, double nx, double ny, double nz, double dt) {
        if (dt <= 0) { h.x = nx; h.y = ny; h.z = nz; return; }
        double vx = (nx - h.x) / dt, vy = (ny - h.y) / dt, vz = (nz - h.z) / dt;
        h.vx += (vx - h.vx) * Math.min(1, dt * 3);
        h.vy += (vy - h.vy) * Math.min(1, dt * 3);
        h.vz += (vz - h.vz) * Math.min(1, dt * 3);
        h.x = nx; h.y = ny; h.z = nz;
        double sp = Math.hypot(h.vx, h.vz);
        if (sp > 3) {
            double want = Math.atan2(h.vx, -h.vz);
            double turn = Nene.angle(want - h.heading);
            h.heading += turn * Math.min(1, dt * 1.5);
            double rate = Nene.angle(h.heading - h.lastHeading) / Math.max(dt, 1e-3);
            double bank = Math.atan(sp * rate / 9.81);
            h.roll += (Math.max(-0.5, Math.min(0.5, bank)) - h.roll) * Math.min(1, dt * 2);
        } else h.roll += (0 - h.roll) * Math.min(1, dt * 2);
        h.lastHeading = h.heading;
        // Nase im Vorwärtsflug unten (rund 0,1° je m/s) [A]
        h.pitch += (-Math.toRadians(0.11 * sp) - h.pitch) * Math.min(1, dt * 1.5);
    }

    /** Vorführung: ein Rundflug auf der Schleife, ab dem Punkt der Strecke, der der Kamera am nächsten ist. */
    void demo(World w) {
        Heli h = new Heli();
        h.path = loop;
        h.route = 0;
        h.operator = rnd.nextInt(4);
        h.demo = true;
        double[] o = new double[1];
        loop.distance(w.camX, w.camZ, o);
        h.s = Math.max(0, Math.min(loop.length * 0.6, o[0] - 1500));
        h.hovered = h.s > loop.s[3] + 400;
        h.v = CRUISE;
        h.day = w.day; h.from = w.hour; h.to = w.hour + duration(0);
        place(h);
        flying.add(h);
    }

    private void place(Heli h) {
        double[] o = new double[5];
        h.path.at(h.s, 0, o);
        h.x = o[0]; h.z = o[2];
        h.y = FlightPlan.altitude(h.path, h.route, h.s);
        h.heading = Math.atan2(o[3], -o[4]);
        h.lastHeading = h.heading;
        h.v = CRUISE;
    }

    /** Was gerade fliegt und was der Plan heute vorsieht. */
    String status(World w) {
        int wd = World.weekday(w.day);
        long inAir = flying.stream().filter(h -> !h.hvo).count();
        boolean hvo = flying.stream().anyMatch(h -> h.hvo);
        String day = World.WEEKDAYS[wd];
        String s;
        if (!FlightPlan.allowedDay(w.day)) s = day + ": keine Rundflüge erlaubt";
        else {
            List<FlightPlan.Tour> l = FlightPlan.day(w.day);
            s = String.format(Locale.GERMAN, "%s: %d Rundflüge geplant (10–14 Uhr)", day, l.size());
        }
        s += inAir > 0 ? String.format(Locale.GERMAN, ", %d in der Luft", inAir) : ", gerade keiner in der Luft";
        if (hvo) s += ", Beobachtungsflug am Schlot";
        return s;
    }

    List<Heli> flying() { return new ArrayList<>(flying); }
}
