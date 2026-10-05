"""Evaluacion del historial de construccion de Maya sin Maya.

Un mesh con historial no guarda sus vertices en el .ma: guarda la "receta"
(polySphere -> polyTweak -> deleteComponent ...). Aqui se re-ejecuta esa
receta imitando el orden de indices de Maya (vertices, aristas y caras), que
es lo que permite que cada operacion siguiente seleccione las mismas
componentes que en Maya.

Si aparece una operacion no soportada se lanza NotImplementedError con su
nombre; el importador avisa y (si esta configurado) puede usar Maya.

Convenciones verificadas contra primitivas guardadas sin historial en
repositorios publicos de GitHub (ver README).
"""

import math

from .maya_scene import MeshData, parse_component_list

# ---------------------------------------------------------------------------
# Mesh con el orden de componentes de Maya
# ---------------------------------------------------------------------------


class PMesh:
    def __init__(self):
        self.verts = []    # [[x, y, z]]
        self.faces = []    # [[v0, v1, ...]]  (vertices en orden de la cara)
        self.edges = []    # [(a, b)]          (orden de aristas de Maya)
        self.hard = []     # [bool] por arista
        self.uvs = []      # [(u, v)]
        self.fuv = []      # por cara: [uv_idx por vertice] o None
        self.exact = True  # False si alguna operacion no reproduce el orden de Maya
        self.subdiv = 0    # niveles de polySmoothFace pendientes (modificador en Blender)

    def copy(self):
        m = PMesh()
        m.verts = [list(v) for v in self.verts]
        m.faces = [list(f) for f in self.faces]
        m.edges = list(self.edges)
        m.hard = list(self.hard)
        m.uvs = list(self.uvs)
        m.fuv = [list(f) if f is not None else None for f in self.fuv]
        m.exact = self.exact
        m.subdiv = self.subdiv
        return m

    # -- utilidades ---------------------------------------------------------
    def edge_index(self):
        return {(min(a, b), max(a, b)): i for i, (a, b) in enumerate(self.edges)}

    def add_edge(self, a, b, hard=False, index=None):
        """Agrega la arista si no existe; devuelve su indice."""
        if index is None:
            index = self.edge_index()
        key = (min(a, b), max(a, b))
        if key in index:
            return index[key]
        self.edges.append((a, b))
        self.hard.append(hard)
        index[key] = len(self.edges) - 1
        return index[key]

    def rebuild_edges_from_faces(self):
        """Aristas en orden de primera aparicion en las caras (orden de MFnMesh.create)."""
        old_hard = {(min(a, b), max(a, b)) for (a, b), h in zip(self.edges, self.hard) if h}
        self.edges, self.hard = [], []
        index = {}
        for f in self.faces:
            for k in range(len(f)):
                a, b = f[k], f[(k + 1) % len(f)]
                key = (min(a, b), max(a, b))
                if key not in index:
                    index[key] = len(self.edges)
                    self.edges.append((a, b))
                    self.hard.append(key in old_hard)

    def face_edges(self, fi, index=None):
        index = index or self.edge_index()
        f = self.faces[fi]
        return [index[(min(f[k], f[(k + 1) % len(f)]), max(f[k], f[(k + 1) % len(f)]))] for k in range(len(f))]

    def compact(self, keep_faces=None):
        """Elimina caras marcadas y luego aristas/vertices sin uso, conservando el orden."""
        if keep_faces is not None:
            self.faces = [f for f, k in zip(self.faces, keep_faces) if k]
            self.fuv = [f for f, k in zip(self.fuv, keep_faces) if k]
        used_e = set()
        for f in self.faces:
            for k in range(len(f)):
                used_e.add((min(f[k], f[(k + 1) % len(f)]), max(f[k], f[(k + 1) % len(f)])))
        keep_e = [(min(a, b), max(a, b)) in used_e for a, b in self.edges]
        self.edges = [e for e, k in zip(self.edges, keep_e) if k]
        self.hard = [h for h, k in zip(self.hard, keep_e) if k]
        used_v = set()
        for a, b in self.edges:
            used_v.add(a)
            used_v.add(b)
        remap = {}
        verts = []
        for i, v in enumerate(self.verts):
            if i in used_v:
                remap[i] = len(verts)
                verts.append(v)
        self.verts = verts
        self.faces = [[remap[v] for v in f] for f in self.faces]
        self.edges = [(remap[a], remap[b]) for a, b in self.edges]
        used_uv = sorted({u for f in self.fuv if f for u in f if u is not None and u >= 0})
        if used_uv and len(used_uv) != len(self.uvs):
            uvmap = {u: k for k, u in enumerate(used_uv)}
            self.uvs = [self.uvs[u] for u in used_uv]
            self.fuv = [[uvmap.get(u, -1) for u in f] if f else f for f in self.fuv]

    # -- conversion -------------------------------------------------------
    @classmethod
    def from_meshdata(cls, md):
        m = cls()
        m.verts = [list(v) for v in md.verts]
        m.faces = [list(f) for f in md.faces]
        if md.edges:
            m.edges = [tuple(e) for e in md.edges]
            m.hard = [(min(a, b), max(a, b)) in md.hard_edges for a, b in m.edges]
        else:
            m.rebuild_edges_from_faces()
            m.hard = [(min(a, b), max(a, b)) in md.hard_edges for a, b in m.edges]
            m.exact = False
        if md.uv_sets:
            _, uvs, per_face = md.uv_sets[0]
            m.uvs = list(uvs)
            m.fuv = [list(f) for f in per_face]
        else:
            m.fuv = [None] * len(m.faces)
        return m

    def to_meshdata(self):
        md = MeshData()
        md.verts = [tuple(v) for v in self.verts]
        md.faces = [list(f) for f in self.faces]
        md.edges = list(self.edges)
        md.hard_edges = {(min(a, b), max(a, b)) for (a, b), h in zip(self.edges, self.hard) if h}
        md.subdivision = self.subdiv
        if self.uvs and any(f is not None for f in self.fuv):
            md.uv_sets = [('map1', list(self.uvs),
                           [f if f is not None else [-1] * len(face) for f, face in zip(self.fuv, self.faces)])]
        return md


