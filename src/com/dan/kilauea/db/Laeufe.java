package com.dan.kilauea.db;

import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.magma.Quake;
import com.dan.kilauea.monitor.Monitor;
import com.dan.kilauea.script.Player;
import com.dan.kilauea.script.Script;
import com.dan.kilauea.terrain.Geo;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 16b (Frage 25 = A): schreibt jeden Lauf eines Drehbuchs oder freien Ausbruchs nach KIL_LAUF, dazu die Beben aus dem
 * Magmamodell (KIL_BEBEN) und das Messnetz alle 15 Minuten Simulation (KIL_MESSUNG). Gesammelt wird im Speicher (beim Start,
 * jede Sekunde auf dem DB-Faden und beim Ende, bevor das Messnetz zurückgesetzt wird); geschrieben gebündelt auf dem DB-Faden.
 * Ohne Datenbank oder ohne die Tabellen aus 16b tut die Klasse nichts. Nichts davon läuft im Bildlauf außer dem Sammeln beim
 * Start und Ende, das nur im Speicher liest.
 */
public final class Laeufe implements Player.Listener {
    /** Takt des Messnetzes in KIL_MESSUNG (s Simulation). */
    public static final double TAKT = 900;
    /** Höchstens so viele Beben je Lauf (2018 sind es rund 7 500). */
    public static final int MAX_BEBEN = 50_000;
    /** Beben und Messwerte bleiben für so viele Läufe. */
    public static final int BEHALTEN = 10;

    /** Ein Beben, wie es in KIL_BEBEN geht. */
    public record Beben(double stunde, double staerke, double tiefeKm, String quelle, double lat, double lon) { }
    /** Eine Zeile des Messnetzes. */
    public record Messung(double stunde, double uwd, double sdh, double gpsCm, double hebungCm, double so2, double rate, double rsam) { }

    private final MagmaSystem magma;
    private final Monitor monitor;

    // Zustand des laufenden Laufs (unter this)
    private boolean aktiv;
    private int nummer;                 // zählt die Läufe dieser Sitzung (für zu spät kommende Aufgaben)
    private String drehbuch, titel;
    private Script.Frei frei;
    private double t0, lastQ, naechste;
    private int skipQ, kapitel, gezaehlt;
    private final List<Beben> beben = new ArrayList<>();
    private final List<Messung> messungen = new ArrayList<>();
    private double stunden;
    // nur auf dem DB-Faden
    private long laufId;
    private int laufNummer;
    private int geschriebenKapitel;

    public Laeufe(MagmaSystem magma, Monitor monitor) {
        this.magma = magma;
        this.monitor = monitor;
    }

    /** Anmelden beim Spieler und Takt auf dem DB-Faden starten. */
    public static Laeufe anmelden(Player player, MagmaSystem magma, Monitor monitor) {
        Laeufe l = new Laeufe(magma, monitor);
        player.listener = l;
        Dienst.alle(l::takt, 1000);
        Dienst.vorDemEnde(l::beimBeenden);
        return l;
    }

    // ------------------------------------------------------------ Spieler (Bildlauf)

    @Override public synchronized void started(Script s) {
        aktiv = true;
        nummer++;
        drehbuch = s.id;
        titel = s.title;
        frei = s.frei;
        synchronized (magma) { t0 = magma.time(); }
        lastQ = t0;
        skipQ = 0;
        naechste = 0;
        kapitel = 1;
        gezaehlt = 0;
        beben.clear();
        messungen.clear();
        stunden = 0;
        sammeln();
    }

    @Override public synchronized void chapter(int nr) {
        if (aktiv) kapitel = Math.max(kapitel, nr);
    }

    @Override public synchronized void finished() {
        if (aktiv) sammeln();
    }

    @Override public synchronized void ended(boolean aborted) {
        if (!aktiv) return;
        sammeln();
        aktiv = false;
        Paket p = paket(true, aborted);
        if (Dienst.bereitMitWissen()) Dienst.spaeter(() -> schreiben(p));
    }

    /** Was auf einmal geschrieben wird: alles Gesammelte eines Laufs bis jetzt (unter this bilden). */
    private record Paket(int nr, String drehbuch, String titel, Script.Frei frei, int kapitel, double stunden,
                         List<Beben> beben, List<Messung> messungen, boolean ende, boolean abgebrochen) { }

    private Paket paket(boolean ende, boolean abgebrochen) {
        Paket p = new Paket(nummer, drehbuch, titel, frei, kapitel, stunden, new ArrayList<>(beben), new ArrayList<>(messungen), ende, abgebrochen);
        beben.clear();
        messungen.clear();
        return p;
    }

