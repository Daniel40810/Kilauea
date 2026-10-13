package com.dan.kilauea.script;

import com.dan.kilauea.lava.VentSite;
import com.dan.kilauea.magma.Rift;
import com.dan.kilauea.terrain.Geo;

import java.util.List;
import java.util.Locale;

/**
 * Die fünf Drehbücher nach echten Ausbrüchen (Phase 12) und der freie Ausbruch an einer Riftzone.
 * <p>
 * Ablauf, Daten, Fontänenhöhen und Orte folgen den Berichten des USGS Hawaiian Volcano Observatory [L]. Die
 * Zeit ist erzählt, nicht gemessen [G]: Wochen werden zu Stunden Simulation, die Lava rechnet in dieser Zeit
 * mit Raten in der richtigen Größenordnung. Die Tafel zeigt den Kalender der echten Ereignisse. Das Gelände
 * ist das von heute (nach 2018); die Lava eines Drehbuchs fließt darüber. Nur der Gipfel 2018 wird vorher
 * aufgebaut und sackt dann ab ({@link com.dan.kilauea.terrain.Deformation}).
 */
public final class Scripts {
    private Scripts() { }

    static final double MIN = 60, HOUR = 3600, DAY = 86400;

    public static List<Script> all() { return List.of(iki1959(), maunaUlu(), puuOo(), y2018(), fountains2024()); }

    public static Script byId(String id) {
        for (Script s : all()) if (s.id.equals(id)) return s;
        throw new IllegalArgumentException("kein Drehbuch " + id);
    }

    // ------------------------------------------------------------------ 1959

    public static Script iki1959() {
        return Script.of("1959", "Kīlauea Iki 1959",
                        "36 Tage, 17 Fontänen-Episoden, die höchste mit 580 m. Die Lava füllt den Krater zu einem See, der Jahrzehnte zum Erstarren braucht.")
                .setup(s -> { s.lake(false); s.supply(1); })
                .chapter("August bis November 1959", "Magma steigt auf",
                        "Seit August zeichnen die Seismographen Beben in 45 bis 60 km Tiefe auf, dann immer flacher. Der Gipfel hebt sich. "
                                + "Im Schnitt sieht man, wie sich die Speicher füllen [L USGS; Ablauf im Modell A].")
                .dates("1959-08-14", "1959-11-14").lasts(12 * DAY, DAY)
                .look(19.4135, -155.2512, 0, 200, 30, 3600).hour(15).sky("1959-11-10")
                .at(0, s -> { s.supply(10); s.inject(3e6, true); })
                .chapter("14. November 1959, 20:08", "Ein Vorhang aus Feuer",
                        "An der Südwand des Kīlauea Iki reißt eine Spalte auf; anderthalb Stunden später ist sie rund 900 m lang. Lava schießt in einer "
                                + "Reihe von Fontänen hoch und stürzt in den Krater [L].")
                .lasts(50 * MIN, 120).look(19.4133, -155.2525, 20, 200, 16, 1500).hour(20.15).sky("1959-11-14")
                .at(0, s -> {
                    s.supply(1);
                    s.begin("Kīlauea Iki 1959", 19.4135, -155.2512);
                    s.open("spalte", "Spalte an der Südwand", 19.4130, -155.2548, 19.4137, -155.2500, 40, 35);
                })
                .chapter("15. bis 19. November 1959", "Ein Schlot setzt sich durch",
                        "Die Spalte verstummt bis auf einen Schlot im Westen. Seine Fontäne wächst bis auf 380 m. Bims und Schlacke fallen im Südwesten "
                                + "und bauen den Puʻu Puaʻi auf; der Krater füllt sich zu einem See aus Lava [L].")
                .lasts(2 * HOUR, 240).look(19.4150, -155.2505, 60, 215, 18, 2600).hour(18.0).sky("1959-11-18")
                .at(0, s -> { s.close("spalte"); s.open("schlot", VentSite.KILAUEA_IKI, 60, 180); })
                .at(30 * MIN, s -> s.fountain("schlot", 290))
                .at(70 * MIN, s -> { s.fountain("schlot", 380); s.rate("schlot", 75); })
                .chapter("21. November 1959", "Rückfluss",
                        "Episode 1 endet, der See ist 98 m tief. Kaum ist die Fontäne still, fließt Lava aus dem See in den Schlot zurück, später mit einem "
                                + "Strudel gegen den Uhrzeigersinn. Das wiederholt sich nach den meisten der 17 Episoden [L; hier als Sog am Schlot, A].")
                .lasts(40 * MIN, 120).hour(18.1).sky("1959-11-21")
                .at(0, s -> { s.fountain("schlot", 0); s.rate("schlot", -25); })
                .chapter("17. Dezember 1959", "Die höchste Fontäne",
                        "Episode 15: Die Fontäne steigt auf rund 580 m, die höchste in Hawaiʻi im 20. Jahrhundert [L, USGS: 1 900 Fuß]. "
                                + "Der See steigt wieder; der Wind trägt Peles Haar nach Südwesten.")
                .dates("1959-12-17", "1959-12-17").lasts(45 * MIN, 120).look(19.4150, -155.2500, 220, 225, 10, 3300).hour(18.1)
                .at(0, s -> { s.rate("schlot", 120); s.fountain("schlot", 580); })
                .chapter("20. Dezember 1959", "Der See erstarrt",
                        "Nach 36 Tagen ist es vorbei. Der See war bis zu 126 m tief; seine Kruste trägt bald, aber innen bleibt er Jahrzehnte heiß. "
                                + "Heute führt der Kīlauea Iki Trail über ihn [L]. Hier kühlt seine Oberfläche in zwei Stunden.")
                .lasts(2 * HOUR, 600).look(19.4160, -155.2490, 0, 200, 34, 2600).hour(9).sky("1959-12-21")
                .at(0, s -> s.closeAll())
                .build();
    }

