"""Lector de Maya Binary (.mb) en Python puro, sin Maya.

El .mb es un archivo IFF ("FOR8" en Maya 2014+, "FOR4" en versiones viejas).
Cada nodo es un bloque FOR8 cuyo tipo es un ID de 4 letras (XFRM = transform,
DMSH = mesh, ...). Dentro hay un CREA (nombre/padre) y un bloque por cada
setAttr. Las conexiones van en una lista LIS8:CONS con bloques CWFL.

Lo importante para Blender: Maya guarda en cada mesh un bloque MESH llamado
``ci`` (cachedInMesh) con la geometria ya evaluada, asi que aunque el objeto
tenga historial de construccion se puede recuperar sin Maya.

Formato del bloque MESH (big endian), descifrado a partir de estos archivos y
de la estructura del .ma equivalente:

    nombre\\0 flag(1)
    nFloats  float*nFloats              -> vertices (x,y,z)
    nInts    (v0|hard<<31, v1)*         -> aristas; bit 31 = arista dura
    nInts    edge*                      -> caras como lista de aristas;
                                           bit 31 = arista invertida,
                                           bits 29-30 = fin de cara
    nFloats  float*  [nInts int*]       -> normales bloqueadas (se ignoran;
                                           la lista de enteros solo existe
                                           si nFloats > 0)
    nUVSets  { int  nombre\\0  nFloats float*  nInts uvIdx* }*

Basado en la estructura IFF documentada por mottosso/maya-scenefile-parser
(https://github.com/mottosso/maya-scenefile-parser) y cgkit/mayabinary.py.
"""

import struct

from .maya_scene import MayaNode, MayaScene, MeshData, split_index_range

# IDs de tipo de nodo que nos interesan (el resto se guarda con su ID crudo)
NODE_TYPES = {
    'XFRM': 'transform',
    'DMSH': 'mesh',
    'DCAM': 'camera',
    'RLAM': 'lambert',
    'RBLN': 'blinn',
    'RPHO': 'phong',
    'SHAD': 'shadingEngine',
    'DMTI': 'materialInfo',
    'GRPP': 'groupParts',
    'GPID': 'groupId',
}


class MayaBinaryError(RuntimeError):
    pass


class _Chunk:
    __slots__ = ('tag', 'form', 'start', 'size', 'children')

    def __init__(self, tag, form, start, size):
        self.tag = tag
        self.form = form
        self.start = start
        self.size = size
        self.children = []


def _align(n, a):
    return (n + a - 1) & ~(a - 1)


class _IffReader:
    def __init__(self, data):
        self.data = data
        magic = data[:4]
        if magic == b'FOR8':
            self.hdr, self.size_fmt, self.align = 16, '>Q', 8
        elif magic == b'FOR4':
            self.hdr, self.size_fmt, self.align = 8, '>I', 4
        else:
            raise MayaBinaryError('No es un archivo Maya Binary (cabecera %r)' % magic)
        self.group_tags = (b'FOR8', b'LIS8', b'CAT8') if self.hdr == 16 else (b'FOR4', b'LIS4', b'CAT4')

    def parse(self, start=0, end=None):
        end = len(self.data) if end is None else end
        out = []
        off = start
        d = self.data
        while off + self.hdr <= end:
            tag = d[off:off + 4]
            size = struct.unpack_from(self.size_fmt, d, off + self.hdr - struct.calcsize(self.size_fmt))[0]
            body = off + self.hdr
            if tag in self.group_tags:
                form = d[body:body + 4]
                ch = _Chunk(tag, form, body + 4, size - 4)
                ch.children = self.parse(body + 4, body + 4 + _align(size - 4, self.align))
                off = body + 4 + _align(size - 4, self.align)
            else:
                ch = _Chunk(tag, None, body, size)
                off = body + _align(size, self.align)
            out.append(ch)
        return out


def _cstr(buf, off):
    end = buf.index(b'\0', off)
    return buf[off:end].decode('utf-8', 'replace'), end + 1


