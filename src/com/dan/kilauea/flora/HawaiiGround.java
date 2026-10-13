package com.dan.kilauea.flora;

import com.dan.ground.Biome;
import com.dan.ground.Plant;
import com.dan.ground.Site;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;

/**
 * Bodendecke am Gipfel für {@code com.dan.ground}: Farne, Seggen, ʻōhelo und Lavabrocken, verteilt
 * nach denselben Masken wie Boden und Bäume [L Arten: Little &amp; Skolmen; NPS-Artenlisten; G Maße,
 * Farben]. Immergrün: Gras trocknet nicht, ʻōhelo trägt das ganze Jahr Beeren.
 */
public final class HawaiiGround {
    private HawaiiGround() { }

    /** ʻĀmaʻu (Sadleria cyatheoides): steife Wedel bis 1 m, junge rötlich; Pionier auf junger Lava. */
    static Plant amau() {
        Plant p = new Plant("ʻĀmaʻu", "Sadleria cyatheoides", Plant.Kind.GRASS);
        p.size = 0.75f; p.sizeVar = 0.35f; p.blades = 8; p.bladeWidth = 0.07f; p.spread = 0.08f; p.lean = 0.45f; p.stiffness = 1.3f;
        p.green = new float[]{0.055f, 0.095f, 0.030f}; p.dry = new float[]{0.12f, 0.05f, 0.03f}; p.winter = p.green;
        p.greenUp = 1; p.dryStart = 366; p.dryFull = 367;
        return p;
    }

    /** Uluhe (Dicranopteris linearis): gabelig verzweigter Farn, dichte gelbgrüne Matten. */
    static Plant uluhe() {
        Plant p = new Plant("Uluhe", "Dicranopteris linearis", Plant.Kind.GRASS);
        p.size = 0.5f; p.sizeVar = 0.3f; p.blades = 14; p.bladeWidth = 0.035f; p.spread = 0.14f; p.lean = 0.65f; p.stiffness = 0.8f;
        p.green = new float[]{0.075f, 0.120f, 0.030f}; p.dry = p.green; p.winter = p.green;
        p.greenUp = 1; p.dryStart = 366; p.dryFull = 367;
        return p;
    }

    /** Segge und Gras der offenen Flächen (Carex, Deschampsia): fein, graugrün bis strohfarben. */
    static Plant sedge() {
        Plant p = new Plant("Segge", "Carex wahuensis", Plant.Kind.GRASS);
        p.size = 0.35f; p.blades = 12; p.bladeWidth = 0.008f; p.spread = 0.06f; p.lean = 0.5f; p.stiffness = 0.7f;
        p.green = new float[]{0.10f, 0.11f, 0.05f}; p.dry = new float[]{0.20f, 0.17f, 0.09f}; p.winter = p.green;
        p.greenUp = 1; p.dryStart = 366; p.dryFull = 367;
        return p;
    }

    /** ʻŌhelo (Vaccinium reticulatum): niedriger Strauch mit roten Beeren, Pele heilig. */
    static Plant ohelo() {
        Plant p = new Plant("ʻŌhelo", "Vaccinium reticulatum", Plant.Kind.FLOWER);
        p.size = 0.32f; p.sizeVar = 0.4f; p.petals = 6; p.head = 0.012f;
        p.petal = new float[]{0.42f, 0.03f, 0.04f}; p.center = new float[]{0.30f, 0.02f, 0.03f};
        p.bloomStart = 1; p.bloomPeak = 120; p.bloomEnd = 360; p.stiffness = 1.6f;
        return p;
    }

    static Plant rubble() {
        Plant p = new Plant("Lavabrocken", "", Plant.Kind.STONE);
        p.size = 0.16f; p.sizeVar = 0.7f; p.color = new float[]{0.045f, 0.042f, 0.040f};
        return p;
    }

    static Plant block() {
        Plant p = new Plant("Lavablock", "", Plant.Kind.STONE);
        p.size = 0.34f; p.sizeVar = 0.5f; p.color = new float[]{0.050f, 0.046f, 0.042f};
        return p;
    }

    static Plant cinder() {
        Plant p = new Plant("Asche und Schlacke", "", Plant.Kind.SOIL);
        p.size = 1.4f; p.sizeVar = 0.5f; p.color = new float[]{0.10f, 0.09f, 0.075f};
        return p;
    }

    public static Biome biome() {
        return new Biome("Gipfel · Kīlauea")
                .add(uluhe(), 0, 6.0f, 1).add(amau(), 0, 1.6f, 0.3f).add(sedge(), 0, 4.0f, -0.6f)
                .add(ohelo(), 1, 1.8f, -0.2f)
                .add(rubble(), 2, 1.4f, 0).add(block(), 2, 0.08f, 0)
                .add(cinder(), 3, 0.3f, 0);
    }

    /** Wo was wächst: aus Bewuchs, Kronendach, Alter, ʻaʻā, Asche und Regen. */
    public static Site site() {
        return new Site() {
            final ThreadLocal<double[]> tmp = ThreadLocal.withInitial(() -> new double[4]);

            @Override public float height(double x, double z) { return (float) Ground.height(x, z); }

            @Override public void cover(double x, double z, float[] out) {
                HeightGrid g = Ground.grid();
                if (g == null) { java.util.Arrays.fill(out, 0); return; }
                double[] m = FloraField.masks(g, x, z, tmp.get());
                double veg = m[0], trees = m[1], rain = m[2], age = m[3];
                double ash = g.cover(x, z, 2), aa = g.geo(x, z, 1);
                out[0] = (float) (veg * (1 - 0.3 * trees));
                out[1] = (float) (veg * (1 - trees) * smooth(700, 1400, rain) * smooth(3500, 2200, rain) * smooth(15, 60, age));
                out[2] = (float) ((1 - veg) * (1 - ash) * (0.35 + 0.65 * aa));
                out[3] = (float) (ash * 0.8);
                out[4] = (float) Math.min(1, rain / 4000);
            }
        };
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
