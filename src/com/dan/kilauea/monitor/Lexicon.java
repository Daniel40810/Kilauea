package com.dan.kilauea.monitor;

import com.dan.kilauea.camera.Tour;
import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Geology;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wissen zu Orten und Ausbrüchen (Phase 14): was die Infotafel zeigt, wenn man zu einem Ort fliegt oder mit
 * „Was liegt hier?“ auf den Boden unter dem Blick zeigt. Die Texte zu den Ausbrüchen stützen sich auf die
 * Berichte und Seiten des USGS Hawaiian Volcano Observatory und des National Park Service [L]; Alter, Art und
 * Bewuchs an einer Stelle liest die Auskunft aus dem Modell der Insel (Phase 3) [A], dessen Umrisse der Ströme
 * genähert sind.
 */
public final class Lexicon {
    private Lexicon() { }

    /** Eine Tafel: Überschrift, Zeile darunter (Ort, Jahr), Text, und die Werte darin mit Herkunft. */
    public record Info(String kind, String title, String line, String text, List<Reading> readings) { }

    // ------------------------------------------------------------------ Ausbrüche

    /** Kurztexte zu den datierten Strömen und Flächen aus {@link Geology} [L, HVO/NPS]. */
    static final Map<String, String> ERUPTIONS = Map.ofEntries(
            Map.entry("Keaīwa 1823", "Spaltenausbruch an der Südwestriftzone. Sehr dünnflüssige Lava breitete sich in flachen Pāhoehoe-Decken hangabwärts aus."),
            Map.entry("Ostriftzone 1840", "Rund vier Wochen: Das Magma lief vom Gipfel bis in die untere Ostriftzone, die Lava erreichte bei Nanawale das Meer."),
            Map.entry("Mauna Iki 1919–20", "Acht Monate an der Südwestriftzone; aus den Strömen wuchs der flache Schild Mauna Iki."),
            Map.entry("Puna 1955", "88 Tage in der unteren Ostriftzone; Ströme bedeckten Felder, Straßen und Häuser in Puna."),
            Map.entry("Kapoho 1960", "Januar und Februar 1960: Fontänen in der unteren Ostriftzone, die Ströme begruben das Dorf Kapoho und bauten neues Land an der Küste."),
            Map.entry("Chain of Craters 1965–69", "Mehrere kurze Spaltenausbrüche zwischen den Grubenkratern der oberen Ostriftzone, kurz vor Mauna Ulu."),
            Map.entry("Mauna Ulu 1969–74", "Fünf Jahre Ausbruch: zuerst Fontänen bis 540 m, dann ein Lavasee und Röhren bis ins Meer; es wuchs ein Schild von 121 m."),
            Map.entry("Südwestriftzone 1971", "Kurzer Spaltenausbruch in der Südwestriftzone."),
            Map.entry("Südwestriftzone 1974", "Kurzer Spaltenausbruch in der Südwestriftzone, Ende 1974."),
            Map.entry("Kalalua 1977", "Spaltenausbruch in der mittleren Ostriftzone im Herbst 1977."),
            Map.entry("Puʻu ʻŌʻō 1983–86", "Der Beginn von 35 Jahren Ausbruch: 44 Fontänen-Episoden, aus Schlacke wuchs der Kegel Puʻu ʻŌʻō."),
            Map.entry("Kupaianaha 1986–92", "Ruhiger Ausfluss aus einem flachen Schild nahe Puʻu ʻŌʻō; Röhren trugen die Lava bis ins Meer, 1990 wurde Kalapana überflossen."),
            Map.entry("Puʻu ʻŌʻō Flanke 1992–2007", "Schlote an der Flanke des Puʻu ʻŌʻō speisten Röhren bis zur Südküste, mit vielen Meereseintritten."),
            Map.entry("Thanksgiving Eve 2007–11", "Ab November 2007 eine Spalte östlich des Puʻu ʻŌʻō („Thanksgiving Eve Breakout“); Ströme bis zur Küste bei Kalapana."),
            Map.entry("Kahaualeʻa 2011–13", "Ströme nach Norden in den Wald von Kahaualeʻa."),
            Map.entry("27. Juni 2014", "Der Strom vom 27. Juni lief rund 20 km nach Nordosten und bedrohte 2014/15 den Ort Pāhoa."),
            Map.entry("Strom 61g 2016–17", "Aus Schlot 61g an der Ostflanke des Puʻu ʻŌʻō lief die Lava in einer Röhre bis Kamokuna und fiel dort ins Meer."),
            Map.entry("Spalte 8 2018", "Spalte 8 (Ahuʻailāʻau) förderte bis rund 100 m³/s; ein Kanal trug die Lava nach Kapoho und füllte die Bucht."),
            Map.entry("Leilani-Spalten 2018", "Ab dem 3. Mai 2018 öffneten sich 24 Spalten in Leilani Estates; über 700 Gebäude liegen unter Lava."),
            Map.entry("Nāpau 2024", "Kurzer Ausbruch im September 2024 nahe dem Nāpau-Krater in der mittleren Ostriftzone."),
            Map.entry("Mauna Loa 1859", "Der längste Strom des Mauna Loa in historischer Zeit: rund 51 km bis an die Nordwestküste."),
            Map.entry("Mauna Loa 1950 (Hoʻōpūloa)", "Einer der stärksten Ausbrüche des Mauna Loa; dieser Strom erreichte das Meer nach wenigen Stunden."),
            Map.entry("Mauna Loa 1984", "März und April 1984; die Ströme liefen nach Nordosten Richtung Hilo und hielten weit vor der Stadt."),
            Map.entry("Mauna Loa 2022", "27. November bis 10. Dezember 2022; der Strom hielt wenige Kilometer vor der Saddle Road."),
            Map.entry("Huʻehuʻe 1801", "Ausbruch des Hualālai; auf diesem Strom liegt heute der Flughafen von Kona."),
            Map.entry("Kaʻūpūlehu 1801", "Ausbruch des Hualālai, der Strom lief bis an die Westküste."),
            Map.entry("Kaluapele, Boden 1971–82", "Der Boden der Gipfelcaldera aus Ausbrüchen der 1970er und frühen 1980er Jahre."),
            Map.entry("Kīlauea Iki, Lavasee 1959", "Der See von 1959, bis zu 126 m tief; innen jahrzehntelang heiß. Der Kīlauea Iki Trail quert ihn."),
            Map.entry("Puʻu Puaʻi 1959", "Ein Hügel aus Bims und Schlacke, die der Wind 1959 aus der Fontäne nach Südwesten trug."),
            Map.entry("Halemaʻumaʻu, Lavasee 2020–25", "Seit Dezember 2020 wiederholt Lavaseen und Fontänen im Krater; jede Episode legt eine neue Lage auf den Boden."),
            Map.entry("Mauna Ulu, Kegel 1974", "Der Gipfel des Schildes von Mauna Ulu mit seinem Krater."),
            Map.entry("Puʻu ʻŌʻō, Kegel 2018", "Der Kegel aus 35 Jahren Ausbruch; im April 2018 brach sein Krater ein."),
            Map.entry("Spalte 8, Kegel 2018", "Der Schlackenkegel von Spalte 8, rund 55 m hoch."),
            Map.entry("Kapoho, neues Land 2018", "Wo bis Juni 2018 die Kapoho-Bucht lag, ist heute Land."),
            Map.entry("Mokuʻāweoweo, Boden 1984", "Die Gipfelcaldera des Mauna Loa; ihr Boden stammt aus dem Ausbruch von 1984."));

