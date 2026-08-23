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

# Ollama da 4096 tokens de contexto por defecto y una captura de pantalla sola
# se come mas que eso. 8192 alcanza para 1080p; una pantalla 4K puede pedir mas.
NUM_CTX = 8192

# USD por millon de tokens (entrada, salida). Aproximado, para el estimado que
# se imprime; la factura real la manda Anthropic.
PRECIOS = {
    "claude-opus-5": (5.0, 25.0),
    "claude-sonnet-5": (3.0, 15.0),
    "claude-haiku-4-5": (1.0, 5.0),
}

ESQUEMA = {
    "type": "object",
    # OJO: el orden importa. La salida estructurada se genera campo por campo
    # en este orden, asi que la letra va AL FINAL: primero el modelo transcribe
    # lo que ve y razona, y solo entonces se compromete con una respuesta. Con
    # 'respuesta' arriba, el modelo adivinaba y despues se justificaba.
    "properties": {
        "hay_pregunta": {
            "type": "boolean",
            "description": "true solo si hay una pregunta de opcion multiple visible.",
        },
        "pregunta": {
            "type": "string",
            "description": "Transcribe el enunciado tal como aparece en pantalla.",
        },
        "opciones": {
            "type": "array",
            "items": {"type": "string"},
            "description": "Transcribe cada opcion, en orden, empezando por la A.",
        },
        "razon": {
            "type": "string",
            "description": (
                "Piensa aqui antes de contestar. Repasa las opciones una por una "
                "y di por que cada una sirve o no. Este campo se escribe ANTES "
                "que la respuesta: usalo para razonar, no para justificar."
            ),
        },
        "respuesta": {
            "type": "string",
            "enum": list(LETTERS) + ["NINGUNA"],
            "description": "La letra de la opcion correcta, segun lo que acabas de razonar.",
        },
        "confianza": {
            "type": "number",
            "description": "Que tan seguro estas, de 0 a 1. Se honesto: si dudas, bajalo.",
        },
    },
    "required": ["hay_pregunta", "pregunta", "opciones", "razon", "respuesta",
                 "confianza"],
    "additionalProperties": False,
}

def esquema(barato: bool = False) -> dict:
    """El esquema de salida. En modo barato se recorta lo que el modelo escribe.

    Los tokens de salida cuestan 5x los de entrada, y transcribir las opciones
    mas razonar largo es justo lo que infla la cuenta. El modo barato quita la
    transcripcion y pide una razon de una linea; se sigue razonando ANTES de
    dar la letra, que es lo que de verdad sostiene el acierto.
    """
    e = json.loads(json.dumps(ESQUEMA))  # copia
    if barato:
        del e["properties"]["opciones"]
        e["required"] = [c for c in e["required"] if c != "opciones"]
        e["properties"]["pregunta"]["description"] = (
            "El enunciado en pocas palabras, solo para identificarla."
        )
        e["properties"]["razon"]["description"] = (
            "Una sola frase corta con el porque. Se escribe ANTES que la "
            "respuesta: piensa aqui, pero se breve."
        )
    return e


_CABEZA = """Busca la pregunta de opcion multiple. Si hay varias, quedate con la
que este mas al centro / mas destacada, o la primera sin contestar.

Trabaja en este orden, sin saltarte pasos:

"""

_PASOS_COMPLETO = """1. Transcribe el enunciado en 'pregunta', tal como aparece.
2. Transcribe cada opcion en 'opciones', en orden, empezando por la A.
3. En 'razon', repasa las opciones una por una y descarta las que no sirven.
   Piensa aqui de verdad — es tu unico espacio para hacerlo.
4. Hasta entonces, elige la letra en 'respuesta'."""

# En modo barato NO existe el campo 'opciones': pedirlo aqui seria mandar al
# modelo a rellenar algo que el esquema no acepta.
_PASOS_BARATO = """1. Pon el enunciado en 'pregunta', en pocas palabras.
2. En 'razon', repasa las opciones una por una y descarta las que no sirven.
   Piensa aqui de verdad — es tu unico espacio para hacerlo, pero se breve:
   una sola frase.
3. Hasta entonces, elige la letra en 'respuesta'."""