    // ------------------------------------------------------------------ Mauna Ulu

    public static Script maunaUlu() {
        return Script.of("maunaulu", "Mauna Ulu 1969–1974",
                        "Eine Spaltenreihe, zwölf Fontänen-Episoden bis 540 m, dann fünf Jahre Lavasee und Ströme bis ins Meer. Es wächst ein Schild von 121 m.")
                .setup(s -> { s.lake(true); })
                .chapter("24. Mai 1969", "Spalten zwischen ʻAloʻi und ʻAlae",
                        "Zwischen zwei Grubenkratern der Ostriftzone öffnet sich eine Reihe von Spalten. Ein Vorhang aus niedrigen Fontänen, "
                                + "die Lava fließt nach Süden [L].")
                .lasts(50 * MIN, 120).look(19.3664, -155.2025, 0, 170, 20, 2600).hour(19.2).sky("1969-05-24")
                .at(0, s -> {
                    s.begin("Mauna Ulu 1969", 19.3654, -155.2026);
                    s.open("spalte", "Spalten bei Mauna Ulu", 19.3668, -155.2080, 19.3660, -155.1970, 30, 45);
                })
                .chapter("Juni bis Dezember 1969", "Zwölf Fontänen-Episoden",
                        "Die Spalten erlöschen bis auf einen Schlot. Er wirft in Episoden Fontänen von 200 bis 540 m aus, dazwischen Stunden der Ruhe [L].")
                .dates("1969-06-01", "1969-12-30").lasts(110 * MIN, 120).look(19.3654, -155.2026, 200, 200, 10, 3600).hour(18.1).sky("1969-12-30")
                .at(0, s -> { s.close("spalte"); s.open("schlot", VentSite.MAUNA_ULU, 40, 220); })
                .at(35 * MIN, s -> s.rate("schlot", 0))
                .at(55 * MIN, s -> { s.rate("schlot", 60); s.fountain("schlot", 540); })
                .at(100 * MIN, s -> s.fountain("schlot", 90))
                .chapter("1970 bis 1971", "See und Ströme",
                        "Im wachsenden Kegel steht ein Lavasee; aus ihm laufen Ströme über die Flanke nach Süden zur Hōlei Pali [L]. "
                                + "Hier: 10 m³/s über vier Stunden.")
                .lasts(4 * HOUR, 600).look(19.3600, -155.2026, 0, 175, 32, 4500).hour(11).sky("1970-08-01")
                .at(0, s -> { s.rate("schlot", 10); s.fountain("schlot", 25); })
                .chapter("1972 bis 1974", "Röhren bis zum Meer",
                        "Der Strom deckt sich zu: unter einer Kruste fließt die Lava in Röhren weiter, Oberlichter bleiben offen. "
                                + "Die echten Ströme liefen so über Monate 12 km bis ins Meer; hier ist nur ihr Anfang zu sehen [L; Zeitraffer G].")
                .dates("1972-02-01", "1974-07-22").lasts(3 * HOUR, 600).look(19.3560, -155.2030, 0, 160, 40, 2500).hour(16)
                .chapter("22. Juli 1974", "Ende",
                        "Der Ausbruch endet nach fünf Jahren. Mauna Ulu steht 121 m über dem alten Gelände, seine Ströme bedecken rund 46 km² [L].")
                .lasts(HOUR, 600).look(19.3654, -155.2026, 0, 200, 30, 3800).hour(17.5).sky("1974-07-22")
                .at(0, s -> s.closeAll())
                .build();
    }

