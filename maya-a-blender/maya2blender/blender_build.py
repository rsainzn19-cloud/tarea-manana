"""Convierte un MayaScene (maya_scene.py) en objetos de Blender."""

import math

import bpy
import mathutils

from .maya_scene import transform_matrix

DEFAULT_CAMERAS = {'persp', 'top', 'front', 'side', 'perspShape', 'topShape', 'frontShape', 'sideShape'}
SKIP_SHAPE_TYPES = {'camera', 'imagePlane', 'DCAM'}


def _to_blender_matrix(m):
    """Maya usa vectores fila (p * M); Blender columnas (M @ p): se transpone."""
    return mathutils.Matrix([[m[j][i] for j in range(4)] for i in range(4)])


def _axis_conversion(up_axis, scale):
    conv = mathutils.Matrix.Identity(4)
    if up_axis == 'y':
        conv = mathutils.Matrix.Rotation(math.radians(90.0), 4, 'X')
    return conv @ mathutils.Matrix.Scale(scale, 4)


def _make_material(name, color):
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes.get('Principled BSDF')
        if bsdf is not None:
            bsdf.inputs['Base Color'].default_value = (color[0], color[1], color[2], 1.0)
            bsdf.inputs['Roughness'].default_value = 0.6
        mat.diffuse_color = (color[0], color[1], color[2], 1.0)
    return mat


def build_mesh(name, data, scene, shape_path):
    """Crea un bpy.types.Mesh a partir de MeshData."""
    me = bpy.data.meshes.new(name)
    nv = len(data.verts)
    good = [i for i, f in enumerate(data.faces)
            if len(f) >= 3 and len(set(f)) == len(f) and all(0 <= v < nv for v in f)]

    me.vertices.add(nv)
    me.vertices.foreach_set('co', [c for v in data.verts for c in v])
    loop_starts, loop_verts = [], []
    for i in good:
        loop_starts.append(len(loop_verts))
        loop_verts.extend(data.faces[i])
    me.loops.add(len(loop_verts))
    me.loops.foreach_set('vertex_index', loop_verts)
    me.polygons.add(len(good))
    me.polygons.foreach_set('loop_start', loop_starts)
    me.update(calc_edges=True)

    # UVs (cada set de Maya -> un UV map)
    for uv_name, uvs, per_face in data.uv_sets:
        if not uvs:
            continue
        layer = me.uv_layers.new(name=uv_name)
        flat = []
        for i in good:
            for idx in per_face[i]:
                u, v = uvs[idx] if 0 <= idx < len(uvs) else (0.0, 0.0)
                flat.extend((u, v))
        layer.data.foreach_set('uv', flat)

    # sombreado: suave + aristas duras de Maya como "sharp"
    if data.hard_edges:
        edge_index = {}
        for e in me.edges:
            a, b = e.vertices
            edge_index[(min(a, b), max(a, b))] = e.index
        sharp = [False] * len(me.edges)
        for key in data.hard_edges:
            idx = edge_index.get(key)
            if idx is not None:
                sharp[idx] = True
        attr = me.attributes.get('sharp_edge') or me.attributes.new('sharp_edge', 'BOOLEAN', 'EDGE')
        attr.data.foreach_set('value', sharp)
    if hasattr(me, 'shade_smooth'):
        me.shade_smooth()
    else:  # Blender < 4.1
        me.polygons.foreach_set('use_smooth', [True] * len(me.polygons))

    # materiales
    slots = []
    whole = scene.whole_object_sg.get(shape_path)
    if data.face_sets:
        new_index = {old: k for k, old in enumerate(good)}
        mat_idx = [0] * len(good)
        slots.append(_make_material('lambert1', (0.5, 0.5, 0.5)))
        for sg, faces in data.face_sets.items():
            info = scene.materials.get(sg, {})
            slots.append(_make_material(info.get('shader', sg), info.get('color', (0.5, 0.5, 0.5))))
            for f in faces:
                if f in new_index:
                    mat_idx[new_index[f]] = len(slots) - 1
        me.polygons.foreach_set('material_index', mat_idx)
    elif whole and whole in scene.materials:
        info = scene.materials[whole]
        slots.append(_make_material(info.get('shader', whole), info.get('color', (0.5, 0.5, 0.5))))
    else:
        slots.append(_make_material('lambert1', (0.5, 0.5, 0.5)))
    for mat in slots:
        me.materials.append(mat)

    me.validate(clean_customdata=False)
    me.update()
    return me