    public static String eruptionText(String name) {
        String t = ERUPTIONS.get(name);
        if (t != null) return t;
        if (name.startsWith("Mauna Loa")) return "Ein Ausbruch des Mauna Loa.";
        return "Ein datierter Ausbruch.";
    }

    // ------------------------------------------------------------------ Orte

    /** Tafel zu einem benannten Ort: Kurztext des Orts und, wenn ein Rundflug dort hält, dessen Text [L]. */
    public static Info place(Place p) {
        StringBuilder t = new StringBuilder(p.note).append('.');
        stops:
        for (Tour tour : Tour.all())
            for (Tour.Stop s : tour.stops)
                if (s.label().equals(p.label)) {
                    t.append(' ').append(s.text());
                    break stops;   // nur der erste Rundflug, der dort hält
                }
        String text = t.toString();
        if (!text.contains("[L")) text += " [L, USGS/NPS]";
        double h = Ground.grid() != null ? Math.max(0, Ground.height(p.x(), p.z())) : Double.NaN;
        List<Reading> r = new ArrayList<>();
        r.add(Reading.word("Lage", coords(p.lat, p.lon), Prov.L, "Karten von USGS und NPS, auf rund 100 m"));
        if (!Double.isNaN(h)) r.add(Reading.of("Höhe", h, "m", Prov.M, "Höhenmodell der Insel"));
        String line = coords(p.lat, p.lon) + (Double.isNaN(h) ? "" : " · " + metres(h) + " " + Prov.M.tag());
        return new Info("Ort", p.label, line, text, r);
    }

