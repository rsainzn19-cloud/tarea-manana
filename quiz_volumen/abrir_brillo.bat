@echo off
title Quiz Brillo
REM Doble clic para lanzar la version de BRILLO en modo espera (Alt Alt).
REM Comparte el mismo programa que el de volumen; solo cambia --salida.
cd /d "%~dp0"

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

REM A=20%%, B=40%%, C=60%%, D=80%% de brillo. Con --step 1 la A dejaria la
REM pantalla casi apagada, por eso 20.
REM Para cambiar de configuracion, edita la linea de abajo:
REM   --salida ambos                   mueve brillo Y volumen a la vez
REM   --step 25                        cuatro niveles mas separados
REM   agrega --popup mini              muestra tambien un recuadro con la letra
REM   agrega --barato                  ~1.6 centavos por pregunta en vez de 2.8
python quiz_volumen.py --motor ocr-claude --salida brillo --step 20 --hotkey

echo.
echo El programa termino. Cierra esta ventana o presiona una tecla.
pause >nul
