"""Exporta escenas de Maya a FBX + OBJ y guarda una copia sin historial.

Sirve para archivos que el importador sin Maya no puede leer completos (por
ejemplo FishShip_V2.ma, que solo guarda el historial de modelado y no los
vertices). Necesita Maya (la licencia educativa gratuita sirve).

Opcion A - desde la terminal, sin abrir la interfaz de Maya:

    "C:\\Program Files\\Autodesk\\Maya2024\\bin\\mayapy.exe" exportar_desde_maya.py FishShip_V2.ma RailShip-v5.mb

Opcion B - dentro de Maya: abre la escena, ve a Windows > General Editors >
Script Editor, pestana "Python", pega este archivo completo y ejecutalo
(Ctrl+Enter). Exporta la escena abierta.

Genera junto a cada archivo:
    <nombre>.fbx               -> Blender: File > Import > FBX
    <nombre>.obj               -> Blender: File > Import > Wavefront (.obj)
    <nombre>_sin_historial.ma  -> lo lee el add-on maya2blender sin Maya
"""

import os
import sys

import maya.cmds as cmds
import maya.mel as mel


def exportar_escena_actual(base):
    base = base.replace('\\', '/')

    # FBX: geometria evaluada, UVs, normales/aristas duras y jerarquia
    cmds.loadPlugin('fbxmaya', quiet=True)
    mel.eval('FBXResetExport')
    mel.eval('FBXExportSmoothingGroups -v true')
    mel.eval('FBXExportSmoothMesh -v false')
    mel.eval('FBXExportInputConnections -v false')
    mel.eval('FBXExport -f "%s.fbx"' % base)
    print('FBX:', base + '.fbx')

    # OBJ
    cmds.loadPlugin('objExport', quiet=True)
    cmds.file(base + '.obj', force=True, exportAll=True, type='OBJexport',
              options='groups=1;ptgroups=1;materials=1;smoothing=1;normals=1')
    print('OBJ:', base + '.obj')

    # copia sin historial (los meshes quedan con sus vertices guardados)
    shapes = cmds.ls(type='mesh', noIntermediate=True, long=True) or []
    if shapes:
        cmds.delete(shapes, constructionHistory=True)
    cmds.file(rename=base + '_sin_historial.ma')
    cmds.file(save=True, type='mayaAscii', force=True)
    print('MA sin historial:', base + '_sin_historial.ma')


def main(paths):
    for path in paths:
        path = os.path.abspath(path)
        cmds.file(path, open=True, force=True, ignoreVersion=True)
        exportar_escena_actual(os.path.splitext(path)[0])


if __name__ == '__main__':
    if os.path.basename(sys.executable).lower().startswith('mayapy'):
        import maya.standalone
        maya.standalone.initialize(name='python')
        try:
            main(sys.argv[1:])
        finally:
            maya.standalone.uninitialize()
    else:
        # dentro de la interfaz de Maya: exporta la escena abierta
        escena = cmds.file(query=True, sceneName=True)
        if not escena:
            raise RuntimeError('Guarda o abre una escena antes de ejecutar el script')
        exportar_escena_actual(os.path.splitext(escena)[0])
