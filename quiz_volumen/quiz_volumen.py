#!/usr/bin/env python3
"""
quiz_volumen.py — prueba de concepto.

1. Toma una captura de la pantalla.
2. La analiza para identificar la pregunta de opcion multiple. Con --motor
   local u ocr, el analisis pasa entero en tu maquina (Ollama); con --motor
   claude usa la API de Anthropic.
3. Traduce la respuesta a un numero (A=1, B=2, C=3, ...) y pone el volumen
   del sistema en ese valor.

Uso rapido:
    export ANTHROPIC_API_KEY=sk-ant-...
    python3 quiz_volumen.py                 # una sola vez
    python3 quiz_volumen.py --watch 5       # revisa cada 5 segundos
    python3 quiz_volumen.py --step 10       # A=10%, B=20%, C=30%  (mas visible)
    python3 quiz_volumen.py --motor local   # todo local, sin internet
    python3 quiz_volumen.py --fake-answer C # prueba solo el control de volumen

Funciona en macOS, Windows y Linux (ver README.md para las dependencias).
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time

import motores
from motores import LETTERS, MOTORES, OLLAMA_HOST

SISTEMA = platform.system()  # 'Darwin', 'Windows', 'Linux'


# --------------------------------------------------------------------------
# 1. Captura de pantalla
# --------------------------------------------------------------------------

def capturar(destino: str, monitor: int = 1, region: tuple | None = None) -> str:
    """Guarda un PNG de la pantalla en `destino` y devuelve la ruta."""
    try:
        import mss
        import mss.tools

        with mss.mss() as sct:
            if region:
                x, y, w, h = region
                area = {"left": x, "top": y, "width": w, "height": h}
            else:
                # monitors[0] = todas las pantallas juntas; [1] = la principal
                area = sct.monitors[monitor]
            shot = sct.grab(area)
            mss.tools.to_png(shot.rgb, shot.size, output=destino)
        return destino
    except ImportError:
        pass  # sin mss: probamos las herramientas del sistema
    except Exception as e:
        raise RuntimeError(f"mss no pudo capturar la pantalla: {e}") from e

    if region:
        raise RuntimeError("--region necesita el paquete mss: pip install mss")

    if SISTEMA == "Darwin":
        # -x = sin sonido de obturador
        subprocess.run(["screencapture", "-x", destino], check=True)
        return destino

    if SISTEMA == "Linux":
        for cmd in (
            ["gnome-screenshot", "-f", destino],
            ["spectacle", "-b", "-n", "-o", destino],
            ["scrot", "-o", destino],
            ["grim", destino],
            ["import", "-window", "root", destino],
        ):
            if shutil.which(cmd[0]):
                subprocess.run(cmd, check=True)
                return destino

    if SISTEMA == "Windows":
        try:
            from PIL import ImageGrab

            ImageGrab.grab(all_screens=True).save(destino, "PNG")
            return destino
        except ImportError:
            pass

    raise RuntimeError(
        "No encontre como capturar la pantalla. Instala mss:  pip install mss"
    )


# --------------------------------------------------------------------------
# 2. Control de volumen
# --------------------------------------------------------------------------

def _run(cmd: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True)


def leer_volumen() -> int | None:
    """Volumen actual en 0-100, o None si no se pudo leer."""
    try:
        if SISTEMA == "Darwin":
            r = _run(["osascript", "-e", "output volume of (get volume settings)"])
            return int(r.stdout.strip()) if r.returncode == 0 else None

        if SISTEMA == "Linux":
            if shutil.which("wpctl"):
                r = _run(["wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@"])
                if r.returncode == 0:
                    # "Volume: 0.45"
                    return round(float(r.stdout.split()[1]) * 100)
            if shutil.which("pactl"):
                r = _run(["pactl", "get-sink-volume", "@DEFAULT_SINK@"])
                if r.returncode == 0:
                    for parte in r.stdout.split():
                        if parte.endswith("%"):
                            return int(parte.rstrip("%"))
            return None

        if SISTEMA == "Windows":
            vol = _pycaw()
            if vol is not None:
                return round(vol.GetMasterVolumeLevelScalar() * 100)
            return None
    except Exception:
        return None
    return None


def _pycaw():
    """Devuelve la interfaz IAudioEndpointVolume de Windows, o None."""
    try:
        from ctypes import POINTER, cast

        from comtypes import CLSCTX_ALL
        from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume

        altavoces = AudioUtilities.GetSpeakers()
        interfaz = altavoces.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        return cast(interfaz, POINTER(IAudioEndpointVolume))
    except Exception:
        return None


def poner_volumen(pct: int) -> None:
    """Pone el volumen del sistema en `pct` (0-100). Lanza RuntimeError si falla."""
    pct = max(0, min(100, int(pct)))

    if SISTEMA == "Darwin":
        r = _run(["osascript", "-e", f"set volume output volume {pct}"])
        if r.returncode != 0:
            raise RuntimeError(f"osascript fallo: {r.stderr.strip()}")
        return

    if SISTEMA == "Linux":
        if shutil.which("wpctl"):
            r = _run(["wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", f"{pct}%"])
            if r.returncode == 0:
                return
        if shutil.which("pactl"):
            r = _run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{pct}%"])
            if r.returncode == 0:
                return
        if shutil.which("amixer"):
            r = _run(["amixer", "-q", "sset", "Master", f"{pct}%"])
            if r.returncode == 0:
                return
        raise RuntimeError(
            "No pude cambiar el volumen: instala pipewire (wpctl), "
            "pulseaudio-utils (pactl) o alsa-utils (amixer)."
        )

    if SISTEMA == "Windows":
        vol = _pycaw()
        if vol is not None:
            vol.SetMasterVolumeLevelScalar(pct / 100.0, None)
            return
        if shutil.which("nircmd"):
            r = _run(["nircmd", "setsysvolume", str(round(pct * 65535 / 100))])
            if r.returncode == 0:
                return
        raise RuntimeError(
            "No pude cambiar el volumen en Windows: pip install pycaw comtypes"
        )

    raise RuntimeError(f"Sistema no soportado: {SISTEMA}")


# --------------------------------------------------------------------------
# 3. Pegamento
# --------------------------------------------------------------------------

def letra_a_volumen(letra: str, step: int) -> int:
    """A -> 1*step, B -> 2*step, ... (recortado a 0-100)."""
    idx = LETTERS.index(letra.upper())
    return max(0, min(100, (idx + 1) * step))


def una_ronda(args, tmpdir: str, visto: set[str]) -> bool:
    """Hace un ciclo completo. Devuelve True si cambio el volumen."""
    if args.fake_answer:
        r = {
            "hay_pregunta": True,
            "pregunta": "(modo de prueba, sin llamar a la API)",
            "respuesta": args.fake_answer.upper(),
            "confianza": 1.0,
            "razon": "respuesta forzada con --fake-answer",
        }
    else:
        if args.image:
            png = args.image
        else:
            png = capturar(
                os.path.join(tmpdir, "captura.png"),
                monitor=args.monitor,
                region=args.region,
            )
            if args.save_shot:
                shutil.copy(png, args.save_shot)

        # En modo --watch, no volvemos a preguntar si la pantalla no cambio.
        with open(png, "rb") as f:
            firma = hashlib.sha256(f.read()).hexdigest()
        if firma in visto:
            print("  (la pantalla no cambio, no pregunto)")
            return False
        visto.add(firma)

        funcion, _ = MOTORES[args.motor]
        print(f"  analizando con el motor '{args.motor}' ({args.modelo})...")
        r = funcion(png, modelo=args.modelo, host=args.ollama_host,
                    verbose=args.ver_ocr)

    print(f"  pregunta : {r['pregunta'] or '-'}")
    print(f"  respuesta: {r['respuesta']}  (confianza {r['confianza']:.2f})")
    print(f"  razon    : {r['razon']}")

    if not r["hay_pregunta"] or r["respuesta"] == "NINGUNA":
        print("  -> no hay pregunta en pantalla, no toco el volumen")
        return False

    if r["confianza"] < args.min_confianza:
        print(f"  -> confianza por debajo de {args.min_confianza}, no toco el volumen")
        return False

    vol = letra_a_volumen(r["respuesta"], args.step)
    print(f"  -> {r['respuesta']} = {LETTERS.index(r['respuesta']) + 1} -> volumen {vol}%")

    if args.dry_run:
        print("  (--dry-run: no cambio nada)")
        return False

    anterior = leer_volumen()
    poner_volumen(vol)
    print("  volumen cambiado")

    if args.hold:
        time.sleep(args.hold)
        if anterior is not None:
            poner_volumen(anterior)
            print(f"  volumen restaurado a {anterior}%")
    return True


def parse_region(texto: str) -> tuple:
    partes = [int(p) for p in texto.split(",")]
    if len(partes) != 4:
        raise argparse.ArgumentTypeError("la region va como x,y,ancho,alto")
    return tuple(partes)


def main() -> int:
    p = argparse.ArgumentParser(
        description="Lee la pregunta de la pantalla y codifica la respuesta en el volumen.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("--watch", type=float, metavar="SEG",
                   help="repetir cada SEG segundos hasta Ctrl-C")
    p.add_argument("--step", type=int, default=1,
                   help="volumen por letra: A=1*step, B=2*step... (default 1; "
                        "usa 10 para que se note)")
    p.add_argument("--hold", type=float, metavar="SEG",
                   help="despues de SEG segundos, regresar el volumen al valor anterior")
    p.add_argument("--min-confianza", type=float, default=0.0, metavar="0-1",
                   help="no cambiar el volumen si el modelo esta menos seguro que esto")
    p.add_argument("--monitor", type=int, default=1,
                   help="que pantalla capturar (1 = la principal, 0 = todas)")
    p.add_argument("--region", type=parse_region, metavar="X,Y,W,H",
                   help="capturar solo un rectangulo (necesita mss)")
    p.add_argument("--image", metavar="RUTA",
                   help="usar este PNG en vez de capturar la pantalla")
    p.add_argument("--save-shot", metavar="RUTA",
                   help="guardar una copia de la captura para revisarla")
    p.add_argument("--motor", choices=["auto", "local", "ocr", "claude"], default="auto",
                   help="quien contesta: local = modelo de vision en tu maquina (Ollama), "
                        "ocr = tesseract + modelo de texto local, claude = la API. "
                        "auto (default) usa local si Ollama esta corriendo.")
    p.add_argument("--modelo", help="modelo concreto a usar (default segun el motor)")
    p.add_argument("--ollama-host", default=OLLAMA_HOST,
                   help=f"donde escucha Ollama (default {OLLAMA_HOST})")
    p.add_argument("--ver-ocr", action="store_true",
                   help="imprimir el texto que leyo el OCR (para depurar --motor ocr)")
    p.add_argument("--dry-run", action="store_true",
                   help="hacer todo menos cambiar el volumen")
    p.add_argument("--fake-answer", metavar="LETRA",
                   help="saltarse el analisis y fingir esta respuesta (para probar el volumen)")
    args = p.parse_args()

    if args.fake_answer and args.fake_answer.upper() not in LETTERS:
        print(f"--fake-answer tiene que ser una letra de {LETTERS}", file=sys.stderr)
        return 2

    if not args.fake_answer:
        try:
            args.motor = motores.elegir_motor(args.motor, args.ollama_host)
        except RuntimeError as e:
            print(e, file=sys.stderr)
            return 2
        if args.modelo is None:
            args.modelo = MOTORES[args.motor][1]
        if args.motor == "claude" and not os.environ.get("ANTHROPIC_API_KEY"):
            print("Falta ANTHROPIC_API_KEY. Exportala primero:", file=sys.stderr)
            print("  export ANTHROPIC_API_KEY=sk-ant-...", file=sys.stderr)
            print("O usa --motor local para no salir a internet.", file=sys.stderr)
            return 2

    print(f"sistema: {SISTEMA} | volumen actual: {leer_volumen()}")
    if not args.fake_answer:
        destino = "tu maquina" if args.motor in ("local", "ocr") else "la API de Anthropic"
        print(f"motor: {args.motor} ({args.modelo}) -> corre en {destino}")

    visto: set[str] = set()
    with tempfile.TemporaryDirectory() as tmpdir:
        try:
            while True:
                print(f"\n[{time.strftime('%H:%M:%S')}]")
                try:
                    una_ronda(args, tmpdir, visto)
                except Exception as e:
                    print(f"  error: {e}", file=sys.stderr)
                    if not args.watch:
                        return 1
                if not args.watch:
                    return 0
                time.sleep(args.watch)
        except KeyboardInterrupt:
            print("\nlisto.")
            return 0


if __name__ == "__main__":
    sys.exit(main())
