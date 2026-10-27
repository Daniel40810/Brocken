@echo off
rem Brocken: Verknuepfung "Brocken" mit Symbol auf dem Desktop und im Startmenue anlegen (Phase 18)
cd /d "%~dp0"
if not exist Brocken.ico (echo Brocken.ico fehlt im Projektordner: ant symbol ausfuehren. & pause & exit /b 1)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$w = New-Object -ComObject WScript.Shell; foreach ($d in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) { $s = $w.CreateShortcut((Join-Path $d 'Brocken.lnk')); $s.TargetPath = '%~dp0Brocken.bat'; $s.WorkingDirectory = '%~dp0'; $s.IconLocation = '%~dp0Brocken.ico'; $s.WindowStyle = 7; $s.Description = 'Brocken - Dreihundert Tage Nebel: der Brocken im Harz'; $s.Save() }"
if errorlevel 1 (echo Die Verknuepfung liess sich nicht anlegen. & pause & exit /b 1)
echo Verknuepfung "Brocken" liegt auf dem Desktop und im Startmenue.
pause