# ---------------------------------------------------------------------------
# Primitivas (orden de indices igual al de Maya)
# ---------------------------------------------------------------------------

def _ring_angle(i, sa):
    # Maya numera los anillos empezando en -360/sa grados y avanzando en sentido horario
    return -2.0 * math.pi * (i + 1) / sa


def _axis_rotation(ax):
    """Matriz 3x3 que lleva el eje Y al eje 'ax' del primitivo."""
    if ax is None:
        return None
    x, y, z = ax
    n = math.sqrt(x * x + y * y + z * z) or 1.0
    x, y, z = x / n, y / n, z / n
    if abs(x) < 1e-9 and abs(z) < 1e-9:
        return None if y > 0 else [[1, 0, 0], [0, -1, 0], [0, 0, -1]]
    # rotacion de Rodrigues de (0,1,0) a (x,y,z)
    kx, kz = z, -x                   # (0,1,0) x (x,y,z), componente y = 0
    s = math.sqrt(kx * kx + kz * kz)
    c = y
    kx, kz = kx / s, kz / s
    t = 1 - c
    return [[t * kx * kx + c, -kz * s, t * kx * kz],
            [kz * s, c, -kx * s],
            [t * kx * kz, kx * s, t * kz * kz + c]]


def _apply_axis(m, attrs):
    rot = _axis_rotation(attrs.get('ax'))
    if rot is None:
        return m
    m.verts = [[rot[0][0] * x + rot[0][1] * y + rot[0][2] * z,
                rot[1][0] * x + rot[1][1] * y + rot[1][2] * z,
                rot[2][0] * x + rot[2][1] * y + rot[2][2] * z] for x, y, z in m.verts]
    return m


def _grid_quads(m, rows, cols, start, uv_start=None, uv_cols=None, closed=True):
    """Caras de una banda de anillos (cilindro, esfera, toro)."""
    for k in range(rows):
        for i in range(cols):
            i2 = (i + 1) % cols if closed else i + 1
            a = start + k * cols + i
            b = start + k * cols + i2
            c = start + (k + 1) * cols + i2
            d = start + (k + 1) * cols + i
            m.faces.append([a, b, c, d])
            if uv_start is not None:
                u = uv_start + k * uv_cols + i
                m.fuv.append([u, u + 1, u + uv_cols + 1, u + uv_cols])
            else:
                m.fuv.append(None)