    // ------------------------------------------------------------ Sammeln (im Speicher)

    /** Neue Beben und fällige Messzeilen seit dem letzten Mal; unter this. */
    private void sammeln() {
        double now;
        List<Quake> qs;
        synchronized (magma) {
            now = magma.time();
            qs = magma.quakesSince(lastQ);
        }
        stunden = Math.max(0, (now - t0) / 3600);
        int i = 0;
        while (i < qs.size() && i < skipQ && qs.get(i).t() == lastQ) i++;
        for (; i < qs.size(); i++) {
            Quake q = qs.get(i);
            if (gezaehlt >= MAX_BEBEN) break;
            double lat = Geo.lat(q.z()), lon = Geo.lon(q.x(), q.z());
            beben.add(new Beben((q.t() - t0) / 3600, q.mag(), -q.y() / 1000, quelle(q.kind()), lat, lon));
            gezaehlt++;
        }
        if (!qs.isEmpty()) {
            double last = qs.get(qs.size() - 1).t();
            int same = 0;
            for (int k = qs.size() - 1; k >= 0 && qs.get(k).t() == last; k--) same++;
            skipQ = same;   // so viele Beben zur Zeit last sind schon übernommen
            lastQ = last;
        }
        while (t0 + naechste <= now + 1e-6) {
            double t = t0 + naechste;
            messungen.add(new Messung(naechste / 3600, monitor.valueAt(Monitor.CH_UWD, t), monitor.valueAt(Monitor.CH_SDH, t),
                    monitor.valueAt(Monitor.CH_BASE, t), monitor.valueAt(Monitor.CH_UPLIFT, t), monitor.valueAt(Monitor.CH_SO2, t),
                    monitor.valueAt(Monitor.CH_RATE, t), monitor.valueAt(Monitor.CH_SEIS, t) * 1000));
            naechste += TAKT;
        }
    }

    /** Art eines Bebens wie in KIL_BEBEN_QUELLE_CK. */
    public static String quelle(Quake.Kind k) {
        switch (k) {
            case DIKE: return "GANG";
            case FLANK: return "FLANKE";
            case COLLAPSE: return "EINSTURZ";
            default: return "GIPFEL";
        }
    }

    // ------------------------------------------------------------ DB-Faden

    /** Jede Sekunde: sammeln und schreiben, solange ein Lauf aktiv ist. */
    private void takt() {
        Paket p;
        synchronized (this) {
            if (!aktiv) return;
            sammeln();
            boolean db = Dienst.bereitMitWissen();
            p = paket(false, false);
            if (!db) return;     // ohne Datenbank nichts aufheben
        }
        schreiben(p);
    }

    /** Beim Beenden der App mit laufendem Drehbuch: alles Gesammelte schreiben (der Lauf bleibt ohne Ende). */
    private void beimBeenden() {
        Paket p;
        synchronized (this) {
            if (!aktiv) return;
            sammeln();
            p = paket(false, false);
        }
        if (Dienst.bereitMitWissen()) schreiben(p);
    }

    /** Ein Paket in die Datenbank (nur auf dem DB-Faden). */
    private void schreiben(Paket p) {
        if (!p.ende() && p.beben().isEmpty() && p.messungen().isEmpty() && laufNummer == p.nr() && p.kapitel() == geschriebenKapitel) return;
        try {
            Connection c = Dienst.verbindung();
            if (laufNummer != p.nr()) {
                laufId = start(c, p.drehbuch(), p.titel(), p.frei());
                laufNummer = p.nr();
                geschriebenKapitel = 0;
            }
            if (laufId <= 0) return;
            schreibeBeben(c, laufId, p.beben());
            schreibeMessungen(c, laufId, p.messungen());
            if (p.ende()) {
                try (CallableStatement cs = c.prepareCall("{call KIL_WISSEN.lauf_ende(?,?,?,?,?)}")) {
                    cs.setLong(1, laufId);
                    cs.setInt(2, p.kapitel());
                    cs.setString(3, p.abgebrochen() ? "J" : "N");
                    cs.setDouble(4, p.stunden());
                    cs.setInt(5, BEHALTEN);
                    cs.execute();
                }
                Dienst.ereignis("DREHBUCH", p.titel(), (p.abgebrochen() ? "abgebrochen" : "zu Ende")
                        + String.format(java.util.Locale.GERMAN, " in Kapitel %d nach %.1f h Simulation; KIL_LAUF %d", p.kapitel(), p.stunden(), laufId), false);
            } else if (p.kapitel() != geschriebenKapitel) {
                try (CallableStatement cs = c.prepareCall("{call KIL_WISSEN.lauf_stand(?,?,?)}")) {
                    cs.setLong(1, laufId);
                    cs.setInt(2, p.kapitel());
                    cs.setDouble(3, p.stunden());
                    cs.execute();
                }
            }
            geschriebenKapitel = p.kapitel();
        } catch (SQLException e) {
            Dienst.fehler(e);
        }
    }

