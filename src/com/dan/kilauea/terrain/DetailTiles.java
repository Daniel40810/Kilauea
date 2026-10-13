package com.dan.kilauea.terrain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Alle Detailkacheln (Phase 4 und 5) und ihre Formen. Lagen [L] nach Karten und Berichten von USGS und
 * NPS sowie nach den Höhendaten (Gruben, die das 75-m-Raster zeigt); Maße [L], wo sie belegt sind,
 * sonst [G].
 * <ul>
 * <li><b>Gipfel</b> ({@link SummitTile}), 4 m.</li>
 * <li><b>Kette der Krater</b>, 6 m: Hiʻiaka, Pauahi, Mauna Ulu, Makaopuhi und Nāpau entlang der
 * oberen Ostriftzone (Lua Manu, Puhimau und Koʻokoʻolau liegen noch in der Gipfelkachel, die sie mit schneidet).</li>
 * <li><b>Puʻu ʻŌʻō</b>, 4 m: Wände des 2018 eingestürzten Kraters.</li>
 * <li><b>Spalten von 2018 und Kapoho</b>, 6 m: Reihe der Spalten 1 bis 24 in Leilani Estates, Kegel der
 * Spalte 8 (Ahuʻailāʻau), sein Lavakanal bis in die Kapoho-Bucht, Klippe am neuen Land.</li>
 * <li><b>Südküste</b>, 8 m: Hilina Pali und Hōlei Pali als Steilstufen, Meeresklippe von Kaʻaha bis
 * Kamokuna.</li>
 * </ul>
 */
public final class DetailTiles {
    private DetailTiles() { }

    private static volatile List<DetailTile> tiles = List.of();

    public static List<DetailTile> all() { return tiles; }

    public static void use(List<DetailTile> t) { tiles = List.copyOf(t); }

    /** Unterschied aller Kacheln an (x, z). */
    public static double deltaAt(double x, double z) {
        double d = 0;
        for (DetailTile t : tiles) if (t.spec.inside(x, z)) d += t.at(x, z);
        return d;
    }

    /** Die Kachel mit der Kennung id (oder null). */
    public static DetailTile get(String id) {
        for (DetailTile t : tiles) if (t.spec.id.equals(id)) return t;
        return null;
    }

    /** Lädt oder baut alle Kacheln; step bekommt den Namen der Kachel, die gerade dran ist. */
    public static List<DetailTile> load(File projectDir, HeightGrid g, java.util.function.Consumer<String> step) {
        List<DetailTile> out = new ArrayList<>();
        for (DetailTile.Spec s : specs(g)) {
            if (step != null) step.accept(s.name);
            out.add(DetailTile.load(projectDir, g, s));
        }
        return out;
    }

    public static List<DetailTile.Spec> specs(HeightGrid g) {
        return List.<DetailTile.Spec>of(new SummitTile(g), new Chain(g), new PuuOo(), new Puna2018(), new SouthCoast());
    }

    // ------------------------------------------------------------------ Kette der Krater

    /** Grubenkrater entlang der Chain of Craters Road [Lage L/G, Tiefe L wo angegeben]. */
    public static final DetailTile.Pit[] CHAIN_PITS = {
            new DetailTile.Pit("Lua Manu", 19.3970, -155.2497, 105, 85, 20, 45),
            new DetailTile.Pit("Puhimau", 19.3932, -155.2485, 170, 150, 20, 150),
            new DetailTile.Pit("Koʻokoʻolau", 19.3868, -155.2420, 120, 95, 25, 50),
            new DetailTile.Pit("Hiʻiaka", 19.3744, -155.2297, 260, 150, 25, 75),
            new DetailTile.Pit("Pauahi", 19.3686, -155.2220, 305, 200, 20, 91),
            new DetailTile.Pit("Mauna Ulu", 19.3672, -155.2022, 150, 120, 30, 55),
            new DetailTile.Pit("Makaopuhi", 19.3657, -155.1700, 800, 500, 10, 95),
    };

