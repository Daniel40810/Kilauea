package com.dan.kilauea.render;

import com.dan.kilauea.sound.Scene;
import com.dan.kilauea.sound.SoundEngine;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;

/**
 * Baut aus der Szene den Schnappschuss für den Klang (rund 20-mal je Sekunde) und meldet Ereignisse
 * (Beben, Rufe, Abbrüche am Meereseintritt) jedes Bild. Rechnet nur mit dem, was ohnehin da ist; im
 * Bildlauf höchstens einige Zehntel Millisekunden.
 */
final class SoundSampler {
    private double acc = 1, lastQuakeT = Double.NaN, lastSurge = Double.NaN;
    private long lastCall = -1;
    private double px = Double.NaN, pz, speed;
    private final double[] call = new double[5];
    private double[] cracks;

    void sample(Renderer r, SoundEngine eng, double dt) {
        HeightGrid g = Ground.grid();
        if (g == null) return;
        var cam = r.camera;
        // eigene Geschwindigkeit (für die Schritte)
        if (!Double.isNaN(px) && dt > 0) speed += (Math.hypot(cam.ex - px, cam.ez - pz) / dt - speed) * Math.min(1, dt * 6);
        px = cam.ex; pz = cam.ez;
        events(r, eng);
        acc += dt;
        if (acc < 0.05) return;
        acc = 0;
        Scene s = new Scene();
        double[] f = cam.forward();
        s.listener(cam.ex, cam.ey, cam.ez, f[0], f[2]);
        double gh = Ground.height(cam.ex, cam.ez);
        s.elevation = gh;
        s.aboveGround = cam.ey - gh;
        s.underwater = r.showOcean && cam.ey < -0.05;
        var tube = com.dan.kilauea.terrain.LavaTube.get();
        s.walking = cam.walking;
        s.inTube = cam.walking && tube != null && tube.underRoof(cam.ex, cam.ez, cam.ey - com.dan.kilauea.camera.OrbitCamera.EYE);
        if (s.inTube) s.aboveGround = com.dan.kilauea.camera.OrbitCamera.EYE;
        s.walkSpeed = cam.walking ? speed : 0;
        s.surface = surface(g, cam.ex, cam.ez, s.inTube);
        s.wind = r.state.windSpeed;
        s.rain = r.state.rain;
        double tr = 0, vg = 0;
        for (int k = 0; k < 5; k++) {
            double ox = k == 0 ? 0 : (k == 1 ? 60 : k == 2 ? -60 : 0), oz = k == 3 ? 60 : k == 4 ? -60 : 0;
            tr += g.cover(cam.ex + ox, cam.ez + oz, 1) / 5;
            vg += g.cover(cam.ex + ox, cam.ez + oz, 0) / 5;
        }
        s.trees = tr;
        s.veg = vg;
        s.rainYear = g.geo(cam.ex, cam.ez, 3) * com.dan.kilauea.terrain.Geology.RAIN_MAX;
        s.hour = r.state.clock.hour();
        s.sunElevation = r.state.clock.elevationDeg;
        // Feuer (nur, was im Bild ist)
        if (r.lavaLayer.show) {
            double[][] fo = r.lava.fountains();
            for (double[] q : fo) {
                if (s.fountainCount >= 4) break;
                int i = 5 * s.fountainCount++;
                s.fountains[i] = 0.5 * (q[0] + q[2]);
                s.fountains[i + 1] = q[4];
                s.fountains[i + 2] = 0.5 * (q[1] + q[3]);
                s.fountains[i + 3] = q[5];
                s.fountains[i + 4] = Math.hypot(q[2] - q[0], q[3] - q[1]);
            }
            int n = Math.min(4, r.lava.hotCount);
            // die nächsten zuerst
            int[] idx = {0, 1, 2, 3, 4, 5, 6, 7};
            double[] h = r.lava.hot;
            int all = Math.min(8, r.lava.hotCount);
            for (int a = 0; a < all; a++)
                for (int b = a + 1; b < all; b++)
                    if (Math.hypot(h[3 * idx[b]] - cam.ex, h[3 * idx[b] + 1] - cam.ez) < Math.hypot(h[3 * idx[a]] - cam.ex, h[3 * idx[a] + 1] - cam.ez)) {
                        int t = idx[a]; idx[a] = idx[b]; idx[b] = t;
                    }
            for (int k = 0; k < Math.min(n, all); k++) {
                s.hot[3 * k] = h[3 * idx[k]];
                s.hot[3 * k + 1] = h[3 * idx[k] + 1];
                s.hot[3 * k + 2] = h[3 * idx[k] + 2];
                s.hotCount++;
            }
            var lake = r.lava.lake();
            if (r.lava.showLake && !Double.isNaN(lake.rimY)) {
                s.lake = true;
                s.lakeX = lake.cx; s.lakeZ = lake.cz; s.lakeY = lake.surfaceY(); s.lakeLevel = lake.level();
            }
            var e = r.lava.seaEntry;
            synchronized (e) {
                for (int k = 0; k < Math.min(4, e.count); k++) {
                    s.entries[3 * k] = e.entries[5 * k];
                    s.entries[3 * k + 1] = e.entries[5 * k + 1];
                    s.entries[3 * k + 2] = e.entries[5 * k + 2];
                    s.entryCount++;
                }
            }
            double[] tip = r.pillowTip();
            if (tip != null) { s.pillowX = tip[0]; s.pillowY = tip[1]; s.pillowZ = tip[2]; }
        }
        // Dampfspalten am Gipfel
        if (cracks == null) {
            java.util.List<double[]> l = new java.util.ArrayList<>();
            for (double[][] c : com.dan.kilauea.terrain.SummitPaths.STEAM_CRACKS) for (double[] p : c) l.add(new double[]{Geo.x(p[0], p[1]), Geo.z(p[0])});
            cracks = new double[l.size() * 2];
            for (int i = 0; i < l.size(); i++) { cracks[2 * i] = l.get(i)[0]; cracks[2 * i + 1] = l.get(i)[1]; }
        }
        for (int i = 0; i < cracks.length; i += 2) {
            double d = Math.hypot(cracks[i] - cam.ex, cracks[i + 1] - cam.ez);
            if (d < s.ventDist) { s.ventDist = d; s.ventX = cracks[i]; s.ventZ = cracks[i + 1]; }
        }
        s.ventY = Ground.height(s.ventX, s.ventZ) + 1;
        // Meer: die nächsten Küstenpunkte
        var near = r.sea.nearshore();
        if (r.showOcean && r.sea.show && near != null) {
            java.util.List<com.dan.kilauea.sea.Coast.Point> pts = near.coast.points;
            int m = pts.size();
            double[] best = new double[16];
            int[] bi = new int[16];
            int nb = 0;
            for (int i = 0; i < m; i += Math.max(1, m / 800)) {
                var p = pts.get(i);
                double d = Math.hypot(p.x - cam.ex, p.z - cam.ez);
                if (d > 2500) continue;
                // gleichmäßig verteilt: keine zwei Punkte näher als 120 m
                boolean close = false;
                for (int j = 0; j < nb; j++) { var q = pts.get(bi[j]); if (Math.hypot(q.x - p.x, q.z - p.z) < 120) { close = true; if (d < best[j]) { best[j] = d; bi[j] = i; } break; } }
                if (close) continue;
                if (nb < 16) { best[nb] = d; bi[nb] = i; nb++; }
                else {
                    int w = 0;
                    for (int j = 1; j < 16; j++) if (best[j] > best[w]) w = j;
                    if (d < best[w]) { best[w] = d; bi[w] = i; }
                }
            }
            for (int j = 0; j < nb; j++) {
                var p = pts.get(bi[j]);
                s.coast[4 * j] = p.x; s.coast[4 * j + 1] = p.z; s.coast[4 * j + 2] = p.cliff ? 1 : 0;
                s.coast[4 * j + 3] = Math.sqrt(best[j] * best[j] + cam.ey * cam.ey);
            }
            s.coastCount = nb;
            double[] a = SeaLayer.swellA(r.state), b = SeaLayer.swellB(r.state);
            s.swellDx = a[0]; s.swellDz = a[1]; s.swellH = a[2]; s.swellT = a[3];
            s.seaDx = b[0]; s.seaDz = b[1]; s.seaH = b[2]; s.seaT = b[3];
            s.seaTime = r.simTime();
        }
        // Leben
        var life = r.life;
        synchronized (life) {
            s.sourceCount = Math.min(life.emitCount, s.sources.length / 8);
            System.arraycopy(life.emit, 0, s.sources, 0, s.sourceCount * 8);
        }
        eng.scene(s);
    }

