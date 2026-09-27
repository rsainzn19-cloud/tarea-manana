"""Traductor de manga: OCR del japonés, traducción y overlay sobre la imagen."""

from .pipeline import MangaTranslator
from .translator import make_translator

__all__ = ["MangaTranslator", "make_translator"]
