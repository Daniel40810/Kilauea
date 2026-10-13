package com.dan.kilauea.tools;

import com.dan.kilauea.life.Life;
import com.dan.kilauea.sound.Scene;
import com.dan.kilauea.sound.SoundEngine;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;

import java.io.File;
import java.util.Locale;

/**
 * Prüfhilfe Phase 15: Hörproben ohne Lautsprecher. Stellt typische Lagen nach (Hörer, Blick, Quellen),
 * rechnet den Klang und speichert ihn als WAV; dazu Pegel, Spitze und die lautesten Frequenzbänder.
 * <p>Aufruf: {@code KlangProbe <ordner> [sekunden] [name …]}</p>
 */
public final class KlangProbe {
    /** Eine Lage: Name, Beschreibung, Schnappschuss je Zeitpunkt, Ereignisse beim Start. */
    public record Probe(String name, String text, java.util.function.DoubleFunction<Scene> at, java.util.function.Consumer<SoundEngine> start) { }

    /** Grundschnappschuss an einem Ort: Hörer am Boden (Höhe über Grund), Blick nach Kompassrichtung. */
    public static Scene base(double x, double z, double above, double headingDeg, double hour, double sun) {
        HeightGrid g = Ground.grid();
        Scene s = new Scene();
        double gh = Ground.height(x, z);
        double a = Math.toRadians(headingDeg);
        s.listener(x, gh + above, z, Math.sin(a), -Math.cos(a));
        s.elevation = gh;
        s.aboveGround = above;
        s.wind = 7.5;
        s.hour = hour;
        s.sunElevation = sun;
        if (g != null) {
            s.trees = g.cover(x, z, 1);
            s.veg = g.cover(x, z, 0);
            s.rainYear = g.geo(x, z, 3) * com.dan.kilauea.terrain.Geology.RAIN_MAX;
        }
        return s;
    }

    static double x(double lat, double lon) { return Geo.x(lat, lon); }
    static double z(double lat) { return Geo.z(lat); }

    /** Küstenpunkte um den Hörer, wie sie der Renderer liefert. */
    private static final java.util.Map<String, com.dan.kilauea.sea.Coast> COAST = new java.util.HashMap<>();

    public static void coast(Scene s, double swell, double period) {
        String key = Math.round(s.x) + "/" + Math.round(s.z);
        var c = COAST.computeIfAbsent(key, k -> com.dan.kilauea.sea.Coast.scan(Ground::height, s.x, s.z, 1500, 10));
        int nb = 0;
        for (var p : c.points) {
            if (nb >= 16) break;
            boolean close = false;
            for (int j = 0; j < nb; j++) if (Math.hypot(s.coast[4 * j] - p.x, s.coast[4 * j + 1] - p.z) < 120) { close = true; break; }
            if (close) continue;
            double d = Math.hypot(p.x - s.x, p.z - s.z);
            if (d > 1500) continue;
            s.coast[4 * nb] = p.x; s.coast[4 * nb + 1] = p.z; s.coast[4 * nb + 2] = p.cliff ? 1 : 0; s.coast[4 * nb + 3] = Math.hypot(d, s.y);
            nb++;
        }
        s.coastCount = nb;
        double a = Math.toRadians(190 + 180);
        s.swellDx = Math.sin(a); s.swellDz = -Math.cos(a); s.swellH = swell; s.swellT = period;
        s.seaDx = -0.94; s.seaDz = 0.34; s.seaH = 0.16 * 7.5; s.seaT = 3.3 + 0.62 * 7.5;
    }

