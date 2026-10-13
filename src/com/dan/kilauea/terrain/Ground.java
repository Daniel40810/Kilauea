package com.dan.kilauea.terrain;

/**
 * Höhenabfrage für Kamera und Simulation: aus dem Höhenraster (kubisch, wie der Shader) mit dem
 * Feinrelief ({@link Relief}); solange das Raster noch nicht steht, aus der gerechneten Fläche.
 */
public final class Ground {
    private static volatile HeightGrid grid;

    private Ground() { }

    /** Nach dem Bau des Rasters aufrufen. */
    public static void use(HeightGrid g) { grid = g; }

    public static HeightGrid grid() { return grid; }

    /**
     * Was auf dem Gelände liegt und es verändert (Phase 8: Lava dieses Laufs, Grube des Lavasees): bekommt
     * die Höhe des Geländes und gibt die Höhe der Oberfläche zurück. Kamera und Gehen sehen sie, das
     * Lava-Modell selbst liest {@link #solidHeight}.
     */
    public interface Overlay { double surface(double x, double z, double ground); }

    private static volatile Overlay overlay, shape;

    /** Was auf dem Gelände liegt (Lava dieses Laufs); zählt nur in {@link #height}. */
    public static void overlay(Overlay o) { overlay = o; }

    /** Was das feste Gelände selbst verändert (Grube des Lavasees); zählt auch in {@link #solidHeight}. */
    public static void shape(Overlay o) { shape = o; }

    /** Höhe über dem Meer in Metern (unter Wasser negativ), mit Feinrelief und allem, was darauf liegt. */
    public static double height(double x, double z) {
        double h = solidHeight(x, z);
        Overlay o = overlay;
        return o == null ? h : o.surface(x, z, h);
    }

    /** Höhe des festen Geländes ohne Lava dieses Laufs (mit Feinrelief und der Grube des Lavasees). */
    public static double solidHeight(double x, double z) {
        double h = rawHeight(x, z);
        Overlay o = shape;
        return o == null ? h : o.surface(x, z, h);
    }

    /** Höhe aus den Daten mit Feinrelief, ohne Grube und Lava. */
    public static double rawHeight(double x, double z) {
        HeightGrid g = grid;
        if (g == null) return Island.height(x, z);
        double h = g.height(x, z) + DetailTiles.deltaAt(x, z);
        double rel = Relief.at(x, z, Relief.young(g.age(x, z)), g.geo(x, z, 1), g.cover(x, z, 0), h);
        // Phase 12: Verformung zur Laufzeit (Einsturz 2018) nach dem Relief, wie im Shader
        return h + rel * (1 - Relief.roadFlat(g.road(x, z))) + Deformation.at(x, z);
    }

    /** Höhe nur aus den Daten (ohne Feinrelief). */
    public static double baseHeight(double x, double z) {
        HeightGrid g = grid;
        return g == null ? Island.height(x, z) : g.height(x, z) + DetailTiles.deltaAt(x, z) + Deformation.at(x, z);
    }
}
