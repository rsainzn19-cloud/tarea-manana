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
from motores import LETTERS, MOTORES, OLLAMA_HOST, SOLO_TEXTO

SISTEMA = platform.system()  # 'Darwin', 'Windows', 'Linux'


# --------------------------------------------------------------------------
# 1. Captura de pantalla
# --------------------------------------------------------------------------

def capturar(destino: str, monitor: int = 1, region: tuple | None = None) -> str:
    """Guarda un PNG de la pantalla en `destino` y devuelve la ruta."""
    try:
        import mss
        import mss.tools

        crear = getattr(mss, "MSS", None) or mss.mss  # mss.mss quedo deprecado
        with crear() as sct:
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


# Codigos de tecla de Windows para --tecla
_TECLAS = {
    "alt": 0x12, "ctrl": 0x11, "shift": 0x10,
    "f8": 0x77, "f9": 0x78, "f10": 0x79, "f11": 0x7A, "f12": 0x7B,
}
# Letras: los codigos de tecla de A-Z son 0x41-0x5A, en orden.
_TECLAS.update({chr(c).lower(): c for c in range(0x41, 0x5B)})


def esperar_tecla(tecla: str, taps: int, ventana: float = 0.6) -> None:
    """Se bloquea hasta que tocas `tecla` `taps` veces dentro de `ventana` seg.

    Lee el teclado global, asi que funciona aunque la terminal no tenga el
    foco: puedes estar en la ventana de la pregunta.
    """
    if SISTEMA != "Windows":
        raise RuntimeError(
            "--hotkey por ahora solo esta implementado en Windows. "
            "En macOS/Linux usa --watch."
        )
    import ctypes

    vk = _TECLAS[tecla]
    user32 = ctypes.windll.user32
    abajo_antes = False
    golpes: list[float] = []

    while True:
        abajo = bool(user32.GetAsyncKeyState(vk) & 0x8000)
        ahora = time.monotonic()
        if abajo and not abajo_antes:  # flanco: se acaba de presionar
            golpes = [t for t in golpes if ahora - t <= ventana]
            golpes.append(ahora)
            if len(golpes) >= taps:
                # esperar a que suelte, para no disparar dos veces
                while user32.GetAsyncKeyState(vk) & 0x8000:
                    time.sleep(0.02)
                return
        abajo_antes = abajo
        time.sleep(0.02)