    public static java.util.List<Probe> probes() {
        java.util.List<Probe> l = new java.util.ArrayList<>();
        Place hm = Place.HALEMAUMAU, uek = Place.UEKAHUNA;
        // 1 Fontäne vom Kraterrand (Episode, 300 m)
        l.add(new Probe("fontaene_uekahuna", "Fontäne 300 m im Halemaʻumaʻu, gehört an der Uēkahuna-Klippe (rund 1,7 km)", t -> {
            Scene s = base(uek.x(), uek.z(), 1.7, 140, 20.5, -20);
            s.fountainCount = 1;
            s.fountains[0] = hm.x() - 250; s.fountains[1] = 830; s.fountains[2] = hm.z() + 150; s.fountains[3] = 300; s.fountains[4] = 60;
            return s;
        }, null));
        // 2 Lavasee am Rand
        l.add(new Probe("lavasee", "Lavasee im Halemaʻumaʻu, 350 m vom Rand", t -> {
            Scene s = base(hm.x() + 330, hm.z() - 120, 1.7, 250, 21, -25);
            s.lake = true; s.lakeX = hm.x(); s.lakeZ = hm.z(); s.lakeY = 800;
            return s;
        }, null));
        // 3 Strom bei Mauna Ulu
        l.add(new Probe("strom_maunaulu", "Pāhoehoe-Strom, 2 ha heiße Fläche in 40 m", t -> {
            Place p = Place.MAUNA_ULU;
            Scene s = base(p.x() + 300, p.z() + 600, 1.7, 0, 11, 60);
            s.hotCount = 2;
            s.hot[0] = p.x() + 300; s.hot[1] = p.z() + 560; s.hot[2] = 20000;
            s.hot[3] = p.x() + 380; s.hot[4] = p.z() + 520; s.hot[5] = 8000;
            return s;
        }, null));
        // 4 Meer an der Klippe
        l.add(new Probe("meer_klippe", "Brandung an der Meeresklippe beim Felsbogen Hōlei, Südswell 1,5 m, 14 s", t -> {
            Place p = Place.HOLEI_ARCH;
            Scene s = base(p.x(), p.z() - 60, 1.7, 180, 11, 60);
            coast(s, 1.5, 14);
            s.seaTime = t;
            s.stamp = -1;
            return s;
        }, null));
        // 5 Meereseintritt
        l.add(new Probe("eintritt_kamokuna", "Meereseintritt bei Kamokuna, 400 m entfernt; nach 4 s und 9 s bricht das Delta ab", t -> {
            Place p = Place.KAMOKUNA;
            Scene s = base(p.x() - 380, p.z() - 150, 1.7, 110, 11, 60);
            coast(s, 1.0, 14);
            s.seaTime = t;
            s.entryCount = 1;
            s.entries[0] = p.x(); s.entries[1] = p.z(); s.entries[2] = 300;
            return s;
        }, e -> { }));
        // 6 Wald am Morgen
        l.add(new Probe("wald_morgen", "Regenwald bei Nāhuku um 6:45: Morgenchor (ʻapapane, ʻamakihi, ʻōmaʻo, Japanbrillenvogel)", t -> {
            Place p = Place.NAHUKU;
            Scene s = base(p.x() + 80, p.z() + 60, 1.7, 90, 6.75, 2);
            s.wind = 4;
            return s;
        }, null));
        // 7 Wald nachts mit Coquí
        l.add(new Probe("wald_nacht", "Regenwald bei Nāhuku um 21 Uhr: Coquí-Frösche", t -> {
            Place p = Place.NAHUKU;
            Scene s = base(p.x() + 80, p.z() + 60, 1.7, 90, 21, -30);
            s.wind = 3;
            return s;
        }, null));
        // 8 Hubschrauber über den Hörer
        l.add(new Probe("hubschrauber", "Rundflug-Hubschrauber überfliegt den Hörer am Puʻu ʻŌʻō in 520 m Höhe mit 46 m/s (Doppler)", t -> {
            Place p = Place.PUU_OO;
            Scene s = base(p.x() - 1300, p.z(), 1.7, 0, 11, 60);
            double hx = s.x - 600 + 46 * t, hy = s.y + 520, hz = s.z - 120;
            s.sourceCount = 1;
            float[] a = s.sources;
            a[0] = Life.E_HELI; a[1] = (float) hx; a[2] = (float) hy; a[3] = (float) hz; a[4] = 46; a[5] = 0; a[6] = 0; a[7] = 1;
            return s;
        }, null));
        // 9 Auto fährt vorbei
        l.add(new Probe("auto", "Auto fährt in 8 m Abstand mit 15 m/s vorbei (Chain of Craters Road)", t -> {
            Place p = Place.HOLEI_PALI;
            Scene s = base(p.x(), p.z(), 1.7, 0, 11, 60);
            s.sourceCount = 1;
            float[] a = s.sources;
            a[0] = Life.E_CAR; a[1] = (float) (s.x - 90 + 15 * t); a[2] = (float) (s.y - 1); a[3] = (float) (s.z - 8); a[4] = 15; a[5] = 0; a[6] = 0; a[7] = 15;
            return s;
        }, null));
        // 10 Nēnē
        l.add(new Probe("nene", "Nēnē rufen an der Uēkahuna-Klippe, 6–20 m entfernt", t -> base(uek.x(), uek.z(), 1.7, 140, 9.5, 40), e -> {
            for (int i = 0; i < 6; i++) e.post(SoundEngine.EV_CALL, i == 3 ? Life.C_NENE_FLIGHT : Life.C_NENE, uek.x() + 6 + 2 * i, Ground.height(uek.x(), uek.z()) + 0.4, uek.z() - 8 + 2 * i);
        }));
        // 11 Einsturzbeben 2018
        l.add(new Probe("beben_einsturz", "Einsturzbeben M 5,3 unter dem Halemaʻumaʻu (2018), gehört an der Uēkahuna-Klippe; davor ein M 2,4", t -> base(uek.x(), uek.z(), 1.7, 140, 15, 40),
                e -> { e.post(SoundEngine.EV_QUAKE, hm.x(), -200, hm.z(), 2.4); }));
        // 12 Sturm auf dem Grat
        l.add(new Probe("wind_sturm", "Passat 15 m/s an der Hilina Pali (Kante, offen)", t -> {
            Place p = Place.HILINA_PALI;
            Scene s = base(p.x(), p.z(), 1.7, 160, 13, 60);
            s.wind = 15;
            s.trees = 0;
            return s;
        }, null));
        // 13 Regen im Wald
        l.add(new Probe("regen_wald", "Starker Regen im Regenwald bei Nāhuku am Nachmittag", t -> {
            Place p = Place.NAHUKU;
            Scene s = base(p.x() + 80, p.z() + 60, 1.7, 90, 15, 40);
            s.rain = 0.85;
            return s;
        }, null));
        // 14 In der Röhre
        l.add(new Probe("nahuku_innen", "Zu Fuß durch die Lavaröhre Nāhuku: Schritte, Tropfen, Hall", t -> {
            Place p = Place.NAHUKU;
            Scene s = base(p.x(), p.z(), 1.7, 200, 11, 60);
            s.inTube = true;
            s.walking = true;
            s.walkSpeed = 1.3;
            s.surface = 3;
            return s;
        }, null));
        // 15 Schritte auf ʻaʻā
        l.add(new Probe("schritte_aa", "Zu Fuß über ʻaʻā-Brocken an der Kaʻū-Wüste, leichter Wind", t -> {
            Place p = Place.KAU_DESERT;
            Scene s = base(p.x(), p.z(), 1.7, 60, 10, 50);
            s.walking = true;
            s.walkSpeed = 1.4;
            s.surface = 1;
            s.wind = 5;
            return s;
        }, null));
        // 16 Unter Wasser an den Kissen
        l.add(new Probe("unterwasser", "Unter Wasser an der Kissenlava vor Kamokuna, 8 m entfernt", t -> {
            Place p = Place.KAMOKUNA;
            Scene s = base(p.x(), p.z() + 300, 0, 180, 11, 60);
            s.y = -25; s.aboveGround = 3; s.underwater = true;
            s.pillowX = p.x(); s.pillowY = -28; s.pillowZ = p.z() + 306;
            s.swellH = 1.0;
            return s;
        }, null));
        return l;
    }

