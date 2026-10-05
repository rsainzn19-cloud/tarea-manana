"""Evaluacion del historial de construccion de Maya (placeholder).

Un .ma guarda los meshes con historial como una "receta" (polyCube ->
polyExtrudeFace -> polyBevel3 ...) sin los vertices finales. Reproducir esa
receta exige imitar el nucleo de modelado de Maya con su mismo orden de
indices. Este modulo resuelve el historial cuando todas las operaciones estan
soportadas; si no, lanza NotImplementedError y el mesh queda marcado como
"no evaluado".
"""


def evaluate_shape_history(reader, shape):
    raise NotImplementedError('evaluacion de historial no disponible')