def mostrar_popup(r: dict, vol: int | None, segundos: float,
                  tamano: str = "completo") -> None:
    """Recuadro con la respuesta, con la pinta de un dialogo de sistema.

    Compacto, centrado y claro de un vistazo, como los avisos de accesibilidad
    de Windows: esa forma funciona porque se lee sin buscarla. Lleva su propio
    nombre y su propio texto.
    """
    try:
        import tkinter as tk
        from tkinter import font as tkfont
    except ImportError:
        print("  (sin tkinter no puedo mostrar el popup; instala Python con "
              "tcl/tk o quita --popup)")
        return

    FONDO, BORDE = "#fbfbfb", "#d6d6d6"
    TITULO, CUERPO, TENUE = "#1a1a1a", "#333333", "#6b6b6b"
    ACENTO = "#0067c0"  # el azul de acento de Windows

    v = tk.Tk()
    v.title("Quiz Volumen")
    v.configure(bg=BORDE)
    v.attributes("-topmost", True)
    v.resizable(False, False)

    def fuente(tam, peso="normal"):
        for familia in ("Segoe UI Variable Display", "Segoe UI", "Helvetica"):
            if familia in tkfont.families():
                return (familia, tam, peso)
        return ("TkDefaultFont", tam, peso)

    if tamano == "mini":
        # Solo la letra y que tan seguro esta: para mirar de reojo y seguir.
        mini = tk.Frame(v, bg=FONDO, padx=18, pady=10)
        mini.pack(padx=1, pady=1)
        tk.Label(mini, text=r["respuesta"], bg=FONDO, fg=ACENTO,
                 font=fuente(34, "bold")).pack(side="left")
        tk.Label(mini, text=f"{r['confianza']:.0%}", bg=FONDO, fg=TENUE,
                 font=fuente(9)).pack(side="left", padx=(10, 0), anchor="s",
                                      pady=(0, 8))
        cerrar = lambda *_: v.destroy()
        v.update_idletasks()
        x = (v.winfo_screenwidth() - v.winfo_width()) // 2
        y = v.winfo_screenheight() // 6
        v.geometry(f"+{x}+{y}")
        v.bind("<Escape>", cerrar)
        v.bind("<Button-1>", cerrar)
        mini.bind("<Button-1>", cerrar)
        if segundos:
            v.after(int(segundos * 1000), cerrar)
        v.mainloop()
        return

    marco = tk.Frame(v, bg=FONDO, padx=24, pady=20)
    marco.pack(padx=1, pady=1)

    tk.Label(marco, text="Quiz Volumen", bg=FONDO, fg=TITULO,
             font=fuente(14, "bold")).pack(anchor="w")
    tk.Label(marco, text="Respuesta sugerida para la pregunta en pantalla",
             bg=FONDO, fg=TENUE, font=fuente(9)).pack(anchor="w", pady=(2, 14))

    fila = tk.Frame(marco, bg=FONDO)
    fila.pack(anchor="w", fill="x")
    tk.Label(fila, text=r["respuesta"], bg=FONDO, fg=ACENTO,
             font=fuente(40, "bold")).pack(side="left")
    lado = tk.Frame(fila, bg=FONDO)
    lado.pack(side="left", padx=(16, 0), anchor="s", pady=(0, 8))
    tk.Label(lado, text=f"confianza {r['confianza']:.0%}", bg=FONDO, fg=CUERPO,
             font=fuente(10)).pack(anchor="w")
    if vol is not None:
        tk.Label(lado, text=f"volumen {vol}%", bg=FONDO, fg=TENUE,
                 font=fuente(9)).pack(anchor="w")

    if r.get("pregunta"):
        tk.Label(marco, text=r["pregunta"], bg=FONDO, fg=CUERPO, justify="left",
                 wraplength=380, font=fuente(10)).pack(anchor="w", pady=(14, 0))
    if r.get("razon"):
        tk.Label(marco, text=r["razon"], bg=FONDO, fg=TENUE, justify="left",
                 wraplength=380, font=fuente(9)).pack(anchor="w", pady=(6, 0))

    cerrar = lambda *_: v.destroy()
    pie = tk.Frame(marco, bg=FONDO)
    pie.pack(fill="x", pady=(18, 0))
    tk.Button(pie, text="Cerrar", command=cerrar, font=fuente(9),
              bg="#fdfdfd", fg=CUERPO, activebackground="#f0f0f0",
              relief="solid", bd=1, padx=18, pady=4,
              highlightthickness=0).pack(side="right")

    # Centrado, como los avisos del sistema
    v.update_idletasks()
    x = (v.winfo_screenwidth() - v.winfo_width()) // 2
    y = (v.winfo_screenheight() - v.winfo_height()) // 3
    v.geometry(f"+{x}+{y}")

    v.bind("<Escape>", cerrar)
    v.bind("<Return>", cerrar)
    if segundos:
        v.after(int(segundos * 1000), cerrar)
    v.mainloop()


def encoger(origen: str, destino: str, max_ancho: int) -> str:
    """Reduce la captura a `max_ancho` px de ancho. Devuelve la ruta a usar.

    Una captura de pantalla completa ahoga al codificador de vision de los
    modelos locales (el proceso se cae con un 500). Encogerla baja muchisimo
    la memoria y el tiempo, y el texto sigue siendo legible.
    """
    if not max_ancho:
        return origen
    try:
        from PIL import Image
    except ImportError:
        print("  aviso: sin Pillow no puedo encoger la captura "
              "(pip install pillow). Mando la original.")
        return origen

    im = Image.open(origen)
    if im.width <= max_ancho:
        im.close()
        return origen
    alto = round(im.height * max_ancho / im.width)
    chica = im.convert("RGB").resize((max_ancho, alto), Image.LANCZOS)
    print(f"  encogiendo captura {im.width}x{im.height} -> {max_ancho}x{alto}")
    im.close()
    chica.save(destino)
    return destino