    static final class Chain extends DetailTile.Spec {
        private final double[] rims;

        Chain(HeightGrid g) {
            super("kette", "Kette der Krater", 19.355, -155.262, 19.3790, -155.135, 6, 3,
                    new DetailTile.Sharpen(20, 8, 20, 50, 0.12, 0.3, 0.34, 0.66, 0.15, 0.06, 0.3), false);
            rims = new double[CHAIN_PITS.length];
            for (int i = 0; i < rims.length; i++) {
                var p = CHAIN_PITS[i];
                rims[i] = g == null ? 0 : DetailTile.rim(g, p) + shield(p.x() + p.ra() * 1.25, p.z());
            }
        }

        @Override public double pre(double x, double z, double b) {
            double h = b + shield(x, z);
            for (int i = 0; i < CHAIN_PITS.length; i++) h = DetailTile.carve(CHAIN_PITS[i], x, z, h, rims[i]);
            return h;
        }

        /**
         * Schild des Mauna Ulu: 1969–74 wuchs er bis 121 m über das alte Gelände [L, USGS]; das
         * geglättete 75-m-Raster zeigt nur rund 50 m, darum hier bis 45 m dazu, flach auslaufend [G].
         */
        static double shield(double x, double z) {
            var p = CHAIN_PITS[5];
            double r = Math.hypot(x - p.x(), z - p.z()) * (1 + 0.1 * (Noise.fbm(x * 0.003, z * 0.003, 3) - 0.5));
            return 45 * Math.pow(Island.smooth(1100, 120, r), 1.4);
        }

        @Override public float[] exempt() { return SummitTile.exemptMask(this, List.<double[][]>of(DetailTile.xz(ParkRoads.CHAIN_OF_CRATERS)), 10, 30); }
    }

    // ------------------------------------------------------------------ Puʻu ʻŌʻō

    static final class PuuOo extends DetailTile.Spec {
        PuuOo() {
            super("puuoo", "Puʻu ʻŌʻō", 19.370, -155.125, 19.405, -155.085, 4, 2,
                    new DetailTile.Sharpen(25, 10, 25, 60, 0.15, 0.35, 0.38, 0.62, 0.2, 0.08, 0.5), false);
        }
    }

    // ------------------------------------------------------------------ Spalten von 2018

    /** Kegel der Spalte 8 (Ahuʻailāʻau): Mitte, Fuß-Halbmesser 230 m, Höhe 55 m [L USGS], Krater 70 m, offen nach Nordosten zum Kanal [L]. */
    public static final double[] FISSURE8 = {19.4625, -154.9085};
    /** Spalten 1 bis 24 als Linie durch Leilani Estates [G nach USGS-Karte]. */
    public static final double[][] FISSURE_LINE = {{19.4552, -154.9310}, {19.4590, -154.9200}, {19.4625, -154.9085}, {19.4668, -154.8990}, {19.4720, -154.8890}, {19.4785, -154.8780}};
    /** Lavakanal der Spalte 8 nach Nordosten und Osten bis in die Kapoho-Bucht [G nach USGS-Karte]. */
    public static final double[][] CHANNEL = {
            {19.4650, -154.9058}, {19.4705, -154.8990}, {19.4762, -154.8905}, {19.4800, -154.8805}, {19.4860, -154.8700},
            {19.4930, -154.8600}, {19.5000, -154.8480}, {19.5080, -154.8360}, {19.5140, -154.8250}, {19.5170, -154.8180}};

    static final double[][] FISSURE_XZ = DetailTile.xz(FISSURE_LINE), CHANNEL_XZ = DetailTile.xz(CHANNEL);