def build_scene(scene, collection_name, scale=1.0, convert_axes=True,
                include_intermediate=True, angles_in_degrees=False):
    """Crea los objetos en una coleccion nueva y devuelve (coleccion, stats)."""
    coll = bpy.data.collections.new(collection_name)
    bpy.context.scene.collection.children.link(coll)
    extra = None
    stats = {'meshes': 0, 'empties': 0, 'skipped': [], 'unevaluated': []}

    conv = _axis_conversion(scene.up_axis, scale) if convert_axes else mathutils.Matrix.Scale(scale, 4)
    objects = {}

    def shapes_of(node):
        return [c for c in node.children if c.type not in ('transform',)]

    def is_camera_xform(node):
        sh = shapes_of(node)
        return node.name in DEFAULT_CAMERAS or (sh and all(s.type in SKIP_SHAPE_TYPES for s in sh)
                                                 and not any(c.type == 'transform' for c in node.children))

    def visit(node, parent_obj):
        nonlocal extra
        if node.type != 'transform' or is_camera_xform(node):
            return
        shapes = shapes_of(node)
        mesh_shapes = [s for s in shapes if s.type == 'mesh']
        visible = [s for s in mesh_shapes if not s.attrs.get('io') and s.mesh is not None]
        hidden_hist = [s for s in mesh_shapes if not s.attrs.get('io') and s.mesh is None]
        inter = [s for s in mesh_shapes if s.attrs.get('io') and s.mesh is not None]

        data = None
        if visible:
            data = build_mesh(visible[0].name, visible[0].mesh, scene, visible[0].path)
            stats['meshes'] += 1
        obj = bpy.data.objects.new(node.name, data)
        if data is None:
            obj.empty_display_size = 0.5 * scale
            stats['empties'] += 1
        coll.objects.link(obj)
        objects[node.path] = obj

        local = _to_blender_matrix(transform_matrix(node.attrs, angles_in_degrees))
        if parent_obj is None:
            obj.matrix_basis = conv @ local
        else:
            obj.parent = parent_obj
            obj.matrix_basis = local
        if node.attrs.get('v') in (0, 0.0, False):
            obj.hide_viewport = True
            obj.hide_render = True

        # shapes extra bajo el mismo transform
        for s in visible[1:]:
            child = bpy.data.objects.new(s.name, build_mesh(s.name, s.mesh, scene, s.path))
            child.parent = obj
            coll.objects.link(child)
            stats['meshes'] += 1

        # mesh con historial que no se pudo evaluar: se importa la geometria
        # original (el shape intermedio) aparte y oculta.
        if hidden_hist:
            stats['unevaluated'].append(node.name)
            if include_intermediate and inter:
                if extra is None:
                    extra = bpy.data.collections.new(collection_name + ' - original sin historial')
                    coll.children.link(extra)
                for s in inter:
                    o = bpy.data.objects.new(node.name + '_ORIGINAL', build_mesh(s.name, s.mesh, scene, s.path))
                    o.parent = obj
                    extra.objects.link(o)
                    o.hide_set(True) if bpy.context.view_layer.objects.get(o.name) else None
                    o.hide_render = True

        for c in node.children:
            if c.type == 'transform':
                visit(c, obj)

    for root in scene.dag_roots():
        visit(root, None)
    return coll, stats
