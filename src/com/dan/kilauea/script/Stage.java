package com.dan.kilauea.script;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.lava.LavaSim;
import com.dan.kilauea.lava.Vent;
import com.dan.kilauea.lava.VentSite;
import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.magma.Rift;
import com.dan.kilauea.terrain.Deformation;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die Bühne der Drehbücher (Phase 12): alles, was ein Drehbuch am Modell tun darf, ohne Grafik. Lava-Modell
 * und Magmasystem kommen von außen; was nur das Bild betrifft (Kamera, Uhrzeit, Asche, Schnitt), geht an
 * {@link Effects}, das ohne Fenster nichts tut. So laufen dieselben Drehbücher in der App, im Werkzeug
 * {@code DrehbuchLauf} und in der Prüfung.
 */
public final class Stage {
    /** Was nur das Bild betrifft. Ohne Fenster: nichts. */
    public interface Effects {
        /** Kamera auf (x, y, z) richten: Blick (°, 0 = Norden), Neigung (°), Abstand (m). */
        default void view(double x, double y, double z, double yawDeg, double pitchDeg, double dist) { }
        /** Uhrzeit (Stunden HST; negativ: Stunden nach Sonnenuntergang) setzen, dazu den Tag im Jahr (1–365; 0 = Tag bleibt). */
        default void time(int dayOfYear, double h) { }
        /** Schnitt durch den Berg: "OFF", "EAST", "SOUTHWEST", "NORTH_SOUTH". */
        default void section(String mode) { }
        /** Kamera vor den Schnitt (sobald er liegt). */
        default void lookAtSection() { }
        /** Asche und Staub (Anzahl der Schwaden). */
        default void ash(double x, double z, double radius, int puffs) { }
        /** Teilchen (Fontäne, Glutbrocken, Kissen) leeren. */
        default void clearParticles() { }
    }

    public final LavaSim lava;
    public final MagmaSystem magma;
    public Effects fx = new Effects() { };
    private final Map<String, Vent> vents = new LinkedHashMap<>();
    private final Map<Vent, Double> eruptedSeen = new java.util.HashMap<>();
    /** Ausgetretene Lava ist blasig: rund drei Viertel davon ist dichtes Magma, das dem Speicher fehlt [A]. */
    static final double DENSE = 0.75;

    public Stage(LavaSim lava, MagmaSystem magma) {
        this.lava = lava;
        this.magma = magma;
    }

    // ------------------------------------------------------------------ Lava

    /** Neuen Lauf beginnen (leeres Feld) mit Ort für Karte und Zustand. */
    public void begin(String label, double lat, double lon) {
        vents.clear();
        eruptedSeen.clear();
        lava.begin(label, Geo.x(lat, lon), Geo.z(lat), label);
    }

    /** Spalte oder Schlot öffnen: von (lat0, lon0) nach (lat1, lon1), Rate (m³/s), Fontäne (m, 0 = keine). */
    public Vent open(String id, String name, double lat0, double lon0, double lat1, double lon1, double rate, double fountain) {
        return openXZ(id, name, Geo.x(lat0, lon0), Geo.z(lat0), Geo.x(lat1, lon1), Geo.z(lat1), rate, fountain);
    }

    /** Wie {@link #open}, an einem der benannten Orte. */
    public Vent open(String id, VentSite s, double rate, double fountain) {
        Vent v = openXZ(id, s.label, s.x0(), s.z0(), s.x1(), s.z1(), rate, fountain);
        v.site = s;
        return v;
    }

    public Vent openXZ(String id, String name, double x0, double z0, double x1, double z1, double rate, double fountain) {
        Vent v = vents.get(id);
        if (v != null) {
            v.rate = rate;
            v.fountain = fountain;
            return v;
        }
        if (lava.field() == null || lava.focusLabel == null) lava.begin(name, 0.5 * (x0 + x1), 0.5 * (z0 + z1), name);
        v = new Vent(name, x0, z0, x1, z1, rate);
        v.fountain = fountain;
        lava.open(v);
        vents.put(id, v);
        eruptedSeen.put(v, 0.0);
        return v;
    }

    public void rate(String id, double r) { Vent v = vents.get(id); if (v != null) v.rate = r; }
    public void fountain(String id, double h) { Vent v = vents.get(id); if (v != null) v.fountain = h; }
    public void close(String id) { rate(id, 0); }
    public void closeAll() { for (Vent v : vents.values()) v.rate = 0; }
    public Vent vent(String id) { return vents.get(id); }
    public Map<String, Vent> vents() { return vents; }

    /** Zellen mit beweglicher Lava insgesamt (m³). */
    public double mobile() {
        LavaField f = lava.field();
        if (f == null) return 0;
        synchronized (f) { return f.stats(lava.focusX, lava.focusZ).mobile; }
    }

    /** Ein Stück des Deltas bricht ab (am stärksten Eintritt), wie am 31. Dezember 2016 bei Kamokuna [L]. */
    public double deltaCollapse(double radius) {
        LavaField f = lava.field();
        if (f == null) return 0;
        double[] e;
        synchronized (lava.seaEntry) {
            if (lava.seaEntry.count == 0) return 0;
            e = new double[]{lava.seaEntry.entries[0], lava.seaEntry.entries[1]};
        }
        double v;
        synchronized (f) { v = f.collapse(e[0], e[1], radius, -0.5); }
        lastCollapse = v;
        fx.ash(e[0], e[1], radius * 0.6, 40);
        return v;
    }

    /** Volumen des letzten Abbruchs (m³). */
    public double lastCollapse;