def prim_cube(a):
    """polyCube con subdivisiones.

    Orden de Maya: un anillo de filas alrededor del eje X (frente -> arriba ->
    atras -> abajo), luego los vertices interiores del lado derecho (+X) y del
    izquierdo (-X). Verificado con cubos 1x1x1 y 3x3x3 guardados sin historial.
    """
    w = a.get('w', 1.0)
    h = a.get('h', 1.0)
    d = a.get('d', 1.0)
    sw, sh, sd = max(1, int(a.get('sw', 1))), max(1, int(a.get('sh', 1))), max(1, int(a.get('sd', 1)))
    m = PMesh()
    R = 2 * (sh + sd)
    n = sw + 1

    def row_pos(r):
        if r <= sh:
            return -h / 2 + h * r / sh, d / 2
        if r <= sh + sd:
            return h / 2, d / 2 - d * (r - sh) / sd
        if r <= 2 * sh + sd:
            return h / 2 - h * (r - sh - sd) / sh, -d / 2
        return -h / 2, -d / 2 + d * (r - 2 * sh - sd) / sd

    def row_v(r):
        if r <= sh:
            return 0.25 * r / sh
        if r <= sh + sd:
            return 0.25 + 0.25 * (r - sh) / sd
        if r <= 2 * sh + sd:
            return 0.5 + 0.25 * (r - sh - sd) / sh
        return 0.75 + 0.25 * (r - 2 * sh - sd) / sd

    for r in range(R):
        y, z = row_pos(r)
        for i in range(n):
            m.verts.append([-w / 2 + w * i / sw, y, z])
    side_start = {}
    for side, x in ((1, w / 2), (0, -w / 2)):
        side_start[side] = len(m.verts)
        for yy in range(1, sh):
            for zz in range(1, sd):
                m.verts.append([x, -h / 2 + h * yy / sh, -d / 2 + d * zz / sd])

    def ring_index(r, i):
        return (r % R) * n + i

    def grid(side, yy, zz):
        """Vertice del lado (+X si side=1) en altura yy (0..sh) y profundidad zz (0 atras .. sd frente)."""
        i = sw if side else 0
        if yy == 0:
            return ring_index(2 * sh + sd + zz, i)
        if yy == sh:
            return ring_index(sh + (sd - zz), i)
        if zz == 0:
            return ring_index(2 * sh + sd - yy, i)
        if zz == sd:
            return ring_index(yy, i)
        return side_start[side] + (yy - 1) * (sd - 1) + (zz - 1)

    # UVs: anillo + extras de cada lado (como el cubo por defecto de Maya)
    for r in range(R + 1):
        for i in range(n):
            m.uvs.append((0.375 + 0.25 * i / sw, row_v(r)))
    side_uv = {}
    for side in (1, 0):
        side_uv[side] = {}
        for yy in range(sh + 1):
            for zz in range(sd):
                side_uv[side][(yy, zz)] = len(m.uvs)
                if side:
                    m.uvs.append((0.625 + 0.25 * (sd - zz) / sd, 0.25 * yy / sh))
                else:
                    m.uvs.append((0.125 + 0.25 * zz / sd, 0.25 * yy / sh))

    def guv(side, yy, zz):
        if zz == sd:
            return yy * n + (sw if side else 0)
        return side_uv[side][(yy, zz)]

    for r in range(R):
        for i in range(sw):
            m.faces.append([ring_index(r, i), ring_index(r, i + 1), ring_index(r + 1, i + 1), ring_index(r + 1, i)])
            m.fuv.append([r * n + i, r * n + i + 1, (r + 1) * n + i + 1, (r + 1) * n + i])
    for yy in range(sh):
        for zz in range(sd):
            m.faces.append([grid(1, yy, zz + 1), grid(1, yy, zz), grid(1, yy + 1, zz), grid(1, yy + 1, zz + 1)])
            m.fuv.append([guv(1, yy, zz + 1), guv(1, yy, zz), guv(1, yy + 1, zz), guv(1, yy + 1, zz + 1)])
    for yy in range(sh):
        for zz in range(sd):
            m.faces.append([grid(0, yy, zz), grid(0, yy, zz + 1), grid(0, yy + 1, zz + 1), grid(0, yy + 1, zz)])
            m.fuv.append([guv(0, yy, zz), guv(0, yy, zz + 1), guv(0, yy + 1, zz + 1), guv(0, yy + 1, zz)])

    corner_rows = {0, sh, sh + sd, 2 * sh + sd}
    for r in range(R):
        for i in range(sw):
            m.edges.append((ring_index(r, i), ring_index(r, i + 1)))
            m.hard.append(r in corner_rows)
    for r in range(R):
        for i in range(n):
            m.edges.append((ring_index(r, i), ring_index(r + 1, i)))
            m.hard.append(i in (0, sw))
    for side in (1, 0):
        for yy in range(1, sh):
            for zz in range(sd):
                m.edges.append((grid(side, yy, zz), grid(side, yy, zz + 1)))
                m.hard.append(False)
        for yy in range(sh):
            for zz in range(1, sd):
                m.edges.append((grid(side, yy, zz), grid(side, yy + 1, zz)))
                m.hard.append(False)
    return _apply_axis(m, a)


def prim_plane(a):
    w = a.get('w', 1.0)
    h = a.get('h', 1.0)
    sw, sh = int(a.get('sw', 10)), int(a.get('sh', 10))
    m = PMesh()
    for j in range(sh + 1):
        for i in range(sw + 1):
            m.verts.append([-w / 2.0 + w * i / sw, 0.0, h / 2.0 - h * j / sh])
            m.uvs.append((i / sw, j / sh))
    _grid_quads(m, sh, sw + 1, 0, 0, sw + 1, closed=False)
    # quitar las caras "de mas" que genera la banda abierta
    m.faces = [f for k, f in enumerate(m.faces) if k % (sw + 1) != sw]
    m.fuv = [f for k, f in enumerate(m.fuv) if k % (sw + 1) != sw]
    # aristas: por vertice (fila a fila), primero la horizontal y luego la vertical
    n = sw + 1
    for j in range(sh + 1):
        for i in range(sw + 1):
            v = j * n + i
            if i < sw:
                m.edges.append((v, v + 1))
                m.hard.append(j in (0, sh))
            if j < sh:
                m.edges.append((v, v + n))
                m.hard.append(i in (0, sw))
    return _apply_axis(m, a)


