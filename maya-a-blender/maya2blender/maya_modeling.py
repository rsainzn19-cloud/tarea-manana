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
        # orden de Maya: lateral de x, lateral de y, borde nuevo
        m.add_edge(x, newv[x], index=idx)
        m.add_edge(y, newv[y], index=idx)
        m.add_edge(newv[x], newv[y], index=idx)
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


SPLIT_RING_FLIP_T = True
SPLIT_RING_FLIP_CHAIN = False


def _ring_chain(m, sel, root):
    """Ordena las aristas de un anillo (aristas opuestas de quads consecutivos)."""
    key_of = {tuple(sorted(m.edges[e])): e for e in sel}
    adj = {e: [] for e in sel}
    for fi, f in enumerate(m.faces):
        if len(f) != 4:
            continue
        es = [tuple(sorted((f[k], f[(k + 1) % 4]))) for k in range(4)]
        for k in range(2):
            if es[k] in key_of and es[k + 2] in key_of:
                a, b = key_of[es[k]], key_of[es[k + 2]]
                adj[a].append((b, fi))
                adj[b].append((a, fi))
    if root not in adj:
        root = sel[0]
    # recorrer desde la raiz por la cara de mayor indice hasta un extremo
    def walk(start, first):
        out, prev, cur, face = [start], None, start, first
        while True:
            nxt = [x for x in adj[cur] if x[1] != face and x[0] != prev] if face is not None else []
            if face is not None:
                step = [x for x in adj[cur] if x[1] == face]
                if not step:
                    break
                prev, (cur, _) = cur, step[0]
                if cur == start:
                    return out, True
                out.append(cur)
                rest = [x for x in adj[cur] if x[1] != face]
                if not rest:
                    break
                face = rest[0][1]
            else:
                break
        return out, False
    faces = sorted(adj[root], key=lambda x: -x[1] if not SPLIT_RING_FLIP_CHAIN else x[1])
    if not faces:
        return [root], {}, False
    fwd, closed = walk(root, faces[0][1])
    if closed:
        order = fwd
    else:
        back, _ = walk(root, faces[1][1]) if len(faces) > 1 else ([root], False)
        order = list(reversed(fwd[1:])) + [root] + back[1:]
        order = list(reversed(order))
    return order, adj, closed


