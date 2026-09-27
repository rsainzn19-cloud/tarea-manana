"""Detección de texto: encuentra dónde hay texto japonés en la página.

Usa el detector CRAFT de EasyOCR (sólo detección, sin su reconocedor) y luego
agrupa las cajas cercanas en "bloques" (normalmente un bloque = un globo de
diálogo), porque el texto japonés vertical sale partido en muchas cajitas.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np


@dataclass
class TextBlock:
    x1: int
    y1: int
    x2: int
    y2: int
    text: str = ""          # texto original (japonés) leído por OCR
    translation: str = ""   # texto traducido
    render_box: tuple[int, int, int, int] | None = field(default=None, repr=False)
    # máscara de los trazos del texto original (ver cleaner.refine_blocks)
    mask: np.ndarray | None = field(default=None, repr=False)
    mask_origin: tuple[int, int] = field(default=(0, 0), repr=False)

    @property
    def width(self) -> int:
        return self.x2 - self.x1

    @property
    def height(self) -> int:
        return self.y2 - self.y1

    @property
    def box(self) -> tuple[int, int, int, int]:
        return self.x1, self.y1, self.x2, self.y2


class TextDetector:
    def __init__(self, gpu: bool = False):
        import easyocr  # import diferido: tarda en cargar

        self.reader = easyocr.Reader(["ja"], gpu=gpu, recognizer=False, verbose=False)

    def detect(self, image: np.ndarray) -> list[TextBlock]:
        """Devuelve los bloques de texto de una imagen RGB en orden de lectura."""
        horizontal, free = self.reader.detect(
            image,
            min_size=8,
            text_threshold=0.6,
            low_text=0.3,
            link_threshold=0.3,
            add_margin=0.05,
        )
        boxes = [(x1, y1, x2, y2) for x1, x2, y1, y2 in horizontal[0]]
        for poly in free[0]:
            xs = [p[0] for p in poly]
            ys = [p[1] for p in poly]
            boxes.append((min(xs), min(ys), max(xs), max(ys)))

        h, w = image.shape[:2]
        boxes = [
            (max(0, int(x1)), max(0, int(y1)), min(w, int(x2)), min(h, int(y2)))
            for x1, y1, x2, y2 in boxes
            if x2 > x1 and y2 > y1
        ]
        blocks = merge_boxes(boxes)
        return sort_reading_order(blocks)


def merge_boxes(boxes: list[tuple[int, int, int, int]], gap_factor: float = 0.6) -> list[TextBlock]:
    """Une cajas que se tocan o están muy cerca (union-find).

    La distancia máxima de unión es proporcional al tamaño típico de un
    carácter, así las columnas de un mismo globo se juntan pero los globos
    vecinos quedan separados.
    """
    if not boxes:
        return []

    char_size = float(np.median([min(x2 - x1, y2 - y1) for x1, y1, x2, y2 in boxes]))
    gap = max(2, int(char_size * gap_factor))

    parent = list(range(len(boxes)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i, a in enumerate(boxes):
        for j in range(i + 1, len(boxes)):
            b = boxes[j]
            if (
                a[0] - gap <= b[2] and b[0] - gap <= a[2]
                and a[1] - gap <= b[3] and b[1] - gap <= a[3]
            ):
                parent[find(i)] = find(j)

    groups: dict[int, list[tuple[int, int, int, int]]] = {}
    for i, box in enumerate(boxes):
        groups.setdefault(find(i), []).append(box)

    blocks = []
    for members in groups.values():
        x1 = min(b[0] for b in members)
        y1 = min(b[1] for b in members)
        x2 = max(b[2] for b in members)
        y2 = max(b[3] for b in members)
        # descarta ruido: bloques más pequeños que un carácter
        if (x2 - x1) * (y2 - y1) < (char_size * 0.8) ** 2:
            continue
        blocks.append(TextBlock(x1, y1, x2, y2))
    return blocks


def sort_reading_order(blocks: list[TextBlock]) -> list[TextBlock]:
    """Orden de lectura de manga: de arriba a abajo y de derecha a izquierda.

    Los bloques cuyo rango vertical se solapa se consideran de la misma "fila"
    y dentro de ella se leen de derecha a izquierda.
    """
    rows: list[list[TextBlock]] = []
    for block in sorted(blocks, key=lambda b: b.y1):
        for row in rows:
            top = min(b.y1 for b in row)
            bottom = max(b.y2 for b in row)
            overlap = min(bottom, block.y2) - max(top, block.y1)
            if overlap > 0.3 * min(block.height, bottom - top):
                row.append(block)
                break
        else:
            rows.append([block])
    return [b for row in rows for b in sorted(row, key=lambda b: -b.x2)]
