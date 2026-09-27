"""OCR: lee el texto japonés de cada bloque con manga-ocr.

manga-ocr es un modelo entrenado específicamente con manga: entiende texto
vertical y horizontal, furigana y las fuentes típicas de los globos.
"""

from __future__ import annotations

import re

from PIL import Image

from .detector import TextBlock

# hiragana, katakana, kanji y caracteres japoneses de ancho completo
_JAPANESE = re.compile(r"[぀-ヿ㐀-䶿一-鿿ｦ-ﾟ]")


class MangaReader:
    def __init__(self, force_cpu: bool = True):
        from manga_ocr import MangaOcr  # import diferido: carga un modelo grande

        self.model = MangaOcr(force_cpu=force_cpu)

    def read(self, image: Image.Image, blocks: list[TextBlock], pad: int = 4) -> list[TextBlock]:
        """Rellena block.text y descarta los bloques sin texto japonés
        (el detector a veces confunde trazos del dibujo con texto)."""
        kept = []
        for block in blocks:
            crop = image.crop((
                max(0, block.x1 - pad),
                max(0, block.y1 - pad),
                min(image.width, block.x2 + pad),
                min(image.height, block.y2 + pad),
            ))
            block.text = self.model(crop).strip()
            if _JAPANESE.search(block.text):
                kept.append(block)
        return kept