def prim_cylinder(a):
    r = a.get('r', 1.0)
    h = a.get('h', 2.0)
    sa, sh = int(a.get('sa', 20)), int(a.get('sh', 1))
    sc = int(a.get('sc', 1))
    if sc > 1:
        raise NotImplementedError('polyCylinder con subdivisionsCaps > 1')
    m = PMesh()
    for k in range(sh + 1):
        y = -h / 2.0 + h * k / sh
        for i in range(sa):
            t = _ring_angle(i, sa)
            m.verts.append([r * math.cos(t), y, r * math.sin(t)])
    # UVs: banda lateral + dos tapas circulares
    for k in range(sh + 1):
        for i in range(sa + 1):
            m.uvs.append((0.375 + 0.25 * i / sa, 0.3125 + 0.375 * k / sh))
    _grid_quads(m, sh, sa, 0)
    m.fuv = []
    for k in range(sh):
        for i in range(sa):
            u = k * (sa + 1) + i
            m.fuv.append([u, u + 1, u + sa + 2, u + sa + 1])
    side_uv = len(m.uvs)
    for c in ((0.5, 0.15625), (0.5, 0.84375)):
        for i in range(sa):
            t = _ring_angle(i, sa)
            m.uvs.append((c[0] + 0.15625 * math.cos(t), c[1] - 0.15625 * math.sin(t)))
    bottom_uv, top_uv = side_uv, side_uv + sa
    top = sh * sa
    if sc == 1:
        bc = len(m.verts)
        m.verts.append([0.0, -h / 2.0, 0.0])
        m.verts.append([0.0, h / 2.0, 0.0])
        tc = bc + 1
        m.uvs.append((0.5, 0.15625))
        m.uvs.append((0.5, 0.84375))
        for i in range(sa):
            m.faces.append([(i + 1) % sa, i, bc])
            m.fuv.append([bottom_uv + (i + 1) % sa, bottom_uv + i, len(m.uvs) - 2])
        for i in range(sa):
            m.faces.append([top + i, top + (i + 1) % sa, tc])
            m.fuv.append([top_uv + i, top_uv + (i + 1) % sa, len(m.uvs) - 1])
    elif sc == 0:
        m.faces.append([(sa - i) % sa for i in range(sa)])
        m.fuv.append([bottom_uv + (sa - i) % sa for i in range(sa)])
        m.faces.append([top + i for i in range(sa)])
        m.fuv.append([top_uv + i for i in range(sa)])
    # aristas: anillos, verticales, radios de la tapa inferior y superior
    for k in range(sh + 1):
        for i in range(sa):
            m.edges.append((k * sa + i, k * sa + (i + 1) % sa))
            m.hard.append(k in (0, sh))
    for k in range(sh):
        for i in range(sa):
            m.edges.append((k * sa + i, (k + 1) * sa + i))
            m.hard.append(False)
    if sc == 1:
        for i in range(sa):
            m.edges.append((bc, i))
            m.hard.append(False)
        for i in range(sa):
            m.edges.append((top + i, tc))
            m.hard.append(False)
    return _apply_axis(m, a)


def prim_cone(a):
    r = a.get('r', 1.0)
    h = a.get('h', 2.0)
    sa, sh = int(a.get('sa', 20)), int(a.get('sh', 1))
    sc = int(a.get('sc', 1))
    if sh != 1 or sc > 1:
        raise NotImplementedError('polyCone con subdivisiones de altura/tapa')
    m = PMesh()
    for i in range(sa):
        t = _ring_angle(i, sa)
        m.verts.append([r * math.cos(t), -h / 2.0, r * math.sin(t)])
    apex_uv_c = (0.5, 0.75)
    for i in range(sa):
        t = _ring_angle(i, sa)
        m.uvs.append((0.5 + 0.25 * math.cos(t), 0.25 - 0.25 * math.sin(t)))
    if sc == 1:
        bc = len(m.verts)
        m.verts.append([0.0, -h / 2.0, 0.0])
    apex = len(m.verts)
    m.verts.append([0.0, h / 2.0, 0.0])
    side0 = len(m.uvs)
    for i in range(sa + 1):
        t = -2.0 * math.pi * i / sa
        m.uvs.append((apex_uv_c[0] + 0.25 * math.cos(t), apex_uv_c[1] - 0.25 * math.sin(t)))
    m.uvs.append((0.5, 0.25))
    m.uvs.append(apex_uv_c)
    cuv, auv = len(m.uvs) - 2, len(m.uvs) - 1
    if sc == 1:
        for i in range(sa):
            m.faces.append([(i + 1) % sa, i, bc])
            m.fuv.append([(i + 1) % sa, i, cuv])
    else:
        m.faces.append([(sa - i) % sa for i in range(sa)])
        m.fuv.append([(sa - i) % sa for i in range(sa)])
    for i in range(sa):
        m.faces.append([i, (i + 1) % sa, apex])
        m.fuv.append([side0 + i, side0 + i + 1, auv])
    for i in range(sa):
        m.edges.append((i, (i + 1) % sa))
        m.hard.append(True)
    if sc == 1:
        for i in range(sa):
            m.edges.append((bc, i))
            m.hard.append(False)
    for i in range(sa):
        m.edges.append((i, apex))
        m.hard.append(False)
    return _apply_axis(m, a)


def prim_sphere(a):
    r = a.get('r', 1.0)
    sa, sh = int(a.get('sa', 20)), int(a.get('sh', 20))
    m = PMesh()
    for k in range(1, sh):
        phi = math.pi * k / sh - math.pi / 2.0
        for i in range(sa):
            t = _ring_angle(i, sa)
            m.verts.append([r * math.cos(phi) * math.cos(t), r * math.sin(phi), r * math.cos(phi) * math.sin(t)])
    south, north = len(m.verts), len(m.verts) + 1
    m.verts.append([0.0, -r, 0.0])
    m.verts.append([0.0, r, 0.0])
    for k in range(1, sh):
        for i in range(sa + 1):
            m.uvs.append((i / sa, k / sh))
    _grid_quads(m, sh - 2, sa, 0)
    m.fuv = []
    for k in range(sh - 2):
        for i in range(sa):
            u = k * (sa + 1) + i
            m.fuv.append([u, u + 1, u + sa + 2, u + sa + 1])
    pole0 = len(m.uvs)
    for i in range(sa):
        m.uvs.append(((i + 0.5) / sa, 0.0))
    for i in range(sa):
        m.uvs.append(((i + 0.5) / sa, 1.0))
    top = (sh - 2) * sa
    for i in range(sa):
        m.faces.append([(i + 1) % sa, i, south])
        m.fuv.append([i + 1, i, pole0 + i])
    for i in range(sa):
        m.faces.append([top + i, top + (i + 1) % sa, north])
        m.fuv.append([(sh - 2) * (sa + 1) + i, (sh - 2) * (sa + 1) + i + 1, pole0 + sa + i])
    for k in range(sh - 1):
        for i in range(sa):
            m.edges.append((k * sa + i, k * sa + (i + 1) % sa))
    for k in range(sh - 2):
        for i in range(sa):
            m.edges.append((k * sa + i, (k + 1) * sa + i))
    for i in range(sa):
        m.edges.append((south, i))
    for i in range(sa):
        m.edges.append((top + i, north))
    m.hard = [False] * len(m.edges)
    return _apply_axis(m, a)


