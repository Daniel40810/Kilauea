-- ============================================================================
-- Kīlauea, Phase 16a: Paket KIL_API für den Betrieb
-- Die App ruft es aus einem eigenen Faden; nichts davon läuft im Bildlauf.
-- Phase 16b: wo_liegt (Ort, Krater und Strom unter einem Punkt) steht im Paket KIL_WISSEN (05).
-- ============================================================================

CREATE OR REPLACE PACKAGE KIL_API AS

  -- Fassung des Pakets, etwa 'KIL_API 16.0 (Phase 16a)'
  FUNCTION version RETURN VARCHAR2;

  -- Punkt aus Länge und Breite (SRID 8307); NULL, wenn eins von beiden fehlt
  FUNCTION punkt(p_laenge IN NUMBER, p_breite IN NUMBER) RETURN MDSYS.SDO_GEOMETRY;

  -- Neue Sitzung beim Programmstart; gibt die SITZUNG_ID zurück
  FUNCTION sitzung_start(
    p_rechner         IN VARCHAR2,
    p_benutzer        IN VARCHAR2,
    p_app_version     IN VARCHAR2,
    p_java_version    IN VARCHAR2,
    p_kerne           IN NUMBER,
    p_speicher_max_mb IN NUMBER,
    p_fenster_b       IN NUMBER,
    p_fenster_h       IN NUMBER,
    p_qualitaet       IN VARCHAR2,
    p_zustand_geladen IN VARCHAR2) RETURN NUMBER;

  -- Grafikkarte und OpenGL-Fassung nachtragen, sobald der Treiber sie nennt
  PROCEDURE sitzung_grafik(p_sitzung_id IN NUMBER, p_grafik IN VARCHAR2, p_opengl IN VARCHAR2);

  -- Sitzung beim Beenden schließen
  PROCEDURE sitzung_ende(
    p_sitzung_id  IN NUMBER,
    p_bilder_s    IN NUMBER,
    p_ms_gpu      IN NUMBER,
    p_ms_cpu      IN NUMBER,
    p_speicher_mb IN NUMBER);

  -- Ein Ereignis ins Protokoll
  PROCEDURE protokoll(
    p_sitzung_id IN NUMBER,
    p_sekunde    IN NUMBER,
    p_art        IN VARCHAR2,
    p_ziel       IN VARCHAR2 DEFAULT NULL,
    p_text       IN VARCHAR2 DEFAULT NULL,
    p_tag        IN NUMBER   DEFAULT NULL,
    p_stunde     IN NUMBER   DEFAULT NULL,
    p_laenge     IN NUMBER   DEFAULT NULL,
    p_breite     IN NUMBER   DEFAULT NULL);

  -- Jüngster Zustand einer Art (AUTO oder ANSICHT); NULL, wenn es keinen gibt
  FUNCTION zustand_letzter(p_art IN VARCHAR2 DEFAULT 'AUTO') RETURN NUMBER;

  -- Aufräumen: von AUTO bleiben die letzten p_auto_behalten, das Protokoll p_protokoll_tage Tage
  PROCEDURE aufraeumen(p_auto_behalten IN NUMBER DEFAULT 10, p_protokoll_tage IN NUMBER DEFAULT 90);

END KIL_API;
/

