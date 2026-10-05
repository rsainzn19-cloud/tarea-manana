"""Convierte archivos de Maya (.mb / .ma) a .blend (y opcionalmente .glb / .obj / .fbx).

Uso con Blender (no hace falta Maya):

    blender -b -P maya2blender/convertir.py -- originales/RailShip-v5.mb RailShip-v5.blend
    blender -b -P maya2blender/convertir.py -- originales/FishShip_V2.ma FishShip_V2.blend --glb --obj

O con el modulo bpy de PyPI (pip install bpy):

    python maya2blender/convertir.py originales/RailShip-v5.mb RailShip-v5.blend

Opciones:
    --escala N      factor de escala (por defecto 1: 1 unidad de Maya = 1 m en Blender;
                    usa 0.01 si quieres que los centimetros de Maya queden en metros)
    --sin-ejes      no rotar de Y-arriba (Maya) a Z-arriba (Blender)
    --glb --obj --fbx   exporta tambien en esos formatos junto al .blend
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import bpy  # noqa: E402

from maya2blender import load_maya_file  # noqa: E402


def main(argv):
    args = argv[argv.index('--') + 1:] if '--' in argv else argv[1:]
    flags = {a for a in args if a.startswith('--')}
    pos = [a for a in args if not a.startswith('--')]
    scale = 1.0
    if '--escala' in args:
        scale = float(args[args.index('--escala') + 1])
        pos.remove(args[args.index('--escala') + 1])
    if not pos:
        print(__doc__)
        return 1
    src = pos[0]
    dst = pos[1] if len(pos) > 1 else os.path.splitext(src)[0] + '.blend'

    # escena vacia
    bpy.ops.wm.read_factory_settings(use_empty=True)
    coll, stats, warnings = load_maya_file(src, scale=scale, convert_axes='--sin-ejes' not in flags)

    print('Coleccion: %s' % coll.name)
    print('Meshes: %d  Grupos (empties): %d' % (stats['meshes'], stats['empties']))
    for name in stats['unevaluated']:
        print('AVISO: "%s" solo tiene historial de construccion (sin vertices guardados); '
              'ver README, opcion 3.' % name)
    for w in warnings:
        print('AVISO:', w)

    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(dst))
    print('Guardado:', dst)
    base = os.path.splitext(dst)[0]
    if '--glb' in flags:
        bpy.ops.export_scene.gltf(filepath=base + '.glb', export_format='GLB')
        print('Guardado:', base + '.glb')
    if '--obj' in flags:
        bpy.ops.wm.obj_export(filepath=base + '.obj', forward_axis='NEGATIVE_Z', up_axis='Y')
        print('Guardado:', base + '.obj')
    if '--fbx' in flags:
        bpy.ops.export_scene.fbx(filepath=base + '.fbx')
        print('Guardado:', base + '.fbx')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
