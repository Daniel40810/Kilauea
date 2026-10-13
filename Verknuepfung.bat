@echo off
rem Legt Verknuepfungen "Kilauea" (Desktop und Startmenue) und "Kilauea Dauerlauf" (im Projekt) mit dem Programmsymbol an.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Verknuepfung.ps1"
if errorlevel 1 (
  echo.
  echo Die Verknuepfung konnte nicht angelegt werden - siehe Meldung oben.
)
pause