def op_split_ring(node, m, ctx):
    """polySplitRing (insertar loops): cada loop parte las aristas del anillo y
    agrega, por arista del anillo, [segmento nuevo, arista del loop]."""
    from .maya_history import _components, _expand
    sel = sorted(set(_expand(_components(node).get('e', []), len(m.edges))))
    if not sel:
        return m
    root = int(node.attrs.get('re', sel[0]))
    order, adj, closed = _ring_chain(m, sel, root)
    if len(order) != len(sel):
        raise NotImplementedError('polySplitRing con un anillo no continuo')
    stp = int(node.attrs.get('stp', 1))
    div = max(1, int(node.attrs.get('div', 1)))
    wt = float(node.attrs.get('wt', 0.5))
    # orientar cada arista: lado "a" conectado al lado "a" de la anterior
    orient = {}
    ra, rb = m.edges[order[0]]
    if order[0] != root and root in order:
        pass
    orient[order[0]] = (ra, rb)
    for i in range(1, len(order)):
        pa, pb = orient[order[i - 1]]
        a, b = m.edges[order[i]]
        face = next(fi for e2, fi in adj[order[i]] if e2 == order[i - 1])
        f = m.faces[face]
        # a de la arista actual comparte arista de la cara con pa
        fe = {tuple(sorted((f[k], f[(k + 1) % 4]))) for k in range(4)}
        orient[order[i]] = (a, b) if tuple(sorted((a, pa))) in fe else (b, a)
    # la raiz define el sentido
    ra0, rb0 = m.edges[root]
    if orient[root] != (ra0, rb0):
        orient = {e: (b, a) for e, (a, b) in orient.items()}
    if SPLIT_RING_FLIP_T:
        orient = {e: (b, a) for e, (a, b) in orient.items()}
    if stp == 2:
        ts = [k / (div + 1.0) for k in range(1, div + 1)]
    elif stp == 1:
        ts = [wt]
    else:
        rl = math.dist(m.verts[ra0], m.verts[rb0]) or 1.0
        ts = [('abs', wt * rl)]
    idx = m.edge_index()
    origin = {e: (orient[e][0], orient[e][1]) for e in order}
    far = {e: orient[e][1] for e in order}      # extremo lejano del tramo que queda por partir
    near = {e: orient[e][0] for e in order}     # extremo cercano del tramo que queda por partir
    seg_edge = {e: e for e in order}            # indice de la arista del tramo que queda por partir
    strip = [next(fi for e2, fi in adj[order[i]] if e2 == order[i + 1]) for i in range(len(order) - 1)]
    if closed:
        strip.append(next(fi for e2, fi in adj[order[-1]] if e2 == order[0]))
    for t in ts:
        newv = {}
        for e in order:
            a0, b0 = origin[e]
            pa, pb = m.verts[a0], m.verts[b0]
            if isinstance(t, tuple):
                L = math.dist(pa, pb) or 1.0
                tt = min(1.0, t[1] / L)
            else:
                tt = t
            newv[e] = len(m.verts)
            m.verts.append([pa[k] + (pb[k] - pa[k]) * tt for k in range(3)])
        new_faces = []
        prev_near = dict(near)
        for i, e in enumerate(order):
            n = near[e]
            fv = far[e]
            v = newv[e]
            # el tramo (n, fv) pasa a (n, v); se agrega (v, fv)
            si = seg_edge[e]
            m.edges[si] = (n, v)
            idx.pop(tuple(sorted((n, fv))), None)
            idx[tuple(sorted((n, v)))] = si
            m.edges.append((v, fv))
            m.hard.append(m.hard[si])
            idx[tuple(sorted((v, fv)))] = len(m.edges) - 1
            seg_edge[e] = len(m.edges) - 1
            near[e] = v
            # actualizar caras que usan la arista (n, fv)
            for fi, f in enumerate(m.faces):
                for k in range(len(f)):
                    if {f[k], f[(k + 1) % len(f)]} == {n, fv}:
                        f.insert(k + 1, v)
                        if m.fuv[fi]:
                            m.fuv[fi] = None
                        break
            if i > 0 or closed:
                j = i - 1 if i > 0 else len(order) - 1
                pe = order[j]
                u, w = newv[pe], v
                cand = [fi for fi, f in enumerate(m.faces) if u in f and w in f and fi not in new_faces]
                if not cand:
                    raise NotImplementedError('polySplitRing: no se encontro la cara a partir')
                fi = cand[0]
                f = m.faces[fi]
                iu, iw = f.index(u), f.index(w)
                p1 = f[iu:iw + 1] if iu < iw else f[iu:] + f[:iw + 1]
                p2 = f[iw:iu + 1] if iw < iu else f[iw:] + f[:iu + 1]
                # la parte que contiene el primer vertice de la cara conserva el indice
                keep, other = (p1, p2) if f[0] in p1[1:-1] or f[0] == p1[0] and f[0] not in p2[1:-1] and f[0] != p2[0] else (p2, p1)
                if f[0] in (u, w):
                    # el primer vertice es uno de los nuevos: se queda la parte que sigue a f[1]
                    keep, other = (p1, p2) if f[1] in p1 else (p2, p1)
                k0 = keep.index(f[0]) if f[0] in keep else 0
                m.faces[fi] = keep[k0:] + keep[:k0]
                m.faces.append(other)
                m.fuv.append(None)
                m.edges.append((u, w))
                m.hard.append(False)
                idx[tuple(sorted((u, w)))] = len(m.edges) - 1
                new_faces.append(len(m.faces) - 1)
        if closed:
            pass
    m.exact = m.exact
    return f32(m)




def _dihedral(m, f1, f2):
    from .maya_history import _face_normal
    n1, n2 = _face_normal(m, m.faces[f1]), _face_normal(m, m.faces[f2])
    return math.degrees(math.acos(max(-1.0, min(1.0, sum(x * y for x, y in zip(n1, n2))))))


