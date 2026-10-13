package com.dan.kilauea.lava;

/**
 * Bild des Lavasees für die Grafik (Phase 8), gerechnet im Lava-Thread: je Bildpunkt die nächste Platte
 * (Voronoi über die Plattenmitten) und der Abstand zur Naht. Kanäle (je 0..255):
 * R Glut der Naht (1 in der Naht, breiter, wo sie sich öffnet), G Alter der Kruste (√(Alter/2 h)),
 * B Zufall der Platte, A 255 auf dem See.
 * Dazu die jüngsten Spritzer (x, z, Alter s, Stärke).
 */
public final class LakeImage {
    public static final int N = 256;
    public final byte[] rgba = new byte[N * N * 4];
    /** Ausschnitt in der Szene: x0, z0, Seite (m). */
    public double x0, z0, size;
    public final float[] spatter = new float[16 * 4];
    public int spatterCount;
    /** Anteil der Nähte an der Seefläche (für den Lichtschein). */
    public double seamShare;
    public volatile int version;

    private final double[] u = new double[LavaLake.MAX], v = new double[LavaLake.MAX], ag = new double[LavaLake.MAX], wu = new double[LavaLake.MAX], wv = new double[LavaLake.MAX];
    private final int[] id = new int[LavaLake.MAX];

    /** Neu rechnen; der See muss gesperrt sein. */
    public void update(LavaLake L) {
        size = 2.25 * L.a;
        x0 = L.cx - size / 2;
        z0 = L.cz - size / 2;
        int n = L.plates();
        for (int i = 0; i < n; i++) {
            u[i] = L.plateU(i);
            v[i] = L.plateV(i);
            ag[i] = L.plateAge(i);
        }
        double step = size / N;
        long seam = 0, inside = 0;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int k = 4 * (j * N + i);
                double x = x0 + (i + 0.5) * step, z = z0 + (j + 0.5) * step;
                double lu = L.toU(x, z), lv = L.toV(x, z);
                if (L.shape(lu, lv) >= 1.07) { rgba[k] = rgba[k + 1] = rgba[k + 2] = rgba[k + 3] = 0; continue; }
                int b1 = -1, b2 = -1;
                double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
                for (int p = 0; p < n; p++) {
                    double du = u[p] - lu, dv = v[p] - lv, d = du * du + dv * dv;
                    if (d < d1) { d2 = d1; b2 = b1; d1 = d; b1 = p; } else if (d < d2) { d2 = d; b2 = p; }
                }
                double glow = 0, age = 3600;
                int pid = 0;
                if (b1 >= 0) {
                    double gap = Math.sqrt(d2) - Math.sqrt(d1);
                    double open = b2 >= 0 ? L.seamOpening(b1, b2) : 0;
                    if (Double.isNaN(open)) open = 0;
                    double width = 0.9 + Math.max(0, open) * 90;      // breiter, wo sich die Naht öffnet [G]
                    glow = Math.max(0, 1 - gap / width);
                    age = ag[b1];
                    pid = (b1 * 73 + 17) & 255;
                }
                if (glow > 0.5) seam++;
                inside++;
                rgba[k] = (byte) Math.round(glow * 255);
                rgba[k + 1] = (byte) Math.round(Math.min(1, Math.sqrt(age / 7200)) * 255);
                rgba[k + 2] = (byte) pid;
                rgba[k + 3] = (byte) 255;
            }
        seamShare = inside > 0 ? seam / (double) inside : 0;
        int m = 0;
        for (int s = 0; s < L.spatterCount() && m < 16; s++) {
            double a = L.time() - L.spatterTime(s);
            if (a > 25) break;
            spatter[4 * m] = (float) L.spatterX(s);
            spatter[4 * m + 1] = (float) L.spatterZ(s);
            spatter[4 * m + 2] = (float) a;
            spatter[4 * m + 3] = (float) L.spatterStrength(s);
            m++;
        }
        spatterCount = m;
        version++;
    }
}
