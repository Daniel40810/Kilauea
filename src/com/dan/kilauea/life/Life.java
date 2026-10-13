package com.dan.kilauea.life;

import java.util.Arrays;

/**
 * Leben in der Szene (Phase 15), ohne Grafik: Nēnē, Waldvögel, Koaʻe kea und ʻIo, Hubschrauber, Wanderer
 * und Autos. Jede Gruppe lässt sich einzeln an- und ausschalten; ausgeschaltet rechnet sie nicht und
 * zeichnet nichts, ihre Figuren bleiben aber erhalten, so dass sie beim Einschalten sofort wieder da
 * sind (nichts wird neu geladen).
 * <p>Je Bild schreibt {@link #update} die Figuren in Eimer je Netz ({@link #inst}, 16 Zahlen je Figur:
 * Lage und Gierwinkel; Maßstab, Neigung, Rollen, Takt; Farbe; Flügelschlag, Schritt, Hals, Licht) und die
 * Schallquellen in {@link #emit}. Rufe (Nēnē, Vögel, ʻIo) gehen als Ereignisse in einen Ringpuffer.</p>
 */
public final class Life {
    public enum Group {
        NENE("Nēnē"), BIRDS("Vögel"), HELI("Hubschrauber"), HIKERS("Wanderer"), CARS("Autos");
        public final String label;
        Group(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public static final int K_NENE = 0, K_NENE_FLY = 1, K_APAPANE = 2, K_AMAKIHI = 3, K_OMAO = 4, K_MEJIRO = 5, K_TROPIC = 6, K_HAWK = 7,
            K_HELI = 8, K_HIKER = 9, HIKER_VARIANTS = 4, K_SEDAN = 13, K_SUV = 14, K_VAN = 15, KINDS = 16;
    public static final Group[] GROUP_OF = {Group.NENE, Group.NENE, Group.BIRDS, Group.BIRDS, Group.BIRDS, Group.BIRDS, Group.BIRDS, Group.BIRDS,
            Group.HELI, Group.HIKERS, Group.HIKERS, Group.HIKERS, Group.HIKERS, Group.CARS, Group.CARS, Group.CARS};
    /** Weiteste Entfernung zur Kamera, in der eine Figur gezeichnet wird (m) [G]. */
    static final double[] DRAW = {1500, 2500, 450, 450, 450, 450, 6000, 6000, 60000, 2500, 2500, 2500, 2500, 9000, 9000, 9000};
    public static final int F = 16;

    /** Das Netz zu einer Art. */
    public static LifeMesh mesh(int kind) {
        return switch (kind) {
            case K_NENE -> Shapes.nene();
            case K_NENE_FLY -> Shapes.neneFlying();
            case K_APAPANE, K_AMAKIHI, K_OMAO, K_MEJIRO -> Shapes.songbird(kind - K_APAPANE);
            case K_TROPIC -> Shapes.tropicbird();
            case K_HAWK -> Shapes.hawk();
            case K_HELI -> Shapes.helicopter();
            case K_SEDAN, K_SUV, K_VAN -> Shapes.car(kind - K_SEDAN);
            default -> Shapes.hiker(kind - K_HIKER);
        };
    }

    // ------------------------------------------------------------------ Schall
    /** Quellen: Art, x, y, z, vx, vy, vz, Stärke. */
    public static final int E_HELI = 1, E_CAR = 2, E_STEP = 3;
    public final float[] emit = new float[8 * 96];
    public int emitCount;
    /** Rufe: Art, x, y, z, Wanduhr-Sekunde; Arten {@link #C_NENE} … */
    public static final int C_NENE = 1, C_NENE_FLIGHT = 2, C_APAPANE = 3, C_AMAKIHI = 4, C_OMAO = 5, C_MEJIRO = 6, C_IO = 7, C_TROPIC = 8;
    private final double[] calls = new double[5 * 256];
    private long callSeq;

    /** Einen Ruf melden. */
    void call(int type, double x, double y, double z) {
        int i = (int) (callSeq % 256);
        calls[5 * i] = type; calls[5 * i + 1] = x; calls[5 * i + 2] = y; calls[5 * i + 3] = z; calls[5 * i + 4] = clock;
        callSeq++;
    }

    /** Zahl der Rufe seit Beginn (zum Abholen neuer Rufe). */
    public synchronized long callSeq() { return callSeq; }

    /** Ruf Nummer seq (Art, x, y, z, Zeit) in out; false, wenn er schon überschrieben ist. */
    public synchronized boolean callAt(long seq, double[] out) {
        if (seq >= callSeq || seq < callSeq - 256) return false;
        System.arraycopy(calls, 5 * (int) (seq % 256), out, 0, 5);
        return true;
    }

    void source(int type, double x, double y, double z, double vx, double vy, double vz, double level) {
        if (emitCount >= emit.length / 8) return;
        int i = 8 * emitCount++;
        emit[i] = type; emit[i + 1] = (float) x; emit[i + 2] = (float) y; emit[i + 3] = (float) z;
        emit[i + 4] = (float) vx; emit[i + 5] = (float) vy; emit[i + 6] = (float) vz; emit[i + 7] = (float) level;
    }

    // ------------------------------------------------------------------ Figuren
    public final float[][] inst = new float[KINDS][];
    public final int[] count = new int[KINDS];
    private final boolean[] on = new boolean[Group.values().length];
    private double cx, cy, cz;
    /** Wanduhr der Figuren (s). */
    double clock;

    public final Nene nene;
    public final Birds birds;
    public final Helicopters helis;
    public final Hikers hikers;
    public final Cars cars;

    public Life(long seed) {
        Arrays.fill(on, true);
        for (int k = 0; k < KINDS; k++) inst[k] = new float[F * 32];
        nene = new Nene(this, seed);
        birds = new Birds(this, seed + 1);
        helis = new Helicopters(this, seed + 2);
        hikers = new Hikers(this, seed + 3);
        cars = new Cars(this, seed + 4);
    }

    public synchronized boolean on(Group g) { return on[g.ordinal()]; }

    /** Gruppe an oder aus; wirkt mit dem nächsten Bild, ohne dass etwas neu geladen wird. */
    public synchronized void set(Group g, boolean v) { on[g.ordinal()] = v; }

    /** Alle Figuren und Quellen für dieses Bild rechnen. */
    public synchronized void update(double dt, World w) {
        clock += dt;
        cx = w.camX; cy = w.camY; cz = w.camZ;
        Arrays.fill(count, 0);
        emitCount = 0;
        if (com.dan.kilauea.terrain.Ground.grid() == null) return;
        if (on[Group.NENE.ordinal()]) nene.update(dt, w);
        if (on[Group.BIRDS.ordinal()]) birds.update(dt, w);
        if (on[Group.HELI.ordinal()]) helis.update(dt, w);
        if (on[Group.HIKERS.ordinal()]) hikers.update(dt, w);
        if (on[Group.CARS.ordinal()]) cars.update(dt, w);
    }

    /** Eine Figur in ihren Eimer legen (zu weit weg: nicht zeichnen). */
    void put(int kind, double x, double y, double z, double yaw, double scale, double pitch, double roll, double phase,
             float[] col, double wing, double gait, double neck, double light) {
        double dx = x - cx, dy = y - cy, dz = z - cz, d = DRAW[kind];
        if (dx * dx + dy * dy + dz * dz > d * d) return;
        int n = count[kind];
        if ((n + 1) * F > inst[kind].length) inst[kind] = Arrays.copyOf(inst[kind], inst[kind].length * 2);
        float[] a = inst[kind];
        int i = n * F;
        a[i] = (float) x; a[i + 1] = (float) y; a[i + 2] = (float) z; a[i + 3] = (float) yaw;
        a[i + 4] = (float) scale; a[i + 5] = (float) pitch; a[i + 6] = (float) roll; a[i + 7] = (float) (phase % 6283.185307179586);
        a[i + 8] = col[0]; a[i + 9] = col[1]; a[i + 10] = col[2]; a[i + 11] = 0;
        a[i + 12] = (float) wing; a[i + 13] = (float) gait; a[i + 14] = (float) neck; a[i + 15] = (float) light;
        count[kind] = n + 1;
    }

    /** Vorführung: ein Rundflug-Hubschrauber startet sofort (außerhalb des Plans). */
    public synchronized void demoHelicopter(World w) { helis.demo(w); }

    /** Die Hubschrauber in der Luft (Prüfung). */
    public synchronized java.util.List<Helicopters.Heli> helicopters() { return helis.flying(); }

    /** Figuren je Gruppe im Bild (Prüfung, Bedienfeld). */
    public synchronized int shown(Group g) {
        int n = 0;
        for (int k = 0; k < KINDS; k++) if (GROUP_OF[k] == g) n += count[k];
        return n;
    }

    /** Zeile für das Bedienfeld. */
    public synchronized String summary(World w) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(java.util.Locale.GERMAN, "Im Bild: %d Nēnē, %d Vögel, %d Hubschrauber, %d Wanderer, %d Autos",
                shown(Group.NENE), shown(Group.BIRDS), shown(Group.HELI), shown(Group.HIKERS), shown(Group.CARS)));
        b.append(" · ").append(helis.status(w));
        return b.toString();
    }

    static float[] rgb(double r, double g, double b) { return new float[]{(float) r, (float) g, (float) b}; }
}
