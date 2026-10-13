package com.dan.kilauea.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.kilauea.atmos.SunClock;
import com.dan.kilauea.render.Quality;
import com.dan.kilauea.render.Renderer;
import com.dan.kilauea.render.SceneView;
import com.dan.kilauea.terrain.Place;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.function.Consumer;

/** Bedienfeld rechts: Ansicht, Zeit, Luft und Meer, Bild, Daten. Wächst mit jeder Phase. */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    public static final Color BG = new Color(15, 17, 20), INK = new Color(230, 228, 224), MUTED = new Color(150, 154, 160),
            ACCENT = new Color(240, 140, 64);

    private final SceneView view;
    private final Renderer r;
    private final JLabel dayLbl = label(""), timeLbl = label(""), hazeLbl = label(""), windLbl = label(""), windDirLbl = label(""), weatherLbl = label(""), swellLbl = label(""), periodLbl = label(""), seaLbl = label(""),
            evLbl = label(""), placeLbl = label(" "), sourceLbl = label(" "), perfLbl = label(" "), magmaLbl = label(" "),
            lavaLbl = label(" "), lakeLbl = label(" "), entryLbl = label(" "), scriptLbl = label(" "), monitorLbl = label(" "),
            lifeLbl = label(" "), soundLbl = label(" ");
    private final FSlider day = new FSlider(1, 365, 280), time = new FSlider(0, 24 * 60 - 1, 17 * 60 + 15),
            haze = new FSlider(0, 100, 25), wind = new FSlider(0, 160, 75), windDir = new FSlider(0, 359, 70), swell = new FSlider(0, 40, 10), period = new FSlider(80, 200, 140), ev = new FSlider(-20, 20, 0);
    private boolean fromScene;
    // Phase 16
    private final FComboBox viewBox = new FComboBox(new Object[0]);
    private FCheckBox viewWithTime;
    private final JLabel viewLbl = label(" "), dbLbl = label(" "), logLbl = label(" ");
    /** Alle Schalter und Regler, die zum Zustand gehören, mit Namen (Frage 21 = A). */
    private final java.util.Map<String, Bindung> bindungen = new java.util.LinkedHashMap<>();
    /** Die Blöcke zum Auf- und Zuklappen (Frage 28 = A). */
    private final java.util.Map<String, Block> bloecke = new java.util.LinkedHashMap<>();
    private JPanel target = this;
    private boolean grafikGemeldet;

    private interface Bindung { String get(); void set(String v); }

    public ControlPanel(SceneView view) {
        this.view = view;
        this.r = view.renderer();
        setBackground(BG);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        head("ANSICHT", "ansicht", true);
        put(row(new OverviewMap(view)));
        note("Karte: Klick fliegt hin. Orange das Blickfeld, weiß der Drehpunkt.");
        FButton home = new FButton("Ganze Insel (Pos1)");
        home.addActionListener(e -> { view.goOverview(); view.requestFocusInWindow(); });
        put(row(home));
        Object[] places = Place.values();
        FComboBox placeBox = new FComboBox(places);
        placeBox.setSelectedItem(Place.KALUAPELE);
        put(row(placeBox));
        FButton go = new FButton("Hinfliegen");
        go.addActionListener(e -> { view.goPlace((Place) placeBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(row(go));
        put(row(placeLbl));
        // Phase 16: eigene Ansichten (Taste V), aus der Datenbank oder der Datei
        JLabel viewHead = label("Eigene Ansichten");
        viewHead.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        put(row(viewHead));
        put(row(viewBox));
        FButton viewGo = new FButton("Ansicht hinfliegen");
        viewGo.addActionListener(e -> { ansichtHinfliegen(); view.requestFocusInWindow(); });
        put(row(viewGo));
        FButton viewSave = new FButton("Ansicht merken (V)");
        viewSave.addActionListener(e -> ansichtMerken());
        put(row(viewSave));
        FCheckBox viewTime = flag("ansicht.mit_zeit", "Mit Datum und Uhrzeit merken", false, v -> { });
        viewWithTime = viewTime;
        put(row(viewTime));
        JPanel viewEdit = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        viewEdit.setOpaque(false);
        FButton viewRename = new FButton("Umbenennen");
        viewRename.addActionListener(e -> ansichtUmbenennen());
        FButton viewDelete = new FButton("Löschen");
        viewDelete.addActionListener(e -> ansichtLoeschen());
        viewEdit.add(viewRename);
        viewEdit.add(viewDelete);
        put(row(viewEdit));
        viewLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        viewLbl.setForeground(MUTED);
        put(row(viewLbl));
        view.setKeyAction(java.awt.event.KeyEvent.VK_V, () -> javax.swing.SwingUtilities.invokeLater(this::ansichtMerken));
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(this::tastenZeigen));
        // Beschreibung folgt der Auswahl (Statusbild Phase 4: sie blieb beim alten Ort stehen)
        placeBox.addActionListener(e -> {
            Object o = placeBox.getSelectedItem();
            if (o instanceof Place p) placeLbl.setText("<html><body style='width:205px'>" + p.note + "</body></html>");
        });
        placeLbl.setText("<html><body style='width:205px'>" + Place.KALUAPELE.note + "</body></html>");
        view.setPlaceListener(p -> {
            placeBox.setSelectedItem(p);
            placeLbl.setText("<html><body style='width:205px'>" + p.note + "</body></html>");
        });
        Object[] spots = com.dan.kilauea.terrain.SummitPaths.WALK_SPOTS.toArray();
        FComboBox spotBox = new FComboBox(spots);
        put(row(spotBox));
        FButton walk = new FButton("Zu Fuß hier beginnen");
        walk.addActionListener(e -> { view.goWalk((com.dan.kilauea.terrain.SummitPaths.Spot) spotBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(row(walk));
        FCheckBox walking = new FCheckBox("Gehen (G)");
        walking.addActionListener(e -> { view.toggleWalk(); view.requestFocusInWindow(); });
        view.setWalkListener(walking::setSelected);
        put(row(walking));
        FCheckBox orbit = new FCheckBox("Kreisen (R)");
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        view.setOrbitListener(orbit::setSelected);
        put(row(orbit));
        // Phase 13: Rundflüge mit Kinobalken und Tafel
        head("RUNDFLUG", "rundflug", false);
        FComboBox tourBox = new FComboBox(com.dan.kilauea.camera.Tour.all().toArray());
        put(row(tourBox));
        FButton tourGo = new FButton("Rundflug starten");
        tourGo.addActionListener(e -> {
            r.startTour((com.dan.kilauea.camera.Tour) tourBox.getSelectedItem());
            com.dan.kilauea.db.Dienst.ereignis("RUNDFLUG", String.valueOf(tourBox.getSelectedItem()), "gestartet", true);
            view.requestFocusInWindow();
        });
        put(row(tourGo));
        FButton tourStop = new FButton("Rundflug beenden");
        tourStop.addActionListener(e -> { r.tour.stop(); com.dan.kilauea.db.Dienst.ereignis("RUNDFLUG", String.valueOf(tourBox.getSelectedItem()), "beendet", true); view.requestFocusInWindow(); });
        put(row(tourStop));
        put(row(flag("rundflug.balken", "Kinobalken bei Rundflügen", true, v -> r.barsOnTour = v)));
        put(row(flag("bild.balken_immer", "Kinobalken immer", false, v -> r.bars = v)));
        put(row(flag("kamera.freie_sicht", "Freie Sicht (Kamera hebt sich über Hänge)", true, v -> r.camera.keepView = v)));
        put(row(flag("rundflug.morgen", "Rundflug am Morgen (8 Uhr, danach zurück)", true, v -> r.tourMorning = v)));
        note("Rundflüge fliegen von Ort zu Ort und kreisen an jedem langsam; die Tafel erzählt dazu. Maus oder Taste übernimmt die "
                + "Kamera sofort. Alle Wege zwischen Orten fliegt die Kamera jetzt in einem Bogen: auf weiten Strecken steigt sie auf. "
                + "Steht Gelände zwischen Auge und Ziel, hebt sie sich darüber.");
        note("Links ziehen dreht, rechts ziehen verschiebt, das Rad zoomt vom Kraterrand bis 180 km. W A S D gleiten über das Land "
                + "(Umschalt schneller), Pfeiltasten drehen. R lässt die Kamera kreisen. G wechselt zum Gehen (Augen 1,65 m über dem Boden, W A S D gehen, "
                + "Umschalt läuft, ziehen schaut; steile Wände und Kanten halten auf). Tasten 1 bis 9: Halemaʻumaʻu, Caldera, Uēkahuna, Kīlauea Iki, Nāhuku, "
                + "Mauna Ulu, Puʻu ʻŌʻō, Felsbogen Hōlei, Kamokuna. F12 speichert ein Foto. V merkt den Blick als Ansicht, F1 zeigt alle Tasten.");

        gap();
        head("AUSBRÜCHE (DREHBUCH)", "drehbuch", false);
        FComboBox scriptBox = new FComboBox(com.dan.kilauea.script.Scripts.all().toArray());
        put(row(scriptBox));
        JLabel scriptNote = label(" ");
        scriptNote.setFont(new Font("SansSerif", Font.PLAIN, 11));
        scriptNote.setForeground(MUTED);
        Runnable showNote = () -> {
            Object o = scriptBox.getSelectedItem();
            if (o instanceof com.dan.kilauea.script.Script sc)
                scriptNote.setText("<html><body style='width:205px'>" + sc.summary + " (" + sc.chapters().size() + " Kapitel)</body></html>");
        };
        scriptBox.addActionListener(e -> showNote.run());
        showNote.run();
        put(row(scriptNote));
        FButton play = new FButton("Drehbuch abspielen");
        play.addActionListener(e -> {
            r.player.start((com.dan.kilauea.script.Script) scriptBox.getSelectedItem());
            com.dan.kilauea.db.Dienst.ereignis("DREHBUCH", String.valueOf(scriptBox.getSelectedItem()), "abgespielt", false);
            view.requestFocusInWindow();
        });
        put(row(play));
        FButton pause = new FButton("Anhalten / Weiter");
        pause.addActionListener(e -> { r.player.pause(); view.requestFocusInWindow(); });
        put(row(pause));
        FButton next = new FButton("Nächstes Kapitel");
        next.addActionListener(e -> { r.player.skip(); view.requestFocusInWindow(); });
        put(row(next));
        FButton back = new FButton("Beenden (zurück zum Ausgangszustand)");
        back.addActionListener(e -> { r.player.stop(); com.dan.kilauea.db.Dienst.ereignis("DREHBUCH", String.valueOf(scriptBox.getSelectedItem()), "beendet", false); view.requestFocusInWindow(); });
        put(row(back));
        put(row(flag("drehbuch.kamera_folgt", "Kamera folgt dem Drehbuch", true, v -> r.player.cameraFollows = v)));
        put(row(flag("drehbuch.tafel", "Infotafel im Bild", true, v -> r.showCaption = v)));
        scriptLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        scriptLbl.setForeground(MUTED);
        put(row(scriptLbl));

        JLabel freeHead = label("Freier Ausbruch an einer Riftzone");
        freeHead.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        put(row(freeHead));
        FComboBox riftBox = new FComboBox(new Object[]{com.dan.kilauea.magma.Rift.EAST, com.dan.kilauea.magma.Rift.SOUTHWEST});
        riftBox.setSelectedIndex(0);
        put(row(riftBox));
        FSlider dist = new FSlider(3, 52, 25);
        JLabel distLbl = label(" ");
        Runnable distText = () -> {
            var rf = (com.dan.kilauea.magma.Rift) riftBox.getSelectedItem();
            int max = (int) Math.floor(rf.length() / 1000) - 1;
            if (dist.getValue() > max) dist.setValue(max);
            distLbl.setText(String.format(Locale.GERMAN, "%d km vom Gipfel (bis %d km)", dist.getValue(), max));
        };
        riftBox.addActionListener(e -> distText.run());
        dist.addChangeListener(e -> distText.run());
        distText.run();
        put(row(distLbl));
        put(row(dist));
        double[] frates = {5, 10, 20, 50, 100};
        FComboBox frate = new FComboBox(new Object[]{"5 m³/s", "10 m³/s", "20 m³/s", "50 m³/s", "100 m³/s"});
        frate.setSelectedIndex(2);
        put(row(frate));
        double[] flens = {100, 300, 600, 1000};
        FComboBox flen = new FComboBox(new Object[]{"Spalte 100 m", "Spalte 300 m", "Spalte 600 m", "Spalte 1 000 m"});
        flen.setSelectedIndex(1);
        put(row(flen));
        FButton here = new FButton("Ort unter dem Blick wählen");
        here.addActionListener(e -> {
            Object[] nr = com.dan.kilauea.script.Stage.nearestRift(r.camera.targetX(), r.camera.targetZ());
            riftBox.setSelectedItem(nr[0]);
            dist.setValue((int) Math.max(3, Math.round((Double) nr[1] / 1000)));
            distText.run();
            view.requestFocusInWindow();
        });
        put(row(here));
        FButton trigger = new FButton("Gang und Ausbruch auslösen");
        trigger.addActionListener(e -> {
            var rf = (com.dan.kilauea.magma.Rift) riftBox.getSelectedItem();
            r.player.start(com.dan.kilauea.script.Scripts.free(rf, dist.getValue() * 1000.0, frates[Math.max(0, frate.getSelectedIndex())],
                    flens[Math.max(0, flen.getSelectedIndex())], 4));
            com.dan.kilauea.db.Dienst.ereignis("AUSBRUCH", "frei: " + rf + ", " + dist.getValue() + " km",
                    frate.getSelectedItem() + ", " + flen.getSelectedItem(), false);
            view.requestFocusInWindow();
        });
        put(row(trigger));
        note("Fünf Drehbücher nach echten Ausbrüchen [L, USGS HVO]: Datum, Orte, Fontänenhöhen und Ablauf sind belegt, die Zeit ist "
                + "erzählt [G]: Wochen werden zu Stunden Simulation. Läuft Lava, folgt die Geschichte ihr; kommt das Modell nicht nach, "
                + "wartet sie. 2018 baut den Gipfel vor dem Einsturz auf und lässt ihn in Stufen absacken. Der freie Ausbruch schickt einen "
                + "Gang vom Gipfel in die Riftzone und öffnet darüber eine Spalte. Am Ende kehrt alles in den Zustand vorher zurück: keine "
                + "Lava, Gelände von heute, Speicher wie zuvor.");

        // Phase 14: Messnetz, Wärmebild, Wissen
        gap();
        head("MESSNETZ UND WISSEN", "messnetz", false);
        FComboBox monMode = new FComboBox(new Object[]{"Messnetz im Bild: bei Drehbüchern", "Messnetz im Bild: immer", "Messnetz im Bild: aus"});
        monMode.addActionListener(e -> { r.monitorMode = Math.max(0, monMode.getSelectedIndex()); view.requestFocusInWindow(); });
        put(row(monMode));
        bindIndex("messnetz.tafel", monMode);
        Object[] seis = new Object[com.dan.kilauea.monitor.Station.SEISMIC.size() + 1];
        seis[0] = "Seismometer: das nächste zum Blick";
        for (int i = 0; i < seis.length - 1; i++) seis[i + 1] = "Seismometer " + com.dan.kilauea.monitor.Station.SEISMIC.get(i).name();
        FComboBox seisBox = new FComboBox(seis);
        seisBox.addActionListener(e -> { r.monitor.seisChoice = seisBox.getSelectedIndex() - 1; view.requestFocusInWindow(); });
        put(row(seisBox));
        bindIndex("messnetz.seismometer", seisBox);
        FCheckBox thermalBox = new FCheckBox("Wärmebild (T)");
        thermalBox.addActionListener(e -> { r.thermal = thermalBox.isSelected(); view.requestFocusInWindow(); });
        view.setThermalListener(thermalBox::setSelected);
        put(row(thermalBox));
        bind("messnetz.waermebild", () -> r.thermal ? "J" : "N", v -> { r.thermal = "J".equals(v); thermalBox.setSelected(r.thermal); });
        FButton what = new FButton("Was liegt hier? (I)");
        what.addActionListener(e -> { view.showGroundInfo(); view.requestFocusInWindow(); });
        put(row(what));
        put(row(flag("wissen.ortstafel", "Tafel zu Orten beim Hinfliegen", true, v -> r.placeInfo = v)));
        monitorLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        monitorLbl.setForeground(MUTED);
        put(row(monitorLbl));
        note("Das Messnetz zeigt, was die Stationen des Observatoriums über das Modell messen würden: Seismograf (bis 20-facher "
                + "Zeitraffer als Trommel, darüber RSAM mit den Beben), Neigung an UWD und SDH, GPS quer über die Caldera, SO₂-Ausstoß. "
                + "Das Zeitfenster folgt dem Zeitraffer. Jeder Wert trägt seine Marke: M gemessen, L belegt, A abgeleitet, G gestaltet. "
                + "Das Wärmebild färbt jede Stelle nach ihrer Temperatur (Skala −20 … 1 200 °C), das Kreuz in der Mitte misst. "
                + "„Was liegt hier?“ nennt Strom, Jahr, Art und Bewuchs unter dem Blick.");

        // Phase 15: Leben und Klang, jede Gruppe einzeln
        gap();
        head("LEBEN UND KLANG", "leben", false);
        for (com.dan.kilauea.life.Life.Group g : com.dan.kilauea.life.Life.Group.values())
            put(row(flag("leben." + g.name().toLowerCase(java.util.Locale.ROOT), g.label, true, v -> r.life.set(g, v))));
        FButton heli = new FButton("Hubschrauber jetzt (Vorführung)");
        heli.addActionListener(e -> { r.life.demoHelicopter(r.world); view.requestFocusInWindow(); });
        put(row(heli));
        lifeLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lifeLbl.setForeground(MUTED);
        put(row(lifeLbl));
        FCheckBox soundBox = new FCheckBox("Klang (M)");
        soundBox.setSelected(true);
        soundBox.addActionListener(e -> { r.sound.muted(!soundBox.isSelected()); view.requestFocusInWindow(); });
        view.setSoundListener(soundBox::setSelected);
        put(row(soundBox));
        bind("klang.an", () -> soundBox.isSelected() ? "J" : "N", v -> { boolean on = "J".equals(v); soundBox.setSelected(on); r.sound.muted(!on); });
        FSlider vol = new FSlider(0, 100, 80);
        JLabel volLbl = label("Lautstärke 80 %");
        vol.addChangeListener(e -> { r.sound.master(vol.getValue() / 100.0); volLbl.setText("Lautstärke " + vol.getValue() + " %"); });
        put(row(volLbl));
        put(row(vol));
        bindSlider("klang.lautstaerke", vol, 1);
        for (com.dan.kilauea.sound.SoundEngine.Group g : com.dan.kilauea.sound.SoundEngine.Group.values())
            put(row(flag("klang." + g.name().toLowerCase(java.util.Locale.ROOT), "Klang: " + g.label, true, v -> r.sound.set(g, v))));
        soundLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        soundLbl.setForeground(MUTED);
        put(row(soundLbl));
        note("Nēnē grasen am Gipfel und an der Chain of Craters Road; in der Brutzeit (September bis Mai) paarweise, ab Januar "
                + "mit Gösseln. Autos halten, wenn eine Nēnē auf der Straße steht. Rundflüge nach dem Flugplan des Parks von 2023: "
                + "nur Puʻu-ʻŌʻō-Schleife und Küste, mindestens 1 500 ft über Grund, Mo/Di/Do/Fr/Sa 10–14 Uhr, nie über dem Gipfel [L]. "
                + "Wanderer auf Kīlauea Iki Trail, Crater Rim Trail, Devastation Trail und durch Nāhuku; an Sperrzonen kehren sie um. "
                + "Klang ganz synthetisch, ohne Tondateien: Lautstärke nach Entfernung und Luftdämpfung, Schall läuft 343 m/s, "
                + "Doppler bei Hubschraubern und Autos, Beben mit P- und S-Welle. Jede Gruppe schaltet sofort, ohne Neuladen.");

        gap();
        head("ZEIT (HST, UTC−10)", "zeit", true);
        put(row(dayLbl));
        put(row(day));
        put(row(timeLbl));
        put(row(time));
        day.addChangeListener(e -> { if (!fromScene) apply(); });
        time.addChangeListener(e -> { if (!fromScene) apply(); });
        FComboBox lapse = new FComboBox(new Object[]{"Uhr steht", "1 Minute je Sekunde", "10 Minuten je Sekunde", "Ein Tag in 90 Sekunden", "1 Stunde je Sekunde"});
        lapse.addActionListener(e -> r.timeLapse = new double[]{0, 60, 600, 960, 3600}[Math.max(0, lapse.getSelectedIndex())]);
        put(row(lapse));
        bindIndex("zeit.raffer", lapse);
        FButton now = new FButton("Jetzt auf Hawaiʻi");
        now.addActionListener(e -> {
            ZonedDateTime t = ZonedDateTime.now(ZoneId.of("Pacific/Honolulu"));
            int doy = Math.min(365, t.getDayOfYear());
            r.state.clock.set(doy, t.getHour() + t.getMinute() / 60.0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(row(now));
        note("Die Sonne steht für jeden Tag des Jahres 2026 richtig über dem Gipfel (19,41° N, 155,28° W). "
                + "Hawaiʻi kennt keine Sommerzeit.");

        gap();
        head("UNTER DER OBERFLÄCHE", "schnitt", false);
        FComboBox cut = new FComboBox(com.dan.kilauea.render.SectionRenderer.Mode.values());
        cut.addActionListener(e -> {
            r.sectionMode = (com.dan.kilauea.render.SectionRenderer.Mode) cut.getSelectedItem();
            view.requestFocusInWindow();
        });
        put(row(cut));
        bindIndex("schnitt.lage", cut);
        FButton toCut = new FButton("Vor den Schnitt fliegen");
        toCut.addActionListener(e -> {
            if (r.sectionMode == com.dan.kilauea.render.SectionRenderer.Mode.OFF) cut.setSelectedItem(com.dan.kilauea.render.SectionRenderer.Mode.EAST);
            view.goSection();
            view.requestFocusInWindow();
        });
        put(row(toCut));
        FComboBox mlapse = new FComboBox(new Object[]{"Magma steht", "Magma in Echtzeit", "1 Stunde je Sekunde", "1 Tag je Sekunde"});
        mlapse.setSelectedIndex(2);
        mlapse.addActionListener(e -> r.magmaSpeed = new double[]{0, 1, 3600, 86400}[Math.max(0, mlapse.getSelectedIndex())]);
        put(row(mlapse));
        bindIndex("magma.tempo", mlapse);
        put(row(flag("magma.zufluss", "Zufluss verstärken (×20)", false, v -> { synchronized (r.magma) { r.magma.boost(v ? 20 : 1); } })));
        put(row(flag("magma.gipfelschlot", "Gipfelschlot offen (Episoden)", false, v -> { synchronized (r.magma) { r.magma.summitVent = v; } })));
        magmaLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        magmaLbl.setForeground(MUTED);
        put(row(magmaLbl));
        note("Der Schnitt klappt den Berg auf: Lavabänke, darunter ab rund 11 km alte Meereskruste und die Gleitfläche der "
                + "Südflanke (blau gestrichelt). Glühend die Speicher unter Halemaʻumaʻu (1–2 km) und der Südcaldera (3–5 km) [L], "
                + "heller bei höherem Druck; Gänge in der Riftzone; Beben als Ringe. Ein qualitatives Modell, keine Vorhersage: "
                + "Zufluss rund 0,1 km³ im Jahr [L], ab 10 MPa bricht ein Gang aus [A]. Mit „Zufluss verstärken“ und "
                + "„1 Tag je Sekunde“ läuft nach rund zehn Sekunden ein Gang in die Riftzone.");

        gap();
        head("LAVA (MODELL)", "lava", false);
        FComboBox vsite = new FComboBox(com.dan.kilauea.lava.VentSite.values());
        vsite.setSelectedItem(com.dan.kilauea.lava.VentSite.MAUNA_ULU);
        put(row(vsite));
        double[] rates = {1, 4, 10, 20, 50, 100};
        FComboBox vrate = new FComboBox(new Object[]{"1 m³/s", "4 m³/s", "10 m³/s", "20 m³/s", "50 m³/s", "100 m³/s"});
        vrate.setSelectedIndex(2);
        vsite.addActionListener(e -> {
            double def = ((com.dan.kilauea.lava.VentSite) vsite.getSelectedItem()).rate;
            int best = 0;
            for (int k = 0; k < rates.length; k++) if (Math.abs(rates[k] - def) < Math.abs(rates[best] - def)) best = k;
            vrate.setSelectedIndex(best);
        });
        vrate.addActionListener(e -> r.lava.rate(rates[Math.max(0, vrate.getSelectedIndex())]));
        put(row(vrate));
        FComboBox lspeed = new FComboBox(new Object[]{"Lava steht", "Lava in Echtzeit", "1 Minute je Sekunde", "10 Minuten je Sekunde"});
        lspeed.setSelectedIndex(2);
        lspeed.addActionListener(e -> r.lava.speed = new double[]{0, 1, 60, 600}[Math.max(0, lspeed.getSelectedIndex())]);
        put(row(lspeed));
        bindIndex("lava.tempo", lspeed);
        FButton erupt = new FButton("Ausbruch starten");
        erupt.addActionListener(e -> {
            r.lava.erupt((com.dan.kilauea.lava.VentSite) vsite.getSelectedItem(), rates[Math.max(0, vrate.getSelectedIndex())]);
            com.dan.kilauea.db.Dienst.ereignis("AUSBRUCH", String.valueOf(vsite.getSelectedItem()), String.valueOf(vrate.getSelectedItem()), false);
            view.requestFocusInWindow();
        });
        put(row(erupt));
        FButton close = new FButton("Schlot schließen");
        close.addActionListener(e -> { r.lava.stopVent(); view.requestFocusInWindow(); });
        put(row(close));
        FButton flyVent = new FButton("Zum Schlot fliegen");
        flyVent.addActionListener(e -> {
            var s = (com.dan.kilauea.lava.VentSite) vsite.getSelectedItem();
            view.goTo(s.x(), s.z());
            view.requestFocusInWindow();
        });
        put(row(flyVent));
        put(row(new LavaMap(r.lava)));
        FCheckBox diveFlag = flag("kamera.tauchen", "Tauchen erlauben (unter den Meeresspiegel)", false, v -> r.camera.diving = v);
        FButton dive = new FButton("Zu den Kissen tauchen");
        dive.addActionListener(e -> {
            if (r.diveToPillows()) diveFlag.setSelected(true);
            else entryLbl.setText("<html><body style='width:205px'>Noch keine Kissenlava: erst muss Lava ins Meer laufen "
                    + "(zum Beispiel „Kamokuna, Röhrenausgang“ mit „10 Minuten je Sekunde“).</body></html>");
            view.requestFocusInWindow();
        });
        put(row(dive));
        put(row(diveFlag));
        for (JLabel l : new JLabel[]{lavaLbl, lakeLbl, entryLbl}) {
            l.setFont(new Font("SansSerif", Font.PLAIN, 11));
            l.setForeground(MUTED);
            put(row(l));
        }
        put(row(flag("lava.bild", "Lava im Bild", true, r::showLava)));
        put(row(flag("lava.see", "Lavasee zeigen", true, v -> r.lava.showLake = v)));
        note("Meereseintritt (Phase 10): wo Lava ins Meer läuft, kocht das Wasser, Laze steigt auf, Glutbrocken fliegen, das Delta "
                + "wächst und bricht ab; über eine Klippe fällt sie als Feuerwehrschlauch. Unter Wasser knospt Kissenlava, alle 10 bis 30 s "
                + "ein neues Kissen [L Beobachtungen vor Kīlauea; Takt und Formen G]. "
                + "Ein Zellenmodell mit 10-m-Zellen; im Bild glüht die Lava nach Planck: Kruste dunkel, Risse mit der Kerntemperatur, "
                + "Fontänen als Teilchen mit Peles Haar, der Lavasee in seiner Grube, ein Lichtschein auf die Umgebung [L Planck, CIE 1931; "
                + "Rissanteile, Funken und Schlieren G]. Die Karte zeigt die Kerntemperatur "
                + "(hellgelb 1 150 °C bis dunkelrot 1 000 °C), ʻaʻā schraffiert, Lava unter Dach abgedunkelt mit Oberlichtern, "
                + "leergelaufene Röhren blaugrau, erstarrtes Gestein schwarz. Fließgesetz nach Bingham wie MAGFLOW [L], Zähigkeit und "
                + "Fließgrenze nach Temperatur [A], Kruste nach Hon u. a. 1994 [L], ʻaʻā nach Peterson & Tilling 1980 [L, Schwelle A]. "
                + "Darunter der Lavasee: Platten treiben von der Quelle zur Senke [L, 0,1–0,5 m/s], der Spiegel folgt dem Druck im "
                + "Halemaʻumaʻu-Speicher [G]. Keine Vorhersage.");

        gap();
        head("LUFT UND MEER", "luft", false);
        put(row(hazeLbl));
        put(row(haze));
        bindSlider("luft.dunst", haze, 1);
        haze.addChangeListener(e -> applyAir());
        put(row(windLbl));
        put(row(wind));
        bindSlider("luft.wind", wind, 0.1);
        wind.addChangeListener(e -> applyAir());
        put(row(windDirLbl));
        put(row(windDir));
        bindSlider("luft.richtung", windDir, 1);
        windDir.addChangeListener(e -> applyAir());
        put(row(flag("luft.wolken", "Wolken, Regen und Vog", true, v -> r.showWeather(v))));
        weatherLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        weatherLbl.setForeground(MUTED);
        put(row(weatherLbl));
        put(row(swellLbl));
        put(row(swell));
        bindSlider("meer.swell", swell, 0.1);
        swell.addChangeListener(e -> applyAir());
        put(row(periodLbl));
        put(row(period));
        bindSlider("meer.periode", period, 0.1);
        period.addChangeListener(e -> applyAir());
        put(row(flag("meer.brandung", "Brandung und Gischt", true, r::showSurf)));
        seaLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        seaLbl.setForeground(MUTED);
        put(row(seaLbl));
        note("Der Passat weht aus Ostnordost; er bringt den Regen an die Ostflanke und eine kurze See an die Puna-Küste. "
                + "Die Dünung kommt aus Süden. Vor der Küste steilt sie auf und bricht, wo das Wasser flacher als rund das "
                + "1,3-Fache der Wellenhöhe ist; an Klippen bricht sie nicht, sie schlägt an die Wand und wirft Gischt hoch "
                + "[L lineare Wellentheorie, McCowan, Iribarren; Schaum und Gischt G]. "
                + "Wolken (Phase 11): der Passat staut sich an der Ostflanke zwischen rund 650 und 2 100 m; darüber, oberhalb der "
                + "Passatinversion, bleibt es klar, die Gipfel ragen heraus [L Giambelluca u. a., Cao u. a. 2007]. Nachmittags "
                + "wachsen die Wolken, nachts klart das Land auf; Regen fällt, wo das Band dicht ist und der Hang steigt. "
                + "Der Vog (Gasfahne aus dem Halemaʻumaʻu) zieht mit dem Wind, beim Passat nach Südwesten Richtung Kona, "
                + "beim Kona-Wind nach Norden [L USGS; Ausbreitung A, Stärke G].");

        gap();
        head("BILD", "bild", true);
        FComboBox qual = new FComboBox(Quality.values());
        qual.setSelectedItem(Quality.HOCH);
        qual.addActionListener(e -> { r.setQuality((Quality) qual.getSelectedItem()); view.requestFocusInWindow(); });
        put(row(qual));
        bind("bild.qualitaet", () -> ((Quality) qual.getSelectedItem()).name(), v -> {
            for (Quality q : Quality.values()) if (q.name().equals(v)) qual.setSelectedItem(q);
        });
        put(row(evLbl));
        put(row(ev));
        bindSlider("bild.belichtung", ev, 0.1);
        ev.addChangeListener(e -> { r.state.ev = ev.getValue() / 10.0; evLbl.setText(String.format(Locale.GERMAN, "Belichtung %+.1f Blenden", r.state.ev)); });
        put(row(flag("bild.schatten", "Schatten", true, v -> r.shadows = v)));
        put(row(flag("bild.meer", "Meer", true, v -> r.showOcean = v)));
        put(row(flag("bild.felsbogen", "Felsbogen Hōlei vor dem Einsturz (bis 09/2026)", false, v -> r.archIntact = v)));
        put(row(flag("bild.pflanzen", "Bäume und Farne", true, v -> r.showFlora = v)));
        put(row(flag("bild.erdkruemmung", "Erdkrümmung", true, v -> r.curvature = v)));
        put(row(flag("bild.detailstufen", "Detailstufen zeigen (Prüfbild)", false, v -> r.showLod = v)));
        put(row(flag("bild.lavaalter", "Alter der Lava zeigen (Prüfbild)", false, v -> r.showAge = v)));
        put(row(flag("bild.aufloesung_auto", "Auflösung selbst regeln (GPU unter 15 ms)", false, v -> r.autoScale = v)));
        perfLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        perfLbl.setForeground(MUTED);
        put(row(perfLbl));
        FButton photo = new FButton("Foto speichern (F12)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        put(row(photo));
        note("Niedrig rechnet in zwei Dritteln der Auflösung ohne Bloom und Feinrelief; Hoch in voller Auflösung. "
                + "Die Statuszeile zeigt Bilder/s und die Zeit der Grafikkarte je Bild.");

        gap();
        head("DATEN", "daten", true);
        put(row(sourceLbl));
        note("Höhen [M]: SRTM/GLSDEM 3″ (Stand Februar 2000) und GTOPO30 auf 75 m; darauf die Änderungen bis 2018 "
                + "(Gipfeleinbruch, Puʻu ʻŌʻō, Kapoho) [G nach USGS]. Detailkacheln von 4 bis 8 m schärfen Kraterwände, Pali und Meeresklippe [A], "
                + "Grubenkrater und Formen von 2018 nach Lage und Maß [L/G]. Meeresboden [G] aus dem Abstand zur Küste, bis Tiefendaten "
                + "im Projekt liegen. Feinrelief unter 75 m [G]. Lava: 40 datierte Ströme 1801–2024 mit Jahr und Schlot [L], Weg bergab "
                + "gerechnet [A]; Regen aus dem Passat [A]; Bewuchs nach Alter und Regen [A]; Straßenverlauf nach NPS-Karte [L]. "
                + "Marken: M gemessen · L Literatur · A abgeleitet · G gestaltet.");
        // Phase 16: Datenbank, Einrichter, Protokoll
        JLabel dbHead = label("Datenbank");
        dbHead.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        put(row(dbHead));
        put(row(dbLbl));
        JPanel dbButtons = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        dbButtons.setOpaque(false);
        FButton setup = new FButton("Einrichter …");
        setup.addActionListener(e -> { new EinrichterDialog(javax.swing.SwingUtilities.getWindowAncestor(this), false).setVisible(true); ansichtenNeu(); });
        FButton recheck = new FButton("Neu prüfen");
        recheck.addActionListener(e -> com.dan.kilauea.db.Dienst.neuPruefen());
        dbButtons.add(setup);
        dbButtons.add(recheck);
        put(row(dbButtons));
        JLabel logHead = label("Protokoll (die letzten 20)");
        logHead.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        put(row(logHead));
        logLbl.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        logLbl.setForeground(MUTED);
        put(row(logLbl));
        note("Zustand (Kamera, Zeit, Wetter, Bild und alle Schalter) wird alle 60 s und beim Beenden gesichert: mit Datenbank in "
                + "KIL_ZUSTAND und KIL_SCHALTER, sonst in ~/.kilauea. Das Protokoll steht zusätzlich in logs/kilauea_JJJJ-MM-TT.log (14 Tage). "
                + "Die App läuft auch ganz ohne Datenbank.");
        com.dan.kilauea.db.Dienst.beiAenderung(() -> { dbText(); ansichtenNeu(); });
        com.dan.kilauea.db.Dienst.beiProtokoll(this::logText);

        add(Box.createVerticalGlue());
        r.lava.start();
        syncFromScene();
        applyAir();
        new Timer(1000, e -> {
            syncFromScene();
            com.dan.kilauea.db.Dienst.messung(r.fps(), r.gpuMs(), r.cpuMs());
            if (!grafikGemeldet && !r.glInfo().isEmpty()) { grafikGemeldet = true; com.dan.kilauea.db.Dienst.sitzungGrafik(r.glInfo()); }
        }).start();
        dbText();
        logText();
        ansichtenNeu();
    }

    private FCheckBox flag(String key, String text, boolean def, Consumer<Boolean> set) {
        FCheckBox b = new FCheckBox(text);
        b.setSelected(def);
        b.addActionListener(e -> { set.accept(b.isSelected()); view.requestFocusInWindow(); });
        bind(key, () -> b.isSelected() ? "J" : "N", v -> { boolean on = "J".equals(v); b.setSelected(on); set.accept(on); });
        return b;
    }

    private void apply() {
        r.state.clock.set(day.getValue(), time.getValue() / 60.0);
        labels();
    }

    /** Himmelsrichtung, aus der der Wind kommt, als Wort; 70° ist der Passat, um 200° der Kona-Wind. */
    static String compass(int deg) {
        String[] n = {"Nord", "Nordnordost", "Nordost", "Ostnordost", "Ost", "Ostsüdost", "Südost", "Südsüdost",
                "Süd", "Südsüdwest", "Südwest", "Westsüdwest", "West", "Westnordwest", "Nordwest", "Nordnordwest"};
        String s = n[(int) Math.round(deg / 22.5) % 16];
        if (deg >= 40 && deg <= 100) s += ", Passat";
        if (deg >= 160 && deg <= 250) s += ", Kona-Wind";
        return s;
    }

    private void applyAir() {
        r.state.haze = 4e-6 + haze.getValue() / 100.0 * 4.8e-5;
        r.state.windSpeed = wind.getValue() / 10.0;
        r.state.swellHeight = swell.getValue() / 10.0;
        hazeLbl.setText(String.format(Locale.GERMAN, "Dunst %.1f · 10⁻⁵/m (Sicht rund %.0f km)", r.state.haze * 1e5, 3.9 / (r.state.haze * 1.11 + 1.3e-5) / 1000));
        r.state.windDirDeg = windDir.getValue();
        windLbl.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", r.state.windSpeed));
        windDirLbl.setText(String.format(Locale.GERMAN, "aus %d° (%s)", windDir.getValue(), compass(windDir.getValue())));
        r.state.swellPeriod = period.getValue() / 10.0;
        swellLbl.setText(String.format(Locale.GERMAN, "Südswell %.1f m", r.state.swellHeight));
        periodLbl.setText(String.format(Locale.GERMAN, "Periode %.0f s (Wellenlänge im Tiefen %.0f m)", r.state.swellPeriod,
                com.dan.kilauea.sea.Waves.deepLength(r.state.swellPeriod)));
    }

    /** Regler nach der Uhr der Szene stellen (sie läuft im Zeitraffer weiter). */
    private void syncFromScene() {
        fromScene = true;
        day.setValue(r.state.clock.day());
        time.setValue((int) Math.round(r.state.clock.hour() * 60) % (24 * 60));
        fromScene = false;
        labels();
        synchronized (r.magma) { magmaLbl.setText("<html><body style='width:205px'>" + r.magma.summary() + "</body></html>"); }
        lavaLbl.setText("<html><body style='width:205px'>" + r.lava.summary() + "</body></html>");
        scriptLbl.setText("<html><body style='width:205px'>" + r.player.summary() + "</body></html>");
        com.dan.kilauea.monitor.Reading pr = r.thermalProbe();
        monitorLbl.setText("<html><body style='width:205px'>" + r.monitor.summary() + (r.thermal && pr != null ? " · Kreuz " + pr.withTag() : "") + "</body></html>");
        entryLbl.setText("<html><body style='width:205px'>" + r.entryStats() + "</body></html>");
        lifeLbl.setText("<html><body style='width:205px'>" + r.life.summary(r.world) + " · " + r.lifeStats() + "</body></html>");
        soundLbl.setText("<html><body style='width:205px'>" + r.sound.summary() + "</body></html>");
        lakeLbl.setText("<html><body style='width:205px'>" + r.lava.lakeSummary() + "</body></html>");
        seaLbl.setText("<html><body style='width:205px'>" + r.seaStats() + "</body></html>");
        weatherLbl.setText("<html><body style='width:205px'>" + r.weatherStats() + "</body></html>");
        perfLbl.setText("<html><body style='width:205px'>Grafikkarte je Bild: " + r.passTimes() + "<br>Prozessor je Bild: " + r.cpuTimes() + "<br>" + r.floraStats() + "</body></html>");
        String src = r.terrainSource();
        sourceLbl.setText("<html><body style='width:205px'>" + (src.isEmpty() ? "Gelände lädt …" : src) + "</body></html>");
    }

    private void labels() {
        dayLbl.setText("Tag: " + SunClock.dateLabel(r.state.clock.day()));
        timeLbl.setText(String.format(Locale.GERMAN, "Uhrzeit: %s HST · Sonne %.1f°", SunClock.timeLabel(r.state.clock.hour()), r.state.clock.elevationDeg));
    }


    // ------------------------------------------------------------ Phase 16: Zustand

    private void bind(String key, java.util.function.Supplier<String> get, Consumer<String> set) {
        if (!com.dan.kilauea.db.Zustand.gueltigerName(key)) throw new IllegalArgumentException("Schaltername " + key);
        if (bindungen.containsKey(key)) throw new IllegalArgumentException("Schalter doppelt: " + key);
        bindungen.put(key, new Bindung() {
            @Override public String get() { return get.get(); }
            @Override public void set(String v) { set.accept(v); }
        });
    }

    /** Auswahl als Nummer; setSelectedIndex löst den Lauscher aus und stellt die Szene ein. */
    private void bindIndex(String key, FComboBox box) {
        bind(key, () -> Integer.toString(Math.max(0, box.getSelectedIndex())), v -> {
            try {
                int i = (int) Math.round(Double.parseDouble(v));
                if (i >= 0 && i < box.getItemCount() && i != box.getSelectedIndex()) box.setSelectedIndex(i);
            } catch (NumberFormatException e) { /* Vorgabe bleibt */ }
        });
    }

    /** Regler: Wert = Stellung × Maß (etwa 0,1 für m/s); setValue löst den Lauscher aus. */
    private void bindSlider(String key, FSlider sl, double mass) {
        bind(key, () -> com.dan.kilauea.db.Zustand.fmtWert(sl.getValue() * mass), v -> {
            try {
                int k = (int) Math.round(Double.parseDouble(v) / mass);
                sl.setValue(Math.max(sl.getMinimum(), Math.min(sl.getMaximum(), k)));
            } catch (NumberFormatException e) { /* Vorgabe bleibt */ }
        });
    }

    /** Die Namen aller Schalter, die zum Zustand gehören (für die Prüfung). */
    public java.util.Set<String> schalterNamen() { return java.util.Collections.unmodifiableSet(bindungen.keySet()); }

    /** Der ganze Zustand: Kamera, Uhr und alle Schalter. Auf dem Ereignis-Faden aufrufen. */
    public com.dan.kilauea.db.Zustand erfassen() {
        com.dan.kilauea.db.Zustand z = new com.dan.kilauea.db.Zustand();
        kamera(z);
        z.setze("zeit.tag", r.state.clock.day());
        z.setze("zeit.stunde", Math.min(23.999, r.state.clock.hour()));
        for (java.util.Map.Entry<String, Bindung> e : bindungen.entrySet()) {
            try { z.setze(e.getKey(), e.getValue().get()); } catch (RuntimeException ex) { /* übergehen */ }
        }
        return z;
    }

    /** Die Kamera in den Zustand: beim Gehen Standort, Blickrichtung und Neigung, sonst Drehpunkt, Richtung, Neigung, Abstand. */
    private void kamera(com.dan.kilauea.db.Zustand z) {
        com.dan.kilauea.camera.OrbitCamera c = r.camera;
        if (c.walking) {
            z.setze("kamera.modus", "GEHEN");
            z.setze("kamera.x", c.wx).setze("kamera.y", c.wfoot).setze("kamera.z", c.wz);
            z.setze("kamera.gier", -Math.toDegrees(c.wyaw)).setze("kamera.neigung", Math.toDegrees(c.wpitch)).setze("kamera.abstand", 0);
        } else {
            double[] p = c.lookPoint();
            z.setze("kamera.modus", c.autoOrbit ? "KREISEN" : "FLUG");
            z.setze("kamera.x", p[0]).setze("kamera.y", p[1]).setze("kamera.z", p[2]);
            z.setze("kamera.gier", c.yawDeg()).setze("kamera.neigung", c.pitchDeg()).setze("kamera.abstand", c.distance());
        }
    }

    /**
     * Stellt einen Zustand ein: zuerst alle Schalter und Regler (ihre Lauscher stellen die Szene), dann Uhr und Kamera.
     * sofort: Kamera springt (Programmstart), sonst fliegt sie hin (Ansicht). Werte, die fehlen, bleiben, wie sie sind.
     */
    public void anwenden(com.dan.kilauea.db.Zustand z, boolean sofort) {
        if (z == null) return;
        for (java.util.Map.Entry<String, Bindung> e : bindungen.entrySet()) {
            String v = z.text(e.getKey());
            if (v == null) continue;
            try { e.getValue().set(v); } catch (RuntimeException ex) { /* übergehen */ }
        }
        if (z.hatZeit()) {
            r.state.clock.set((int) Math.round(z.zahl("zeit.tag", r.state.clock.day())), z.zahl("zeit.stunde", r.state.clock.hour()));
            syncFromScene();
        }
        if (z.hatKamera()) {
            double x = z.zahl("kamera.x", 0), y = z.zahl("kamera.y", 0), zz = z.zahl("kamera.z", 0);
            double gier = z.zahl("kamera.gier", 0), neig = z.zahl("kamera.neigung", 25), ab = z.zahl("kamera.abstand", 2000);
            String modus = z.text("kamera.modus");
            if ("GEHEN".equals(modus)) {
                view.walkAt(x, zz, gier, neig);
            } else {
                r.camera.autoOrbit = false;
                r.camera.lookAt(x, y, zz, gier, neig, Math.max(com.dan.kilauea.camera.OrbitCamera.MIN_DIST, ab));
                if (sofort) r.camera.snap();
                view.setAutoOrbit("KREISEN".equals(modus));
            }
        }
    }

    // ------------------------------------------------------------ Phase 16: Ansichten

    private void ansichtenNeu() {
        Object sel = viewBox.getSelectedItem();
        java.util.List<com.dan.kilauea.db.Dienst.Ansicht> l = com.dan.kilauea.db.Dienst.ansichten();
        viewBox.removeAllItems();
        for (com.dan.kilauea.db.Dienst.Ansicht a : l) viewBox.addItem(a);
        if (sel != null) for (int i = 0; i < viewBox.getItemCount(); i++) {
            com.dan.kilauea.db.Dienst.Ansicht a = (com.dan.kilauea.db.Dienst.Ansicht) viewBox.getItemAt(i);
            if (sel instanceof com.dan.kilauea.db.Dienst.Ansicht b && a.id == b.id && a.ausDatei == b.ausDatei) viewBox.setSelectedIndex(i);
        }
        if (l.isEmpty()) viewLbl.setText("<html><body style='width:205px'>Noch keine eigene Ansicht. Taste V merkt den Blick.</body></html>");
        else viewLbl.setText("<html><body style='width:205px'>" + l.size() + (l.size() == 1 ? " Ansicht" : " Ansichten")
                + (com.dan.kilauea.db.Dienst.bereit() ? "" : " (ohne Datenbank nur die aus der Datei)") + "</body></html>");
    }

    private com.dan.kilauea.db.Dienst.Ansicht gewaehlt() {
        Object o = viewBox.getSelectedItem();
        return o instanceof com.dan.kilauea.db.Dienst.Ansicht a ? a : null;
    }

    private void ansichtMerken() {
        com.dan.kilauea.db.Zustand z = erfassen().nurKamera(viewWithTime != null && viewWithTime.isSelected());
        Place p = Place.nearest(r.camera.targetX(), r.camera.targetZ(), new double[1]);
        String vorschlag = (r.camera.walking ? "Zu Fuß bei " : "Blick auf ") + p.label;
        String name = com.dan.foptionpane.FOptionPane.showInputDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                "Name der Ansicht (leer: „" + vorschlag + "“)" + (z.hatZeit() ? "\nmit Datum und Uhrzeit" : ""), "Ansicht merken", com.dan.foptionpane.FOptionPane.QUESTION_MESSAGE);
        view.requestFocusInWindow();
        if (name == null) return;
        name = name.trim().isEmpty() ? vorschlag : name.trim();
        viewLbl.setText("<html><body style='width:205px'>" + esc(com.dan.kilauea.db.Dienst.merken(z, name)) + "</body></html>");
        ansichtenNeu();
        if (viewBox.getItemCount() > 0) viewBox.setSelectedIndex(0);
    }

    private void ansichtHinfliegen() {
        com.dan.kilauea.db.Dienst.Ansicht a = gewaehlt();
        if (a == null) return;
        com.dan.kilauea.db.Zustand z = com.dan.kilauea.db.Dienst.laden(a);
        if (z == null) { viewLbl.setText("<html><body style='width:205px'>„" + esc(a.name) + "“ ist nicht lesbar.</body></html>"); return; }
        r.tour.stop();
        anwenden(z, false);
        com.dan.kilauea.db.Dienst.ereignis("ANSICHT", a.name, "hingeflogen", true);
    }

    private void ansichtUmbenennen() {
        com.dan.kilauea.db.Dienst.Ansicht a = gewaehlt();
        if (a == null) return;
        String name = com.dan.foptionpane.FOptionPane.showInputDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                "Neuer Name für „" + a.name + "“", "Ansicht umbenennen", com.dan.foptionpane.FOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        if (!com.dan.kilauea.db.Dienst.umbenennen(a, name.trim())) viewLbl.setText("Umbenennen ging nicht.");
        ansichtenNeu();
    }

    private void ansichtLoeschen() {
        com.dan.kilauea.db.Dienst.Ansicht a = gewaehlt();
        if (a == null) return;
        int ok = com.dan.foptionpane.FOptionPane.showConfirmDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                "Ansicht „" + a.name + "“ löschen?", "Ansicht löschen", com.dan.foptionpane.FOptionPane.YES_NO_OPTION);
        if (ok != com.dan.foptionpane.FOptionPane.YES_OPTION) return;
        if (!com.dan.kilauea.db.Dienst.loeschen(a)) viewLbl.setText("Löschen ging nicht.");
        ansichtenNeu();
    }

    // ------------------------------------------------------------ Phase 16: Daten, Protokoll, Tasten

    private void dbText() {
        com.dan.kilauea.db.Dienst.Lage l = com.dan.kilauea.db.Dienst.lage();
        String m = com.dan.kilauea.db.Dienst.meldung();
        String f = com.dan.kilauea.db.Dienst.fassung();
        dbLbl.setForeground(l == com.dan.kilauea.db.Dienst.Lage.BEREIT ? new Color(150, 210, 160) : l == com.dan.kilauea.db.Dienst.Lage.PRUEFT ? MUTED : new Color(230, 190, 120));
        dbLbl.setText("<html><body style='width:205px'>" + esc(com.dan.kilauea.db.Dienst.kurz())
                + (l == com.dan.kilauea.db.Dienst.Lage.BEREIT ? "<br>" + com.dan.kilauea.db.Dienst.tabellen() + " Tabellen KIL_ · " + esc(f) : "")
                + (m.isEmpty() ? "" : "<br><span style='color:#969aa0'>" + esc(m) + "</span>") + "</body></html>");
    }

    private void logText() {
        StringBuilder b = new StringBuilder("<html><body style='width:205px'>");
        java.util.List<String> l = com.dan.kilauea.db.Dienst.letzte();
        if (l.isEmpty()) b.append("noch leer");
        for (String z : l) b.append(esc(z)).append("<br>");
        logLbl.setText(b.append("</body></html>").toString());
    }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    /** F1: alle Tasten auf einen Blick, in einem eigenen Fenster im F-Stil. */
    private void tastenZeigen() {
        String[][] t = {
                {"Links ziehen", "drehen"}, {"Rechts ziehen", "verschieben"}, {"Rad, Bild auf/ab", "zoomen"},
                {"W A S D", "gleiten oder gehen (Umschalt: schneller)"}, {"Pfeiltasten", "drehen"},
                {"Pos1", "ganze Insel"}, {"1 bis 9", "Orte: Halemaʻumaʻu, Caldera, Uēkahuna, Kīlauea Iki, Nāhuku, Mauna Ulu, Puʻu ʻŌʻō, Hōlei, Kamokuna"},
                {"R", "kreisen"}, {"G", "gehen oder fliegen"}, {"T", "Wärmebild"}, {"I", "Was liegt hier?"}, {"M", "Klang an oder aus"},
                {"V", "Blick als Ansicht merken"}, {"F1", "diese Übersicht"}, {"F12", "Foto"}, {"F5", "Shader neu laden"}};
        StringBuilder b = new StringBuilder("<html><body style='width:400px'><table cellspacing=0 cellpadding=3>");
        for (String[] z : t) b.append("<tr><td style='color:#f08c40'><b>").append(esc(z[0])).append("</b></td><td>").append(esc(z[1])).append("</td></tr>");
        b.append("</table><br><span style='color:#969aa0'>Ein Klick auf einen Blockkopf im Bedienfeld klappt ihn auf oder zu; was offen ist, "
                + "merkt sich die App.</span></body></html>");
        com.dan.foptionpane.FDialog d = new com.dan.foptionpane.FDialog(javax.swing.SwingUtilities.getWindowAncestor(this), "Kīlauea · Tasten",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel l = label(b.toString());
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        p.add(l, BorderLayout.CENTER);
        FButton ok = new FButton("Schließen");
        ok.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(ok);
        p.add(south, BorderLayout.SOUTH);
        JPanel cp = d.getComponentPane();
        cp.setLayout(new BorderLayout());
        cp.add(p, BorderLayout.CENTER);
        d.setPreferredDialogSize(new Dimension(600, 560));
        d.pack();
        d.setLocationRelativeTo(javax.swing.SwingUtilities.getWindowAncestor(this));
        d.setVisible(true);
        view.requestFocusInWindow();
    }

    // Scrollable: so breit wie der sichtbare Bereich
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle rr, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle rr, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    /** Ein Block mit Kopfzeile zum Auf- und Zuklappen; was danach kommt, landet bis zum nächsten Kopf im Block. */
    private final class Block {
        final String key, text;
        final JLabel kopf = new JLabel();
        final JPanel inhalt = new JPanel();
        Block(String key, String text, boolean offen) {
            this.key = key;
            this.text = text;
            kopf.setForeground(ACCENT);
            kopf.setFont(new Font("SansSerif", Font.BOLD, 12));
            kopf.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
            kopf.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            kopf.setToolTipText("Klick klappt den Block auf oder zu");
            kopf.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { offen(!inhalt.isVisible()); }
            });
            inhalt.setOpaque(false);
            inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
            inhalt.setAlignmentX(Component.LEFT_ALIGNMENT);
            offen(offen);
        }
        void offen(boolean o) {
            inhalt.setVisible(o);
            kopf.setText((o ? "▾  " : "▸  ") + text);
            ControlPanel.this.revalidate();
            ControlPanel.this.repaint();
        }
    }

    private void head(String text, String key, boolean offen) {
        Block b = new Block(key, text, offen);
        bloecke.put(key, b);
        add(row(b.kopf));
        add(b.inhalt);
        target = b.inhalt;
        bind("panel." + key, () -> b.inhalt.isVisible() ? "J" : "N", v -> b.offen("J".equals(v)));
    }

    /** Fügt in den aktuellen Block ein. */
    private void put(Component c) { target.add(c); }

    /** Alle Blöcke auf oder zu. */
    public void alleBloecke(boolean offen) { for (Block b : bloecke.values()) b.offen(offen); }

    private void note(String text) {
        JLabel l = new JLabel("<html><body style='width:205px'>" + text + "</body></html>");
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        put(row(l));
    }

    private void gap() { add(Box.createVerticalStrut(10)); target = this; }

    private static JComponent row(Component c) {
        // Phase 16: die Höchsthöhe folgt dem Inhalt (Texte wachsen nach dem Anlegen; in den Klappblöcken schnitt die alte feste Grenze sie ab)
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return p;
    }
}