# --------------------------------------------------------------------------
# 2. Control de volumen
# --------------------------------------------------------------------------

def _run(cmd: list[str]) -> subprocess.CompletedProcess:
    # encoding explicito: sin el, Python usa la codificacion regional y un
    # acento en la salida tira UnicodeDecodeError.
    return subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


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


_ERROR_PYCAW = None  # se guarda el motivo real por si hay que explicarlo


def _pycaw():
    """Interfaz IAudioEndpointVolume de Windows, o None (motivo en _ERROR_PYCAW)."""
    global _ERROR_PYCAW
    try:
        from ctypes import POINTER, cast

        from comtypes import CLSCTX_ALL
        from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume

        altavoces = AudioUtilities.GetSpeakers()
        interfaz = altavoces.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        return cast(interfaz, POINTER(IAudioEndpointVolume))
    except Exception as e:
        _ERROR_PYCAW = f"{type(e).__name__}: {e}"
        return None


# Windows: teclas multimedia de volumen. No necesita instalar nada, pero se
# mueve en pasos de 2% (los 50 pasos del control de Windows), asi que el
# volumen final se redondea al par mas cercano.
_VK_VOLUME_DOWN = 0xAE
_VK_VOLUME_UP = 0xAF
_KEYEVENTF_KEYUP = 0x0002
_PASO_TECLA = 2