def prim_torus(a):
    r = a.get('r', 1.0)
    sr = a.get('sr', 0.5)
    tw = math.radians(a.get('tw', 0.0))
    sa, sh = int(a.get('sa', 20)), int(a.get('sh', 20))
    m = PMesh()
    for j in range(sh):
        th = 2.0 * math.pi * j / sh + tw
        rad = r - sr * math.cos(th)
        y = sr * math.sin(th)
        for i in range(sa):
            t = _ring_angle(i, sa)
            m.verts.append([rad * math.cos(t), y, rad * math.sin(t)])
    for j in range(sh + 1):
        for i in range(sa + 1):
            m.uvs.append((i / sa, j / sh))
    for j in range(sh):
        jn = (j + 1) % sh
        for i in range(sa):
            i2 = (i + 1) % sa
            m.faces.append([j * sa + i2, j * sa + i, jn * sa + i, jn * sa + i2])
            u = j * (sa + 1) + i
            m.fuv.append([u + 1, u, u + sa + 1, u + sa + 2])
    for j in range(sh):
        for i in range(sa):
            m.edges.append((j * sa + i, j * sa + (i + 1) % sa))
    for j in range(sh):
        for i in range(sa):
            m.edges.append((j * sa + i, ((j + 1) % sh) * sa + i))
    m.hard = [False] * len(m.edges)
    return _apply_axis(m, a)


PRIMITIVES = {
    'polyCube': prim_cube,
    'polyPlane': prim_plane,
    'polyCylinder': prim_cylinder,
    'polyCone': prim_cone,
    'polySphere': prim_sphere,
    'polyTorus': prim_torus,
}

# ---------------------------------------------------------------------------
# Operaciones
# ---------------------------------------------------------------------------

# no cambian la posicion de los vertices ni la topologia
PASS_THROUGH = {
    'groupParts', 'polyPlanarProj', 'polyCylProj', 'polySphProj', 'polyAutoProj',
    'polyMapCut', 'polyMapSew', 'polyMapSewMove', 'polyMapDel', 'polyTweakUV', 'polyMoveUV',
    'polyLayoutUV', 'polyFlipUV', 'polyNormalizeUV', 'polyEditUV', 'polyUVRectangle',
    'polyOptUvs', 'polyPinUV', 'polyContourProj', 'Unfold3DUnfold', 'Unfold3DOptimize',
    'unfold3dNode', 'polyCopyUV', 'polyUVSet', 'createColorSet', 'deleteColorSet',
    'polyColorPerVertex', 'polyColorMod', 'polyColorDel', 'polyBlindData', 'polyNormalPerVertex',
    'polySetToFaceNormal', 'polyCBoolOp_uv', 'polyUnsmooth_uv', 'uvChooser',
}

# deformadores: se toma la pose de reposo (bind pose)
DEFORMERS = {
    'skinCluster', 'tweak', 'blendShape', 'ffd', 'cluster', 'nonLinear', 'wire', 'wrap',
    'deltaMush', 'sculpt', 'jiggle', 'shrinkWrap', 'proximityWrap', 'tension', 'softMod',
    'textureDeformer', 'morph', 'solidify',
}

INPUT_ATTRS = ('ip', 'inputPolymesh', 'ig', 'inputGeometry', 'i', 'inMesh',
               'ip[0].ig', 'input[0].inputGeometry', 'in', 'inputMesh')


def _components(node, attr='ics'):
    return parse_component_list(node.attrs.get(attr) or [])


def _expand(indices, count):
    if '*' in indices:
        return list(range(count))
    return [i for i in indices if 0 <= i < count]


def op_poly_tweak(node, m, ctx):
    tk = ctx.array(node, 'tk', 3)
    for i, d in tk.items():
        if i < len(m.verts):
            v = m.verts[i]
            m.verts[i] = [v[0] + d[0], v[1] + d[1], v[2] + d[2]]
    return f32(m)


def op_transform_geometry(node, m, ctx):
    t = node.attrs.get('txf')
    if not t:
        return m
    M = [t[0:4], t[4:8], t[8:12], t[12:16]]
    m.verts = [[x * M[0][0] + y * M[1][0] + z * M[2][0] + M[3][0],
                x * M[0][1] + y * M[1][1] + z * M[2][1] + M[3][1],
                x * M[0][2] + y * M[1][2] + z * M[2][2] + M[3][2]] for x, y, z in m.verts]
    return m