    // ------------------------------------------------------------------ Lavasee

    public void lake(boolean show) {
        lava.installGround();
        lava.showLake = show;
        lava.refreshRim();
    }

    /** Spiegel des Sees zusätzlich absenken (m); 0 = normal. */
    public void lakeDrop(double m) { lava.lake().drop = m; }

    // ------------------------------------------------------------------ Gelände

    /** Einsturz des Gipfels: Schicht an mit Fortschritt F (0 = vor 2018, 1 = heute). */
    public void collapse(double f) {
        lava.installGround();
        Deformation.set(true, f);
        lava.refreshRim();
    }

    public void collapseOff() {
        Deformation.set(false, 1);
        lava.refreshRim();
    }

    /** Ein Einsturzbeben am Gipfel mit Staubwolke, und der Boden sackt auf F. */
    public void collapseEvent(double f, double mag) {
        collapse(f);
        double x = Geo.x(19.4060, -155.2830), z = Geo.z(19.4060);
        synchronized (magma) {
            magma.event(x, 500, z, mag);
            if (collapseStep > 0) magma.inject(collapseStep, true);
        }
        fx.ash(x, z, 700, 45);
    }

    // ------------------------------------------------------------------ Magma

    public void intrude(Rift r, double s, double speed) { synchronized (magma) { magma.intrude(r, s, speed); } }
    public void intrude(Rift r, double s, double speed, double width) { synchronized (magma) { magma.intrude(r, s, speed, width); } }

    /**
     * Phase 14: Bei jedem Einsturzbeben drückt der sinkende Block auf den Speicher, die Neigungsmesser springen kurz
     * zurück (m³, die dem flachen Speicher dabei gutgeschrieben werden; 0 = aus). 2018 zeigten die Neigungsmesser bei
     * jedem Einsturz einen plötzlichen Richtungswechsel [L]; das Maß ist gestaltet [G].
     */
    public volatile double collapseStep;
    public void endDike() { synchronized (magma) { magma.endDike(); } }
    public boolean dikeArrived() {
        synchronized (magma) { return magma.activeDike() == null || magma.activeDike().arrived(); }
    }
    /**
     * Zufluss in den Halemaʻumaʻu-Speicher (m³/s; negativ: Abfluss), solange gesetzt. So heben und senken die
     * Drehbücher den Gipfel in der Größenordnung, die die Neigungsmesser zeigen, auch wenn die Lava des Modells
     * weniger Volumen hat als die echte Episode [G].
     */
    public volatile double summitFeed;

    /**
     * Phase 14: ruhige Entgasung des Gipfels (SO₂, t/d), wenn ein Drehbuch sie nach einer Messung vorgibt; NaN = nach
     * dem Lavasee (5 000 t/d) oder Ruhe (30 t/d), siehe {@code monitor.Gas}.
     */
    public volatile double passiveSO2 = Double.NaN;

    public void supply(double factor) { synchronized (magma) { magma.supplyFactor = factor; } }
    public void inject(double m3, boolean summit) { synchronized (magma) { magma.inject(m3, summit); } }

    /**
     * Was die Schlote seit dem letzten Aufruf gefördert haben, fehlt dem Speicher (Gipfel: Halemaʻumaʻu, sonst
     * Südcaldera). So sinkt der Gipfel während einer Fontänen-Episode und kippt, wie es die Neigungsmesser zeigen [A].
     */
    void couple(double dt) {
        double feed = summitFeed;
        if (feed != 0) synchronized (magma) {
            if (feed > 0) magma.inject(feed * dt, true);
            else magma.withdraw(-feed * dt, true);
        }
        double hx = Geo.x(19.4060, -155.2830), hz = Geo.z(19.4060);
        for (Vent v : vents.values()) {
            double now = v.erupted(), was = eruptedSeen.getOrDefault(v, 0.0);
            double d = now - was;
            eruptedSeen.put(v, now);
            if (Math.abs(d) < 1e-6) continue;
            boolean summit = Math.hypot(0.5 * (v.x0 + v.x1) - hx, 0.5 * (v.z0 + v.z1) - hz) < 4000;
            synchronized (magma) {
                if (d > 0) magma.withdraw(d * DENSE, summit);
                else magma.inject(-d * DENSE, summit);   // Rückfluss
            }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Punkt (x, z) auf einer Riftzone in s Metern vom Gipfel und die Richtung dort (Einheitsvektor). */
    public static double[] riftPoint(Rift r, double s) {
        double[] p = r.at(s), a = r.at(s - 60), b = r.at(s + 60);
        double dx = b[0] - a[0], dz = b[1] - a[1], l = Math.max(1e-9, Math.hypot(dx, dz));
        return new double[]{p[0], p[1], dx / l, dz / l};
    }

    /** Nächster Punkt auf den Riftzonen zu (x, z): Riftzone, Bogenlänge s (m), Abstand (m). */
    public static Object[] nearestRift(double x, double z) {
        Rift best = Rift.EAST;
        double bs = 0, bd = Double.MAX_VALUE;
        for (Rift r : Rift.values()) {
            for (double s = 0; s <= r.length(); s += 50) {
                double[] p = r.at(s);
                double d = Math.hypot(p[0] - x, p[1] - z);
                if (d < bd) { bd = d; bs = s; best = r; }
            }
        }
        return new Object[]{best, bs, bd};
    }

    /** Boden an (lat, lon). */
    public static double ground(double lat, double lon) { return Ground.height(Geo.x(lat, lon), Geo.z(lat)); }
}
