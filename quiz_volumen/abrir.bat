@echo off
REM Doble clic para lanzar el programa en modo espera (Alt Alt para capturar).
REM %~dp0 = la carpeta donde vive este .bat, asi funciona la muevas donde la muevas.
cd /d "%~dp0"
python quiz_volumen.py --motor local --hotkey --step 10
echo.
echo El programa termino. Cierra esta ventana o presiona una tecla.
pause >nul