def op_bevel(node, m, ctx):
    """polyBevel3 (chaflan, 1 segmento) sobre cadenas de aristas.

    Cada vertice de la cadena se parte en uno por lado, deslizado sobre la
    arista no biselada de ese lado; cada arista biselada se convierte en una
    cara delgada.
    """
    from .maya_history import _components, _expand
    if int(node.attrs.get('sg', 1)) != 1:
        raise NotImplementedError('polyBevel3 con mas de un segmento')
    if not node.attrs.get('co', 1):
        raise NotImplementedError('polyBevel3 sin chaflan')
    sel = sorted(set(_expand(_components(node).get('e', []), len(m.edges))))
    if not sel:
        return m
    selkeys = {tuple(sorted(m.edges[e])) for e in sel}
    ef = _edge_faces(m)
    vb = {}
    for e in sel:
        a, b = m.edges[e]
        if len(ef.get(tuple(sorted((a, b))), [])) != 2:
            raise NotImplementedError('polyBevel3 sobre aristas de borde')
        vb.setdefault(a, []).append(e)
        vb.setdefault(b, []).append(e)
    if any(len(x) > 2 for x in vb.values()):
        raise NotImplementedError('polyBevel3 con esquinas (3 o mas aristas por vertice)')
    frac = float(node.attrs.get('f', 0.5))
    oaf = node.attrs.get('oaf', 1)
    offset = float(node.attrs.get('o', 0.0))

    vfaces = {}
    for fi, f in enumerate(m.faces):
        for v in f:
            if v in vb:
                vfaces.setdefault(v, []).append(fi)

    def around(f, v):
        k = f.index(v)
        return f[(k - 1) % len(f)], f[(k + 1) % len(f)]

    # 1) para cada vertice: dos grupos de caras (uno por lado) y su posicion nueva
    plan = {}
    for v in sorted(vb):
        left = set(vfaces.get(v, []))
        groups = []
        while left:
            seed = min(left)
            grp, stack = {seed}, [seed]
            left.discard(seed)
            while stack:
                fi = stack.pop()
                for w in around(m.faces[fi], v):
                    key = tuple(sorted((v, w)))
                    if key in selkeys:
                        continue
                    for fj, _, _ in ef.get(key, []):
                        if fj in left:
                            left.discard(fj)
                            grp.add(fj)
                            stack.append(fj)
            groups.append(sorted(grp))
        if len(groups) != 2:
            raise NotImplementedError('polyBevel3: cadena que termina dentro de la malla')
        rails_of = []
        for grp in groups:
            rails = sorted({w for fi in grp for w in around(m.faces[fi], v)
                            if tuple(sorted((v, w))) not in selkeys})
            if len(rails) != 1:
                raise NotImplementedError('polyBevel3: vertice con %d aristas laterales' % len(rails))
            rails_of.append(rails[0])
        plan[v] = (groups, rails_of)
    # ancho uniforme: con offsetAsFraction, 1.0 es el maximo sin solaparse:
    # la mitad de una arista lateral con bisel en ambos extremos, o la arista
    # entera si el otro extremo no se bisela
    if oaf:
        limits = []
        for v, (_, rails) in plan.items():
            for w in rails:
                L = math.dist(m.verts[v], m.verts[w])
                limits.append(L / 2.0 if w in plan else L)
        width = frac * min(limits)
    else:
        width = offset
    for v in list(plan):
        groups, rails_of = plan[v]
        targets = []
        p = m.verts[v]
        for w in rails_of:
            q = m.verts[w]
            L = math.dist(p, q) or 1.0
            t = min(1.0, width / L)
            targets.append([p[k] + (q[k] - p[k]) * t for k in range(3)])
        plan[v] = (groups, targets)

    # 2) renumeracion al estilo de Maya:
    #    vertices: los no partidos en su orden; luego los nuevos en orden de
    #    aparicion recorriendo las caras de bisel (en el orden de las aristas)
    #    caras: las no afectadas; las de bisel; las afectadas (en su orden)
    #    aristas: las no afectadas; luego las de cada cara de bisel y de cada
    #    cara afectada, en orden de aparicion
    side_of = {}   # (cara, v) -> indice de grupo
    for v, (groups, _) in plan.items():
        for gi, grp in enumerate(groups):
            for fi in grp:
                side_of[(fi, v)] = gi
    bevel_faces = []   # caras como lista de (v, grupo)
    for e in sel:
        a, b = m.edges[e]
        (f1, x1, y1), (f2, x2, y2) = ef[tuple(sorted((a, b)))]
        if x1 != a:
            f1, f2 = f2, f1
        # f1 recorre a->b; la cara de bisel: [a1, a2, b2, b1]
        bevel_faces.append([(a, side_of[(f1, a)]), (a, side_of[(f2, a)]),
                            (b, side_of[(f2, b)]), (b, side_of[(f1, b)])])
    kept_v = [v for v in range(len(m.verts)) if v not in plan]
    vmap = {v: k for k, v in enumerate(kept_v)}
    verts = [m.verts[v] for v in kept_v]
    newv = {}

    def vid(item):
        if item not in newv:
            v, gi = item
            newv[item] = len(verts)
            verts.append(plan[v][1][gi])
        return newv[item]

    bevel_v = [[vid(it) for it in f] for f in bevel_faces]
    affected = [fi for fi, f in enumerate(m.faces) if any(v in plan for v in f)]
    affset = set(affected)
    unaffected = [fi for fi in range(len(m.faces)) if fi not in affset]

    def remap_face(fi):
        return [vid((v, side_of[(fi, v)])) if v in plan else vmap[v] for v in m.faces[fi]]

    affected_v = [remap_face(fi) for fi in affected]
    faces = [[vmap[v] for v in m.faces[fi]] for fi in unaffected] + bevel_v + affected_v
    fuv = [m.fuv[fi] for fi in unaffected] + [None] * len(bevel_v) + [m.fuv[fi] for fi in affected]
    hard_of = {tuple(sorted(e)): h for e, h in zip(m.edges, m.hard)}
    edges, hard, index = [], [], {}

    def add(a, b, h):
        key = tuple(sorted((a, b)))
        if key not in index:
            index[key] = len(edges)
            edges.append((a, b))
            hard.append(h)

    for (a, b), h in zip(m.edges, m.hard):
        if a not in plan and b not in plan:
            add(vmap[a], vmap[b], h)
    inv = {k: v for v, k in vmap.items()}
    inv.update({k: item[0] for item, k in newv.items()})
    for f in bevel_v + affected_v:
        for k in range(len(f)):
            a, b = f[k], f[(k + 1) % len(f)]
            add(a, b, hard_of.get(tuple(sorted((inv[a], inv[b]))), False))
    m.verts, m.faces, m.fuv, m.edges, m.hard = verts, faces, fuv, edges, hard
    first_new = len(unaffected)
    last_new = first_new + len(bevel_v)
    # dureza de las aristas de las caras de bisel segun el angulo de suavizado
    sa = float(node.attrs.get('sa', 30.0))
    if getattr(getattr(ctx, 'reader', None), 'angles_in_radians', False):
        sa = math.degrees(sa)
    ef2 = _edge_faces(m)
    for i, (a, b) in enumerate(m.edges):
        fs = ef2.get(tuple(sorted((a, b))), [])
        if len(fs) == 2 and any(first_new <= x[0] < last_new for x in fs):
            m.hard[i] = _dihedral(m, fs[0][0], fs[1][0]) > sa
    return f32(m)


OPERATIONS = {
    'polyExtrudeEdge': op_extrude_edge,
    'polyExtrudeFace': op_extrude_face,
    'polySplitRing': op_split_ring,
    'polyBevel3': op_bevel,
}
INDEX_SENSITIVE = {'polyExtrudeEdge', 'polyExtrudeFace', 'polySplitRing', 'polyBevel3'}