    /** Eine Probe rechnen; liefert L, R. Beim Einsturzbeben folgt nach 3 s das M 5,3; beim Eintritt brechen Teile ab. */
    public static float[][] render(Probe p, double secs, SoundEngine e) {
        if (p.start() != null) p.start().accept(e);
        Place hm = Place.HALEMAUMAU;
        boolean quake = p.name().equals("beben_einsturz"), entry = p.name().equals("eintritt_kamokuna");
        double[] fired = {0};
        return e.offline(secs, t -> {
            if (quake && t >= 3 && fired[0] == 0) { fired[0] = 1; e.post(SoundEngine.EV_QUAKE, hm.x() + 200, -900, hm.z(), 5.3); }
            if (entry && t >= 4 && fired[0] == 0) { fired[0] = 1; e.post(SoundEngine.EV_SURGE, Place.KAMOKUNA.x(), Place.KAMOKUNA.z(), 1.2, 0); }
            if (entry && t >= 9 && fired[0] == 1) { fired[0] = 2; e.post(SoundEngine.EV_SURGE, Place.KAMOKUNA.x() + 40, Place.KAMOKUNA.z(), 0.8, 0); }
            return p.at().apply(t);
        });
    }

    /** Pegel (dBFS, RMS beider Kanäle) eines Abschnitts. */
    public static double dbfs(float[] l, float[] r, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) s += l[i] * l[i] + r[i] * r[i];
        return 10 * Math.log10(Math.max(1e-12, s / (2.0 * Math.max(1, to - from))));
    }

    /** Leistung im Frequenzband [f0, f1] (Goertzel über Stützfrequenzen, Mittel) eines Kanals. */
    public static double band(float[] x, int from, int to, double f0, double f1) {
        double sum = 0;
        int k = 0;
        for (double f = f0; f <= f1; f += (f1 - f0) / 12.0) {
            double w = 2 * Math.PI * f / Dsp_RATE, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
            for (int i = from; i < to; i++) { double s0 = x[i] + c * s1 - s2; s2 = s1; s1 = s0; }
            sum += s1 * s1 + s2 * s2 - c * s1 * s2;
            k++;
        }
        return sum / k / Math.max(1, to - from);
    }

    static final double Dsp_RATE = SoundEngine.RATE;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "klang");
        out.mkdirs();
        double secs = args.length > 1 ? Double.parseDouble(args[1]) : 12;
        java.util.Set<String> only = new java.util.HashSet<>();
        for (int i = 2; i < args.length; i++) only.add(args[i]);
        com.dan.kilauea.render.Renderer.vorab();
        while (com.dan.kilauea.render.Renderer.vorabFortschritt() < 1) Thread.sleep(200);
        for (Probe p : probes()) {
            if (!only.isEmpty() && !only.contains(p.name())) continue;
            SoundEngine e = new SoundEngine();
            long t0 = System.nanoTime();
            float[][] lr = render(p, secs, e);
            double ms = (System.nanoTime() - t0) / 1e6;
            SoundEngine.wav(new File(out, p.name() + ".wav"), lr[0], lr[1]);
            int n = lr[0].length;
            double pk = 0;
            for (int i = 0; i < n; i++) pk = Math.max(pk, Math.max(Math.abs(lr[0][i]), Math.abs(lr[1][i])));
            double l = dbfs(lr[0], new float[n], 0, n) + 3, r = dbfs(new float[n], lr[1], 0, n) + 3;
            String[] bands = {"< 100", "100–400", "400–1 k", "1–2,5 k", "2,5–6 k", "> 6 k"};
            double[][] b = {{30, 100}, {100, 400}, {400, 1000}, {1000, 2500}, {2500, 6000}, {6000, 12000}};
            StringBuilder sb = new StringBuilder();
            double tot = 0;
            double[] pw = new double[b.length];
            for (int k = 0; k < b.length; k++) { pw[k] = band(lr[0], n / 4, Math.min(n, n / 4 + 44100), b[k][0], b[k][1]); tot += pw[k]; }
            for (int k = 0; k < b.length; k++) sb.append(String.format(Locale.GERMAN, "%s Hz %.0f %% · ", bands[k], 100 * pw[k] / Math.max(1e-30, tot)));
            System.out.printf(Locale.GERMAN, "%-20s L %.1f dBFS · R %.1f dBFS · Spitze %.2f · Rechnung %.1f %% der Echtzeit%n      %s%n      %s%n",
                    p.name(), l, r, pk, 100 * ms / 1000 / secs, p.text(), sb);
        }
    }
}
