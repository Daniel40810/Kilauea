package com.dan.kilauea.db;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Ein Zustand der App als Name → Wert (Phase 16, Frage 21 = A). Die Kamera, die Zeit, das Wetter und das Bild haben feste
 * Spalten in KIL_ZUSTAND ({@link #SPALTEN}); alle übrigen Schalter und Regler gehen als Name und Wert nach KIL_SCHALTER. Ein
 * neuer Schalter braucht darum nur einen neuen Namen. Ohne Datenbank wird derselbe Satz als Datei gespeichert.
 * <p>
 * Namen: Kleinbuchstaben, Ziffern und Unterstrich, mindestens ein Punkt (etwa {@code leben.nene}); so verlangt es auch die
 * Prüfung KIL_SCHALTER_NAME_CK.
 */
public final class Zustand {
    public enum Typ { ZAHL, TEXT }

    /** Eine feste Spalte: Name im Zustand, Spalte in KIL_ZUSTAND, Grenzen und Nachkommastellen (wie die Spalte). */
    public static final class Spalte {
        public final String name, spalte;
        public final Typ typ;
        public final double min, max;
        public final int stellen;
        public final boolean pflicht;
        Spalte(String name, String spalte, Typ typ, double min, double max, int stellen, boolean pflicht) {
            this.name = name; this.spalte = spalte; this.typ = typ; this.min = min; this.max = max; this.stellen = stellen; this.pflicht = pflicht;
        }
    }

    private static Spalte k(String n, String s, double min, double max, int st) { return new Spalte(n, s, Typ.ZAHL, min, max, st, true); }
    private static Spalte z(String n, String s, double min, double max, int st) { return new Spalte(n, s, Typ.ZAHL, min, max, st, false); }

    public static final String MODUS = "kamera.modus", QUALITAET = "bild.qualitaet";
    public static final String[] MODI = {"FLUG", "KREISEN", "GEHEN"}, QUALITAETEN = {"NIEDRIG", "MITTEL", "HOCH", "ULTRA"};

    /** Die festen Spalten in der Reihenfolge von db/01_tabellen.sql. */
    public static final Spalte[] SPALTEN = {
            new Spalte(MODUS, "MODUS", Typ.TEXT, 0, 0, 0, true),
            k("kamera.x", "DREHPUNKT_X", -400_000, 400_000, 2), k("kamera.y", "DREHPUNKT_Y", -7_000, 200_000, 2),
            k("kamera.z", "DREHPUNKT_Z", -400_000, 400_000, 2), k("kamera.gier", "GIER", 0, 359.99, 2),
            k("kamera.neigung", "NEIGUNG", -90, 90, 2), k("kamera.abstand", "ABSTAND", 0, 400_000, 2),
            z("zeit.tag", "TAG", 1, 365, 0), z("zeit.stunde", "STUNDE", 0, 23.999, 3), z("zeit.raffer", "ZEITRAFFER", 0, 4, 0),
            z("luft.dunst", "DUNST", 0, 100, 0), z("luft.wind", "WIND_MS", 0, 16, 1), z("luft.richtung", "WIND_RICHTUNG", 0, 359, 0),
            z("meer.swell", "SWELL_M", 0, 4, 1), z("meer.periode", "SWELL_S", 8, 20, 1),
            new Spalte(QUALITAET, "QUALITAET", Typ.TEXT, 0, 0, 0, false), z("bild.belichtung", "BELICHTUNG", -2, 2, 1),
    };

    private static final Map<String, Spalte> FEST = new LinkedHashMap<>();
    static { for (Spalte s : SPALTEN) FEST.put(s.name, s); }
    private static final Pattern NAME = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)+$");

    /** Kopf: Art (AUTO oder ANSICHT), Name, Zeitpunkt, Sitzung. */
    public String art = "AUTO", name = "AUTO";
    public long gespeichertMs, sitzungId;

    private final Map<String, String> werte = new TreeMap<>();

    public static boolean fest(String n) { return FEST.containsKey(n); }
    public static Spalte spalte(String n) { return FEST.get(n); }
    public static boolean gueltigerName(String n) { return n != null && n.length() <= 60 && NAME.matcher(n).matches(); }

    /** Alle Werte (nur lesen), nach Namen sortiert. */
    public Map<String, String> werte() { return Collections.unmodifiableMap(werte); }

    /** Nur die Schalter (alles ohne feste Spalte). */
    public Map<String, String> schalter() {
        Map<String, String> m = new TreeMap<>();
        for (Map.Entry<String, String> e : werte.entrySet()) if (!FEST.containsKey(e.getKey())) m.put(e.getKey(), e.getValue());
        return m;
    }

    public boolean hat(String n) { return werte.containsKey(n); }
    public String text(String n) { return werte.get(n); }

    public double zahl(String n, double vorgabe) {
        String v = werte.get(n);
        if (v == null) return vorgabe;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return vorgabe; }
    }

    public boolean flag(String n, boolean vorgabe) {
        String v = werte.get(n);
        return v == null ? vorgabe : "J".equals(v);
    }

    /** Ob die Kamera vollständig gesetzt ist (die Pflichtspalten von KIL_ZUSTAND). */
    public boolean hatKamera() {
        for (Spalte s : SPALTEN) if (s.pflicht && !werte.containsKey(s.name)) return false;
        return true;
    }

    /** Ob Tag und Uhrzeit dabei sind. */
    public boolean hatZeit() { return werte.containsKey("zeit.tag") && werte.containsKey("zeit.stunde"); }

    /** Setzt einen Wert als Text; null entfernt ihn. Feste Zahlenspalten werden begrenzt und gerundet wie in der Tabelle. */
    public Zustand setze(String n, String v) {
        if (v == null) { werte.remove(n); return this; }
        Spalte s = FEST.get(n);
        if (s == null) {
            if (!gueltigerName(n)) throw new IllegalArgumentException("ungültiger Name " + n);
            werte.put(n, v.length() > 80 ? v.substring(0, 80) : v);
        } else if (s.typ == Typ.ZAHL) {
            double d;
            try { d = Double.parseDouble(v.trim().replace(',', '.')); } catch (NumberFormatException e) { return this; }
            setze(n, d);
        } else {
            String u = v.trim().toUpperCase(Locale.ROOT);
            String[] erlaubt = n.equals(MODUS) ? MODI : QUALITAETEN;
            for (String a : erlaubt) if (a.equals(u)) { werte.put(n, u); return this; }
        }
        return this;
    }

    public Zustand setze(String n, double d) {
        Spalte s = FEST.get(n);
        if (s == null) return setze(n, fmt(d, 4));
        if (s.typ != Typ.ZAHL || !Double.isFinite(d)) return this;
        if (n.equals("kamera.gier")) d = ((d % 360) + 360) % 360;
        double f = Math.pow(10, s.stellen);
        d = Math.round(d * f) / f;
        if (n.equals("kamera.gier") && d >= 360) d = 0;
        d = Math.max(s.min, Math.min(s.max, d));
        werte.put(n, fmt(d, s.stellen));
        return this;
    }

    public Zustand setze(String n, boolean b) { return setze(n, b ? "J" : "N"); }

    /** Zahl als Text für einen Schalter (bis 4 Nachkommastellen, ohne Nullen am Ende). */
    public static String fmtWert(double d) { return fmt(d, 4); }

    static String fmt(double d, int stellen) {
        String s = String.format(Locale.ROOT, "%." + stellen + "f", d);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    public Zustand kopie() {
        Zustand k = new Zustand();
        k.art = art; k.name = name; k.gespeichertMs = gespeichertMs; k.sitzungId = sitzungId;
        k.werte.putAll(werte);
        return k;
    }

    /** Nur die Kamera (und auf Wunsch Tag und Uhrzeit): für eine Ansicht. */
    public Zustand nurKamera(boolean mitZeit) {
        Zustand k = new Zustand();
        for (Spalte s : SPALTEN) if (s.name.startsWith("kamera.") && werte.containsKey(s.name)) k.werte.put(s.name, werte.get(s.name));
        if (mitZeit) for (String n : new String[]{"zeit.tag", "zeit.stunde"}) if (werte.containsKey(n)) k.werte.put(n, werte.get(n));
        return k;
    }

    // ------------------------------------------------------------ Datei (Rückfall ohne Datenbank)

    public Properties alsProperties() {
        Properties p = new Properties();
        p.setProperty("_art", art);
        p.setProperty("_name", name);
        p.setProperty("_gespeichert", Long.toString(gespeichertMs));
        for (Map.Entry<String, String> e : werte.entrySet()) p.setProperty(e.getKey(), e.getValue());
        return p;
    }

    /** Liest einen Zustand; unlesbare Werte und unbekannte Namen werden übergangen. */
    public static Zustand ausProperties(Properties p) {
        Zustand z = new Zustand();
        z.art = "ANSICHT".equals(p.getProperty("_art")) ? "ANSICHT" : "AUTO";
        z.name = p.getProperty("_name", z.art);
        try { z.gespeichertMs = Long.parseLong(p.getProperty("_gespeichert", "0")); } catch (NumberFormatException e) { z.gespeichertMs = 0; }
        for (String k : p.stringPropertyNames()) {
            if (k.startsWith("_")) continue;
            try { z.setze(k, p.getProperty(k)); } catch (IllegalArgumentException e) { /* übergehen */ }
        }
        return z;
    }

    public static void speichern(File datei, Zustand z) throws IOException {
        File dir = datei.getAbsoluteFile().getParentFile();
        if (dir != null) Files.createDirectories(dir.toPath());
        File tmp = new File(datei.getPath() + ".tmp");
        try (OutputStream o = Files.newOutputStream(tmp.toPath()); OutputStreamWriter w = new OutputStreamWriter(o, StandardCharsets.UTF_8)) {
            z.alsProperties().store(w, "Kilauea: Zustand (" + z.art + ")");
        }
        Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Der Zustand aus der Datei, oder null, wenn es keine gibt oder sie unlesbar ist. */
    public static Zustand laden(File datei) {
        if (datei == null || !datei.isFile()) return null;
        Properties p = new Properties();
        try (InputStream i = Files.newInputStream(datei.toPath()); InputStreamReader r = new InputStreamReader(i, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return null;
        }
        return ausProperties(p);
    }

    @Override public String toString() { return "Zustand[" + art + " " + name + " " + werte + "]"; }
}