def _face_normal(m, f):
    nx = ny = nz = 0.0
    for k in range(len(f)):
        a = m.verts[f[k]]
        b = m.verts[f[(k + 1) % len(f)]]
        nx += (a[1] - b[1]) * (a[2] + b[2])
        ny += (a[2] - b[2]) * (a[0] + b[0])
        nz += (a[0] - b[0]) * (a[1] + b[1])
    n = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
    return nx / n, ny / n, nz / n


def op_soft_edge(node, m, ctx):
    comps = _components(node)
    edges = _expand(comps.get('e', []), len(m.edges)) if comps else list(range(len(m.edges)))
    angle = ctx.deg(node.attrs['a']) if 'a' in node.attrs else 30.0
    efaces = {}
    for fi, f in enumerate(m.faces):
        for k in range(len(f)):
            key = (min(f[k], f[(k + 1) % len(f)]), max(f[k], f[(k + 1) % len(f)]))
            efaces.setdefault(key, []).append(fi)
    for e in edges:
        a, b = m.edges[e]
        fs = efaces.get((min(a, b), max(a, b)), [])
        if len(fs) != 2:
            m.hard[e] = True
            continue
        n1, n2 = _face_normal(m, m.faces[fs[0]]), _face_normal(m, m.faces[fs[1]])
        d = max(-1.0, min(1.0, sum(x * y for x, y in zip(n1, n2))))
        m.hard[e] = math.degrees(math.acos(d)) > angle
    return m


def op_delete_component(node, m, ctx):
    comps = parse_component_list(node.attrs.get('dc') or [])
    faces = set(_expand(comps.get('f', []), len(m.faces)))
    if comps.get('vtx') or comps.get('e'):
        if comps.get('e') and not faces:
            return _delete_edges(m, _expand(comps['e'], len(m.edges)))
        raise NotImplementedError('deleteComponent de vertices')
    m.compact([i not in faces for i in range(len(m.faces))])
    return m


def _delete_edges(m, edges):
    """Borrar aristas une las dos caras vecinas; las aristas de borde no se borran."""
    for e in sorted(set(edges), reverse=True):
        a, b = m.edges[e]
        fs = [fi for fi, f in enumerate(m.faces)
              if any({f[k], f[(k + 1) % len(f)]} == {a, b} for k in range(len(f)))]
        if len(fs) != 2:
            continue  # arista de borde: Maya no la borra
        m.exact = False
        f1, f2 = sorted(fs)
        A, B = m.faces[f1], m.faces[f2]
        # rotar A para que termine en la arista compartida y unir con B
        k = next(k for k in range(len(A)) if {A[k], A[(k + 1) % len(A)]} == {a, b})
        A = A[k + 1:] + A[:k + 1]
        start = A[-1]
        j = B.index(start)
        B = B[j:] + B[:j]
        merged = A[:-1] + B[:-1] if False else A + B[1:-1]
        m.faces[f1] = merged
        m.fuv[f1] = None
        del m.faces[f2]
        del m.fuv[f2]
    m.compact()
    return m


def op_merge_vert(node, m, ctx):
    comps = _components(node)
    sel = _expand(comps.get('vtx', []), len(m.verts)) if comps else list(range(len(m.verts)))
    dist = node.attrs.get('d', 0.01)
    always_two = node.attrs.get('am', 0)
    groups = []
    if always_two and len(sel) == 2:
        groups = [sorted(sel)]
    else:
        unused = sorted(sel)
        while unused:
            seed = unused.pop(0)
            grp = [seed]
            for j in list(unused):
                if any(_dist(m.verts[j], m.verts[g]) <= dist for g in grp):
                    grp.append(j)
                    unused.remove(j)
            if len(grp) > 1:
                groups.append(grp)
    if not groups:
        return m
    target = {}
    for grp in groups:
        c = [sum(m.verts[g][k] for g in grp) / len(grp) for k in range(3)]
        keep = min(grp)
        m.verts[keep] = c
        for g in grp:
            target[g] = keep
    return _weld(m, target)


def _dist(p, q):
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(p, q)))


def _weld(m, target):
    """Une vertices (target: indice -> indice que sobrevive) y limpia la topologia."""
    remap = lambda v: target.get(v, v)
    new_faces, new_fuv = [], []
    for f, uv in zip(m.faces, m.fuv):
        nf, nu = [], []
        for k, v in enumerate(f):
            v2 = remap(v)
            if nf and nf[-1] == v2:
                continue
            nf.append(v2)
            nu.append(uv[k] if uv else -1)
        while len(nf) > 1 and nf[0] == nf[-1]:
            nf.pop()
            nu.pop()
        new_faces.append(nf)
        new_fuv.append(nu if uv else None)
    keep = [len(set(f)) >= 3 for f in new_faces]
    m.faces = new_faces
    m.fuv = new_fuv
    # aristas: remapear y quitar duplicadas/degeneradas conservando la de menor indice
    seen = {}
    edges, hard = [], []
    for (a, b), h in zip(m.edges, m.hard):
        a, b = remap(a), remap(b)
        if a == b:
            continue
        key = (min(a, b), max(a, b))
        if key in seen:
            hard[seen[key]] = hard[seen[key]] and h
            continue
        seen[key] = len(edges)
        edges.append((a, b))
        hard.append(h)
    m.edges, m.hard = edges, hard
    m.compact(keep)
    return m


