"""Lector de Maya ASCII (.ma) en Python puro, sin Maya.

Un .ma es un script MEL con createNode / setAttr / connectAttr. Este lector
interpreta el subconjunto necesario para reconstruir la escena:

* transforms (t, r, s, pivotes, visibilidad) y jerarquia,
* meshes con geometria guardada (vt, ed, fc, uvst, pt),
* asignacion de materiales (lambert/blinn/phong -> shadingEngine),
* meshes con historial de construccion: se intenta evaluar el historial con
  ``maya_history.py``; si alguna operacion no esta soportada el mesh se marca
  como no evaluado.
"""

from .maya_scene import MayaNode, MayaScene, MeshData, split_index_range

# flags de setAttr que consumen un argumento
_SETATTR_ARG_FLAGS = {'-s', '-size', '-k', '-keyable', '-l', '-lock', '-cb', '-channelBox',
                      '-ca', '-caching', '-type', '-typ', '-ch', '-capacityHint'}
_SETATTR_NOARG_FLAGS = {'-av', '-alteredValue', '-c', '-clamp'}

_SHADER_TYPES = {'lambert', 'blinn', 'phong', 'phongE', 'standardSurface', 'aiStandardSurface',
                 'usdPreviewSurface', 'surfaceShader'}


def tokenize_statements(text):
    """Divide el texto MEL en sentencias, y cada sentencia en tokens."""
    statements = []
    tokens = []
    tok = []
    i, n = 0, len(text)
    in_str = False
    while i < n:
        ch = text[i]
        if in_str:
            if ch == '\\' and i + 1 < n:
                nxt = text[i + 1]
                tok.append({'n': '\n', 't': '\t', '"': '"', '\\': '\\'}.get(nxt, nxt))
                i += 2
                continue
            if ch == '"':
                in_str = False
                tokens.append(('s', ''.join(tok)))
                tok = []
            else:
                tok.append(ch)
            i += 1
            continue
        if ch == '"':
            if tok:
                tokens.append(('w', ''.join(tok)))
                tok = []
            in_str = True
        elif ch == ';':
            if tok:
                tokens.append(('w', ''.join(tok)))
                tok = []
            if tokens:
                statements.append(tokens)
            tokens = []
        elif ch == '/' and i + 1 < n and text[i + 1] == '/' and not tok:
            j = text.find('\n', i)
            i = n if j < 0 else j
            continue
        elif ch in ' \t\r\n':
            if tok:
                tokens.append(('w', ''.join(tok)))
                tok = []
        else:
            tok.append(ch)
        i += 1
    if tok:
        tokens.append(('w', ''.join(tok)))
    if tokens:
        statements.append(tokens)
    return statements


def _flag_value(tokens, *names):
    for k, (_, v) in enumerate(tokens):
        if v in names and k + 1 < len(tokens):
            return tokens[k + 1][1]
    return None


def _to_value(word):
    if word in ('yes', 'true', 'on'):
        return 1
    if word in ('no', 'false', 'off'):
        return 0
    try:
        return int(word)
    except ValueError:
        try:
            return float(word)
        except ValueError:
            return word


