package com.dan.kilauea.lava;

import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lavaströme als Zellenmodell auf dem Gelände, ohne Grafik (Phase 7). Das Feld legt ein feines Gitter
 * (Standard 10 m) über die Szene, belegt aber nur Blöcke aus 32 × 32 Zellen, wo Lava ist oder gleich
 * hinkommt; alle Blöcke stammen aus einem Vorrat fester Größe, im Schritt wird nichts angelegt.
 * <p>
 * Je Zelle: Untergrund (Gelände beim Belegen), neues Gestein aus diesem Lauf, bewegliche Lava (Dicke und
 * Kerntemperatur), Alter der Kruste, Zeit seit dem Bedecken, ʻaʻā-Anteil, Geschwindigkeit, Röhre und
 * Oberlicht. Ein Schritt:
 * <ol>
 * <li>Fluss in die acht Nachbarn nach Bingham ({@link Rheology#flux}), aus dem Gefälle der Oberfläche
 * (Untergrund + Gestein + Lava). Die Schrittweite folgt daraus: keine Zelle gibt mehr als 40 % ab.</li>
 * <li>Volumen und Wärme wandern mit dem Fluss (Wärmeinhalt bleibt erhalten); die Schlote fördern.</li>
 * <li>Abkühlung: Strahlung und Wind an der Oberfläche, deren Temperatur mit dem Alter der Kruste sinkt;
 * Wärmeleitung in den Untergrund; unter einem Röhrendach nur Leitung durch das Dach; im Meer
 * Abschrecken. Schnelle Lava (über {@link Rheology#U_BREAK}) reißt ihre Kruste immer wieder auf.</li>
 * <li>Zähigkeit mal Scherrate über der Schwelle macht aus Pāhoehoe ʻaʻā, nie zurück.</li>
 * <li>Ruhige Pāhoehoe mit dicker Kruste, die darunter weiterfließt, wird zur Röhre; wo das Dach an steilen
 * Stufen oder zufällig (fester Keim) einbricht, bleibt ein Oberlicht.</li>
 * <li>Unter {@link Rheology#T_SOLID} erstarrt die Lava zu Gestein; spätere Ströme laufen darüber.</li>
 * </ol>
 * Gleicher Keim und gleiche Folge von {@link #step}-Aufrufen ergeben dasselbe Ergebnis. Nicht
 * threadsicher: Wer liest, während ein anderer Thread rechnet, sperrt das Feld ({@code synchronized}).
 */
public final class LavaField {
    /** Höhe des Untergrunds an (x, z) in Metern. */
    public interface Terrain { double height(double x, double z); }

    public static final int B = 32, BB = B * B;
    /** Größte Schrittweite (s). */
    public static final double DT_MAX = 20;
    /** Zeit (s), in der sich zwei Nachbarn höchstens ausgleichen (begrenzt den Fluss in Teichen) [A]. */
    static final double DT_EQ = 0.25;
    /** Anteil der Lava einer Zelle, der in einem Schritt höchstens abfließt. */
    static final double CFL = 0.4;
    /** Abschrecken im Meer (W/(m²·K)) [G, so dass Lava nach Sekunden bis Minuten erstarrt]. */
    static final double H_SEA = 4e4;
    /** Anteil der Röhrenzellen mit Oberlicht [G]; Gefälle, ab dem das Dach immer einbricht [G]. */
    static final double SKY_CHANCE = 0.012, SKY_SLOPE = 0.5;

    /** Unter diesem Anteil des höchsten Stands gilt eine Röhre als leergelaufen [G]. */
    static final double DRAIN_SHARE = 0.6;
    /** Dicke des Dachs, sobald eine Röhre entsteht (m), und Anteil des Bodenverlusts in der Röhre [A]. */
    static final double ROOF_START = 0.5, TUBE_FLOOR = 0.25;
    /** Alter der Haut über dem Schlot (s) [G]. */
    static final float VENT_AGE = 30;
    /** Halbe Weite eines Schlots (m) [G]. */
    static final double VENT_RADIUS = 15;
    public static final byte TUBE = 1, SKY = 2, VENT = 4, SEA = 8, DRAINED = 16;
    /** Phase 12: der Schlot dieser Zelle fördert gerade (nur dann brodelt die Haut über ihm). */
    static final byte VENT_ON = 32;

    public final double cell, area;
    private final Terrain terrain;
    private final long seed;
    private final int pool, side;
    private final double ox, oz;

    // Zellen (Index = Block · 1024 + lokal)
    private final double[] base, rock, h, temp, dh, de;
    private final float[][] out = new float[8][];
    private final float[] outSum, age, covered, aa, aaIn, speed, arrival, peak, frozenAt;
    private final byte[] flags;
    // Blöcke
    private final int[] table, bi, bj, nbr;
    private final boolean[] awake, ventBlock, dirty;
    private int tableVersion;
    private final int[] awakeList;
    private int awakeCount, used;
    private boolean full;

    private final List<Vent> vents = new ArrayList<>();
    private double time, erupted, frozenTotal, collapsed;
    private long steps;
    private double lastDt;

    // Richtungen: Ost, Süd, West, Nord, Südost, Südwest, Nordwest, Nordost
    private static final int[] DI = {1, 0, -1, 0, 1, -1, -1, 1}, DJ = {0, 1, 0, -1, 1, 1, -1, -1};
    private final double[] dist = new double[8], width = new double[8];

    /** Feld über dem Gelände der Szene ({@link Ground#solidHeight}) mit Zellen von 10 m und 512 Blöcken. */
    public LavaField(long seed) { this(Ground::solidHeight, 10, 512, seed); }

    /**
     * @param terrain  Höhe des Untergrunds
     * @param cell     Zellgröße (m)
     * @param pool     größte Zahl der Blöcke aus 32 × 32 Zellen (Speicher rund 100 kB je Block)
     * @param seed     Keim für Oberlichter
     */
    public LavaField(Terrain terrain, double cell, int pool, long seed) {
        this.terrain = terrain;
        this.cell = cell;
        this.area = cell * cell;
        this.pool = pool;
        this.seed = seed;
        this.ox = HeightGrid.X0 - 0.5 * HeightGrid.CELL;
        this.oz = HeightGrid.Z0 - 0.5 * HeightGrid.CELL;
        this.side = (int) Math.ceil(HeightGrid.EXTENT / (cell * B));
        int n = pool * BB;
        base = new double[n];
        rock = new double[n];
        h = new double[n];
        temp = new double[n];
        dh = new double[n];
        de = new double[n];
        for (int k = 0; k < 8; k++) out[k] = new float[n];
        outSum = new float[n];
        age = new float[n];
        covered = new float[n];
        aa = new float[n];
        aaIn = new float[n];
        speed = new float[n];
        arrival = new float[n];
        peak = new float[n];
        frozenAt = new float[n];
        dirty = new boolean[pool];
        flags = new byte[n];
        table = new int[side * side];
        java.util.Arrays.fill(table, -1);
        bi = new int[pool];
        bj = new int[pool];
        nbr = new int[pool * 9];
        awake = new boolean[pool];
        ventBlock = new boolean[pool];
        awakeList = new int[pool];
        for (int k = 0; k < 8; k++) {
            boolean diag = DI[k] != 0 && DJ[k] != 0;
            dist[k] = diag ? cell * Math.sqrt(2) : cell;
            // Kantenbreiten so, dass ein Kreis rund bleibt: Summe 4·w + 4·w/√2 ≈ Umfang einer Zelle mit Ecken [A]
            width[k] = diag ? cell * 0.4142 : cell * 0.5858;
        }
    }

    // ------------------------------------------------------------------ Gitter

    private int gi(double x) { return (int) Math.floor((x - ox) / cell); }
    private int gj(double z) { return (int) Math.floor((z - oz) / cell); }
    public double cx(int i) { int b = i / BB, l = i % BB; return ox + ((bi[b] * B + (l % B)) + 0.5) * cell; }
    public double cz(int i) { int b = i / BB, l = i % BB; return oz + ((bj[b] * B + (l / B)) + 0.5) * cell; }

    /** Index der Zelle an den globalen Zellkoordinaten oder −1, wenn ihr Block nicht belegt ist. */
    private int index(int gi, int gj) {
        if (gi < 0 || gj < 0) return -1;
        int bx = gi / B, bz = gj / B;
        if (bx >= side || bz >= side) return -1;
        int b = table[bz * side + bx];
        return b < 0 ? -1 : b * BB + (gj % B) * B + (gi % B);
    }

    /** Index der Zelle an (x, z), oder −1. */
    public int indexAt(double x, double z) { return index(gi(x), gj(z)); }

    /** Block an (bx, bz) belegen (oder den vorhandenen liefern); −1, wenn der Vorrat erschöpft ist. */
    private int allocate(int bx, int bz) {
        if (bx < 0 || bz < 0 || bx >= side || bz >= side) return -1;
        int b = table[bz * side + bx];
        if (b >= 0) return b;
        if (used >= pool) { full = true; return -1; }
        b = used++;
        dirty[b] = true;
        tableVersion++;
        table[bz * side + bx] = b;
        bi[b] = bx;
        bj[b] = bz;
        int o = b * BB;
        for (int l = 0; l < BB; l++) {
            double x = ox + (bx * B + (l % B) + 0.5) * cell, z = oz + (bz * B + (l / B) + 0.5) * cell;
            double g = terrain.height(x, z);
            base[o + l] = g;
            arrival[o + l] = -1;
            if (g < 0) flags[o + l] = SEA;
        }
        // Nachbarn eintragen, in beide Richtungen
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                int nx = bx + dx, nz = bz + dz;
                int nb = (nx < 0 || nz < 0 || nx >= side || nz >= side) ? -1 : table[nz * side + nx];
                nbr[b * 9 + (dz + 1) * 3 + (dx + 1)] = nb;
                if (nb >= 0) nbr[nb * 9 + (1 - dz) * 3 + (1 - dx)] = b;
            }
        return b;
    }

    private void wake(int b) {
        if (!awake[b]) { awake[b] = true; awakeList[awakeCount++] = b; }
    }

    /** Nachbarzelle in Richtung k, oder −1. */
    private int neighbour(int b, int l, int k) {
        int li = (l % B) + DI[k], lj = (l / B) + DJ[k];
        if (li >= 0 && lj >= 0 && li < B && lj < B) return b * BB + lj * B + li;
        int sx = li < 0 ? -1 : li >= B ? 1 : 0, sz = lj < 0 ? -1 : lj >= B ? 1 : 0;
        int nb = nbr[b * 9 + (sz + 1) * 3 + (sx + 1)];
        if (nb < 0) return -1;
        return nb * BB + ((lj + B) % B) * B + ((li + B) % B);
    }

    // ------------------------------------------------------------------ Schlote

    /** Schlot hinzufügen (höchstens 16). Die Zellen entlang der Spalte werden belegt. */
    public void addVent(Vent v) {
        if (vents.size() >= 16) throw new IllegalStateException("höchstens 16 Schlote");
        // alle Zellen, deren Mitte höchstens VENT_RADIUS von der Strecke entfernt liegt (ein Schlot hat eine Weite)
        List<Integer> cs = new ArrayList<>();
        double r = Math.max(VENT_RADIUS, 0.75 * cell);
        int ia = gi(Math.min(v.x0, v.x1) - r), ib = gi(Math.max(v.x0, v.x1) + r);
        int ja = gj(Math.min(v.z0, v.z1) - r), jb = gj(Math.max(v.z0, v.z1) + r);
        double ex = v.x1 - v.x0, ez = v.z1 - v.z0, ll = ex * ex + ez * ez;
        for (int cj = ja; cj <= jb; cj++)
            for (int ci = ia; ci <= ib; ci++) {
                double x = ox + (ci + 0.5) * cell, z = oz + (cj + 0.5) * cell;
                double t = ll > 0 ? Math.max(0, Math.min(1, ((x - v.x0) * ex + (z - v.z0) * ez) / ll)) : 0;
                if (Math.hypot(x - v.x0 - ex * t, z - v.z0 - ez * t) > r) continue;
                if (allocate(ci / B, cj / B) < 0) continue;
                cs.add(index(ci, cj));
            }
        v.cells = cs.stream().mapToInt(Integer::intValue).toArray();
        for (int i : v.cells) {
            flags[i] |= VENT;
            ventBlock[i / BB] = true;
            wake(i / BB);
        }
        vents.add(v);
        ensureNeighbours();
    }

    public List<Vent> vents() { return Collections.unmodifiableList(vents); }

    /** Alle Schlote entfernen (ihre Zellen sind danach gewöhnliche Zellen). */
    public void clearVents() {
        for (Vent v : vents) {
            for (int i : v.cells) {
                flags[i] &= (byte) ~(VENT | VENT_ON);
                ventBlock[i / BB] = false;
            }
        }
        vents.clear();
    }

    // ------------------------------------------------------------------ Ablauf

    /** Rechnet dt Sekunden weiter (in Teilschritten bis {@link #DT_MAX}). */
    public void step(double dt) {
        while (dt > 1e-9) dt -= sub(dt);
    }

    /** Ein einziger Teilschritt von höchstens maxDt Sekunden; gibt die gerechnete Zeit zurück (Phase 13: kürzere Sperren). */
    public double stepOnce(double maxDt) { return maxDt > 1e-9 ? sub(maxDt) : 0; }

    /** Blöcke neben Lava am Rand belegen, damit der Fluss nicht an einer Wand endet. */
    private void ensureNeighbours() {
        for (int a = 0; a < awakeCount; a++) {
            int b = awakeList[a];
            int o = b * BB;
            boolean w = false, e = false, n = false, s = false;
            for (int k = 0; k < B; k++) {
                for (int d = 0; d < 2; d++) {
                    if (h[o + k * B + d] > 0) w = true;
                    if (h[o + k * B + B - 1 - d] > 0) e = true;
                    if (h[o + d * B + k] > 0) n = true;
                    if (h[o + (B - 1 - d) * B + k] > 0) s = true;
                }
            }
            if (ventBlock[b]) { w = e = n = s = true; }
            if (!(w || e || n || s)) continue;
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    boolean need = (dx < 0 && w) || (dx > 0 && e) || (dz < 0 && n) || (dz > 0 && s);
                    if (dx != 0 && dz != 0) need = (dx < 0 ? w : e) && (dz < 0 ? n : s);
                    if (need && nbr[b * 9 + (dz + 1) * 3 + (dx + 1)] < 0) allocate(bi[b] + dx, bj[b] + dz);
                }
        }
    }

    private double sub(double maxDt) {
        ensureNeighbours();
        // 1) Fluss aus jeder Zelle mit Lava in tiefer liegende Nachbarn
        double dtLim = Math.min(maxDt, DT_MAX);
        for (int a = 0; a < awakeCount; a++) {
            int b = awakeList[a], o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                double hi = h[i];
                if (hi <= 0) continue;
                double si = base[i] + rock[i] + hi, ti = temp[i];
                double sy = Rheology.yieldStrength(ti), eta = Rheology.viscosity(ti);
                double sum = 0;
                for (int k = 0; k < 8; k++) {
                    int j = neighbour(b, l, k);
                    double q = 0;
                    if (j >= 0) {
                        double dz = si - (base[j] + rock[j] + h[j]);
                        if (dz > 0) {
                            double sin = dz / Math.sqrt(dist[k] * dist[k] + dz * dz);
                            double hcr = sy / (Rheology.RHO * Rheology.G * sin);
                            double r = hi / hcr;
                            if (r > 1) q = sy * hcr * hcr * width[k] / (3 * eta) * (r * r * r - 1.5 * r * r + 0.5);
                            // nie mehr, als die beiden Oberflächen in DT_EQ ausgleicht: ein tiefer Lavateich
                            // gleicht sich sonst in Hundertstelsekunden aus und erzwingt winzige Schritte
                            q = Math.min(q, dz * area / (8 * DT_EQ));
                        }
                    }
                    q = Math.min(q, hi * area);   // nie mehr als die ganze Zelle je Sekunde (gegen Überlauf)
                    out[k][i] = (float) q;
                    sum += (float) q;
                }
                outSum[i] = (float) sum;
                if (sum > 0) dtLim = Math.min(dtLim, CFL * hi * area / sum);
            }
        }
        // Phase 12: welche Schlotzellen gerade fördern (ein geschlossener Schlot verkrustet wie alles andere)
        for (Vent v : vents)
            for (int i : v.cells) flags[i] = (byte) (v.rate > 0 ? flags[i] | VENT_ON : flags[i] & ~VENT_ON);
        // Schlote: höchstens 0,5 m neue Lava je Schritt und Zelle
        for (Vent v : vents)
            if (v.rate > 0 && v.cells.length > 0) dtLim = Math.min(dtLim, 0.5 * v.cells.length * area / v.rate);
        double dt = Math.max(dtLim, Math.min(maxDt, 1e-3));
        // 2) Volumen und Wärme verschieben; Schlote fördern
        for (int a = 0; a < awakeCount; a++) {
            int b = awakeList[a], o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if (h[i] <= 0 || outSum[i] <= 0) continue;
                double ti = temp[i];
                float ai = aa[i];
                for (int k = 0; k < 8; k++) {
                    float q = out[k][i];
                    if (q <= 0) continue;
                    int j = neighbour(b, l, k);
                    double d = q * dt / area;
                    dh[i] -= d;
                    de[i] -= d * ti;
                    dh[j] += d;
                    de[j] += d * ti;
                    if (d > 1e-4 && ai > aaIn[j]) aaIn[j] = ai;
                    wake(j / BB);
                }
            }
        }
        for (Vent v : vents) {
            double rate = v.rate;
            if (rate < 0 && v.cells.length > 0) {
                // Phase 12: Rückfluss (Kīlauea Iki 1959): der Schlot schluckt Lava aus seinen Zellen, höchstens was da ist
                double want = -rate * dt / (v.cells.length * area), back = 0;
                for (int i : v.cells) {
                    double d = Math.min(want, Math.max(0, h[i] + dh[i]));
                    if (d <= 0) continue;
                    dh[i] -= d;
                    de[i] -= d * temp[i];
                    back += d * area;
                }
                v.erupted -= back;
                erupted -= back;
                continue;
            }
            if (rate <= 0 || v.cells.length == 0) continue;
            double d = rate * dt / (v.cells.length * area);
            for (int i : v.cells) {
                dh[i] += d;
                de[i] += d * v.temperature;
            }
            v.erupted += rate * dt;
            erupted += rate * dt;
        }
        // 3) Übernehmen, abkühlen, Zustand
        int keep = 0;
        for (int a = 0; a < awakeCount; a++) {
            int b = awakeList[a], o = b * BB;
            boolean lava = false;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if (dh[i] != 0 || de[i] != 0) {
                    double h0 = h[i], hn = h0 + dh[i];
                    double e = h0 * temp[i] + de[i];
                    if (hn <= 1e-9) {
                        // leergelaufen: eine Röhre bleibt als Hohlraum
                        if ((flags[i] & TUBE) != 0) flags[i] |= DRAINED;
                        rock[i] += Math.max(0, hn);   // Rest unter 1 nm geht ans Gestein
                        hn = 0;
                    } else temp[i] = e / hn;
                    if (h0 <= 1e-6 && hn > 0) {
                        age[i] = 0;
                        speed[i] = 0;
                        if (arrival[i] < 0) arrival[i] = (float) time;
                    }
                    h[i] = hn;
                    dh[i] = 0;
                    de[i] = 0;
                }
                if (aaIn[i] > 0) {
                    if (aaIn[i] > aa[i]) aa[i] = aaIn[i];
                    aaIn[i] = 0;
                }
                double hi = h[i];
                if (hi <= 0) continue;
                lava |= cool(b, l, i, hi, dt);
            }
            dirty[b] = true;
            if (lava || ventBlock[b]) awakeList[keep++] = b;
            else awake[b] = false;
        }
        awakeCount = keep;
        time += dt;
        lastDt = dt;
        steps++;
        return dt;
    }

    /** Abkühlung, Kruste, ʻaʻā, Röhre, Erstarren einer Zelle; true, wenn danach noch Lava da ist. */
    private boolean cool(int b, int l, int i, double hi, double dt) {
        double t = temp[i];
        // Geschwindigkeit aus dem Abfluss; dünner als 0,2 m zählt als 0,2 m (sonst täuscht ein fast leerer Rand Tempo vor).
        // Phase 12: als Vektor über die Richtungen. In einem tiefen Teich gleicht das Modell die Oberfläche ständig
        // zwischen Nachbarn aus; diese Ausgleiche heben sich als Vektor auf, als Summe täuschten sie Meter je Sekunde
        // vor, und die Kruste eines stehenden Sees (Kīlauea Iki 1959) riss ständig auf.
        double qx = 0, qz = 0;
        for (int k = 0; k < 8; k++) {
            float q = out[k][i];
            if (q <= 0) continue;
            double w = k >= 4 ? 0.7071 : 1;
            qx += q * DI[k] * w;
            qz += q * DJ[k] * w;
        }
        // Ein Strom am Hang fächert sich auf drei Nachbarn auf; dafür der Faktor 1,35, höchstens die Summe [A].
        double u0 = Math.min(outSum[i], 1.35 * Math.hypot(qx, qz)) / (Math.max(hi, 0.2) * cell);
        // geglättet über rund eine Minute: kurze Stöße beim Aufbrechen der Front zählen nicht als Tempo
        double u = speed[i] + (u0 - speed[i]) * Math.min(1, dt / 60);
        speed[i] = (float) u;
        byte f = flags[i];
        boolean tube = (f & TUBE) != 0, sky = (f & SKY) != 0;
        age[i] += (float) dt;
        covered[i] += (float) dt;
        // Am Schlot brodelt die Lava: die Haut reißt ständig, ist aber nie ganz offen (wie 30 s alte Kruste) [G]
        if ((f & VENT_ON) != 0) age[i] = Math.min(age[i], VENT_AGE);
        else if (u > Rheology.U_BREAK && (!tube || sky)) age[i] = 0;
        double loss;
        boolean roofed = tube && !sky;
        if (roofed) {
            // unter dem Dach nur Wärmeleitung durch das Dach; es wächst auf der Unterseite weiter [A]
            double roof = Math.max(ROOF_START, Rheology.crust(age[i]));
            loss = Rheology.K_ROOF * (t - Rheology.T_AIR) / roof;
        } else {
            double ts = Rheology.T_AIR + (t - Rheology.T_AIR) * Rheology.surfaceFactor(age[i]);
            loss = Rheology.topLoss(ts);
        }
        if ((f & SEA) != 0 && base[i] + rock[i] < 0) loss += H_SEA * (t - 25);
        // in einer Röhre fließt die Lava über ihren eigenen, vorgewärmten Boden [A]
        loss += Rheology.baseLoss(t, covered[i]) * (tube ? TUBE_FLOOR : 1);
        double cap = Rheology.RHO * Rheology.C_EFF * hi;
        t = Math.max(Rheology.T_AIR, t - loss * dt / cap);
        temp[i] = t;
        // Pāhoehoe → ʻaʻā: Zähigkeit · Scherrate
        // (über Minuten anhaltend: ein kurzer Ruck reißt die Haut auf, macht aber noch kein ʻaʻā)
        if (aa[i] < 1 && (f & VENT) == 0 && t < Rheology.AA_TEMP && u >= Rheology.AA_SPEED && hi >= 0.2 && Rheology.viscosity(t) * u / hi > Rheology.AA_STRESS)
            aa[i] = (float) Math.min(1, aa[i] + dt / Rheology.AA_TIME);
        // Röhre: ruhige Pāhoehoe, dicke Kruste, darunter fließt es weiter
        if (!tube && aa[i] < 0.5 && u >= Rheology.U_TUBE && u <= Rheology.U_BREAK && Rheology.crust(age[i]) >= Rheology.ROOF_MIN) {
            f |= TUBE;
            if (hash(i) < SKY_CHANCE || slope(b, l, i) > SKY_SLOPE) f |= SKY;
            flags[i] = f;
        }
        // Röhre läuft leer: der Spiegel fällt deutlich unter den höchsten Stand, das Dach bleibt oben (Hohlraum)
        if (hi > peak[i]) peak[i] = (float) hi;
        if (tube && peak[i] > 0.5 && hi < DRAIN_SHARE * peak[i]) flags[i] |= DRAINED;
        if (t <= Rheology.T_SOLID) {
            rock[i] += hi;
            frozenTotal += hi * area;
            frozenAt[i] = (float) time;
            h[i] = 0;
            speed[i] = 0;
            // erstarrt: eine leergelaufene Röhre bleibt als Hohlraum unter dem Dach erhalten
            if ((flags[i] & DRAINED) == 0) flags[i] = (byte) (flags[i] & ~(TUBE | SKY));
            return false;
        }
        return true;
    }

    /** Größtes Gefälle des Untergrunds an der Zelle. */
    private double slope(int b, int l, int i) {
        double m = 0;
        for (int k = 0; k < 4; k++) {
            int j = neighbour(b, l, k);
            if (j >= 0) m = Math.max(m, Math.abs(base[j] - base[i]) / cell);
        }
        return m;
    }

    /** Fester Zufall je Zelle (0..1) aus Keim und Lage. */
    private double hash(int i) {
        int b = i / BB, l = i % BB;
        long x = (long) (bi[b] * B + l % B) * 0x9E3779B97F4A7C15L ^ (long) (bj[b] * B + l / B) * 0xC2B2AE3D27D4EB4FL ^ seed;
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return (x >>> 11) / (double) (1L << 53);
    }

    // ------------------------------------------------------------------ Abfragen

    public double time() { return time; }
    public long steps() { return steps; }
    public double lastDt() { return lastDt; }
    /** Ausgetreten (m³). */
    public double erupted() { return erupted; }
    /** Belegte Blöcke, Vorrat, ob er erschöpft ist. */
    public int blocksUsed() { return used; }
    public int pool() { return pool; }
    public boolean full() { return full; }
    public int awakeBlocks() { return awakeCount; }

    /** Zustand einer Stelle. */
    public static final class Probe {
        public boolean known;
        public double ground, rock, thickness, temperature, aa, speed, crustAge, arrival;
        public boolean tube, skylight, drained, sea;
    }

    /** Zustand an (x, z) in p schreiben; false, wenn dort nichts belegt ist. */
    public boolean probe(double x, double z, Probe p) {
        int i = indexAt(x, z);
        p.known = i >= 0;
        if (i < 0) return false;
        p.ground = base[i];
        p.rock = rock[i];
        p.thickness = h[i];
        p.temperature = h[i] > 0 ? temp[i] : Double.NaN;
        p.aa = aa[i];
        p.speed = speed[i];
        p.crustAge = age[i];
        p.arrival = arrival[i];
        p.tube = (flags[i] & TUBE) != 0;
        p.skylight = (flags[i] & SKY) != 0;
        p.drained = (flags[i] & DRAINED) != 0;
        p.sea = (flags[i] & SEA) != 0;
        return true;
    }

    /** Ein Blick auf alle bedeckten Zellen (Lava oder neues Gestein), für Prüfung und Karten. */
    public interface CellVisitor {
        void cell(double x, double z, double ground, double rock, double h, double t, double aa, double u, byte flags, double arrival);
    }

    public void forEachCovered(CellVisitor v) {
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if (h[i] <= 0 && rock[i] <= 0 && (flags[i] & DRAINED) == 0) continue;
                v.cell(ox + (bi[b] * B + l % B + 0.5) * cell, oz + (bj[b] * B + l / B + 0.5) * cell, base[i], rock[i], h[i],
                        h[i] > 0 ? temp[i] : Double.NaN, aa[i], speed[i], flags[i], arrival[i]);
            }
        }
    }

    /** Kennzahlen eines Laufs. */
    public static final class Stats {
        public double time, erupted, mobile, rock, area, activeArea, length, lowest, highest, meanT, minT, maxT, aaShare, roofArea, frontT;
        /** Ins Meer abgebrochen (m³, Phase 10). */
        public double collapsed;
        public int skylights, cells, seaCells, blocks;
        public boolean finite = true;

        @Override public String toString() {
            return String.format(Locale.GERMAN, "%.1f h: %,.0f m³ gefördert, %,.0f beweglich, %,.0f erstarrt · %.3f km² (davon %.3f fließend) · Länge %.2f km · "
                            + "Höhe %.0f…%.0f m · Kern %.0f…%.0f °C (Mittel %.0f, Front %.0f) · ʻaʻā %.0f %% · unter Dach %.1f ha, %d Oberlichter",
                    time / 3600, erupted, mobile, rock, area / 1e6, activeArea / 1e6, length / 1000, lowest, highest, minT, maxT, meanT, frontT, aaShare * 100,
                    roofArea / 1e4, skylights);
        }
    }

    /** Kennzahlen; Länge und Front gemessen ab (vx, vz). */
    public Stats stats(double vx, double vz) {
        Stats s = new Stats();
        s.time = time;
        s.erupted = erupted;
        s.collapsed = collapsed;
        s.blocks = used;
        s.lowest = Double.MAX_VALUE;
        s.highest = -Double.MAX_VALUE;
        s.minT = Double.MAX_VALUE;
        s.maxT = -Double.MAX_VALUE;
        double tw = 0, tsum = 0, aaA = 0, far = -1;
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                double hi = h[i], ri = rock[i];
                if (!Double.isFinite(hi) || !Double.isFinite(ri) || !Double.isFinite(temp[i])) s.finite = false;
                if (hi <= 0 && ri <= 0) continue;
                double x = ox + (bi[b] * B + l % B + 0.5) * cell, z = oz + (bj[b] * B + l / B + 0.5) * cell;
                s.cells++;
                s.area += area;
                s.mobile += hi * area;
                s.rock += ri * area;
                if ((flags[i] & SEA) != 0) s.seaCells++;
                double surf = base[i] + ri + hi;
                s.lowest = Math.min(s.lowest, surf);
                s.highest = Math.max(s.highest, surf);
                if (aa[i] >= 0.5) aaA += area;
                double d = Math.hypot(x - vx, z - vz);
                if (d > s.length) s.length = d;
                if (hi > 0) {
                    s.activeArea += area;
                    s.minT = Math.min(s.minT, temp[i]);
                    s.maxT = Math.max(s.maxT, temp[i]);
                    tsum += temp[i] * hi;
                    tw += hi;
                    if (d > far) far = d;
                    if ((flags[i] & TUBE) != 0) s.roofArea += area;
                    if ((flags[i] & SKY) != 0) s.skylights++;
                }
            }
        }
        // Front: Mittel der fließenden Zellen in den äußersten 60 m
        double fs = 0, fn = 0;
        if (far > 0)
            for (int b = 0; b < used; b++) {
                int o = b * BB;
                for (int l = 0; l < BB; l++) {
                    int i = o + l;
                    if (h[i] <= 0) continue;
                    double x = ox + (bi[b] * B + l % B + 0.5) * cell, z = oz + (bj[b] * B + l / B + 0.5) * cell;
                    if (Math.hypot(x - vx, z - vz) > far - 60) { fs += temp[i]; fn++; }
                }
            }
        s.frontT = fn > 0 ? fs / fn : Double.NaN;
        s.meanT = tw > 0 ? tsum / tw : Double.NaN;
        s.aaShare = s.area > 0 ? aaA / s.area : 0;
        if (s.cells == 0) { s.lowest = s.highest = Double.NaN; s.minT = s.maxT = Double.NaN; }
        return s;
    }

    /** Prüfsumme über den Zustand (für „gleicher Ablauf“). */
    public long fingerprint() {
        long x = 1469598103934665603L;
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if (h[i] <= 0 && rock[i] <= 0) continue;
                x = (x ^ Double.doubleToLongBits(h[i])) * 1099511628211L;
                x = (x ^ Double.doubleToLongBits(rock[i])) * 1099511628211L;
                x = (x ^ Double.doubleToLongBits(temp[i])) * 1099511628211L;
                x = (x ^ flags[i]) * 1099511628211L;
            }
        }
        return x;
    }

    /** Alles zurücksetzen (Gelände bleibt belegt, Lava und Gestein weg). */
    public void reset() {
        java.util.Arrays.fill(h, 0);
        java.util.Arrays.fill(rock, 0);
        java.util.Arrays.fill(age, 0);
        java.util.Arrays.fill(covered, 0);
        java.util.Arrays.fill(aa, 0);
        java.util.Arrays.fill(speed, 0);
        java.util.Arrays.fill(arrival, -1);
        java.util.Arrays.fill(peak, 0);
        java.util.Arrays.fill(frozenAt, 0);
        java.util.Arrays.fill(dirty, 0, used, true);
        for (int k = 0; k < flags.length; k++) flags[k] &= (byte) (SEA | VENT | VENT_ON);
        for (Vent v : vents) v.erupted = 0;
        time = erupted = frozenTotal = collapsed = 0;
        steps = 0;
    }

    /** Untergrund an einer Zelle (für Prüfungen). */
    public double ground(int i) { return base[i]; }

    // ------------------------------------------------------------------ Für die Grafik (Phase 8)

    /** Blöcke je Seite des Gitters über der Szene, Ursprung (Ecke der Zelle 0, 0). */
    public int side() { return side; }
    public double originX() { return ox; }
    public double originZ() { return oz; }
    /** Lage eines belegten Blocks im Gitter. */
    public int blockX(int b) { return bi[b]; }
    public int blockZ(int b) { return bj[b]; }
    /** Ändert sich, sobald ein Block belegt wird (dann muss die Tabelle neu auf die Grafikkarte). */
    public int tableVersion() { return tableVersion; }

    /** Hat sich der Block seit der letzten Ausgabe geändert? Löscht die Marke. */
    public boolean takeDirty(int b) {
        boolean d = dirty[b];
        dirty[b] = false;
        return d;
    }

    /**
     * Werte eines Blocks für die Grafik, je Zelle (Zeile für Zeile, 32 × 32):
     * <ul>
     * <li>a (4 je Zelle): Höhe der Oberfläche (m ü. M.), Dicke über dem Gelände (Gestein + Lava), bewegliche
     * Lava (m), Kerntemperatur (°C; bei erstarrtem Gestein die abklingende Innentemperatur, sonst 0)</li>
     * <li>c (4 je Zelle): Oberflächentemperatur (°C), ʻaʻā-Anteil, Merkmale ({@link #TUBE} …), Alter der Kruste (s)</li>
     * <li>v (2 je Zelle): Fließen nach x und z (m/s)</li>
     * </ul>
     * Das Gestein kühlt nach dem Erstarren weiter aus, innen mit einer Zeit nach seiner Dicke (Wärmeleitung,
     * d²/4κ, mindestens eine Stunde) [A]; die Oberfläche folgt wie bei der Kruste ({@link Rheology#surfaceFactor}).
     */
    public void exportBlock(int b, float[] a, float[] c, float[] v) {
        int o = b * BB;
        double tAir = Rheology.T_AIR;
        for (int l = 0; l < BB; l++) {
            int i = o + l;
            double hi = h[i], ri = rock[i], add = hi + ri;
            a[4 * l] = (float) (base[i] + add);
            a[4 * l + 1] = (float) add;
            a[4 * l + 2] = (float) hi;
            double core = 0, surf = tAir, ux = 0, uz = 0;
            byte f = flags[i];
            if (hi > 0) {
                core = temp[i];
                boolean roofed = (f & TUBE) != 0 && (f & SKY) == 0;
                surf = roofed ? tAir + 40 : tAir + (core - tAir) * Rheology.surfaceFactor(age[i]);
                double sx = 0, sz = 0;
                for (int k = 0; k < 8; k++) {
                    double q = out[k][i];
                    double len = (DI[k] != 0 && DJ[k] != 0) ? 0.7071 : 1;
                    sx += q * DI[k] * len;
                    sz += q * DJ[k] * len;
                }
                double sl = Math.hypot(sx, sz);
                if (sl > 1e-9) { ux = sx / sl * speed[i]; uz = sz / sl * speed[i]; }
            } else if (ri > 0.01) {
                double since = Math.max(0, time - frozenAt[i]);
                double tau = Math.max(3600, ri * ri / (4 * Rheology.KAPPA));
                core = tAir + (Rheology.T_SOLID - tAir) * Math.exp(-since / tau);
                surf = tAir + (core - tAir) * Rheology.surfaceFactor(age[i] + since);
            }
            a[4 * l + 3] = (float) core;
            c[4 * l] = (float) surf;
            c[4 * l + 1] = aa[i];
            c[4 * l + 2] = f;
            c[4 * l + 3] = age[i];
            v[2 * l] = (float) ux;
            v[2 * l + 1] = (float) uz;
        }
    }

    /** Bewegliche Lava oder Gestein dieses Laufs an (x, z), bilinear zwischen den Zellmitten (m); 0 außerhalb. */
    public double addedAt(double x, double z) {
        double u = (x - ox) / cell - 0.5, w = (z - oz) / cell - 0.5;
        int i0 = (int) Math.floor(u), j0 = (int) Math.floor(w);
        double fx = u - i0, fz = w - j0, s = 0;
        for (int dj = 0; dj < 2; dj++)
            for (int di = 0; di < 2; di++) {
                int i = index(i0 + di, j0 + dj);
                if (i < 0) continue;
                s += (h[i] + rock[i]) * (di == 0 ? 1 - fx : fx) * (dj == 0 ? 1 - fz : fz);
            }
        return s;
    }

    // ------------------------------------------------------------ Meereseintritt (Phase 10)

    /** Eine Stelle, an der heiße Lava im Wasser liegt. */
    public interface SeaContact {
        /** heat: Wärmestrom ins Wasser (W), t: Lava (°C), depth: Wassertiefe über der Lava (m), h: bewegliche Lava (m). */
        void at(double x, double z, double heat, double t, double depth, double h);
    }

    /** Alle Zellen, in denen bewegliche Lava im Meer oder auf dem neuen Ufer knapp über dem Spiegel liegt. */
    public void forEachSeaContact(SeaContact c) {
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if ((flags[i] & SEA) == 0 || h[i] < 0.005 || temp[i] < 600) continue;
                double top = base[i] + rock[i];
                if (top >= 0.5) continue;
                // unter Wasser schreckt die ganze Fläche ab; auf dem neuen Ufer nur der Rand, an dem die Wellen
                // die Lava erreichen (rund 5 % der Fläche) [G]
                double heat = H_SEA * (temp[i] - 25) * area * (top < 0 ? 1 : 0.05);
                c.at(ox + (bi[b] * B + l % B + 0.5) * cell, oz + (bj[b] * B + l / B + 0.5) * cell, heat, temp[i], -top, h[i]);
            }
        }
    }

    /** Ein Lavafall: Lava strömt über eine Stufe (Klippe, Rand des Deltas). */
    public interface Fall {
        /** Oberkante (x, y, z), Fuß (x1, y1, z1) — y1 ist an Wasser der Meeresspiegel —, Fluss (m³/s), Temperatur (°C). */
        void at(double x, double y, double z, double x1, double y1, double z1, double flux, double t);
    }

    /** Alle Abflüsse über eine Stufe von mehr als minDrop (m). */
    public void forEachFall(double minDrop, Fall f) {
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if (h[i] < 0.02 || outSum[i] <= 0 || temp[i] < 900) continue;
                double top = base[i] + rock[i] + h[i];
                for (int k = 0; k < 8; k++) {
                    double q = out[k][i];
                    if (q < 1e-3) continue;
                    int j = neighbour(b, l, k);
                    if (j < 0) continue;
                    double below = Math.max(base[j] + rock[j] + h[j], (flags[j] & SEA) != 0 ? 0 : -1e9);
                    if (top - below < minDrop) continue;
                    double x = ox + (bi[b] * B + l % B + 0.5) * cell, z = oz + (bj[b] * B + l / B + 0.5) * cell;
                    double len = (DI[k] != 0 && DJ[k] != 0) ? 0.7071 : 1;
                    f.at(x + DI[k] * cell * 0.5 * len, top, z + DJ[k] * cell * 0.5 * len,
                            x + DI[k] * cell * len, below, z + DJ[k] * cell * len, q, temp[i]);
                }
            }
        }
    }

    /** Fläche (m²) neuen Landes im Meer: Zellen, die vorher Meer waren und jetzt über den Spiegel ragen. */
    public double newLand() {
        double a = 0;
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if ((flags[i] & SEA) != 0 && base[i] < -0.5 && base[i] + rock[i] + h[i] > 0) a += area;
            }
        }
        return a;
    }

    /**
     * Ein Stück des Deltas bricht ab und rutscht ins Meer (Phase 10): alle neuen Zellen im Meer im Umkreis r um
     * (x, z), deren Grund tiefer als minBase liegt, verlieren ihr Gestein bis auf ein Viertel und ihre Lava.
     * Liefert das abgebrochene Volumen (m³); es zählt in {@link Stats#collapsed}.
     */
    public double collapse(double x, double z, double r, double minBase) {
        double v = 0;
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            boolean hit = false;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if ((flags[i] & SEA) == 0 || base[i] > minBase) continue;
                double cx = ox + (bi[b] * B + l % B + 0.5) * cell, cz = oz + (bj[b] * B + l / B + 0.5) * cell;
                if (Math.hypot(cx - x, cz - z) > r) continue;
                double keep = rock[i] * 0.25;
                v += (rock[i] - keep + h[i]) * area;
                rock[i] = keep;
                h[i] = 0;
                age[i] = 0;
                hit = true;
            }
            if (hit) { dirty[b] = true; wake(b); }
        }
        collapsed += v;
        return v;
    }

    /** Offene, heiße Lava (Phase 11, für Dampf über dem Strom): je Zelle mit einer Oberfläche über minSurf (°C). */
    public void forEachHot(double minSurf, SeaContact c) {
        for (int b = 0; b < used; b++) {
            int o = b * BB;
            for (int l = 0; l < BB; l++) {
                int i = o + l;
                if (h[i] < 0.02) continue;
                byte f = flags[i];
                if ((f & TUBE) != 0 && (f & SKY) == 0) continue;
                double ts = Rheology.T_AIR + (temp[i] - Rheology.T_AIR) * Rheology.surfaceFactor(age[i]);
                if (ts < minSurf) continue;
                c.at(ox + (bi[b] * B + l % B + 0.5) * cell, oz + (bj[b] * B + l / B + 0.5) * cell, area, ts, 0, h[i]);
            }
        }
    }
}