_COLA = """

Si las opciones estan numeradas (1, 2, 3) o con vinetas, cuentalas de arriba a
abajo: A para la primera, B para la segunda, y asi.

Si no ves ninguna pregunta de opcion multiple, pon hay_pregunta en false y
respuesta en NINGUNA."""


def reglas(barato: bool = False) -> str:
    return _CABEZA + (_PASOS_BARATO if barato else _PASOS_COMPLETO) + _COLA


def instrucciones_imagen(barato: bool = False) -> str:
    return "Estas viendo una captura de pantalla.\n\n" + reglas(barato)


def instrucciones_texto(texto: str, barato: bool = False) -> str:
    return (
        "Este es el texto que se leyo de una captura de pantalla (puede traer "
        "errores de OCR y basura de la interfaz).\n\n"
        f"{texto}\n\n" + reglas(barato)
    )


def _normalizar(d: dict) -> dict:
    """Rellena huecos y arregla tipos, porque los modelos locales son creativos."""
    letra = _extraer_letra(str(d.get("respuesta", "")))
    try:
        confianza = float(d.get("confianza", 0.5))
    except (TypeError, ValueError):
        confianza = 0.5
    if letra == "NINGUNA":
        confianza = 0.0
    opciones = d.get("opciones") or []
    if not isinstance(opciones, list):
        opciones = [str(opciones)]
    return {
        "hay_pregunta": bool(d.get("hay_pregunta", letra != "NINGUNA")),
        "pregunta": str(d.get("pregunta", "") or ""),
        "opciones": [str(o) for o in opciones],
        "razon": str(d.get("razon", "") or ""),
        "respuesta": letra,
        "confianza": max(0.0, min(1.0, confianza)),
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
                 imagen_b64: str | None = None, num_ctx: int = NUM_CTX,
                 pensar=None, verbose: bool = False) -> dict:
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

    payload = {
        "model": modelo,
        "messages": [mensaje],
        "format": ESQUEMA,          # salida estructurada: obliga al JSON
        "stream": False,
        "options": {"temperature": 0, "num_ctx": num_ctx},
    }
    if pensar is not None:
        # Los modelos de razonamiento (qwen3, etc.) piensan en un canal aparte,
        # fuera del JSON. Razonar libremente y DESPUES rellenar el esquema es
        # mucho mejor que razonar apretujado dentro de un campo del esquema.
        payload["think"] = pensar
    cuerpo = json.dumps(payload).encode()

    try:
        with _abrir(f"{host}/api/chat", data=cuerpo) as r:
            datos = json.loads(r.read())
    except urllib.error.HTTPError as e:
        detalle = e.read().decode()
        if "think" in detalle.lower() and "support" in detalle.lower():
            raise RuntimeError(
                f"El modelo '{modelo}' no soporta --pensar. Quita esa opcion, "
                f"o usa un modelo de razonamiento como qwen3."
            ) from e
        if "exceed_context_size" in detalle or "context size" in detalle:
            raise RuntimeError(
                f"La captura no cabe en el contexto del modelo (ahora en "
                f"{num_ctx} tokens).\n"
                f"Prueba con el doble:  --num-ctx {num_ctx * 2}\n"
                f"O captura solo el area de la pregunta con --region X,Y,ANCHO,ALTO,\n"
                f"o usa --motor ocr, que manda texto en vez de imagen."
            ) from e
        raise RuntimeError(f"Ollama devolvio error {e.code}: {detalle[:300]}") from e

    pensamiento = (datos.get("message", {}) or {}).get("thinking") or ""
    if pensamiento and verbose:
        print("  --- razonamiento del modelo ---")
        for linea in pensamiento.strip().splitlines()[:20]:
            print(f"  | {linea}")
        print("  -------------------------------")

    texto = datos.get("message", {}).get("content", "")
    try:
        return _normalizar(json.loads(texto))
    except json.JSONDecodeError as e:
        raise RuntimeError(f"El modelo local no devolvio JSON valido: {texto[:300]}") from e


def responder_local(ruta_png: str, modelo: str = MODELO_VISION,
                    host: str = OLLAMA_HOST, num_ctx: int = NUM_CTX,
                    pensar=None, verbose: bool = False, **_) -> dict:
    """Modelo de vision local: ve la captura directo, sin OCR."""
    with open(ruta_png, "rb") as f:
        b64 = base64.standard_b64encode(f.read()).decode("utf-8")
    return _chat_ollama(host, modelo, instrucciones_imagen(), imagen_b64=b64,
                        num_ctx=num_ctx, pensar=pensar, verbose=verbose)