    /** KIL_WISSEN.lauf_start; gibt die LAUF_ID zurück. */
    static long start(Connection c, String drehbuch, String titel, Script.Frei fr) throws SQLException {
        try (CallableStatement cs = c.prepareCall("{? = call KIL_WISSEN.lauf_start(?,?,?,?,?,?,?,?,?)}")) {
            cs.registerOutParameter(1, Types.NUMERIC);
            long sid = Dienst.sitzung();
            if (sid > 0) cs.setLong(2, sid); else cs.setNull(2, Types.NUMERIC);
            cs.setString(3, drehbuch);
            cs.setString(4, titel);
            if (fr != null) {
                cs.setString(5, fr.riftzone());
                cs.setDouble(6, Math.round(fr.km() * 10) / 10.0);
                cs.setDouble(7, fr.rate());
                cs.setDouble(8, fr.laengeM());
                cs.setDouble(9, fr.lon());
                cs.setDouble(10, fr.lat());
            } else {
                cs.setNull(5, Types.VARCHAR);
                for (int i = 6; i <= 10; i++) cs.setNull(i, Types.NUMERIC);
            }
            cs.execute();
            return cs.getLong(1);
        }
    }

    static void schreibeBeben(Connection c, long lauf, List<Beben> b) throws SQLException {
        if (b.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO KIL_BEBEN (LAUF_ID, SIM_STUNDE, STAERKE, TIEFE_KM, QUELLE, GEOM) "
                + "VALUES (?, ?, ?, ?, ?, KIL_API.punkt(?, ?))")) {
            int n = 0;
            for (Beben q : b) {
                ps.setLong(1, lauf);
                ps.setDouble(2, Math.round(q.stunde() * 1000) / 1000.0);
                ps.setDouble(3, Math.max(-2, Math.min(9, Math.round(q.staerke() * 10) / 10.0)));
                ps.setDouble(4, Math.max(-5, Math.min(100, Math.round(q.tiefeKm() * 100) / 100.0)));
                ps.setString(5, q.quelle());
                ps.setDouble(6, q.lon());
                ps.setDouble(7, q.lat());
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    static void schreibeMessungen(Connection c, long lauf, List<Messung> m) throws SQLException {
        if (m.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO KIL_MESSUNG (LAUF_ID, SIM_STUNDE, UWD, SDH, GPS_CM, HEBUNG_CM, SO2, RATE, RSAM) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Messung x : m) {
                ps.setLong(1, lauf);
                ps.setDouble(2, Math.round(x.stunde() * 1000) / 1000.0);
                zahl(ps, 3, x.uwd(), 2, 9_999_999);
                zahl(ps, 4, x.sdh(), 2, 9_999_999);
                zahl(ps, 5, x.gpsCm(), 2, 9_999_999);
                zahl(ps, 6, x.hebungCm(), 2, 9_999_999);
                zahl(ps, 7, Math.max(0, x.so2()), 0, 999_999_999);
                zahl(ps, 8, Math.max(0, x.rate()), 2, 9_999_999);
                zahl(ps, 9, Math.max(0, x.rsam()), 1, 999_999_999);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void zahl(PreparedStatement ps, int i, double v, int stellen, double grenze) throws SQLException {
        if (Double.isNaN(v) || Double.isInfinite(v)) { ps.setNull(i, Types.NUMERIC); return; }
        double f = Math.pow(10, stellen);
        ps.setDouble(i, Math.max(-grenze, Math.min(grenze, Math.round(v * f) / f)));
    }

    // ------------------------------------------------------------ für Prüfung und Werkzeuge

    /** Was gerade gesammelt und noch nicht geschrieben ist (Kopie). */
    public synchronized List<Beben> wartendeBeben() { return new ArrayList<>(beben); }
    public synchronized List<Messung> wartendeMessungen() { return new ArrayList<>(messungen); }
    /** Sammeln wie im Takt, ohne zu schreiben (Prüfung). */
    public synchronized void sammelnJetzt() { if (aktiv) sammeln(); }
    public synchronized int kapitel() { return kapitel; }
    public synchronized int gezaehlt() { return gezaehlt; }
    public synchronized boolean aktiv() { return aktiv; }
    /** Gesammeltes verwerfen (Prüfung ohne Datenbank). */
    public synchronized void leeren() { beben.clear(); messungen.clear(); }
}
