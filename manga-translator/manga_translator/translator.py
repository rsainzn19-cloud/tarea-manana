"""Traducción del texto leído.

Motores disponibles:
  - google : Google Translate vía deep-translator (gratis, sin clave).
  - deepl  : DeepL (necesita DEEPL_API_KEY). Muy bueno para japonés.
  - claude : Claude de Anthropic (necesita ANTHROPIC_API_KEY). Traduce toda
             la página de una vez viendo la imagen, así entiende el contexto,
             quién habla y el tono, y puede corregir errores del OCR.
"""

from __future__ import annotations

import base64
import io
import json
import os
import sys
import time

from PIL import Image

LANGUAGE_NAMES = {
    "en": "English", "es": "Spanish", "pt": "Portuguese", "fr": "French",
    "de": "German", "it": "Italian",
}


class GoogleTranslator:
    def __init__(self, target: str = "en"):
        from deep_translator import GoogleTranslator as _Google

        self._client = _Google(source="ja", target=target)

    def translate(self, texts: list[str], page: Image.Image | None = None) -> list[str]:
        from deep_translator.exceptions import TooManyRequests

        results = []
        for text in texts:
            # Google permite ~5 peticiones por segundo: pausa y reintenta.
            for attempt in range(4):
                try:
                    results.append(self._client.translate(text) or "")
                    break
                except TooManyRequests:
                    time.sleep(2 ** attempt)
            else:
                raise RuntimeError(
                    "Google Translate rechaza las peticiones (límite de uso o "
                    "captcha). Espera un rato o usa --translator deepl/claude.")
            time.sleep(0.25)
        return results


class DeepLTranslator:
    def __init__(self, target: str = "en"):
        from deep_translator import DeeplTranslator as _DeepL

        api_key = os.environ.get("DEEPL_API_KEY")
        if not api_key:
            sys.exit("Falta la variable de entorno DEEPL_API_KEY para usar DeepL.")
        self._client = _DeepL(
            api_key=api_key, source="ja", target=target,
            use_free_api=api_key.endswith(":fx"),
        )

    def translate(self, texts: list[str], page: Image.Image | None = None) -> list[str]:
        return [self._client.translate(t) or "" for t in texts]


SYSTEM_PROMPT = """You are a professional manga translator and typesetter.
You receive the OCR'd Japanese text of every speech bubble / caption on one
manga page (numbered in reading order) and, when available, the page image.

Translate each item into natural, fluent {language} as a published
localization would:
- Keep each character's voice and tone (casual, polite, rough, cute...).
- Use the page image to work out who is speaking and what is going on.
- The OCR can contain mistakes; silently fix obvious ones using context.
- Sound effects: give a short {language} equivalent (e.g. ドキドキ -> "Ba-dump").
- Keep translations concise: they must fit inside the original bubble.
- Return exactly one translation per id, same ids as the input."""

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "translations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer"},
                    "text": {"type": "string"},
                },
                "required": ["id", "text"],
                "additionalProperties": False,
            },
        }
    },
    "required": ["translations"],
    "additionalProperties": False,
}


class ClaudeTranslator:
    def __init__(self, target: str = "en", model: str = "claude-opus-5",
                 effort: str = "medium", send_image: bool = True):
        import anthropic

        self._anthropic = anthropic
        # Usa ANTHROPIC_API_KEY (o un perfil de `ant auth login`).
        self._client = anthropic.Anthropic()
        self.model = model
        self.effort = effort
        self.send_image = send_image
        self.language = LANGUAGE_NAMES.get(target, target)
        self._fallback_target = target

    def translate(self, texts: list[str], page: Image.Image | None = None) -> list[str]:
        if not texts:
            return []

        numbered = "\n".join(f"{i}: {t}" for i, t in enumerate(texts))
        content: list[dict] = []
        if page is not None and self.send_image:
            content.append({
                "type": "image",
                "source": {"type": "base64", "media_type": "image/jpeg",
                           "data": _encode_page(page)},
            })
        content.append({
            "type": "text",
            "text": f"Japanese text on this page, in reading order:\n{numbered}",
        })

        try:
            response = self._client.beta.messages.create(
                model=self.model,
                max_tokens=16000,
                system=SYSTEM_PROMPT.format(language=self.language),
                messages=[{"role": "user", "content": content}],
                thinking={"type": "adaptive"},
                output_config={
                    "effort": self.effort,
                    "format": {"type": "json_schema", "schema": OUTPUT_SCHEMA},
                },
                # Si el modelo rechaza la petición, la API la reintenta
                # automáticamente con el modelo de respaldo recomendado.
                betas=["server-side-fallback-2026-07-01"],
                fallbacks="default",
            )
        except self._anthropic.AuthenticationError:
            sys.exit("Clave de Anthropic inválida o ausente: define ANTHROPIC_API_KEY.")
        except self._anthropic.APIStatusError as e:
            print(f"  [claude] error de la API ({e.status_code}): {e.message}; "
                  "uso Google Translate para esta página.", file=sys.stderr)
            return GoogleTranslator(self._fallback_target).translate(texts)
        except self._anthropic.APIConnectionError:
            print("  [claude] sin conexión con la API; uso Google Translate "
                  "para esta página.", file=sys.stderr)
            return GoogleTranslator(self._fallback_target).translate(texts)

        if response.stop_reason != "end_turn":
            print(f"  [claude] respuesta incompleta ({response.stop_reason}); "
                  "uso Google Translate para esta página.", file=sys.stderr)
            return GoogleTranslator(self._fallback_target).translate(texts)

        raw = next(b.text for b in response.content if b.type == "text")
        by_id = {item["id"]: item["text"] for item in json.loads(raw)["translations"]}
        # Si faltara algún id, se queda sin traducir en vez de desordenarse.
        return [by_id.get(i, "") for i in range(len(texts))]


def _encode_page(page: Image.Image, max_side: int = 1568) -> str:
    img = page.convert("RGB")
    img.thumbnail((max_side, max_side))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return base64.standard_b64encode(buf.getvalue()).decode("ascii")


def make_translator(name: str, target: str = "en", **claude_opts):
    if name == "google":
        return GoogleTranslator(target)
    if name == "deepl":
        return DeepLTranslator(target)
    if name == "claude":
        return ClaudeTranslator(target, **claude_opts)
    raise ValueError(f"Traductor desconocido: {name}")
