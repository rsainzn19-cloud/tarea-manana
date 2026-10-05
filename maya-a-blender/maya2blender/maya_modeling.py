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
    bad = _unsupported_transform(node, ('r', 'lr', 'ls', 'off', 'ltx', 'lty', 's'))
    if bad:
        raise NotImplementedError('polyExtrudeFace con %s' % bad)
    lt = list(node.attrs.get('lt') or (0.0, 0.0, 0.0))
    if 'ltz' in node.attrs:
        lt[2] = float(node.attrs['ltz'])
    if abs(lt[0]) > 1e-6 or abs(lt[1]) > 1e-6:
        raise NotImplementedError('polyExtrudeFace con traslacion local en X/Y')
    t_local = _world_vector_to_local(node, list(node.attrs.get('t') or (0.0, 0.0, 0.0)))
    sel = sorted(set(_expand(_components(node).get('f', []), len(m.faces))))
    selset = set(sel)
    if not sel:
        return m
    ef = _edge_faces(m)
    # se quedan en la base los vertices usados por caras no seleccionadas o
    # que tocan una arista de borde de la seleccion (alli se crea una pared)
    # (equivale a: todos menos los vertices estrictamente interiores a la region)
    interior = {v for fi in sel for v in m.faces[fi]}
    for fi, f in enumerate(m.faces):
        if fi not in selset:
            interior.difference_update(f)
    for fi in sel:
        f = m.faces[fi]
        for k in range(len(f)):
            a, b = f[k], f[(k + 1) % len(f)]
            if not any(x[0] in selset for x in ef[(min(a, b), max(a, b))] if x[0] != fi):
                interior.discard(a)
                interior.discard(b)
    for a, b in m.edges:
        if not ef.get((min(a, b), max(a, b))):
            interior.discard(a)
            interior.discard(b)
    base_used = [v for v in range(len(m.verts)) if v not in interior]
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

    # desplazamiento de la tapa: lt.z a lo largo de la normal de cada region
    # (caras conectadas por aristas) y t en espacio mundo
    if abs(lt[2]) > 1e-12 or any(abs(x) > 1e-12 for x in t_local):
        from .maya_history import _face_normal
        region_of = {}
        for fi in sel:
            if fi in region_of:
                continue
            stack, region_of[fi] = [fi], fi
            while stack:
                cur = stack.pop()
                f = m.faces[cur]
                for k in range(len(f)):
                    for x in ef[(min(f[k], f[(k + 1) % len(f)]), max(f[k], f[(k + 1) % len(f)]))]:
                        if x[0] in selset and x[0] not in region_of:
                            region_of[x[0]] = fi
                            stack.append(x[0])
        normals = {}
        for fi in sel:
            nx = _face_normal(m, m.faces[fi])
            acc = normals.setdefault(region_of[fi], [0.0, 0.0, 0.0])
            for k in range(3):
                acc[k] += nx[k]
        moved = set()
        for fi in sel:
            acc = normals[region_of[fi]]
            ln = math.sqrt(sum(x * x for x in acc)) or 1.0
            for v in m.faces[fi]:
                k = cap_map[v]
                if k in moved:
                    continue
                moved.add(k)
                verts[k] = [verts[k][j] + acc[j] / ln * lt[2] + t_local[j] for j in range(3)]
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
    def walk(start, face):
        """Avanza de arista en arista cruzando caras hasta un extremo (o cerrar el anillo)."""
        out, cur = [start], start
        while True:
            step = [x for x in adj[cur] if x[1] == face]
            if not step:
                break
            cur = step[0][0]
            if cur == start:
                return out, True
            out.append(cur)
            rest = [x for x in adj[cur] if x[1] != face]
            if not rest:
                break
            face = rest[0][1]
        return out, False

    faces = sorted(adj[root], key=lambda x: -x[1])
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


