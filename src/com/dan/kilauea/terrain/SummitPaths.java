package com.dan.kilauea.terrain;

import java.util.List;

/**
 * Wege am Gipfel und die Lavaröhre Nāhuku, als Linien (Breite, Länge) [L Verlauf nach NPS-Karten und
 * Wegbeschreibungen, auf rund 30–80 m genähert]. Die Wege legt {@code com.dan.road} als Pfade auf das
 * Gelände; die Gipfelkachel schärft in ihrem Band keine Wände, damit man dort gehen kann.
 */
public final class SummitPaths {
    private SummitPaths() { }

    /** Ein Weg: Name, Art (0 Pfad, 1 befestigter Weg), Stützpunkte. */
    public record Trail(String name, int kind, double[][] latLon) {
        public double[][] xz() {
            double[][] r = new double[latLon.length][];
            for (int i = 0; i < latLon.length; i++) r[i] = new double[]{Geo.x(latLon[i][0], latLon[i][1]), Geo.z(latLon[i][0])};
            return r;
        }
    }

    /**
     * Kīlauea Iki Trail: vom Aussichtspunkt am Nordrand nach Osten, in Kehren die Nordostwand hinab auf den
     * Boden des Lavasees von 1959, über den Boden nach Westen, am Fuß des Puʻu Puaʻi hinauf und am Westrand zurück.
     */
    public static final Trail KILAUEA_IKI = new Trail("Kīlauea Iki Trail", 0, new double[][]{
            {19.4178, -155.2500}, {19.4180, -155.2470}, {19.4177, -155.2440}, {19.4169, -155.2420}, {19.4161, -155.2428},
            {19.4157, -155.2417}, {19.4152, -155.2430}, {19.4148, -155.2442}, {19.4145, -155.2470}, {19.4143, -155.2500},
            {19.4140, -155.2530}, {19.4137, -155.2548}, {19.4134, -155.2562}, {19.4148, -155.2568}, {19.4162, -155.2560},
            {19.4174, -155.2530}, {19.4178, -155.2500}});

    /** Crater Rim Trail am Nordrand der Caldera: Steam Vents, Steaming Bluff, Uēkahuna. */
    public static final Trail CRATER_RIM_TRAIL = new Trail("Crater Rim Trail", 0, new double[][]{
            {19.4318, -155.2648}, {19.4307, -155.2670}, {19.4296, -155.2700}, {19.4284, -155.2738}, {19.4266, -155.2772},
            {19.4248, -155.2805}, {19.4232, -155.2838}, {19.4218, -155.2862}, {19.4208, -155.2876}});

    /** Devastation Trail: befestigter Weg über den Bimsfall von 1959. */
    public static final Trail DEVASTATION = new Trail("Devastation Trail", 1, new double[][]{
            {19.4112, -155.2530}, {19.4100, -155.2545}, {19.4088, -155.2556}, {19.4075, -155.2566}, {19.4063, -155.2576}});

    /** Weg zur Lavaröhre: vom Parkplatz in die Eingangsgrube und von der Ausgangsgrube zurück. */
    public static final Trail NAHUKU_LOOP = new Trail("Nāhuku-Rundweg", 1, new double[][]{
            {19.4163, -155.2378}, {19.4159, -155.2381}, {19.41555, -155.23825}, {19.41545, -155.2383}});
    public static final Trail NAHUKU_BACK = new Trail("Nāhuku-Rückweg", 1, new double[][]{
            {19.41395, -155.23905}, {19.4142, -155.2396}, {19.4150, -155.2395}, {19.4158, -155.2388}, {19.4163, -155.2378}});

    /** Ein Ausgangspunkt zum Gehen: Name, Breite, Länge, Blickrichtung (Grad, 0 = Norden, im Uhrzeigersinn). */
    public record Spot(String name, double lat, double lon, double heading) {
        public double x() { return Geo.x(lat, lon); }
        public double z() { return Geo.z(lat); }
        @Override public String toString() { return name; }
    }

    public static final List<Spot> WALK_SPOTS = List.of(
            new Spot("Uēkahuna, am Kraterrand", 19.4200, -155.2870, 135),
            new Spot("Steaming Bluff", 19.4306, -155.2668, 200),
            new Spot("Kīlauea Iki, Aussicht am Rand", 19.4169, -155.2500, 175),
            new Spot("Kīlauea Iki, auf dem Boden", 19.4145, -155.2470, 260),
            new Spot("Nāhuku, Weg zum Eingang", 19.4160, -155.2380, 210),
            new Spot("Devastation Trail", 19.4108, -155.2534, 220));

    public static final List<Trail> TRAILS = List.of(KILAUEA_IKI, CRATER_RIM_TRAIL, DEVASTATION, NAHUKU_LOOP, NAHUKU_BACK);

    /**
     * Achse der Lavaröhre Nāhuku vom Eingang zum Ausgang (Breite, Länge, Tiefe des Bodens unter dem
     * Gelände in m) [L Länge rund 180 m begehbar; Verlauf und Tiefe genähert].
     */
    public static final double[][] NAHUKU_TUBE = {
            {19.41552, -155.23826, 7.5}, {19.41530, -155.23840, 8.5}, {19.41510, -155.23850, 9.5}, {19.41490, -155.23860, 10.5}, {19.41460, -155.23868, 11},
            {19.41430, -155.23876, 11}, {19.41410, -155.23890, 10}, {19.41390, -155.23910, 7.5}};

    /** Gruben am Eingang und Ausgang (Breite, Länge, Halbmesser m, Tiefe m). */
    public static final double[][] NAHUKU_PITS = {{19.41552, -155.23826, 15, 9}, {19.41390, -155.23910, 13, 8}};

    /** Dampfspalten (Linien, Breite, Länge): Steaming Bluff und die Risse nördlich davon [L Lage, G Verlauf]. */
    public static final double[][][] STEAM_CRACKS = {
            {{19.4322, -155.2632}, {19.4314, -155.2652}, {19.4305, -155.2672}, {19.4298, -155.2690}},
            {{19.4328, -155.2640}, {19.4320, -155.2660}, {19.4311, -155.2681}},
            {{19.4310, -155.2620}, {19.4303, -155.2641}, {19.4294, -155.2662}},
            {{19.4290, -155.2705}, {19.4282, -155.2725}}};
}
