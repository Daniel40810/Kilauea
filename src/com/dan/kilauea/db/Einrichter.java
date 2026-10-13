package com.dan.kilauea.db;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Der Einrichter (Phase 16, nach Kneiphof): prüft Verbindung und Voraussetzungen (Rechte, Zeichensatz, Spatial, Platz im
 * Tablespace), nimmt den Bestand auf (db/bestand.txt), führt die Skripte db/NN_*.sql aus und prüft danach jedes Objekt, die
 * Spatial-Metadaten und -Indizes und das Paket mit einer Funktionsprobe, die alles zurücknimmt. Er meldet
 * "ERGEBNIS: alles in Ordnung" oder die Zahl der Probleme; das Protokoll steht in db/einrichtung.txt.
 * <p>
 * Er legt nur Fehlendes an: Was es schon gibt, wird nicht verändert und nicht gelöscht.
 */
public final class Einrichter {
    private Einrichter() { }

    public interface Ausgabe { void zeile(String s); }

    public static final class Ergebnis {
        public final boolean ok;
        public final int probleme;
        public final String text;
        Ergebnis(int probleme, String text) { this.ok = probleme == 0; this.probleme = probleme; this.text = text; }
    }

    /** Fehlernummern, die nur sagen "gibt es schon": Name belegt, Spalten schon indiziert, Schlüssel und Fremdschlüssel da. */
    private static final Set<Integer> VORHANDEN = new HashSet<>(Arrays.asList(955, 1408, 1430, 2260, 2261, 2264, 2275));
    /** Rechte, ohne die nichts geht. */
    static final String[] RECHTE = {"CREATE SESSION", "CREATE TABLE", "CREATE SEQUENCE", "CREATE TRIGGER", "CREATE PROCEDURE"};
    /** Geometriespalten mit Metadaten und Spatial-Index (2 aus Phase 16a, 10 aus 16b). */
    static final String[][] GEOMETRIEN = {{"KIL_ZUSTAND", "GEOM"}, {"KIL_PROTOKOLL", "GEOM"},
            {"KIL_ORT", "GEOM"}, {"KIL_KRATER", "MITTE"}, {"KIL_KRATER", "RAND"}, {"KIL_STROM", "SCHLOT"}, {"KIL_STROM", "SPALTE"},
            {"KIL_STROM", "ACHSE"}, {"KIL_STROM", "UMRISS"}, {"KIL_STATION", "GEOM"}, {"KIL_LAUF", "GEOM"}, {"KIL_BEBEN", "GEOM"}};
    /** Geladene Geometrien, deren Gültigkeit nach db/06 geprüft wird. */
    static final String[][] GELADEN = {{"KIL_KRATER", "RAND", "NAME"}, {"KIL_STROM", "SPALTE", "NAME"}, {"KIL_STROM", "ACHSE", "NAME"}, {"KIL_STROM", "UMRISS", "NAME"}};

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public static Ergebnis lauf(Connection c, File dbDir, Ausgabe aus) {
        final StringBuilder log = new StringBuilder();
        final Ausgabe out = s -> { log.append(s).append('\n'); if (aus != null) aus.zeile(s); };
        int probleme = 0;
        out.zeile("Einrichter Kīlauea · Phase 16b · " + LocalDateTime.now().format(ZEIT));
        try {
            // ---- 1 Verbindung
            out.zeile("");
            out.zeile("1  Verbindung");
            String user;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT SYS_CONTEXT('USERENV','SESSION_USER'), SYS_CONTEXT('USERENV','CON_NAME'), SYS_CONTEXT('USERENV','SERVICE_NAME') FROM DUAL")) {
                rs.next();
                user = rs.getString(1);
                out.zeile("   Benutzer " + user + ", Container " + rs.getString(2) + ", Dienst " + rs.getString(3));
            }
            out.zeile("   " + c.getMetaData().getDatabaseProductVersion().split("\n")[0].trim());

            // ---- 2 Voraussetzungen
            out.zeile("");
            out.zeile("2  Voraussetzungen");
            int vor = voraussetzungen(c, out);
            if (vor > 0) {
                out.zeile("   Ohne diese Voraussetzungen legt der Einrichter nichts an.");
                return fertig(vor, log, dbDir, out);
            }

            // ---- 3 Bestand
            out.zeile("");
            out.zeile("3  Bestand im Schema");
            Map<String, Integer> typen = new TreeMap<>();
            StringBuilder best = new StringBuilder("Bestand im Schema " + user + ", aufgenommen " + LocalDateTime.now().format(ZEIT) + "\n\nTYP\tNAME\tSTATUS\n");
            int kil = 0, alle = 0;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT OBJECT_TYPE, OBJECT_NAME, STATUS FROM USER_OBJECTS ORDER BY OBJECT_TYPE, OBJECT_NAME")) {
                while (rs.next()) {
                    String t = rs.getString(1), n = rs.getString(2);
                    typen.merge(t, 1, Integer::sum);
                    best.append(t).append('\t').append(n).append('\t').append(rs.getString(3)).append('\n');
                    alle++;
                    if (n.startsWith("KIL_")) kil++;
                }
            }
            StringBuilder sum = new StringBuilder();
            for (String t : new String[]{"TABLE", "VIEW", "SEQUENCE", "TRIGGER", "PACKAGE", "INDEX"}) {
                Integer n = typen.get(t);
                if (n == null) continue;
                if (sum.length() > 0) sum.append(", ");
                sum.append(n).append(' ').append(t);
            }
            out.zeile("   " + alle + " Objekte" + (sum.length() > 0 ? " (" + sum + ")" : ""));
            out.zeile(kil == 0 ? "   Das Präfix KIL_ ist frei." : "   " + kil + " Objekte mit dem Präfix KIL_ gibt es schon; sie bleiben, nur Fehlendes wird ergänzt.");
            schreibe(new File(dbDir, "bestand.txt"), best.toString(), out);