def _split_strip_face(m, idx, u, w, new_faces):
    """Parte la cara que contiene u y w con la arista (u, w); la parte que
    contiene el primer vertice de la cara conserva su indice."""
    cand = [fi for fi, f in enumerate(m.faces) if u in f and w in f and fi not in new_faces]
    if not cand:
        raise NotImplementedError('polySplitRing: no se encontro la cara a partir')
    fi = cand[0]
    f = m.faces[fi]
    iu, iw = f.index(u), f.index(w)
    p1 = f[iu:iw + 1] if iu < iw else f[iu:] + f[:iw + 1]
    p2 = f[iw:iu + 1] if iw < iu else f[iw:] + f[:iu + 1]
    first = f[1] if f[0] in (u, w) else f[0]
    keep, other = (p1, p2) if first in p1 else (p2, p1)
    k0 = keep.index(f[0]) if f[0] in keep else 0
    m.faces[fi] = keep[k0:] + keep[:k0]
    m.faces.append(other)
    m.fuv.append(None)
    m.edges.append((u, w))
    m.hard.append(False)
    idx[tuple(sorted((u, w)))] = len(m.edges) - 1
    new_faces.append(len(m.faces) - 1)


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
    # los loops se miden desde el extremo final de la arista raiz (verificado
    # con FishShip_V2.ma: polyTweak6 y la caja de polyExtrudeFace3)
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
            if i > 0:
                _split_strip_face(m, idx, newv[order[i - 1]], v, new_faces)
        if closed and len(order) > 2:
            _split_strip_face(m, idx, newv[order[-1]], newv[order[0]], new_faces)
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
        if len(groups) < 2 or len(groups) != max(2, len(vb[v])):
            raise NotImplementedError('polyBevel3: cadena que termina dentro de la malla')
        rails_of = []
        for grp in groups:
            rails = sorted({w for fi in grp for w in around(m.faces[fi], v)
                            if tuple(sorted((v, w))) not in selkeys})
            if not rails:
                raise NotImplementedError('polyBevel3: sector sin aristas laterales')
            if len(rails) > 1 and len(vb[v]) < 3:
                raise NotImplementedError('polyBevel3: vertice con %d aristas laterales' % len(rails))
            rails_of.append(rails)
        plan[v] = (groups, rails_of)
    # ancho uniforme: con offsetAsFraction, 1.0 es el maximo sin solaparse:
    # la mitad de una arista lateral con bisel en ambos extremos, o la arista
    # entera si el otro extremo no se bisela
    if oaf:
        limits = []
        for v, (_, rails) in plan.items():
            for rs in rails:
                for w in rs:
                    L = math.dist(m.verts[v], m.verts[w])
                    limits.append(L / 2.0 if w in plan else L)
        width = frac * min(limits)
    else:
        width = offset
    corner_extra = {}   # (cara, v) -> grupo, para quads en sectores de esquina
    for v in list(plan):
        groups, rails_of = plan[v]
        targets = []
        p = m.verts[v]
        for gi, (grp, rs) in enumerate(zip(groups, rails_of)):
            if len(rs) == 1:
                q = m.verts[rs[0]]
                L = math.dist(p, q) or 1.0
                t = min(1.0, width / L)
                targets.append([p[k] + (q[k] - p[k]) * t for k in range(3)])
                continue
            # sector de esquina con varias aristas: punto sobre la bisectriz de
            # las dos aristas biseladas que lo limitan
            bounds = sorted({w for fi in grp for w in around(m.faces[fi], v)
                             if tuple(sorted((v, w))) in selkeys})
            dirs = []
            for w in bounds[:2]:
                d = [m.verts[w][k] - p[k] for k in range(3)]
                n = math.sqrt(sum(x * x for x in d)) or 1.0
                dirs.append([x / n for x in d])
            bis = [dirs[0][k] + dirs[-1][k] for k in range(3)]
            nb = math.sqrt(sum(x * x for x in bis)) or 1.0
            cosang = max(-1.0, min(1.0, sum(a * b for a, b in zip(dirs[0], dirs[-1]))))
            half = math.acos(cosang) / 2.0
            dist = width / max(math.sin(half), 1e-3)
            targets.append([p[k] + bis[k] / nb * dist for k in range(3)])
            if len(vb[v]) >= 3:
                for fi in grp:
                    if len(m.faces[fi]) > 3 and not any(tuple(sorted((v, w))) in selkeys
                                                        for w in around(m.faces[fi], v)):
                        corner_extra[(fi, v)] = gi
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

    def cyclic_groups(v):
        """Grupos (sectores) de v en orden alrededor del vertice."""
        groups = plan[v][0]
        gof = {fi: gi for gi, grp in enumerate(groups) for fi in grp}
        start = min(gof)
        order, fi, seen = [], start, set()
        while fi is not None and fi not in seen:
            seen.add(fi)
            if not order or order[-1] != gof[fi]:
                order.append(gof[fi])
            nxt = around(m.faces[fi], v)[1]
            cand = [x[0] for x in ef.get(tuple(sorted((v, nxt))), []) if x[0] != fi and x[0] in gof]
            fi = cand[0] if cand else None
        if len(order) > 1 and order[0] == order[-1]:
            order.pop()
        for gi in range(len(groups)):
            if gi not in order:
                order.append(gi)
        return order

    def vid(item):
        if item not in newv:
            v = item[0]
            if len(vb[v]) >= 3 and len(item) == 2:
                # esquina: se crean todos sus vertices de sector juntos
                cyc = cyclic_groups(v)
                k = cyc.index(item[1])
                for gi in cyc[k:] + cyc[:k]:
                    newv[(v, gi)] = len(verts)
                    verts.append(list(plan[v][1][gi]))
            else:
                newv[item] = len(verts)
                verts.append(list(plan[v][1][item[1]]))
        return newv[item]


    bevel_v = [[vid(it) for it in f] for f in bevel_faces]
    # orden de salida: primero las caras de aristas que no tocan esquinas,
    # luego, por esquina, las de sus aristas y su tapa
    corners = [v for v in sorted(plan) if len(vb[v]) >= 3]
    touches = [any(v in corners for v in m.edges[e]) for e in sel]
    ordered = [f for f, t in zip(bevel_v, touches) if not t]
    for c in corners:
        ordered += [f for f, e in zip(bevel_v, sel) if c in m.edges[e]]
        cyc = cyclic_groups(c)
        ordered.append([vid((c, gi)) for gi in reversed(cyc)])
    ordered += [f for f, e, t in zip(bevel_v, sel, touches) if t and not any(c in m.edges[e] for c in corners)]
    bevel_v = ordered
    affected = [fi for fi, f in enumerate(m.faces) if any(v in plan for v in f)]
    affset = set(affected)
    unaffected = [fi for fi in range(len(m.faces)) if fi not in affset]

    def remap_face(fi):
        out = []
        for v in m.faces[fi]:
            if v not in plan:
                out.append(vmap[v])
            elif (fi, v) in corner_extra:
                out.append(vid((v, corner_extra[(fi, v)], 'quad', fi)))
            else:
                out.append(vid((v, side_of[(fi, v)])))
        return out

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

    old_faces_of = _edge_faces(m)
    # en cada extremo de cadena sobre un borde, la primera arista de borde
    # conserva su lugar (verificado con polyExtrudeEdge6 de FishShip_V2.ma)
    end_keep = set()
    for v in plan:
        if len(vb[v]) == 1:
            brails = sorted(i for i, (x, y) in enumerate(m.edges) if v in (x, y)
                            and len(old_faces_of[tuple(sorted((x, y)))]) == 1)
            if len(brails) == 2:
                end_keep.add(tuple(sorted(m.edges[brails[0]])))
    for (a, b), h in zip(m.edges, m.hard):
        if a not in plan and b not in plan:
            add(vmap[a], vmap[b], h)
        elif tuple(sorted((a, b))) in end_keep \
                and tuple(sorted((a, b))) not in selkeys:
            fi = old_faces_of[tuple(sorted((a, b)))][0][0]
            na = vmap[a] if a not in plan else (newv.get((a, side_of[(fi, a)])) if (fi, a) not in corner_extra else None)
            nb = vmap[b] if b not in plan else (newv.get((b, side_of[(fi, b)])) if (fi, b) not in corner_extra else None)
            if na is not None and nb is not None:
                add(na, nb, h)
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


