"""Genera una página de manga de prueba (dibujo y diálogos originales).

Sirve para probar el traductor sin necesitar una página real:
    python examples/make_sample.py            # crea examples/sample_page.png
Necesita una fuente japonesa (p. ej. Noto Sans CJK); pásala con --font si no
se encuentra sola.
"""

from __future__ import annotations

import argparse
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

JP_FONTS = [
    ("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 0),
    ("/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc", 0),
    ("C:/Windows/Fonts/meiryob.ttc", 0),
]

# (caja del globo x1, y1, x2, y2, texto en columnas de derecha a izquierda)
BUBBLES = [
    ((560, 70, 800, 330), ["おはよう！", "今日はいい", "天気だね。"]),
    ((90, 90, 300, 330), ["学校に", "遅れちゃうよ！"]),
    ((560, 520, 820, 800), ["ちょっと", "待って、", "忘れ物した！"]),
    ((110, 900, 330, 1170), ["お腹", "すいた…", "何か食べたいな。"]),
]
NARRATION = ((500, 880, 820, 940), "その日の午後…")


def load_font(path: str | None, size: int) -> ImageFont.FreeTypeFont:
    candidates = [(path, 0)] if path else JP_FONTS
    for font_path, index in candidates:
        if font_path and Path(font_path).exists():
            return ImageFont.truetype(font_path, size, index=index)
    raise SystemExit("No encuentro una fuente japonesa; usa --font RUTA.ttf")


def draw_vertical(draw: ImageDraw.ImageDraw, box, columns, font) -> None:
    """Escribe columnas verticales, de derecha a izquierda, centradas en el globo."""
    size = font.size
    col_w = int(size * 1.25)
    total_w = col_w * len(columns)
    cx = (box[0] + box[2]) // 2
    cy = (box[1] + box[3]) // 2
    x = cx + total_w // 2 - col_w
    for column in columns:
        y = cy - (len(column) * size) // 2
        for ch in column:
            if ch in "、。":  # en vertical van arriba a la derecha
                draw.text((x + size * 0.55, y - size * 0.45), ch, font=font, fill="black")
            else:
                draw.text((x, y), ch, font=font, fill="black")
            y += size
        x -= col_w


def draw_person(draw: ImageDraw.ImageDraw, cx: int, cy: int, scale: float, rnd) -> None:
    """Personaje sencillo inventado: cabeza, pelo con mechones y cuerpo."""
    r = int(60 * scale)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill="white", outline="black", width=4)
    for i in range(7):  # mechones de pelo
        x = cx - r + i * (2 * r // 6)
        draw.polygon([(x - 18, cy - r + 20), (x + 18, cy - r + 20), (x + rnd.randint(-10, 10), cy - r - 30)],
                     fill=(40, 40, 40))
    eye = int(10 * scale)
    for dx in (-r // 2.5, r // 2.5):
        draw.ellipse((cx + dx - eye, cy - eye, cx + dx + eye, cy + eye * 2), fill="black")
    draw.arc((cx - r // 3, cy + r // 4, cx + r // 3, cy + r // 1.6), 20, 160, fill="black", width=3)
    draw.rectangle((cx - r, cy + r + 10, cx + r, cy + r * 3), fill=(120, 120, 120), outline="black", width=4)


def make_page(font_path: str | None = None) -> Image.Image:
    rnd = random.Random(7)
    page = Image.new("RGB", (900, 1300), "white")
    draw = ImageDraw.Draw(page)

    panels = [(40, 40, 860, 460), (40, 490, 860, 840), (40, 870, 860, 1260)]
    for panel in panels:
        # trama de fondo para que el dibujo no sea plano
        for _ in range(60):
            x = rnd.randint(panel[0] + 80, panel[2])
            draw.line((x, panel[1], x - 80, panel[3]), fill=(215, 215, 215), width=2)
        draw.rectangle(panel, outline="black", width=6)

    draw_person(draw, 440, 220, 1.2, rnd)
    draw_person(draw, 330, 620, 1.0, rnd)
    draw_person(draw, 600, 1060, 1.1, rnd)

    font = load_font(font_path, 30)
    for box, columns in BUBBLES:
        draw.ellipse(box, fill="white", outline="black", width=4)
        draw_vertical(draw, box, columns, font)

    box, text = NARRATION
    draw.rectangle(box, fill="white", outline="black", width=3)
    small = load_font(font_path, 26)
    tw = draw.textlength(text, font=small)
    draw.text(((box[0] + box[2] - tw) / 2, box[1] + 14), text, font=small, fill="black")
    return page


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", help="fuente japonesa .ttf/.otf/.ttc")
    parser.add_argument("-o", "--output", default=str(Path(__file__).parent / "sample_page.png"))
    args = parser.parse_args()
    make_page(args.font).save(args.output)
    print(f"Página de prueba guardada en {args.output}")