    // ------------------------------------------------------------------ Boden

    /** Was liegt an (x, z)? Lava dieses Laufs, ein datierter Strom oder älteres Gestein, dazu Bewuchs und Regen. */
    public static Info ground(double x, double z, LavaField field) {
        HeightGrid g = Ground.grid();
        double lat = Geo.lat(z), lon = Geo.lon(x, z);
        List<Reading> r = new ArrayList<>();
        double h = g != null ? Ground.height(x, z) : Double.NaN;
        String line = coords(lat, lon) + (Double.isNaN(h) ? "" : " · " + metres(h) + " " + Prov.M.tag());
        if (!Double.isNaN(h) && h < 0)
            return new Info("Auskunft", "Meer", line, String.format(Locale.GERMAN, "Hier ist das Meer rund %.0f m tief [G: Meeresboden aus dem Abstand zur Küste].", -h),
                    List.of(Reading.of("Tiefe", -h, "m", Prov.G, "Meeresboden gestaltet")));
        // 1. Lava dieses Laufs
        if (field != null) {
            LavaField.Probe p = new LavaField.Probe();
            boolean hit;
            synchronized (field) { hit = field.probe(x, z, p); }
            if (hit && p.known && p.thickness > 0.05) {
                r.add(Reading.of("Oberfläche", p.temperature, "°C", Prov.A, "Lava-Modell"));
                r.add(Reading.of("Dicke", p.thickness, "m", Prov.A, "Lava-Modell"));
                r.add(Reading.of("ʻaʻā-Anteil", 100 * p.aa, "%", Prov.A, "Lava-Modell"));
                String state = p.temperature > 600 ? "glühend, sie fließt" : p.temperature > 100 ? "mit heißer Kruste" : "erstarrt";
                String text = String.format(Locale.GERMAN, "Lava dieses Ausbruchs, %s: Oberfläche rund %.0f °C, %.1f m dick, %.0f %% ʻaʻā%s%s [A, Lava-Modell].",
                        state, p.temperature, p.thickness, 100 * p.aa, p.tube ? ", unter einem Röhrendach" : "", p.skylight ? ", mit Oberlicht" : "");
                return new Info("Auskunft", "Lava dieses Laufs", line, text, r);
            }
        }
        if (g == null) return new Info("Auskunft", "Gelände lädt", line, "Das Gelände ist noch nicht gebaut.", r);
        int code = g.geoNearest(x, z, 0);
        double age = Geology.ageOf(code);
        double aa = g.geo(x, z, 1), veg = g.cover(x, z, 1), ash = g.cover(x, z, 2), rain = g.geo(x, z, 3) * Geology.RAIN_MAX;
        int year = (int) Math.round(Geology.NOW - age);
        // 2. datierter Strom oder Fläche
        String name = null;
        int nameYear = 0;
        double tol = Math.max(1.5, 0.06 * (age + 1));
        double best = Double.MAX_VALUE;
        for (Geology.Patch pa : Geology.PATCHES) {
            if (Math.abs(Geology.NOW - pa.year() - age) > tol) continue;
            double dx = x - Geo.x(pa.lat(), pa.lon()), dz = z - Geo.z(pa.lat());
            double rot = Math.toRadians(pa.rot()), u = dx * Math.cos(rot) - dz * Math.sin(rot), v = dx * Math.sin(rot) + dz * Math.cos(rot);
            double e = Math.hypot(u / pa.rx(), v / pa.rz());
            if (e < 1.3 && e * 100 < best) { best = e * 100; name = pa.name(); nameYear = pa.year(); }
        }
        if (name == null) {
            for (Geology.Flow f : Geology.FLOWS) {
                if (Math.abs(Geology.NOW - f.year() - age) > tol) continue;
                double d = trackDistance(f, x, z);
                if (d < f.lengthKm() * 1000 + 2500 && d < best) { best = d; name = f.name(); nameYear = f.year(); }
            }
        }
        String vegText = veg > 0.75 ? "dichter Wald" : veg > 0.45 ? "lichter Wald und Farne" : veg > 0.2 ? "Gras, Farne, erste ʻōhiʻa" : veg > 0.06 ? "Flechten und einzelne Pflanzen" : "kaum Bewuchs";
        String type = aa > 0.6 ? "ʻaʻā (rau, aus Klinkern)" : aa > 0.3 ? "Pāhoehoe und ʻaʻā gemischt" : "Pāhoehoe (glatt, mit Stricken)";
        r.add(Reading.of("Alter", Math.round(age), "Jahre", name != null ? Prov.L : Prov.A, name != null ? "Jahr des Ausbruchs nach HVO" : "Alterscode der Insel"));
        r.add(Reading.word("Art", type, Prov.A, "ʻaʻā-Anteil aus Tempo und Gefälle"));
        r.add(Reading.word("Bewuchs", vegText, Prov.A, "aus Alter und Regen"));
        r.add(Reading.of("Regen", rain, "mm/Jahr", Prov.A, "Passatregen aus Höhe und Hang"));
        String ashText = ash > 0.3 ? " Darauf liegt Asche." : "";
        String text;
        String title;
        if (name != null) {
            title = name;
            line = (name.matches(".*\\d{4}.*") ? "datierter Ausbruch " + Prov.L.tag() : "Ausbruch " + nameYear + " " + Prov.L.tag()) + " · " + line;
            String et = eruptionText(name);
            if (et.endsWith(".")) et = et.substring(0, et.length() - 1);
            text = et + " [L]. Hier: " + type + ", " + vegText + "; rund " + String.format(Locale.GERMAN, "%,d", Math.round(rain / 100) * 100) + " mm Regen im Jahr [A]." + ashText;
        } else {
            title = age < 300 ? "Strom um " + year : age < 1500 ? "Älterer Strom" : "Altes Gestein";
            String ageT = age < 300 ? "rund " + Math.round(age) + " Jahre" : age < 1500 ? "einige hundert Jahre" : age < 10_000 ? "einige tausend Jahre" : "zehntausende Jahre";
            text = "Lava, " + ageT + " alt [A, Alterscode der Insel]: " + type + ", " + vegText + "; rund " + String.format(Locale.GERMAN, "%,d", Math.round(rain / 100) * 100)
                    + " mm Regen im Jahr [A]." + ashText + " Rund 90 % der Oberfläche des Kīlauea sind jünger als 1 100 Jahre [L, USGS].";
        }
        return new Info("Auskunft", title, line, text, r);
    }

    /** Abstand (m) von (x, z) zur Spur eines Stroms: Spalte und Wegpunkte als Linienzug. */
    static double trackDistance(Geology.Flow f, double x, double z) {
        List<double[]> pts = new ArrayList<>();
        for (double[] p : f.fissure()) pts.add(new double[]{Geo.x(p[0], p[1]), Geo.z(p[0])});
        if (f.via() != null) for (double[] p : f.via()) pts.add(new double[]{Geo.x(p[0], p[1]), Geo.z(p[0])});
        double best = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            double[] a = pts.get(i), b = i + 1 < pts.size() ? pts.get(i + 1) : a;
            double dx = b[0] - a[0], dz = b[1] - a[1], l2 = dx * dx + dz * dz;
            double t = l2 > 0 ? Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / l2)) : 0;
            best = Math.min(best, Math.hypot(x - a[0] - t * dx, z - a[1] - t * dz));
        }
        return best;
    }

    static String coords(double lat, double lon) {
        return String.format(Locale.GERMAN, "%.3f° N · %.3f° W", lat, -lon);
    }

    static String metres(double h) {
        return String.format(Locale.GERMAN, "%,.0f m", h).replace('.', ' ');
    }
}
