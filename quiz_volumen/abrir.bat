@echo off
title Quiz Volumen
REM Doble clic para lanzar el programa en modo espera (Alt Alt para capturar).
REM %~dp0 = la carpeta donde vive este .bat, asi funciona la muevas donde la muevas.
cd /d "%~dp0"

REM Usa Opus 5, que es el modelo por defecto. ~1.6 centavos por pregunta.
REM Para cambiar de configuracion, edita la linea de abajo:
REM   quita --barato                   el modelo transcribe las opciones que leyo
REM                                    (util para depurar; sube a ~2.8 centavos)
REM   agrega --modelo claude-sonnet-5  mas barato, casi igual de bueno (~1 centavo)
REM   agrega --ver-ocr                 muestra el texto que leyo tesseract
python quiz_volumen.py --motor ocr-claude --barato --hotkey --step 10

echo.
echo El programa termino. Cierra esta ventana o presiona una tecla.
pause >nul
