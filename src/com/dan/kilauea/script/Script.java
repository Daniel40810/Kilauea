package com.dan.kilauea.script;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ein Drehbuch (Phase 12): ein Ausbruch nach einem echten Vorbild in Kapiteln. Jedes Kapitel hat ein Datum,
 * eine Überschrift, einen Text für die Infotafel, eine Dauer in Simulationszeit und einen Zeitraffer, dazu
 * Stichworte (Cues), die zu bestimmten Zeiten im Kapitel etwas an der {@link Stage} tun. Geschrieben wird es
 * mit dem Baukasten {@link #of}.
 */
public final class Script {
    public final String id, title, summary;
    final Consumer<Stage> setup;
    final List<Chapter> chapters;

    private Script(String id, String title, String summary, Consumer<Stage> setup, List<Chapter> chapters) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.setup = setup;
        this.chapters = chapters;
    }

    public List<Chapter> chapters() { return chapters; }

    /** Die Wahl beim freien Ausbruch (Phase 16b, für KIL_LAUF): Riftzone (OST/SUEDWEST), km vom Gipfel, m³/s, Länge der Spalte, Mitte. */
    public record Frei(String riftzone, double km, double rate, double laengeM, double lat, double lon) { }

    /** Beim freien Ausbruch die Wahl, sonst null. */
    public Frei frei;

    /** Simulationszeit aller Kapitel (s), ohne Kapitel, die auf eine Bedingung warten. */
    public double nominalDuration() {
        double s = 0;
        for (Chapter c : chapters) s += c.duration;
        return s;
    }

    @Override public String toString() { return title; }

    /** Ein Kapitel. */
    public static final class Chapter {
        public final String title, text;
        final String date;
        final LocalDate from, to;
        public final double duration;
        /** Simulationssekunden je Sekunde Wanduhr. */
        public final double speed;
        /** Bis zu diesem Tempo rechnet die Lava mit (darüber kühlt sie langsamer, als die Geschichte läuft). */
        public final double lavaCap;
        final double[] view;

        /** Blick des Kapitels: Breite, Länge, Höhe über Boden, Gierwinkel, Neigung, Abstand; oder null. */
        public double[] view() { return view == null ? null : view.clone(); }
        final double hour;
        /** Tag für den Himmel (Sonnenstand), oder null: der Tag der Szene bleibt. */
        final LocalDate sky;
        final List<Cue> cues;
        final Predicate<Stage> until;

        Chapter(String date, LocalDate from, LocalDate to, String title, String text, double duration, double speed, double lavaCap,
                double[] view, double hour, LocalDate sky, List<Cue> cues, Predicate<Stage> until) {
            this.sky = sky;
            this.date = date;
            this.from = from;
            this.to = to;
            this.title = title;
            this.text = text;
            this.duration = duration;
            this.speed = speed;
            this.lavaCap = lavaCap;
            this.view = view;
            this.hour = hour;
            this.cues = cues;
            this.until = until;
        }

        /** Datum für die Tafel nach t Sekunden im Kapitel. */
        public String date(double t) {
            if (from == null) return date;
            long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
            LocalDate d = from.plusDays(Math.round(days * Math.max(0, Math.min(1, t / duration))));
            return d.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN));
        }

        /** Wie schnell die Geschichte läuft, als Text. */
        public String lapse() { return Script.lapse(speed); }

        /** Phase 16b: Datum wie im Drehbuch (Text), Beginn und Ende (oder null). */
        public String dateText() { return date; }
        public LocalDate from() { return from; }
        public LocalDate to() { return to; }
    }

    /** Ein Stichwort: zur Zeit at (s) im Kapitel tun, was action sagt. */
    record Cue(double at, Consumer<Stage> action) { }

    static String lapse(double speed) {
        if (speed <= 1.01) return "Echtzeit";
        if (speed < 3600) return String.format(Locale.GERMAN, "%.0f Minuten je Sekunde", speed / 60);
        if (speed < 86400) return String.format(Locale.GERMAN, "%.0f Stunden je Sekunde", speed / 3600).replace("1 Stunden", "1 Stunde");
        return String.format(Locale.GERMAN, "%.0f Tage je Sekunde", speed / 86400).replace("1 Tage", "1 Tag");
    }

    // ------------------------------------------------------------------ Baukasten

    public static Builder of(String id, String title, String summary) { return new Builder(id, title, summary); }

    public static final class Builder {
        private final String id, title, summary;
        private Consumer<Stage> setup = s -> { };
        private final List<Chapter> chapters = new ArrayList<>();
        private String date, ctitle, text;
        private LocalDate from, to, sky;
        private double duration, speed = 60, lavaCap = 600, hour = Double.NaN;
        private double[] view;
        private List<Cue> cues;
        private Predicate<Stage> until;

        Builder(String id, String title, String summary) {
            this.id = id;
            this.title = title;
            this.summary = summary;
        }

        /** Was vor dem ersten Kapitel gilt (Lavasee, Gelände, Druck). */
        public Builder setup(Consumer<Stage> s) { setup = s; return this; }

        public Builder chapter(String date, String title, String text) {
            flush();
            this.date = date;
            this.ctitle = title;
            this.text = text;
            cues = new ArrayList<>();
            return this;
        }

        /** Datum läuft über das Kapitel von a bis b (ISO, etwa "2018-05-27"). */
        public Builder dates(String a, String b) { from = LocalDate.parse(a); to = LocalDate.parse(b); return this; }

        /** Tag, nach dem Sonne und Mond stehen (ISO); ohne Angabe der Anfang von {@link #dates}. */
        public Builder sky(String d) { sky = LocalDate.parse(d); return this; }

        /** Dauer (Simulationssekunden) und Tempo (Simulationssekunden je Sekunde). */
        public Builder lasts(double seconds, double speed) { this.duration = seconds; this.speed = speed; return this; }

        /** Höchstes Tempo, bei dem die Lava noch mitgerechnet wird (sonst kühlt sie nur langsam weiter). */
        public Builder lavaCap(double c) { this.lavaCap = c; return this; }

        /** Blick: Ziel (Breite, Länge, Höhe über Boden), Blickrichtung (°), Neigung (°), Abstand (m). */
        public Builder view(double lat, double lon, double above, double yaw, double pitch, double dist) {
            view = new double[]{lat, lon, above, yaw, pitch, dist};
            return this;
        }

        /** Blick nach Kompassrichtung (0 = Nord, 90 = Ost) statt nach dem Winkel der Kamera. */
        public Builder look(double lat, double lon, double above, double bearing, double pitch, double dist) {
            return view(lat, lon, above, ((360 - bearing) % 360 + 360) % 360, pitch, dist);
        }

        public Builder hour(double h) { hour = h; return this; }

        public Builder at(double t, Consumer<Stage> a) { cues.add(new Cue(t, a)); return this; }

        /** Das Kapitel endet, sobald die Bedingung gilt (spätestens nach seiner Dauer). */
        public Builder until(Predicate<Stage> p) { until = p; return this; }

        private void flush() {
            if (ctitle == null) return;
            cues.sort((a, b) -> Double.compare(a.at(), b.at()));
            chapters.add(new Chapter(date, from, to, ctitle, text, duration, speed, lavaCap, view, hour, sky != null ? sky : from, List.copyOf(cues), until));
            date = ctitle = text = null;
            from = to = sky = null;
            duration = 0;
            speed = 60;
            lavaCap = 600;
            hour = Double.NaN;
            view = null;
            until = null;
        }

        public Script build() {
            flush();
            return new Script(id, title, summary, setup, List.copyOf(chapters));
        }
    }
}