    // ------------------------------------------------------------------ Puʻu ʻŌʻō und Kamokuna

    public static Script puuOo() {
        return Script.of("puuoo", "Puʻu ʻŌʻō und Kamokuna 1983–2018",
                        "35 Jahre an einem Kegel: erst hohe Fontänen, dann Röhren bis zur Küste. 2016 läuft die Lava bei Kamokuna ins Meer, das Delta bricht ab, ein Feuerwehrschlauch fällt aus der Klippe.")
                .setup(s -> { s.lake(true); })
                .chapter("3. Januar 1983", "Spalten in der Ostriftzone",
                        "Nach einem Gang aus dem Gipfel öffnen sich Spalten über rund 8 km der Ostriftzone. Bald sammelt sich alles an einer Stelle [L].")
                .lasts(40 * MIN, 120).look(19.3878, -155.1060, 0, 160, 18, 3800).hour(18.3).sky("1983-01-03")
                .at(0, s -> {
                    s.begin("Puʻu ʻŌʻō 1983", 19.3880, -155.1050);
                    s.open("spalte", "Spalten 1983", 19.3850, -155.1150, 19.3905, -155.0960, 25, 40);
                })
                .chapter("1983 bis 1986", "44 Fontänen-Episoden",
                        "Alle paar Wochen eine Fontäne bis rund 460 m; aus Schlacke wächst der Kegel Puʻu ʻŌʻō auf 255 m [L]. "
                                + "Das Gelände zeigt den Krater, wie er nach dem Einsturz 2018 ist; die Fontäne steigt aus ihm.")
                .dates("1983-06-01", "1986-07-18").lasts(35 * MIN, 120).look(19.3880, -155.1050, 180, 200, 10, 3600).hour(18.75)
                .at(0, s -> { s.close("spalte"); s.open("kegel", "Schlot im Kegel", 19.3880, -155.1052, 19.3880, -155.1048, 60, 420); })
                .chapter("1986 bis 2016", "Röhren tragen die Lava",
                        "Der Ausbruch wird ruhig: im Mittel rund 4 m³/s, aber über Jahrzehnte. Unter einer Kruste fließt die Lava in Röhren "
                                + "bis zur Küste. 2016 öffnet sich Schlot 61g an der Ostflanke [L].")
                .dates("1986-07-18", "2016-05-24").lasts(3 * HOUR, 600).look(19.3846, -155.0992, 0, 150, 30, 2600).hour(10)
                .at(0, s -> { s.close("kegel"); s.open("61g", VentSite.PUU_OO, 4, 6); })
                .chapter("26. Juli 2016", "Kamokuna: Lava erreicht das Meer",
                        "Nach zwei Monaten und rund 10 km in einer Röhre tritt die Lava bei Kamokuna an der Klippe aus und fällt ins Meer. "
                                + "Laze steigt auf, ein Delta aus neuem Land wächst [L].")
                .dates("2016-07-26", "2016-12-30").lasts(4 * HOUR, 600).look(19.3205, -155.0450, 0, 175, 20, 1500).hour(15)
                .at(0, s -> s.open("kamokuna", VentSite.KAMOKUNA, 6, 0))
                .chapter("31. Dezember 2016", "Das Delta bricht ab",
                        "Am Silvesternachmittag rutscht ein großes Stück des rund 10 ha großen Deltas ins Meer. Wellen und Gesteinsbrocken reißen auch "
                                + "die alte Klippe an [L]. Das Delta des Modells ist nach Stunden noch klein; es bricht ein Stück davon ab [G].")
                .lasts(20 * MIN, 60).look(19.3215, -155.0450, 0, 180, 18, 900).hour(15).sky("2016-12-31")
                .at(10, s -> s.deltaCollapse(160))
                .chapter("Januar bis März 2017", "Der Feuerwehrschlauch",
                        "Aus einem Riss in der Klippe schießt ein Strahl aus Lava 20 m tief ins Meer, bei Nacht weithin zu sehen [L].")
                .dates("2017-01-28", "2017-03-20").lasts(40 * MIN, 60).look(19.3205, -155.0450, -10, 180, 6, 500).hour(18.45)
                .at(0, s -> s.rate("kamokuna", 8))
                .chapter("30. April 2018", "Der Kegel stürzt ein",
                        "Das Magma weicht in die untere Ostriftzone aus. Der Kraterboden des Puʻu ʻŌʻō bricht ein, 35 Jahre Ausbruch sind vorbei. "
                                + "Drei Tage später beginnt in Leilani Estates der Ausbruch von 2018 (eigenes Drehbuch) [L].")
                .lasts(90 * MIN, 600).look(19.3880, -155.1050, 0, 210, 28, 3000).hour(11).sky("2018-04-30")
                .at(0, s -> s.closeAll())
                .build();
    }

