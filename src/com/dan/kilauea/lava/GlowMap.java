package com.dan.kilauea.lava;

/**
 * Lichtschein der Lava auf die Umgebung, als grobe Karte über dem Gebiet der Lava (Phase 8): je Feld der
 * Karte die Bestrahlungsstärke (RGB, Engine-Einheiten) in rund 12 m Höhe, die von allen glühenden Flächen
 * und Fontänen dort ankommt. Gerechnet im Lava-Thread, etwa zweimal je Sekunde; der Gelände-Shader liest
 * sie und färbt Gestein, Wände und Bäume von unten orange.
 * <p>
 * Jede Zelle strahlt wie im Shader: Kruste mit ihrer Oberflächentemperatur, dazwischen offene Risse mit der
 * Kerntemperatur ({@link #crackShare}). Die Abstrahlung einer Zelle ist π · L · Fläche; am Empfänger kommt
 * P / (4π (d² + h²)) an (Kugelstrahler in h über dem Boden) [A, eine grobe Näherung; Abschattung durch
 * Gelände fehlt].
 */
public final class GlowMap {
    public static final int G = 96;
    /** Höhe der Empfänger über der Lava (m) [G]. */
    static final double HZ = 12;

    /** Karte: x0, z0 (Welt, m), Seite (m); RGB je Feld. */
    public double x0, z0, size;
    public final float[] rgb = new float[G * G * 3];
    public volatile int version;
    public boolean empty = true;

    private final float[] src = new float[G * G * 3];
    private final float[] a = new float[4 * LavaField.BB], c = new float[4 * LavaField.BB], v = new float[2 * LavaField.BB];
    private final double[] col = new double[3], col2 = new double[3];

    /**
     * Anteil offener Risse nach dem Verhältnis e von Oberfläche zu Kern (beide über der Luft) [G]: frische
     * Lava (e über 0,8) ist fast ganz offen, nach rund einer Minute (e um 0,4) sind es ein, zwei Prozent,
     * alte Kruste hat nur noch feine glühende Fugen (0,3 %).
     */
    public static double crackShare(double surf, double core) {
        if (core <= Rheology.T_AIR + 1) return 0;
        double e = Math.max(0, Math.min(1, (surf - Rheology.T_AIR) / (core - Rheology.T_AIR)));
        double t = Math.max(0, Math.min(1, (e - 0.2) / 0.7));
        double s = t * t * (3 - 2 * t);
        return 0.003 + 0.997 * Math.pow(s, 2.5);
    }

    /**
     * Neu rechnen. Das Feld (darf null sein) muss gesperrt sein. Dazu punktförmige Quellen: je
     * {x, z, Höhe über dem Empfänger (m), Leistung R, G, B} (Fontäne, Lavasee).
     */
    public void update(LavaField f, java.util.List<double[]> points) {
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double bs = LavaField.B * (f != null ? f.cell : 10);
        if (f != null)
            for (int b = 0; b < f.blocksUsed(); b++) {
                double bx = f.originX() + f.blockX(b) * bs, bz = f.originZ() + f.blockZ(b) * bs;
                minX = Math.min(minX, bx); minZ = Math.min(minZ, bz);
                maxX = Math.max(maxX, bx + bs); maxZ = Math.max(maxZ, bz + bs);
            }
        for (double[] q : points) {
            minX = Math.min(minX, q[0]); minZ = Math.min(minZ, q[1]);
            maxX = Math.max(maxX, q[0]); maxZ = Math.max(maxZ, q[1]);
        }
        java.util.Arrays.fill(rgb, 0);
        java.util.Arrays.fill(src, 0);
        if (minX > maxX) { empty = true; version++; return; }
        double side = Math.max(maxX - minX, maxZ - minZ) + 1200;
        size = side;
        x0 = 0.5 * (minX + maxX) - side / 2;
        z0 = 0.5 * (minZ + maxZ) - side / 2;
        double cs = side / G;
        // Quellen der Lava sammeln
        if (f != null)
            for (int b = 0; b < f.blocksUsed(); b++) {
                f.exportBlock(b, a, c, v);
                double bx = f.originX() + f.blockX(b) * bs, bz = f.originZ() + f.blockZ(b) * bs;
                for (int l = 0; l < LavaField.BB; l++) {
                    double core = a[4 * l + 3], surf = c[4 * l];
                    if (surf < 520 && core < 520) continue;
                    int fl = (int) c[4 * l + 2];
                    boolean roofed = (fl & LavaField.TUBE) != 0 && (fl & LavaField.SKY) == 0;
                    double share = roofed ? 0 : crackShare(surf, core);
                    if ((fl & LavaField.SKY) != 0) share = Math.max(share, 0.12);   // das Loch im Dach
                    Glow.radiance(surf, col);
                    Glow.radiance(core, col2);
                    int gi = (int) ((bx + (l % LavaField.B + 0.5) * f.cell - x0) / cs), gj = (int) ((bz + (l / LavaField.B + 0.5) * f.cell - z0) / cs);
                    if (gi < 0 || gj < 0 || gi >= G || gj >= G) continue;
                    double p = Math.PI * f.area;
                    int k = 3 * (gj * G + gi);
                    for (int ch = 0; ch < 3; ch++) src[k + ch] += (float) (p * (col[ch] * (1 - share) + col2[ch] * share));
                }
            }
        int nz = 0;
        int[] idx = new int[G * G];
        for (int k = 0; k < G * G; k++) if (src[3 * k] > 0) idx[nz++] = k;
        for (int j = 0; j < G; j++)
            for (int i = 0; i < G; i++) {
                double rx = x0 + (i + 0.5) * cs, rz = z0 + (j + 0.5) * cs;
                double r = 0, g = 0, bl = 0;
                for (int q = 0; q < nz; q++) {
                    int k = idx[q];
                    double dx = (k % G - i) * cs, dz = (k / G - j) * cs;
                    double w = 1 / (4 * Math.PI * (dx * dx + dz * dz + HZ * HZ + cs * cs * 0.25));
                    r += src[3 * k] * w;
                    g += src[3 * k + 1] * w;
                    bl += src[3 * k + 2] * w;
                }
                for (double[] p : points) {
                    double dx = rx - p[0], dz = rz - p[1];
                    double w = 1 / (4 * Math.PI * (dx * dx + dz * dz + p[2] * p[2]));
                    r += p[3] * w;
                    g += p[4] * w;
                    bl += p[5] * w;
                }
                int k = 3 * (j * G + i);
                rgb[k] = (float) r;
                rgb[k + 1] = (float) g;
                rgb[k + 2] = (float) bl;
            }
        empty = nz == 0 && points.isEmpty();
        version++;
    }

    /** Punktquelle einer glühenden Fläche (m²) bei der Temperatur t (°C), h über den Empfängern. */
    public static double[] point(double x, double z, double h, double area, double t) {
        double[] c = new double[3];
        Glow.radiance(t, c);
        double p = Math.PI * area;
        return new double[]{x, z, h, c[0] * p, c[1] * p, c[2] * p};
    }
}