class MayaAsciiReader:
    def __init__(self, path):
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            self.text = f.read()
        self.scene = MayaScene()
        self.cur = None
        self.raw = {}   # nodo -> lista de (attr, tipo, valores) en orden
        self.angles_in_radians = False

    def read(self):
        for st in tokenize_statements(self.text):
            cmd = st[0][1]
            try:
                if cmd == 'createNode':
                    self._create_node(st)
                elif cmd == 'setAttr':
                    self._set_attr(st)
                elif cmd == 'connectAttr':
                    plugs = [v for t, v in st[1:] if t == 's']
                    if len(plugs) >= 2:
                        self.scene.connections.append((plugs[0], plugs[1]))
                elif cmd == 'select':
                    name = _flag_value(st, '-ne', '-noExpand')
                    self.cur = self.scene.resolve(name) if name else None
                elif cmd == 'currentUnit':
                    lin = _flag_value(st, '-l', '-linear')
                    if lin:
                        self.scene.linear_unit = lin
                elif cmd == 'parent':
                    self._parent(st)
            except (ValueError, IndexError) as exc:
                self.scene.warnings.append('Sentencia ignorada (%s): %s' % (exc, ' '.join(v for _, v in st[:4])))
        self._finish()
        return self.scene

    # ------------------------------------------------------------------
    def _create_node(self, st):
        ntype = st[1][1]
        name = _flag_value(st, '-n', '-name') or ntype
        parent = _flag_value(st, '-p', '-parent')
        node = MayaNode(ntype, name, parent)
        self.scene.add_node(node)
        self.raw[id(node)] = []
        self.cur = node

    def _parent(self, st):
        names = [v for t, v in st[1:] if t == 's']
        if len(names) >= 2:
            child = self.scene.resolve(names[0])
            parent = self.scene.resolve(names[1])
            if child is not None and parent is not None:
                if child.parent is not None and child in child.parent.children:
                    child.parent.children.remove(child)
                child.parent = parent
                parent.children.append(child)

    def _set_attr(self, st):
        node = self.cur
        k = 1
        attr = None
        vtype = None
        values = []
        # los flags pueden ir antes o despues del nombre del atributo
        while k < len(st):
            t, v = st[k]
            if t == 'w' and v in _SETATTR_ARG_FLAGS:
                if v in ('-type', '-typ'):
                    vtype = st[k + 1][1]
                k += 2
                continue
            if t == 'w' and v in _SETATTR_NOARG_FLAGS:
                k += 1
                continue
            if t == 's' and attr is None:
                attr = v
            else:
                values.append(st[k])
            k += 1
        if attr is None:
            return
        if '.' in attr and not attr.startswith('.'):
            node, attr = self.scene.node_of_plug(attr)
            attr = '.' + attr
        if node is None:
            return
        attr = attr[1:] if attr.startswith('.') else attr
        self.raw[id(node)].append((attr, vtype, values))

        # valores simples que sirven para transforms y flags
        if vtype in ('double3', 'float3', 'double2', 'float2') and len(values) in (2, 3):
            node.attrs[attr] = tuple(float(v) for _, v in values)
        elif vtype == 'string' and values:
            node.attrs[attr] = values[0][1]
        elif vtype == 'componentList':
            node.attrs[attr] = [v for _, v in values[1:]]
        elif vtype == 'matrix' and len(values) >= 16 and values[0][1] != 'xform':
            node.attrs[attr] = [float(v) for _, v in values[:16]]
        elif vtype is None and len(values) == 1:
            node.attrs[attr] = _to_value(values[0][1])
        elif vtype is None and len(values) == 3 and attr in ('t', 'r', 's', 'rp', 'sp', 'rpt', 'spt', 'sh', 'ra'):
            node.attrs[attr] = tuple(float(v) for _, v in values)

    # ------------------------------------------------------------------
    def _finish(self):
        sc = self.scene
        for node in sc.nodes:
            if node.type == 'mesh':
                node.mesh = self._build_mesh(node)
        # historial de construccion
        from .maya_history import evaluate_shape_history
        for node in sc.nodes:
            if node.type == 'mesh' and not node.attrs.get('io'):
                has_input = any(sc.node_of_plug(d)[0] is node and sc.node_of_plug(d)[1] in ('i', 'inMesh')
                                for _, d in sc.connections)
                if has_input:
                    try:
                        evaluated = evaluate_shape_history(self, node)
                    except NotImplementedError as exc:
                        sc.warnings.append('%s: historial no evaluado (%s)' % (node.name, exc))
                        evaluated = None
                    node.mesh = evaluated
        self._resolve_materials()

    def float_values(self, values):
        return [float(v) for _, v in values]

    def _build_mesh(self, node):
        entries = self.raw.get(id(node), [])
        vt, pt, edges, faces_raw = {}, {}, {}, []
        uv_names, uv_points = {}, {}
        for attr, vtype, values in entries:
            base, a, b = split_index_range(attr)
            if base == 'vt' and a is not None:
                f = self.float_values(values)
                for k, idx in enumerate(range(a, b + 1)):
                    vt[idx] = tuple(f[3 * k:3 * k + 3])
            elif base == 'pt' and a is not None:
                f = self.float_values(values)
                for k, idx in enumerate(range(a, b + 1)):
                    if 3 * k + 2 < len(f):
                        pt[idx] = tuple(f[3 * k:3 * k + 3])
            elif base == 'ed' and a is not None:
                f = [int(v) for _, v in values]
                for k, idx in enumerate(range(a, b + 1)):
                    edges[idx] = tuple(f[3 * k:3 * k + 3])
            elif base == 'fc' and vtype == 'polyFaces':
                faces_raw.append([v for _, v in values])
            elif attr.startswith('uvst[') and attr.endswith('].uvsn'):
                uv_names[int(attr[5:attr.index(']')])] = values[0][1] if values else 'map1'
            elif attr.startswith('uvst[') and '.uvsp' in attr:
                s = int(attr[5:attr.index(']')])
                _, a2, b2 = split_index_range(attr)
                if a2 is None:
                    continue
                f = self.float_values(values)
                pts = uv_points.setdefault(s, {})
                for k, idx in enumerate(range(a2, b2 + 1)):
                    pts[idx] = tuple(f[2 * k:2 * k + 2])
        if not vt or not edges or not faces_raw:
            node.attrs['pt[]'] = pt
            return None

        m = MeshData()
        nv = max(vt) + 1
        m.verts = [vt.get(i, (0.0, 0.0, 0.0)) for i in range(nv)]
        for i, d in pt.items():
            if i < nv:
                v = m.verts[i]
                m.verts[i] = (v[0] + d[0], v[1] + d[1], v[2] + d[2])
        m.edges = [edges[i][:2] for i in sorted(edges)]
        for a_, b_, smooth in edges.values():
            if not smooth:
                m.hard_edges.add((min(a_, b_), max(a_, b_)))
        uv_faces = {}
        for words in faces_raw:
            k = 0
            while k < len(words):
                w = words[k]
                if w in ('f', 'h'):
                    n = int(words[k + 1])
                    es = [int(x) for x in words[k + 2:k + 2 + n]]
                    loop = []
                    for e in es:
                        a_, b_, _ = edges[e if e >= 0 else -e - 1]
                        loop.append(a_ if e >= 0 else b_)
                    if w == 'f':
                        m.faces.append(loop)
                    k += 2 + n
                elif w == 'mu':
                    s, n = int(words[k + 1]), int(words[k + 2])
                    uv_faces.setdefault(s, {})[len(m.faces) - 1] = [int(x) for x in words[k + 3:k + 3 + n]]
                    k += 3 + n
                elif w in ('mc', 'fc'):
                    s, n = int(words[k + 1]), int(words[k + 2])
                    k += 3 + n
                else:
                    k += 1
        for s, pts in sorted(uv_points.items()):
            if not pts:
                continue
            uvs = [pts.get(i, (0.0, 0.0)) for i in range(max(pts) + 1)]
            per_face = [uv_faces.get(s, {}).get(i, [-1] * len(f)) for i, f in enumerate(m.faces)]
            m.uv_sets.append((uv_names.get(s, 'map%d' % (s + 1)), uvs, per_face))
        return m

    def _resolve_materials(self):
        sc = self.scene
        for src, dst in sc.connections:
            snode, sattr = sc.node_of_plug(src)
            dnode, dattr = sc.node_of_plug(dst)
            if snode is None or dnode is None or dnode.type != 'shadingEngine':
                continue
            if dattr in ('ss', 'surfaceShader') and snode.type in _SHADER_TYPES:
                info = sc.materials.setdefault(dnode.name, {})
                info['shader'] = snode.name
                col = snode.attrs.get('c') or snode.attrs.get('base_color') or snode.attrs.get('bc')
                if isinstance(col, tuple) and len(col) == 3:
                    info['color'] = col
            elif dattr.startswith('dsm') and snode.type == 'mesh':
                if sattr in ('iog', 'iog[0]', 'instObjGroups', 'instObjGroups[0]'):
                    sc.whole_object_sg[snode.path] = dnode.name
                elif '.og[' in sattr and snode.mesh is not None:
                    k = int(sattr.rsplit('[', 1)[1].rstrip(']'))
                    comps = snode.attrs.get('iog[0].og[%d].gcl' % k) or []
                    from .maya_scene import parse_component_list
                    faces = parse_component_list(comps).get('f', [])
                    if faces and faces[0] != '*':
                        snode.mesh.face_sets.setdefault(dnode.name, []).extend(faces)
        for info in sc.materials.values():
            info.setdefault('color', (0.5, 0.5, 0.5))


def read_ma(path):
    return MayaAsciiReader(path).read()