    // ------------------------------------------------------------------ 2018

    /** Bogenlänge der Spalte 8 entlang der Ostriftzone (m vom Gipfel). */
    static final double S_F8 = 42140;

    static void fissure(Stage s, String id, double sAlong, double half, double rate, double fountain) {
        double[] p = Stage.riftPoint(Rift.EAST, sAlong);
        s.openXZ(id, "Spalte " + id, p[0] - p[2] * half, p[1] - p[3] * half, p[0] + p[2] * half, p[1] + p[3] * half, rate, fountain);
    }

    public static Script y2018() {
        Script.Builder b = Script.of("2018", "Untere Ostriftzone und Gipfel 2018",
                        "Der größte Ausbruch der unteren Ostriftzone seit mindestens 200 Jahren: Das Magma läuft aus dem Gipfel 40 km weit nach Osten. In Leilani Estates öffnen sich 24 Spalten, "
                                + "Spalte 8 füllt die Kapoho-Bucht; der Gipfel sackt in 62 Stößen bis zu 500 m ab.")
                .setup(s -> { s.collapse(0); s.lake(true); s.lakeDrop(0); s.inject(4e6, true); })
                .chapter("Ende April 2018", "Der Gipfel ist voll",
                        "Der Lavasee im Overlook-Krater des Halemaʻumaʻu steht so hoch wie nie und läuft über. Auch am Puʻu ʻŌʻō staut sich das Magma [L]. "
                                + "Das Gelände zeigt den Gipfel vor dem Einsturz [G].")
                .dates("2018-04-21", "2018-04-29").lasts(2 * DAY, 4 * HOUR).look(19.4062, -155.2846, 0, 160, 20, 2600).hour(18.45)
                .at(0, s -> s.summitFeed = 6)   // Phase 14: der Gipfel hebt sich noch [L Verlauf, G Maß]
                .chapter("30. April bis 3. Mai 2018", "Ein Gang läuft nach Osten",
                        "Der Kraterboden des Puʻu ʻŌʻō bricht ein. Magma dringt in einem Gang die Ostriftzone hinab, Beben wandern mit seiner Spitze. "
                                + "Der Schnitt zeigt ihn [L; Tempo im Modell rund 11 km am Tag, A].")
                .dates("2018-04-30", "2018-05-03").lasts(5 * DAY, DAY).hour(12)
                .at(0, s -> { s.fx.section("EAST"); s.fx.lookAtSection(); s.intrude(Rift.EAST, S_F8 + 600, 0.13, 0.4); s.lakeDrop(10); })
                .until(Stage::dikeArrived)
                .chapter("3. bis 26. Mai 2018", "Spalten in Leilani Estates",
                        "Zwischen Häusern öffnet sich Spalte um Spalte, 24 in drei Wochen. Am Gipfel sinkt der Lavasee und ist am 10. Mai nicht mehr "
                                + "zu sehen; ab dem 16. Mai explodiert der Krater, am 17. steigt Asche bis rund 9 km [L].")
                .dates("2018-05-03", "2018-05-26").lasts(2 * HOUR, 240).look(19.4610, -154.9110, 0, 40, 18, 3200).hour(18.55)
                .at(0, s -> {
                    s.fx.section("OFF");
                    s.begin("Untere Ostriftzone 2018", 19.4625, -154.9085);
                    fissure(s, "1", S_F8 - 300, 110, 6, 35);
                    s.summitFeed = -300;   // Phase 14: der Gipfel sinkt, während unten die Spalten aufreißen [L Verlauf, G Maß]
                })
                .at(10 * MIN, s -> { fissure(s, "2", S_F8 - 900, 120, 5, 30); s.lakeDrop(25); })
                .at(22 * MIN, s -> fissure(s, "7", S_F8 + 400, 100, 6, 40))
                .at(35 * MIN, s -> { fissure(s, "13", S_F8 + 1300, 140, 8, 50); s.lakeDrop(40); })
                .at(45 * MIN, s -> s.close("1"))
                .at(55 * MIN, s -> { s.lakeDrop(70); s.collapseEvent(0.03, 4.4); })
                .at(62 * MIN, s -> {   // 17. Mai: Ascheexplosion
                    double x = Geo.x(19.4062, -155.2846), z = Geo.z(19.4062);
                    s.lake(false);
                    s.fx.ash(x, z, 250, 120);
                    s.collapseEvent(0.05, 4.9);
                })
                .at(70 * MIN, s -> fissure(s, "17", S_F8 + 2200, 160, 6, 60))
                .at(80 * MIN, s -> s.close("2"))
                .at(95 * MIN, s -> s.collapseEvent(0.08, 5.0))
                .at(105 * MIN, s -> fissure(s, "8", S_F8, 60, 30, 50));
        // Spalte 8 und der Einsturz
        b.chapter("27. Mai bis 4. Juni 2018", "Spalte 8 und der Kanal",
                        "Spalte 8 übernimmt: eine Fontäne von 50 bis 80 m, ein Kanal trägt bis rund 100 m³/s nach Nordosten. "
                                + "Am 3. Juni erreicht die Lava die Kapoho-Bucht und füllt sie in wenigen Tagen [L]. Das Modell zeigt in seinen Stunden den Anfang des Stroms; "
                                + "das Feld bis Kapoho ist im Gelände von heute schon da [G].")
                .dates("2018-05-27", "2018-06-04").lasts(150 * MIN, 600).look(19.4650, -154.9030, 0, 55, 20, 3800).hour(18.7)
                .at(0, s -> {
                    s.close("7"); s.close("13"); s.close("17"); s.rate("8", 100); s.fountain("8", 70);
                    // Phase 14: der flache Speicher läuft leer, bei jedem Einsturz springt die Neigung zurück (Sägezahn) [L Verlauf, G Maß]
                    s.summitFeed = -900;
                    s.collapseStep = 1.2e5;
                });
        double f = 0.08;
        for (int k = 0; k < 15; k++) {
            double ff = f + (0.25 - f) * (k + 1) / 15.0;
            double mag = 5.2 + 0.1 * (k % 3);
            b.at((k + 0.5) * 10 * MIN, s -> s.collapseEvent(ff, mag));
        }
        b.chapter("5. Juni bis 2. August 2018", "Der Gipfel sackt ab",
                        "Fast täglich ein Beben der Stärke 5,2 bis 5,4: Der Calderaboden bricht in 62 Stößen ein, insgesamt über 500 m. "
                                + "Jedes Mal steigt eine Staubwolke auf. Unten fördert Spalte 8 weiter [L, USGS; Ablauf über die Caldera G].")
                .dates("2018-06-05", "2018-08-02").lasts(150 * MIN, 600).look(19.4210, -155.2880, 0, 150, 22, 5200).hour(10)
                .lavaCap(600);
        for (int k = 0; k < 42; k++) {
            double ff = 0.25 + 0.75 * (k + 1) / 42.0;
            double mag = 5.2 + 0.1 * ((k * 7) % 3);
            b.at((k + 0.5) * (150 * MIN) / 42, s -> s.collapseEvent(ff, mag));
        }
        b.chapter("4. August 2018", "Stille",
                        "Der letzte Einsturz ist am 2. August, zwei Tage später lässt Spalte 8 nach. Rund 1,5 km³ Lava, 3,5 km² neues Land, über 700 Gebäude "
                                + "unter Lava. Der Gipfel ist über 500 m tiefer, so wie das Gelände heute [L].")
                .lasts(30 * MIN, 600).look(19.4100, -155.2830, 0, 160, 34, 6000).hour(16).sky("2018-08-04")
                .at(0, s -> { s.closeAll(); s.endDike(); s.collapse(1); s.summitFeed = 0; s.collapseStep = 0; });
        return b.build();
    }

