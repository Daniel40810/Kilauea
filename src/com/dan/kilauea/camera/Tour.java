package com.dan.kilauea.camera;

import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Rundflug (Phase 13): Stationen mit Ziel, Blick, Verweildauer und einem Text für die Infotafel. Zwischen den
 * Stationen fliegt die Kamera ({@link OrbitCamera#lookAt}), an jeder kreist sie langsam. Die Texte stützen sich auf
 * USGS und NPS [L]; Blicke und Zeiten sind gestaltet [G].
 */
public final class Tour {
    /** Eine Station: Ziel (x, z), Blick (Gierwinkel der Kamera, °), Neigung (°), Abstand (m), Verweilen (s). */
    public record Stop(String label, String title, String text, double x, double z, double yawDeg, double pitchDeg, double dist, double hold) { }

    public final String id, title, summary;
    public final List<Stop> stops;

    private Tour(String id, String title, String summary, List<Stop> stops) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.stops = List.copyOf(stops);
    }

    @Override public String toString() { return title; }

    // ------------------------------------------------------------------ Baukasten

    static final class B {
        final List<Stop> s = new ArrayList<>();

        /** Station an einem benannten Ort mit seinem Blick. */
        B at(Place p, String title, String text, double hold) {
            s.add(new Stop(p.label, title, text, p.x(), p.z(), p.viewYaw, p.viewPitch, p.viewDist, hold));
            return this;
        }

        /** Station an einem Ort mit eigenem Blick: Kompassrichtung (0 = Nord), Neigung, Abstand. */
        B at(Place p, String title, String text, double bearing, double pitch, double dist, double hold) {
            s.add(new Stop(p.label, title, text, p.x(), p.z(), yaw(bearing), pitch, dist, hold));
            return this;
        }

        /** Station an Breite und Länge. */
        B at(String label, double lat, double lon, String title, String text, double bearing, double pitch, double dist, double hold) {
            s.add(new Stop(label, title, text, Geo.x(lat, lon), Geo.z(lat), yaw(bearing), pitch, dist, hold));
            return this;
        }

        static double yaw(double bearing) { return ((360 - bearing) % 360 + 360) % 360; }
    }

    public static List<Tour> all() { return List.of(summit(), riftToSea(), eruption2018()); }

    public static Tour byId(String id) {
        for (Tour t : all()) if (t.id.equals(id)) return t;
        throw new IllegalArgumentException("kein Rundflug " + id);
    }

    public static Tour summit() {
        return new Tour("gipfel", "Rundflug: Der Gipfel", "Caldera, Uēkahuna, Halemaʻumaʻu, Steaming Bluff, Kīlauea Iki und Nāhuku.", new B()
                .at(Place.KALUAPELE, "Kaluapele", "Die Gipfelcaldera des Kīlauea, rund 4 × 3 km groß. Im Sommer 2018 sackte ihr Boden um den "
                        + "Halemaʻumaʻu in 62 Stößen um bis zu 500 m ab [L].", 9)
                .at(Place.UEKAHUNA, "Uēkahuna-Klippe", "Der höchste Punkt am Calderarand, über 1 240 m. Hier stand bis 2018 das Observatorium "
                        + "des USGS; die Beben beim Einsturz beschädigten das Gebäude [L].", 8)
                .at(Place.HALEMAUMAU, "Halemaʻumaʻu", "Das „Haus des ewigen Feuers“. Ein Lavasee füllte es von 1823 bis 1924 und von 2008 "
                        + "bis 2018; seit Dezember 2024 werfen Fontänen-Episoden Lava auf den Kraterboden [L].", 9)
                .at("Steaming Bluff", 19.4306, -155.2668, "Steaming Bluff", "Regenwasser sickert in den warmen Boden und steigt als Dampf "
                        + "aus Spalten am Calderarand auf, am Morgen am deutlichsten [L].", 200, 18, 900, 7)
                .at(Place.KILAUEA_IKI, "Kīlauea Iki", "1959 füllten Fontänen bis 580 m den Krater mit einem See aus Lava, bis 126 m tief. "
                        + "Der Weg führt heute über seine Kruste [L].", 9)
                .at(Place.NAHUKU, "Nāhuku", "Die Thurston-Lavaröhre: rund 200 m Röhre im Regenwald aus ʻōhiʻa und Baumfarnen, "
                        + "vor etwa 500 Jahren entstanden [L]. Mit „Gehen“ kann man hindurch.", 8)
                .at(Place.KALUAPELE, "Zurück über der Caldera", "Ende des Rundflugs. Ziehen mit der Maus oder eine Taste übernimmt "
                        + "jederzeit die Kamera.", 30, 40, 14_000, 6)
                .s);
    }

    public static Tour riftToSea() {
        return new Tour("rift", "Rundflug: Riftzone bis zum Meer", "Die Chain of Craters Road entlang: Grubenkrater, Mauna Ulu, Puʻu ʻŌʻō, die Pali und die Küste.", new B()
                .at(Place.HALEMAUMAU, "Halemaʻumaʻu", "Hier beginnt die Ostriftzone. Unter dem Gipfel speist der Speicher die Gänge, "
                        + "die die Riftzone hinablaufen [L].", 120, 30, 4500, 6)
                .at(Place.PUHIMAU, "Puhimau-Krater", "Ein Grubenkrater, rund 150 m tief. Grubenkrater entstehen, wenn Gestein über "
                        + "einem geleerten Magmaraum einbricht [L].", 7)
                .at(Place.PAUAHI, "Pauahi-Krater", "Drei Gruben, zusammen rund 600 × 400 m und 90 m tief; 1973 und 1979 brach "
                        + "auch hier Lava aus [L].", 7)
                .at(Place.MAUNA_ULU, "Mauna Ulu", "Von 1969 bis 1974 wuchs hier ein Schild von 121 m; seine Ströme deckten die "
                        + "Straße über 19 km zu und liefen bis ins Meer [L].", 8)
                .at(Place.MAKAOPUHI, "Makaopuhi", "Ein Doppelkrater von 1,6 km, 1972–73 teils von Mauna Ulu gefüllt [L].", 6)
                .at(Place.PUU_OO, "Puʻu ʻŌʻō", "35 Jahre Ausbruch von 1983 bis 2018: erst Fontänen bis rund 460 m, dann Röhren bis zur Küste. "
                        + "Am 30. April 2018 stürzte der Krater ein [L].", 9)
                .at(Place.HOLEI_PALI, "Hōlei Pali", "Eine Steilstufe der Südflanke, die langsam ins Meer rutscht. Die Straße führt in "
                        + "Kehren hinab [L].", 7)
                .at(Place.HOLEI_ARCH, "Felsbogen Hōlei", "Am Ende der Straße stand ein 27 m hoher Bogen in der Meeresklippe; er stürzte im "
                        + "September 2026 ein [L].", 30, 14, 600, 7)
                .at(Place.KAMOKUNA, "Kamokuna", "2016/17 lief hier Lava ins Meer, baute ein Delta, das am 31. Dezember 2016 abbrach, "
                        + "und fiel als Feuerwehrschlauch aus der Klippe [L].", 8)
                .at("Ostriftzone", 19.3500, -155.1300, "Die ganze Strecke", "Rund 30 km vom Gipfel bis zur Küste. Ende des Rundflugs.",
                        330, 45, 30_000, 6)
                .s);
    }

    public static Tour eruption2018() {
        double[] ll = {19.4580, -154.9260};
        return new Tour("2018", "Rundflug: Der Ausbruch 2018", "Vom eingestürzten Gipfel über Puʻu ʻŌʻō nach Leilani Estates, Spalte 8 und Kapoho.", new B()
                .at(Place.HALEMAUMAU, "Der Gipfel", "Im Mai 2018 lief das Magma aus dem Gipfel nach Osten ab. Der Lavasee verschwand, und bis "
                        + "August sackte der Boden der Caldera um über 500 m ab [L].", 160, 34, 6000, 8)
                .at(Place.PUU_OO, "Puʻu ʻŌʻō", "30. April 2018: Der Kraterboden bricht ein, ein Gang dringt die Ostriftzone hinab [L].", 8)
                .at("Leilani Estates", ll[0], ll[1], "Leilani Estates", "Ab dem 3. Mai öffnen sich zwischen Häusern 24 Spalten auf rund "
                        + "6 km [L].", 30, 30, 4500, 8)
                .at(Place.FISSURE8, "Spalte 8", "Ahuʻailāʻau: Ab Ende Mai fördert fast nur noch Spalte 8, mit Fontänen bis 80 m. "
                        + "Ihr Kegel steht rund 55 m hoch [L].", 8)
                .at("Lavakanal", 19.4900, -154.8700, "Der Kanal", "Ein Kanal trug die Lava rund 13 km nach Nordosten, zeitweise mit "
                        + "Wellen, die über die Dämme schwappten [L].", 220, 30, 5000, 7)
                .at(Place.KAPOHO, "Kapoho", "Am 3. Juni erreichte die Lava die Kapoho-Bucht und füllte sie; 875 acres (3,5 km²) neues "
                        + "Land entstanden [L].", 8)
                .at("Untere Ostriftzone", 19.4700, -154.9000, "Bilanz", "Rund 1,5 km³ Lava, über 700 Gebäude unter Lava, der Gipfel über "
                        + "500 m tiefer. Ende des Rundflugs [L].", 250, 50, 32_000, 6)
                .s);
    }
}
