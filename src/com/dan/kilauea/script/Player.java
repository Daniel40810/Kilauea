package com.dan.kilauea.script;

import com.dan.kilauea.lava.LavaField;
import com.dan.kilauea.magma.MagmaSystem;
import com.dan.kilauea.terrain.Deformation;
import com.dan.kilauea.terrain.Geo;
import com.dan.kilauea.terrain.Ground;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spielt ein {@link Script} ab (Phase 12). Vor dem Start sichert er den Zustand der Szene (Speicher, Lavasee,
 * Gelände, Uhr, Tempo, Kamera über den {@link Host}); am Ende, nach „Beenden“ oder beim nächsten Drehbuch
 * kehrt er genau dorthin zurück: kein Lavafeld, keine Schlote, Gelände von heute, Speicher wie vorher.
 * <p>
 * Die Geschichte läuft in Simulationszeit. Rechnet die Lava im Kapitel mit (Tempo bis {@code lavaCap}),
 * folgt die Geschichte der Lava: kommt deren Faden nicht nach, läuft auch die Geschichte langsamer, damit
 * die Stichworte zur Lava passen. In schnellen Kapiteln (Tage je Sekunde) läuft nur das Magma mit; die
 * Lava kühlt dann gemächlich weiter.
 * <p>
 * Aufrufe aus der Oberfläche ({@link #start}, {@link #pause}, {@link #skip}, {@link #stop}) werden gesammelt und
 * im Bildlauf ausgeführt ({@link #update}); ohne Fenster ruft man {@link #run} bzw. {@link #advance} direkt.
 */
public final class Player {
    /** Was die App beim Start sichern und am Ende zurückholen muss (Uhr, Tempo, Kamera, Schnitt). */
    public interface Host extends Stage.Effects {
        default Object save() { return null; }
        default void restore(Object saved) { }
        /** Das Magma wird vom Spieler gerechnet; die App soll es solange nicht selbst weiterrechnen. */
        default void magmaOwnedByScript(boolean owned) { }
    }

    public enum State { IDLE, PLAYING, PAUSED, FINISHED }

    /**
     * Wer mitschreiben will (Phase 16b: Läufe in der Datenbank). Die Aufrufe kommen aus dem Bildlauf (oder dem Faden des
     * Werkzeugs) und müssen kurz sein.
     */
    public interface Listener {
        /** Ein Drehbuch beginnt; das erste Kapitel ist eingetreten. */
        void started(Script s);
        /** Ein Kapitel beginnt (ab 1). */
        void chapter(int nr);
        /** Das letzte Kapitel ist zu Ende (die Nachlaufzeit beginnt). */
        void finished();
        /** Zurück in den Ausgangszustand, aborted: vor dem Ende abgebrochen. Kommt vor dem Zurücksetzen von Messnetz und Magma. */
        void ended(boolean aborted);
    }

    /** Mitschreiber (oder null). */
    public volatile Listener listener;

    /** Was die Infotafel zeigt. */
    public record Caption(String script, int chapter, int chapters, String date, String title, String text, double progress,
                          String lapse, boolean waiting, State state, double outro) { }

    private final Stage stage;
    private Host host;
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private volatile State state = State.IDLE;
    private volatile Script script;
    private int index;
    private double t, lastFieldTime, story;
    private int cueNext;
    private volatile Caption caption;
    /** Nach dem letzten Kapitel so lange stehen bleiben (s Wanduhr), dann zurück. */
    public volatile double outroSeconds = 12;
    private double outroLeft;
    /** Die Kamera folgt den Blicken der Kapitel. */
    public volatile boolean cameraFollows = true;
    /** Ohne eigenen Faden der Lava (Werkzeug, Prüfung): die Lava wird hier gerechnet. */
    public boolean headless;

    // gesichert beim Start
    private MagmaSystem.Snapshot magmaSnap;
    private boolean lakeShow;
    private double lakeDrop, lavaSpeed;
    private Object hostSnap;

    public Player(Stage stage, Host host) {
        this.stage = stage;
        this.host = host;
        stage.fx = host;
    }

    public Stage stage() { return stage; }
    public State state() { return state; }
    public Script script() { return script; }
    public Caption currentCaption() { return caption; }
    public int chapterIndex() { return index; }
    /** Simulationszeit seit dem Start (s). */
    public double storyTime() { return story; }
    /** Zeit im laufenden Kapitel (s). */
    public double chapterTime() { return t; }
    /** Festhalten (Standbilder): die Geschichte läuft nicht weiter, Befehle und Tafel schon. */
    public volatile boolean hold;

    // ------------------------------------------------------------------ Bedienung (aus jedem Faden)

    public void start(Script s) { commands.add(() -> doStart(s)); }
    public void pause() { commands.add(() -> { if (state == State.PLAYING) state = State.PAUSED; else if (state == State.PAUSED) state = State.PLAYING; refreshCaption(); }); }
    public void skip() { commands.add(() -> { if (state == State.PLAYING || state == State.PAUSED) nextChapter(true); }); }
    public void stop() { commands.add(this::returnToStart); }

    // ------------------------------------------------------------------ Ablauf

    /** Im Bildlauf: Befehle ausführen, die Geschichte um wallDt Sekunden Wanduhr weiterführen. */
    public void update(double wallDt) {
        Runnable r;
        while ((r = commands.poll()) != null) r.run();
        if (state == State.FINISHED) {
            outroLeft -= wallDt;
            refreshCaption();
            if (outroLeft <= 0) returnToStart();
            return;
        }
        if (state != State.PLAYING || hold) { if (state != State.IDLE) refreshCaption(); return; }
        Script.Chapter c = script.chapters.get(index);
        stage.lava.speed = Math.min(c.speed, c.lavaCap);
        double d;
        double ft = stage.lava.fieldTime();
        if (syncsWithLava(c) && !headless) {
            d = Math.max(0, ft - lastFieldTime);
            // ein neues Feld beginnt bei 0: dann wie die Wanduhr
            if (ft < lastFieldTime) d = wallDt * c.speed;
        } else d = wallDt * c.speed;
        lastFieldTime = ft;
        advance(d);
    }

    /** Ob die Geschichte im Kapitel der Lava folgt: Lava ist unterwegs und das Tempo rechnet sie mit. */
    private boolean syncsWithLava(Script.Chapter c) {
        if (c.speed > c.lavaCap) return false;
        LavaField f = stage.lava.field();
        return f != null && f.awakeBlocks() > 0;
    }

    /**
     * Die Geschichte um d Simulationssekunden weiterführen (Stichworte, Magma, Kopplung, Kapitelwechsel). Ohne
     * Fenster ({@link #headless}) rechnet sie auch die Lava, in Stücken bis zum nächsten Stichwort.
     */
    public void advance(double d) {
        while (d > 1e-9 && state == State.PLAYING) {
            Script.Chapter c = script.chapters.get(index);
            double left = c.duration - t;
            double nextCue = cueNext < c.cues.size() ? c.cues.get(cueNext).at() - t : Double.MAX_VALUE;
            double h = Math.min(d, Math.min(left, Math.max(0, nextCue)));
            if (headless) h = Math.min(h, c.speed > c.lavaCap ? 3600 : 120);
            if (h > 0) {
                if (headless) {
                    boolean sync = syncsWithLava(c);
                    stage.lava.stepNow(sync ? h : h * Math.min(c.speed, c.lavaCap) / c.speed);
                }
                synchronized (stage.magma) { stage.magma.step(h); }
                stage.couple(h);
                t += h;
                story += h;
                d -= h;
            }
            fireCues(c, false);
            if (t >= c.duration - 1e-9 || (c.until != null && c.until.test(stage))) {
                nextChapter(false);
                // in einem neuen Kapitel gilt dessen Tempo: der Rest von d war nach dem alten bemessen
                break;
            }
        }
        refreshCaption();
    }

    /** Ohne Fenster: ein ganzes Drehbuch durchlaufen lassen (gibt die Zahl der Schritte zurück). */
    public int run(Script s, double chunk) {
        headless = true;
        doStart(s);
        int n = 0;
        while (state == State.PLAYING && n < 1_000_000) {
            Script.Chapter c = script.chapters.get(index);
            advance(Math.min(chunk * c.speed, Math.max(1, c.duration - t)));
            n++;
        }
        return n;
    }

    private void fireCues(Script.Chapter c, boolean all) {
        while (cueNext < c.cues.size() && (all || c.cues.get(cueNext).at() <= t + 1e-9)) {
            c.cues.get(cueNext).action().accept(stage);
            cueNext++;
        }
    }

    private void doStart(Script s) {
        if (state != State.IDLE) returnToStart();
        // sichern
        synchronized (stage.magma) { magmaSnap = stage.magma.snapshot(); }
        lakeShow = stage.lava.showLake;
        lakeDrop = stage.lava.lake().drop;
        lavaSpeed = stage.lava.speed;
        hostSnap = host.save();
        host.magmaOwnedByScript(true);
        script = s;
        index = 0;
        story = 0;
        state = State.PLAYING;
        stage.lava.clearAll();
        stage.summitFeed = 0;
        stage.passiveSO2 = Double.NaN;
        stage.collapseStep = 0;
        host.clearParticles();
        s.setup.accept(stage);
        enterChapter();
        lastFieldTime = stage.lava.fieldTime();
        refreshCaption();
        Listener l = listener;
        if (l != null) l.started(s);
    }

    private void enterChapter() {
        Script.Chapter c = script.chapters.get(index);
        t = 0;
        cueNext = 0;
        if (!Double.isNaN(c.hour)) host.time(c.sky != null ? Math.min(365, c.sky.getDayOfYear()) : 0, c.hour);
        if (c.view != null && cameraFollows) {
            double x = Geo.x(c.view[0], c.view[1]), z = Geo.z(c.view[0]);
            host.view(x, Math.max(0, Ground.height(x, z)) + c.view[2], z, c.view[3], c.view[4], c.view[5]);
        }
        stage.lava.speed = Math.min(c.speed, c.lavaCap);
        fireCues(c, false);
    }

    private void nextChapter(boolean skipping) {
        Script.Chapter c = script.chapters.get(index);
        // übersprungene Stichworte nachholen, damit der Zustand stimmt (Schlote, Gelände)
        fireCues(c, true);
        if (index + 1 >= script.chapters.size()) {
            state = State.FINISHED;
            outroLeft = outroSeconds;
            refreshCaption();
            Listener l = listener;
            if (l != null) l.finished();
            return;
        }
        index++;
        enterChapter();
        lastFieldTime = stage.lava.fieldTime();
        refreshCaption();
        Listener l = listener;
        if (l != null) l.chapter(index + 1);
    }

    /** Zurück in den Zustand vor dem Drehbuch. */
    public void returnToStart() {
        if (state == State.IDLE) return;
        Listener l = listener;
        if (l != null) l.ended(state != State.FINISHED);
        stage.closeAll();
        stage.summitFeed = 0;
        stage.passiveSO2 = Double.NaN;
        stage.collapseStep = 0;
        stage.endDike();
        stage.lava.clearAll();
        stage.lava.lake().drop = lakeDrop;
        stage.lava.showLake = lakeShow;
        Deformation.set(false, 1);
        stage.lava.refreshRim();
        synchronized (stage.magma) { stage.magma.restore(magmaSnap); }
        stage.lava.speed = lavaSpeed;
        host.clearParticles();
        host.restore(hostSnap);
        host.magmaOwnedByScript(false);
        state = State.IDLE;
        script = null;
        caption = null;
    }

    private void refreshCaption() {
        Script s = script;
        if (s == null) { caption = null; return; }
        if (state == State.FINISHED) {
            Script.Chapter c = s.chapters.get(s.chapters.size() - 1);
            caption = new Caption(s.title, s.chapters.size(), s.chapters.size(), c.date(c.duration), "Ende des Drehbuchs",
                    c.text, 1, "", false, state, Math.max(0, outroLeft));
            return;
        }
        Script.Chapter c = s.chapters.get(index);
        double before = 0, all = 0;
        for (int i = 0; i < s.chapters.size(); i++) {
            double w = Math.max(1, Math.log10(1 + s.chapters.get(i).duration / Math.max(1, s.chapters.get(i).speed)));
            if (i < index) before += w;
            all += w;
        }
        double w = Math.max(1, Math.log10(1 + c.duration / Math.max(1, c.speed)));
        double prog = (before + w * Math.min(1, t / Math.max(1e-9, c.duration))) / all;
        boolean waiting = syncsWithLava(c) && stage.lava.behind();
        caption = new Caption(s.title, index + 1, s.chapters.size(), c.date(t), c.title, c.text, prog, c.lapse(), waiting, state, 0);
    }

    /** Kurzer Zustand für das Bedienfeld. */
    public String summary() {
        Caption k = caption;
        if (k == null) return "Kein Drehbuch läuft.";
        if (k.state() == State.FINISHED) return String.format(Locale.GERMAN, "%s ist zu Ende. Zurück in den Ausgangszustand in %.0f s.", k.script(), k.outro());
        return String.format(Locale.GERMAN, "%s · Kapitel %d von %d: %s · %s · %s%s%s", k.script(), k.chapter(), k.chapters(), k.title(), k.date(),
                k.lapse(), k.state() == State.PAUSED ? " · angehalten" : "", k.waiting() ? " · die Lava rechnet, die Geschichte wartet" : "");
    }
}
