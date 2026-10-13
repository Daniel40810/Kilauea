# Legt auf dem Desktop eine Verknuepfung "Kilauea" mit dem Programmsymbol an.
# Alle Pfade kommen aus dem Ordner dieser Datei ($PSScriptRoot): so stoert das i mit Strich im
# Ordnernamen nicht (keine Pfade als Text in der Datei, keine Codepage).
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar  = Join-Path $root 'dist\Kilauea.jar'
$ico  = Join-Path $root 'icon\kilauea.ico'
if (-not (Test-Path -LiteralPath $jar)) { throw "JAR fehlt: $jar  (in NetBeans zuerst Clean and Build)" }
if (-not (Test-Path -LiteralPath $ico)) { throw "ICO fehlt: $ico" }
$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) { $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe' }
else { $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue; if ($cmd) { $javaw = $cmd.Source } }
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }
$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'Kilauea.lnk'
$opts = '-Xmx4g --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED'
$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath       = $javaw
$s.Arguments        = $opts + ' -jar "' + $jar + '"'
$s.WorkingDirectory = $root
$s.IconLocation     = $ico
$s.Description      = 'Kilauea - der ganze Vulkan in 3D'
$s.Save()
Write-Host "Verknuepfung angelegt: $lnk"