    /** Untergrund unter den Füßen (0 Pāhoehoe, 1 ʻaʻā/Schlacke, 2 Weg, 3 Röhre, 4 Asche). */
    static int surface(HeightGrid g, double x, double z, boolean tube) {
        if (tube) return 3;
        for (int k = 0; k < 2; k++) {
            var net = com.dan.kilauea.terrain.ParkRoads.trails(k);
            if (net == null) continue;
            com.dan.road.Network.Hit h = new com.dan.road.Network.Hit();
            if (net.locate(x, z, h) && Math.abs(h.u) < 1.6) return 2;
        }
        if (com.dan.kilauea.terrain.ParkRoads.distance(x, z) < 4) return 2;
        if (g.cover(x, z, 2) > 0.5) return 4;
        if (g.geo(x, z, 1) > 0.5) return 1;
        return 0;
    }

    private void events(Renderer r, SoundEngine eng) {
        // Beben seit dem letzten Bild
        java.util.List<com.dan.kilauea.magma.Quake> qs;
        synchronized (r.magma) {
            double now = r.magma.time();
            if (Double.isNaN(lastQuakeT) || now < lastQuakeT) { lastQuakeT = now; qs = java.util.List.of(); }
            else { qs = r.magma.quakesSince(lastQuakeT); lastQuakeT = now; }
        }
        int k = 0;
        for (var q : qs) {
            if (k++ > 24) break;
            eng.post(SoundEngine.EV_QUAKE, q.x(), q.y(), q.z(), q.mag());
        }
        // Rufe der Tiere
        long seq = r.life.callSeq();
        if (lastCall < 0) lastCall = seq;
        for (long i = Math.max(lastCall, seq - 32); i < seq; i++)
            if (r.life.callAt(i, call)) eng.post(SoundEngine.EV_CALL, call[0], call[1], call[2], call[3]);
        lastCall = seq;
        // Abbrüche am Meereseintritt
        var e = r.lava.seaEntry;
        synchronized (e) {
            if (Double.isNaN(lastSurge)) {
                lastSurge = -1;
                for (double[] ev : e.events) lastSurge = Math.max(lastSurge, ev[0]);
            } else {
                double top = lastSurge;
                for (double[] ev : e.events) {
                    if (ev[0] <= lastSurge) continue;
                    eng.post(SoundEngine.EV_SURGE, ev[1], ev[2], Math.max(0.3, Math.min(2, Math.log10(Math.max(10, ev[3])) / 4)), 0);
                    top = Math.max(top, ev[0]);
                }
                lastSurge = top;
            }
        }
    }
}
