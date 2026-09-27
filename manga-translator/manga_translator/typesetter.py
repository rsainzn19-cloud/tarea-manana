"""Escribe la traducción dentro de cada globo, ajustando tamaño y saltos de línea."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from .detector import TextBlock

_BUNDLED_FONT = Path(__file__).resolve().parent.parent / "fonts" / "ComicNeue-Bold.ttf"
_SYSTEM_FONTS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
]


def default_font() -> str | None:
    for candidate in [_BUNDLED_FONT, *map(Path, _SYSTEM_FONTS)]:
        if candidate.exists():
            return str(candidate)
    return None


@lru_cache(maxsize=256)
def _font(path: str | None, size: int) -> ImageFont.FreeTypeFont:
    if path:
        return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def _wrap(draw: ImageDraw.ImageDraw, text: str, font, max_width: int,
          allow_break: bool = False) -> list[str] | None:
    """Salto de línea voraz por palabras.

    Si una palabra no cabe sola en una línea devuelve None, salvo con
    allow_break, que la corta con guion (último recurso).
    """
    lines: list[str] = []
    current = ""
    for word in text.split():
        candidate = f"{current} {word}".strip()
        if draw.textlength(candidate, font=font) <= max_width:
            current = candidate
            continue
        if current:
            lines.append(current)
        if draw.textlength(word, font=font) > max_width and not allow_break:
            return None
        while draw.textlength(word, font=font) > max_width and len(word) > 1:
            cut = len(word) - 1
            while cut > 1 and draw.textlength(word[:cut] + "-", font=font) > max_width:
                cut -= 1
            lines.append(word[:cut] + "-")
            word = word[cut:]
        current = word
    if current:
        lines.append(current)
    return lines


def _fit(draw, text: str, font_path: str | None, width: int, height: int,
         max_size: int, min_size: int):
    """Busca el mayor tamaño de letra con el que el texto cabe en la caja."""
    best = None
    lo, hi = min_size, max(min_size, max_size)
    while lo <= hi:
        size = (lo + hi) // 2
        font = _font(font_path, size)
        lines = _wrap(draw, text, font, width)
        line_h = int(size * 1.15)
        if lines is not None and len(lines) * line_h <= height:
            best = (font, lines, line_h)
            lo = size + 1
        else:
            hi = size - 1
    if best is None:  # no cabe ni con la letra mínima: se corta y se escribe igual
        font = _font(font_path, min_size)
        best = (font, _wrap(draw, text, font, width, allow_break=True), int(min_size * 1.15))
    return best


def typeset(image: Image.Image, blocks: list[TextBlock], font_path: str | None = None,
            uppercase: bool = False, max_font_size: int = 40, min_font_size: int = 9) -> Image.Image:
    font_path = font_path or default_font()
    out = image.convert("RGB")
    draw = ImageDraw.Draw(out)

    for block in blocks:
        text = block.translation.strip()
        if not text:
            continue
        if uppercase:
            text = text.upper()

        x1, y1, x2, y2 = block.render_box or block.box
        # margen interior para que el texto no toque el borde del globo
        margin = max(2, int(min(x2 - x1, y2 - y1) * 0.04))
        x1, y1, x2, y2 = x1 + margin, y1 + margin, x2 - margin, y2 - margin
        width, height = max(1, x2 - x1), max(1, y2 - y1)

        font, lines, line_h = _fit(draw, text, font_path, width, height,
                                   max_font_size, min_font_size)

        # color según el fondo: letra negra en fondo claro, blanca en oscuro
        bg = out.crop((x1, y1, x2, y2)).convert("L").resize((1, 1)).getpixel((0, 0))
        fill, stroke = ((0, 0, 0), (255, 255, 255)) if bg > 110 else ((255, 255, 255), (0, 0, 0))
        stroke_w = max(1, font.size // 12)

        top = y1 + (height - len(lines) * line_h) // 2
        for i, line in enumerate(lines):
            line_w = draw.textlength(line, font=font)
            left = x1 + (width - line_w) / 2
            draw.text((left, top + i * line_h), line, font=font, fill=fill,
                      stroke_width=stroke_w, stroke_fill=stroke)
    return out
