package com.dan.kilauea.terrain;

/**
 * Was sich seit der Höhenmessung (SRTM, Februar 2000) verändert hat, als eigene Schicht über den
 * Grunddaten. Alle Formen sind nach Berichten und Karten des USGS genähert [G]; Lage nach Karte [L].
 * <ul>
 * <li>Gipfel 2018: der Calderaboden um Halemaʻumaʻu sackte in Stufen ab, der Krater weitete und
 * vertiefte sich um mehrere hundert Meter; seit Dezember 2020 füllen Lavasee und Fontänen-Episoden ihn
 * wieder auf. Hier: abgesackter Block rund 2,6 × 2,0 km um 110 m, darin ein Krater von rund 1,4 km
 * mit ebenem Boden auf 830 m.</li>
 * <li>Puʻu ʻŌʻō 2018: der Krater stürzte ein, rund 300 m tief.</li>
 * <li>Kapoho 2018: Lava füllte die Kapoho-Bucht und schuf rund 3,5 km² neues Land; auf dem Feld liegt
 * die Lava 10 bis 20 m dick.</li>
 * </ul>
 * Die Schicht lässt sich abschalten (Gelände von 2000), etwa für die Drehbücher in Phase 12.
 */
public final class Changes2018 {
    private Changes2018() { }

    // Lagen (Breite, Länge) [L, auf rund 100 m genähert]
    static final double[] HMM = {19.4060, -155.2830};
    static final double[] PUU_OO = {19.3880, -155.1050};
    /** Neues Land vor Kapoho (Vieleck, Breite/Länge) [G nach USGS-Karte vom August 2018]. */
    static final double[][] KAPOHO_NEW = {
            {19.5120, -154.8230}, {19.5160, -154.8090}, {19.5050, -154.7990}, {19.4920, -154.8000}, {19.4800, -154.8080},
            {19.4720, -154.8180}, {19.4800, -154.8260}, {19.4980, -154.8250}};
    /** Strom der Spalte 8 bis zum Meer (Vieleck) [G]. */
    static final double[][] KAPOHO_FLOW = {
            {19.4640, -154.9140}, {19.4700, -154.9000}, {19.4900, -154.8700}, {19.5160, -154.8300}, {19.5160, -154.8090},
            {19.4720, -154.8180}, {19.4600, -154.8500}, {19.4560, -154.8900}, {19.4580, -154.9120}};

    private static double[][] xz(double[][] ll) {
        double[][] r = new double[ll.length][];
        for (int i = 0; i < ll.length; i++) r[i] = new double[]{Geo.x(ll[i][0], ll[i][1]), Geo.z(ll[i][0])};
        return r;
    }

    private static final double[][] NEW_XZ = xz(KAPOHO_NEW), FLOW_XZ = xz(KAPOHO_FLOW);

    /** Wendet die Schicht auf die Höhe h an (x, z in m). */
    public static double apply(double x, double z, double h) {
        // Gipfel
        double hx = Geo.x(HMM[0], HMM[1]), hz = Geo.z(HMM[0]);
        double dx = x - hx, dz = z - hz;
        double a = Math.toRadians(35), u = (dx * Math.cos(a) + dz * Math.sin(a)) / 1300, v = (-dx * Math.sin(a) + dz * Math.cos(a)) / 1000;
        double rb = Math.sqrt(u * u + v * v);
        // Ränder unregelmäßig: Ringbrüche folgen alten Spalten, nicht einem Zirkel
        rb += 0.16 * (Noise.fbm(x * 0.0011 + 3, z * 0.0011 - 1, 3) - 0.5) * 2;
        if (rb < 1.2) {
            // zwei Stufen: äußerer Ring 45 m, innerer Block weitere 65 m
            h -= 45 * Island.smooth(1.05, 0.95, rb) + 65 * Island.smooth(0.78, 0.68, rb);
            double rp = Math.hypot(dx * 0.85, dz * 1.1) + 140 * (Noise.fbm(x * 0.002 - 4, z * 0.002 + 6, 3) - 0.5) * 2;
            double floor = 830;
            if (rp < 900 && h > floor) {
                double wall = Island.smooth(760, 600, rp);   // steile Wand von rund 700 m Halbmesser
                h = h + (floor - h) * wall;
            }
        }
        // Puʻu ʻŌʻō
        double px = x - Geo.x(PUU_OO[0], PUU_OO[1]), pz = z - Geo.z(PUU_OO[0]);
        double rp = Math.hypot(px, pz);
        if (rp < 520) h -= 300 * Island.smooth(480, 300, rp);
        // Kapoho: Strom 10–20 m dick, neues Land vor der Küste
        // Ränder des Stroms ausgefranst (Loben), nicht als Vieleck
        double wx = x + 260 * (Noise.fbm(x * 0.0016 + 11, z * 0.0016, 4) - 0.5), wz = z + 260 * (Noise.fbm(x * 0.0016, z * 0.0016 - 7, 4) - 0.5);
        double inFlow = Island.polySoft(FLOW_XZ, wx, wz, 120);
        if (inFlow > 0) h = Math.max(h, h * (1 - inFlow) + (Math.max(h, 0) + 12 + 6 * Noise.value(x * 0.004, z * 0.004)) * inFlow);
        double inNew = Island.polySoft(NEW_XZ, wx, wz, 90);
        if (inNew > 0) h = h * (1 - inNew) + (6 + 8 * Noise.fbm(x * 0.002, z * 0.002, 3)) * inNew;
        return h;
    }

}
