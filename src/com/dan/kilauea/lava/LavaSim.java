package com.dan.kilauea.lava;

import com.dan.kilauea.terrain.Ground;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Lässt das Lava-Modell in der App laufen, in einem eigenen Thread neben dem Bildlauf: Strom
 * ({@link LavaField}) und Lavasee ({@link LavaLake}). Der Thread rechnet höchstens rund 60 % einer
 * Sekunde je Sekunde; kommt er im Zeitraffer nicht nach, läuft die Lava langsamer, als eingestellt,
 * und {@link #behind()} meldet das. Wer liest (Karte, später der Lava-Shader), sperrt {@link #field()}
 * bzw. {@link #lake()} mit {@code synchronized}.
 */
public final class LavaSim {
    /** Vorrat: 512 Blöcke zu 320 × 320 m (52 km², rund 57 MB). */
    public static final int POOL = 512;

    private final DoubleSupplier summitPressure;
    private volatile LavaField field;
    private final LavaLake lake = LavaLake.halemaumau(2026);
    private volatile Vent vent;
    private volatile VentSite site;
    /** Simulationssekunden je Sekunde Wanduhr für den Strom (0 = steht). */
    public volatile double speed = 60;
    private volatile boolean behind;
    private volatile double owed;
    private Thread thread;
    /** Lichtschein der Lava (Phase 8); wer liest, sperrt ihn. */
    public final GlowMap glow = new GlowMap();
    /** Lavasee und Grube zeigen (die Grube gehört dann zum Gelände). */
    public volatile boolean showLake = true;
    private volatile boolean installed;
    /** Wo die Lava ins Meer läuft (Phase 10); wer liest, sperrt es. */
    public final SeaEntry seaEntry = new SeaEntry();
    private long lastEntry;

    /** @param summitPressure Druck im Halemaʻumaʻu-Speicher (Pa), für den Spiegel des Sees */
    public LavaSim(DoubleSupplier summitPressure) { this.summitPressure = summitPressure; }

    /** Thread starten (einmal; der See läuft dann in Echtzeit mit). */
    public synchronized void start() {
        if (thread != null) return;
        thread = new Thread(this::loop, "Lava");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    /**
     * Grube des Sees ins feste Gelände und die Lava als Auflage in die Höhenabfrage eintragen (einmal, sobald
     * das Höhenraster steht). Kamera und Gehen sehen dann Lava, Grube und Seespiegel wie das Bild.
     */
    public synchronized void installGround() {
        if (installed || Ground.grid() == null) return;
        lake.rimY = Ground.rawHeight(lake.cx, lake.cz);
        Ground.shape((x, z, h) -> showLake ? lake.pit(x, z, h) : h);
        Ground.overlay((x, z, h) -> {
            double s = h;
            LavaField f = field;
            if (f != null) {
                // ohne Sperre: die Kamera darf nicht auf den Lava-Thread warten; ein Wert aus dem
                // vorigen Teilschritt genügt für die Höhe
                double add = f.addedAt(x, z);
                if (add > 0.01) s = Math.max(s, h + add);
            }
            if (showLake && !Double.isNaN(lake.rimY) && lake.contains(x, z)) s = Math.max(s, lake.surfaceY());
            return s;
        });
        installed = true;
    }

    /** Fontäne über dem höchsten Schlot: Lage (x, y, z) und Höhe (m), oder null. */
    public double[] fountain() {
        double[][] all = fountains();
        double[] best = null;
        for (double[] f : all) if (best == null || f[5] > best[5]) best = f;
        if (best == null) return null;
        return new double[]{0.5 * (best[0] + best[2]), best[4], 0.5 * (best[1] + best[3]), best[5]};
    }

    /**
     * Alle Fontänen (Phase 12, mehrere Schlote): je Schlot x0, z0, x1, z1, Boden (m ü. M.), Höhe (m); nur
     * Schlote, die fördern und eine Fontäne haben.
     */
    public double[][] fountains() {
        LavaField f = field;
        if (f == null) return new double[0][];
        java.util.List<Vent> vs;
        synchronized (f) { vs = new java.util.ArrayList<>(f.vents()); }
        java.util.List<double[]> r = new java.util.ArrayList<>();
        for (Vent v : vs) {
            double h = v.fountainHeight();
            if (h <= 1) continue;
            double x = 0.5 * (v.x0 + v.x1), z = 0.5 * (v.z0 + v.z1);
            r.add(new double[]{v.x0, v.z0, v.x1, v.z1, Ground.solidHeight(x, z), h});
        }
        return r.toArray(new double[0][]);
    }

    /**
     * Neuen Ausbruch an einem Ort beginnen: das Feld wird geleert (das Gelände bleibt), der Schlot fördert.
     * Braucht das Höhenraster ({@link Ground#grid()}); vorher geschieht nichts.
     */
    public void erupt(VentSite s, double rate) {
        if (Ground.grid() == null) return;
        begin(s.label, s.x(), s.z(), s);
        Vent v = Vent.at(s, rate);
        open(v);
        vent = v;
        site = s;
        start();
    }

    /**
     * Phase 12: einen neuen Lauf beginnen, ohne Schlot: das Feld wird geleert (das Gelände bleibt), Schlote und
     * Meereseintritt entfernt. label, x, z: wonach Karte und Zustand sich richten; key: Erkennung für die Karte.
     */
    public void begin(String label, double x, double z, Object key) {
        if (Ground.grid() == null) return;
        installGround();
        LavaField f = field;
        if (f == null) f = new LavaField(Ground::solidHeight, 10, POOL, 2026);
        synchronized (f) {
            // Der Vorrat wird nicht zurückgegeben: ein neues Feld, wenn er zu mehr als der Hälfte belegt ist
            if (f.blocksUsed() > POOL / 2) f = new LavaField(Ground::solidHeight, 10, POOL, 2026);
            else f.reset();
            f.clearVents();
            synchronized (seaEntry) { seaEntry.clear(); }
            owed = 0;
        }
        field = f;
        vent = null;
        site = null;
        focusLabel = label;
        focusX = x;
        focusZ = z;
        focusKey = key;
    }

    /** Phase 12: einen Schlot zum laufenden Lauf hinzufügen (höchstens 16 je Lauf). */
    public Vent open(Vent v) {
        LavaField f = field;
        if (f == null) {
            if (Ground.grid() == null) return v;
            begin(v.name, 0.5 * (v.x0 + v.x1), 0.5 * (v.z0 + v.z1), v);
            f = field;
        }
        synchronized (f) {
            if (f.vents().size() >= 16) throw new IllegalStateException("höchstens 16 Schlote je Lauf");
            f.addVent(v);
        }
        if (vent == null) vent = v;
        return v;
    }

    /** Wonach Karte und Zustand sich richten (Ort des Ausbruchs). */
    public volatile String focusLabel;
    public volatile double focusX, focusZ;
    public volatile Object focusKey;

    /** Schlote schließen (die Lava läuft aus und erstarrt). */
    public void stopVent() {
        LavaField f = field;
        if (f == null) return;
        synchronized (f) { for (Vent v : f.vents()) v.rate = 0; }
    }

    /** Förderrate des laufenden Schlots ändern. */
    public void rate(double r) { Vent v = vent; if (v != null) v.rate = r; }

    /** Alles leeren. */
    public void reset() {
        LavaField f = field;
        if (f == null) return;
        synchronized (f) {
            for (Vent v : f.vents()) v.rate = 0;
            f.reset();
        }
        vent = null;
        site = null;
        focusKey = null;
        synchronized (seaEntry) { seaEntry.clear(); }
    }

    /**
     * Ganz zurück (Phase 12, Ende eines Drehbuchs): ein frisches, leeres Feld ohne Schlote, Meereseintritt leer.
     * Das alte Feld bleibt, bis nichts mehr darauf zeigt (Karte, Grafik).
     */
    public void clearAll() {
        LavaField f = field;
        if (f != null) synchronized (f) { f.clearVents(); f.reset(); }
        vent = null;
        site = null;
        focusKey = null;
        focusLabel = null;
        owed = 0;
        synchronized (seaEntry) { seaEntry.clear(); }
    }

    /** Rand des Lavasees neu nehmen, nachdem sich das Gelände verformt hat (Phase 12, Einsturz 2018). */
    public void refreshRim() {
        if (!installed) return;
        lake.rimY = Ground.rawHeight(lake.cx, lake.cz);
    }

    /** Zeit des Feldes (s) oder 0. */
    public double fieldTime() { LavaField f = field; return f == null ? 0 : f.time(); }

    /** Noch nicht gerechnete Simulationssekunden (der Faden kommt nicht nach). */
    public double owed() { return owed; }

    /**
     * Ohne Faden (Werkzeuge, Prüfung): Strom und See um s Simulationssekunden weiterrechnen, Meereseintritt,
     * Glut und heiße Stellen gleich mit.
     */
    public void stepNow(double s) {
        synchronized (lake) {
            lake.pressure(summitPressure.getAsDouble());
            lake.step(Math.min(s, 30));
        }
        LavaField f = field;
        if (f != null && s > 0) synchronized (f) { f.step(s); }
        refreshSeaEntry();
    }

    public LavaField field() { return field; }
    public LavaLake lake() { return lake; }
    public VentSite site() { return site; }
    public Vent vent() { return vent; }
    /** true, wenn der Zeitraffer gerade nicht mitkommt. */
    public boolean behind() { return behind; }

    private long lastGlow;

    private long lastLakeImage;
    /** Bild des Lavasees für die Grafik (Phase 8); wer liest, sperrt es. */
    public final LakeImage lakeImage = new LakeImage();

    /** Lichtschein sofort neu rechnen (Standbilder; sonst zweimal je Sekunde im Thread). */
    public void refreshGlow() {
        LavaField f = field;
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        // Fontänen: glühende Fläche rund H²/6 (dazu die Länge der Spalte), Mitte in 40 % der Höhe [G]
        for (double[] fo : fountains()) {
            double hh = fo[5], len = Math.hypot(fo[2] - fo[0], fo[3] - fo[1]);
            pts.add(GlowMap.point(0.5 * (fo[0] + fo[2]), 0.5 * (fo[1] + fo[3]), hh * 0.4, hh * hh / 6 + 0.3 * hh * len, 1100));
        }
        // Lavasee: offene Nähte bei 1 150 °C, rund 20 m tiefer als der Rand [G]
        if (showLake && !Double.isNaN(lake.rimY)) {
            double share;
            synchronized (lakeImage) { share = lakeImage.seamShare; }
            if (share <= 0) share = 0.05;
            pts.add(GlowMap.point(lake.cx, lake.cz, 25, Math.PI * lake.a * lake.b * share, 1150));
        }
        if (f != null) {
            synchronized (f) {
                synchronized (glow) { glow.update(f, pts); }
                refreshHot(f);
            }
        } else synchronized (glow) { glow.update(null, pts); }
    }

    /** Meereseintritt neu auswerten (sonst viermal je Sekunde im Thread). */
    public void refreshSeaEntry() {
        LavaField f = field;
        if (f == null) return;
        synchronized (f) {
            synchronized (seaEntry) { seaEntry.update(f); }
        }
    }

    /** Heiße, offene Lava in Gruppen von 80 m (Phase 11): je Gruppe x, z, Fläche (m²); wer liest, sperrt das Feld. */
    public final double[] hot = new double[8 * 3];
    public volatile int hotCount;

    private void refreshHot(LavaField f) {
        java.util.Map<Long, double[]> bins = new java.util.HashMap<>();
        f.forEachHot(650, (x, z, a, t, d, h) -> {
            long key = (Math.floorDiv((long) Math.floor(x), 80L) << 32) ^ (Math.floorDiv((long) Math.floor(z), 80L) & 0xffffffffL);
            double[] b = bins.computeIfAbsent(key, k -> new double[3]);
            b[0] += a; b[1] += x * a; b[2] += z * a;
        });
        java.util.List<double[]> l = new java.util.ArrayList<>(bins.values());
        l.sort((p, q) -> Double.compare(q[0], p[0]));
        int n = Math.min(8, l.size());
        for (int i = 0; i < n; i++) {
            double[] b = l.get(i);
            hot[3 * i] = b[1] / b[0];
            hot[3 * i + 1] = b[2] / b[0];
            hot[3 * i + 2] = b[0];
        }
        hotCount = n;
    }

    /** Bild des Sees sofort neu rechnen (Standbilder). */
    public void refreshLakeImage() {
        synchronized (lake) {
            synchronized (lakeImage) { lakeImage.update(lake); }
        }
    }

    private void loop() {
        long last = System.nanoTime();
        while (true) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.nanoTime();
            double wall = Math.min(0.5, (now - last) / 1e9);
            last = now;
            // See: in Echtzeit, höchstens 30-fach (er ist zum Ansehen da)
            synchronized (lake) {
                lake.pressure(summitPressure.getAsDouble());
                lake.step(wall * Math.max(1, Math.min(30, speed)));
            }
            if (now - lastLakeImage > 200_000_000L) {
                lastLakeImage = now;
                refreshLakeImage();
            }
            if (now - lastGlow > 500_000_000L) {
                lastGlow = now;
                refreshGlow();
            }
            LavaField f = field;
            if (f == null || speed <= 0) { owed = 0; continue; }
            // nicht beliebig vorschießen: kommt der Faden nicht nach, läuft die Lava langsamer (Drehbücher folgen ihr)
            owed = Math.min(owed + wall * speed, Math.max(20, speed * 1.5));
            long budget = now + 30_000_000L;   // 30 ms von 50
            // je Teilschritt sperren, damit Bildlauf und Karte nie lange warten (Phase 13: einzeln statt bis zu 20 s
            // auf einmal; das Statusbild zu Phase 12 zeigte 9 ms je Bild, die der Bildlauf auf die Lava wartete)
            while (owed > 1e-6 && System.nanoTime() < budget) {
                synchronized (f) { owed -= f.stepOnce(Math.min(owed, LavaField.DT_MAX)); }
                Thread.yield();
            }
            behind = owed > Math.max(2, speed * 0.25);
            if (now - lastEntry > 250_000_000L) {
                lastEntry = now;
                refreshSeaEntry();
            }

        }
    }

    /** Kurzer Zustand für das Bedienfeld. */
    public String summary() {
        LavaField f = field;
        String lbl = focusLabel;
        if (f == null || lbl == null) return "Kein Ausbruch. Ort und Rate wählen, dann „Ausbruch starten“.";
        LavaField.Stats s;
        double rateSum = 0;
        int open = 0;
        synchronized (f) {
            s = f.stats(focusX, focusZ);
            for (Vent w : f.vents()) if (w.rate > 0) { rateSum += w.rate; open++; }
        }
        return String.format(Locale.GERMAN, "%s · %s · nach %s: %,.0f m³ · %.2f km lang · %.3f km² · Kern im Mittel %.0f °C, Front %.0f °C · "
                        + "ʻaʻā %.0f %% · unter Dach %.1f ha, %d Oberlichter%s",
                lbl, open == 0 ? "Schlot zu" : String.format(Locale.GERMAN, open > 1 ? "%.0f m³/s aus %d Schloten" : "%.0f m³/s", rateSum, open),
                clock(s.time), s.erupted, s.length / 1000, s.area / 1e6, s.meanT, s.frontT, s.aaShare * 100, s.roofArea / 1e4, s.skylights,
                behind ? " · Zeitraffer kommt nicht nach" : "");
    }

    public String lakeSummary() {
        synchronized (lake) {
            return String.format(Locale.GERMAN, "Lavasee: %d Platten, Spiegel %.1f m über dem Kraterboden%s", lake.plates(), lake.level(),
                    lake.pistonRising() ? " · Gaskolben steigt" : "");
        }
    }

    static String clock(double s) {
        long m = Math.round(s / 60);
        return m < 60 ? m + " min" : String.format(Locale.GERMAN, "%d h %02d min", m / 60, m % 60);
    }
}