            // ---- 4 Skripte
            out.zeile("");
            out.zeile("4  Skripte in " + dbDir.getPath());
            List<Skript> skripte;
            try {
                skripte = Skript.laden(dbDir);
            } catch (IOException e) {
                out.zeile("   FEHLER: " + e.getMessage());
                return fertig(1, log, dbDir, out);
            }
            Map<String, Integer> zeilen = new LinkedHashMap<>();
            Set<Skript.Objekt> erwartet = new LinkedHashSet<>();
            try (Statement st = c.createStatement()) {
                st.setEscapeProcessing(false);
                for (Skript s : skripte) {
                    int neu = 0, vorh = 0, fehler = 0;
                    long rows = 0;
                    List<String> meldungen = new ArrayList<>();
                    for (String sql : s.anweisungen) {
                        try {
                            st.clearWarnings();
                            st.execute(sql);
                            int n = st.getUpdateCount();
                            if (n > 0 && (sql.regionMatches(true, 0, "INSERT", 0, 6) || sql.regionMatches(true, 0, "MERGE", 0, 5))) rows += n;
                            neu++;
                            SQLWarning w = st.getWarnings();
                            // mit Übersetzungsfehlern angelegt: das zeigt die Prüfung unten (UNGÜLTIG und USER_ERRORS)
                            if (w != null) meldungen.add("   Hinweis bei " + Skript.kurz(sql) + ": " + Dienst.erste(w.getMessage()));
                        } catch (SQLException e) {
                            if (VORHANDEN.contains(e.getErrorCode())) vorh++;
                            else {
                                fehler++;
                                meldungen.add("   FEHLER ORA-" + String.format("%05d", e.getErrorCode()) + " bei " + Skript.kurz(sql) + ": " + Dienst.erste(e.getMessage()));
                            }
                        }
                    }
                    String z = s.name + ": " + s.anweisungen.size() + " Anweisungen, " + neu + " ausgeführt, " + vorh + " schon vorhanden, " + fehler + " Fehler";
                    if (rows > 0) z += ", " + rows + " Datenzeilen neu";
                    out.zeile("   " + z);
                    for (String m : meldungen) out.zeile(m);
                    probleme += fehler;
                    erwartet.addAll(s.erwartet());
                    for (Map.Entry<String, Integer> e : s.erwartetZeilen.entrySet()) zeilen.merge(e.getKey(), e.getValue(), Math::max);
                }
            }

