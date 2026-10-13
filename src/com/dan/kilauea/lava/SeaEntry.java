package com.dan.kilauea.lava;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Wo die Lava ins Meer läuft, ohne Grafik (Phase 10). Aus dem Feld:
 * <ul>
 * <li>Eintrittsstellen: Zellen mit Lava unter dem Meeresspiegel, zu Gruppen von 40 m zusammengefasst, mit dem
 * Wärmestrom ins Wasser (MW). Daraus Dampf und Laze, kochendes, trübes Wasser, Glutbrocken und Kissenlava.</li>
 * <li>Lavafälle: Abflüsse über eine Stufe von mehr als 4 m (Klippe, Rand des Deltas); an der Klippe als
 * Feuerwehrschlauch aus dem Ende der Röhre.</li>
 * <li>Delta: neues Land im Meer. Es bricht von Zeit zu Zeit ab; die Wahrscheinlichkeit wächst mit seiner
 * Fläche (2,5 je Stunde und Hektar, ab 0,2 ha) [G nach dem Ablauf an Kamokuna 2016/17: Abbrüche von Bruchteilen eines
 * Hektars bis 10 ha, L Größenordnung].</li>
 * </ul>
 * Fester Keim; {@link #update} läuft unter der Sperre des Feldes.
 */
public final class SeaEntry {
    public static final int MAX = 8, MAX_FALL = 24;
    /** Je Gruppe: x, z, Wärmestrom (MW), Temperatur (°C), Wassertiefe (m). */
    public final double[] entries = new double[MAX * 5];
    public int count;
    /** Je Fall: x, y, z oben; x1, y1, z1 unten; Fluss (m³/s); Temperatur (°C). */
    public final double[] falls = new double[MAX_FALL * 8];
    public int fallCount;
    /** Neues Land im Meer (m²), abgebrochen bisher (m³), Zahl der Abbrüche. */
    public double newLand, collapsedVolume;
    public int collapses;
    /** Letzte Abbrüche: Zeit (s), x, z, Volumen (m³). */
    public final List<double[]> events = new ArrayList<>();
    public volatile int version;
    /** Abbrüche zulassen (die Prüfung schaltet sie für einzelne Läufe ab). */
    public boolean allowCollapse = true;
    /** Wahrscheinlichkeit eines Abbruchs je Stunde und Hektar neuen Landes [G]. */
    public static final double COLLAPSE_RATE = 2.5;

    private final SplittableRandom rnd = new SplittableRandom(2016);
    private double lastTime = Double.NaN;
    private final Map<Long, double[]> bins = new HashMap<>();
    private final List<double[]> tmp = new ArrayList<>();

    public void update(LavaField f) {
        double time = f.time();
        double dt = Double.isNaN(lastTime) ? 0 : Math.max(0, time - lastTime);
        lastTime = time;
        // Eintritt: Zellen zu Gruppen von 40 m
        bins.clear();
        f.forEachSeaContact((x, z, heat, t, depth, h) -> {
            long key = (Math.floorDiv((long) Math.floor(x), 40L) << 32) ^ (Math.floorDiv((long) Math.floor(z), 40L) & 0xffffffffL);
            double[] b = bins.computeIfAbsent(key, k -> new double[5]);
            b[0] += heat; b[1] += x * heat; b[2] += z * heat; b[3] += t * heat; b[4] += depth * heat;
        });
        tmp.clear();
        tmp.addAll(bins.values());
        tmp.sort((a, b) -> Double.compare(b[0], a[0]));
        count = Math.min(MAX, tmp.size());
        for (int i = 0; i < count; i++) {
            double[] b = tmp.get(i);
            entries[5 * i] = b[1] / b[0];
            entries[5 * i + 1] = b[2] / b[0];
            entries[5 * i + 2] = b[0] / 1e6;
            entries[5 * i + 3] = b[3] / b[0];
            entries[5 * i + 4] = b[4] / b[0];
        }
        // Lavafälle
        tmp.clear();
        f.forEachFall(4.0, (x, y, z, x1, y1, z1, q, t) -> tmp.add(new double[]{x, y, z, x1, y1, z1, q, t}));
        tmp.sort((a, b) -> Double.compare(b[6], a[6]));
        fallCount = Math.min(MAX_FALL, tmp.size());
        for (int i = 0; i < fallCount; i++) System.arraycopy(tmp.get(i), 0, falls, 8 * i, 8);
        // Delta und Abbrüche
        newLand = f.newLand();
        if (allowCollapse && dt > 0 && newLand > 2000) {
            double rate = COLLAPSE_RATE * newLand / 1e4 / 3600;
            if (rnd.nextDouble() < 1 - Math.exp(-rate * dt)) {
                double cx, cz;
                if (count > 0) { cx = entries[0]; cz = entries[1]; }
                else if (fallCount > 0) { cx = falls[3]; cz = falls[5]; }
                else cx = cz = Double.NaN;
                if (!Double.isNaN(cx)) {
                    double v = f.collapse(cx, cz, 30 + 40 * rnd.nextDouble(), -2);
                    if (v > 0) {
                        collapses++;
                        collapsedVolume += v;
                        events.add(new double[]{time, cx, cz, v});
                        if (events.size() > 8) events.remove(0);
                    }
                }
            }
        }
        version++;
    }

    /** Wärmestrom aller Eintrittsstellen (MW). */
    public double totalHeat() {
        double s = 0;
        for (int i = 0; i < count; i++) s += entries[5 * i + 2];
        return s;
    }

    public void clear() {
        count = fallCount = 0;
        newLand = collapsedVolume = 0;
        collapses = 0;
        events.clear();
        lastTime = Double.NaN;
        version++;
    }

    public String summary() {
        if (count == 0 && fallCount == 0 && newLand <= 0) return "Meereseintritt: keine Lava im Meer";
        return String.format(java.util.Locale.GERMAN, "Meereseintritt: %d Stellen, %.0f MW ins Wasser · %d Lavafälle · neues Land %.2f ha · %d Abbrüche (%,.0f m³)",
                count, totalHeat(), fallCount, newLand / 1e4, collapses, collapsedVolume);
    }
}