    // ------------------------------------------------------------------ 2024/25

    public static Script fountains2024() {
        return Script.of("2024", "Fontänen im Halemaʻumaʻu 2024/25",
                        "Seit Dezember 2024 Episode um Episode: Der Gipfel hebt sich über Tage, dann schießen aus zwei Schloten Fontänen von einigen hundert Metern, "
                                + "nach Stunden ist es vorbei und der Gipfel senkt sich.")
                .setup(s -> s.lake(false))
                .chapter("Dezember 2024", "Der Gipfel hebt sich",
                        "Magma füllt den flachen Speicher unter dem Halemaʻumaʻu. Die Neigungsmesser am Kraterrand zeigen Hebung, kleine Beben häufen sich [L]. "
                                + "Im Bedienfeld unter „Unter der Oberfläche“ steigt der Druck; Mengen erzählt [G], die Neigung im Maß der Messungen 2025 [L].")
                .dates("2024-12-15", "2024-12-23").lasts(5 * DAY, DAY).look(19.4050, -155.2870, 0, 225, 32, 2600).hour(9)
                .at(0, s -> s.summitFeed = 0.7)
                .chapter("23. Dezember 2024", "Episode 1",
                        "Um 2:20 Uhr öffnen sich in der Südwestecke des Kraters zwei Schlote. Erst brodelt die Lava, dann steigen Fontänen auf; "
                                + "die Lava breitet sich über den Kraterboden aus [L].")
                .lasts(70 * MIN, 120).look(19.4050, -155.2870, 20, 230, 18, 1800).hour(6.9).sky("2024-12-23")
                .at(0, s -> {
                    s.summitFeed = -70;
                    s.begin("Halemaʻumaʻu 2024", 19.4050, -155.2872);
                    s.open("nord", "Nordschlot", 19.4055, -155.2877, 19.4056, -155.2874, 6, 15);
                    s.open("sued", "Südschlot", 19.4040, -155.2869, 19.4041, -155.2866, 4, 10);
                })
                .at(15 * MIN, s -> { s.rate("nord", 15); s.fountain("nord", 80); s.rate("sued", 8); s.fountain("sued", 45); })
                .at(40 * MIN, s -> { s.rate("nord", 25); s.fountain("nord", 180); })
                .chapter("Pause", "Der Gipfel lädt sich wieder auf",
                        "Die Schlote verstummen, die Lava auf dem Kraterboden erstarrt. Nach jeder Episode hebt sich der Gipfel wieder, "
                                + "meist eine bis zwei Wochen lang [L].")
                .dates("2024-12-24", "2025-06-19").lasts(7 * DAY, DAY).look(19.4050, -155.2870, 0, 225, 32, 2600).hour(9.5)
                .at(0, s -> { s.closeAll(); s.summitFeed = 0.55; s.passiveSO2 = 1750; })
                .chapter("20. Juni 2025", "Episode 26: Fontänen über 300 m",
                        "Eine der stärksten Episoden: aus dem Nordschlot eine Fontäne von rund 300 m, der Wind trägt Schlacke und Peles Haar weit nach Südwesten. "
                                + "Nach rund zehn Stunden ist es vorbei, der Gipfel sinkt [L].")
                .lasts(80 * MIN, 120).look(19.4060, -155.2870, 150, 220, 12, 3600).hour(19.35).sky("2025-06-20")
                .at(0, s -> { s.summitFeed = -70; s.passiveSO2 = Double.NaN; s.open("nord", "Nordschlot", 19.4055, -155.2877, 19.4056, -155.2874, 10, 40); s.open("sued", "Südschlot", 19.4040, -155.2869, 19.4041, -155.2866, 6, 20); })
                .at(10 * MIN, s -> { s.rate("nord", 30); s.fountain("nord", 330); s.rate("sued", 12); s.fountain("sued", 150); })
                .at(65 * MIN, s -> { s.rate("nord", 8); s.fountain("nord", 60); s.rate("sued", 0); })
                .chapter("Danach", "Ein neuer Kraterboden",
                        "Jede Episode legt eine neue Lage auf den Boden des Halemaʻumaʻu; seit 2024 ist er so um Dutzende Meter gestiegen. "
                                + "Wie es weitergeht, sagt dieses Modell nicht voraus [L; G].")
                .lasts(90 * MIN, 600).look(19.4050, -155.2860, 0, 210, 34, 2400).hour(9.5).sky("2025-06-21")
                .at(0, s -> { s.closeAll(); s.summitFeed = 0; })
                .build();
    }

