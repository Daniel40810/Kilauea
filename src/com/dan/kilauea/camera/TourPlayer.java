package com.dan.kilauea.camera;

import com.dan.kilauea.script.Player;
import com.dan.kilauea.terrain.Ground;

/**
 * Spielt einen {@link Tour Rundflug} ab (Phase 13): fliegt von Station zu Station, kreist an jeder langsam und
 * liefert den Text für die Infotafel. Jede eigene Bewegung (Maus, Tasten) beendet den Rundflug; die Kamera
 * bleibt dann, wo sie ist.
 */
public final class TourPlayer {
    private volatile Tour tour;
    private int index;
    private double held;
    private boolean flying;
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> commands = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** Grad je Sekunde, die die Kamera an einer Station kreist. */
    public double drift = 2.0;
    /** Angehalten: Station und Flug bleiben stehen (für Standbilder). */
    public volatile boolean paused;

    public void start(Tour t) { commands.add(() -> begin(t)); }
    public void stop() { commands.add(this::end); }
    public boolean active() { return tour != null; }
    public Tour tour() { return tour; }
    public int index() { return index; }

    private OrbitCamera cam;

    /** Einmal je Bild. */
    public void update(double dt, OrbitCamera c) {
        cam = c;
        Runnable r;
        while ((r = commands.poll()) != null) r.run();
        Tour t = tour;
        if (t == null || paused) return;
        if (c.userMoved || c.walking) { end(); return; }
        if (flying) {
            if (!c.flying()) { flying = false; held = 0; c.orbitSpeed = drift; c.autoOrbit = true; }
            return;
        }
        held += dt;
        if (held >= t.stops.get(index).hold()) {
            if (index + 1 >= t.stops.size()) { end(); return; }
            index++;
            go(c);
        }
    }

    private void begin(Tour t) {
        tour = t;
        index = 0;
        if (cam != null) {
            cam.userMoved = false;
            go(cam);
        } else flying = false;
    }

    private void go(OrbitCamera c) {
        Tour.Stop s = tour.stops.get(index);
        c.autoOrbit = false;
        c.lookAt(s.x(), Math.max(0, Ground.height(s.x(), s.z())), s.z(), s.yawDeg(), s.pitchDeg(), s.dist());
        c.userMoved = false;
        flying = true;
        held = 0;
    }

    private void end() {
        if (tour != null && cam != null) { cam.autoOrbit = false; cam.orbitSpeed = 3.0; }
        tour = null;
    }

    /** Text für die Infotafel, oder null. */
    public Player.Caption caption() {
        Tour t = tour;
        if (t == null) return null;
        Tour.Stop s = t.stops.get(Math.min(index, t.stops.size() - 1));
        double prog = (index + (flying ? 0 : Math.min(1, held / Math.max(1e-9, s.hold())))) / t.stops.size();
        // groß der Titel der Station, darunter in Gold Lage und Höhe des Ortes
        double lat = com.dan.kilauea.terrain.Geo.lat(s.z()), lon = com.dan.kilauea.terrain.Geo.lon(s.x(), s.z());
        String where = String.format(java.util.Locale.GERMAN, "%s · %.3f° N · %.3f° W · %s m", s.label(), lat, -lon,
                String.format(java.util.Locale.GERMAN, "%,.0f", Math.max(0, Ground.height(s.x(), s.z()))).replace('.', ' '));
        if (s.label().equals(s.title())) where = where.substring(s.label().length() + 3);
        return new Player.Caption(t.title, index + 1, t.stops.size(), s.title(), where, s.text(), prog,
                "Rundflug · Maus oder Taste übernimmt die Kamera", false, Player.State.PLAYING, 0);
    }
}
