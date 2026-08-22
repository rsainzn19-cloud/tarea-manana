"""
motores.py — las tres formas de contestar la pregunta de una captura.

  claude : manda la imagen a la API de Anthropic (necesita internet y ANTHROPIC_API_KEY)
  local  : modelo de vision corriendo en tu maquina via Ollama (no sale nada a internet)
  ocr    : tesseract lee el texto de la pantalla y un modelo de texto local lo contesta

Todos devuelven el mismo dict:
  {hay_pregunta: bool, pregunta: str, respuesta: "A".."H"|"NINGUNA",
   confianza: float, razon: str}
"""

from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import urllib.error
import urllib.request

LETTERS = "ABCDEFGH"

OLLAMA_HOST = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
MODELO_VISION = "qwen2.5vl:7b"
MODELO_TEXTO = "qwen2.5:7b"
MODELO_CLAUDE = "claude-opus-5"

ESQUEMA = {
    "type": "object",
    "properties": {
        "hay_pregunta": {
            "type": "boolean",
            "description": "true solo si hay una pregunta de opcion multiple visible.",
        },
        "pregunta": {
            "type": "string",
            "description": "El enunciado de la pregunta, resumido en una linea. Vacio si no hay.",
        },
        "respuesta": {
            "type": "string",
            "enum": list(LETTERS) + ["NINGUNA"],
            "description": "La letra de la opcion correcta, o NINGUNA si no hay pregunta.",
        },
        "confianza": {
            "type": "number",
            "description": "Que tan seguro estas, de 0 a 1.",
        },
        "razon": {
            "type": "string",
            "description": "Una frase corta explicando por que.",
        },
    },
    "required": ["hay_pregunta", "pregunta", "respuesta", "confianza", "razon"],
    "additionalProperties": False,
}

_REGLAS = """Busca la pregunta de opcion multiple. Si hay varias, quedate con la
que este mas al centro / mas destacada, o la primera sin contestar.

Responde con la letra de la opcion correcta. Si las opciones estan numeradas
(1, 2, 3) o con vinetas, cuentalas de arriba a abajo y usa A para la primera,
B para la segunda, y asi.

Si no ves ninguna pregunta de opcion multiple, pon hay_pregunta en false y
respuesta en NINGUNA."""

INSTRUCCIONES_IMAGEN = "Estas viendo una captura de pantalla.\n\n" + _REGLAS

INSTRUCCIONES_TEXTO = (
    "Este es el texto que se leyo de una captura de pantalla (puede traer "
    "errores de OCR y basura de la interfaz).\n\n{texto}\n\n" + _REGLAS
)


def _extraer_letra(crudo: str) -> str:
    """'B' / 'b)' / 'opcion C' / '2' -> la letra. Si no se distingue, NINGUNA.

    Es a proposito estricto: preferimos no contestar a inventar una letra a
    partir de una frase suelta del modelo.
    """
    texto = crudo.strip().upper()
    if texto in list(LETTERS) + ["NINGUNA"]:
        return texto
    # letra al principio, como token propio: "B)", "B.", "B - porque..."
    m = re.match(rf"\W*([{LETTERS}])\b", texto)
    if m:
        return m.group(1)
    # una unica letra suelta en toda la frase: "la opcion C", "respuesta: D"
    sueltas = set(re.findall(rf"\b([{LETTERS}])\b", texto))
    if len(sueltas) == 1:
        return sueltas.pop()
    # numerada: "2" -> B
    m = re.fullmatch(r"\W*(\d)\W*", texto)
    if m and 1 <= int(m.group(1)) <= len(LETTERS):
        return LETTERS[int(m.group(1)) - 1]
    return "NINGUNA"


def _normalizar(d: dict) -> dict:
    """Rellena huecos y arregla tipos, porque los modelos locales son creativos."""
    letra = _extraer_letra(str(d.get("respuesta", "")))
    try:
        confianza = float(d.get("confianza", 0.5))
    except (TypeError, ValueError):
        confianza = 0.5
    if letra == "NINGUNA":
        confianza = 0.0
    return {
        "hay_pregunta": bool(d.get("hay_pregunta", letra != "NINGUNA")),
        "pregunta": str(d.get("pregunta", "") or ""),
        "respuesta": letra,
        "confianza": max(0.0, min(1.0, confianza)),
        "razon": str(d.get("razon", "") or ""),
    }


# --------------------------------------------------------------------------
# Ollama (local)
# --------------------------------------------------------------------------

def _abrir(url: str, data: bytes | None = None, timeout: int = 600):
    """urlopen sin proxy — Ollama es local y un proxy configurado lo rompe."""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    req = urllib.request.Request(url, data=data,
                                 headers={"Content-Type": "application/json"})
    return opener.open(req, timeout=timeout)


def modelos_ollama(host: str = OLLAMA_HOST) -> list[str] | None:
    """Lista los modelos instalados, o None si Ollama no esta corriendo."""
    try:
        with _abrir(f"{host}/api/tags", timeout=5) as r:
            datos = json.loads(r.read())
        return [m["name"] for m in datos.get("models", [])]
    except Exception:
        return None