    // ------------------------------------------------------------------ freier Ausbruch

    /**
     * Ein Ausbruch an einer frei gewählten Stelle der Riftzonen: ein Gang läuft vom Gipfel bis dorthin, dann
     * öffnet sich darüber eine Spalte entlang der Riftzone, wie es an Hawaiʻis Riftzonen üblich ist [L].
     *
     * @param r       Riftzone
     * @param s       Bogenlänge vom Gipfel (m)
     * @param rate    Förderrate (m³/s)
     * @param length  Länge der Spalte (m)
     * @param hours   Dauer des Ausbruchs (h Simulation)
     */
    public static Script free(Rift r, double s, double rate, double length, double hours) {
        double[] p = Stage.riftPoint(r, s);
        double lat = Geo.lat(p[1]), lon = Geo.lon(p[0], p[1]);
        String where = String.format(Locale.GERMAN, "%s, %.1f km vom Gipfel", r.label, s / 1000);
        double fountain = Math.min(300, 25 * Math.pow(rate / 10, 0.7));
        double bearing = (Math.toDegrees(Math.atan2(p[2], -p[3])) + 90 + 360) % 360;   // quer zur Spalte blicken
        boolean near = s < 2500;
        double days = Math.max(0.2, (s - 1500) / (0.12 * DAY));
        Script sc = Script.of("frei", "Freier Ausbruch: " + where,
                        "Ein Gang läuft vom Gipfel durch die " + r.label + ", dann öffnet sich eine Spalte von " + Math.round(length) + " m.")
                .chapter("Tag 0", "Ein Gang dringt vor",
                        "Magma verlässt den Speicher unter der Südcaldera und schiebt sich als Platte durch die " + r.label + ", rund 10 km am Tag. "
                                + "Beben wandern mit der Spitze, der Gipfel senkt sich [A].")
                .lasts(near ? HOUR : days * DAY * 1.2, near ? 600 : DAY).hour(13)
                .at(0, st -> { st.fx.section(r == Rift.EAST ? "EAST" : "SOUTHWEST"); st.fx.lookAtSection(); st.intrude(r, s, 0.12); })
                .until(Stage::dikeArrived)
                .chapter(String.format(Locale.GERMAN, "Tag %.0f", near ? 0 : days), "Die Spalte öffnet sich",
                        String.format(Locale.GERMAN, "Über der Spitze des Gangs reißt der Boden auf: %.0f m Spalte, %.0f m³/s, Fontänen um %.0f m [Raten G].", length, rate, fountain))
                .lasts(hours * HOUR, 600).look(lat, lon, 0, bearing, 24, Math.max(1800, length * 4)).hour(-0.3)
                .at(0, st -> {
                    st.fx.section("OFF");
                    st.begin(where, lat, lon);
                    st.openXZ("frei", where, p[0] - p[2] * length / 2, p[1] - p[3] * length / 2, p[0] + p[2] * length / 2, p[1] + p[3] * length / 2, rate, fountain);
                })
                .at(hours * HOUR * 0.6, st -> { st.rate("frei", rate * 0.4); st.fountain("frei", fountain * 0.5); })
                .chapter("Danach", "Der Ausbruch klingt ab",
                        "Die Förderung versiegt, der Gang erstarrt, die Lava kühlt. „Beenden“ bringt das Gelände zurück in den Zustand vorher.")
                .lasts(HOUR, 600).hour(9)
                .at(0, st -> { st.closeAll(); st.endDike(); })
                .build();
        sc.frei = new Script.Frei(r == Rift.EAST ? "OST" : "SUEDWEST", s / 1000, rate, length, lat, lon);
        return sc;
    }
}
