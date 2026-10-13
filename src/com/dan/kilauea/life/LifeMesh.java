package com.dan.kilauea.life;

/**
 * Netz einer Figur für die Grafikkarte: je Punkt Lage, Normale, Farbe (linear), Teil und Drehpunkt.
 * Die Figur steht mit den Füßen (Rädern, Kufen) bei y = 0 und schaut nach +z; x zeigt nach links
 * (rechtshändig: y oben, z vorn). Flügel, Arme und Beine einer Seite erkennt der Shader am Vorzeichen
 * des Drehpunkts in x.
 * <p>Teile ({@link #BODY} …) bewegt der Shader: Flügel schlagen um die Längsachse, Beine und Arme
 * schwingen um die Querachse, der Hals neigt sich, Rotoren drehen sich, Lichter leuchten. Die Farbe
 * (1, 0, 1) ist ein Platzhalter für die Lackfarbe oder Jacke der einzelnen Figur.</p>
 */
public final class LifeMesh {
    public static final int BODY = 0, WING_L = 1, WING_R = 2, ROTOR = 3, TAIL_ROTOR = 4, LEG_L = 5, LEG_R = 6, NECK = 7,
            LIGHT = 8, ARM_L = 9, ARM_R = 10, HOT = 11, TAIL = 12;
    /** Farbe, die der Shader durch die Farbe der Figur ersetzt. */
    public static final float[] PAINT = {1, 0, 1};

    public final String name;
    public final float[] pos, nrm, col, pivot;
    public final int[] part, tri;
    /** Größe bei Maßstab 1: Länge (z), Breite (x), Höhe (y) in m. */
    public final float length, width, height;

    LifeMesh(String name, float[] pos, float[] nrm, float[] col, int[] part, float[] pivot, int[] tri) {
        this.name = name;
        this.pos = pos; this.nrm = nrm; this.col = col; this.part = part; this.pivot = pivot; this.tri = tri;
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (int i = 0; i < pos.length; i += 3) {
            x0 = Math.min(x0, pos[i]); x1 = Math.max(x1, pos[i]);
            y0 = Math.min(y0, pos[i + 1]); y1 = Math.max(y1, pos[i + 1]);
            z0 = Math.min(z0, pos[i + 2]); z1 = Math.max(z1, pos[i + 2]);
        }
        length = z1 - z0; width = x1 - x0; height = y1 - y0;
    }

    public int vertices() { return pos.length / 3; }
    public int triangles() { return tri.length / 3; }

    /** Punkte zu einem Teil (Prüfung). */
    public int count(int p) { int n = 0; for (int q : part) if (q == p) n++; return n; }

    /** Größte Ausdehnung eines Teils in x, y, z (Prüfung: Spannweite, Rotor). */
    public float[] extent(int p) {
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < part.length; i++) {
            if (part[i] != p) continue;
            for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], pos[3 * i + k]); hi[k] = Math.max(hi[k], pos[3 * i + k]); }
        }
        return new float[]{hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]};
    }

    @Override public String toString() { return name; }
}
