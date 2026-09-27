"""Une las piezas: detectar -> OCR -> traducir -> borrar -> escribir."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .cleaner import clean_blocks, find_render_boxes, refine_blocks
from .detector import TextBlock, TextDetector
from .ocr import MangaReader
from .typesetter import typeset


class MangaTranslator:
    def __init__(self, translator, gpu: bool = False, font: str | None = None,
                 uppercase: bool = False):
        print("Cargando modelos (la primera vez se descargan, puede tardar)...")
        self.detector = TextDetector(gpu=gpu)
        self.reader = MangaReader(force_cpu=not gpu)
        self.translator = translator
        self.font = font
        self.uppercase = uppercase

    def translate_page(self, page: Image.Image) -> tuple[Image.Image, list[TextBlock]]:
        page = page.convert("RGB")
        pixels = np.array(page)

        blocks = self.detector.detect(pixels)
        refine_blocks(pixels, blocks)
        blocks = self.reader.read(page, blocks)
        if not blocks:
            return page, []

        translations = self.translator.translate([b.text for b in blocks], page=page)
        for block, text in zip(blocks, translations):
            block.translation = text

        cleaned = clean_blocks(pixels, blocks)
        find_render_boxes(cleaned, blocks)
        result = typeset(Image.fromarray(cleaned), blocks,
                         font_path=self.font, uppercase=self.uppercase)
        return result, blocks

    def translate_file(self, src: Path, dst: Path, debug: bool = False) -> list[TextBlock]:
        with Image.open(src) as page:
            result, blocks = self.translate_page(page)

        dst.parent.mkdir(parents=True, exist_ok=True)
        if dst.suffix.lower() in {".jpg", ".jpeg"}:
            result.save(dst, quality=95)
        else:
            result.save(dst)

        # Guarda también el texto (original + traducción) por si se quiere revisar.
        data = [
            {"box": list(b.box), "japanese": b.text, "translation": b.translation}
            for b in blocks
        ]
        dst.with_suffix(".json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

        if debug:
            with Image.open(src) as page:
                save_debug(page.convert("RGB"), blocks, dst.with_name(dst.stem + "_debug.png"))
        return blocks


def save_debug(page: Image.Image, blocks: list[TextBlock], path: Path) -> None:
    """Dibuja los bloques detectados (rojo) y la zona de escritura (azul)."""
    draw = ImageDraw.Draw(page)
    for i, block in enumerate(blocks):
        draw.rectangle(block.box, outline=(255, 0, 0), width=2)
        if block.render_box:
            draw.rectangle(block.render_box, outline=(0, 90, 255), width=2)
        draw.text((block.x1 + 2, block.y1 + 2), str(i), fill=(255, 0, 0))
    page.save(path)

