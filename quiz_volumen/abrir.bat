@echo off
title Quiz Volumen
REM Doble clic para lanzar el programa en modo espera (Alt Alt para capturar).
REM %~dp0 = la carpeta donde vive este .bat, asi funciona la muevas donde la muevas.
cd /d "%~dp0"

REM Este .bat tiene que vivir junto a quiz_volumen.py. Si lo bajaste a otra
REM carpeta, el error de Python es criptico; mejor decirlo claro.
if not exist "%~dp0quiz_volumen.py" (
  echo.
  echo ERROR: no encuentro quiz_volumen.py junto a este archivo.
  echo.
  echo Este .bat esta en:  %~dp0
  echo Muevelo a la carpeta quiz_volumen, donde estan quiz_volumen.py
  echo y motores.py, y vuelve a intentarlo.
  echo.
  pause
  exit /b 1
)

REM Usa Opus 5 con el esquema completo: la configuracion que quedo probada.
REM ~2.8 centavos por pregunta.
REM Para cambiar, edita la linea de abajo:
REM   agrega --barato                  mas barato (~1.6 centavos); deja de
REM                                    transcribir las opciones que leyo
REM   agrega --modelo claude-sonnet-5  mas barato aun, casi igual de bueno
REM   agrega --ver-ocr                 muestra el texto que leyo tesseract
python quiz_volumen.py --motor ocr-claude --hotkey --step 10

echo.
echo El programa termino. Cierra esta ventana o presiona una tecla.
pause >nul
