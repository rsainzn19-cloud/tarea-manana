"""Operaciones de modelado de Maya (extrude, split, bevel...) con su orden de indices.

Las convenciones de orden se dedujeron de archivos reales: cada operacion de
Maya guarda la caja de las componentes seleccionadas (cbn/cbx) y los polyTweak
guardan desplazamientos por indice de vertice, asi que se puede comprobar paso
a paso que los indices generados coinciden con los de Maya.
"""

import array
import math


def f32(m):
    """Maya guarda los puntos en float de 32 bits: redondear igual que Maya."""
    flat = array.array('f', [c for v in m.verts for c in v]).tolist()
    m.verts = [flat[i:i + 3] for i in range(0, len(flat), 3)]
    return m


def _vec(a, b):
    return [b[0] - a[0], b[1] - a[1], b[2] - a[2]]


def _invert_rowmajor(ix):
    """Inversa de una matriz afin 4x4 (convencion de Maya, vectores fila)."""
    M = [ix[0:3], ix[4:7], ix[8:11]]
    t = ix[12:15]
    det = (M[0][0] * (M[1][1] * M[2][2] - M[1][2] * M[2][1])
           - M[0][1] * (M[1][0] * M[2][2] - M[1][2] * M[2][0])
           + M[0][2] * (M[1][0] * M[2][1] - M[1][1] * M[2][0]))
    if abs(det) < 1e-12:
        return None
    inv = [[(M[(j + 1) % 3][(i + 1) % 3] * M[(j + 2) % 3][(i + 2) % 3]
             - M[(j + 1) % 3][(i + 2) % 3] * M[(j + 2) % 3][(i + 1) % 3]) / det for j in range(3)]
           for i in range(3)]
    it = [-(t[0] * inv[0][k] + t[1] * inv[1][k] + t[2] * inv[2][k]) for k in range(3)]
    return inv, it


def _world_vector_to_local(node, vec):
    ix = node.attrs.get('ix')
    if not ix or not node.attrs.get('ws'):
        return vec
    r = _invert_rowmajor(ix)
    if r is None:
        return vec
    inv, _ = r
    return [vec[0] * inv[0][k] + vec[1] * inv[1][k] + vec[2] * inv[2][k] for k in range(3)]


def _unsupported_transform(node, names):
    for n in names:
        v = node.attrs.get(n)
        if v is None:
            continue
        if isinstance(v, tuple):
            if any(abs(x) > 1e-9 for x in v):
                return n
        elif abs(v) > 1e-9:
            return n
    return None


def _edge_faces(m):
    ef = {}
    for fi, f in enumerate(m.faces):
        for k in range(len(f)):
            a, b = f[k], f[(k + 1) % len(f)]
            ef.setdefault((min(a, b), max(a, b)), []).append((fi, a, b))
    return ef


def op_extrude_edge(node, m, ctx):
    from .maya_history import _components, _expand
    if int(node.attrs.get('d', 1)) != 1:
        raise NotImplementedError('polyExtrudeEdge con divisiones')
    if not node.attrs.get('kft', 1):
        raise NotImplementedError('polyExtrudeEdge sin keepFacesTogether')
    bad = _unsupported_transform(node, ('r', 'lr', 'ls', 'off', 'lt', 'ltx', 'lty'))
    if bad:
        raise NotImplementedError('polyExtrudeEdge con %s' % bad)
    sel = sorted(set(_expand(_components(node).get('e', []), len(m.edges))))
    ef = _edge_faces(m)
    sel = [e for e in sel if len(ef.get(tuple(sorted(m.edges[e])), [])) == 1]
    t = node.attrs.get('t') or (0.0, 0.0, 0.0)
    t = _world_vector_to_local(node, list(t))
    ltz = node.attrs.get('ltz', 0.0) or 0.0
    newv = {}
    for e in sel:
        for v in m.edges[e]:
            if v not in newv:
                newv[v] = len(m.verts)
                p = m.verts[v]
                m.verts.append([p[0] + t[0], p[1] + t[1], p[2] + t[2]])
    if ltz:
        raise NotImplementedError('polyExtrudeEdge con localTranslateZ')
    idx = m.edge_index()
    for e in sel:
        a, b = m.edges[e]
        fi, x, y = ef[(min(a, b), max(a, b))][0]
        # la cara vecina recorre x->y; la nueva recorre y->x para conservar la orientacion
        face = [y, x, newv[x], newv[y]]
        m.faces.append(face)
        m.fuv.append(None)
        m.add_edge(x, newv[x], index=idx)
        m.add_edge(newv[x], newv[y], index=idx)
        m.add_edge(y, newv[y], index=idx)
    return f32(m)


