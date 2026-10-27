@echo off
rem Brocken mit Konsole starten: alle Meldungen bleiben stehen (zur Fehlersuche; Phase 18)
rem Dauerlauf gleich beim Start: Brocken_Konsole.bat -Dbrocken.dauerlauf=30
cd /d "%~dp0"
if not exist dist\Brocken.jar (echo dist\Brocken.jar fehlt: bitte in NetBeans Clean and Build ausfuehren. & pause & exit /b 1)
java -Xmx6g -Dsun.java2d.uiScale.enabled=true --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED %* -jar dist\Brocken.jar
echo.
echo Brocken beendet (Rueckgabewert %errorlevel%).
pause
