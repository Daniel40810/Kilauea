package com.dan.kilauea.db;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Die Datenbank im Programm (Phase 16a): Lage der Verbindung, Sitzung, Protokoll, Zustand und Ansichten. Alles Schreiben läuft
 * auf einem eigenen Faden; Bildlauf und Oberfläche warten nie auf Oracle, außer bei Ansichten, die man selbst anstößt (dann
 * höchstens 3 s). Ohne Datenbank (nicht erreichbar, nicht eingerichtet, abgeschaltet mit -Dkilauea.nodb=true) gehen Zustand
 * und Ansichten in Dateien unter ~/.kilauea (oder -Dkilauea.state.dir). Das Protokoll geht immer zusätzlich in
 * logs/kilauea_JJJJ-MM-TT.log (14 Tage, Frage 26 = A).
 */
public final class Dienst {
    private Dienst() { }

    public enum Lage {
        PRUEFT("Verbindung wird geprüft …"), AUS("ohne Datenbank (abgeschaltet)"), KEINE_KONFIG("ohne Datenbank (db/db.properties fehlt)"),
        KEIN_PASSWORT("Passwort fehlt"), OFFLINE("ohne Datenbank (nicht erreichbar)"), NICHT_EINGERICHTET("verbunden, nicht eingerichtet"),
        BEREIT("verbunden, eingerichtet");
        public final String text;
        Lage(String t) { text = t; }
    }

    public static final String APP_VERSION = "16.0";
    /** Die Tabellen einer eingerichteten Datenbank (db/01 legt genau diese an); dazu muss der Körper von KIL_API gültig sein. */
    public static final String[] TABELLEN = {"KIL_SITZUNG", "KIL_ZUSTAND", "KIL_SCHALTER", "KIL_PROTOKOLL"};
    /** Phase 16b (db/04 bis 06): Wissen und Geschichte; dazu der Körper von KIL_WISSEN. Fehlen sie, läuft der Betrieb weiter, nur Läufe werden nicht geschrieben. */
    public static final String[] TABELLEN_WISSEN = {"KIL_ORT", "KIL_KRATER", "KIL_STROM", "KIL_STATION", "KIL_DREHBUCH", "KIL_KAPITEL", "KIL_LAUF", "KIL_BEBEN", "KIL_MESSUNG"};
    /** Arten im Protokoll, wie KIL_PROTOKOLL_ART_CK sie erlaubt. */
    public static final String[] ARTEN = {"START", "ENDE", "FEHLER", "WARNUNG", "INFO", "ANSICHT", "DREHBUCH", "RUNDFLUG", "AUSBRUCH", "FOTO", "EINRICHTER", "DATENBANK"};

    /** Was der Rest des Programms über die Szene weiß: Tag, Stunde und der Ort unter dem Blick (Länge, Breite). */
    public interface Kontext {
        int tag();
        double stunde();
        double[] ort();
    }