def _plane_local(node, axis_world, point_world):
    """Convierte un plano dado en espacio mundo (ix) a espacio local."""
    ix = node.attrs.get('ix')
    if not ix or not node.attrs.get('ws', 1):
        return list(point_world), list(axis_world)
    r = _invert_rowmajor(ix)
    if r is None:
        return list(point_world), list(axis_world)
    inv, it = r
    pl = [point_world[0] * inv[0][k] + point_world[1] * inv[1][k] + point_world[2] * inv[2][k] + it[k]
          for k in range(3)]
    # normal: se transforma con la transpuesta de la inversa (vectores fila -> M)
    M = [ix[0:3], ix[4:7], ix[8:11]]
    nl = [sum(M[k][j] * axis_world[j] for j in range(3)) for k in range(3)]
    n = math.sqrt(sum(x * x for x in nl)) or 1.0
    return pl, [x / n for x in nl]


def op_mirror(node, m, ctx):
    """polyMirror: copia reflejada de toda la malla y union de los bordes cercanos al plano."""
    from .maya_history import _components, _expand, _weld
    comps = _components(node)
    faces_sel = _expand(comps.get('f', ['*']), len(m.faces)) if comps else list(range(len(m.faces)))
    if len(set(faces_sel)) != len(m.faces):
        raise NotImplementedError('polyMirror sobre una seleccion parcial')
    axis = int(node.attrs.get('a', 0))
    pos = float(node.attrs.get('mps', 0.0))
    piv = list(node.attrs.get('p') or (0.0, 0.0, 0.0))
    axis_w = [0.0, 0.0, 0.0]
    axis_w[axis] = 1.0
    point_w = list(piv)
    point_w[axis] = pos
    P, n = _plane_local(node, axis_w, point_w)
    if any(((v[0] - P[0]) * n[0] + (v[1] - P[1]) * n[1] + (v[2] - P[2]) * n[2]) *
           ((m.verts[0][0] - P[0]) * n[0] + (m.verts[0][1] - P[1]) * n[1] + (m.verts[0][2] - P[2]) * n[2]) < -1e-6
           for v in m.verts) and node.attrs.get('cm', 0):
        raise NotImplementedError('polyMirror con corte de malla')
    nv = len(m.verts)
    dist = lambda v: (v[0] - P[0]) * n[0] + (v[1] - P[1]) * n[1] + (v[2] - P[2]) * n[2]
    m.verts += [[v[k] - 2.0 * dist(v) * n[k] for k in range(3)] for v in m.verts]
    m.faces += [[x + nv for x in reversed(f)] for f in list(m.faces)]
    m.fuv += [list(reversed(u)) if u else None for u in list(m.fuv)]
    m.edges += [(a + nv, b + nv) for a, b in list(m.edges)]
    m.hard += list(m.hard)
    mode = int(node.attrs.get('mm', 1))
    if mode == 1:
        ef = _edge_faces(m)
        border = {v for (a, b) in m.edges[:len(m.edges) // 2] if len(ef[tuple(sorted((a, b)))]) == 1
                  for v in (a, b)}
        if node.attrs.get('mtt', 0):
            thr = float(node.attrs.get('mt', 0.001))
        else:
            thr = 0.001
        target = {}
        for v in sorted(border):
            d = dist(m.verts[v])
            if abs(2.0 * d) <= thr:
                m.verts[v] = [m.verts[v][k] - d * n[k] for k in range(3)]
                target[v + nv] = v
        if target:
            m = _weld(m, target)
    m.exact = False
    return f32(m)


def op_split(node, m, ctx):
    """polySplit: recorre una lista de puntos sobre aristas (d < 0) y parte las caras."""
    descs = ctx.array(node, 'd', 1)
    weights = ctx.array(node, 'e', 1)
    if not descs:
        raise NotImplementedError('polySplit sin puntos')
    pts = []
    for i in sorted(descs):
        d = int(descs[i][0])
        if d >= 0:
            raise NotImplementedError('polySplit con puntos en vertices/caras')
        e = d & 0x7FFFFFFF
        w = float(weights.get(i, (0.5,))[0])
        pts.append((e, w))
    newv_of = {}
    path = []
    for e, w in pts:
        if e in newv_of:
            path.append(newv_of[e])
            continue
        a, b = m.edges[e]
        pa, pb = m.verts[a], m.verts[b]
        v = len(m.verts)
        m.verts.append([pa[k] + (pb[k] - pa[k]) * w for k in range(3)])
        newv_of[e] = v
        path.append(v)
        # partir la arista e en (a, v) y (v, b)
        m.edges[e] = (a, v)
        m.edges.append((v, b))
        m.hard.append(m.hard[e])
        for fi, f in enumerate(m.faces):
            for k in range(len(f)):
                if {f[k], f[(k + 1) % len(f)]} == {a, b}:
                    f.insert(k + 1, v)
                    if m.fuv[fi]:
                        m.fuv[fi] = None
                    break
    for u, w in zip(path, path[1:]):
        if u == w:
            continue
        cand = [fi for fi, f in enumerate(m.faces) if u in f and w in f]
        if not cand:
            raise NotImplementedError('polySplit: puntos sin cara comun')
        fi = cand[0]
        f = m.faces[fi]
        iu, iw = f.index(u), f.index(w)
        p1 = f[iu:iw + 1] if iu < iw else f[iu:] + f[:iw + 1]
        p2 = f[iw:iu + 1] if iw < iu else f[iw:] + f[:iu + 1]
        keep, other = (p1, p2) if f[0] in p1 else (p2, p1)
        m.faces[fi] = keep
        m.faces.append(other)
        m.fuv.append(None)
        m.edges.append((u, w))
        m.hard.append(False)
    m.exact = False
    return f32(m)


OPERATIONS = {
    'polyMirror': op_mirror,
    'polySplit': op_split,
    'polyExtrudeEdge': op_extrude_edge,
    'polyExtrudeFace': op_extrude_face,
    'polySplitRing': op_split_ring,
    'polyBevel3': op_bevel,
}
INDEX_SENSITIVE = {'polyExtrudeEdge', 'polyExtrudeFace', 'polySplitRing', 'polyBevel3'}