# --------------------------------------------------------------------------
# OCR + modelo de texto (local)
# --------------------------------------------------------------------------

# El instalador de Windows no agrega tesseract al PATH, asi que lo buscamos
# tambien donde suele quedar.
_RUTAS_TESSERACT = (
    r"C:\Program Files\Tesseract-OCR\tesseract.exe",
    r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
    os.path.expandvars(r"%LOCALAPPDATA%\Programs\Tesseract-OCR\tesseract.exe"),
    os.path.expandvars(r"%LOCALAPPDATA%\Tesseract-OCR\tesseract.exe"),
)


def buscar_tesseract(ruta: str | None = None) -> str:
    """Devuelve el ejecutable de tesseract. Lanza RuntimeError si no aparece."""
    if ruta:
        if os.path.isfile(ruta):
            return ruta
        raise RuntimeError(f"No existe el tesseract que indicaste: {ruta}")

    encontrado = shutil.which("tesseract")
    if encontrado:
        return encontrado
    for candidato in _RUTAS_TESSERACT:
        if os.path.isfile(candidato):
            return candidato

    raise RuntimeError(
        "No encontre tesseract.\n"
        "  Windows: winget install UB-Mannheim.TesseractOCR\n"
        "           (o https://github.com/UB-Mannheim/tesseract/wiki)\n"
        "  macOS:   brew install tesseract tesseract-lang\n"
        "  Ubuntu:  sudo apt install tesseract-ocr tesseract-ocr-spa\n"
        "Si ya lo instalaste en otra carpeta, pasala con "
        "--tesseract \"C:\\ruta\\tesseract.exe\""
    )