def op_poly_unite(node, m_unused, ctx):
    out = PMesh()
    k = 0
    while True:
        src = ctx.input_of(node, 'ip[%d]' % k)
        if src is None:
            if k > 4 and ctx.input_of(node, 'ip[%d]' % (k + 1)) is None:
                break
            k += 1
            if k > 512:
                break
            continue
        sub = ctx.evaluate_plug(src)
        mat = node.attrs.get('im[%d]' % k)
        if mat:
            sub = sub.copy()
            M = [mat[0:4], mat[4:8], mat[8:12], mat[12:16]]
            sub.verts = [[x * M[0][0] + y * M[1][0] + z * M[2][0] + M[3][0],
                          x * M[0][1] + y * M[1][1] + z * M[2][1] + M[3][1],
                          x * M[0][2] + y * M[1][2] + z * M[2][2] + M[3][2]] for x, y, z in sub.verts]
        off_v, off_uv = len(out.verts), len(out.uvs)
        out.verts += sub.verts
        out.faces += [[v + off_v for v in f] for f in sub.faces]
        out.edges += [(a + off_v, b + off_v) for a, b in sub.edges]
        out.hard += sub.hard
        out.uvs += sub.uvs
        out.fuv += [[u + off_uv if u >= 0 else -1 for u in f] if f else None for f in sub.fuv]
        out.exact = out.exact and sub.exact
        k += 1
    return out


def op_triangulate(node, m, ctx):
    comps = _components(node)
    faces = set(_expand(comps.get('f', []), len(m.faces))) if comps else set(range(len(m.faces)))
    new_faces, new_fuv = [], []
    extra, extra_uv = [], []
    for fi, (f, uv) in enumerate(zip(m.faces, m.fuv)):
        if uv is not None and len(uv) != len(f):
            uv = None
        if fi in faces and len(f) > 3:
            tris = [(0, k, k + 1) for k in range(1, len(f) - 1)]
            first = True
            for t in tris:
                tri = [f[t[0]], f[t[1]], f[t[2]]]
                tuv = [uv[t[0]], uv[t[1]], uv[t[2]]] if uv else None
                if first:
                    new_faces.append(tri)
                    new_fuv.append(tuv)
                    first = False
                else:
                    extra.append(tri)
                    extra_uv.append(tuv)
        else:
            new_faces.append(f)
            new_fuv.append(uv)
    m.faces = new_faces + extra
    m.fuv = new_fuv + extra_uv
    idx = m.edge_index()
    for f in extra:
        for k in range(3):
            m.add_edge(f[k], f[(k + 1) % 3], index=idx)
    for f in m.faces:
        for k in range(len(f)):
            m.add_edge(f[k], f[(k + 1) % len(f)], index=idx)
    m.exact = False
    return m


def op_poly_normal(node, m, ctx):
    mode = int(node.attrs.get('nm', 0))
    if mode not in (0, 3):
        raise NotImplementedError('polyNormal modo %d' % mode)
    comps = _components(node)
    faces = _expand(comps.get('f', []), len(m.faces)) if comps else range(len(m.faces))
    for fi in faces:
        f = m.faces[fi]
        m.faces[fi] = [f[0]] + f[1:][::-1]
        if m.fuv[fi]:
            u = m.fuv[fi]
            m.fuv[fi] = [u[0]] + u[1:][::-1]
    return m


def op_smooth_face(node, m, ctx):
    """polySmoothFace: se deja la malla base y se agrega un modificador Subdivision Surface."""
    comps = _components(node)
    if comps and comps.get('f') and '*' not in comps['f'] and len(set(comps['f'])) < len(m.faces):
        raise NotImplementedError('polySmoothFace sobre una seleccion parcial de caras')
    m.subdiv += max(1, int(node.attrs.get('dv', 1)))
    return m


OPERATIONS = {
    'polySmoothFace': op_smooth_face,
    'polyTweak': op_poly_tweak,
    'transformGeometry': op_transform_geometry,
    'polySoftEdge': op_soft_edge,
    'deleteComponent': op_delete_component,
    'polyMergeVert': op_merge_vert,
    'polyUnite': op_poly_unite,
    'polyTriangulate': op_triangulate,
    'polyNormal': op_poly_normal,
}

from .maya_modeling import OPERATIONS as _MODELING_OPS, INDEX_SENSITIVE as _MODELING_IDX, f32  # noqa: E402
OPERATIONS.update(_MODELING_OPS)

# operaciones que seleccionan componentes por indice: necesitan orden exacto
INDEX_SENSITIVE = set(_MODELING_IDX) | {'polyTweak', 'polySoftEdge', 'deleteComponent', 'polyMergeVert', 'polyNormal',
                   'polyTriangulate'}

# ---------------------------------------------------------------------------
# Recorrido del grafo
# ---------------------------------------------------------------------------


