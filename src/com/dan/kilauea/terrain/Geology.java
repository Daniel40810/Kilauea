package com.dan.kilauea.terrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Gestein und Grün auf dem Höhenraster: Alter und Art der Lava, Regen, Bewuchs.
 * <ol>
 * <li><b>Ströme mit Jahr</b> ({@link #FLOWS}): 40 datierte Ströme von 1801 bis 2024 (Kīlauea 20, Mauna Loa 18, Hualālai 2).
 * Jahr, Schlot und Länge nach USGS-Chroniken [L, Lagen auf rund 1 km genähert]; der Weg entsteht, indem
 * mehrere Spuren vom Schlot bergab laufen (steilster Abstieg mit Schwung, wo nötig über bekannte
 * Wegpunkte) und eine Breite auf das Raster malen [A]. Jüngere Ströme decken ältere.</li>
 * <li><b>Grundalter</b> außerhalb der Ströme je Vulkan: Kīlauea meist 200–1 500 Jahre, Mauna Loa
 * 300–8 000, Hualālai 500–5 000, Mauna Kea 4 000–65 000, Kohala über 120 000 [L, als Spanne; das Muster
 * ist gestaltet: Rauschen, das vom Gipfel weg gestreckt ist wie Stromzungen].</li>
 * <li><b>Regen</b> aus dem Passat: Feuchte zieht von Ostnordost über die Insel, regnet an Hängen
 * bergauf aus, über der Passatinversion (rund 2 000 m) kaum noch; im Lee trocken (Kaʻū-Wüste), dazu der
 * Kona-Gürtel am Westhang [A, abgestimmt auf den Rainfall Atlas of Hawaiʻi: Hilo-Hang bis 6 000 mm,
 * Gipfel Kīlauea rund 2 500 mm, Kaʻū unter 1 000 mm].</li>
 * <li><b>Bewuchs</b> aus Alter und Regen (Sukzession): unter 5 Jahren kahl, dann Flechten
 * (<i>Stereocaulon</i>, hellgrau) und Farne, später ʻōhiʻa, nach Jahrhunderten Regenwald mit
 * Baumfarnen; im Trockenen Gras und Büsche, über rund 2 800 m kahle Schlacke [A].</li>
 * </ol>
 * Ergebnis je Zelle in zwei RGBA-Bytes:
 * cover = Bewuchs, Baumanteil, Asche/Tephra, Küste; geo = Alterscode, ʻaʻā-Anteil, Flechten, Regen.
 */
public final class Geology {
    private Geology() { }

    public static final int NOW = 2026;
    /** Alterscode 0..255 ↔ Jahre (logarithmisch bis 300 000 Jahre). */
    public static final double AGE_MAX = 300_000;
    public static final double RAIN_MAX = 8000;

    public static int ageCode(double years) {
        return (int) Math.round(255 * Math.log1p(Math.max(0, years)) / Math.log1p(AGE_MAX));
    }

    public static double ageOf(double code) { return Math.expm1(code / 255.0 * Math.log1p(AGE_MAX)); }

    // ------------------------------------------------------------------ Katalog

    /**
     * Ein Ausbruch: Name, Jahr, Spalte (Breite, Länge … von oben nach unten), Länge des Stroms (km),
     * Breite (m), ʻaʻā-Anteil am Schlot und am Ende, Zahl der Spuren, Wegpunkte (oder null).
     */
    public record Flow(String name, int year, double[][] fissure, double lengthKm, double width, double aaNear, double aaFar,
                       int paths, double[][] via) { }

    /** Eine Fläche ohne Fließweg (Kraterboden, Lavasee): Mitte, Halbachsen (m), Drehung (Grad von Ost). */
    public record Patch(String name, int year, double lat, double lon, double rx, double rz, double rot, double aa) { }

    private static double[][] p(double... v) {
        double[][] r = new double[v.length / 2][];
        for (int i = 0; i < r.length; i++) r[i] = new double[]{v[2 * i], v[2 * i + 1]};
        return r;
    }

    /** Datierte Ströme 1801–2024 [L Jahr und Ort, USGS HVO; Lage auf rund 1 km genähert]. */
    public static final List<Flow> FLOWS = List.of(
            // ---------------- Kīlauea
            new Flow("Keaīwa 1823", 1823, p(19.300, -155.370, 19.255, -155.405), 9, 500, 0.05, 0.1, 7, null),
            new Flow("Ostriftzone 1840", 1840, p(19.425, -155.020, 19.465, -154.945), 13, 800, 0.4, 0.9, 9, p(19.505, -154.890)),
            new Flow("Mauna Iki 1919–20", 1920, p(19.352, -155.345, 19.335, -155.360), 11, 800, 0.0, 0.15, 9, null),
            new Flow("Puna 1955", 1955, p(19.455, -154.990, 19.475, -154.900), 7, 450, 0.7, 0.95, 12, null),
            new Flow("Kapoho 1960", 1960, p(19.503, -154.848, 19.509, -154.833), 4.5, 900, 0.8, 0.95, 12, null),
            new Flow("Chain of Craters 1965–69", 1968, p(19.383, -155.178, 19.378, -155.150), 6, 350, 0.3, 0.5, 6, null),
            new Flow("Mauna Ulu 1969–74", 1972, p(19.369, -155.212, 19.362, -155.196), 12.5, 850, 0.1, 0.35, 18, p(19.300, -155.140)),
            new Flow("Südwestriftzone 1971", 1971, p(19.395, -155.290, 19.370, -155.315), 6, 450, 0.05, 0.1, 6, null),
            new Flow("Südwestriftzone 1974", 1974, p(19.383, -155.300, 19.350, -155.330), 9, 500, 0.05, 0.15, 7, null),
            new Flow("Kalalua 1977", 1977, p(19.393, -155.060, 19.402, -155.035), 10, 450, 0.7, 0.9, 6, p(19.345, -155.010)),
            new Flow("Puʻu ʻŌʻō 1983–86", 1985, p(19.388, -155.105, 19.392, -155.095), 11, 550, 0.75, 0.95, 18, null),
            new Flow("Kupaianaha 1986–92", 1990, p(19.381, -155.083, 19.379, -155.078), 13, 900, 0.05, 0.2, 26, p(19.350, -154.995)),
            new Flow("Puʻu ʻŌʻō Flanke 1992–2007", 2000, p(19.384, -155.110, 19.382, -155.100), 13, 800, 0.05, 0.15, 28, p(19.315, -155.085)),
            new Flow("Thanksgiving Eve 2007–11", 2009, p(19.394, -155.078, 19.392, -155.070), 13, 700, 0.05, 0.2, 18, p(19.345, -154.990)),
            new Flow("Kahaualeʻa 2011–13", 2013, p(19.394, -155.104, 19.398, -155.100), 6, 380, 0.3, 0.5, 8, p(19.430, -155.090)),
            new Flow("27. Juni 2014", 2014, p(19.397, -155.096, 19.400, -155.092), 20, 320, 0.05, 0.2, 6, p(19.445, -155.030, 19.482, -154.972, 19.492, -154.952)),
            new Flow("Strom 61g 2016–17", 2017, p(19.385, -155.098, 19.383, -155.094), 10, 400, 0.05, 0.2, 7, p(19.325, -155.055)),
            new Flow("Spalte 8 2018", 2018, p(19.4602, -154.9100, 19.4605, -154.9095), 13, 650, 0.9, 0.95, 8, p(19.482, -154.880, 19.500, -154.845, 19.505, -154.815)),
            new Flow("Leilani-Spalten 2018", 2018, p(19.470, -154.905, 19.478, -154.880), 6, 700, 0.85, 0.95, 12, p(19.462, -154.862, 19.455, -154.845)),
            new Flow("Nāpau 2024", 2024, p(19.383, -155.150, 19.381, -155.140), 1.5, 220, 0.3, 0.4, 4, null),
            // ---------------- Mauna Loa
            new Flow("Mauna Loa 1843", 1843, p(19.560, -155.520), 26, 1000, 0.4, 0.8, 6, p(19.690, -155.520)),
            new Flow("Mauna Loa 1852", 1852, p(19.565, -155.455), 20, 900, 0.4, 0.8, 6, p(19.640, -155.300)),
            new Flow("Mauna Loa 1855–56", 1856, p(19.585, -155.460), 40, 900, 0.1, 0.4, 9, p(19.640, -155.330, 19.685, -155.215)),
            new Flow("Mauna Loa 1859", 1859, p(19.600, -155.640), 51, 900, 0.4, 0.8, 7, p(19.700, -155.800, 19.810, -155.890, 19.857, -155.925)),
            new Flow("Mauna Loa 1868", 1868, p(19.110, -155.680), 12, 800, 0.6, 0.9, 6, null),
            new Flow("Mauna Loa 1880–81", 1881, p(19.575, -155.475), 47, 700, 0.05, 0.3, 8, p(19.650, -155.300, 19.700, -155.130)),
            new Flow("Mauna Loa 1887", 1887, p(19.140, -155.720), 20, 900, 0.6, 0.9, 6, null),
            new Flow("Mauna Loa 1907", 1907, p(19.230, -155.660), 20, 800, 0.6, 0.9, 6, null),
            new Flow("Mauna Loa 1916", 1916, p(19.260, -155.660), 25, 700, 0.7, 0.9, 5, null),
            new Flow("Mauna Loa 1919 (Ālika)", 1919, p(19.300, -155.640), 25, 800, 0.7, 0.95, 6, p(19.270, -155.890)),
            new Flow("Mauna Loa 1926", 1926, p(19.290, -155.650), 27, 800, 0.7, 0.95, 6, p(19.155, -155.900)),
            new Flow("Mauna Loa 1935", 1935, p(19.600, -155.560), 25, 900, 0.4, 0.8, 6, p(19.700, -155.400)),
            new Flow("Mauna Loa 1942", 1942, p(19.540, -155.490), 25, 900, 0.5, 0.85, 6, p(19.650, -155.290)),
            new Flow("Mauna Loa 1950 (Hoʻōpūloa)", 1950, p(19.300, -155.625, 19.320, -155.622), 24, 1100, 0.8, 0.98, 4, p(19.230, -155.780, 19.190, -155.900)),
            new Flow("Mauna Loa 1950 (Kaʻapuna)", 1950, p(19.340, -155.622, 19.360, -155.622), 22, 1100, 0.8, 0.98, 4, p(19.330, -155.880)),
            new Flow("Mauna Loa 1950 (Hoʻokena)", 1950, p(19.380, -155.624, 19.400, -155.625), 22, 1100, 0.8, 0.98, 4, p(19.400, -155.880)),
            new Flow("Mauna Loa 1984", 1984, p(19.500, -155.520, 19.520, -155.500), 27, 800, 0.5, 0.9, 9, p(19.640, -155.310)),
            new Flow("Mauna Loa 2022", 2022, p(19.525, -155.505, 19.540, -155.490), 19, 1100, 0.4, 0.9, 10, p(19.640, -155.420, 19.665, -155.390)),
            // ---------------- Hualālai
            new Flow("Kaʻūpūlehu 1801", 1801, p(19.720, -155.870), 16, 900, 0.3, 0.6, 6, p(19.830, -155.980)),
            new Flow("Huʻehuʻe 1801", 1801, p(19.730, -155.950), 10, 900, 0.2, 0.5, 5, p(19.740, -156.040)));

    /** Flächen ohne Fließweg [L Jahr; Umriss genähert]. */
    public static final List<Patch> PATCHES = List.of(
            new Patch("Kaluapele, Boden 1971–82", 1976, 19.4100, -155.2770, 1700, 1100, 35, 0.05),
            new Patch("Kīlauea Iki, Lavasee 1959", 1959, 19.4160, -155.2465, 760, 380, 20, 0.0),
            new Patch("Puʻu Puaʻi 1959", 1959, 19.4160, -155.2535, 280, 280, 0, 0.6),
            new Patch("Halemaʻumaʻu, Lavasee 2020–25", 2025, 19.4072, -155.2834, 640, 540, 30, 0.0),
            new Patch("Mauna Ulu, Kegel 1974", 1974, 19.3710, -155.2050, 700, 700, 0, 0.3),
            new Patch("Puʻu ʻŌʻō, Kegel 2018", 2018, 19.3880, -155.1050, 900, 900, 0, 0.5),
            new Patch("Spalte 8, Kegel 2018", 2018, 19.4602, -154.9100, 350, 350, 0, 0.9),
            new Patch("Kapoho, neues Land 2018", 2018, 19.5000, -154.8120, 900, 1250, 0, 0.95),
            new Patch("Mokuʻāweoweo, Boden 1984", 1984, 19.4721, -155.5922, 2200, 1200, 60, 0.1));

    /** Dampf- und Schwefelstellen: Breite, Länge, Halbmesser (m), Ringbreite (m, 0 = Scheibe) [L Lage, G Umriss]. */
    public static final double[][] SULFUR = {
            {19.4335, -155.2617, 180, 0},     // Haʻakulamanu (Sulphur Banks)
            {19.4302, -155.2672, 150, 0},     // Steaming Bluff
            {19.4072, -155.2834, 690, 70},    // Rand des Halemaʻumaʻu
            {19.3880, -155.1050, 430, 60},    // Rand des Puʻu ʻŌʻō
            {19.4160, -155.2465, 60, 0}};     // Dampfstelle im Kīlauea Iki

    // ------------------------------------------------------------------ Bau

    /** Ergebnis für Prüfung und Werkbuch: Fläche je Strom in km². */
    public static final java.util.Map<String, Double> AREA = new java.util.LinkedHashMap<>();

    /**
     * Füllt cover und geo (je 4 Byte pro Zelle) für das Raster g. sd: Abstand zur Küste je Zelle (m,
     * an Land positiv). progress: Rückmeldung 0..1.
     */
    public static void build(HeightGrid g, float[] sd, byte[] cover, byte[] geo, java.util.function.DoubleConsumer progress) {
        int n = HeightGrid.N;
        int[] year = new int[n * n];
        float[] aa = new float[n * n];
        paintPatches(year, aa);
        progress.accept(0.05);
        List<Flow> sorted = new ArrayList<>(FLOWS);
        sorted.sort(Comparator.comparingInt(Flow::year));
        // Patches mit jüngerem Jahr als ein Strom sollen oben liegen: Ströme malen nur, wo sie jünger sind
        int done = 0;
        for (Flow f : sorted) {
            int before = count(year, f.year);
            trace(g, f, year, aa);
            AREA.put(f.name, (count(year, f.year) - before) * HeightGrid.CELL * HeightGrid.CELL / 1e6);
            progress.accept(0.05 + 0.45 * ++done / sorted.size());
        }
        Rain rain = new Rain(g);
        progress.accept(0.65);
        int[] rows = new int[1];
        IntStream.range(0, n).parallel().forEach(j -> {
            double z = HeightGrid.Z0 + j * HeightGrid.CELL;
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                double x = HeightGrid.X0 + i * HeightGrid.CELL;
                double h = g.h[k];
                double gx = (g.h[j * n + Math.min(n - 1, i + 2)] - g.h[j * n + Math.max(0, i - 2)]) / (4 * HeightGrid.CELL);
                Cell c = cell(x, z, h, sd[k], year[k], aa[k], rain.at(x, z, h, Island.smooth(0.01, 0.06, gx)));
                cover[4 * k] = b(c.veg);
                cover[4 * k + 1] = b(c.trees);
                cover[4 * k + 2] = b(c.ash);
                cover[4 * k + 3] = b(c.shore);
                geo[4 * k] = (byte) ageCode(c.age);
                geo[4 * k + 1] = b(c.aa);
                geo[4 * k + 2] = b(c.lichen);
                geo[4 * k + 3] = b(c.rain / RAIN_MAX);
            }
            synchronized (rows) { rows[0]++; if ((rows[0] & 63) == 0) progress.accept(0.65 + 0.35 * rows[0] / n); }
        });
    }

    /**
     * Phase 16b: welcher Strom oder welche Fläche jede Zelle des Rasters trägt, genau wie in {@link #build}: Index in
     * {@link #FLOWS}, Flächen ab FLOWS.size() in der Reihenfolge von {@link #PATCHES}, -1 = älteres Gestein. axes (oder null)
     * bekommt je Strom den Weg der ersten Spur (Szene, m). years (oder null) bekommt das Jahr je Zelle zum Abgleich.
     */
    public static int[] owners(HeightGrid g, java.util.Map<String, List<double[]>> axes, int[] years) {
        int n = HeightGrid.N;
        int[] year = years != null ? years : new int[n * n];
        float[] aa = new float[n * n];
        int[] owner = new int[n * n];
        java.util.Arrays.fill(owner, -1);
        for (int pi = 0; pi < PATCHES.size(); pi++) {
            int[] before = year.clone();
            paintPatch(PATCHES.get(pi), year, aa);
            for (int k = 0; k < year.length; k++) if (year[k] != before[k]) owner[k] = FLOWS.size() + pi;
        }
        List<Flow> sorted = new ArrayList<>(FLOWS);
        sorted.sort(Comparator.comparingInt(Flow::year));
        for (Flow f : sorted) {
            int[] before = year.clone();
            List<double[]> axis = new ArrayList<>();
            trace(g, f, year, aa, axis);
            if (axes != null) axes.put(f.name, axis);
            int id = FLOWS.indexOf(f);
            for (int k = 0; k < year.length; k++) if (year[k] != before[k]) owner[k] = id;
        }
        return owner;
    }

    private static int count(int[] year, int y) {
        int c = 0;
        for (int v : year) if (v == y) c++;
        return c;
    }

    private static byte b(double v) { return (byte) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    /** Alles, was eine Zelle trägt. */
    static final class Cell {
        double age, aa, lichen, rain, veg, trees, ash, shore;
    }

    /** Eine Zelle aus Lage, Höhe, Küstenabstand, Strom (Jahr oder 0), ʻaʻā-Anteil des Stroms und Regen. */
    static Cell cell(double x, double z, double h, double sd, int year, double flowAa, double rain) {
        Cell c = new Cell();
        c.rain = rain;
        if (year > 0) {
            c.age = Math.max(0.3, NOW - year + 0.5);
            c.aa = flowAa;
        } else {
            double[] base = baseAge(x, z, h);
            c.age = base[0];
            c.aa = base[1];
        }
        if (h < -0.5) {
            // Meeresboden: kein Bewuchs, Alter wie das Land daneben (für die Farbe unerheblich)
            c.shore = Island.smooth(-30, -2, h) * Island.smooth(400, 40, -sd);
            return c;
        }
        // Wachstum: Regen bestimmt das Tempo, Höhe und Kälte bremsen
        double growth = Island.clamp((rain - 350) / 2600) * Island.smooth(3300, 2300, h);
        double eff = c.age * growth;                    // „wirksame“ Jahre
        double maxVeg = Island.smooth(350, 1600, rain) * Island.smooth(3100, 2300, h);
        c.veg = maxVeg * Island.smooth(4, 140, eff) * (0.85 + 0.3 * Noise.fbm(x * 0.0011, z * 0.0011, 3));
        c.trees = Island.smooth(900, 2200, rain) * Island.smooth(2900, 1900, h) * Island.smooth(40, 400, eff)
                * (0.8 + 0.4 * Noise.fbm(x * 0.0007 + 3, z * 0.0007, 3));
        // Flechten: auf jungen Strömen im Feuchten silbergrau, verschwinden unter dichtem Bewuchs
        c.lichen = Island.smooth(1000, 2600, rain) * Island.smooth(3, 25, c.age) * (1 - Island.smooth(0.35, 0.8, c.veg))
                * Island.smooth(2800, 1800, h);
        // Gipfel: Schwefelgase lassen um die Caldera wenig wachsen
        double gas = 1 - 0.6 * Island.smooth(3800, 1200, Math.hypot((x - 800) * 0.8, z + 200));
        c.veg *= gas;
        c.trees *= gas;
        // Tephra: Keanakākoʻi-Asche (1790) in der Kaʻū-Wüste, Bimsfall von 1959 südwestlich von Kīlauea Iki
        double kau = 1 - Island.smooth(0.5, 1.25, Math.hypot((x - KAU[0]) / 11000, (z - KAU[1]) / 7500)
                + 0.45 * (Noise.fbm(x * 0.0003 + 2, z * 0.0003, 4) - 0.5) * 2);
        double pumice = Island.smooth(1700, 500, Math.hypot(x - PUAI[0] + 500, (z - PUAI[1] - 400) * 1.3));
        c.ash = Math.max(kau * 0.9 * (1 - Island.smooth(1800, 2600, rain)), pumice);
        if (year > 0 && year > 1790) c.ash *= Island.smooth(1975, 1800, year);   // jüngere Lava liegt auf der Asche
        c.veg *= 1 - 0.7 * c.ash;
        c.trees *= 1 - 0.9 * c.ash;
        // Salz und Gischt an der Küste (Phase 9): an trockenen Küsten keine ʻōhiʻa bis einige hundert Meter
        // landeinwärts, nur Gras und Büsche (Hōlei, Kaʻū); an der nassen Puna-Küste reicht der Wald nah ans Meer
        // [L, Erscheinung; Breiten G]
        double salt = 150 + 1350 * (1 - Island.smooth(1700, 2400, rain));
        c.trees *= Island.smooth(40, salt, sd);
        c.veg *= 0.6 + 0.4 * Island.smooth(10, 120, sd);
        c.shore = Island.smooth(160, 15, sd) * Island.smooth(-3, 2, h);
        return c;
    }

    private static final double[] KAU = {Geo.x(19.3650, -155.3700), Geo.z(19.3650)};
    private static final double[] PUAI = {Geo.x(19.4160, -155.2535), Geo.z(19.4160)};

    // Vulkane: Mitte (x, z), Reichweite (m), Grundalter (Median, Jahre), Streuung (ln), ʻaʻā-Neigung
    private static final double[][] VOLC = {
            {Geo.x(19.4721, -155.5922), Geo.z(19.4721), 55000, 1800, 1.1, 0.4},   // Mauna Loa
            {Geo.x(19.8207, -155.4681), Geo.z(19.8207), 38000, 20000, 0.8, 0.4},   // Mauna Kea
            {Geo.x(19.6920, -155.8700), Geo.z(19.6920), 24000, 2500, 0.8, 0.35},   // Hualālai
            {Geo.x(20.0860, -155.7170), Geo.z(20.0860), 26000, 220000, 0.3, 0.3}}; // Kohala
    private static final double[][] RIFTS_KIL = {
            {Geo.x(19.4072, -155.2834), Geo.z(19.4072)}, {Geo.x(19.3880, -155.1050), Geo.z(19.3880)},
            {Geo.x(19.4602, -154.9100), Geo.z(19.4602)}, {Geo.x(19.5160, -154.8110), Geo.z(19.5160)}};
    private static final double[][] SWR_KIL = {
            {Geo.x(19.4072, -155.2834), Geo.z(19.4072)}, {Geo.x(19.2900, -155.4350), Geo.z(19.2900)}};

    /** Grundalter (Jahre) und ʻaʻā-Anteil außerhalb der Ströme mit Jahr. */
    static double[] baseAge(double x, double z, double h) {
        // Kīlauea: Abstand zu Gipfel und Riftzonen
        double dk = Math.min(Island.segDist(RIFTS_KIL, x, z)[0], Island.segDist(SWR_KIL, x, z)[0]);
        // Grenzen zwischen den Vulkanen verzahnt: Ströme des einen liegen auf dem anderen
        double wob = 0.35 * (Noise.fbm(x * 0.00013 + 4, z * 0.00013 - 2, 4) - 0.5) * 2;
        double best = dk / 22000 + wob;
        int owner = -1;
        for (int v = 0; v < VOLC.length; v++) {
            double s = Math.hypot(x - VOLC[v][0], z - VOLC[v][1]) / VOLC[v][2] + (v == 0 ? -wob : 0);
            if (s < best) { best = s; owner = v; }
        }
        double median, spread, aaBias;
        double ox, oz;
        if (owner < 0) { median = 550; spread = 0.9; aaBias = 0.2; ox = 0; oz = 0; }
        else { median = VOLC[owner][3]; spread = VOLC[owner][4]; aaBias = VOLC[owner][5]; ox = VOLC[owner][0]; oz = VOLC[owner][1]; }
        // Rauschen in Polarkoordinaten um den Gipfel, längs des Hangs gestreckt: Stromzungen
        double dx = x - ox, dz = z - oz, r = Math.hypot(dx, dz) + 1, a = Math.atan2(dz, dx);
        double n1 = Noise.fbm(a * 140 + 11, r * 0.00012, 4), n2 = Noise.fbm(x * 0.0005 - 7, z * 0.0005 + 3, 3);
        double nrm = (0.7 * n1 + 0.3 * n2 - 0.5) * 2;           // etwa −1..1
        double age = median * Math.exp(spread * 1.6 * nrm);
        // ʻAilāʻau (1410–1460): große Pāhoehoe-Felder nordöstlich des Gipfels
        double ail = Island.smooth(9000, 3000, Island.segDist(AILAAU, x, z)[0] + 2500 * (n2 - 0.5));
        if (owner < 0 && ail > 0.5) age = Math.min(age, 590);
        // Mauna Loa: auf steilen Flanken und weiter unten mehr ʻaʻā
        double aa = Island.clamp(aaBias + 0.35 * nrm + (owner == 0 ? 0.15 * Island.smooth(3500, 1500, h) : 0));
        if (owner < 0) aa = Island.clamp(0.15 + 0.35 * nrm);
        return new double[]{Math.max(80, age), aa};
    }

    private static final double[][] AILAAU = {
            {Geo.x(19.4250, -155.2400), Geo.z(19.4250)}, {Geo.x(19.4700, -155.1700), Geo.z(19.4700)}, {Geo.x(19.5600, -155.0600), Geo.z(19.5600)}};

    // ------------------------------------------------------------------ Flächen und Spuren

    private static void paintPatches(int[] year, float[] aa) {
        for (Patch p : PATCHES) paintPatch(p, year, aa);
    }

    private static void paintPatch(Patch p, int[] year, float[] aa) {
        int n = HeightGrid.N;
        {
            double cx = Geo.x(p.lat, p.lon), cz = Geo.z(p.lat);
            double ca = Math.cos(Math.toRadians(p.rot)), sa = Math.sin(Math.toRadians(p.rot));
            double r = Math.max(p.rx, p.rz) * 1.2;
            int i0 = idx(cx - r, true), i1 = idx(cx + r, true), j0 = idx(cz - r, false), j1 = idx(cz + r, false);
            int before = 0;
            for (int j = j0; j <= j1; j++)
                for (int i = i0; i <= i1; i++) {
                    double x = HeightGrid.X0 + i * HeightGrid.CELL - cx, z = HeightGrid.Z0 + j * HeightGrid.CELL - cz;
                    double u = (x * ca - z * sa) / p.rx, v = (x * sa + z * ca) / p.rz;
                    double d = Math.sqrt(u * u + v * v) + 0.15 * (Noise.fbm(x * 0.004 + p.year, z * 0.004, 3) - 0.5) * 2;
                    int k = j * n + i;
                    if (d < 1 && p.year >= year[k]) { year[k] = p.year; aa[k] = (float) p.aa; before++; }
                }
            AREA.put(p.name, before * HeightGrid.CELL * HeightGrid.CELL / 1e6);
        }
    }

    private static int idx(double v, boolean x) {
        double o = x ? HeightGrid.X0 : HeightGrid.Z0;
        return Math.max(0, Math.min(HeightGrid.N - 1, (int) Math.round((v - o) / HeightGrid.CELL)));
    }

    /** Lässt die Spuren eines Stroms bergab laufen und malt seine Fläche. */
    static void trace(HeightGrid g, Flow f, int[] year, float[] aa) { trace(g, f, year, aa, null); }

    /** Wie oben; axis (oder null) sammelt den Weg der ersten Spur (Phase 16b: Achse des Stroms in der Datenbank). */
    static void trace(HeightGrid g, Flow f, int[] year, float[] aa, List<double[]> axis) {
        Random rnd = new Random(f.name.hashCode() * 31L + f.year);
        double[][] fis = new double[f.fissure.length][];
        for (int i = 0; i < fis.length; i++) fis[i] = new double[]{Geo.x(f.fissure[i][0], f.fissure[i][1]), Geo.z(f.fissure[i][0])};
        double[][] via = null;
        if (f.via != null) {
            via = new double[f.via.length][];
            for (int i = 0; i < via.length; i++) via[i] = new double[]{Geo.x(f.via[i][0], f.via[i][1]), Geo.z(f.via[i][0])};
        }
        double total = f.lengthKm * 1000;
        for (int k = 0; k < f.paths; k++) {
            double t = f.paths == 1 ? 0.5 : (k + rnd.nextDouble()) / f.paths;
            double[] p = along(fis, t);
            double len = k < Math.max(1, f.paths / 4) ? total * (0.9 + 0.1 * rnd.nextDouble()) : total * (0.35 + 0.6 * rnd.nextDouble());
            double hw0 = f.width / 2 * (k == 0 ? 1.0 : 0.45 + 0.6 * rnd.nextDouble());
            double dx = 0, dz = 0;
            int vi = 0;
            // jede Spur zielt etwas neben die Wegpunkte: das Feld wird breit, nicht eine Linie
            double[][] myVia = null;
            if (via != null) {
                myVia = new double[via.length][];
                double spread = k == 0 ? 0 : Math.min(f.width * 2.5, 1800);
                for (int i = 0; i < via.length; i++) myVia[i] = new double[]{via[i][0] + rnd.nextGaussian() * spread, via[i][1] + rnd.nextGaussian() * spread};
            }
            double s = 0, lastH = Double.MAX_VALUE, stuck = 0;
            double side = rnd.nextGaussian() * 0.12;
            if (axis != null && k == 0) axis.add(new double[]{p[0], p[1]});
            while (s < len) {
                double x = p[0], z = p[1];
                double e = 60;
                double gx = (g.height(x + e, z) - g.height(x - e, z)) / (2 * e), gz = (g.height(x, z + e) - g.height(x, z - e)) / (2 * e);
                double slope = Math.hypot(gx, gz);
                double ddx = 0, ddz = 0;
                if (slope > 1e-5) { ddx = -gx / slope; ddz = -gz / slope; }
                double tx = 0, tz = 0;
                if (myVia != null && vi < myVia.length) {
                    double vx = myVia[vi][0] - x, vz = myVia[vi][1] - z, vd = Math.hypot(vx, vz);
                    if (vd < 1500) vi++;
                    else { tx = vx / vd; tz = vz / vd; }
                }
                double wgt = myVia != null && vi < myVia.length ? 1.6 : 0;
                // Seitliches Pendeln je Spur (Arme fächern etwas auf)
                double jx = rnd.nextGaussian() * 0.15 + side * -ddz, jz = rnd.nextGaussian() * 0.15 + side * ddx;
                double nx = dx * 0.6 + ddx * Math.min(1.0, slope * 40 + 0.3) + tx * wgt + jx;
                double nz = dz * 0.6 + ddz * Math.min(1.0, slope * 40 + 0.3) + tz * wgt + jz;
                double nl = Math.hypot(nx, nz);
                if (nl < 1e-6) { nx = rnd.nextGaussian(); nz = rnd.nextGaussian(); nl = Math.hypot(nx, nz); }
                dx = nx / nl;
                dz = nz / nl;
                double step = 45;
                p[0] += dx * step;
                p[1] += dz * step;
                s += step;
                double h = g.height(p[0], p[1]);
                if (h < -12) break;                                   // im Meer: der Strom endet in einem kleinen Delta
                if (p[0] < HeightGrid.X0 || p[1] < HeightGrid.Z0 || p[0] > HeightGrid.X0 + HeightGrid.EXTENT || p[1] > HeightGrid.Z0 + HeightGrid.EXTENT) break;
                stuck = h > lastH - 0.05 ? stuck + step : Math.max(0, stuck - step);
                lastH = Math.min(lastH, h);
                if (stuck > 2500 && via == null) break;               // in einer Senke ausgebreitet
                double flat = Island.smooth(0.04, 0.004, slope);
                double hw = hw0 * (0.65 + 0.7 * Noise.value(s * 0.0012 + k * 7.1, f.year * 0.37)) * (1 + 1.3 * flat);
                hw = Math.max(hw, 45);
                double frac = s / Math.max(1, total);
                double a = f.aaNear + (f.aaFar - f.aaNear) * Math.min(1, frac);
                stamp(p[0], p[1], hw, f.year, a, year, aa);
                if (axis != null && k == 0) axis.add(new double[]{p[0], p[1]});
            }
        }
    }

    private static double[] along(double[][] line, double t) {
        if (line.length == 1) return new double[]{line[0][0], line[0][1]};
        double totalLen = 0;
        for (int i = 1; i < line.length; i++) totalLen += Math.hypot(line[i][0] - line[i - 1][0], line[i][1] - line[i - 1][1]);
        double want = t * totalLen;
        for (int i = 1; i < line.length; i++) {
            double l = Math.hypot(line[i][0] - line[i - 1][0], line[i][1] - line[i - 1][1]);
            if (want <= l || i == line.length - 1) {
                double u = l > 0 ? Math.min(1, want / l) : 0;
                return new double[]{line[i - 1][0] + (line[i][0] - line[i - 1][0]) * u, line[i - 1][1] + (line[i][1] - line[i - 1][1]) * u};
            }
            want -= l;
        }
        return new double[]{line[0][0], line[0][1]};
    }

    private static void stamp(double cx, double cz, double r, int yr, double a, int[] year, float[] aa) {
        int n = HeightGrid.N;
        int i0 = idx(cx - r, true), i1 = idx(cx + r, true), j0 = idx(cz - r, false), j1 = idx(cz + r, false);
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                double x = HeightGrid.X0 + i * HeightGrid.CELL, z = HeightGrid.Z0 + j * HeightGrid.CELL;
                double d = Math.hypot(x - cx, z - cz);
                // ausgefranster Rand: Zungen und Buchten von einigen Dutzend Metern
                double edge = r * (0.75 + 0.5 * Noise.value(x * 0.011 + yr, z * 0.011));
                int k = j * n + i;
                if (d < edge && yr >= year[k]) {
                    if (year[k] != yr) aa[k] = (float) a;
                    else aa[k] = (float) Math.max(aa[k], a * 0.5 + aa[k] * 0.5);
                    year[k] = yr;
                }
            }
    }

    // ------------------------------------------------------------------ Regen

    /**
     * Jahresregen (mm) aus dem Passat: Feuchte wandert in Windrichtung (aus 70°) über ein gedrehtes Gitter
     * von 300 m; bergauf regnet sie aus, über der Inversion wenig, bergab trocknet die Luft. Dazu der
     * Kona-Gürtel (Seewind am Nachmittag) am Westhang.
     */
    public static final class Rain {
        static final double STEP = 300;
        final int nt, nq;
        final double cx, cz, half;
        final float[] r;
        // Wind weht nach 250° (WSW): x Osten, z Süden
        static final double WX = -Math.sin(Math.toRadians(70)), WZ = Math.cos(Math.toRadians(70));
        static final double PX = WZ, PZ = -WX;

        public Rain(HeightGrid g) {
            cx = HeightGrid.X0 + HeightGrid.EXTENT / 2;
            cz = HeightGrid.Z0 + HeightGrid.EXTENT / 2;
            half = HeightGrid.EXTENT * 0.75;
            nt = (int) (2 * half / STEP) + 1;
            nq = nt;
            r = new float[nt * nq];
            IntStream.range(0, nq).parallel().forEach(qi -> {
                double q = -half + qi * STEP;
                double m = 1, hPrev = 0, hs = 0;
                for (int ti = 0; ti < nt; ti++) {
                    double t = -half + ti * STEP;
                    double x = cx + WX * t + PX * q, z = cz + WZ * t + PZ * q;
                    double h = Math.max(0, g.height(x, z));
                    hs = hs * 0.75 + h * 0.25;                        // geglättet (Luft folgt nicht jeder Kante)
                    double slope = (hs - hPrev) / STEP;
                    hPrev = hs;
                    double inv = Island.smooth(3000, 1700, hs);
                    double rain = m * (1500 * (0.45 + 0.55 * inv) + 7.0e4 * Math.max(0, slope) * inv);
                    rain *= 1 - Island.clamp(-slope * 22) * 0.75;      // absinkende Luft trocknet
                    if (h <= 0) { rain = 1500 * m * 0.8; m += (1 - m) * STEP / 150_000; }
                    else m = Math.max(0.12, m - rain * STEP * 4.0e-9 * (0.3 + 0.7 * inv));
                    r[qi * nt + ti] = (float) rain;
                }
            });
            // quer zum Wind glätten (benachbarte Bahnen tauschen Luft)
            float[] tmp = r.clone();
            IntStream.range(0, nq).parallel().forEach(qi -> {
                for (int ti = 0; ti < nt; ti++) {
                    double s = 0, w = 0;
                    for (int d = -3; d <= 3; d++) {
                        int q2 = qi + d;
                        if (q2 < 0 || q2 >= nq) continue;
                        double wt = 4 - Math.abs(d);
                        s += tmp[q2 * nt + ti] * wt;
                        w += wt;
                    }
                    r[qi * nt + ti] = (float) (s / w);
                }
            });
        }

        /** Regen in mm/Jahr an (x, z) bei Höhe h; west: 0..1, wie sehr der Hang nach Westen schaut. */
        public double at(double x, double z, double h, double west) {
            double dx = x - cx, dz = z - cz;
            double t = (dx * WX + dz * WZ + half) / STEP, q = (dx * PX + dz * PZ + half) / STEP;
            int ti = (int) Math.floor(t), qi = (int) Math.floor(q);
            double ft = t - ti, fq = q - qi;
            ti = Math.max(0, Math.min(nt - 2, ti));
            qi = Math.max(0, Math.min(nq - 2, qi));
            double a = r[qi * nt + ti], b = r[qi * nt + ti + 1], c = r[(qi + 1) * nt + ti], d = r[(qi + 1) * nt + ti + 1];
            double v = (a + (b - a) * ft) * (1 - fq) + (c + (d - c) * ft) * fq;
            // Kona-Gürtel: Wolken am Nachmittag am Westhang von Hualālai und Mauna Loa, 300–1 800 m
            double kona = Island.smooth(-25000, -50000, x) * west * Island.smooth(150, 600, h) * Island.smooth(2100, 1300, h) * 1100;
            return Math.min(RAIN_MAX, Math.max(250, v + kona));
        }
    }
}
