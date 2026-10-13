package com.dan.kilauea.db;

import com.dan.kilauea.monitor.Lexicon;
import com.dan.kilauea.monitor.Station;
import com.dan.kilauea.script.Script;
import com.dan.kilauea.script.Scripts;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Geology;
import com.dan.kilauea.terrain.Ground;
import com.dan.kilauea.terrain.HeightGrid;
import com.dan.kilauea.terrain.Place;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 16b: die Grunddaten aus dem Code (Frage 20 = A: der Code bleibt die Quelle). Schreibt db/06_grunddaten.sql mit
 * 19 Orten, 14 Kratern, 49 Strömen und Flächen, 12 Stationen, 6 Drehbüchern und 29 Kapiteln. Jede Zeile wird nur eingefügt,
 * wenn es sie noch nicht gibt; jede Geometrie nur gesetzt, wenn die Spalte noch leer ist. Was du in der Datenbank änderst,
 * bleibt also.
 * <p>
 * Gerechnet [A]: Höhe der Orte aus dem Höhenmodell; Rand, Tiefe und Maße der Krater aus dem Gelände (Rand am Ende des
 * höchsten Anstiegs, in 72 Richtungen, siehe {@link #miss}); Achse und Umriss der Ströme genau wie in {@link Geology} (gleicher
 * Zufallskeim), der Umriss als sichtbarer Teil auf dem 75-m-Raster, Douglas–Peucker mit 25 m. Das ist nicht die geologische
 * Karte.
 */
public final class Grunddaten {
    private Grunddaten() { }

    /** Toleranz beim Vereinfachen (m). */
    public static final double TOL_UMRISS = 25, TOL_ACHSE = 15;
    /** Zahlen je Stück beim Laden großer Geometrien (unter der Grenze von 999 Argumenten). */
    static final int STUECK = 800;

    /** Ein Krater: Name, Art, Mitte, Halbachsen der modellierten Form (m, wie in Island und DetailTiles), Ort, entstanden (Jahr oder 0), Kurztext, Marke der Lage. */
    public record KraterDef(String name, String art, double lat, double lon, double ra, double rb, Place ort, int entstanden, String text, String marke) { }

    /** Die Krater [Lage L nach USGS/NPS-Karten bzw. wie im Gelände; Form A aus dem Gelände]. */
    public static final List<KraterDef> KRATER = List.of(
            new KraterDef("Kaluapele", "CALDERA", 19.4085, -155.2790, 2900, 1900, Place.KALUAPELE, 1500,
                    "Gipfelcaldera des Kīlauea, um 1500 entstanden, 2018 um mehrere hundert Meter eingesackt.", "L"),
            new KraterDef("Halemaʻumaʻu", "KRATER", 19.4072, -155.2834, 900, 750, Place.HALEMAUMAU, 2018,
                    "Krater in der Caldera; 2018 eingestürzt, seit 2020 mit Lavaseen und Fontänen wieder gefüllt.", "L"),
            new KraterDef("Kīlauea Iki", "KRATER", 19.4160, -155.2465, 850, 420, Place.KILAUEA_IKI, 1959,
                    "Kleiner Krater östlich der Caldera; 1959 Fontänen bis rund 580 m und ein Lavasee bis 126 m tief.", "L"),
            new KraterDef("Keanakākoʻi", "GRUBENKRATER", 19.4008, -155.2643, 420, 380, null, 0,
                    "Grubenkrater am Südrand der Caldera; Namensgeber der Asche von 1790.", "L"),
            new KraterDef("Lua Manu", "GRUBENKRATER", 19.3970, -155.2497, 105, 85, null, 0,
                    "Kleiner Grubenkrater an der Chain of Craters Road.", "L"),
            new KraterDef("Puhimau", "GRUBENKRATER", 19.3932, -155.2485, 170, 150, Place.PUHIMAU, 0,
                    "Grubenkrater an der Chain of Craters Road, rund 150 m tief.", "L"),
            new KraterDef("Koʻokoʻolau", "GRUBENKRATER", 19.3868, -155.2420, 120, 95, null, 0,
                    "Grubenkrater an der Chain of Craters Road.", "L"),
            new KraterDef("Hiʻiaka", "GRUBENKRATER", 19.3744, -155.2297, 260, 150, null, 0,
                    "Grubenkrater an der Chain of Craters Road.", "L"),
            new KraterDef("Pauahi", "GRUBENKRATER", 19.3686, -155.2220, 305, 200, Place.PAUAHI, 0,
                    "Dreifacher Grubenkrater, rund 600 × 400 m und 90 m tief.", "L"),
            new KraterDef("Mauna Ulu", "KEGELKRATER", 19.3672, -155.2022, 150, 120, Place.MAUNA_ULU, 1974,
                    "Gipfelkrater des Schildes von Mauna Ulu, 1969–1974.", "L"),
            new KraterDef("Makaopuhi", "GRUBENKRATER", 19.3657, -155.1700, 800, 500, Place.MAKAOPUHI, 0,
                    "Doppelkrater 1,6 × 1,0 km, 1972–73 teils von Mauna Ulu gefüllt.", "L"),
            new KraterDef("Puʻu ʻŌʻō", "KEGELKRATER", 19.3880, -155.1050, 420, 420, Place.PUU_OO, 2018,
                    "Krater im Kegel des Puʻu ʻŌʻō; im April 2018 eingebrochen.", "L"),
            new KraterDef("Ahuʻailāʻau (Spalte 8)", "KEGELKRATER", 19.4602, -154.9100, 120, 120, Place.FISSURE8, 2018,
                    "Krater im Schlackenkegel der Spalte 8, Ausbruch 2018.", "L"),
            new KraterDef("Mokuʻāweoweo", "CALDERA", 19.4721, -155.5922, 2400, 1400, Place.MAUNA_LOA, 0,
                    "Gipfelcaldera des Mauna Loa.", "L"));

    /** Art eines Ortes (KIL_ORT_ART_CK). */
    public static String art(Place p) {
        switch (p) {
            case HALEMAUMAU: case KILAUEA_IKI: case PUHIMAU: case PAUAHI: case MAKAOPUHI: return "KRATER";
            case KALUAPELE: return "CALDERA";
            case UEKAHUNA: return "KLIPPE";
            case NAHUKU: return "ROEHRE";
            case MAUNA_ULU: case PUU_OO: case FISSURE8: return "KEGEL";
            case HOLEI_ARCH: case KAMOKUNA: return "KUESTE";
            case HOLEI_PALI: case HILINA_PALI: return "PALI";
            case KAU_DESERT: return "WUESTE";
            case MAUNA_LOA: case MAUNA_KEA: return "VULKAN";
            default: return "GEBIET";
        }
    }

    /** Schauplatz eines Drehbuchs. */
    static Place schauplatz(String id) {
        switch (id) {
            case "1959": return Place.KILAUEA_IKI;
            case "maunaulu": return Place.MAUNA_ULU;
            case "puuoo": return Place.PUU_OO;
            case "2018": return Place.FISSURE8;
            case "2024": return Place.HALEMAUMAU;
            default: return null;
        }
    }

    /** Ein gemessener Krater. */
    public static final class Krater {
        public final KraterDef def;
        public double[] rand;            // Ring (Ost, Nord) in m, gegen den Uhrzeigersinn
        public double tiefe, laenge, breite, flaecheHa, randHoehe;
        Krater(KraterDef d) { def = d; }
    }

    /** Ein Strom oder eine Fläche mit Geometrien (Ost, Nord in m). */
    public static final class Strom {
        public String name, vulkan, art;
        public int jahrVon, jahrBis, jahrModell;
        public double[] schlot, spalte, achse;
        public List<Geometrie.Polygon> umriss = new ArrayList<>();
        public double laengeKm, breiteM, aaNah, aaFern, flaecheKm2;
        public String text;
    }

    /** Das Ergebnis: das Skript und alles, woraus es besteht (für Prüfung und Bild). */
    public static final class Ergebnis {
        public String sql;
        public final List<Krater> krater = new ArrayList<>();
        public final List<Strom> stroeme = new ArrayList<>();
        public final Map<String, Integer> zeilen = new LinkedHashMap<>();
        /** Geometrien je Tabelle.Spalte (Zeilen mit gefüllter Spalte). */
        public final Map<String, Integer> geometrien = new LinkedHashMap<>();
        public int punkte, anweisungen, abweichendeZellen;
        public final List<String> hinweise = new ArrayList<>();
    }

    // ------------------------------------------------------------------ Rechnen

    /** Rechnet alles; g muss geladen sein, Ground und DetailTiles in Gebrauch. */
    public static Ergebnis rechne(HeightGrid g) {
        Ergebnis e = new Ergebnis();
        for (KraterDef d : KRATER) e.krater.add(miss(d));
        stroeme(g, e);
        e.sql = schreibe(e);
        return e;
    }

    /**
     * Krater: Rand in 72 Richtungen. Gesucht wird nur im Fenster der modellierten Form (0,5 × kleine bis 1,35 × große
     * Halbachse) und nur bis zum ersten Abstieg um mehr als 15 m; der Rand ist das Ende des höchsten zusammenhängenden
     * Anstiegs (Neigung über 0,15). So greift der Rand weder in einen Nachbarkrater noch in den Hang dahinter.
     */
    static Krater miss(KraterDef d) {
        Krater k = new Krater(d);
        double cx = Geo.x(d.lat(), d.lon()), cz = Geo.z(d.lat());
        int n = 72;
        double lo = 0.5 * Math.min(d.ra(), d.rb()), hi = 1.35 * Math.max(d.ra(), d.rb());
        double step = Math.max(3, hi / 250);
        double[] rad = new double[n], top = new double[n];
        double boden = Double.MAX_VALUE;
        for (int a = 0; a < n; a++) {
            double ang = Math.toRadians(a * 360.0 / n);
            double dx = Math.cos(ang), dn = Math.sin(ang);
            int m = (int) (hi * 1.15 / step) + 2;
            double[] h = new double[m];
            for (int i = 0; i < m; i++) {
                double r = i * step;
                h[i] = Ground.baseHeight(cx + dx * r, cz - dn * r);
            }
            int i0 = (int) Math.ceil(lo / step), i1 = Math.min(m - 2, (int) (hi / step));
            // Anstiege (Neigung über 0,15 am Stück) bis zum ersten Abstieg um mehr als 15 m: dahinter liegt schon ein
            // Nachbarkrater oder der Außenhang. Der Rand ist das Ende des höchsten Anstiegs.
            double maxH = -Double.MAX_VALUE, bestRise = -1;
            int r = i0, runStart = -1;
            for (int i = 0; i <= i1; i++) {
                maxH = Math.max(maxH, h[i]);
                if (i > i0 && h[i] < maxH - 15) break;
                if (i < i0) continue;
                double sl = (h[i + 1] - h[i]) / step;
                if (sl > 0.15) {
                    if (runStart < 0) runStart = i;
                    double rise = h[i + 1] - h[runStart];
                    if (rise > bestRise) { bestRise = rise; r = i + 1; }
                } else runStart = -1;
            }
            if (bestRise < 0) r = i0;
            rad[a] = Math.max(step * 2, (r + 0.5) * step);
            top[a] = h[Math.min(m - 1, r + 1)];
            for (int i = 0; i <= r; i++) boden = Math.min(boden, h[i]);   // der tiefste Punkt innerhalb des Rands
        }
        // Ausreißer glätten: Median aus fünf Nachbarn
        double[] glatt = new double[n];
        for (int a = 0; a < n; a++) {
            double[] w = new double[5];
            for (int o = -2; o <= 2; o++) w[o + 2] = rad[(a + o + n) % n];
            java.util.Arrays.sort(w);
            glatt[a] = w[2];
        }
        // Zacken in Nachbarkrater hinein: höchstens das 1,25fache des Medians aus 13 Richtungen (± 30°)
        double[] grenze = new double[n];
        for (int a = 0; a < n; a++) {
            double[] w = new double[13];
            for (int o = -6; o <= 6; o++) w[o + 6] = glatt[(a + o + n) % n];
            java.util.Arrays.sort(w);
            grenze[a] = 1.25 * w[6];
        }
        for (int a = 0; a < n; a++) glatt[a] = Math.min(glatt[a], grenze[a]);
        double[] ring = new double[2 * n];
        double sumTop = 0;
        for (int a = 0; a < n; a++) {
            double ang = Math.toRadians(a * 360.0 / n);
            ring[2 * a] = cx + Math.cos(ang) * glatt[a];
            ring[2 * a + 1] = -cz + Math.sin(ang) * glatt[a];
            sumTop += top[a];
        }
        k.rand = ring;
        k.randHoehe = sumTop / n;
        k.tiefe = Math.max(0, k.randHoehe - boden);
        double best = 0;
        int ba = 0, bb = 0;
        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++) {
                double dd = Math.hypot(ring[2 * a] - ring[2 * b], ring[2 * a + 1] - ring[2 * b + 1]);
                if (dd > best) { best = dd; ba = a; bb = b; }
            }
        k.laenge = best;
        double ux = (ring[2 * bb] - ring[2 * ba]) / best, uy = (ring[2 * bb + 1] - ring[2 * ba + 1]) / best;
        double qmin = Double.MAX_VALUE, qmax = -Double.MAX_VALUE;
        for (int a = 0; a < n; a++) {
            double p = -uy * ring[2 * a] + ux * ring[2 * a + 1];
            qmin = Math.min(qmin, p);
            qmax = Math.max(qmax, p);
        }
        k.breite = qmax - qmin;
        k.flaecheHa = Geometrie.flaeche(ring) / 1e4;
        return k;
    }

    static void stroeme(HeightGrid g, Ergebnis e) {
        Map<String, List<double[]>> axes = new LinkedHashMap<>();
        int nn = HeightGrid.N;
        int[] years = new int[nn * nn];
        int[] retrace = Geology.owners(g, axes, years);
        int[] owner = nachRaster(g, retrace, e);
        int flows = Geology.FLOWS.size(), total = flows + Geology.PATCHES.size();
        // Hüllrechteck je Besitzer
        int[] i0 = new int[total], i1 = new int[total], j0 = new int[total], j1 = new int[total];
        java.util.Arrays.fill(i0, Integer.MAX_VALUE);
        java.util.Arrays.fill(j0, Integer.MAX_VALUE);
        java.util.Arrays.fill(i1, -1);
        java.util.Arrays.fill(j1, -1);
        for (int j = 0; j < nn; j++)
            for (int i = 0; i < nn; i++) {
                int o = owner[j * nn + i];
                if (o < 0) continue;
                i0[o] = Math.min(i0[o], i); i1[o] = Math.max(i1[o], i);
                j0[o] = Math.min(j0[o], j); j1[o] = Math.max(j1[o], j);
            }
        for (int id = 0; id < total; id++) {
            Strom s = new Strom();
            if (id < flows) {
                Geology.Flow f = Geology.FLOWS.get(id);
                s.name = f.name();
                s.art = "STROM";
                s.jahrModell = f.year();
                s.laengeKm = f.lengthKm();
                s.breiteM = f.width();
                s.aaNah = f.aaNear();
                s.aaFern = f.aaFar();
                double[][] fis = f.fissure();
                s.schlot = new double[]{Geo.x(fis[0][0], fis[0][1]), -Geo.z(fis[0][0])};
                if (fis.length >= 2) {
                    s.spalte = new double[2 * fis.length];
                    for (int i = 0; i < fis.length; i++) { s.spalte[2 * i] = Geo.x(fis[i][0], fis[i][1]); s.spalte[2 * i + 1] = -Geo.z(fis[i][0]); }
                }
                List<double[]> ax = axes.get(f.name());
                if (ax != null && ax.size() >= 2) {
                    double[] l = new double[2 * ax.size()];
                    for (int i = 0; i < ax.size(); i++) { l[2 * i] = ax.get(i)[0]; l[2 * i + 1] = -ax.get(i)[1]; }
                    s.achse = Geometrie.vereinfache(l, TOL_ACHSE);
                }
            } else {
                Geology.Patch p = Geology.PATCHES.get(id - flows);
                s.name = p.name();
                s.art = "FLAECHE";
                s.jahrModell = p.year();
                s.aaNah = s.aaFern = p.aa();
            }
            s.vulkan = s.name.startsWith("Mauna Loa") || s.name.startsWith("Mokuʻāweoweo") ? "MAUNA_LOA"
                    : s.name.startsWith("Kaʻūpūlehu") || s.name.startsWith("Huʻehuʻe") ? "HUALALAI" : "KILAUEA";
            int[] jahre = jahre(s.name, s.jahrModell);
            s.jahrVon = jahre[0];
            s.jahrBis = jahre[1];
            s.text = Lexicon.eruptionText(s.name);
            if (i1[id] >= 0) {
                int w = i1[id] - i0[id] + 1, h = j1[id] - j0[id] + 1;
                boolean[] m = new boolean[w * h];
                int cells = 0;
                for (int j = 0; j < h; j++)
                    for (int i = 0; i < w; i++)
                        if (owner[(j + j0[id]) * nn + i + i0[id]] == id) { m[j * w + i] = true; cells++; }
                s.flaecheKm2 = cells * HeightGrid.CELL * HeightGrid.CELL / 1e6;
                double x0 = HeightGrid.X0 + i0[id] * HeightGrid.CELL, y0 = -(HeightGrid.Z0 + j0[id] * HeightGrid.CELL);
                List<Geometrie.Polygon> polys = Geometrie.umriss(m, w, h, x0, y0, HeightGrid.CELL, 3 * HeightGrid.CELL * HeightGrid.CELL);
                if (polys.size() > 120) {
                    e.hinweise.add(s.name + ": " + polys.size() + " Teile, die kleinsten " + (polys.size() - 120) + " weggelassen");
                    polys = new ArrayList<>(polys.subList(0, 120));
                }
                for (Geometrie.Polygon p : polys) s.umriss.add(Geometrie.vereinfache(p, TOL_UMRISS));
                // höchstens 300 Ringe (die Elementliste bleibt unter 999 Zahlen): die kleinsten Löcher fallen weg
                int ringe = 0;
                for (Geometrie.Polygon p : s.umriss) ringe += 1 + p.loecher.size();
                while (ringe > 300) {
                    Geometrie.Polygon wo = null;
                    int wi = -1;
                    double kleinst = Double.MAX_VALUE;
                    for (Geometrie.Polygon p : s.umriss)
                        for (int i = 0; i < p.loecher.size(); i++) {
                            double a = Math.abs(Geometrie.flaeche(p.loecher.get(i)));
                            if (a < kleinst) { kleinst = a; wo = p; wi = i; }
                        }
                    if (wo == null) break;
                    wo.loecher.remove(wi);
                    ringe--;
                }
            } else e.hinweise.add(s.name + ": keine Zelle im Raster (ganz überdeckt)");
            e.stroeme.add(s);
        }
    }

    /**
     * Welcher Strom jede Zelle trägt, so wie das Raster sie zeigt. Das Nachrechnen ({@link Geology#owners}) trifft fast
     * jede Zelle; wurde der Zwischenspeicher mit einem älteren Stand gebaut, laufen einzelne Spuren etwas anders. Darum
     * gilt der Alterscode im Raster: Eine Zelle gehört zu einem Strom, wenn ihr Alter zu seinem Jahr passt und sie höchstens
     * 600 m von seiner nachgerechneten Fläche liegt; passen mehrere (gleiches Jahr), entscheidet die Nachbarschaft.
     */
    static int[] nachRaster(HeightGrid g, int[] retrace, Ergebnis e) {
        int nn = HeightGrid.N, flows = Geology.FLOWS.size(), total = flows + Geology.PATCHES.size();
        int[] code = new int[total];
        for (int id = 0; id < total; id++) {
            int y = id < flows ? Geology.FLOWS.get(id).year() : Geology.PATCHES.get(id - flows).year();
            code[id] = Geology.ageCode(Math.max(0.3, Geology.NOW - y + 0.5));
        }
        // Nähe: nachgerechnete Fläche um 8 Zellen (600 m) erweitert, je Strom als Bitfeld über das Raster
        final int R = 8;
        java.util.BitSet[] nah = new java.util.BitSet[total];
        for (int id = 0; id < total; id++) nah[id] = new java.util.BitSet(nn * nn);
        for (int j = 0; j < nn; j++)
            for (int i = 0; i < nn; i++) {
                int o = retrace[j * nn + i];
                if (o < 0) continue;
                // nur Randzellen der Fläche müssen ausstrahlen
                boolean rand = i == 0 || j == 0 || i == nn - 1 || j == nn - 1 || retrace[j * nn + i - 1] != o || retrace[j * nn + i + 1] != o
                        || retrace[(j - 1) * nn + i] != o || retrace[(j + 1) * nn + i] != o;
                if (!rand) { nah[o].set(j * nn + i); continue; }
                for (int jj = Math.max(0, j - R); jj <= Math.min(nn - 1, j + R); jj++)
                    nah[o].set(jj * nn + Math.max(0, i - R), jj * nn + Math.min(nn - 1, i + R) + 1);
            }
        int[] out = new int[nn * nn];
        int gleich = 0, nachgezogen = 0, weg = 0;
        for (int k = 0; k < out.length; k++) {
            int c = g.geo[4 * k] & 255, o = retrace[k];
            if (o >= 0 && code[o] == c) { out[k] = o; gleich++; continue; }
            int best = -1, bestN = -1;
            for (int id = 0; id < total; id++) {
                if (code[id] != c || !nah[id].get(k)) continue;
                int i = k % nn, j = k / nn, cnt = 0;
                for (int jj = Math.max(0, j - 2); jj <= Math.min(nn - 1, j + 2); jj++)
                    for (int ii = Math.max(0, i - 2); ii <= Math.min(nn - 1, i + 2); ii++) if (retrace[jj * nn + ii] == id) cnt++;
                if (cnt > bestN) { bestN = cnt; best = id; }
            }
            out[k] = best;
            if (best >= 0) nachgezogen++;
            else if (o >= 0) weg++;
        }
        e.abweichendeZellen = nachgezogen + weg;
        e.hinweise.add(String.format(Locale.GERMAN, "Zellen: %,d wie nachgerechnet, %,d nach dem Raster zugeordnet, %,d ohne passendes Alter weggelassen",
                gleich, nachgezogen, weg));
        return out;
    }

    /** Jahre aus dem Namen: „Mauna Ulu 1969–74“ → 1969, 1974; „Puna 1955“ → 1955, 0. */
    static int[] jahre(String name, int modell) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(1[89]\\d\\d|20\\d\\d)(?:–(\\d{2,4}))?").matcher(name);
        int von = modell, bis = 0;
        if (m.find()) {
            von = Integer.parseInt(m.group(1));
            if (m.group(2) != null) {
                String b = m.group(2);
                bis = b.length() == 4 ? Integer.parseInt(b) : (von / 100) * 100 + Integer.parseInt(b);
                if (bis < von) bis += 100;
            }
        }
        return new int[]{von, bis};
    }

    // ------------------------------------------------------------------ Schreiben

    static String q(String s) { return s == null ? "NULL" : "'" + s.replace("'", "''") + "'"; }

    static String n(double v, int stellen) {
        if (Double.isNaN(v)) return "NULL";
        String s = String.format(Locale.ROOT, "%." + stellen + "f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    static String datum(LocalDate d) { return d == null ? "NULL" : "DATE '" + d + "'"; }

    static void pruefeLaenge(String was, String text, int max) {
        if (text != null && text.length() > max) throw new IllegalStateException(was + " ist " + text.length() + " Zeichen lang (höchstens " + max + ")");
    }

    /** Länge, Breite eines Punkts der Ebene (Ost, Nord). */
    static double[] lonlat(double x, double nord) {
        double z = -nord;
        return new double[]{Geo.lon(x, z), Geo.lat(z)};
    }

    static String punkt(double x, double nord) {
        double[] ll = lonlat(x, nord);
        return "KIL_API.punkt(" + n(ll[0], 7) + ", " + n(ll[1], 7) + ")";
    }

    private static final class Puffer {
        final StringBuilder b = new StringBuilder();
        int anweisungen;
        void a(String s) { b.append(s).append("\n/\n\n"); anweisungen++; }
        void kommentar(String s) { b.append("-- ").append(s).append('\n'); }
    }

    static String schreibe(Ergebnis e) {
        Puffer p = new Puffer();
        p.b.append("-- ============================================================================\n");
        p.b.append("-- Kīlauea, Phase 16b: Grunddaten aus dem Code (geschrieben von tools/Grunddaten, nicht von Hand ändern)\n");
        p.b.append("-- Jede Zeile wird nur eingefügt, wenn es sie noch nicht gibt; jede Geometrie nur gesetzt, wenn die Spalte leer ist.\n");
        p.b.append("-- Große Geometrien kommen in Stücken über KIL_WISSEN.geom_neu / geom_teil / geom_setze (prüft die Gültigkeit).\n");
        p.b.append("-- Gerechnet [A]: Höhen der Orte, Ränder und Maße der Krater, Achsen und Umrisse der Ströme (nicht die geologische Karte).\n");
        p.b.append("-- ============================================================================\n\n");
        p.b.append("-- ERWARTET KIL_ORT=").append(Place.values().length).append('\n');
        p.b.append("-- ERWARTET KIL_KRATER=").append(e.krater.size()).append('\n');
        p.b.append("-- ERWARTET KIL_STROM=").append(e.stroeme.size()).append('\n');
        int stationen = 4 + Station.SEISMIC.size();
        p.b.append("-- ERWARTET KIL_STATION=").append(stationen).append('\n');
        List<Script> scripts = Scripts.all();
        int kapitel = 0;
        for (Script s : scripts) kapitel += s.chapters().size();
        p.b.append("-- ERWARTET KIL_DREHBUCH=").append(scripts.size() + 1).append('\n');
        p.b.append("-- ERWARTET KIL_KAPITEL=").append(kapitel).append('\n');
        // Geometrien: so viele Zeilen mit gefüllter Spalte
        int achsen = 0, spalten = 0, umrisse = 0, schlote = 0;
        for (Strom s : e.stroeme) {
            if (s.achse != null) achsen++;
            if (s.spalte != null) spalten++;
            if (!s.umriss.isEmpty()) umrisse++;
            if (s.schlot != null) schlote++;
        }
        p.b.append("-- ERWARTET KIL_KRATER.RAND=").append(e.krater.size()).append('\n');
        p.b.append("-- ERWARTET KIL_STROM.SCHLOT=").append(schlote).append('\n');
        p.b.append("-- ERWARTET KIL_STROM.SPALTE=").append(spalten).append('\n');
        p.b.append("-- ERWARTET KIL_STROM.ACHSE=").append(achsen).append('\n');
        p.b.append("-- ERWARTET KIL_STROM.UMRISS=").append(umrisse).append("\n\n");
        e.geometrien.put("KIL_KRATER.RAND", e.krater.size());
        e.geometrien.put("KIL_STROM.SCHLOT", schlote);
        e.geometrien.put("KIL_STROM.SPALTE", spalten);
        e.geometrien.put("KIL_STROM.ACHSE", achsen);
        e.geometrien.put("KIL_STROM.UMRISS", umrisse);
        e.zeilen.put("KIL_ORT", Place.values().length);
        e.zeilen.put("KIL_KRATER", e.krater.size());
        e.zeilen.put("KIL_STROM", e.stroeme.size());
        e.zeilen.put("KIL_STATION", stationen);
        e.zeilen.put("KIL_DREHBUCH", scripts.size() + 1);
        e.zeilen.put("KIL_KAPITEL", kapitel);

        // ---- Orte
        p.b.append("-- ---------------------------------------------------------------- Orte\n\n");
        for (Place pl : Place.values()) {
            String tafel = Lexicon.place(pl).text();
            pruefeLaenge("Tafel " + pl, tafel, 1500);
            pruefeLaenge("Kurztext " + pl, pl.note, 400);
            double h = Math.max(0, Ground.rawHeight(pl.x(), pl.z()));
            p.a("INSERT INTO KIL_ORT (SCHLUESSEL, NAME, ART, BREITE, LAENGE, GEOM, HOEHE_M, BLICK_GIER, BLICK_NEIGUNG, BLICK_ABSTAND, KURZTEXT, TAFEL, MARKE_LAGE, MARKE_HOEHE)\n"
                    + "SELECT " + q(pl.name()) + ", " + q(pl.label) + ", " + q(art(pl)) + ", " + n(pl.lat, 6) + ", " + n(pl.lon, 6) + ", KIL_API.punkt(" + n(pl.lon, 7) + ", " + n(pl.lat, 7) + "), "
                    + n(h, 1) + ", " + n(pl.viewYaw, 1) + ", " + n(pl.viewPitch, 1) + ", " + n(pl.viewDist, 0) + ",\n       " + q(pl.note) + ",\n       " + q(tafel) + ", 'L', 'M' FROM DUAL\n"
                    + " WHERE NOT EXISTS (SELECT 1 FROM KIL_ORT WHERE SCHLUESSEL = " + q(pl.name()) + ")");
        }

        // ---- Krater
        p.b.append("-- ---------------------------------------------------------------- Krater\n\n");
        for (Krater k : e.krater) {
            KraterDef d = k.def;
            double cx = Geo.x(d.lat(), d.lon()), cn = -Geo.z(d.lat());
            p.a("INSERT INTO KIL_KRATER (NAME, ART, ORT_ID, MITTE, TIEFE_M, LAENGE_M, BREITE_M, FLAECHE_HA, ENTSTANDEN, KURZTEXT, MARKE_LAGE, MARKE_FORM)\n"
                    + "SELECT " + q(d.name()) + ", " + q(d.art()) + ", " + (d.ort() == null ? "NULL" : "(SELECT ORT_ID FROM KIL_ORT WHERE SCHLUESSEL = " + q(d.ort().name()) + ")")
                    + ", " + punkt(cx, cn) + ", " + n(k.tiefe, 1) + ", " + n(k.laenge, 0) + ", " + n(k.breite, 0) + ", " + n(k.flaecheHa, 1) + ", "
                    + (d.entstanden() > 0 ? Integer.toString(d.entstanden()) : "NULL") + ",\n       " + q(d.text()) + ", " + q(d.marke()) + ", 'A' FROM DUAL\n"
                    + " WHERE NOT EXISTS (SELECT 1 FROM KIL_KRATER WHERE NAME = " + q(d.name()) + ")");
            geometrie(p, e, "KIL_KRATER", "RAND", "NAME", d.name(), 2003, List.of(k.rand), new int[]{1003});
        }

        // ---- Ströme
        p.b.append("-- ---------------------------------------------------------------- Ströme und Flächen\n\n");
        for (Strom s : e.stroeme) {
            pruefeLaenge("Text " + s.name, s.text, 400);
            p.a("INSERT INTO KIL_STROM (NAME, VULKAN, ART, JAHR_VON, JAHR_BIS, JAHR_MODELL, SCHLOT, LAENGE_KM, BREITE_M, AA_NAH, AA_FERN, FLAECHE_KM2, KURZTEXT, MARKE_JAHR, MARKE_WEG)\n"
                    + "SELECT " + q(s.name) + ", " + q(s.vulkan) + ", " + q(s.art) + ", " + s.jahrVon + ", " + (s.jahrBis > 0 ? Integer.toString(s.jahrBis) : "NULL") + ", " + s.jahrModell + ", "
                    + (s.schlot == null ? "NULL" : punkt(s.schlot[0], s.schlot[1])) + ", " + (s.laengeKm > 0 ? n(s.laengeKm, 1) : "NULL") + ", "
                    + (s.breiteM > 0 ? n(s.breiteM, 0) : "NULL") + ", " + n(s.aaNah, 2) + ", " + n(s.aaFern, 2) + ", " + n(s.flaecheKm2, 2) + ",\n       " + q(s.text)
                    + ", 'L', " + q(s.art.equals("STROM") ? "A" : "G") + " FROM DUAL\n"
                    + " WHERE NOT EXISTS (SELECT 1 FROM KIL_STROM WHERE NAME = " + q(s.name) + ")");
            if (s.spalte != null) geometrie(p, e, "KIL_STROM", "SPALTE", "NAME", s.name, 2002, List.of(s.spalte), new int[]{2});
            if (s.achse != null) geometrie(p, e, "KIL_STROM", "ACHSE", "NAME", s.name, 2002, List.of(s.achse), new int[]{2});
            if (!s.umriss.isEmpty()) {
                List<double[]> ringe = new ArrayList<>();
                List<Integer> arten = new ArrayList<>();
                for (Geometrie.Polygon pg : s.umriss) {
                    ringe.add(pg.aussen);
                    arten.add(1003);
                    for (double[] hl : pg.loecher) { ringe.add(hl); arten.add(2003); }
                }
                int[] et = new int[arten.size()];
                for (int i = 0; i < et.length; i++) et[i] = arten.get(i);
                geometrie(p, e, "KIL_STROM", "UMRISS", "NAME", s.name, s.umriss.size() > 1 ? 2007 : 2003, ringe, et);
            }
        }

        // ---- Stationen
        p.b.append("-- ---------------------------------------------------------------- Stationen des Messnetzes\n\n");
        List<Station> st = new ArrayList<>(List.of(Station.UWD, Station.SDH, Station.UWEV, Station.CRIM));
        st.addAll(Station.SEISMIC);
        for (Station s : st) {
            String art = s.kind() == Station.Kind.TILT ? "NEIGUNG" : s.kind() == Station.Kind.GPS ? "GPS" : "SEISMOMETER";
            p.a("INSERT INTO KIL_STATION (KUERZEL, NAME, ART, GEOM, MARKE_LAGE)\n"
                    + "SELECT " + q(s.code()) + ", " + q(s.name()) + ", " + q(art) + ", KIL_API.punkt(" + n(s.lon(), 7) + ", " + n(s.lat(), 7) + "), " + q(s.where().name()) + " FROM DUAL\n"
                    + " WHERE NOT EXISTS (SELECT 1 FROM KIL_STATION WHERE KUERZEL = " + q(s.code()) + " AND ART = " + q(art) + ")");
        }

        // ---- Drehbücher und Kapitel
        p.b.append("-- ---------------------------------------------------------------- Drehbücher und Kapitel\n\n");
        for (Script s : scripts) {
            LocalDate von = null, bis = null;
            for (Script.Chapter c : s.chapters()) {
                if (c.from() != null && (von == null || c.from().isBefore(von))) von = c.from();
                LocalDate e2 = c.to() != null ? c.to() : c.from();
                if (e2 != null && (bis == null || e2.isAfter(bis))) bis = e2;
            }
            String zeitraum = von == null ? null : von.getYear() + (bis != null && bis.getYear() != von.getYear() ? "–" + bis.getYear() : "");
            pruefeLaenge("Kurztext " + s.id, s.summary, 600);
            Place ort = schauplatz(s.id);
            p.a("INSERT INTO KIL_DREHBUCH (SCHLUESSEL, TITEL, ZEITRAUM, DATUM_VON, DATUM_BIS, ORT_ID, KURZTEXT, KAPITEL)\n"
                    + "SELECT " + q(s.id) + ", " + q(s.title) + ", " + q(zeitraum) + ", " + datum(von) + ", " + datum(bis) + ", "
                    + (ort == null ? "NULL" : "(SELECT ORT_ID FROM KIL_ORT WHERE SCHLUESSEL = " + q(ort.name()) + ")") + ",\n       " + q(s.summary) + ", " + s.chapters().size() + " FROM DUAL\n"
                    + " WHERE NOT EXISTS (SELECT 1 FROM KIL_DREHBUCH WHERE SCHLUESSEL = " + q(s.id) + ")");
            int nr = 0;
            for (Script.Chapter c : s.chapters()) {
                nr++;
                pruefeLaenge("Kapitel " + s.id + "/" + nr, c.text, 2000);
                p.a("INSERT INTO KIL_KAPITEL (DREHBUCH_ID, NR, DATUM_TEXT, DATUM_VON, DATUM_BIS, TITEL, TEXT, SIM_STUNDEN, TEMPO, TEMPO_FAKTOR)\n"
                        + "SELECT D.DREHBUCH_ID, " + nr + ", " + q(c.dateText()) + ", " + datum(c.from()) + ", " + datum(c.to()) + ", " + q(c.title) + ",\n       " + q(c.text) + ",\n       "
                        + n(c.duration / 3600, 2) + ", " + q(c.lapse()) + ", " + n(c.speed, 0) + " FROM KIL_DREHBUCH D\n"
                        + " WHERE D.SCHLUESSEL = " + q(s.id) + " AND NOT EXISTS (SELECT 1 FROM KIL_KAPITEL K WHERE K.DREHBUCH_ID = D.DREHBUCH_ID AND K.NR = " + nr + ")");
            }
        }
        p.a("INSERT INTO KIL_DREHBUCH (SCHLUESSEL, TITEL, ZEITRAUM, DATUM_VON, DATUM_BIS, ORT_ID, KURZTEXT, KAPITEL)\n"
                + "SELECT 'frei', 'Freier Ausbruch', NULL, NULL, NULL, NULL,\n       "
                + q("Ein Gang läuft vom Gipfel in eine Riftzone, dann öffnet sich eine Spalte; Ort, Rate und Länge wählst du im Bedienfeld. Drei Kapitel, die sich nach der Wahl richten.")
                + ", 3 FROM DUAL\n WHERE NOT EXISTS (SELECT 1 FROM KIL_DREHBUCH WHERE SCHLUESSEL = 'frei')");
        e.anweisungen = p.anweisungen;
        return p.b.toString();
    }

    /** Eine große Geometrie in Stücken: geom_neu, geom_teil …, geom_setze (setzt nur, wenn die Spalte leer ist). */
    static void geometrie(Puffer p, Ergebnis e, String tabelle, String spalte, String schluesselSpalte, String schluessel, int gtype,
                          List<double[]> teile, int[] etype) {
        List<Double> ord = new ArrayList<>();
        StringBuilder elem = new StringBuilder();
        int punkte = 0;
        for (int t = 0; t < teile.size(); t++) {
            double[] r = teile.get(t);
            boolean ring = etype[t] != 2;
            int off = ord.size() + 1;
            elem.append(elem.length() == 0 ? "" : ", ").append(off).append(", ").append(etype[t]).append(", 1");
            int m = r.length / 2;
            double lastLon = Double.NaN, lastLat = Double.NaN;
            int start = ord.size();
            for (int i = 0; i < m; i++) {
                double[] ll = lonlat(r[2 * i], r[2 * i + 1]);
                double lo = Math.round(ll[0] * 1e7) / 1e7, la = Math.round(ll[1] * 1e7) / 1e7;
                if (lo == lastLon && la == lastLat) continue;
                ord.add(lo);
                ord.add(la);
                lastLon = lo;
                lastLat = la;
                punkte++;
            }
            if (ring) { ord.add(ord.get(start)); ord.add(ord.get(start + 1)); punkte++; }
        }
        e.punkte += punkte;
        p.kommentar(tabelle + "." + spalte + " · " + schluessel + " · " + teile.size() + (teile.size() == 1 ? " Teil, " : " Teile, ") + punkte + " Punkte");
        p.a("BEGIN KIL_WISSEN.geom_neu; END;");
        for (int a = 0; a < ord.size(); a += STUECK) {
            StringBuilder b = new StringBuilder("BEGIN KIL_WISSEN.geom_teil(MDSYS.SDO_ORDINATE_ARRAY(");
            for (int i = a; i < Math.min(ord.size(), a + STUECK); i++) {
                if (i > a) b.append(i % 8 == 0 ? ",\n  " : ",");
                b.append(n(ord.get(i), 7));
            }
            p.a(b.append(")); END;").toString());
        }
        p.a("BEGIN KIL_WISSEN.geom_setze(" + q(tabelle) + ", " + q(spalte) + ", " + q(schluesselSpalte) + ", " + q(schluessel) + ", " + gtype
                + ", MDSYS.SDO_ELEM_INFO_ARRAY(" + elem + ")); END;");
    }
}
