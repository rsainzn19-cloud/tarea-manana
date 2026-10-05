"""Importador de escenas de Maya (.ma / .mb) para Blender, sin necesitar Maya.

Instalar: Edit > Preferences > Add-ons > Install from Disk... y elegir
maya2blender.zip. Luego: File > Import > Maya (.ma/.mb).

Dos motores:
* Nativo (Python puro): lee .mb y .ma, recupera la geometria guardada y
  re-ejecuta el historial de construccion para las operaciones soportadas.
* Maya (opcional): si Maya esta instalado, exporta la escena a FBX con mayapy
  y la importa. Sirve para lo que el motor nativo no reproduce.
"""

import os

bl_info = {
    'name': 'Maya (.ma/.mb) sin Maya',
    'author': 'tarea-manana',
    'version': (1, 1, 0),
    'blender': (4, 1, 0),
    'location': 'File > Import > Maya (.ma/.mb)',
    'description': 'Importa archivos Maya ASCII y Maya Binary sin tener Maya instalado',
    'category': 'Import-Export',
}


def read_maya_file(path):
    """Lee un .ma o .mb y devuelve un MayaScene (no necesita bpy)."""
    ext = os.path.splitext(path)[1].lower()
    if ext == '.mb':
        from .maya_mb import read_mb
        return read_mb(path)
    if ext == '.ma':
        from .maya_ma import read_ma
        return read_ma(path)
    raise ValueError('Extension no soportada: %s' % ext)


def load_maya_file(path, scale=1.0, convert_axes=True):
    """Lee el archivo con el motor nativo y crea los objetos en la escena actual."""
    from .blender_build import build_scene
    scene = read_maya_file(path)
    name = os.path.splitext(os.path.basename(path))[0]
    coll, stats = build_scene(scene, name, scale=scale, convert_axes=convert_axes,
                              angles_in_degrees=path.lower().endswith('.ma'))
    return coll, stats, scene.warnings


def load_with_maya(path, mayapy, scale=1.0):
    """Importa usando Maya (mayapy -> FBX -> Blender)."""
    import bpy
    from .maya_bridge import export_with_maya
    fbx = export_with_maya(mayapy, path)
    before = set(bpy.data.objects)
    bpy.ops.import_scene.fbx(filepath=fbx, global_scale=scale)
    return [o for o in bpy.data.objects if o not in before]


try:
    import bpy
    from bpy.props import BoolProperty, EnumProperty, FloatProperty, StringProperty
    from bpy_extras.io_utils import ImportHelper
except ImportError:  # usado fuera de Blender (lectores puros)
    bpy = None

if bpy is not None:
    class MAYA2BLENDER_Preferences(bpy.types.AddonPreferences):
        bl_idname = __name__

        mayapy: StringProperty(
            name='mayapy (opcional)', subtype='FILE_PATH',
            description='Ruta a mayapy.exe de Maya. Solo hace falta para archivos con '
                        'operaciones que el motor nativo no reproduce')

        def draw(self, context):
            from .maya_bridge import find_mayapy
            col = self.layout.column()
            col.prop(self, 'mayapy')
            if not self.mayapy:
                detected = find_mayapy()
                col.label(text=('Detectado: %s' % detected) if detected else
                          'Maya no detectado: se usa solo el motor nativo')

    def _mayapy(context):
        from .maya_bridge import find_mayapy
        prefs = context.preferences.addons.get(__name__)
        path = prefs.preferences.mayapy if prefs else ''
        return path or find_mayapy()

    class IMPORT_OT_maya_sin_maya(bpy.types.Operator, ImportHelper):
        """Importa un archivo de Maya (.ma/.mb)"""
        bl_idname = 'import_scene.maya_sin_maya'
        bl_label = 'Importar Maya (.ma/.mb)'
        bl_options = {'REGISTER', 'UNDO'}

        filter_glob: StringProperty(default='*.ma;*.mb', options={'HIDDEN'})
        engine: EnumProperty(
            name='Motor',
            items=[('AUTO', 'Automatico', 'Nativo; si algo no se puede reproducir y Maya esta '
                                          'instalado, usa Maya para todo el archivo'),
                   ('NATIVE', 'Nativo (sin Maya)', 'Solo Python, nunca abre Maya'),
                   ('MAYA', 'Maya', 'Exporta con mayapy a FBX y lo importa')],
            default='AUTO')
        scale: FloatProperty(name='Escala', default=1.0, min=0.0001, max=1000.0,
                             description='1 = 1 unidad de Maya a 1 m; 0.01 = centimetros a metros')
        convert_axes: BoolProperty(name='Y arriba -> Z arriba', default=True)

        def execute(self, context):
            mayapy = _mayapy(context)
            try:
                if self.engine == 'MAYA':
                    objs = load_with_maya(self.filepath, mayapy, self.scale)
                    self.report({'INFO'}, '%d objetos importados con Maya' % len(objs))
                    return {'FINISHED'}
                if self.engine == 'AUTO' and mayapy:
                    scene = read_maya_file(self.filepath)
                    pending = [w for w in scene.warnings if 'historial no evaluado' in w]
                    if pending:
                        objs = load_with_maya(self.filepath, mayapy, self.scale)
                        self.report({'INFO'}, '%d objetos importados con Maya (%d mallas con '
                                              'historial no soportado)' % (len(objs), len(pending)))
                        return {'FINISHED'}
                coll, stats, warnings = load_maya_file(self.filepath, self.scale, self.convert_axes)
            except Exception as exc:  # noqa: BLE001
                self.report({'ERROR'}, 'No se pudo importar: %s' % exc)
                return {'CANCELLED'}
            for w in warnings:
                print('[maya2blender]', w)
            msg = '%d mallas importadas' % stats['meshes']
            if stats['unevaluated']:
                msg += '; faltan %d (historial no soportado: ver consola o usar el motor Maya)' % \
                    len(stats['unevaluated'])
                self.report({'WARNING'}, msg)
            else:
                self.report({'INFO'}, msg)
            return {'FINISHED'}

    def _menu(self, context):
        self.layout.operator(IMPORT_OT_maya_sin_maya.bl_idname, text='Maya (.ma/.mb)')

    _classes = (MAYA2BLENDER_Preferences, IMPORT_OT_maya_sin_maya)

    def register():
        for cls in _classes:
            bpy.utils.register_class(cls)
        bpy.types.TOPBAR_MT_file_import.append(_menu)

    def unregister():
        bpy.types.TOPBAR_MT_file_import.remove(_menu)
        for cls in reversed(_classes):
            bpy.utils.unregister_class(cls)
