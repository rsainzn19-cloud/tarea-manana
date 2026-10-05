"""Puente opcional con Maya (mayapy) para lo que el lector nativo no reproduce.

Si Maya esta instalado, se le pide que abra la escena y la exporte a FBX en
una carpeta temporal; luego Blender importa ese FBX. Es el mismo enfoque de
MenuuTUX/maya-scene-io y de los importadores comerciales.
"""

import glob
import os
import subprocess
import sys
import tempfile

_EXPORT_SCRIPT = r'''
import sys
import maya.standalone
maya.standalone.initialize(name="python")
import maya.cmds as cmds
import maya.mel as mel
src, dst = sys.argv[1], sys.argv[2]
cmds.file(src, open=True, force=True, ignoreVersion=True, prompt=False)
cmds.loadPlugin("fbxmaya", quiet=True)
mel.eval("FBXResetExport")
mel.eval("FBXExportSmoothingGroups -v true")
mel.eval("FBXExportSmoothMesh -v false")
mel.eval("FBXExportInputConnections -v false")
mel.eval('FBXExport -f "%s"' % dst.replace("\\", "/"))
maya.standalone.uninitialize()
'''


def find_mayapy():
    """Busca mayapy en las rutas de instalacion habituales (la version mas nueva primero)."""
    patterns = []
    if sys.platform.startswith('win'):
        for root in (os.environ.get('ProgramFiles', r'C:\Program Files'), r'C:\Program Files'):
            patterns.append(os.path.join(root, 'Autodesk', 'Maya*', 'bin', 'mayapy.exe'))
    elif sys.platform == 'darwin':
        patterns.append('/Applications/Autodesk/maya*/Maya.app/Contents/bin/mayapy')
    else:
        patterns.append('/usr/autodesk/maya*/bin/mayapy')
        patterns.append('/opt/autodesk/maya*/bin/mayapy')
    found = []
    for pat in patterns:
        found.extend(glob.glob(pat))
    found = sorted(set(found), reverse=True)
    return found[0] if found else ''


def export_with_maya(mayapy, scene_path, timeout=600):
    """Exporta la escena a FBX con Maya y devuelve la ruta del FBX."""
    if not mayapy or not os.path.isfile(mayapy):
        raise RuntimeError('No se encontro mayapy (configuralo en las preferencias del add-on)')
    tmp = tempfile.mkdtemp(prefix='maya2blender_')
    script = os.path.join(tmp, 'exportar.py')
    fbx = os.path.join(tmp, os.path.splitext(os.path.basename(scene_path))[0] + '.fbx')
    with open(script, 'w', encoding='utf-8') as f:
        f.write(_EXPORT_SCRIPT)
    proc = subprocess.run([mayapy, script, os.path.abspath(scene_path), fbx],
                          capture_output=True, text=True, timeout=timeout)
    if proc.returncode != 0 or not os.path.isfile(fbx):
        tail = (proc.stderr or proc.stdout or '').strip().splitlines()[-5:]
        raise RuntimeError('Maya no pudo exportar la escena: %s' % ' | '.join(tail))
    return fbx
