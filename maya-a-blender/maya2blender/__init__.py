"""Importador de escenas de Maya (.ma / .mb) para Blender, sin necesitar Maya.

Instalar: Edit > Preferences > Add-ons > Install... y elegir el .zip de esta
carpeta (o copiar la carpeta maya2blender a scripts/addons). Luego:
File > Import > Maya sin Maya (.ma/.mb).
"""

import os

bl_info = {
    'name': 'Maya sin Maya (.ma/.mb)',
    'author': 'tarea-manana',
    'version': (1, 0, 0),
    'blender': (4, 1, 0),
    'location': 'File > Import > Maya sin Maya (.ma/.mb)',
    'description': 'Importa geometria de archivos Maya ASCII y Maya Binary sin tener Maya instalado',
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
    """Lee el archivo y crea los objetos en la escena de Blender actual."""
    from .blender_build import build_scene
    scene = read_maya_file(path)
    name = os.path.splitext(os.path.basename(path))[0]
    coll, stats = build_scene(scene, name, scale=scale, convert_axes=convert_axes,
                              angles_in_degrees=path.lower().endswith('.ma'))
    return coll, stats, scene.warnings


try:
    import bpy
    from bpy.props import BoolProperty, FloatProperty, StringProperty
    from bpy_extras.io_utils import ImportHelper
except ImportError:  # usado fuera de Blender (lectores puros)
    bpy = None

if bpy is not None:
    class IMPORT_OT_maya_sin_maya(bpy.types.Operator, ImportHelper):
        """Importa un archivo de Maya (.ma/.mb) sin Maya"""
        bl_idname = 'import_scene.maya_sin_maya'
        bl_label = 'Importar Maya (.ma/.mb)'
        bl_options = {'REGISTER', 'UNDO'}

        filter_glob: StringProperty(default='*.ma;*.mb', options={'HIDDEN'})
        scale: FloatProperty(name='Escala', default=1.0, min=0.0001, max=1000.0,
                             description='1 = 1 unidad de Maya a 1 m; 0.01 = centimetros a metros')
        convert_axes: BoolProperty(name='Y arriba -> Z arriba', default=True)

        def execute(self, context):
            try:
                coll, stats, warnings = load_maya_file(self.filepath, self.scale, self.convert_axes)
            except Exception as exc:  # noqa: BLE001
                self.report({'ERROR'}, 'No se pudo importar: %s' % exc)
                return {'CANCELLED'}
            msg = '%d meshes importados' % stats['meshes']
            if stats['unevaluated']:
                msg += '; sin historial evaluado: %s' % ', '.join(stats['unevaluated'])
                self.report({'WARNING'}, msg)
            else:
                self.report({'INFO'}, msg)
            for w in warnings:
                print('[maya2blender]', w)
            return {'FINISHED'}

    def _menu(self, context):
        self.layout.operator(IMPORT_OT_maya_sin_maya.bl_idname, text='Maya sin Maya (.ma/.mb)')

    def register():
        bpy.utils.register_class(IMPORT_OT_maya_sin_maya)
        bpy.types.TOPBAR_MT_file_import.append(_menu)

    def unregister():
        bpy.types.TOPBAR_MT_file_import.remove(_menu)
        bpy.utils.unregister_class(IMPORT_OT_maya_sin_maya)