    private static volatile Lage lage = Lage.PRUEFT;
    private static volatile String meldung = "", fassung = "";
    private static volatile int tabellen;
    private static volatile boolean wissen;
    /** Läuft im DB-Faden beim Beenden, bevor die Sitzung geschlossen wird (Läufe sichern). */
    private static final List<Runnable> vorEnde = new CopyOnWriteArrayList<>();
    private static volatile Db.Konfig konfig;
    private static Connection conn;
    private static volatile long sitzungId;
    private static volatile long startNs = System.nanoTime();
    private static volatile Kontext kontext;
    private static final AtomicBoolean beendet = new AtomicBoolean();
    private static final List<Runnable> lauscher = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService EX = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Kilauea-DB");
        t.setDaemon(true);
        return t;
    });
    /** Wartende Einträge fürs Protokoll in der Datenbank (alle 5 s gebündelt geschrieben). */
    private static final List<Eintrag> warteschlange = new ArrayList<>();
    /** Die letzten 20 Einträge für den Block „Daten“. */
    private static final Deque<String> letzte = new ArrayDeque<>();
    private static volatile boolean gestartet;
    // Mittel über die Sitzung
    private static double sumFps, sumGpu, sumCpu;
    private static long nMess;

    /** Ein Eintrag im Protokoll. */
    public static final class Eintrag {
        public final LocalDateTime zeit;
        public final double sekunde;
        public final String art, ziel, text;
        public final int tag;
        public final double stunde;
        public final double[] ort;
        Eintrag(LocalDateTime zeit, double sekunde, String art, String ziel, String text, int tag, double stunde, double[] ort) {
            this.zeit = zeit; this.sekunde = sekunde; this.art = art; this.ziel = ziel; this.text = text; this.tag = tag; this.stunde = stunde; this.ort = ort;
        }
        public String zeile() {
            return zeit.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\t" + art + "\t" + leer(ziel) + "\t" + leer(text).replace('\n', ' ')
                    + (tag > 0 ? String.format(java.util.Locale.ROOT, "\tSzene Tag %d %.2f h", tag, stunde) : "")
                    + (ort != null ? String.format(java.util.Locale.ROOT, "\t%.5f %.5f", ort[1], ort[0]) : "");
        }
        String kurz() { return zeit.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + art + " " + leer(ziel) + (text == null || text.isEmpty() ? "" : " – " + text); }
    }

    private static String leer(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------ Lage

    public static Lage lage() { return lage; }
    public static String meldung() { return meldung; }
    public static Db.Konfig konfig() { return konfig; }
    public static boolean bereit() { return lage == Lage.BEREIT; }
    public static long sitzung() { return sitzungId; }
    /** Fassung des Pakets und Zahl der KIL_-Tabellen (für das Startbild), sobald verbunden. */
    public static String fassung() { return fassung; }
    public static int tabellen() { return tabellen; }
    /** Sind die Tabellen für Wissen und Geschichte (Phase 16b) und KIL_WISSEN da? */
    public static boolean wissen() { return wissen; }
    /** Bereit und mit Phase 16b eingerichtet: dann schreibt die App Läufe, Beben und Messwerte. */
    public static boolean bereitMitWissen() { return lage == Lage.BEREIT && wissen; }

    /** Eine Zeile für Startbild und Block „Daten“. */
    public static String kurz() {
        Db.Konfig k = konfig;
        String z = lage.text;
        if (k != null && (lage == Lage.BEREIT || lage == Lage.NICHT_EINGERICHTET)) z = k.user + ": " + z;
        if (lage == Lage.BEREIT && !wissen) z += "; Wissen und Geschichte (16b) fehlen, „Einrichter …“ ergänzt sie";
        return z;
    }

    /** Ganz kurz für die Statuszeile. */
    public static String statuszeile() {
        switch (lage) {
            case BEREIT: return "DB " + (konfig == null ? "" : konfig.user) + (wissen ? "" : " (ohne 16b)");
            case PRUEFT: return "DB wird geprüft";
            case NICHT_EINGERICHTET: return "DB nicht eingerichtet";
            default: return "ohne Datenbank";
        }
    }

    /** Wird auf dem Ereignis-Faden aufgerufen, wenn sich die Lage ändert. */
    public static void beiAenderung(Runnable r) { lauscher.add(r); }

    private static void lage(Lage l, String m) {
        Lage vorher = lage;
        lage = l;
        meldung = m == null ? "" : m;
        for (Runnable r : lauscher) javax.swing.SwingUtilities.invokeLater(r);
        if (gestartet && vorher != l && l != Lage.PRUEFT) ereignis("DATENBANK", l.text, meldung, false);
    }

    public static File lokalerOrdner() {
        String p = System.getProperty("kilauea.state.dir");
        return p != null && !p.isEmpty() ? new File(p) : new File(System.getProperty("user.home"), ".kilauea");
    }

    public static File zustandsDatei() { return new File(lokalerOrdner(), "zustand.properties"); }
    static File ansichtsOrdner() { return new File(lokalerOrdner(), "ansichten"); }

    /** Der Ordner für das Tagesprotokoll: logs neben db, sonst ~/.kilauea/logs. */
    public static File logOrdner() {
        String p = System.getProperty("kilauea.log.dir");
        if (p != null && !p.isEmpty()) return new File(p);
        File db = Db.ordner().getAbsoluteFile();
        File base = db.getParentFile();
        if (base != null && base.canWrite()) return new File(base, "logs");
        return new File(lokalerOrdner(), "logs");
    }

    // ------------------------------------------------------------ Start

    /** Liest die Zugangsdaten und prüft die Datenbank im Hintergrund; räumt alte Tagesprotokolle auf; startet das Schreiben alle 5 s. */
    public static synchronized void start(Kontext k) {
        kontext = k;
        if (gestartet) return;
        gestartet = true;
        startNs = System.nanoTime();
        aufraeumenLogs();
        EX.scheduleWithFixedDelay(Dienst::schreibeWarteschlange, 5, 5, TimeUnit.SECONDS);
        if (Boolean.getBoolean("kilauea.nodb")) { lage(Lage.AUS, ""); return; }
        try {
            konfig = Db.konfig();
        } catch (IOException e) {
            lage(Lage.KEINE_KONFIG, e.getMessage());
            return;
        }
        if (!konfig.hatPasswort()) {
            lage(Lage.KEIN_PASSWORT, "Passwort für " + konfig.user + " eingeben; nach dem ersten Erfolg steht es in db/db.properties");
            return;
        }
        EX.submit(Dienst::pruefen);
    }

    public static void kontext(Kontext k) { kontext = k; }

    /** Das Startbild gibt das Passwort ein; stimmt es, wird es in db/db.properties gespeichert (Frage 29 = A). */
    public static void verbinden(char[] passwort) {
        Db.Konfig k = konfig;
        if (k == null) return;
        konfig = k.mitPasswort(passwort);
        lage(Lage.PRUEFT, "");
        final Db.Konfig neu = konfig;
        EX.submit(() -> {
            pruefen();
            Lage l = lage;
            if (l == Lage.BEREIT && sitzungId == 0 && sitzungDaten != null) sitzungAnlegen();
            if (l == Lage.BEREIT || l == Lage.NICHT_EINGERICHTET) {
                try {
                    Db.passwortSpeichern(neu, passwort);
                    ereignis("DATENBANK", "Passwort", "in db/db.properties gespeichert", false);
                } catch (IOException | RuntimeException e) {
                    ereignis("WARNUNG", "Passwort", "nicht gespeichert: " + e.getMessage(), false);
                }
            }
        });
    }

    /** Nach dem Einrichter oder auf Knopfdruck: erneut prüfen. */
    public static void neuPruefen() {
        if (konfig == null || !konfig.hatPasswort()) return;
        lage(Lage.PRUEFT, "");
        EX.submit(() -> {
            pruefen();
            if (lage == Lage.BEREIT && sitzungId == 0 && sitzungDaten != null) sitzungAnlegen();
        });
    }

    private static void pruefen() {
        try {
            Connection c = verb();
            StringBuilder in = new StringBuilder();
            for (String t : TABELLEN) in.append(in.length() == 0 ? "" : ",").append('\'').append(t).append('\'');
            StringBuilder in2 = new StringBuilder();
            for (String t : TABELLEN_WISSEN) in2.append(in2.length() == 0 ? "" : ",").append('\'').append(t).append('\'');
            int n, alle, n2;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(CASE WHEN STATUS = 'VALID' AND ((OBJECT_TYPE = 'TABLE' AND OBJECT_NAME IN (" + in
                    + ")) OR (OBJECT_TYPE = 'PACKAGE BODY' AND OBJECT_NAME = 'KIL_API')) THEN 1 END), "
                    + "COUNT(CASE WHEN OBJECT_TYPE = 'TABLE' THEN 1 END), "
                    + "COUNT(CASE WHEN STATUS = 'VALID' AND ((OBJECT_TYPE = 'TABLE' AND OBJECT_NAME IN (" + in2
                    + ")) OR (OBJECT_TYPE = 'PACKAGE BODY' AND OBJECT_NAME = 'KIL_WISSEN')) THEN 1 END) "
                    + "FROM USER_OBJECTS WHERE OBJECT_NAME LIKE 'KIL\\_%' ESCAPE '\\'");
                 ResultSet rs = ps.executeQuery()) { rs.next(); n = rs.getInt(1); alle = rs.getInt(2); n2 = rs.getInt(3); }
            tabellen = alle;
            wissen = n2 == TABELLEN_WISSEN.length + 1;
            if (n == TABELLEN.length + 1) {
                try (CallableStatement cs = c.prepareCall("{? = call KIL_API.version}")) {
                    cs.registerOutParameter(1, Types.VARCHAR);
                    cs.execute();
                    fassung = cs.getString(1);
                } catch (SQLException e) { fassung = ""; }
                lage(Lage.BEREIT, "");
            } else {
                lage(Lage.NICHT_EINGERICHTET, n + " von " + (TABELLEN.length + 1) + " Objekten vorhanden; „Einrichten …“ legt den Rest an");
            }
        } catch (SQLException e) {
            abbruch(e);
        }
    }

    private static Connection verb() throws SQLException {
        if (conn != null) {
            try { if (conn.isValid(2)) return conn; } catch (SQLException e) { /* neu öffnen */ }
            try { conn.close(); } catch (SQLException e) { /* egal */ }
            conn = null;
        }
        Db.Konfig k = konfig;
        if (k == null || !k.hatPasswort()) throw new SQLException("keine Zugangsdaten");
        conn = Db.oeffnen(k);
        return conn;
    }

    private static void abbruch(SQLException e) {
        String m = erste(e.getMessage());
        // Ein Fehler der Anweisung (zu langer Text, verletzte Regel) ist kein Verbindungsfehler: die Verbindung bleibt
        try {
            if (e.getErrorCode() != 1017 && conn != null && conn.isValid(2)) { datei(neu("WARNUNG", "Datenbank", m, false)); return; }
        } catch (SQLException x) { /* dann ist sie wirklich weg */ }
        if (e.getErrorCode() == 1017) {
            konfig = konfig == null ? null : konfig.mitPasswort(null);
            lage(Lage.KEIN_PASSWORT, "Benutzer oder Passwort stimmen nicht: " + m);
        } else lage(Lage.OFFLINE, m);
        try { if (conn != null) conn.close(); } catch (SQLException x) { /* egal */ }
        conn = null;
    }

    /** Erste Zeile einer Meldung, ohne die lange CONNECTION_ID des Treibers. */
    static String erste(String m) {
        if (m == null) return "";
        int i = m.indexOf('\n');
        return (i > 0 ? m.substring(0, i) : m).replaceAll("\\s*\\(CONNECTION_ID=[^)]*\\)", "").trim();
    }

    /** Eine eigene Verbindung für den Einrichter (er läuft auf seinem Faden). */
    public static Connection neueVerbindung() throws SQLException {
        Db.Konfig k = konfig;
        if (k == null || !k.hatPasswort()) throw new SQLException("keine Zugangsdaten");
        return Db.oeffnen(k);
    }

    public static <T> Future<T> aufgabe(Callable<T> c) { return EX.submit(c); }

    /** Regelmäßig auf dem DB-Faden (Läufe sammeln und schreiben). */
    static void alle(Runnable r, long ms) { EX.scheduleWithFixedDelay(r, ms, ms, TimeUnit.MILLISECONDS); }

    /** Auf dem DB-Faden: auszuführen, sobald dort Platz ist. */
    static void spaeter(Runnable r) { EX.submit(r); }

    /** Für Läufe: die Verbindung des DB-Fadens (nur dort aufrufen). */
    static Connection verbindung() throws SQLException { return verb(); }

    /** Für Läufe: ein Fehler beim Schreiben (Verbindung weg oder Anweisung abgelehnt). */
    static void fehler(SQLException e) { abbruch(e); }

    /** Läuft beim Beenden im DB-Faden, bevor die Sitzung geschlossen wird. */
    static void vorDemEnde(Runnable r) { vorEnde.add(r); }

    /** Schaltet die Datenbank für diese Sitzung ab (Zustand und Ansichten gehen in Dateien). */
    public static void abschalten() { lage(Lage.AUS, ""); }

    /** Wartet, bis die erste Prüfung fertig ist (höchstens ms); true, wenn sie fertig ist. */
    public static boolean warte(long ms) {
        long ende = System.currentTimeMillis() + ms;
        while (lage == Lage.PRUEFT && System.currentTimeMillis() < ende) {
            try { Thread.sleep(50); } catch (InterruptedException e) { return false; }
        }
        return lage != Lage.PRUEFT;
    }

    // ------------------------------------------------------------ Zustand

    /** Der letzte AUTO-Zustand: aus der Datenbank, sonst aus der Datei; null, wenn es keinen gibt. Höchstens 3 s. */
    public static Zustand ladeLetzten() {
        Zustand db = null;
        if (lage == Lage.BEREIT) {
            try {
                db = EX.submit(() -> {
                    Connection c = verb();
                    long id = ZustandDb.juengster(c, "AUTO");
                    return id == 0 ? null : ZustandDb.laden(c, id);
                }).get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            }
        }
        Zustand datei = Zustand.laden(zustandsDatei());
        // der jüngere gewinnt (die Datei kann neuer sein, wenn die Datenbank beim letzten Mal fehlte)
        if (db == null) return datei;
        if (datei != null && datei.gespeichertMs > db.gespeichertMs + 5000) return datei;
        return db;
    }

    /** Zwischensicherung (alle 60 Sekunden): Datei sofort, Datenbank im Hintergrund; von AUTO bleiben die letzten 10. */
    public static void zwischensicherung(Zustand z) {
        if (z == null || beendet.get()) return;
        z.art = "AUTO";
        z.name = "AUTO";
        z.gespeichertMs = System.currentTimeMillis();
        try { Zustand.speichern(zustandsDatei(), z); } catch (IOException e) { datei(neu("WARNUNG", "Zustand", "Datei nicht geschrieben: " + e.getMessage(), false)); }
        EX.submit(() -> {
            if (lage != Lage.BEREIT) return;
            try {
                Connection c = verb();
                ZustandDb.speichern(c, z, sitzungId);
                try (CallableStatement p = c.prepareCall("{call KIL_API.aufraeumen(10, 90)}")) { p.execute(); }
            } catch (SQLException e) {
                abbruch(e);
            }
        });
    }

    // ------------------------------------------------------------ Ansichten (Frage 27 = A)

    /** Eine Ansicht in der Liste: aus der Datenbank oder aus der Datei. */
    public static final class Ansicht {
        public final boolean ausDatei;
        public final long id;
        public final String name;
        public final long ms;
        public final boolean mitZeit;
        Ansicht(boolean ausDatei, long id, String name, long ms, boolean mitZeit) { this.ausDatei = ausDatei; this.id = id; this.name = name; this.ms = ms; this.mitZeit = mitZeit; }
        @Override public String toString() { return name + (mitZeit ? " · mit Zeit" : "") + (ausDatei ? " · Datei" : ""); }
    }

    /** Alle Ansichten: aus der Datenbank (wenn bereit) und aus der Datei, neueste zuerst. */
    public static List<Ansicht> ansichten() {
        List<Ansicht> l = new ArrayList<>();
        if (lage == Lage.BEREIT) {
            try {
                for (ZustandDb.Eintrag e : EX.submit(() -> ZustandDb.ansichten(verb())).get(3, TimeUnit.SECONDS))
                    l.add(new Ansicht(false, e.id, e.name, e.ms, e.mitZeit));
            } catch (Exception e) {
                if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            }
        }
        File[] fs = ansichtsOrdner().listFiles((d, n) -> n.matches("a\\d+\\.properties"));
        if (fs != null) {
            for (File f : fs) {
                Zustand z = Zustand.laden(f);
                if (z == null || !z.hatKamera()) continue;
                long id = Long.parseLong(f.getName().replaceAll("\\D", ""));
                l.add(new Ansicht(true, id, z.name, z.gespeichertMs, z.hatZeit()));
            }
        }
        l.sort((a, b) -> Long.compare(b.ms, a.ms));
        return l;
    }

    /** Merkt eine Ansicht: mit Datenbank dort, sonst in der Datei. Liefert eine Meldung für die Oberfläche. */
    public static String merken(Zustand z, String name) {
        z.art = "ANSICHT";
        z.name = name.length() > 60 ? name.substring(0, 60) : name;
        z.gespeichertMs = System.currentTimeMillis();
        if (lage == Lage.BEREIT) {
            try {
                EX.submit(() -> ZustandDb.speichern(verb(), z, sitzungId)).get(3, TimeUnit.SECONDS);
                ereignis("ANSICHT", z.name, "gemerkt" + (z.hatZeit() ? " mit Zeit" : ""), true);
                return "„" + z.name + "“ in der Datenbank gemerkt.";
            } catch (Exception e) {
                if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            }
        }
        try {
            long id = System.currentTimeMillis();
            Zustand.speichern(new File(ansichtsOrdner(), "a" + id + ".properties"), z);
            ereignis("ANSICHT", z.name, "gemerkt in der Datei" + (z.hatZeit() ? ", mit Zeit" : ""), true);
            return "„" + z.name + "“ in der Datei gemerkt (ohne Datenbank).";
        } catch (IOException e) {
            return "Nicht gemerkt: " + e.getMessage();
        }
    }

    public static Zustand laden(Ansicht a) {
        if (a.ausDatei) return Zustand.laden(new File(ansichtsOrdner(), "a" + a.id + ".properties"));
        if (lage != Lage.BEREIT) return null;
        try {
            return EX.submit(() -> ZustandDb.laden(verb(), a.id)).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            return null;
        }
    }

    public static boolean umbenennen(Ansicht a, String name) {
        String n = name.length() > 60 ? name.substring(0, 60) : name;
        try {
            if (a.ausDatei) {
                File f = new File(ansichtsOrdner(), "a" + a.id + ".properties");
                Zustand z = Zustand.laden(f);
                if (z == null) return false;
                z.name = n;
                Zustand.speichern(f, z);
            } else {
                if (lage != Lage.BEREIT) return false;
                EX.submit(() -> { ZustandDb.umbenennen(verb(), a.id, n); return null; }).get(3, TimeUnit.SECONDS);
            }
            ereignis("ANSICHT", n, "umbenannt (vorher „" + a.name + "“)", true);
            return true;
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            return false;
        }
    }

    public static boolean loeschen(Ansicht a) {
        try {
            if (a.ausDatei) Files.deleteIfExists(new File(ansichtsOrdner(), "a" + a.id + ".properties").toPath());
            else {
                if (lage != Lage.BEREIT) return false;
                EX.submit(() -> { ZustandDb.loeschen(verb(), a.id); return null; }).get(3, TimeUnit.SECONDS);
            }
            ereignis("ANSICHT", a.name, "gelöscht", true);
            return true;
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            return false;
        }
    }

    // ------------------------------------------------------------ Sitzung

    private static volatile Object[] sitzungDaten;

    /** Sitzung beginnen; ist die Datenbank (noch) nicht bereit, wird sie angelegt, sobald sie es ist. */
    public static void sitzungStart(int fensterB, int fensterH, String qualitaet, boolean zustandGeladen) {
        startNs = System.nanoTime();
        sitzungDaten = new Object[]{fensterB, fensterH, qualitaet, zustandGeladen};
        EX.submit(() -> { if (lage == Lage.BEREIT) sitzungAnlegen(); });
        ereignis("START", "Kīlauea " + APP_VERSION, zustandGeladen ? "weiter wie zuletzt" : "neu am Gipfel", false);
    }

    private static volatile String grafik, opengl;

    private static void sitzungAnlegen() {
        Object[] d = sitzungDaten;
        if (d == null || sitzungId > 0) return;
        String rechner = "";
        try { rechner = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { /* egal */ }
        try (CallableStatement cs = verb().prepareCall("{? = call KIL_API.sitzung_start(?,?,?,?,?,?,?,?,?,?)}")) {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setString(2, rechner);
            cs.setString(3, System.getProperty("user.name"));
            cs.setString(4, APP_VERSION);
            cs.setString(5, System.getProperty("java.version"));
            cs.setInt(6, Runtime.getRuntime().availableProcessors());
            cs.setLong(7, Runtime.getRuntime().maxMemory() >> 20);
            cs.setInt(8, (Integer) d[0]);
            cs.setInt(9, (Integer) d[1]);
            cs.setString(10, (String) d[2]);
            cs.setString(11, (Boolean) d[3] ? "J" : "N");
            cs.execute();
            sitzungId = cs.getLong(1);
            if (grafik != null) grafikSchreiben();
        } catch (SQLException e) {
            abbruch(e);
        }
    }

    /** Grafikkarte nachtragen, sobald der Treiber sie nennt (etwa „AMD Radeon(TM) Graphics · OpenGL 4.6“). */
    public static void sitzungGrafik(String glInfo) {
        if (glInfo == null || glInfo.isEmpty()) return;
        int i = glInfo.lastIndexOf(" · OpenGL ");
        grafik = i > 0 ? glInfo.substring(0, i) : glInfo;
        opengl = i > 0 ? glInfo.substring(i + 3) : "";
        EX.submit(() -> { if (lage == Lage.BEREIT && sitzungId > 0) grafikSchreiben(); });
    }

    private static void grafikSchreiben() {
        try (CallableStatement cs = verb().prepareCall("{call KIL_API.sitzung_grafik(?,?,?)}")) {
            cs.setLong(1, sitzungId);
            cs.setString(2, grafik);
            cs.setString(3, opengl);
            cs.execute();
        } catch (SQLException e) {
            abbruch(e);
        }
    }

    /** Einmal je Sekunde: Bilder/s und Zeiten für das Mittel der Sitzung. */
    public static synchronized void messung(double fps, double gpuMs, double cpuMs) {
        if (!(fps > 0) || !Double.isFinite(fps)) return;
        sumFps += fps; sumGpu += gpuMs; sumCpu += cpuMs; nMess++;
    }

    private static synchronized double[] mittel() {
        if (nMess == 0) return new double[]{0, 0, 0};
        return new double[]{sumFps / nMess, sumGpu / nMess, sumCpu / nMess};
    }

    // ------------------------------------------------------------ Protokoll

    /** Ein Ereignis ins Protokoll; blockiert nie. mitOrt: den Ort unter dem Blick mitschreiben. */
    public static void ereignis(String art, String ziel, String text, boolean mitOrt) {
        Eintrag e = neu(art, ziel, text, mitOrt);
        synchronized (letzte) {
            letzte.addFirst(e.kurz());
            while (letzte.size() > 20) letzte.removeLast();
        }
        datei(e);
        synchronized (warteschlange) { warteschlange.add(e); }
        if (art.equals("FEHLER") || art.equals("ENDE")) EX.submit(Dienst::schreibeWarteschlange);
        for (Runnable r : protokollLauscher) javax.swing.SwingUtilities.invokeLater(r);
    }

    private static final List<Runnable> protokollLauscher = new CopyOnWriteArrayList<>();
    public static void beiProtokoll(Runnable r) { protokollLauscher.add(r); }

    /** Die letzten (höchstens 20) Einträge, neueste zuerst. */
    public static List<String> letzte() { synchronized (letzte) { return new ArrayList<>(letzte); } }

    private static Eintrag neu(String art, String ziel, String text, boolean mitOrt) {
        boolean bekannt = false;
        for (String a : ARTEN) if (a.equals(art)) bekannt = true;
        if (!bekannt) { text = art + ": " + leer(text); art = "INFO"; }
        Kontext k = kontext;
        int tag = 0;
        double stunde = 0;
        double[] ort = null;
        if (k != null) {
            try {
                tag = k.tag();
                stunde = k.stunde();
                if (mitOrt) ort = k.ort();
            } catch (RuntimeException ex) { /* Kontext noch nicht bereit */ }
        }
        return new Eintrag(LocalDateTime.now(), (System.nanoTime() - startNs) / 1e9, art, ziel, text, tag, stunde, ort);
    }

    private static final Object dateiSperre = new Object();

    private static void datei(Eintrag e) {
        synchronized (dateiSperre) {
            try {
                File dir = logOrdner();
                Files.createDirectories(dir.toPath());
                File f = new File(dir, "kilauea_" + LocalDate.now() + ".log");
                Files.write(f.toPath(), (e.zeile() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException x) { /* das Protokoll darf das Programm nie stören */ }
        }
    }

    /** Tagesprotokolle älter als 14 Tage löschen. */
    static void aufraeumenLogs() {
        File[] fs = logOrdner().listFiles((d, n) -> n.matches("kilauea_\\d{4}-\\d{2}-\\d{2}\\.log"));
        if (fs == null) return;
        LocalDate grenze = LocalDate.now().minusDays(14);
        for (File f : fs) {
            try {
                LocalDate d = LocalDate.parse(f.getName().substring(8, 18));
                if (d.isBefore(grenze)) Files.deleteIfExists(f.toPath());
            } catch (Exception e) { /* übergehen */ }
        }
    }

    /** Schreibt die wartenden Einträge gebündelt in die Datenbank (auf dem DB-Faden). */
    private static void schreibeWarteschlange() {
        List<Eintrag> l;
        synchronized (warteschlange) {
            if (warteschlange.isEmpty()) return;
            if (lage != Lage.BEREIT || sitzungId == 0) {
                // ohne Datenbank: nur die Datei; nicht ewig sammeln
                if (lage != Lage.PRUEFT && lage != Lage.BEREIT) warteschlange.clear();
                return;
            }
            l = new ArrayList<>(warteschlange);
            warteschlange.clear();
        }
        try (CallableStatement cs = verb().prepareCall("{call KIL_API.protokoll(?,?,?,?,?,?,?,?,?)}")) {
            for (Eintrag e : l) {
                cs.setLong(1, sitzungId);
                cs.setDouble(2, Math.round(e.sekunde * 10) / 10.0);
                cs.setString(3, e.art);
                cs.setString(4, e.ziel);
                cs.setString(5, e.text);
                if (e.tag > 0) { cs.setInt(6, e.tag); cs.setDouble(7, Math.min(23.99, Math.round(e.stunde * 100) / 100.0)); }
                else { cs.setNull(6, Types.NUMERIC); cs.setNull(7, Types.NUMERIC); }
                if (e.ort != null) { cs.setDouble(8, e.ort[0]); cs.setDouble(9, e.ort[1]); }
                else { cs.setNull(8, Types.NUMERIC); cs.setNull(9, Types.NUMERIC); }
                cs.addBatch();
            }
            cs.executeBatch();
        } catch (SQLException e) {
            abbruch(e);
        }
    }

    // ------------------------------------------------------------ Ende

    /**
     * Beim Beenden: Zustand in die Datei und, wenn die Datenbank bereit ist, nach KIL_ZUSTAND (AUTO, die letzten 10 bleiben);
     * Protokoll schreiben, Sitzung schließen. Wartet höchstens 2 s. z darf null sein: dann bleibt der letzte Zustand.
     */
    public static void beenden(Zustand z) {
        if (!beendet.compareAndSet(false, true)) return;
        if (z != null) {
            z.art = "AUTO";
            z.name = "AUTO";
            z.gespeichertMs = System.currentTimeMillis();
            try { Zustand.speichern(zustandsDatei(), z); } catch (IOException e) { datei(neu("WARNUNG", "Zustand", "Datei nicht geschrieben: " + e.getMessage(), false)); }
        }
        final double sek = (System.nanoTime() - startNs) / 1e9;
        double[] m = mittel();
        ereignis("ENDE", "Kīlauea " + APP_VERSION, String.format(java.util.Locale.GERMAN, "%.0f s, im Mittel %.0f Bilder/s, GPU %.1f ms, CPU %.1f ms", sek, m[0], m[1], m[2]), false);
        Future<?> f = EX.submit(() -> {
            if (lage != Lage.BEREIT) return;
            try {
                Connection c = verb();
                if (z != null && z.hatKamera()) {
                    ZustandDb.speichern(c, z, sitzungId);
                    try (CallableStatement p = c.prepareCall("{call KIL_API.aufraeumen(10, 90)}")) { p.execute(); }
                }
                for (Runnable r : vorEnde) {
                    try { r.run(); } catch (RuntimeException e) { datei(neu("WARNUNG", "Beenden", String.valueOf(e.getMessage()), false)); }
                }
                schreibeWarteschlange();
                if (sitzungId > 0) {
                    try (CallableStatement cs = c.prepareCall("{call KIL_API.sitzung_ende(?,?,?,?,?)}")) {
                        cs.setLong(1, sitzungId);
                        cs.setDouble(2, m[0]);
                        cs.setDouble(3, m[1]);
                        cs.setDouble(4, m[2]);
                        Runtime rt = Runtime.getRuntime();
                        cs.setLong(5, (rt.totalMemory() - rt.freeMemory()) >> 20);
                        cs.execute();
                    }
                }
            } catch (SQLException e) {
                datei(neu("WARNUNG", "Beenden", e.getMessage(), false));
            }
        });
        try { f.get(2, TimeUnit.SECONDS); } catch (Exception e) { /* nach 2 s nicht länger warten */ }
    }

    public static boolean beendet() { return beendet.get(); }

    /** Nur für die Prüfung: Zustand der Klasse zurücksetzen. */
    static void zuruecksetzenFuerPruefung() {
        beendet.set(false);
        synchronized (letzte) { letzte.clear(); }
        synchronized (warteschlange) { warteschlange.clear(); }
    }

    /** Für die Prüfung: Ansicht aus der Datei als Eintrag. */
    static Supplier<File> ansichtsOrdnerFuerPruefung() { return Dienst::ansichtsOrdner; }
}
