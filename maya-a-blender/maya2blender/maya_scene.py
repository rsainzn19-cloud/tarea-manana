"""Modelo de escena intermedio (sin bpy) compartido por los lectores .ma y .mb.

Los lectores llenan un ``MayaScene`` con nodos DAG (transforms y meshes) y
materiales; luego ``blender_build.py`` lo convierte en objetos de Blender.
Este modulo no importa ``bpy`` para que se pueda probar con Python normal.
"""

import math
import re


class MayaNode:
    """Un nodo de Maya tal como aparece en el archivo."""

    def __init__(self, ntype, name, parent_ref=None):
        self.type = ntype            # 'transform', 'mesh', 'lambert', ...
        self.name = name
        self.parent_ref = parent_ref  # nombre o ruta del padre (sin resolver)
        self.parent = None           # MayaNode resuelto
        self.children = []
        self.attrs = {}              # nombre corto -> valor ya decodificado
        self.mesh = None             # MeshData si es un shape con geometria

    @property
    def path(self):
        if self.parent is None:
            return '|' + self.name
        return self.parent.path + '|' + self.name

    def __repr__(self):
        return '<%s %s>' % (self.type, self.path)


class MeshData:
    """Geometria final de un shape de Maya, lista para crear un mesh."""

    def __init__(self):
        self.verts = []        # [(x, y, z)]
        self.faces = []        # [[v0, v1, ...]]
        self.hard_edges = set()  # {(a, b)} con a < b
        self.uv_sets = []      # [(nombre, [(u, v)], [[uv_idx por vertice de cara]])]
        self.face_sets = {}    # nombre de shadingEngine -> [indices de cara]


class MayaScene:
    def __init__(self):
        self.nodes = []              # en orden de creacion
        self.by_name = {}            # nombre corto -> [MayaNode]
        self.connections = []        # [(origen, destino)]
        self.materials = {}          # shadingEngine -> dict(color=(r,g,b), shader=nombre)
        self.whole_object_sg = {}    # ruta del shape -> shadingEngine
        self.linear_unit = 'cm'
        self.up_axis = 'y'
        self.warnings = []

    # -- construccion -----------------------------------------------------
    def add_node(self, node):
        if node.parent_ref:
            node.parent = self.resolve(node.parent_ref)
            if node.parent is not None:
                node.parent.children.append(node)
        self.nodes.append(node)
        self.by_name.setdefault(node.name, []).append(node)
        return node

    def resolve(self, ref):
        """Resuelve un nombre o ruta parcial ('a|b') al nodo correspondiente."""
        if ref is None:
            return None
        ref = ref.lstrip(':')
        parts = [p for p in ref.split('|') if p]
        if not parts:
            return None
        candidates = self.by_name.get(parts[-1], [])
        if len(parts) == 1:
            return candidates[-1] if candidates else None
        absolute = ref.startswith('|')
        for cand in reversed(candidates):
            path = cand.path.split('|')[1:]
            if absolute and path == parts:
                return cand
            if not absolute and path[-len(parts):] == parts:
                return cand
        return candidates[-1] if candidates else None

    def node_of_plug(self, plug):
        """'a|b.attr[0].x' -> (MayaNode, 'attr[0].x')."""
        plug = plug.lstrip(':')
        # el nombre del nodo termina en el primer '.' que no esta dentro de []
        depth = 0
        for i, ch in enumerate(plug):
            if ch == '[':
                depth += 1
            elif ch == ']':
                depth -= 1
            elif ch == '.' and depth == 0:
                return self.resolve(plug[:i]), plug[i + 1:]
        return self.resolve(plug), ''

    # -- consultas --------------------------------------------------------
    def dag_roots(self):
        return [n for n in self.nodes if n.parent is None and n.type == 'transform']


# ---------------------------------------------------------------------------
# Utilidades compartidas
# ---------------------------------------------------------------------------

_RANGE_RE = re.compile(r'^(?P<base>.*)\[(?P<a>-?\d+)(?::(?P<b>-?\d+))?\]$')


