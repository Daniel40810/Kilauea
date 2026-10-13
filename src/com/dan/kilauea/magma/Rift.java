package com.dan.kilauea.magma;

import com.dan.kilauea.terrain.Geo;

/**
 * Die Riftzonen als Wege, denen ein Gang folgt (Breite, Länge) [L Lage der Krater und Kegel entlang
 * der Riftzonen nach USGS-Karte; der Weg in der Tiefe folgt ihnen, G].
 */
public enum Rift {
    /** Ostriftzone: Keanakākoʻi, Puhimau, Pauahi, Mauna Ulu, Makaopuhi, Nāpau, Puʻu ʻŌʻō, Heiheiahulu, Leilani, Kapoho. */
    EAST("Ostriftzone", new double[][]{
            {19.3950, -155.2760}, {19.4000, -155.2640}, {19.3932, -155.2485}, {19.3744, -155.2297}, {19.3686, -155.2220},
            {19.3672, -155.2022}, {19.3657, -155.1700}, {19.3700, -155.1430}, {19.3880, -155.1050}, {19.4100, -155.0300},
            {19.4400, -154.9700}, {19.4625, -154.9085}, {19.4900, -154.8600}, {19.5100, -154.8250}}),
    /** Südwestriftzone: von der Südcaldera durch die Kaʻū-Wüste zum Meer. */
    SOUTHWEST("Südwestriftzone", new double[][]{
            {19.3950, -155.2760}, {19.3850, -155.2950}, {19.3700, -155.3150}, {19.3500, -155.3350}, {19.3250, -155.3550},
            {19.2950, -155.3800}});

    public final String label;
    /** Weg in der Szene (x, z) und Bogenlänge an jedem Punkt (m). */
    public final double[][] path;
    public final double[] arc;

    Rift(String label, double[][] latLon) {
        this.label = label;
        path = new double[latLon.length][];
        arc = new double[latLon.length];
        for (int i = 0; i < latLon.length; i++) {
            path[i] = new double[]{Geo.x(latLon[i][0], latLon[i][1]), Geo.z(latLon[i][0])};
            if (i > 0) arc[i] = arc[i - 1] + Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
        }
    }

    public double length() { return arc[arc.length - 1]; }

    @Override public String toString() { return label; }

    /** Punkt (x, z) auf dem Weg nach s Metern ab der Südcaldera. */
    public double[] at(double s) {
        s = Math.max(0, Math.min(length(), s));
        int i = 1;
        while (i < arc.length - 1 && arc[i] < s) i++;
        double f = (s - arc[i - 1]) / Math.max(1e-9, arc[i] - arc[i - 1]);
        return new double[]{path[i - 1][0] + (path[i][0] - path[i - 1][0]) * f, path[i - 1][1] + (path[i][1] - path[i - 1][1]) * f};
    }
}