class _Context:
    def __init__(self, reader):
        self.reader = reader
        self.scene = reader.scene
        self.inputs = {}
        for src, dst in self.scene.connections:
            dnode, dattr = self.scene.node_of_plug(dst)
            snode, sattr = self.scene.node_of_plug(src)
            if dnode is not None and snode is not None:
                self.inputs.setdefault(id(dnode), {})[dattr] = (snode, sattr)
        self.cache = {}
        self.warnings = []

    def input_of(self, node, attr):
        return self.inputs.get(id(node), {}).get(attr)

    def geometry_input(self, node):
        ins = self.inputs.get(id(node), {})
        for a in INPUT_ATTRS:
            if a in ins:
                return ins[a]
        for a, src in ins.items():
            if a.startswith('ip[0]') or a.startswith('input[0]'):
                return src
        return None

    def deg(self, value):
        """Angulo en grados (el .mb guarda radianes)."""
        return math.degrees(value) if self.reader.angles_in_radians else value

    def array(self, node, base, width):
        """Lee un atributo multi (p.ej. tk[0:5]) en {indice: tupla}."""
        stored = node.attrs.get(base + '[]')
        if isinstance(stored, dict):
            return {i: (v if isinstance(v, tuple) else (v,)) for i, v in stored.items()}
        out = {}
        from .maya_scene import split_index_range
        for attr, vtype, values in self.reader.raw.get(id(node), []):
            b, lo, hi = split_index_range(attr)
            if b != base or lo is None:
                continue
            f = [float(v) for _, v in values]
            for k, idx in enumerate(range(lo, hi + 1)):
                if width * k + width - 1 < len(f):
                    out[idx] = tuple(f[width * k:width * k + width])
        return out

    def verify_selection(self, node, m):
        """Autocomprobacion: Maya guarda en muchas operaciones la caja (cbn/cbx)
        de las componentes seleccionadas; si la reconstruccion no coincide, los
        indices ya no son los de Maya y se aborta en vez de importar basura."""
        cbn, cbx = node.attrs.get('cbn'), node.attrs.get('cbx')
        if not cbn or not cbx or not m.verts:
            return
        comps = _components(node)
        vs = set()
        for f in _expand(comps.get('f', []), len(m.faces)):
            vs.update(m.faces[f])
        for e in _expand(comps.get('e', []), len(m.edges)):
            vs.update(m.edges[e])
        vs.update(_expand(comps.get('vtx', []), len(m.verts)))
        if not vs:
            return
        ix = node.attrs.get('ix') if node.attrs.get('ws') else None
        pts = []
        for v in vs:
            x, y, z = m.verts[v]
            if ix:
                pts.append((x * ix[0] + y * ix[4] + z * ix[8] + ix[12],
                            x * ix[1] + y * ix[5] + z * ix[9] + ix[13],
                            x * ix[2] + y * ix[6] + z * ix[10] + ix[14]))
            else:
                pts.append((x, y, z))
        got = [min(p[k] for p in pts) for k in range(3)] + [max(p[k] for p in pts) for k in range(3)]
        want = list(cbn) + list(cbx)
        size = max(1e-6, max(want[k + 3] - want[k] for k in range(3)))
        err = max(abs(a - b) for a, b in zip(got, want))
        if err > 1e-3 + 2e-3 * size:
            raise NotImplementedError('autocomprobacion fallida en %s (desvio %.3g)' % (node.name, err))

    def evaluate_plug(self, src):
        node, _ = src
        return self.evaluate(node)

    def evaluate(self, node):
        key = id(node)
        if key in self.cache:
            return self.cache[key].copy()
        t = node.type
        if node.type == 'mesh':
            if node.mesh is not None:
                result = PMesh.from_meshdata(node.mesh)
            else:
                up = self.geometry_input(node)
                if up is None:
                    raise NotImplementedError('mesh %s sin geometria ni historial' % node.name)
                result = self.evaluate(up[0])
                pts = node.attrs.get('pt[]') or {}
                for i, d in pts.items():
                    if i < len(result.verts):
                        v = result.verts[i]
                        result.verts[i] = [v[0] + d[0], v[1] + d[1], v[2] + d[2]]
        elif t in PRIMITIVES:
            attrs = dict(node.attrs)
            if 'tw' in attrs:
                attrs['tw'] = self.deg(attrs['tw'])
            result = f32(PRIMITIVES[t](attrs))
        else:
            up = self.geometry_input(node)
            if t == 'polyUnite':
                result = OPERATIONS[t](node, None, self)
            elif up is None:
                raise NotImplementedError('%s sin entrada' % t)
            else:
                m = self.evaluate(up[0])
                if t in PASS_THROUGH:
                    result = m
                elif t in DEFORMERS:
                    if t not in ('skinCluster', 'tweak', 'groupParts'):
                        self.warnings.append('%s: deformador "%s" ignorado (pose de reposo)' % (node.name, t))
                    result = m
                elif t in OPERATIONS:
                    if m.subdiv:
                        raise NotImplementedError('%s despues de polySmoothFace' % t)
                    if t in INDEX_SENSITIVE and not m.exact:
                        raise NotImplementedError('%s despues de una operacion aproximada' % t)
                    self.verify_selection(node, m)
                    result = OPERATIONS[t](node, m, self)
                else:
                    raise NotImplementedError('operacion %s no soportada' % t)
        self.cache[key] = result
        return result.copy()


def evaluate_shape_history(reader, shape):
    """Devuelve MeshData con la geometria final del shape, o lanza NotImplementedError."""
    ctx = _Context(reader)
    up = ctx.geometry_input(shape)
    if up is None:
        raise NotImplementedError('sin entrada')
    m = ctx.evaluate(up[0])
    pts = shape.attrs.get('pt[]') or {}
    for i, d in pts.items():
        if i < len(m.verts):
            v = m.verts[i]
            m.verts[i] = [v[0] + d[0], v[1] + d[1], v[2] + d[2]]
    reader.scene.warnings.extend(ctx.warnings)
    return m.to_meshdata()