def _chat_ollama(host: str, modelo: str, contenido: str,
                 imagen_b64: str | None = None) -> dict:
    instalados = modelos_ollama(host)
    if instalados is None:
        raise RuntimeError(
            f"Ollama no responde en {host}. Instalalo desde ollama.com y "
            "dejalo corriendo (`ollama serve`)."
        )
    # 'qwen2.5vl:7b' tambien casa con 'qwen2.5vl:7b-instruct-q4_K_M'
    if not any(m == modelo or m.startswith(modelo) for m in instalados):
        raise RuntimeError(
            f"Ollama no tiene el modelo '{modelo}'. Bajalo con:\n"
            f"    ollama pull {modelo}\n"
            f"Instalados ahora mismo: {', '.join(instalados) or '(ninguno)'}"
        )

    mensaje: dict = {"role": "user", "content": contenido}
    if imagen_b64:
        mensaje["images"] = [imagen_b64]

    cuerpo = json.dumps({
        "model": modelo,
        "messages": [mensaje],
        "format": ESQUEMA,          # salida estructurada: obliga al JSON
        "stream": False,
        "options": {"temperature": 0},
    }).encode()

    try:
        with _abrir(f"{host}/api/chat", data=cuerpo) as r:
            datos = json.loads(r.read())
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"Ollama devolvio error {e.code}: {e.read().decode()[:300]}") from e

    texto = datos.get("message", {}).get("content", "")
    try:
        return _normalizar(json.loads(texto))
    except json.JSONDecodeError as e:
        raise RuntimeError(f"El modelo local no devolvio JSON valido: {texto[:300]}") from e


def responder_local(ruta_png: str, modelo: str = MODELO_VISION,
                    host: str = OLLAMA_HOST, **_) -> dict:
    """Modelo de vision local: ve la captura directo, sin OCR."""
    with open(ruta_png, "rb") as f:
        b64 = base64.standard_b64encode(f.read()).decode("utf-8")
    return _chat_ollama(host, modelo, INSTRUCCIONES_IMAGEN, imagen_b64=b64)


# --------------------------------------------------------------------------
# OCR + modelo de texto (local)
# --------------------------------------------------------------------------

def ocr_texto(ruta_png: str) -> str:
    """Saca el texto de la imagen con tesseract."""
    if not shutil.which("tesseract"):
        raise RuntimeError(
            "Falta tesseract.\n"
            "  macOS:   brew install tesseract tesseract-lang\n"
            "  Ubuntu:  sudo apt install tesseract-ocr tesseract-ocr-spa\n"
            "  Windows: https://github.com/UB-Mannheim/tesseract/wiki"
        )
    for idioma in (["-l", "spa+eng"], []):
        r = subprocess.run(["tesseract", ruta_png, "stdout", *idioma],
                           capture_output=True, text=True)
        if r.returncode == 0 and r.stdout.strip():
            return r.stdout
    raise RuntimeError(f"tesseract no leyo nada: {r.stderr.strip()[:200]}")


def responder_ocr(ruta_png: str, modelo: str = MODELO_TEXTO,
                  host: str = OLLAMA_HOST, verbose: bool = False, **_) -> dict:
    """tesseract lee la pantalla y un modelo de texto local contesta."""
    texto = ocr_texto(ruta_png)
    if verbose:
        print("  --- texto leido por OCR ---")
        for linea in texto.strip().splitlines()[:25]:
            print(f"  | {linea}")
        print("  ---------------------------")
    return _chat_ollama(host, modelo, INSTRUCCIONES_TEXTO.format(texto=texto.strip()))


# --------------------------------------------------------------------------
# Claude (API)
# --------------------------------------------------------------------------

def responder_claude(ruta_png: str, modelo: str = MODELO_CLAUDE, **_) -> dict:
    import anthropic

    with open(ruta_png, "rb") as f:
        datos = base64.standard_b64encode(f.read()).decode("utf-8")

    respuesta = anthropic.Anthropic().messages.create(
        model=modelo,
        max_tokens=16000,
        messages=[{
            "role": "user",
            "content": [
                {"type": "image",
                 "source": {"type": "base64", "media_type": "image/png", "data": datos}},
                {"type": "text", "text": INSTRUCCIONES_IMAGEN},
            ],
        }],
        output_config={"format": {"type": "json_schema", "schema": ESQUEMA}},
    )
    texto = next(b.text for b in respuesta.content if b.type == "text")
    return _normalizar(json.loads(texto))


MOTORES = {
    "claude": (responder_claude, MODELO_CLAUDE),
    "local": (responder_local, MODELO_VISION),
    "ocr": (responder_ocr, MODELO_TEXTO),
}


def elegir_motor(nombre: str, host: str = OLLAMA_HOST) -> str:
    """Resuelve 'auto': local si Ollama esta corriendo, si no Claude."""
    if nombre != "auto":
        return nombre
    if modelos_ollama(host) is not None:
        return "local"
    if os.environ.get("ANTHROPIC_API_KEY"):
        return "claude"
    raise RuntimeError(
        "No hay motor disponible: Ollama no responde y no hay ANTHROPIC_API_KEY.\n"
        "Instala Ollama (ollama.com) o exporta tu API key."
    )
