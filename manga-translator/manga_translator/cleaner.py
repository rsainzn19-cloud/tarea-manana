"""Máscara del texto original, borrado y cálculo del espacio en el globo."""

from __future__ import annotations

import cv2
import numpy as np

from .detector import TextBlock


def refine_blocks(image: np.ndarray, blocks: list[TextBlock]) -> None:
    """Calcula la máscara de los trazos del texto de cada bloque.

    Alrededor de la caja detectada se buscan las manchas que se distinguen
    del fondo. Se quedan las que tocan la caja (y, en una segunda pasada, las
    muy cercanas a ellas, como el punto de un "！"), pero nunca las que salen
    de la zona de búsqueda: así el contorno del globo no se considera texto.
    La caja del bloque se amplía para abarcar todos los trazos encontrados.
    """
    gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
    h, w = gray.shape

    for block in blocks:
        pad = max(6, int(min(block.width, block.height) * 0.25))
        x1, y1 = max(0, block.x1 - pad), max(0, block.y1 - pad)
        x2, y2 = min(w, block.x2 + pad), min(h, block.y2 + pad)
        crop = gray[y1:y2, x1:x2]

        border = np.concatenate([crop[0], crop[-1], crop[:, 0], crop[:, -1]])
        ink = (np.abs(crop.astype(np.int16) - np.median(border)) > 50).astype(np.uint8)
        n, labels, stats, _ = cv2.connectedComponentsWithStats(ink, connectivity=8)

        inside = np.ones(n, dtype=bool)  # componentes que no tocan el borde
        for lab in range(1, n):
            sx, sy, sw, sh, _ = stats[lab]
            inside[lab] = sx > 0 and sy > 0 and sx + sw < crop.shape[1] and sy + sh < crop.shape[0]
        inside[0] = False

        box_core = np.zeros_like(ink)
        box_core[block.y1 - y1:block.y2 - y1, block.x1 - x1:block.x2 - x1] = 1
        core = box_core
        keep = np.zeros(n, dtype=bool)
        for _ in range(2):
            hit = np.unique(labels[core > 0])
            keep[hit[inside[hit]]] = True
            near = max(3, pad // 3)
            grown = cv2.dilate(keep[labels].astype(np.uint8), np.ones((near, near), np.uint8))
            core = box_core | grown

        mask = keep[labels]
        block.mask = mask
        block.mask_origin = (x1, y1)
        if mask.any():
            ys, xs = np.nonzero(mask)
            block.x1 = min(block.x1, x1 + int(xs.min()))
            block.y1 = min(block.y1, y1 + int(ys.min()))
            block.x2 = max(block.x2, x1 + int(xs.max()) + 1)
            block.y2 = max(block.y2, y1 + int(ys.max()) + 1)


def clean_blocks(image: np.ndarray, blocks: list[TextBlock]) -> np.ndarray:
    """Devuelve una copia de la imagen (RGB) sin el texto japonés.

    Si el fondo del bloque es liso (globo blanco) los trazos se pintan con ese
    color; si no (texto sobre el dibujo) se reconstruyen con inpainting.
    """
    out = image.copy()
    for block in blocks:
        if block.mask is None or not block.mask.any():
            continue
        ox, oy = block.mask_origin
        mh, mw = block.mask.shape
        crop = out[oy:oy + mh, ox:ox + mw]
        mask = cv2.dilate(block.mask.astype(np.uint8) * 255, np.ones((3, 3), np.uint8),
                          iterations=2)

        # fondo = píxeles de la caja del bloque que no son texto
        in_box = np.zeros(mask.shape, dtype=bool)
        in_box[block.y1 - oy:block.y2 - oy, block.x1 - ox:block.x2 - ox] = True
        background = in_box & (mask == 0)
        gray = cv2.cvtColor(crop, cv2.COLOR_RGB2GRAY)

        if background.sum() > 20 and gray[background].std() < 15:
            crop[mask > 0] = np.median(crop[background], axis=0).astype(np.uint8)
        else:
            crop[:] = cv2.inpaint(crop, mask, 5, cv2.INPAINT_TELEA)
    return out


def find_render_boxes(cleaned: np.ndarray, blocks: list[TextBlock]) -> None:
    """Calcula block.render_box: la zona donde se escribirá la traducción.

    Busca el globo que contiene el bloque (la región conectada del mismo color
    que el fondo del texto). Si está cerrado, se usa el rectángulo inscrito
    en él, que suele ser más ancho que la columna vertical japonesa. Si el
    texto estaba sobre el dibujo, se ensancha un poco la caja original.
    """
    h, w = cleaned.shape[:2]
    gray = cv2.cvtColor(cleaned, cv2.COLOR_RGB2GRAY)

    for block in blocks:
        size = max(block.width, block.height)
        sx1, sy1 = max(0, block.x1 - size), max(0, block.y1 - size)
        sx2, sy2 = min(w, block.x2 + size), min(h, block.y2 + size)
        window = gray[sy1:sy2, sx1:sx2]

        inner = gray[block.y1:block.y2, block.x1:block.x2]
        bg_level = float(np.median(inner))
        similar = (np.abs(window.astype(np.int16) - bg_level) < 30).astype(np.uint8)
        # corta uniones finas entre el globo y zonas blancas del dibujo
        similar = cv2.morphologyEx(similar, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))

        _, labels, stats, _ = cv2.connectedComponentsWithStats(similar, connectivity=4)
        region = labels[block.y1 - sy1:block.y2 - sy1, block.x1 - sx1:block.x2 - sx1]
        ids, counts = np.unique(region[region > 0], return_counts=True)

        box = None
        if len(ids):
            bx, by, bw, bh, _ = (int(v) for v in stats[ids[np.argmax(counts)]])
            touches_edge = (
                (bx == 0 and sx1 > 0) or (by == 0 and sy1 > 0)
                or (bx + bw == sx2 - sx1 and sx2 < w) or (by + bh == sy2 - sy1 and sy2 < h)
            )
            if not touches_edge:
                # rectángulo inscrito en una elipse: ~70% de cada lado
                gx1, gy1 = sx1 + bx, sy1 + by
                mx, my = int(bw * 0.15), int(bh * 0.15)
                box = (
                    min(gx1 + mx, block.x1), min(gy1 + my, block.y1),
                    max(gx1 + bw - mx, block.x2), max(gy1 + bh - my, block.y2),
                )

        if box is None:
            # texto suelto sobre el dibujo: darle algo más de ancho
            cx = (block.x1 + block.x2) // 2
            half = max(block.width, int(block.height * 0.7)) // 2
            box = (max(0, cx - half), block.y1, min(w, cx + half), block.y2)

        block.render_box = box
