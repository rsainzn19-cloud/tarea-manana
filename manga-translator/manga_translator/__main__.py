"""Uso: python -m manga_translator ENTRADA [ENTRADA ...] [opciones]

ENTRADA puede ser una imagen o una carpeta con imágenes (se procesan en orden).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .pipeline import MangaTranslator
from .translator import make_translator

IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".bmp"}


def collect_images(inputs: list[str]) -> list[Path]:
    images: list[Path] = []
    for item in map(Path, inputs):
        if item.is_dir():
            images += sorted(p for p in item.iterdir() if p.suffix.lower() in IMAGE_EXTS)
        elif item.suffix.lower() in IMAGE_EXTS and item.exists():
            images.append(item)
        else:
            print(f"Ignorado (no es una imagen): {item}", file=sys.stderr)
    return images


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        prog="manga_translator",
        description="Traduce páginas de manga del japonés con OCR y escribe la "
                    "traducción encima de la imagen.",
    )
    parser.add_argument("inputs", nargs="+", help="imágenes o carpetas de entrada")
    parser.add_argument("-o", "--output", default="traducido",
                        help="carpeta de salida (por defecto: ./traducido)")
    parser.add_argument("-t", "--translator", choices=["google", "deepl", "claude"],
                        default="google", help="motor de traducción (por defecto: google)")
    parser.add_argument("-l", "--lang", default="en",
                        help="idioma destino, código ISO (por defecto: en)")
    parser.add_argument("--font", help="fuente .ttf/.otf para el texto traducido")
    parser.add_argument("--uppercase", action="store_true",
                        help="escribir en MAYÚSCULAS, estilo scanlation")
    parser.add_argument("--gpu", action="store_true", help="usar GPU (CUDA) si hay")
    parser.add_argument("--debug", action="store_true",
                        help="guardar también una imagen con las cajas detectadas")
    parser.add_argument("--claude-model", default="claude-opus-5",
                        help="modelo de Claude a usar con -t claude")
    parser.add_argument("--claude-effort", default="medium",
                        choices=["low", "medium", "high", "xhigh", "max"],
                        help="esfuerzo de razonamiento de Claude (por defecto: medium)")
    parser.add_argument("--no-image-context", action="store_true",
                        help="con -t claude, no enviar la imagen de la página")
    args = parser.parse_args(argv)

    images = collect_images(args.inputs)
    if not images:
        sys.exit("No se encontraron imágenes para traducir.")

    claude_opts = {}
    if args.translator == "claude":
        claude_opts = {"model": args.claude_model, "effort": args.claude_effort,
                       "send_image": not args.no_image_context}
    translator = make_translator(args.translator, args.lang, **claude_opts)
    app = MangaTranslator(translator, gpu=args.gpu, font=args.font, uppercase=args.uppercase)

    out_dir = Path(args.output)
    failed = 0
    for n, src in enumerate(images, 1):
        dst = out_dir / f"{src.stem}_{args.lang}{src.suffix}"
        print(f"[{n}/{len(images)}] {src} -> {dst}")
        try:
            blocks = app.translate_file(src, dst, debug=args.debug)
        except Exception as e:  # una página fallida no detiene el resto
            failed += 1
            print(f"    ERROR: {e}", file=sys.stderr)
            continue
        for block in blocks:
            print(f"    {block.text}  =>  {block.translation}")
    print(f"Listo. {len(images) - failed} página(s) traducida(s), {failed} con error.")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