            // ---- 5 Prüfung
            out.zeile("");
            out.zeile("5  Prüfung");
            probleme += pruefeObjekte(c, erwartet, out);
            probleme += pruefeSpatial(c, out);
            probleme += funktionsprobe(c, out);
            probleme += pruefeZeilen(c, zeilen, out);
            probleme += pruefeGeometrien(c, out);
            probleme += funktionsprobeWissen(c, out);
        } catch (SQLException e) {
            out.zeile("   FEHLER: " + Dienst.erste(e.getMessage()));
            probleme++;
        }
        return fertig(probleme, log, dbDir, out);
    }

    /** Rechte, Spatial, Zeichensatz und Platz; liefert die Zahl der Hindernisse (der Zeichensatz ist nur ein Hinweis). */
    static int voraussetzungen(Connection c, Ausgabe out) throws SQLException {
        int hindernisse = 0;
        Set<String> rechte = new TreeSet<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT PRIVILEGE FROM SESSION_PRIVS")) {
            while (rs.next()) rechte.add(rs.getString(1));
        }
        List<String> fehlen = new ArrayList<>();
        for (String r : RECHTE) if (!rechte.contains(r)) fehlen.add(r);
        if (fehlen.isEmpty()) out.zeile("   Rechte: " + String.join(", ", RECHTE) + " vorhanden");
        else { out.zeile("   FEHLT: Rechte " + String.join(", ", fehlen) + " (Rolle DB_CONNECT prüfen)"); hindernisse++; }

        int sdo;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM ALL_TYPES WHERE OWNER = 'MDSYS' AND TYPE_NAME = 'SDO_GEOMETRY'")) { rs.next(); sdo = rs.getInt(1); }
        if (sdo > 0) {
            String v = "";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MDSYS.SDO_VERSION FROM DUAL")) { if (rs.next()) v = " " + rs.getString(1); }
            catch (SQLException e) { /* Fassung ist nur zur Anzeige */ }
            out.zeile("   Spatial: MDSYS.SDO_GEOMETRY vorhanden" + v);
        } else { out.zeile("   FEHLT: Oracle Spatial (MDSYS.SDO_GEOMETRY ist nicht sichtbar)"); hindernisse++; }

        String cs = "";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET'")) { if (rs.next()) cs = rs.getString(1); }
        if ("AL32UTF8".equals(cs)) out.zeile("   Zeichensatz: AL32UTF8 (ʻokina und Makron bleiben erhalten)");
        else out.zeile("   HINWEIS: Zeichensatz " + cs + " statt AL32UTF8; Namen wie Halemaʻumaʻu verlieren ʻ und ā. Die Tabellen funktionieren trotzdem.");

        String ts = "";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT DEFAULT_TABLESPACE FROM USER_USERS")) { if (rs.next()) ts = rs.getString(1); }
        boolean unbegrenzt = rechte.contains("UNLIMITED TABLESPACE");
        long quote = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX_BYTES, BYTES FROM USER_TS_QUOTAS WHERE TABLESPACE_NAME = ?")) {
            ps.setString(1, ts);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) quote = rs.getLong(1) < 0 ? Long.MAX_VALUE : Math.max(0, rs.getLong(1) - rs.getLong(2));
            }
        }
        if (unbegrenzt || quote == Long.MAX_VALUE) out.zeile("   Platz: Tablespace " + ts + ", unbegrenzt");
        else if (quote >= 50L << 20) out.zeile("   Platz: Tablespace " + ts + ", noch " + (quote >> 20) + " MB frei");
        else { out.zeile("   FEHLT: Platz im Tablespace " + ts + " (Quote " + (quote >> 20) + " MB; nötig sind rund 50 MB)"); hindernisse++; }
        return hindernisse;
    }

    private static Ergebnis fertig(int probleme, StringBuilder log, File dbDir, Ausgabe out) {
        out.zeile("");
        out.zeile(probleme == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + probleme + (probleme == 1 ? " Problem" : " Probleme") + ", siehe oben");
        schreibe(new File(dbDir, "einrichtung.txt"), log.toString(), null);
        return new Ergebnis(probleme, log.toString());
    }

    private static void schreibe(File f, String text, Ausgabe out) {
        try {
            if (f.getParentFile() != null) Files.createDirectories(f.getParentFile().toPath());
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
            if (out != null) out.zeile("   Bestand geschrieben nach " + f.getPath());
        } catch (IOException e) {
            if (out != null) out.zeile("   Hinweis: " + f.getPath() + " nicht schreibbar (" + e.getMessage() + ")");
        }
    }

    /** Jedes erwartete Objekt muss da und gültig sein; Übersetzungsfehler aus USER_ERRORS werden gezeigt. */
    private static int pruefeObjekte(Connection c, Set<Skript.Objekt> erwartet, Ausgabe out) throws SQLException {
        Map<String, String> status = new java.util.HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT OBJECT_TYPE, OBJECT_NAME, STATUS FROM USER_OBJECTS WHERE OBJECT_NAME LIKE 'KIL\\_%' ESCAPE '\\'")) {
            while (rs.next()) status.put(rs.getString(1) + " " + rs.getString(2), rs.getString(3));
        }
        int probleme = 0;
        Map<String, int[]> je = new TreeMap<>();
        for (Skript.Objekt o : erwartet) {
            int[] n = je.computeIfAbsent(o.typ, k -> new int[2]);
            String s = status.get(o.typ + " " + o.name);
            if (s == null) { out.zeile("   FEHLT: " + o.typ + " " + o.name); probleme++; }
            else if (!s.equals("VALID")) { out.zeile("   UNGÜLTIG: " + o.typ + " " + o.name); probleme++; }
            else n[0]++;
        }
        StringBuilder sum = new StringBuilder();
        for (Map.Entry<String, int[]> e : je.entrySet()) {
            if (sum.length() > 0) sum.append(", ");
            sum.append(e.getValue()[0]).append(' ').append(e.getKey());
        }
        out.zeile("   Objekte vorhanden und gültig: " + sum);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT NAME, TYPE, LINE, TEXT FROM USER_ERRORS WHERE NAME LIKE 'KIL\\_%' ESCAPE '\\' ORDER BY NAME, TYPE, SEQUENCE")) {
            int k = 0;
            while (rs.next() && k++ < 12) out.zeile("   Übersetzungsfehler " + rs.getString(1) + " (" + rs.getString(2) + ") Zeile " + rs.getInt(3) + ": " + Dienst.erste(rs.getString(4)));
        }
        return probleme;
    }

    /** Metadaten (SRID 8307, zwei Dimensionen) und Spatial-Index je Geometriespalte. */
    private static int pruefeSpatial(Connection c, Ausgabe out) throws SQLException {
        int probleme = 0, ok = 0;
        for (String[] g : GEOMETRIEN) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT M.SRID, (SELECT COUNT(*) FROM TABLE(M.DIMINFO)), "
                            + "(SELECT MAX(I.INDEX_NAME || ':' || I.DOMIDX_STATUS || ':' || I.DOMIDX_OPSTATUS) FROM USER_INDEXES I JOIN USER_IND_COLUMNS C "
                            + "ON C.INDEX_NAME = I.INDEX_NAME WHERE I.TABLE_NAME = M.TABLE_NAME AND C.COLUMN_NAME = M.COLUMN_NAME AND I.INDEX_TYPE = 'DOMAIN') "
                            + "FROM USER_SDO_GEOM_METADATA M WHERE M.TABLE_NAME = ? AND M.COLUMN_NAME = ?")) {
                ps.setString(1, g[0]);
                ps.setString(2, g[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { out.zeile("   FEHLT: Spatial-Metadaten für " + g[0] + "." + g[1]); probleme++; continue; }
                    int srid = rs.getInt(1), dim = rs.getInt(2);
                    String ix = rs.getString(3);
                    if (srid != 8307 || dim != 2) { out.zeile("   FALSCH: " + g[0] + "." + g[1] + " hat SRID " + srid + " und " + dim + " Dimensionen (erwartet 8307 und 2)"); probleme++; continue; }
                    if (ix == null) { out.zeile("   FEHLT: Spatial-Index auf " + g[0] + "." + g[1]); probleme++; continue; }
                    String[] p = ix.split(":", -1);
                    if (!"VALID".equals(p[1]) || !"VALID".equals(p[2])) { out.zeile("   UNGÜLTIG: Spatial-Index " + p[0] + " (" + p[1] + ", " + p[2] + ")"); probleme++; continue; }
                    ok++;
                }
            }
        }
        out.zeile("   Spatial: " + ok + " von " + GEOMETRIEN.length + " Geometriespalten mit SRID 8307 und gültigem Index");
        return probleme;
    }

    /** Ein Zustand mit allen Spalten und zwei Schaltern für die Funktionsprobe (Halemaʻumaʻu, Blick von Uēkahuna). */
    public static Zustand probe() {
        Zustand z = new Zustand();
        z.art = "ANSICHT";
        z.name = "PROBE Halemaumau";
        z.setze("kamera.modus", "FLUG").setze("kamera.x", 0).setze("kamera.y", 830).setze("kamera.z", 0)
                .setze("kamera.gier", 145).setze("kamera.neigung", 28).setze("kamera.abstand", 2600)
                .setze("zeit.tag", 280).setze("zeit.stunde", 17.25).setze("zeit.raffer", 0).setze("luft.dunst", 25).setze("luft.wind", 7.5)
                .setze("luft.richtung", 70).setze("meer.swell", 1).setze("meer.periode", 14).setze("bild.qualitaet", "HOCH").setze("bild.belichtung", 0);
        z.setze("leben.nene", true).setze("klang.lautstaerke", "80");
        return z;
    }

    /** Sitzung, Protokoll mit Ort, Zustand mit Schaltern und Geometrie, Rücklesen und Suche im Umkreis; danach alles zurück. */
    private static int funktionsprobe(Connection c, Ausgabe out) {
        boolean auto = true;
        try {
            auto = c.getAutoCommit();
            c.setAutoCommit(false);
            String v;
            try (CallableStatement cs = c.prepareCall("{? = call KIL_API.version}")) {
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute();
                v = cs.getString(1);
            }
            long sid;
            try (CallableStatement cs = c.prepareCall("{? = call KIL_API.sitzung_start(?,?,?,?,?,?,?,?,?,?)}")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setString(2, "Probe"); cs.setString(3, "Einrichter"); cs.setString(4, Dienst.APP_VERSION); cs.setString(5, "Probe");
                cs.setInt(6, 1); cs.setInt(7, 1); cs.setInt(8, 1); cs.setInt(9, 1); cs.setString(10, "HOCH"); cs.setString(11, "N");
                cs.execute();
                sid = cs.getLong(1);
            }
            try (CallableStatement cs = c.prepareCall("{call KIL_API.sitzung_grafik(?,?,?)}")) {
                cs.setLong(1, sid); cs.setString(2, "Probe"); cs.setString(3, "OpenGL 4.6"); cs.execute();
            }
            try (CallableStatement cs = c.prepareCall("{call KIL_API.protokoll(?,?,?,?,?,?,?,?,?)}")) {
                cs.setLong(1, sid); cs.setDouble(2, 0); cs.setString(3, "EINRICHTER"); cs.setString(4, "Funktionsprobe"); cs.setString(5, "Halemaʻumaʻu");
                cs.setInt(6, 280); cs.setDouble(7, 17.25); cs.setDouble(8, -155.2834); cs.setDouble(9, 19.4072);
                cs.execute();
            }
            Zustand z = probe();
            long zid = ZustandDb.speichernOhneCommit(c, z, sid);
            Zustand zurueck = ZustandDb.laden(c, zid);
            boolean gleich = zurueck != null && zurueck.werte().equals(z.werte()) && z.name.equals(zurueck.name);
            long latest;
            try (CallableStatement cs = c.prepareCall("{? = call KIL_API.zustand_letzter('ANSICHT')}")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.execute();
                latest = cs.getLong(1);
            }
            String gueltig, gueltigP;
            int nah;
            try (PreparedStatement ps = c.prepareStatement("SELECT MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(GEOM, 0.05), "
                    + "(SELECT MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(P.GEOM, 0.05) FROM KIL_PROTOKOLL P WHERE P.SITZUNG_ID = ?) "
                    + "FROM KIL_ZUSTAND WHERE ZUSTAND_ID = ?")) {
                ps.setLong(1, sid); ps.setLong(2, zid);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); gueltig = rs.getString(1); gueltigP = rs.getString(2); }
            }
            String nahFehler = "";
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM KIL_ZUSTAND WHERE ZUSTAND_ID = ? AND "
                    + "MDSYS.SDO_WITHIN_DISTANCE(GEOM, KIL_API.punkt(-155.2834, 19.4072), 'distance=50 unit=M') = 'TRUE'")) {
                ps.setLong(1, zid);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); nah = rs.getInt(1); }
            } catch (SQLException e) {
                nah = -1;
                nahFehler = Dienst.erste(e.getMessage());
            }
            try (CallableStatement cs = c.prepareCall("{call KIL_API.sitzung_ende(?,?,?,?,?)}")) {
                cs.setLong(1, sid); cs.setDouble(2, 45); cs.setDouble(3, 20); cs.setDouble(4, 4); cs.setLong(5, 900);
                cs.execute();
            }
            int ev, dauer;
            try (PreparedStatement ps = c.prepareStatement("SELECT (SELECT COUNT(*) FROM KIL_PROTOKOLL WHERE SITZUNG_ID = ?), (SELECT NVL(DAUER_S, -1) FROM KIL_SITZUNG WHERE SITZUNG_ID = ?) FROM DUAL")) {
                ps.setLong(1, sid); ps.setLong(2, sid);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); ev = rs.getInt(1); dauer = rs.getInt(2); }
            }
            boolean gut = ev == 1 && dauer >= 0 && latest == zid && gleich && "TRUE".equals(gueltig) && "TRUE".equals(gueltigP);
            out.zeile("   Funktionsprobe (" + v + "): Sitzung, Protokoll mit Ort, Zustand mit " + z.schalter().size() + " Schaltern, Rücklesen, Geometrie "
                    + (gut ? "ok" : "FEHLER: Protokoll " + ev + ", Dauer " + dauer + ", jüngste Ansicht " + (latest == zid) + ", Rücklesen gleich " + gleich
                    + ", Geometrie " + gueltig + "/" + gueltigP));
            if (nah == 1) out.zeile("   Suche im Umkreis (SDO_WITHIN_DISTANCE, 50 m) findet den Zustand: ok");
            else out.zeile("   Hinweis: Suche im Umkreis fand den Zustand nicht" + (nah < 0 ? " (" + nahFehler + ")" : ""));
            out.zeile("   Alles zurückgenommen.");
            return gut ? 0 : 1;
        } catch (SQLException e) {
            out.zeile("   Funktionsprobe: FEHLER " + Dienst.erste(e.getMessage()));
            return 1;
        } finally {
            try { c.rollback(); c.setAutoCommit(auto); } catch (SQLException e) { /* ignorieren */ }
        }
    }

    private static int pruefeZeilen(Connection c, Map<String, Integer> erwartet, Ausgabe out) throws SQLException {
        if (erwartet.isEmpty()) return 0;
        int probleme = 0;
        StringBuilder sum = new StringBuilder();
        StringBuilder geo = new StringBuilder();
        for (Map.Entry<String, Integer> e : erwartet.entrySet()) {
            String k = e.getKey();
            if (!k.matches("[A-Z0-9_]+(\\.[A-Z0-9_]+)?")) continue;
            int p = k.indexOf('.');
            String sql = p < 0 ? "SELECT COUNT(*) FROM " + k : "SELECT COUNT(*) FROM " + k.substring(0, p) + " WHERE " + k.substring(p + 1) + " IS NOT NULL";
            int n;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { rs.next(); n = rs.getInt(1); }
            StringBuilder ziel = p < 0 ? sum : geo;
            if (ziel.length() > 0) ziel.append(", ");
            ziel.append(k.replace("KIL_", "")).append(' ').append(n);
            if (n < e.getValue()) {
                out.zeile(p < 0 ? "   ZU WENIG ZEILEN: " + k + " hat " + n + ", erwartet mindestens " + e.getValue()
                        : "   ZU WENIG GEOMETRIEN: " + k + " ist in " + n + " Zeilen gefüllt, erwartet mindestens " + e.getValue());
                probleme++;
            }
        }
        out.zeile("   Grunddaten (Zeilen): " + sum);
        if (geo.length() > 0) out.zeile("   Grunddaten (Geometrien): " + geo);
        return probleme;
    }

    /** Jede geladene Fläche und Linie gültig (VALIDATE_GEOMETRY_WITH_CONTEXT, 0,05 m)? Zeigt bis zu fünf ungültige. */
    private static int pruefeGeometrien(Connection c, Ausgabe out) {
        int probleme = 0, alle = 0;
        for (String[] g : GELADEN) {
            String sql = "SELECT " + g[2] + ", MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + g[1] + ", 0.05) FROM " + g[0] + " WHERE " + g[1] + " IS NOT NULL";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    alle++;
                    String v = rs.getString(2);
                    if (!"TRUE".equals(v)) {
                        if (probleme < 5) out.zeile("   UNGÜLTIG: " + g[0] + "." + g[1] + " " + rs.getString(1) + ": " + v);
                        probleme++;
                    }
                }
            } catch (SQLException e) {
                out.zeile("   Geometrien in " + g[0] + "." + g[1] + " nicht prüfbar: " + Dienst.erste(e.getMessage()));
                probleme++;
            }
        }
        if (alle > 0) out.zeile("   Geometrien gültig: " + (alle - probleme) + " von " + alle);
        return probleme;
    }

    /**
     * Phase 16b: KIL_WISSEN.version, wo_liegt am Halemaʻumaʻu (findet Ort, Krater und Strom), ein Lauf mit einem Beben und einer
     * Messung, Ende mit Zählung, Suche der Beben im Umkreis; danach alles zurück.
     */
    private static int funktionsprobeWissen(Connection c, Ausgabe out) {
        boolean auto = true;
        try {
            auto = c.getAutoCommit();
            c.setAutoCommit(false);
            String v;
            try (CallableStatement cs = c.prepareCall("{? = call KIL_WISSEN.version}")) {
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute();
                v = cs.getString(1);
            }
            String wo;
            try (PreparedStatement ps = c.prepareStatement("SELECT KIL_WISSEN.wo_liegt(19.4072, -155.2834) FROM DUAL");
                 ResultSet rs = ps.executeQuery()) { rs.next(); wo = rs.getString(1); }
            boolean woGut = wo != null && wo.startsWith("Ort: Halema") && wo.contains("Krater: Halema") && wo.contains("Kaluapele");
            long lauf = Laeufe.start(c, "2018", "PROBE Einrichter", new com.dan.kilauea.script.Script.Frei("OST", 40, 50, 300, 19.4625, -154.9085));
            Laeufe.schreibeBeben(c, lauf, List.of(new Laeufe.Beben(1.5, 5.3, 0.5, "EINSTURZ", 19.4072, -155.2834),
                    new Laeufe.Beben(2.25, 2.1, 3.0, "GANG", 19.45, -154.95)));
            Laeufe.schreibeMessungen(c, lauf, List.of(new Laeufe.Messung(0, 0, 0, 0, 0, 200, 0, 12), new Laeufe.Messung(0.25, -3.5, -1.2, -2, -4, 3500, 80, 950)));
            try (CallableStatement cs = c.prepareCall("{call KIL_WISSEN.lauf_stand(?,?,?)}")) { cs.setLong(1, lauf); cs.setInt(2, 2); cs.setDouble(3, 1.0); cs.execute(); }
            try (CallableStatement cs = c.prepareCall("{call KIL_WISSEN.lauf_ende(?,?,?,?,?)}")) {
                cs.setLong(1, lauf); cs.setInt(2, 3); cs.setString(3, "J"); cs.setDouble(4, 2.5); cs.setInt(5, 1000); cs.execute();
            }
            int beben, mess, kap, db;
            double maxM;
            String abbr;
            try (PreparedStatement ps = c.prepareStatement("SELECT BEBEN, MESSUNGEN, MAX_STAERKE, KAPITEL_BIS, ABGEBROCHEN, NVL(DREHBUCH_ID, 0) FROM KIL_LAUF WHERE LAUF_ID = ?")) {
                ps.setLong(1, lauf);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); beben = rs.getInt(1); mess = rs.getInt(2); maxM = rs.getDouble(3); kap = rs.getInt(4); abbr = rs.getString(5); db = rs.getInt(6); }
            }
            int nah;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM KIL_BEBEN WHERE LAUF_ID = ? AND "
                    + "MDSYS.SDO_WITHIN_DISTANCE(GEOM, KIL_API.punkt(-155.2834, 19.4072), 'distance=1000 unit=M') = 'TRUE'")) {
                ps.setLong(1, lauf);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); nah = rs.getInt(1); }
            }
            boolean gut = woGut && beben == 2 && mess == 2 && Math.abs(maxM - 5.3) < 1e-9 && kap == 3 && "J".equals(abbr) && nah == 1;
            out.zeile("   wo_liegt(19,4072; −155,2834): " + wo);
            out.zeile("   Funktionsprobe (" + v + "): wo_liegt, Lauf mit Beben und Messungen, Ende mit Zählung, Beben im Umkreis "
                    + (gut ? "ok" : "FEHLER: wo_liegt " + woGut + ", Beben " + beben + ", Messungen " + mess + ", größtes " + maxM
                    + ", Kapitel " + kap + ", abgebrochen " + abbr + ", im Umkreis " + nah));
            if (db == 0) out.zeile("   Hinweis: Drehbuch 2018 nicht in KIL_DREHBUCH; die Grunddaten (06) fehlen");
            out.zeile("   Alles zurückgenommen.");
            return gut ? 0 : 1;
        } catch (SQLException e) {
            out.zeile("   Funktionsprobe Wissen: FEHLER " + Dienst.erste(e.getMessage()));
            return 1;
        } finally {
            try { c.rollback(); c.setAutoCommit(auto); } catch (SQLException e) { /* ignorieren */ }
        }
    }

    /** Auf der Konsole: java -cp "build/classes;lib/*" com.dan.kilauea.db.Einrichter (Passwort aus db.properties, Umgebung oder Eingabe). */
    public static void main(String[] args) throws Exception {
        Db.Konfig k = Db.konfig();
        if (!k.hatPasswort()) {
            java.io.Console con = System.console();
            if (con == null) { System.err.println("Kein Passwort: in db/db.properties eintragen oder KILAUEA_DB_PASSWORD setzen."); System.exit(2); }
            k = k.mitPasswort(con.readPassword("Passwort für " + k.user + ": "));
        }
        try (Connection c = Db.oeffnen(k)) {
            Ergebnis e = lauf(c, Db.ordner(), System.out::println);
            System.exit(e.ok ? 0 : 1);
        }
    }
}
