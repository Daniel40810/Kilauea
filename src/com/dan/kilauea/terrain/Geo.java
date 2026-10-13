package com.dan.kilauea.terrain;

/**
 * Lage auf der Erde und in der Szene. Die Szene rechnet in Metern in einer Tangentialebene um den
 * Ursprung am Krater Halemaʻumaʻu: x nach Osten, y nach oben, z nach Süden (wie in Kneiphof).
 * <p>
 * Über 100 km weicht die Ebene von der Kugel um weniger als 0,1 % in der Lage ab; die Krümmung der Erde
 * (rund 800 m Absenkung in 100 km Entfernung) rechnet der Gelände-Shader getrennt ein, siehe
 * {@link #curvatureDrop(double)}.
 * <p>
 * Herkunft: Ursprung 19,4072° N, 155,2834° W [L, USGS-Karte, auf ±50 m genähert]; Erdradius
 * 6 371 008,8 m [L, mittlerer Radius IUGG].
 */
public final class Geo {
    /** Ursprung der Szene: Halemaʻumaʻu. */
    public static final double LAT0 = 19.4072, LON0 = -155.2834;
    public static final double R_EARTH = 6_371_008.8;
    private static final double M_PER_DEG = Math.PI / 180.0 * R_EARTH;
    private static final double COS_LAT0 = Math.cos(Math.toRadians(LAT0));

    private Geo() { }

    /** Ost in Metern zur Länge (Grad, West negativ). */
    public static double x(double lat, double lon) { return (lon - LON0) * M_PER_DEG * Math.cos(Math.toRadians(0.5 * (lat + LAT0))); }

    /** Süd in Metern zur Breite. */
    public static double z(double lat) { return -(lat - LAT0) * M_PER_DEG; }

    public static double lat(double z) { return LAT0 - z / M_PER_DEG; }

    public static double lon(double x, double z) {
        double lat = lat(z);
        return LON0 + x / (M_PER_DEG * Math.cos(Math.toRadians(0.5 * (lat + LAT0))));
    }

    /** Absenkung eines Punktes in Entfernung d (m) unter die Tangentialebene: d² / 2R. */
    public static double curvatureDrop(double d) { return d * d / (2 * R_EARTH); }

    /** Breite und Länge als Text, zum Beispiel „19,407° N · 155,283° W“. */
    public static String label(double x, double z) {
        double la = lat(z), lo = lon(x, z);
        return String.format(java.util.Locale.GERMAN, "%.3f° %s · %.3f° %s", Math.abs(la), la >= 0 ? "N" : "S", Math.abs(lo), lo >= 0 ? "O" : "W");
    }

    static double cosLat0() { return COS_LAT0; }
}