def op_extrude_face(node, m, ctx):
    """polyExtrudeFace con keepFacesTogether.

    Orden observado en Maya:
      vertices: primero los que siguen usando caras no seleccionadas (en su
      orden original), luego los de las caras extruidas en orden de primera
      aparicion recorriendo esas caras;
      caras: conservan su indice; las laterales se agregan al final, por cara
      seleccionada y por arista de esa cara.
    """
    from .maya_history import _components, _expand
    if int(node.attrs.get('d', 1)) != 1:
        raise NotImplementedError('polyExtrudeFace con divisiones')
    if not node.attrs.get('kft', 1):
        raise NotImplementedError('polyExtrudeFace sin keepFacesTogether')
    bad = _unsupported_transform(node, ('r', 'lr', 'ls', 'off', 'lt', 'ltx', 'lty', 'ltz', 't'))
    if bad:
        raise NotImplementedError('polyExtrudeFace con %s' % bad)
    sel = sorted(set(_expand(_components(node).get('f', []), len(m.faces))))
    selset = set(sel)
    if not sel:
        return m
    ef = _edge_faces(m)
    # se quedan en la base los vertices usados por caras no seleccionadas o
    # que tocan una arista de borde de la seleccion (alli se crea una pared)
    keep = {v for fi, f in enumerate(m.faces) if fi not in selset for v in f}
    for fi in sel:
        f = m.faces[fi]
        for k in range(len(f)):
            a, b = f[k], f[(k + 1) % len(f)]
            if not any(x[0] in selset for x in ef[(min(a, b), max(a, b))] if x[0] != fi):
                keep.add(a)
                keep.add(b)
    base_used = sorted(keep)
    base_map = {v: k for k, v in enumerate(base_used)}
    cap_map = {}
    for fi in sel:
        for v in m.faces[fi]:
            if v not in cap_map:
                cap_map[v] = len(base_used) + len(cap_map)
    old_verts = m.verts
    verts = [list(old_verts[v]) for v in base_used] + [None] * len(cap_map)
    for v, k in cap_map.items():
        verts[k] = list(old_verts[v])

    old_faces = m.faces
    faces = [[(cap_map if fi in selset else base_map)[v] for v in f] for fi, f in enumerate(old_faces)]
    fuv = list(m.fuv)
    side = []
    for fi in sel:
        f = old_faces[fi]
        for k in range(len(f)):
            a, b = f[k], f[(k + 1) % len(f)]
            others = [x for x in ef[(min(a, b), max(a, b))] if x[0] != fi]
            if any(o[0] in selset for o in others):
                continue  # arista interior de la region
            side.append([base_map[a], base_map[b], cap_map[b], cap_map[a]])
    faces += side
    fuv += [None] * len(side)

    # aristas: las viejas que siguen en la base conservan su orden; luego, por
    # cada cara seleccionada y cada arista de esa cara: si es borde de la
    # region -> verticales nuevas (a, b) y la copia en la tapa; si es interior
    # -> solo la copia en la tapa
    old_edges, old_hard = m.edges, m.hard
    hard_of = {(min(a, b), max(a, b)): h for (a, b), h in zip(old_edges, old_hard)}
    edges, hard, index = [], [], {}

    def add(a, b, h):
        key = (min(a, b), max(a, b))
        if key not in index:
            index[key] = len(edges)
            edges.append((a, b))
            hard.append(h)

    def region_faces(a, b):
        return [x[0] for x in ef.get((min(a, b), max(a, b)), []) if x[0] in selset]

    def is_boundary(a, b):
        fs = ef.get((min(a, b), max(a, b)), [])
        return any(x[0] not in selset for x in fs) or len(region_faces(a, b)) == 1

    for (a, b), h in zip(old_edges, old_hard):
        if not region_faces(a, b) or is_boundary(a, b):
            add(base_map[a], base_map[b], h)
    for fi in sel:
        f = old_faces[fi]
        for k in range(len(f)):
            a, b = f[k], f[(k + 1) % len(f)]
            h = hard_of.get((min(a, b), max(a, b)), False)
            if is_boundary(a, b):
                add(base_map[a], cap_map[a], False)
                add(base_map[b], cap_map[b], False)
            add(cap_map[a], cap_map[b], h)
    m.verts, m.faces, m.fuv, m.edges, m.hard = verts, faces, fuv, edges, hard
    return f32(m)


OPERATIONS = {
    'polyExtrudeEdge': op_extrude_edge,
    'polyExtrudeFace': op_extrude_face,
}
INDEX_SENSITIVE = {'polyExtrudeEdge', 'polyExtrudeFace'}