def _volumen_por_teclas(pct: int) -> None:
    import ctypes

    user32 = ctypes.windll.user32

    def pulsar(vk: int) -> None:
        user32.keybd_event(vk, 0, 0, 0)
        user32.keybd_event(vk, 0, _KEYEVENTF_KEYUP, 0)

    # No hay forma de leer el nivel actual por esta via, asi que bajamos a 0
    # y subimos lo que haga falta.
    for _ in range(100 // _PASO_TECLA):
        pulsar(_VK_VOLUME_DOWN)
    for _ in range(round(pct / _PASO_TECLA)):
        pulsar(_VK_VOLUME_UP)


def poner_volumen(pct: int) -> str:
    """Pone el volumen del sistema en `pct` (0-100).

    Devuelve como lo logro. Lanza RuntimeError si no pudo.
    """
    pct = max(0, min(100, int(pct)))

    if SISTEMA == "Darwin":
        r = _run(["osascript", "-e", f"set volume output volume {pct}"])
        if r.returncode != 0:
            raise RuntimeError(f"osascript fallo: {r.stderr.strip()}")
        return "osascript"

    if SISTEMA == "Linux":
        if shutil.which("wpctl"):
            r = _run(["wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", f"{pct}%"])
            if r.returncode == 0:
                return "wpctl"
        if shutil.which("pactl"):
            r = _run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{pct}%"])
            if r.returncode == 0:
                return "pactl"
        if shutil.which("amixer"):
            r = _run(["amixer", "-q", "sset", "Master", f"{pct}%"])
            if r.returncode == 0:
                return "amixer"
        raise RuntimeError(
            "No pude cambiar el volumen: instala pipewire (wpctl), "
            "pulseaudio-utils (pactl) o alsa-utils (amixer)."
        )

    if SISTEMA == "Windows":
        vol = _pycaw()
        if vol is not None:
            vol.SetMasterVolumeLevelScalar(pct / 100.0, None)
            return "pycaw"
        if shutil.which("nircmd"):
            r = _run(["nircmd", "setsysvolume", str(round(pct * 65535 / 100))])
            if r.returncode == 0:
                return "nircmd"
        try:
            _volumen_por_teclas(pct)
            return f"teclas multimedia (pycaw no cargo -> {_ERROR_PYCAW})"
        except Exception as e:
            raise RuntimeError(
                f"No pude cambiar el volumen en Windows.\n"
                f"  pycaw: {_ERROR_PYCAW}\n"
                f"  teclas multimedia: {type(e).__name__}: {e}\n"
                f"Prueba:  pip install --upgrade pycaw comtypes"
            ) from e

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
            "opciones": [],
            "respuesta": args.fake_answer.upper(),
            "confianza": 1.0,
            "razon": "respuesta forzada con --fake-answer",
        }
    else:
        if args.image:
            png = args.image
        else:
            if args.delay:
                print(f"  capturando en {args.delay:g}s — cambia a la ventana "
                      f"de la pregunta...")
                time.sleep(args.delay)
            png = capturar(
                os.path.join(tmpdir, "captura.png"),
                monitor=args.monitor,
                region=args.region,
            )
            if args.save_shot:
                shutil.copy(png, args.save_shot)

        # Encoger es para que la imagen quepa en el codificador de vision del
        # modelo. En modo ocr la imagen nunca llega al modelo (solo el texto),
        # asi que reducirla solo le quita resolucion a tesseract.
        if args.motor not in SOLO_TEXTO:
            png = encoger(png, os.path.join(tmpdir, "chica.png"), args.max_ancho)

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
                    verbose=args.ver_ocr, num_ctx=args.num_ctx,
                    tesseract=args.tesseract, pensar=args.pensar,
                    barato=args.barato)

    print(f"  pregunta : {r['pregunta'] or '-'}")
    for i, opcion in enumerate(r.get("opciones") or []):
        print(f"     {LETTERS[i] if i < len(LETTERS) else '?'}) {opcion}")
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
        if args.popup:
            mostrar_popup(r, None, args.popup_seg, args.popup)
        return False

    anterior = leer_volumen()
    como = poner_volumen(vol)
    print(f"  volumen cambiado (via {como})")

    if args.popup:
        mostrar_popup(r, vol, args.popup_seg, args.popup)

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
    p.add_argument("--hotkey", action="store_true",
                   help="quedarse esperando en segundo plano y capturar cada vez "
                        "que toques la tecla (ver --tecla y --taps). Solo Windows.")
    p.add_argument("--tecla", choices=sorted(_TECLAS), default="alt",
                   metavar="TECLA",
                   help="que tecla dispara la captura con --hotkey: una letra "
                        "(a-z), alt, ctrl, shift o f8-f12 (default alt)")
    p.add_argument("--popup", nargs="?", const="completo", default=None,
                   choices=["completo", "mini"],
                   help="mostrar la respuesta en un recuadro en pantalla. "
                        "'completo' (default) trae pregunta y razonamiento; "
                        "'mini' solo la letra, para mirar de reojo")
    p.add_argument("--popup-seg", type=float, default=8, metavar="SEG",
                   help="segundos que dura el recuadro antes de cerrarse solo "
                        "(default 8; 0 = hasta que le des clic)")
    p.add_argument("--taps", type=int, default=2, metavar="N",
                   help="cuantos toques seguidos hacen falta (default 2)")
    p.add_argument("--max-ancho", type=int, default=1280, metavar="PX",
                   help="encoger la captura a este ancho antes de analizarla "
                        "(default 1280; 0 = no encoger). Necesita Pillow.")
    p.add_argument("--delay", type=float, default=0, metavar="SEG",
                   help="esperar SEG segundos antes de capturar, para darte "
                        "tiempo de cambiar de ventana")
    p.add_argument("--monitor", type=int, default=1,
                   help="que pantalla capturar (1 = la principal, 0 = todas)")
    p.add_argument("--region", type=parse_region, metavar="X,Y,W,H",
                   help="capturar solo un rectangulo (necesita mss)")
    p.add_argument("--image", metavar="RUTA",
                   help="usar este PNG en vez de capturar la pantalla")
    p.add_argument("--save-shot", metavar="RUTA",
                   help="guardar una copia de la captura para revisarla")
    p.add_argument("--motor",
                   choices=["auto", "local", "ocr", "ocr-claude", "claude"],
                   default="auto",
                   help="quien contesta: local = modelo de vision en tu maquina "
                        "(Ollama); ocr = tesseract + modelo de texto local; "
                        "ocr-claude = tesseract lee aqui y solo el TEXTO va a la "
                        "API; claude = la captura completa va a la API. "
                        "auto (default) usa local si Ollama esta corriendo.")
    p.add_argument("--modelo", help="modelo concreto a usar (default segun el motor)")
    p.add_argument("--ollama-host", default=OLLAMA_HOST,
                   help=f"donde escucha Ollama (default {OLLAMA_HOST})")
    p.add_argument("--num-ctx", type=int, default=motores.NUM_CTX, metavar="N",
                   help=f"tokens de contexto para el modelo local (default "
                        f"{motores.NUM_CTX}). Subelo si la captura no cabe.")
    p.add_argument("--barato", action="store_true",
                   help="recortar lo que el modelo escribe (sin transcribir las "
                        "opciones, razon de una linea). Baja bastante el costo "
                        "en los motores de API.")
    p.add_argument("--pensar", nargs="?", const=True, default=None, metavar="NIVEL",
                   help="dejar que el modelo razone antes de rellenar la respuesta "
                        "(solo modelos de razonamiento, p.ej. qwen3). Acepta un "
                        "nivel opcional: low, medium, high, max")
    p.add_argument("--tesseract", metavar="RUTA",
                   help="ruta al tesseract.exe, si no esta en el PATH ni en "
                        "las carpetas de siempre")
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
        if args.motor in ("claude", "ocr-claude") and not os.environ.get("ANTHROPIC_API_KEY"):
            print("Falta ANTHROPIC_API_KEY. Exportala primero:", file=sys.stderr)
            print("  export ANTHROPIC_API_KEY=sk-ant-...", file=sys.stderr)
            print("O usa --motor local para no salir a internet.", file=sys.stderr)
            return 2

    print(f"sistema: {SISTEMA} | volumen actual: {leer_volumen()}")
    if not args.fake_answer:
        destino = {
            "local": "tu maquina",
            "ocr": "tu maquina",
            "ocr-claude": "tu maquina lee, solo el texto sale a la API",
            "claude": "la API de Anthropic",
        }[args.motor]
        print(f"motor: {args.motor} ({args.modelo}) -> {destino}")

    visto: set[str] = set()
    with tempfile.TemporaryDirectory() as tmpdir:
        try:
            while True:
                if args.hotkey:
                    print(f"\nesperando: toca {args.tecla.upper()} {args.taps} veces "
                          f"seguidas para capturar  (Ctrl-C para salir)")
                    try:
                        esperar_tecla(args.tecla, args.taps)
                    except RuntimeError as e:
                        print(e, file=sys.stderr)
                        return 2
                    # un disparo explicito siempre analiza, aunque la pantalla
                    # sea identica a la vez pasada
                    visto.clear()

                print(f"\n[{time.strftime('%H:%M:%S')}]")
                try:
                    una_ronda(args, tmpdir, visto)
                except Exception as e:
                    # str(e) vacio (p.ej. StopIteration) dejaba "error:" pelon
                    print(f"  error: {e or type(e).__name__}", file=sys.stderr)
                    if not (args.watch or args.hotkey):
                        return 1
                if not (args.watch or args.hotkey):
                    return 0
                if args.watch and not args.hotkey:
                    time.sleep(args.watch)
        except KeyboardInterrupt:
            print("\nlisto.")
            return 0


if __name__ == "__main__":
    sys.exit(main())
