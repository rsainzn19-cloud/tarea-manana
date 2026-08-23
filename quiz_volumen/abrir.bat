@echo off
title Quiz Volumen
REM Doble clic para lanzar el programa en modo espera (Alt Alt para capturar).
REM %~dp0 = la carpeta donde vive este .bat, asi funciona la muevas donde la muevas.
cd /d "%~dp0"

REM Para cambiar de configuracion, edita la linea de abajo:
REM   --modelo claude-opus-5     mas caro, un poco mejor
REM   --modelo claude-haiku-4-5  mucho mas barato, mas errores
REM   quita --barato             para ver que opciones leyo (cuesta mas)
python quiz_volumen.py --motor ocr-claude --modelo claude-sonnet-5 --barato --hotkey --step 10

echo.
echo El programa termino. Cierra esta ventana o presiona una tecla.
pause >nul