def ocr_texto(ruta_png: str, tesseract: str | None = None,
              verbose: bool = False) -> str:
    """Saca el texto de la imagen con tesseract."""
    exe = buscar_tesseract(tesseract)
    if verbose:
        print(f"  usando tesseract: {exe}")

    # spa+eng si el paquete de espanol esta instalado; si no, el default
    #
    # encoding="utf-8" es obligatorio: tesseract escribe UTF-8, pero sin esto
    # Python decodifica con la codificacion regional (cp1252 en Windows en
    # espanol) y el primer acento raro tira un UnicodeDecodeError dentro del
    # hilo lector, dejando stdout en None. errors="replace" evita que un byte
    # suelto tumbe toda la lectura.
    r = None
    for idioma in (["-l", "spa+eng"], []):
        r = subprocess.run([exe, ruta_png, "stdout", *idioma],
                           capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
        if r.returncode == 0 and (r.stdout or "").strip():
            if idioma and verbose:
                print("  idioma: spa+eng")
            elif not idioma:
                print("  aviso: sin el paquete de espanol; leyendo en el idioma "
                      "por defecto (reinstala tesseract marcando Spanish)")
            return r.stdout
    detalle = (r.stderr or "").strip()[:200] if r else ""
    raise RuntimeError(
        f"tesseract no leyo nada del PNG. {detalle}".strip()
    )


def responder_ocr(ruta_png: str, modelo: str = MODELO_TEXTO,
                  host: str = OLLAMA_HOST, verbose: bool = False,
                  num_ctx: int = NUM_CTX, tesseract: str | None = None,
                  pensar=None, **_) -> dict:
    """tesseract lee la pantalla y un modelo de texto local contesta."""
    texto = ocr_texto(ruta_png, tesseract=tesseract, verbose=verbose)
    if verbose:
        print("  --- texto leido por OCR ---")
        for linea in texto.strip().splitlines()[:25]:
            print(f"  | {linea}")
        print("  ---------------------------")
    return _chat_ollama(host, modelo, instrucciones_texto(texto.strip()),
                        num_ctx=num_ctx, pensar=pensar, verbose=verbose)


# --------------------------------------------------------------------------
# Claude (API)
# --------------------------------------------------------------------------

def _pedir_a_claude(contenido: list | str, modelo: str,
                    barato: bool = False) -> dict:
    import anthropic

    respuesta = anthropic.Anthropic().messages.create(
        model=modelo,
        max_tokens=4000,
        messages=[{"role": "user", "content": contenido}],
        output_config={"format": {"type": "json_schema", "schema": esquema(barato)}},
    )

    uso = getattr(respuesta, "usage", None)
    if uso is not None:
        entrada = getattr(uso, "input_tokens", 0) or 0
        salida = getattr(uso, "output_tokens", 0) or 0
        linea = f"  tokens: {entrada} entrada + {salida} salida"
        precio = PRECIOS.get(modelo)
        if precio:
            costo = entrada * precio[0] / 1e6 + salida * precio[1] / 1e6
            linea += f"  ~ {costo * 100:.2f} centavos de dolar"
        print(linea)

    # La API puede devolver 200 sin texto: hay que mirar stop_reason antes de
    # tocar content, o revienta con un StopIteration sin mensaje.
    bloque = next((b for b in respuesta.content if b.type == "text"), None)
    if bloque is None:
        paro = getattr(respuesta, "stop_reason", None)
        detalles = getattr(respuesta, "stop_details", None)
        if paro == "refusal":
            categoria = getattr(detalles, "category", None) or "sin categoria"
            explicacion = getattr(detalles, "explanation", None) or ""
            raise RuntimeError(
                f"La API se nego a contestar esta pregunta "
                f"(stop_reason=refusal, {categoria}). {explicacion}".strip()
            )
        raise RuntimeError(
            f"La API no devolvio texto (stop_reason={paro}). "
            f"Bloques recibidos: {[b.type for b in respuesta.content] or 'ninguno'}"
        )

    try:
        return _normalizar(json.loads(bloque.text))
    except json.JSONDecodeError as e:
        paro = getattr(respuesta, "stop_reason", None)
        if paro == "max_tokens":
            raise RuntimeError(
                "La respuesta se corto por max_tokens y quedo incompleta."
            ) from e
        raise RuntimeError(
            f"La API devolvio algo que no es JSON valido: {bloque.text[:200]}"
        ) from e


def responder_claude(ruta_png: str, modelo: str = MODELO_CLAUDE,
                     barato: bool = False, **_) -> dict:
    """Manda la captura completa a la API."""
    with open(ruta_png, "rb") as f:
        datos = base64.standard_b64encode(f.read()).decode("utf-8")
    return _pedir_a_claude([
        {"type": "image",
         "source": {"type": "base64", "media_type": "image/png", "data": datos}},
        {"type": "text", "text": instrucciones_imagen(barato)},
    ], modelo, barato)


def responder_ocr_claude(ruta_png: str, modelo: str = MODELO_CLAUDE,
                         verbose: bool = False, tesseract: str | None = None,
                         barato: bool = False, **_) -> dict:
    """tesseract lee la pantalla aqui; a la API solo viaja el texto.

    Tu captura de pantalla nunca sale de la maquina, y como el texto ocupa
    muchisimos menos tokens que una imagen, cuesta una fraccion.
    """
    texto = ocr_texto(ruta_png, tesseract=tesseract, verbose=verbose)
    if verbose:
        print("  --- texto leido por OCR ---")
        for linea in texto.strip().splitlines()[:25]:
            print(f"  | {linea}")
        print("  ---------------------------")
    return _pedir_a_claude(instrucciones_texto(texto.strip(), barato),
                           modelo, barato)


MOTORES = {
    "claude": (responder_claude, MODELO_CLAUDE),
    "ocr-claude": (responder_ocr_claude, MODELO_CLAUDE),
    "local": (responder_local, MODELO_VISION),
    "ocr": (responder_ocr, MODELO_TEXTO),
}

# Motores donde la imagen nunca llega al modelo: encogerla solo le quitaria
# resolucion a tesseract.
SOLO_TEXTO = ("ocr", "ocr-claude")


def elegir_motor(nombre: str, host: str = OLLAMA_HOST) -> str:
    """Resuelve 'auto': local si Ollama esta corriendo, si no Claude."""
    if nombre != "auto":
        return nombre
    # Lista vacia = Ollama corriendo pero sin modelos: no sirve como motor.
    if modelos_ollama(host):
        return "local"
    if os.environ.get("ANTHROPIC_API_KEY"):
        return "claude"
    raise RuntimeError(
        "No hay motor disponible: Ollama no responde y no hay ANTHROPIC_API_KEY.\n"
        "Instala Ollama (ollama.com) o exporta tu API key."
    )
