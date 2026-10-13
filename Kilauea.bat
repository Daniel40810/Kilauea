@echo off
rem Kilauea starten. JOGL braucht unter Java 21 Zugriff auf einige AWT-Interna.
cd /d "%~dp0"
if not exist "dist\Kilauea.jar" (
    echo Fehler: dist\Kilauea.jar nicht gefunden. Bitte in NetBeans zuerst "Clean and Build".
    timeout /t 6
    exit /b 1
)
set OPTS=-Xmx4g --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED
rem Falls das Bild schwarz bleibt: die naechste Zeile aktivieren (leichtes GLJPanel statt GLCanvas)
rem set OPTS=%OPTS% -Dkilauea.gljpanel=true
rem Dauerlauf von 10 Minuten mit Bericht in logs\ (Phase 17): die naechste Zeile aktivieren
rem set OPTS=%OPTS% -Dkilauea.dauerlauf=true
start "" javaw %OPTS% -jar "dist\Kilauea.jar"
exit