def split_index_range(attr):
    """'pt[3:7]' -> ('pt', 3, 7); 'pt[4]' -> ('pt', 4, 4); 'pt' -> ('pt', None, None)."""
    m = _RANGE_RE.match(attr)
    if not m:
        return attr, None, None
    a = int(m.group('a'))
    b = int(m.group('b')) if m.group('b') is not None else a
    return m.group('base'), a, b


def parse_component_list(items):
    """['f[0:3]', 'f[7]'] -> {'f': [0,1,2,3,7]}; acepta tambien 'f[*]'."""
    out = {}
    for item in items:
        m = re.match(r'^(\w+)\[(\*|\d+)(?::(\d+))?\]$', item.strip())
        if not m:
            continue
        kind = m.group(1)
        if m.group(2) == '*':
            out.setdefault(kind, []).append('*')
            continue
        a = int(m.group(2))
        b = int(m.group(3)) if m.group(3) else a
        out.setdefault(kind, []).extend(range(a, b + 1))
    return out


# --- matrices 4x4 (convencion de Maya: vectores fila, p' = p * M) -----------

def mat_identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_translate(v):
    m = mat_identity()
    m[3][0], m[3][1], m[3][2] = v
    return m


def mat_scale(v):
    m = mat_identity()
    m[0][0], m[1][1], m[2][2] = v
    return m


def mat_shear(sh):
    xy, xz, yz = sh
    m = mat_identity()
    m[1][0] = xy
    m[2][0] = xz
    m[2][1] = yz
    return m


def _rot_axis(axis, ang):
    c, s = math.cos(ang), math.sin(ang)
    m = mat_identity()
    if axis == 0:
        m[1][1], m[1][2], m[2][1], m[2][2] = c, s, -s, c
    elif axis == 1:
        m[0][0], m[0][2], m[2][0], m[2][2] = c, -s, s, c
    else:
        m[0][0], m[0][1], m[1][0], m[1][1] = c, s, -s, c
    return m


# rotateOrder de Maya: 0 xyz, 1 yzx, 2 zxy, 3 xzy, 4 yxz, 5 zyx
_ROT_ORDERS = [(0, 1, 2), (1, 2, 0), (2, 0, 1), (0, 2, 1), (1, 0, 2), (2, 1, 0)]


def mat_rotate(r, order=0):
    """r en radianes. Con vectores fila, 'xyz' aplica primero X, luego Y, luego Z."""
    m = mat_identity()
    for axis in _ROT_ORDERS[int(order) % 6]:
        m = mat_mul(m, _rot_axis(axis, r[axis]))
    return m


def transform_matrix(attrs, angles_in_degrees=False):
    """Matriz local de un transform de Maya (incluye pivotes)."""
    def v3(key, default):
        val = attrs.get(key)
        if val is None:
            return default
        return tuple(float(x) for x in val)

    t = v3('t', (0.0, 0.0, 0.0))
    r = v3('r', (0.0, 0.0, 0.0))
    s = v3('s', (1.0, 1.0, 1.0))
    sh = v3('sh', (0.0, 0.0, 0.0))
    ra = v3('ra', (0.0, 0.0, 0.0))
    rp = v3('rp', (0.0, 0.0, 0.0))
    sp = v3('sp', (0.0, 0.0, 0.0))
    rpt = v3('rpt', (0.0, 0.0, 0.0))
    spt = v3('spt', (0.0, 0.0, 0.0))
    ro = attrs.get('ro', 0) or 0
    if angles_in_degrees:
        r = tuple(math.radians(x) for x in r)
        ra = tuple(math.radians(x) for x in ra)

    neg = lambda v: (-v[0], -v[1], -v[2])
    m = mat_translate(neg(sp))
    m = mat_mul(m, mat_scale(s))
    m = mat_mul(m, mat_shear(sh))
    m = mat_mul(m, mat_translate(sp))
    m = mat_mul(m, mat_translate(spt))
    m = mat_mul(m, mat_translate(neg(rp)))
    m = mat_mul(m, mat_rotate(ra, 0))
    m = mat_mul(m, mat_rotate(r, ro))
    m = mat_mul(m, mat_translate(rp))
    m = mat_mul(m, mat_translate(rpt))
    m = mat_mul(m, mat_translate(t))
    return m
