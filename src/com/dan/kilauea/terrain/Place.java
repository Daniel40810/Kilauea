package com.dan.kilauea.terrain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Benannte Orte der Szene mit Lage und einem Blick, aus dem die Kamera sie zeigt. Die Lagen sind
 * Richtwerte aus Karten des USGS und des National Park Service [L], auf rund 100 m genähert; in Phase 2
 * werden sie gegen die Höhendaten geprüft. Die Blicke (Richtung, Neigung, Abstand) sind gestaltet [G].
 */
public enum Place {
    //                       Name                          Breite    Länge       Blick°  Neig.  Abstand m  Kurz
    HALEMAUMAU("Halemaʻumaʻu", 19.4072, -155.2834, 200, 28, 2600, "Krater in der Gipfelcaldera, seit 12/2024 mit Fontänen-Episoden"),
    KALUAPELE("Caldera Kaluapele", 19.4080, -155.2780, 160, 38, 9000, "Gipfelcaldera, 2018 um mehrere hundert Meter eingesackt"),
    UEKAHUNA("Uēkahuna-Klippe", 19.4206, -155.2878, 145, 12, 1800, "Nordwestrand der Caldera, Blick über den Krater"),
    KILAUEA_IKI("Kīlauea Iki", 19.4160, -155.2465, 250, 30, 2400, "Kleiner Krater, 1959 Fontänen bis rund 580 m"),
    NAHUKU("Nāhuku (Thurston-Lavaröhre)", 19.4155, -155.2385, 210, 24, 900, "Lavaröhre im Regenwald"),
    MAUNA_ULU("Mauna Ulu", 19.3672, -155.2022, 230, 24, 3200, "Schildkegel der Ostriftzone, 1969–1974, mit Gipfelkrater"),
    PUU_OO("Puʻu ʻŌʻō", 19.3880, -155.1050, 220, 26, 4200, "Kegel der Ostriftzone, Ausbruch 1983–2018"),
    HOLEI_ARCH("Felsbogen Hōlei", 19.2948, -155.0975, 320, 6, 200, "Meeresklippe am Ende der Chain of Craters Road; der 27 m hohe Felsbogen stürzte im September 2026 ins Meer"),
    KAMOKUNA("Kamokuna", 19.3225, -155.0450, 300, 14, 1600, "Meereseintritt 2016/17, Feuerwehrschlauch aus der Klippe"),
    FISSURE8("Spalte 8 (Ahuʻailāʻau)", 19.4625, -154.9085, 210, 22, 1600, "Kegel der Spalte 8, rund 55 m hoch, Ausbruch 2018; nach Nordosten der Lavakanal"),
    KAPOHO("Kapoho", 19.5160, -154.8110, 250, 22, 7000, "2018 unter Lava, rund 3,5 km² neues Land"),
    PUHIMAU("Puhimau-Krater", 19.3932, -155.2485, 200, 32, 1100, "Grubenkrater an der Chain of Craters Road, rund 150 m tief"),
    PAUAHI("Pauahi-Krater", 19.3686, -155.2220, 200, 30, 1500, "Dreifacher Grubenkrater, rund 600 × 400 m, 90 m tief"),
    MAKAOPUHI("Makaopuhi", 19.3657, -155.1700, 200, 30, 3200, "Doppelkrater 1,6 × 1,0 km, 1972–73 teils von Mauna Ulu gefüllt"),
    HOLEI_PALI("Hōlei Pali", 19.3150, -155.1500, 340, 14, 3500, "Steilstufe der Südflanke; die Chain of Craters Road führt in Kehren hinab"),
    HILINA_PALI("Hilina Pali", 19.2953, -155.3077, 20, 18, 4500, "Steilstufe der Südflanke, bis rund 500 m hoch"),
    KAU_DESERT("Kaʻū-Wüste", 19.3800, -155.3600, 60, 20, 6000, "Asche und Spalten im Regenschatten"),
    MAUNA_LOA("Mauna Loa", 19.4721, -155.5922, 110, 20, 30000, "Gipfel 4 169 m, größter Vulkan der Erde"),
    MAUNA_KEA("Mauna Kea", 19.8207, -155.4681, 160, 18, 32000, "Gipfel 4 207 m, Sternwarten");

    public final String label, note;
    public final double lat, lon, viewYaw, viewPitch, viewDist;

    Place(String label, double lat, double lon, double yaw, double pitch, double dist, String note) {
        this.label = label;
        this.lat = lat;
        this.lon = lon;
        this.viewYaw = yaw;
        this.viewPitch = pitch;
        this.viewDist = dist;
        this.note = note;
    }

    public double x() { return Geo.x(lat, lon); }
    public double z() { return Geo.z(lat); }

    public static List<Place> all() { return Collections.unmodifiableList(Arrays.asList(values())); }

    /** Nächster Ort zum Punkt (x, z) und sein Abstand in Metern. */
    public static Place nearest(double x, double z, double[] distOut) {
        Place best = null;
        double bd = Double.MAX_VALUE;
        for (Place p : values()) {
            double d = Math.hypot(p.x() - x, p.z() - z);
            if (d < bd) { bd = d; best = p; }
        }
        if (distOut != null && distOut.length > 0) distOut[0] = bd;
        return best;
    }

    @Override public String toString() { return label; }
}
