# Legt Verknuepfungen fuer Kilauea mit dem Programmsymbol an (Phase 17):
#   Desktop:   "Kilauea" mit i-Strich (startet die App)
#   Startmenue: dieselbe Verknuepfung unter Programme
#   Projekt:   "Kilauea Dauerlauf" (startet die App und gleich den Dauerlauf von 10 Minuten)
# Alle Pfade kommen aus dem Ordner dieser Datei ($PSScriptRoot): so stoert das i mit Strich im
# Ordnernamen nicht. Die Datei selbst bleibt reines ASCII (PowerShell 5 liest sie sonst falsch);
# das i mit Strich entsteht als Zeichen 0x012B.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar  = Join-Path $root 'dist\Kilauea.jar'
$ico  = Join-Path $root 'icon\kilauea.ico'
if (-not (Test-Path -LiteralPath $jar)) { throw "JAR fehlt: $jar  (in NetBeans zuerst Clean and Build)" }
if (-not (Test-Path -LiteralPath $ico)) { throw "ICO fehlt: $ico" }

# javaw suchen: JAVA_HOME, sonst PATH
$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) { $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe' }
else { $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue; if ($cmd) { $javaw = $cmd.Source } }
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java 21 in den PATH aufnehmen." }

# Java muss mindestens 21 sein (das Projekt ist mit JDK 21 gebaut)
$java = Join-Path (Split-Path $javaw) 'java.exe'
if (Test-Path -LiteralPath $java) {
  $alt = $ErrorActionPreference; $ErrorActionPreference = 'Continue'   # java -version schreibt nach stderr
  $ver = (& $java -version 2>&1 | Out-String)
  $ErrorActionPreference = $alt
  if ($ver -match 'version "(\d+)') {
    $major = [int]$Matches[1]
    if ($major -lt 21) { Write-Warning "Java $major gefunden ($javaw). Kilauea braucht Java 21 oder neuer." }
    else { Write-Host "Java ${major}: $javaw" }
  }
}

$name = 'K' + [char]0x012B + 'lauea'
$opts = '-Xmx4g --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED'
$sh = New-Object -ComObject WScript.Shell

function Neu($lnk, $extra, $text) {
  $s = $sh.CreateShortcut($lnk)
  $s.TargetPath       = $javaw
  $s.Arguments        = ($opts + ' ' + $extra + ' -jar "' + $jar + '"').Replace('  ', ' ')
  $s.WorkingDirectory = $root
  $s.IconLocation     = "$ico,0"
  $s.Description      = $text
  $s.Save()
  Write-Host "Verknuepfung angelegt: $lnk"
}

$desktop = [Environment]::GetFolderPath('Desktop')
$programs = [Environment]::GetFolderPath('Programs')
Neu (Join-Path $desktop ($name + '.lnk')) '' ($name + ' - der ganze Vulkan in 3D')
Neu (Join-Path $programs ($name + '.lnk')) '' ($name + ' - der ganze Vulkan in 3D')
Neu (Join-Path $root ($name + ' Dauerlauf.lnk')) '-Dkilauea.dauerlauf=true' ($name + ' - Dauerlauf von 10 Minuten mit Bericht in logs')

# Die alte Verknuepfung aus Phase 1 ("Kilauea" ohne Strich) entfernen, wenn sie auf dieses Projekt zeigt
$altLnk = Join-Path $desktop 'Kilauea.lnk'
if ((Test-Path -LiteralPath $altLnk) -and ($altLnk -ne (Join-Path $desktop ($name + '.lnk')))) {
  $a = $sh.CreateShortcut($altLnk)
  if ($a.Arguments -like ('*' + $jar + '*')) { Remove-Item -LiteralPath $altLnk; Write-Host "Alte Verknuepfung entfernt: $altLnk" }
}