    /** Kegel der Spalte 8, Rampen der Spalten und Kanal mit Dämmen (Zuschlag in m). */
    static double eruption2018(double x, double z) {
        double add = 0;
        // Kegel
        double cx = Geo.x(FISSURE8[0], FISSURE8[1]), cz = Geo.z(FISSURE8[0]);
        double dx = x - cx, dz = z - cz, r = Math.hypot(dx, dz);
        if (r < 300) {
            double az = Math.toDegrees(Math.atan2(dx, -dz));                 // 0 = Norden, im Uhrzeigersinn
            double open = Island.smooth(42, 22, Math.abs(((az - 45) % 360 + 540) % 360 - 180));   // Lücke nach NO
            double rr = r * (1 + 0.08 * (Noise.fbm(x * 0.02, z * 0.02, 3) - 0.5));
            double cone = 55 * Math.pow(Island.smooth(230, 80, rr), 1.3);
            double crater = 47 * Island.smooth(95, 55, rr);
            add += Math.max(0, cone - crater) * (1 - 0.92 * open) + 8 * Island.smooth(95, 40, rr);
        }
        // Spalten: niedrige Rampen aus Schlacke mit einem Schlitz in der Mitte, mit Lücken
        double[] fd = DetailTile.lineDist(FISSURE_XZ, x, z);
        if (fd[0] < 40) {
            double gap = Island.smooth(0.30, 0.48, Noise.value(fd[1] * 60, 2.5));
            double hgt = 4 + 4 * Noise.value(fd[1] * 25, 7.1);
            add += (hgt * Island.smooth(28, 6, fd[0]) - 3.5 * Island.smooth(3.5, 1.0, fd[0])) * gap;
        }
        // Kanal: Dämme beidseits, Rinne dazwischen; zur Küste breiter und flacher
        double[] cd = DetailTile.lineDist(CHANNEL_XZ, x, z);
        if (cd[0] < 160) {
            double w = 35 + 45 * cd[1], lev = 7 * (1 - 0.6 * cd[1]);
            double levee = lev * Math.exp(-Math.pow((cd[0] - w) / 18, 2));
            double bed = -2.5 * Island.smooth(w * 0.9, w * 0.6, cd[0]);
            add += (levee + bed) * Island.smooth(0, 0.03, cd[1]);
        }
        return add;
    }

    static final class Puna2018 extends DetailTile.Spec {
        Puna2018() {
            super("puna2018", "Spalten von 2018 und Kapoho", 19.450, -154.935, 19.535, -154.800, 6, 2,
                    new DetailTile.Sharpen(20, 8, 25, 60, 0.15, 0.35, 0.40, 0.60, 0.2, 0.06, 0.5), true);
            edge = 250;
        }

        @Override public double post(double x, double z, double h, double base) { return h > 0 ? h + eruption2018(x, z) : h; }
    }

    // ------------------------------------------------------------------ Südküste

    static final class SouthCoast extends DetailTile.Spec {
        SouthCoast() {
            super("suedkueste", "Südküste", 19.270, -155.370, 19.335, -155.030, 8, 2,
                    new DetailTile.Sharpen(25, 10, 60, 150, 0.25, 0.5, 0.35, 0.65, 0.25, 0.08, 0.7), true);
            edge = 400;
        }

        @Override public float[] exempt() { return SummitTile.exemptMask(this, List.<double[][]>of(DetailTile.xz(ParkRoads.CHAIN_OF_CRATERS)), 14, 40); }

        /** Klippe 12 bis 28 m [G], am Felsbogen Hōlei rund 25 m (der Bogen war 27 m hoch [L]). */
        @Override public double cliffTop(double x, double z) {
            double c = 12 + 16 * Noise.fbm(x * 0.0006 + 2, z * 0.0006, 3);
            double ax = Geo.x(SeaArch.LAT, SeaArch.LON), az = Geo.z(SeaArch.LAT);
            return Math.max(c, 25 * Island.smooth(900, 300, Math.hypot(x - ax, z - az)));
        }
    }
}