class MayaBinaryReader:
    def __init__(self, path):
        with open(path, 'rb') as f:
            self.data = f.read()
        self.iff = _IffReader(self.data)
        self.scene = MayaScene()
        self.version = 0
        self._shape_sg_groups = {}   # (shape, og index) -> [faces]

    # ------------------------------------------------------------------
    def read(self):
        top = self.iff.parse()
        if not top or top[0].form != b'Maya':
            raise MayaBinaryError('Bloque raiz Maya no encontrado')
        for ch in top[0].children:
            if ch.form == b'HEAD':
                self._read_head(ch)
            elif ch.tag in self.iff.group_tags and ch.form == b'CONS':
                self._read_connections(ch)
            elif ch.form is not None:
                self._read_node(ch)
        self._resolve_materials()
        return self.scene

    def _payload(self, ch):
        return self.data[ch.start:ch.start + ch.size]

    def _read_head(self, ch):
        for c in ch.children:
            p = self._payload(c).rstrip(b'\0').decode('utf-8', 'replace')
            if c.tag == b'VERS':
                try:
                    self.version = int(p.split()[0])
                except ValueError:
                    self.version = 0
            elif c.tag == b'LUNI':
                self.scene.linear_unit = p

    def _read_connections(self, ch):
        for c in ch.children:
            if c.children:
                self._read_connections(c)
            elif c.tag == b'CWFL':
                p = self._payload(c)
                parts = p[1:].split(b'\0')
                if len(parts) >= 2:
                    self.scene.connections.append(
                        (parts[0].decode('utf-8', 'replace'), parts[1].decode('utf-8', 'replace')))

    # ------------------------------------------------------------------
    def _read_node(self, ch):
        type_id = ch.form.decode('latin1')
        crea = next((c for c in ch.children if c.tag == b'CREA'), None)
        if crea is None:
            return
        p = self._payload(crea)
        has_uuid = self.version >= 2016 or self.version == 0
        strings = p[1:len(p) - 16] if has_uuid and len(p) > 17 and p[len(p) - 17] == 0 else p[1:]
        parts = strings.split(b'\0')
        name = parts[0].decode('utf-8', 'replace')
        parent = parts[1].decode('utf-8', 'replace') if len(parts) > 1 and parts[1] else None
        node = MayaNode(NODE_TYPES.get(type_id, type_id), name, parent)
        self.scene.add_node(node)

        mesh_blob = None
        for c in ch.children:
            if c.tag in (b'CREA', b'SLCT', b'ATTR'):
                continue
            p = self._payload(c)
            try:
                attr, off = _cstr(p, 0)
            except ValueError:
                continue
            off += 1  # byte de flags
            tag = c.tag
            try:
                if tag == b'MESH':
                    if attr in ('ci', 'i', 'o', 'cachedInMesh', 'inMesh'):
                        mesh_blob = (p, off)
                elif tag == b'DBLE':
                    self._set_array(node, attr, struct.unpack_from('>%dd' % ((len(p) - off) // 8), p, off), 1)
                elif tag in (b'DBL2', b'DBL3', b'FLT2', b'FLT3'):
                    n = 2 if tag in (b'DBL2', b'FLT2') else 3
                    fmt = 'd' if tag.startswith(b'D') else 'f'
                    cnt = (len(p) - off) // struct.calcsize(fmt)
                    self._set_array(node, attr, struct.unpack_from('>%d%s' % (cnt, fmt), p, off), n)
                elif tag == b'STR ':
                    node.attrs[attr], _ = _cstr(p, off)
                elif tag == b'CMP#':
                    node.attrs[attr] = self._read_components(p, off)
            except (struct.error, ValueError):
                self.scene.warnings.append('No se pudo leer %s.%s' % (name, attr))

        if node.type == 'mesh' and mesh_blob is not None:
            try:
                node.mesh = self._read_mesh(*mesh_blob)
                self._apply_tweaks(node)
            except Exception as exc:  # noqa: BLE001 - seguimos con el resto
                self.scene.warnings.append('Mesh %s ilegible: %s' % (name, exc))

    def _set_array(self, node, attr, values, width):
        base, a, b = split_index_range(attr)
        if a is None:
            if width == 1:
                node.attrs[attr] = values[0] if len(values) == 1 else values
            else:
                node.attrs[attr] = tuple(values[:width]) if len(values) == width else \
                    [tuple(values[i:i + width]) for i in range(0, len(values), width)]
            return
        arr = node.attrs.setdefault(base + '[]', {})
        for k, idx in enumerate(range(a, b + 1)):
            if width == 1:
                arr[idx] = values[k]
            else:
                arr[idx] = tuple(values[k * width:(k + 1) * width])

    def _read_components(self, p, off):
        count, = struct.unpack_from('>I', p, off)
        off += 4
        comps = {}
        for _ in range(count):
            kind = p[off:off + 4].decode('latin1')
            n, = struct.unpack_from('>I', p, off + 4)
            off += 8
            vals = struct.unpack_from('>%dI' % (2 * n), p, off)
            off += 8 * n
            idx = comps.setdefault(kind, [])
            for i in range(n):
                idx.extend(range(vals[2 * i], vals[2 * i + 1] + 1))
        return comps

    # ------------------------------------------------------------------
    def _read_mesh(self, p, off):
        def ints(o):
            n, = struct.unpack_from('>I', p, o)
            return struct.unpack_from('>%dI' % n, p, o + 4), o + 4 + 4 * n

        def floats(o):
            n, = struct.unpack_from('>I', p, o)
            return struct.unpack_from('>%df' % n, p, o + 4), o + 4 + 4 * n

        m = MeshData()
        vf, off = floats(off)
        m.verts = [vf[i:i + 3] for i in range(0, len(vf) - 2, 3)]
        ev, off = ints(off)
        edges = []
        for i in range(0, len(ev), 2):
            a, b = ev[i] & 0x7FFFFFFF, ev[i + 1] & 0x7FFFFFFF
            edges.append((a, b))
            if ev[i] & 0x80000000:
                m.hard_edges.add((min(a, b), max(a, b)))
        fe, off = ints(off)
        face = []
        for val in fe:
            e = edges[val & 0x0FFFFFFF]
            face.append(e[1] if val & 0x80000000 else e[0])
            if val & 0x60000000:
                m.faces.append(face)
                face = []
        if face:
            m.faces.append(face)
        nfv = sum(len(f) for f in m.faces)

        # normales bloqueadas por el usuario (+ su lista de indices): se saltan
        normals, off = floats(off)
        if normals:
            _, off = ints(off)
        if off + 4 <= len(p):
            nsets, = struct.unpack_from('>I', p, off)
            off += 4
            for _ in range(min(nsets, 16)):
                off += 4  # entero desconocido (siempre 0 en los archivos vistos)
                name, off = _cstr(p, off)
                uvf, off = floats(off)
                idx, off = ints(off)
                if len(idx) != nfv:
                    break
                uvs = [uvf[i:i + 2] for i in range(0, len(uvf) - 1, 2)]
                per_face = []
                k = 0
                for f in m.faces:
                    per_face.append([(-1 if idx[k + j] == 0xFFFFFFFF else idx[k + j]) for j in range(len(f))])
                    k += len(f)
                m.uv_sets.append((name, uvs, per_face))
        return m

    def _apply_tweaks(self, node):
        pts = node.attrs.get('pt[]')
        if not pts:
            return
        verts = node.mesh.verts
        for i, d in pts.items():
            if 0 <= i < len(verts):
                v = verts[i]
                verts[i] = (v[0] + d[0], v[1] + d[1], v[2] + d[2])

    # ------------------------------------------------------------------
    def _resolve_materials(self):
        sc = self.scene
        for src, dst in sc.connections:
            snode, sattr = sc.node_of_plug(src)
            dnode, dattr = sc.node_of_plug(dst)
            if dnode is None or snode is None:
                continue
            # material -> shadingEngine
            if dnode.type == 'shadingEngine' and dattr in ('ss', 'surfaceShader'):
                info = sc.materials.setdefault(dnode.name, {})
                info['shader'] = snode.name
                col = snode.attrs.get('c')
                if isinstance(col, tuple) and len(col) == 3:
                    info['color'] = col
            # shape -> shadingEngine (objeto completo o grupo de caras)
            if dnode.type == 'shadingEngine' and dattr.startswith('dsm') and snode.type == 'mesh':
                if sattr in ('iog', 'iog[0]', 'instObjGroups', 'instObjGroups[0]'):
                    sc.whole_object_sg[snode.path] = dnode.name
                elif '.og[' in sattr or '.objectGroups[' in sattr:
                    k = int(sattr.rsplit('[', 1)[1].rstrip(']'))
                    comp = snode.attrs.get('iog[0].og[%d].gcl' % k) or \
                        snode.attrs.get('instObjGroups[0].objectGroups[%d].objectGrpCompList' % k)
                    if snode.mesh is not None and comp:
                        faces = comp.get('CMDF', [])
                        snode.mesh.face_sets.setdefault(dnode.name, []).extend(faces)
        for name, info in sc.materials.items():
            info.setdefault('color', (0.5, 0.5, 0.5))


def read_mb(path):
    return MayaBinaryReader(path).read()