CREATE OR REPLACE PACKAGE BODY KIL_API AS

  FUNCTION version RETURN VARCHAR2 IS
  BEGIN
    RETURN 'KIL_API 16.0 (Phase 16a)';
  END version;

  FUNCTION punkt(p_laenge IN NUMBER, p_breite IN NUMBER) RETURN MDSYS.SDO_GEOMETRY IS
  BEGIN
    IF p_laenge IS NULL OR p_breite IS NULL THEN
      RETURN NULL;
    END IF;
    RETURN MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(ROUND(p_laenge, 7), ROUND(p_breite, 7), NULL), NULL, NULL);
  END punkt;

  FUNCTION sitzung_start(
    p_rechner         IN VARCHAR2,
    p_benutzer        IN VARCHAR2,
    p_app_version     IN VARCHAR2,
    p_java_version    IN VARCHAR2,
    p_kerne           IN NUMBER,
    p_speicher_max_mb IN NUMBER,
    p_fenster_b       IN NUMBER,
    p_fenster_h       IN NUMBER,
    p_qualitaet       IN VARCHAR2,
    p_zustand_geladen IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    INSERT INTO KIL_SITZUNG (RECHNER, BENUTZER, APP_VERSION, JAVA_VERSION, KERNE, SPEICHER_MAX_MB,
                             FENSTER_B, FENSTER_H, QUALITAET, ZUSTAND_GELADEN)
    VALUES (SUBSTR(p_rechner, 1, 80), SUBSTR(p_benutzer, 1, 60), SUBSTR(p_app_version, 1, 20),
            SUBSTR(p_java_version, 1, 40), p_kerne, p_speicher_max_mb, p_fenster_b, p_fenster_h,
            SUBSTR(p_qualitaet, 1, 12), CASE WHEN p_zustand_geladen = 'J' THEN 'J' ELSE 'N' END)
    RETURNING SITZUNG_ID INTO v_id;
    RETURN v_id;
  END sitzung_start;

  PROCEDURE sitzung_grafik(p_sitzung_id IN NUMBER, p_grafik IN VARCHAR2, p_opengl IN VARCHAR2) IS
  BEGIN
    UPDATE KIL_SITZUNG
       SET GRAFIK = SUBSTR(p_grafik, 1, 160),
           OPENGL = SUBSTR(p_opengl, 1, 80)
     WHERE SITZUNG_ID = p_sitzung_id;
  END sitzung_grafik;

  PROCEDURE sitzung_ende(
    p_sitzung_id  IN NUMBER,
    p_bilder_s    IN NUMBER,
    p_ms_gpu      IN NUMBER,
    p_ms_cpu      IN NUMBER,
    p_speicher_mb IN NUMBER) IS
  BEGIN
    UPDATE KIL_SITZUNG
       SET BEENDET     = LOCALTIMESTAMP,
           DAUER_S     = GREATEST(0, ROUND((CAST(LOCALTIMESTAMP AS DATE) - CAST(GESTARTET AS DATE)) * 86400)),
           BILDER_S    = LEAST(9999, ROUND(p_bilder_s, 1)),
           MS_GPU      = ROUND(p_ms_gpu, 1),
           MS_CPU      = ROUND(p_ms_cpu, 1),
           SPEICHER_MB = p_speicher_mb
     WHERE SITZUNG_ID = p_sitzung_id;
  END sitzung_ende;

  PROCEDURE protokoll(
    p_sitzung_id IN NUMBER,
    p_sekunde    IN NUMBER,
    p_art        IN VARCHAR2,
    p_ziel       IN VARCHAR2 DEFAULT NULL,
    p_text       IN VARCHAR2 DEFAULT NULL,
    p_tag        IN NUMBER   DEFAULT NULL,
    p_stunde     IN NUMBER   DEFAULT NULL,
    p_laenge     IN NUMBER   DEFAULT NULL,
    p_breite     IN NUMBER   DEFAULT NULL) IS
  BEGIN
    INSERT INTO KIL_PROTOKOLL (SITZUNG_ID, SEKUNDE, ART, ZIEL, TEXT, SZENE_TAG, SZENE_STUNDE, GEOM)
    VALUES (p_sitzung_id, ROUND(NVL(p_sekunde, 0), 1), SUBSTR(p_art, 1, 12), SUBSTR(p_ziel, 1, 80),
            SUBSTR(p_text, 1, 400), p_tag, ROUND(p_stunde, 2), punkt(p_laenge, p_breite));
  END protokoll;

  FUNCTION zustand_letzter(p_art IN VARCHAR2 DEFAULT 'AUTO') RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT MAX(ZUSTAND_ID) INTO v_id FROM KIL_ZUSTAND WHERE ART = p_art;
    RETURN v_id;
  END zustand_letzter;

  PROCEDURE aufraeumen(p_auto_behalten IN NUMBER DEFAULT 10, p_protokoll_tage IN NUMBER DEFAULT 90) IS
  BEGIN
    DELETE FROM KIL_ZUSTAND
     WHERE ART = 'AUTO'
       AND ZUSTAND_ID NOT IN (SELECT ZUSTAND_ID
                                FROM (SELECT ZUSTAND_ID FROM KIL_ZUSTAND WHERE ART = 'AUTO' ORDER BY ZUSTAND_ID DESC)
                               WHERE ROWNUM <= p_auto_behalten);
    DELETE FROM KIL_PROTOKOLL WHERE ZEIT < LOCALTIMESTAMP - NUMTODSINTERVAL(p_protokoll_tage, 'DAY');
  END aufraeumen;

END KIL_API;
/
