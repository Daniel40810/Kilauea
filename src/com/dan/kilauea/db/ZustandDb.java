package com.dan.kilauea.db;

import com.dan.kilauea.terrain.Geo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Zustände in KIL_ZUSTAND (feste Spalten) und KIL_SCHALTER (Name und Wert) schreiben und lesen. */
public final class ZustandDb {
    private ZustandDb() { }

    /** Eine Zeile der Liste der Ansichten. */
    public static final class Eintrag {
        public final long id;
        public final String name;
        public final long ms;
        public final boolean mitZeit;
        Eintrag(long id, String name, long ms, boolean mitZeit) { this.id = id; this.name = name; this.ms = ms; this.mitZeit = mitZeit; }
        @Override public String toString() { return name; }
    }

    private static String spalten() {
        StringBuilder b = new StringBuilder();
        for (Zustand.Spalte s : Zustand.SPALTEN) b.append(", ").append(s.spalte);
        return b.toString();
    }

    /** Fügt den Zustand mit seinen Schaltern in einer Transaktion ein und liefert die ZUSTAND_ID. */
    public static long speichern(Connection c, Zustand z, long sitzungId) throws SQLException {
        if (!z.hatKamera()) throw new SQLException("Zustand ohne Kamera kann nicht gespeichert werden");
        StringBuilder cols = new StringBuilder("ART, NAME, SITZUNG_ID"), ph = new StringBuilder("?, ?, ?");
        for (Zustand.Spalte s : Zustand.SPALTEN) { cols.append(", ").append(s.spalte); ph.append(", ?"); }
        cols.append(", GEOM");
        ph.append(", KIL_API.punkt(?, ?)");
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            long id;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO KIL_ZUSTAND (" + cols + ") VALUES (" + ph + ")", new String[]{"ZUSTAND_ID"})) {
                int i = 1;
                ps.setString(i++, z.art);
                String n = z.name == null || z.name.isBlank() ? z.art : z.name.trim();
                ps.setString(i++, n.length() > 60 ? n.substring(0, 60) : n);
                if (sitzungId > 0) ps.setLong(i++, sitzungId); else ps.setNull(i++, Types.NUMERIC);
                for (Zustand.Spalte s : Zustand.SPALTEN) {
                    String v = z.text(s.name);
                    if (v == null) ps.setNull(i++, s.typ == Zustand.Typ.ZAHL ? Types.NUMERIC : Types.VARCHAR);
                    else if (s.typ == Zustand.Typ.ZAHL) ps.setBigDecimal(i++, new java.math.BigDecimal(v));
                    else ps.setString(i++, v);
                }
                double x = z.zahl("kamera.x", 0), zz = z.zahl("kamera.z", 0);
                ps.setDouble(i++, Geo.lon(x, zz));
                ps.setDouble(i, Geo.lat(zz));
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getLong(1); }
            }
            Map<String, String> sch = z.schalter();
            if (!sch.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO KIL_SCHALTER (ZUSTAND_ID, NAME, WERT) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> e : sch.entrySet()) {
                        ps.setLong(1, id);
                        ps.setString(2, e.getKey());
                        ps.setString(3, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            c.commit();
            return id;
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException x) { /* egal */ }
            throw e;
        } finally {
            try { c.setAutoCommit(auto); } catch (SQLException x) { /* egal */ }
        }
    }

    /** Wie {@link #speichern}, aber ohne commit (für die Funktionsprobe des Einrichters, die alles zurücknimmt). */
    static long speichernOhneCommit(Connection c, Zustand z, long sitzungId) throws SQLException {
        StringBuilder cols = new StringBuilder("ART, NAME, SITZUNG_ID"), ph = new StringBuilder("?, ?, ?");
        for (Zustand.Spalte s : Zustand.SPALTEN) { cols.append(", ").append(s.spalte); ph.append(", ?"); }
        long id;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO KIL_ZUSTAND (" + cols + ", GEOM) VALUES (" + ph + ", KIL_API.punkt(?, ?))", new String[]{"ZUSTAND_ID"})) {
            int i = 1;
            ps.setString(i++, z.art);
            ps.setString(i++, z.name);
            ps.setLong(i++, sitzungId);
            for (Zustand.Spalte s : Zustand.SPALTEN) {
                String v = z.text(s.name);
                if (v == null) ps.setNull(i++, s.typ == Zustand.Typ.ZAHL ? Types.NUMERIC : Types.VARCHAR);
                else if (s.typ == Zustand.Typ.ZAHL) ps.setBigDecimal(i++, new java.math.BigDecimal(v));
                else ps.setString(i++, v);
            }
            double x = z.zahl("kamera.x", 0), zz = z.zahl("kamera.z", 0);
            ps.setDouble(i++, Geo.lon(x, zz));
            ps.setDouble(i, Geo.lat(zz));
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getLong(1); }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO KIL_SCHALTER (ZUSTAND_ID, NAME, WERT) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> e : z.schalter().entrySet()) {
                ps.setLong(1, id); ps.setString(2, e.getKey()); ps.setString(3, e.getValue()); ps.addBatch();
            }
            ps.executeBatch();
        }
        return id;
    }

    public static Zustand laden(Connection c, long id) throws SQLException {
        Zustand z = new Zustand();
        try (PreparedStatement ps = c.prepareStatement("SELECT ART, NAME, GESPEICHERT, SITZUNG_ID" + spalten() + " FROM KIL_ZUSTAND WHERE ZUSTAND_ID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                z.art = rs.getString(1);
                z.name = rs.getString(2);
                Timestamp t = rs.getTimestamp(3);
                z.gespeichertMs = t == null ? 0 : t.getTime();
                z.sitzungId = rs.getLong(4);
                int i = 5;
                for (Zustand.Spalte s : Zustand.SPALTEN) {
                    if (s.typ == Zustand.Typ.ZAHL) {
                        java.math.BigDecimal b = rs.getBigDecimal(i);
                        if (b != null) z.setze(s.name, b.doubleValue());
                    } else {
                        String v = rs.getString(i);
                        if (v != null) z.setze(s.name, v);
                    }
                    i++;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT NAME, WERT FROM KIL_SCHALTER WHERE ZUSTAND_ID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(2);
                    try { z.setze(rs.getString(1), v == null ? "" : v); } catch (IllegalArgumentException e) { /* übergehen */ }
                }
            }
        }
        return z;
    }

    /** ZUSTAND_ID des jüngsten Zustands der Art, oder 0. */
    public static long juengster(Connection c, String art) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(ZUSTAND_ID) FROM KIL_ZUSTAND WHERE ART = ?")) {
            ps.setString(1, art);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    /** Die Ansichten, neueste zuerst (höchstens 100). */
    public static List<Eintrag> ansichten(Connection c) throws SQLException {
        List<Eintrag> l = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT ZUSTAND_ID, NAME, GESPEICHERT, CASE WHEN TAG IS NULL THEN 'N' ELSE 'J' END "
                + "FROM KIL_ZUSTAND WHERE ART = 'ANSICHT' ORDER BY ZUSTAND_ID DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next() && l.size() < 100) {
                Timestamp t = rs.getTimestamp(3);
                l.add(new Eintrag(rs.getLong(1), rs.getString(2), t == null ? 0 : t.getTime(), "J".equals(rs.getString(4))));
            }
        }
        return l;
    }

    public static void umbenennen(Connection c, long id, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE KIL_ZUSTAND SET NAME = ? WHERE ZUSTAND_ID = ? AND ART = 'ANSICHT'")) {
            ps.setString(1, name.length() > 60 ? name.substring(0, 60) : name);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Löscht eine Ansicht (nur Art ANSICHT; die Schalter gehen mit). */
    public static void loeschen(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM KIL_ZUSTAND WHERE ZUSTAND_ID = ? AND ART = 'ANSICHT'")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}
